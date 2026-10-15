"""플레이어 명령을 Jev에 보내 행동 확률을 받고, 그 확률대로 행동 하나를 뽑아요.

사용 예:
    from jev_client import JevClient

    with JevClient() as jev:
        decision = jev.decide("위로 올라가서 공격해")

    decision.probabilities  # {"move_forward": 0.75, "attack": 0.25, ...} 합이 1
    decision.choice         # "move_forward" (확률이 가장 높은 행동)
    decision.action         # 확률대로 뽑은 행동
    decision.is_command     # False면 행동을 시키는 말이 아니에요 (예: "오늘 날씨 좋다")
    decision.cost_usd       # 이번 요청 요금(USD)

명령줄에서 바로 확인 (aiserver 폴더에서):
    .venv/bin/python jev_client.py "앞으로 가" "오늘 날씨 좋다"
"""

import json
import random
import sys
from dataclasses import asdict, dataclass
from pathlib import Path

from dotenv import load_dotenv
from typesafe_sdk import Choice, Noul, TypeSafeClient

AISERVER_DIR = Path(__file__).resolve().parent
ACTIONS_PATH = AISERVER_DIR / "actions.json"

# 입력 100만 토큰당 가격(USD). 출력 토큰은 무료. https://docs.typesafe.ai/models
PRICE_PER_INPUT_MTOK = 0.042
# 행동별 Noul 중 가장 높은 값이 이보다 낮으면 명령이 아니라고 봐요.
# 테스트에서 명령이 아닌 말은 11% 이하, 실제 명령은 39% 이상이었어요.
COMMAND_THRESHOLD = 0.3
NOUL_INSTRUCTIONS = "Does the player's command ask the character to do this: {description}?"


@dataclass(frozen=True)
class ActionDecision:
    action: str
    choice: str
    probabilities: dict[str, float]
    confidence: float
    is_command: bool
    command_score: float
    model: str
    input_tokens: int
    cost_usd: float

    def to_dict(self):
        return asdict(self)


class JevClient:
    """Choice 질문 하나(행동 확률)와 행동별 Noul 질문(명령 판별)을 한 요청으로 보내요."""

    def __init__(self, actions_path=ACTIONS_PATH, api_key=None, model=None, rng=None):
        load_dotenv(AISERVER_DIR / ".env")
        data = json.loads(Path(actions_path).read_text(encoding="utf-8"))
        self.actions = data["actions"]
        self._questions = {"action": Choice(instructions=data["instructions"], criteria=self.actions)}
        for name, description in self.actions.items():
            self._questions[f"noul_{name}"] = Noul(instructions=NOUL_INSTRUCTIONS.format(description=description))
        self._rng = rng or random.Random()
        # API 키가 없으면 여기서 typesafe_sdk.TypeSafeError가 나요.
        self._client = TypeSafeClient(api_key=api_key, model=model)

    def decide(self, prompt: str) -> ActionDecision:
        response = self._client.system_one(prompt, self._questions)
        answer = response.choices["action"]
        probabilities = answer.probabilities
        action = self._rng.choices(list(probabilities), weights=list(probabilities.values()))[0]

        command_score = max(response.nouls[f"noul_{name}"].noul for name in self.actions)
        input_tokens = response.usage.input_tokens or 0
        return ActionDecision(
            action=action,
            choice=answer.choice,
            probabilities=probabilities,
            confidence=answer.confidence,
            is_command=command_score >= COMMAND_THRESHOLD,
            command_score=command_score,
            model=response.model,
            input_tokens=input_tokens,
            cost_usd=input_tokens / 1_000_000 * PRICE_PER_INPUT_MTOK,
        )

    def close(self):
        self._client.close()

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        self.close()


if __name__ == "__main__":
    total_cost = 0.0
    with JevClient() as jev:
        for prompt in sys.argv[1:]:
            decision = jev.decide(prompt)
            total_cost += decision.cost_usd
            print(json.dumps({"prompt": prompt, **decision.to_dict()}, ensure_ascii=False, indent=2))
    print(f"요청 {len(sys.argv) - 1}건, 요금 합계 ${total_cost:.6f}", file=sys.stderr)

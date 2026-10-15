"""플레이어 명령을 Jev에 보내 게임 캐릭터의 행동 확률로 바꿔요.

사용 예:
    from jev_client import JevClient

    with JevClient() as jev:
        decision = jev.decide("위로 올라가서 공격해")

    decision.probabilities  # {"move_up": 0.75, "attack": 0.25, ...} 합이 1
    decision.choice         # "move_up" (확률이 가장 높은 행동)
    decision.is_command     # False면 행동을 시키는 말이 아니에요 (예: "오늘 날씨 좋다")

명령줄에서 바로 확인 (aiserver 폴더에서):
    .venv/bin/python jev_client.py "위로 가" "오늘 날씨 좋다"
"""

import json
import sys
from dataclasses import asdict, dataclass
from pathlib import Path

from dotenv import load_dotenv
from typesafe_sdk import Choice, Noul, TypeSafeClient

AISERVER_DIR = Path(__file__).resolve().parent
ACTIONS_PATH = AISERVER_DIR / "actions.json"

# 행동별 Noul 중 가장 높은 값이 이보다 낮으면 명령이 아니라고 봐요.
# 테스트에서 명령이 아닌 말은 11% 이하, 실제 명령은 39% 이상이었어요.
COMMAND_THRESHOLD = 0.3
NOUL_INSTRUCTIONS = "Does the player's command ask the character to do this: {description}?"


@dataclass(frozen=True)
class ActionDecision:
    choice: str
    probabilities: dict[str, float]
    confidence: float
    is_command: bool
    command_score: float
    model: str

    def to_dict(self):
        return asdict(self)


class JevClient:
    """Choice 질문 하나(행동 확률)와 행동별 Noul 질문(명령 판별)을 한 요청으로 보내요."""

    def __init__(self, actions_path=ACTIONS_PATH, api_key=None, model=None):
        load_dotenv(AISERVER_DIR / ".env")
        data = json.loads(Path(actions_path).read_text(encoding="utf-8"))
        self.actions = data["actions"]
        self._questions = {"action": Choice(instructions=data["instructions"], criteria=self.actions)}
        for name, description in self.actions.items():
            self._questions[f"noul_{name}"] = Noul(instructions=NOUL_INSTRUCTIONS.format(description=description))
        # API 키가 없으면 여기서 typesafe_sdk.TypeSafeError가 나요.
        self._client = TypeSafeClient(api_key=api_key, model=model)

    def decide(self, prompt: str) -> ActionDecision:
        response = self._client.system_one(prompt, self._questions)
        answer = response.choices["action"]
        command_score = max(response.nouls[f"noul_{name}"].noul for name in self.actions)
        return ActionDecision(
            choice=answer.choice,
            probabilities=answer.probabilities,
            confidence=answer.confidence,
            is_command=command_score >= COMMAND_THRESHOLD,
            command_score=command_score,
            model=response.model,
        )

    def close(self):
        self._client.close()

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        self.close()


if __name__ == "__main__":
    with JevClient() as jev:
        for prompt in sys.argv[1:]:
            print(json.dumps({"prompt": prompt, **jev.decide(prompt).to_dict()}, ensure_ascii=False, indent=2))

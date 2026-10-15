# aiserver

메인 서버에서 플레이어 명령을 받아, 게임 캐릭터가 할 수 있는 행동마다 확률을 매겨 돌려주는 서버예요.
모델은 TypeSafe AI의 [Jev](https://docs.typesafe.ai/introduction)를 API로 써요.

## 환경 설정

Python 3.10 이상이 필요해요.

```bash
cd aiserver
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
cp .env.example .env
```

그다음 `.env`의 `TYPESAFE_API_KEY`에 [TypeSafe 콘솔](https://console.typesafe.ai/keys)에서 받은 키를 넣으세요.
`.env`는 `.gitignore`에 들어 있어서 커밋되지 않아요.

## 파일

| 파일 | 내용 |
|---|---|
| `jev_client.py` | Jev를 호출해 명령 하나를 행동 확률로 바꾸는 `JevClient` |
| `actions.json` | 탑뷰 게임 캐릭터의 행동 9개(상하좌우 이동, 좌우 회전, 공격, 방어, 구르기). 키는 행동 이름, 값은 Jev가 읽는 영어 설명이에요. |

## 사용법

```python
from jev_client import JevClient

with JevClient() as jev:
    decision = jev.decide("위로 올라가서 공격해")
    print(decision.to_dict())
```

명령줄에서 바로 확인할 수도 있어요.

```bash
.venv/bin/python jev_client.py "위로 가" "오늘 날씨 좋다"
```

`decide()`는 Jev에 한 번만 요청해요. 이 요청에 행동 확률을 묻는 Choice 질문과, 명령인지 판별하는 행동별 Noul 질문이 같이 들어가요.

| 필드 | 내용 |
|---|---|
| `probabilities` | 행동마다 확률. 합이 1이에요. |
| `choice` | 확률이 가장 높은 행동 |
| `confidence` | 확률이 한 행동에 얼마나 몰렸는지(0~1). 낮으면 Jev도 헷갈린다는 뜻이에요. |
| `is_command` | 행동을 시키는 말인지. `command_score`가 0.3 이상이면 `True`예요. |
| `command_score` | 행동별 Noul 중 가장 높은 값(0~1) |
| `model` | 실제로 응답한 모델 버전 |

Choice는 명령이 아닌 말에도 반드시 행동 하나를 골라요. 그래서 `is_command`가 `False`면 `probabilities`는 무시하는 게 좋아요.
가장 높은 행동을 쓸지, 확률대로 뽑을지는 받는 쪽에서 정하면 돼요.

## 테스트하며 알게 된 점 (jev-1.13.0, 한국어 명령 20개)

- 분명한 명령("위로 가", "오른쪽으로 돌아", "굴러!", "ㄱㄱ 돌격" 등)은 모두 맞혔고 확률이 92~100%였어요.
- 확률 숫자는 대략값이에요. 같은 입력도 몇 %p 흔들리고, `actions.json`의 행동 순서를 바꾸면 애매한 명령에서 최대 20%p 정도 움직여요. 행동 순서는 바꾸지 않는 게 좋아요.
- "앞으로", "뒤로"처럼 캐릭터 기준 방향은 Jev가 화면 방향을 알 수 없어서 불안정해요.
- Jev는 영어가 주력이고 한중일 언어는 상대적으로 약하다고 [문서](https://docs.typesafe.ai/models)에 나와 있어요.

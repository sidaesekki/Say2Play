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
| `server.py` | 메인 서버가 호출하는 HTTP 서버. `POST /v1/decide`로 `JevClient`를 감싸요(임시 스펙). |
| `actions.json` | 탑뷰 게임 캐릭터의 행동 7개(전후 이동, 좌우 회전, 공격, 방어, 구르기). 키는 행동 이름, 값은 Jev가 읽는 영어 설명이에요. |

## 사용법

```python
from jev_client import JevClient

with JevClient() as jev:
    decision = jev.decide("앞으로 가")
    print(decision.to_dict())
```

명령줄에서 바로 확인할 수도 있어요.

```bash
.venv/bin/python jev_client.py "앞으로 가" "오늘 날씨 좋다"
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

## HTTP 서버

메인 서버는 이 서버를 HTTP로 호출해요. 스펙은 실제 스펙이 정해지기 전까지 쓰는 임시 스펙이에요.

```bash
.venv/bin/python server.py   # 기본 포트 8001 (PORT 환경 변수로 변경)
curl -X POST localhost:8001/v1/decide -d '{"text": "앞으로 가"}'
# {"choice": "move_forward", "probabilities": {...}, "confidence": ..., "isCommand": true, "commandScore": ..., "model": "jev-1.13.0"}
```

Jev 호출에 실패하면 502, `text`가 비어 있으면 400을 돌려줘요. 상태 확인은 `GET /health`예요.

## 테스트하며 알게 된 점 (jev-1.13.0, 한국어 명령 20개)

- 분명한 명령("앞으로 가", "오른쪽으로 돌아", "굴러!", "ㄱㄱ 돌격" 등)은 모두 맞혔고 확률이 92~100%였어요.
- 확률 숫자는 대략값이에요. 같은 입력도 몇 %p 흔들리고, `actions.json`의 행동 순서를 바꾸면 애매한 명령에서 최대 20%p 정도 움직여요. 행동 순서는 바꾸지 않는 게 좋아요.
- `앞으로`와 `뒤로`는 캐릭터가 바라보는 방향과 그 반대 방향으로 이동해요.
- Jev는 영어가 주력이고 한중일 언어는 상대적으로 약하다고 [문서](https://docs.typesafe.ai/models)에 나와 있어요.

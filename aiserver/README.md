# aiserver

메인 서버에서 플레이어 명령을 받아, 게임 캐릭터가 할 수 있는 행동마다 확률을 매겨 돌려주는 서버예요.
모델은 TypeSafe AI의 [Jev](https://docs.typesafe.ai/introduction)를 API로 써요.
음성 명령은 별도의 음성 인식(STT) 서버가 텍스트로 바꿔요.

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

음성 인식 서버도 띄우려면 패키지를 추가로 설치해요. MLX를 쓰기 때문에 **Apple Silicon 맥에서만** 돌아가요.
도커 이미지(`server.py`)에는 들어가지 않아요.

```bash
.venv/bin/pip install -r requirements-stt.txt
```

## 파일

| 파일 | 내용 |
|---|---|
| `jev_client.py` | Jev를 호출해 명령 하나를 행동 확률로 바꾸는 `JevClient` |
| `server.py` | 메인 서버가 호출하는 HTTP 서버. `POST /v1/decide`로 `JevClient`를 감싸요(임시 스펙). |
| `actions.json` | 탑뷰 게임 캐릭터의 행동 7개(전후 이동, 좌우 회전, 공격, 방어, 구르기). 키는 행동 이름, 값은 Jev가 읽는 영어 설명이에요. |
| `voice_server.py` | 음성 인식 서버. `POST /v1/stt`로 음성 한 구간을 텍스트로 바꿔요. |
| `stt.py` | Whisper(MLX)로 16kHz 모노 WAV를 텍스트로 바꾸는 `SpeechToText` |
| `requirements-stt.txt` | 음성 인식 서버용 패키지(fastapi, uvicorn, mlx-whisper) |

## 사용법

```python
from jev_client import JevClient

with JevClient() as jev:
    decision = jev.decide("앞으로 가")
    print(decision.to_dict())
```

명령줄에서 바로 확인할 수도 있어요. 마지막에 요금 합계가 나와요.

```bash
.venv/bin/python jev_client.py "앞으로 가" "오늘 날씨 좋다"
```

`decide()`는 Jev에 한 번만 요청해요. 이 요청에 행동 확률을 묻는 Choice 질문과, 명령인지 판별하는 행동별 Noul 질문이 같이 들어가요.

| 필드 | 내용 |
|---|---|
| `probabilities` | 행동마다 확률. 합이 1이에요. |
| `choice` | 확률이 가장 높은 행동 |
| `action` | `probabilities`대로 뽑은 행동. 70%짜리 행동은 열 번 중 일곱 번쯤 나와요. |
| `confidence` | 확률이 한 행동에 얼마나 몰렸는지(0~1). 낮으면 Jev도 헷갈린다는 뜻이에요. |
| `is_command` | 행동을 시키는 말인지. `command_score`가 0.3 이상이면 `True`예요. |
| `command_score` | 행동별 Noul 중 가장 높은 값(0~1) |
| `model` | 실제로 응답한 모델 버전 |
| `input_tokens`, `cost_usd` | 이번 요청의 입력 토큰 수와 요금(USD). 입력 100만 토큰당 $0.042이고 출력은 무료예요. |

Choice는 명령이 아닌 말에도 반드시 행동 하나를 골라요. 그래서 `is_command`가 `False`면 `probabilities`는 무시하는 게 좋아요.
가장 높은 행동(`choice`)을 쓸지, 확률대로 뽑은 행동(`action`)을 쓸지는 받는 쪽에서 정하면 돼요.

## HTTP 서버

메인 서버는 이 서버를 HTTP로 호출해요. 스펙은 실제 스펙이 정해지기 전까지 쓰는 임시 스펙이에요.

```bash
.venv/bin/python server.py   # 기본 포트 8001 (PORT 환경 변수로 변경)
curl -X POST localhost:8001/v1/decide -d '{"text": "앞으로 가"}'
# {"choice": "move_forward", "probabilities": {...}, "confidence": ..., "isCommand": true, "commandScore": ..., "model": "jev-1.13.0"}
```

Jev 호출에 실패하면 502, `text`가 비어 있으면 400을 돌려줘요. 상태 확인은 `GET /health`예요.

## 음성 인식 서버

음성 명령은 이렇게 흘러가요. 말의 시작과 끝은 클라이언트가 판단해요(예: 브라우저의 음성 구간 감지 VAD).

```
클라이언트: 마이크 → 말소리 구간만 잘라 16kHz 모노 WAV → POST /v1/stt (voice_server.py) → 텍스트
클라이언트: 텍스트 → 메인 서버 → POST /v1/decide (server.py) → 행동
```

```bash
.venv/bin/uvicorn voice_server:app --port 8002
curl -X POST localhost:8002/v1/stt -H 'Content-Type: audio/wav' --data-binary @command.wav
# {"text": "앞으로 가", "audioMs": 581, "sttMs": 330}
```

- 처음 켤 때 Hugging Face에서 음성 인식 모델을 내려받아요. 기본 모델 `mlx-community/whisper-small-mlx`는 약 0.5GB예요. 모델을 메모리에 올린 뒤에 요청을 받기 시작해요.
- WAV가 아니거나 16kHz 모노 16비트가 아니면 400을 돌려줘요. 상태 확인은 `GET /health`예요.
- `.env` 설정:
  - `STT_MODEL`: 모델을 바꿔요. 예: `mlx-community/whisper-large-v3-turbo`(약 1.6GB, 더 정확해요).
  - `CORS_ORIGINS`: 이 서버를 부를 수 있는 브라우저 주소를 좁혀요. 비워 두면 모두 허용해요.
- 한국어로 고정하고, 게임 용어("앞으로 가, 뒤로 가, 왼쪽으로 돌아, 공격, 방어, 구르기" 등)를 초기 프롬프트로 알려 줘요. 그러면 Whisper가 이 단어들로 더 잘 알아들어요.
- 맥 음성 합성으로 만든 짧은 명령으로 M1 맥북 에어에서 시험했어요.
  - 0.5~1.8초 음성을 0.3~0.4초에 인식했어요.
  - "막아"를 "mega"로 잘못 듣는 등 아주 짧은 말에서 틀리기도 해요.
  - 실제 목소리로는 더 확인해야 해요.

## 테스트하며 알게 된 점 (jev-1.13.0, 한국어 명령 20개)

- 분명한 명령("앞으로 가", "오른쪽으로 돌아", "굴러!", "ㄱㄱ 돌격" 등)은 모두 맞혔고 확률이 92~100%였어요.
- 확률 숫자는 대략값이에요. 같은 입력도 몇 %p 흔들리고, `actions.json`의 행동 순서를 바꾸면 애매한 명령에서 최대 20%p 정도 움직여요. 행동 순서는 바꾸지 않는 게 좋아요.
- `앞으로`와 `뒤로`는 캐릭터가 바라보는 방향과 그 반대 방향으로 이동해요.
- Jev는 영어가 주력이고 한중일 언어는 상대적으로 약하다고 [문서](https://docs.typesafe.ai/models)에 나와 있어요.

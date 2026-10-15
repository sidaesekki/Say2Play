"""음성 인식(STT) 서버. 클라이언트가 보낸 음성 한 구간을 Whisper로 텍스트로 바꿔 돌려줘요.

말의 시작과 끝은 클라이언트가 판단해서, 말소리 구간만 잘라 보내요.
인식된 텍스트로 행동을 정하는 건 메인 서버가 부르는 server.py(POST /v1/decide)가 맡아요.
음성 인식에 MLX를 써서 Apple Silicon 맥에서만 돌아가요. 패키지는 requirements-stt.txt로 설치해요.

    POST /v1/stt   본문: 16kHz 모노 16비트 WAV
    200 {"text": "앞으로 가", "audioMs": 581, "sttMs": 330}
    400 WAV 형식이 아닐 때

    GET /health    {"status": "ok", "sttModel": "mlx-community/whisper-small-mlx"}

실행 (aiserver 폴더에서):
    .venv/bin/uvicorn voice_server:app --port 8002
"""

import os
from contextlib import asynccontextmanager
from pathlib import Path

from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from starlette.concurrency import run_in_threadpool

from stt import SpeechToText

load_dotenv(Path(__file__).resolve().parent / ".env")
services = {}


@asynccontextmanager
async def lifespan(app):
    stt = SpeechToText()
    await run_in_threadpool(stt.warm_up)
    services["stt"] = stt
    yield


app = FastAPI(title="Say2Play STT server", lifespan=lifespan)
# 브라우저(게임 클라이언트)가 직접 부르니 다른 주소에서 오는 요청을 허용해요. .env의 CORS_ORIGINS로 좁힐 수 있어요.
app.add_middleware(
    CORSMiddleware,
    allow_origins=(os.environ.get("CORS_ORIGINS") or "*").split(","),
    allow_methods=["GET", "POST"],
    allow_headers=["Content-Type"],
)


def transcribe(body: bytes):
    try:
        transcript = services["stt"].transcribe_wav(body)
    except ValueError as error:
        raise HTTPException(400, str(error)) from error
    return {"text": transcript.text, "audioMs": transcript.audio_ms, "sttMs": transcript.stt_ms}


@app.get("/health")
def health():
    return {"status": "ok", "sttModel": services["stt"].model}


@app.post("/v1/stt")
async def stt(request: Request):
    return await run_in_threadpool(transcribe, await request.body())

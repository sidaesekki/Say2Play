"""Whisper(MLX)로 음성을 텍스트로 바꿔요. MLX를 쓰기 때문에 Apple Silicon 맥에서만 돌아가요.

사용 예:
    from stt import SpeechToText

    stt = SpeechToText()
    transcript = stt.transcribe_wav(wav_bytes)  # 16kHz 모노 16비트 WAV
    transcript.text
"""

import io
import os
import threading
import time
import wave
from dataclasses import asdict, dataclass

import mlx_whisper
import numpy as np

# .env의 STT_MODEL로 바꿀 수 있어요. 예: mlx-community/whisper-large-v3-turbo
DEFAULT_MODEL = "mlx-community/whisper-small-mlx"
SAMPLE_RATE = 16000
# 게임 용어를 미리 보여 주면 Whisper가 이 단어들로 더 잘 알아들어요.
INITIAL_PROMPT = "앞으로 가, 뒤로 가, 왼쪽으로 돌아, 오른쪽으로 돌아, 공격, 방어, 구르기, 적"


@dataclass(frozen=True)
class Transcript:
    text: str
    audio_ms: int
    stt_ms: int

    def to_dict(self):
        return asdict(self)


def read_wav(data: bytes) -> np.ndarray:
    """16kHz 모노 16비트 WAV를 Whisper가 받는 float32 배열로 바꿔요."""
    try:
        with wave.open(io.BytesIO(data)) as wav:
            if (wav.getframerate(), wav.getnchannels(), wav.getsampwidth()) != (SAMPLE_RATE, 1, 2):
                raise ValueError("16kHz 모노 16비트 WAV만 받아요.")
            frames = wav.readframes(wav.getnframes())
    except (wave.Error, EOFError) as error:
        # 너무 짧은 본문은 wave.Error 대신 EOFError가 나요.
        raise ValueError(f"WAV 형식이 아니에요: {str(error) or '본문이 너무 짧아요'}") from error
    return np.frombuffer(frames, dtype=np.int16).astype(np.float32) / 32768.0


class SpeechToText:
    def __init__(self, model=None):
        self.model = model or os.environ.get("STT_MODEL") or DEFAULT_MODEL
        # MLX 추론은 한 번에 하나씩 돌려요.
        self._lock = threading.Lock()

    def warm_up(self):
        """모델을 미리 내려받고 메모리에 올려서 첫 요청이 느려지지 않게 해요."""
        self.transcribe(np.zeros(SAMPLE_RATE // 2, dtype=np.float32))

    def transcribe(self, audio: np.ndarray) -> Transcript:
        start = time.perf_counter()
        with self._lock:
            result = mlx_whisper.transcribe(
                audio,
                path_or_hf_repo=self.model,
                language="ko",
                initial_prompt=INITIAL_PROMPT,
                condition_on_previous_text=False,
                temperature=0.0,
            )
        return Transcript(
            text=result["text"].strip(),
            audio_ms=round(len(audio) / SAMPLE_RATE * 1000),
            stt_ms=round((time.perf_counter() - start) * 1000),
        )

    def transcribe_wav(self, data: bytes) -> Transcript:
        return self.transcribe(read_wav(data))

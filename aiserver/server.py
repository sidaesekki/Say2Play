"""메인 서버가 호출하는 HTTP 서버예요. JevClient를 감싸서 명령 하나를 행동 하나로 바꿔 돌려줘요.

스펙은 실제 스펙이 정해지기 전까지 쓰는 임시 스펙이에요(mainserver의 HttpActionResolver와 같아요).

    POST /v1/decide   {"text": "위로 가"}
    200 {"choice": "move_up", "probabilities": {...}, "confidence": 0.9,
         "isCommand": true, "commandScore": 0.8, "model": "jev-1.13.0"}
    400 text가 없거나 비었을 때
    502 Jev 호출에 실패했을 때

    GET /health       {"status": "ok"}

실행 (aiserver 폴더에서):
    .venv/bin/python server.py           # 기본 포트 8001, PORT 환경 변수로 바꿀 수 있어요.
"""

import json
import logging
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from jev_client import JevClient

log = logging.getLogger("aiserver")


class Handler(BaseHTTPRequestHandler):
    jev: JevClient

    def do_GET(self):
        if self.path == "/health":
            self._send(200, {"status": "ok"})
        else:
            self._send(404, {"error": "not found"})

    def do_POST(self):
        if self.path != "/v1/decide":
            self._send(404, {"error": "not found"})
            return
        try:
            length = int(self.headers.get("Content-Length", 0))
            text = json.loads(self.rfile.read(length) or b"{}").get("text")
        except (ValueError, AttributeError):
            text = None
        if not isinstance(text, str) or not text.strip():
            self._send(400, {"error": "text is required"})
            return
        try:
            decision = self.jev.decide(text.strip())
        except Exception:
            log.exception("Jev 호출 실패")
            self._send(502, {"error": "jev request failed"})
            return
        self._send(200, {
            "choice": decision.choice,
            "probabilities": decision.probabilities,
            "confidence": decision.confidence,
            "isCommand": decision.is_command,
            "commandScore": decision.command_score,
            "model": decision.model,
        })

    def _send(self, status, body):
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


def main():
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
    port = int(os.environ.get("PORT", "8001"))
    # API 키가 없으면 여기서 바로 실패해요.
    Handler.jev = JevClient()
    server = ThreadingHTTPServer(("0.0.0.0", port), Handler)
    log.info("listening on :%d", port)
    try:
        server.serve_forever()
    finally:
        Handler.jev.close()


if __name__ == "__main__":
    main()

# Say2Play — React 게임 클라이언트

React + Vite 기반의 탑뷰 게임입니다. 기존 Canvas 훈련장 디자인을 유지하며, 게임 상태와 명령 해석은 서버에서 관리합니다.

## 실행

Node.js 20.19+ 또는 22.12+가 필요합니다.

```sh
cd gameclient
npm install
npm run dev
```

터미널에 표시된 Vite 주소(기본 http://localhost:5173)에 접속합니다. HTML 파일을 직접 열어 실행하는 방식은 지원하지 않습니다.

개발 서버는 `/api`와 `/ws` 요청을 `http://localhost:8080`으로 전달합니다. 서버 주소를 바꾸려면 `.env.example`을 `.env.local`로 복사하고 `API_PROXY_TARGET`을 수정한 뒤 개발 서버를 재시작하세요.

실제 플레이에는 mainserver와 aiserver가 함께 떠 있어야 합니다. 저장소 루트에서 `docker compose up --build`로 한 번에 띄울 수 있습니다.

```sh
npm test
npm run build
npm run preview
```

`dist/`가 배포 결과물입니다. 운영 환경에서는 같은 도메인의 `/api/v1`과 `/ws`를 메인 서버로 프록시하세요. 별도 도메인을 사용한다면 빌드 전에 `VITE_API_BASE_URL`과 `VITE_WS_BASE_URL`을 설정하고 서버에서 CORS 및 WebSocket Origin을 허용해야 합니다. `API_PROXY_TARGET`은 개발 서버 전용이며 preview/정적 호스팅에는 적용되지 않습니다.

## 동작 및 API

- 로비에서 **혼자 하기**(두 캐릭터를 모두 조종) 또는 **친구와 하기**(1:1)를 고릅니다. `POST /api/v1/sessions`(`mode: SOLO | MULTI`)로 세션을 만들고 받은 참가자 토큰을 탭의 sessionStorage에 저장합니다.
- 친구와 하기는 방을 만들면 주소창이 초대 링크(`?session={sessionId}`)로 바뀝니다. 링크로 들어온 사람은 `POST /sessions/{sessionId}/join`으로 참가하며, 두 명이 모이면 시작합니다. 내 캐릭터의 입력창만 보입니다.
- 이후 REST 요청은 `X-Player-Token` 헤더에 토큰을 담아 보냅니다. 실시간 이벤트는 STOMP(`/ws`, CONNECT 헤더 `X-Session-Id`·`X-Player-Token`)로 `/topic/sessions/{sessionId}`를 구독해 받습니다.
- 연결 직후 `GET /sessions/{sessionId}`로 상태를 복원하고, 그사이 받은 이벤트를 버퍼링했다가 적용합니다. 새로고침해도 10초 안에 다시 연결되면 자리가 유지됩니다.
- 입력 한 건은 **동작 하나**입니다. `ACTION_STARTED`의 `from`, `to`, `durationMs`로 애니메이션을 보간하고 `ACTION_FINISHED`의 좌표와 방향을 확정 상태로 적용합니다. 이동 가능 여부, 충돌, 적중은 서버가 결정합니다.
- 한쪽이 쓰러지면(`ROUND_OVER`) 승리 배너와 카운트다운을 표시하고, 3초 뒤 `SESSION_RESET`으로 새 판이 시작됩니다. 수동 초기화 버튼은 혼자 하기에만 있습니다.
- 게임 나가기는 `POST /sessions/{sessionId}/leave`입니다. 1:1에서 상대가 나가면 같은 링크로 새 상대를 기다립니다.
- Enter로 전송, Shift+Enter로 줄바꿈합니다. 한국어 조합 중 Enter는 전송하지 않습니다. 실패한 입력은 유지하고 자동 재전송하지 않습니다.
- 상태바 좌표는 API와 동일한 좌상단 `(0, 0)` 기준입니다.

## 구성

- `src/main.jsx`: React 화면, 캐릭터 입력 패널, Canvas 생명주기
- `src/useGame.js`: 로비·참가, STOMP 연결과 복구, 명령·초기화·나가기 처리
- `src/api.js`: REST 공통 응답·에러 처리, 토큰 헤더, STOMP 주소와 초대 링크
- `src/state.js`: 서버 상태 복원, 이벤트 적용, 애니메이션 좌표
- `src/renderer.js`: Canvas 지도·캐릭터·동작 렌더링
- `style.css`: 기존 반응형 스타일
- `src/*.test.js`: API 계약 및 서버 이벤트·보간 테스트 (Node 내장 테스트 러너)

기준: [API 명세](../docs/api.md), [상태·동작 정의](../docs/erd.md).

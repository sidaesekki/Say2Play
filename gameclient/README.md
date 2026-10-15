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

**현재 저장소의 mainserver는 Spring Boot 기본 골격만 있고 게임 API 구현은 없습니다.** 실제 플레이에는 [API 명세](../docs/api.md)의 REST 및 WebSocket API를 구현한 서버와 해당 서버가 사용하는 AI 서비스가 필요합니다. 서버가 없으면 연결 실패 메시지가 표시됩니다.

```sh
npm test
npm run build
npm run preview
```

`dist/`가 배포 결과물입니다. 운영 환경에서는 같은 도메인의 `/api/v1`과 `/ws`를 메인 서버로 프록시하세요. 별도 도메인을 사용한다면 빌드 전에 `VITE_API_BASE_URL`과 `VITE_WS_BASE_URL`을 설정하고 서버에서 CORS 및 WebSocket Origin을 허용해야 합니다. `API_PROXY_TARGET`은 개발 서버 전용이며 preview/정적 호스팅에는 적용되지 않습니다.

## 동작 및 API

- 첫 진입: `POST /api/v1/sessions`, 이후 `/ws/sessions/{sessionId}` 연결.
- 세션 ID를 탭의 sessionStorage에 저장합니다. 새로고침·재연결 시 `GET /sessions/{sessionId}`로 상태를 복원합니다. 동기화 중 수신한 이벤트는 버퍼링한 뒤 적용합니다.
- 두 캐릭터에 각각 원문 `{text}`를 REST로 전송합니다. 앞뒤 공백 제외 1~500자이며, 입력 한 건은 **동작 하나**입니다. 반복·쉼표·줄바꿈을 클라이언트에서 여러 동작으로 나누지 않습니다.
- `ACTION_STARTED`의 `from`, `to`, `durationMs`로 애니메이션을 보간하며 `ACTION_FINISHED`의 좌표와 방향을 확정 상태로 적용합니다. 이동 가능 여부와 충돌은 서버가 결정합니다.
- Enter로 전송, Shift+Enter로 줄바꿈합니다. 한국어 조합 중 Enter는 전송하지 않습니다. 실패한 입력은 유지하고 자동 재전송하지 않습니다.
- 초기화는 `POST /sessions/{sessionId}/reset`, 게임 나가기는 `DELETE /sessions/{sessionId}`입니다. 새로고침 시 복구를 위해 페이지 종료 시에는 세션을 삭제하지 않습니다. 미종료 세션은 서버의 30분 만료 정책을 따릅니다.
- 연결 중단 시 최대 10초 간격으로 재연결합니다. 세션 종료·만료 시 새 게임을 시작할 수 있습니다.
- 상태바 좌표는 API와 동일한 좌상단 `(0, 0)` 기준입니다. 마지막 입력 문구는 해당 응답의 `action.id`와 함께 메모리에만 보관됩니다.

## 구성

- `src/main.jsx`: React 화면, 캐릭터 입력 패널, Canvas 생명주기
- `src/useGame.js`: 세션, 연결 복구, 명령·초기화·종료 처리
- `src/api.js`: REST 공통 응답·에러 처리 및 WebSocket 주소
- `src/state.js`: 서버 상태 복원, 이벤트 적용, 애니메이션 좌표
- `src/renderer.js`: Canvas 지도·캐릭터·동작 렌더링
- `style.css`: 기존 반응형 스타일
- `src/*.test.js`: API 계약 및 서버 이벤트·보간 테스트 (Node 내장 테스트 러너)

기준: [API 명세](../docs/api.md), [상태·동작 정의](../docs/erd.md).

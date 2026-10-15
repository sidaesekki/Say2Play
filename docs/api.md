# Say2Play API 명세 v1 (혼자 하기 · 1:1 멀티플레이)

> 기준 문서: [erd.md](erd.md)

## 공통

- Base URL: `/api/v1`
- 사용자 계정은 없다. 세션을 만들거나(`POST /sessions`) 참가하면(`POST /sessions/{sessionId}/join`) 참가자 토큰(`playerToken`)을 받고, 이후 요청은 `X-Player-Token` 헤더에 이 토큰을 담아 보낸다.
- 모든 게임 상태는 서버 메모리에 있다. 서버를 재시작하거나 세션이 만료되면 상태가 사라진다.
- 세션 하나에 캐릭터가 2개(`PLAYER`, `ENEMY`) 있다. 세션 종류(`mode`)는 두 가지다.
  - `SOLO`(혼자 하기): 한 사람이 두 캐릭터를 모두 조종한다. 수동 초기화를 쓸 수 있다.
  - `MULTI`(1:1): 만든 사람이 `PLAYER`, 초대 링크로 참가한 사람이 `ENEMY`를 맡는다. 자기 캐릭터에만 명령할 수 있다.
- 한쪽이 쓰러지면 판이 끝나고(`ROUND_OVER`) 3초 뒤 자동으로 초기화된다(두 모드 공통).
- 세션은 마지막 입력(`lastActiveAt`) 이후 30분이 지나면 만료된다. 참가자가 모두 나가도 종료된다.
- 클라이언트는 명령을 REST로 보내고, 진행 상황은 [STOMP](#stomp-ws)로 받는다. 같은 세션의 모든 참가자에게 같은 이벤트가 브로드캐스트된다.
- 좌표(`x`, `y`)는 그리드 칸 단위다. 좌상단이 `(0, 0)`이고, 오른쪽이 `x+`, 아래쪽이 `y+`다.

**성공 응답 형식:**

```json
{
  "success": true,
  "data": { }
}
```

**에러 응답 형식:**

```json
{
  "success": false,
  "error": {
    "code": "SESSION_NOT_FOUND",
    "message": "세션을 찾을 수 없습니다."
  }
}
```

**공통 Errors:**

| Status | code | 설명 |
| --- | --- | --- |
| `401` | `INVALID_PLAYER_TOKEN` | `X-Player-Token`이 없거나 이 세션의 참가자 토큰이 아님 (토큰이 필요한 API) |
| `404` | `SESSION_NOT_FOUND` | `sessionId`에 해당하는 세션이 없거나 만료됨 |
| `500` | `INTERNAL_ERROR` | 서버 내부 오류 |

---

### POST /sessions

> 새 게임 세션을 만들고 요청한 사람을 첫 참가자로 넣습니다. 로비에서 "혼자 하기" 또는 "친구와 하기"를 누를 때 호출합니다.
>

**Auth Required:** No

**Request Body:**

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `mode` | string | Yes | `SOLO` 또는 `MULTI` |

```json
{
  "mode": "MULTI"
}
```

**Response `201`:**

```json
{
  "success": true,
  "data": {
    "session": {
      "id": "3f2a1c9e-7b1d-4c2e-9a51-0d6e8f4b2c10",
      "mode": "MULTI",
      "status": "WAITING",
      "mapCols": 13,
      "mapRows": 9
    },
    "characters": [
      {
        "id": "a8c4e2b1-5f3d-4e7a-b6c9-1d2e3f4a5b6c",
        "type": "PLAYER",
        "x": 6,
        "y": 7,
        "facing": "N",
        "state": "IDLE",
        "hp": 100,
        "queue": []
      },
      {
        "id": "b9d5f3c2-6a4e-4f8b-87da-2e3f4a5b6c7d",
        "type": "ENEMY",
        "x": 6,
        "y": 1,
        "facing": "S",
        "state": "IDLE",
        "hp": 100,
        "queue": []
      }
    ],
    "players": [
      { "controls": ["PLAYER"], "connected": false }
    ],
    "me": {
      "playerToken": "q3Jx0b5V8m1nQ7...",
      "controls": ["PLAYER"]
    }
  }
}
```

> 플레이어는 아래쪽 `(6, 7)`에서 북쪽을, 적은 위쪽 `(6, 1)`에서 남쪽을 바라본 상태로 시작합니다.
> `me.playerToken`은 **이 응답에서만** 내려줍니다. 클라이언트는 탭 단위로 보관하고(`sessionStorage`) 이후 요청의 `X-Player-Token` 헤더와 STOMP CONNECT 헤더에 넣습니다.
> `SOLO`는 `status: PLAYING`, `me.controls: ["PLAYER", "ENEMY"]`로 바로 시작합니다.
> `MULTI`는 상대가 참가할 때까지 `WAITING`입니다. 초대 링크는 `{클라이언트 주소}?session={sessionId}`입니다.
> 응답을 받으면 클라이언트는 바로 [STOMP](#stomp-ws)에 연결합니다. 10초 안에 연결하지 않으면 나간 것으로 처리됩니다(→ 재접속 유예).
>

**Errors:**

| Status | code | 설명 |
| --- | --- | --- |
| `400` | `VALIDATION_ERROR` | `mode`가 없거나 `SOLO`/`MULTI`가 아님 |

---

### POST /sessions/{sessionId}/join

> 초대 링크로 `MULTI` 세션에 참가합니다. 빈 자리(`PLAYER` → `ENEMY` 순)를 맡고, 두 명이 모이면 새 판이 시작됩니다.
>

**Auth Required:** No

**Path Params:**

| 파라미터 | 설명 |
| --- | --- |
| `sessionId` | 초대 링크의 세션 ID |

**Request Body:** 없음

**Response `201`:** `POST /sessions`와 같은 형식입니다. `session.status`는 `PLAYING`, `me.controls`는 맡은 캐릭터(`["ENEMY"]`)입니다.

> 이미 참가 중인 사람에게는 `PLAYER_JOINED` 이벤트가 전송됩니다.
> 나간 사람이 있던 방이면 비어 있는 자리(`PLAYER`일 수도 있음)를 맡습니다.
>

**Errors:**

| Status | code | 설명 |
| --- | --- | --- |
| `409` | `SESSION_FULL` | 이미 두 명이 참가 중이거나 `SOLO` 세션 |

---

### GET /sessions/{sessionId}

> 세션의 현재 상태(캐릭터별 상태와 대기 중인 동작, 참가자)를 조회합니다. 새로고침하거나 STOMP에 다시 연결한 뒤 화면을 복구할 때 호출합니다.
>

**Auth Required:** Yes (`X-Player-Token`)

**Path Params:**

| 파라미터 | 설명 |
| --- | --- |
| `sessionId` | 세션 ID |

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "session": {
      "id": "3f2a1c9e-7b1d-4c2e-9a51-0d6e8f4b2c10",
      "mode": "MULTI",
      "status": "PLAYING",
      "mapCols": 13,
      "mapRows": 9
    },
    "characters": [
      {
        "id": "a8c4e2b1-5f3d-4e7a-b6c9-1d2e3f4a5b6c",
        "type": "PLAYER",
        "x": 6,
        "y": 6,
        "facing": "N",
        "state": "ATTACKING",
        "hp": 100,
        "queue": [
          {
            "id": "c1d2e3f4-0000-4a1b-8c2d-3e4f5a6b7c8d",
            "seq": 4,
            "actionType": "attack",
            "status": "RUNNING",
            "targetX": null,
            "targetY": null,
            "blocked": false,
            "startedAt": "2026-10-15T12:00:03.120Z"
          },
          {
            "id": "d2e3f4a5-1111-4b2c-9d3e-4f5a6b7c8d9e",
            "seq": 5,
            "actionType": "defend",
            "status": "QUEUED",
            "targetX": null,
            "targetY": null,
            "blocked": false,
            "startedAt": null
          }
        ]
      },
      {
        "id": "b9d5f3c2-6a4e-4f8b-87da-2e3f4a5b6c7d",
        "type": "ENEMY",
        "x": 6,
        "y": 1,
        "facing": "S",
        "state": "IDLE",
        "hp": 100,
        "queue": []
      }
    ],
    "players": [
      { "controls": ["PLAYER"], "connected": true },
      { "controls": ["ENEMY"], "connected": false }
    ],
    "me": {
      "controls": ["PLAYER"]
    }
  }
}
```

> `players`는 참가자마다 맡은 캐릭터와 STOMP 연결 여부입니다. 토큰은 들어 있지 않습니다. `me`는 요청한 참가자이며 `playerToken`은 생략됩니다.
> `queue`는 캐릭터별로 `seq` 오름차순입니다. 실행 중인 동작이 있으면 첫 번째 항목의 `status`가 `RUNNING`입니다.
> 완료된 동작은 큐에서 제거되므로 포함되지 않습니다.
> `targetX`, `targetY`, `blocked`는 이동 동작(`move_*`, `roll`)이 실행을 시작할 때 정해집니다. `QUEUED` 상태에서는 `null`/`false`입니다.
>

**Errors:** 공통 Errors만 해당

---

### POST /sessions/{sessionId}/characters/{characterId}/commands

> 지정한 캐릭터에게 사용자가 입력한 텍스트를 보냅니다. 서버는 AI로 Action Set 중 가장 비슷한 Action **1개**를 찾아 그 캐릭터의 큐에 넣고, 매칭 결과를 돌려줍니다.
> 자기가 맡은 캐릭터(`me.controls`)에만 보낼 수 있고, 세션이 `PLAYING`일 때만 받습니다.
>

**Auth Required:** Yes (`X-Player-Token`)

**Path Params:**

| 파라미터 | 설명 |
| --- | --- |
| `sessionId` | 세션 ID |
| `characterId` | 명령을 받을 캐릭터 ID (내가 맡은 캐릭터) |

**Request Body:**

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `text` | string | Yes | 사용자가 입력한 원문 (1~500자, 앞뒤 공백 제거 후 기준) |

```json
{
  "text": "앞으로 가"
}
```

**Response `201`:**

```json
{
  "success": true,
  "data": {
    "characterId": "a8c4e2b1-5f3d-4e7a-b6c9-1d2e3f4a5b6c",
    "action": {
      "id": "e3f4a5b6-2222-4c3d-8e4f-5a6b7c8d9e0f",
      "seq": 6,
      "actionType": "move_forward",
      "status": "QUEUED",
      "targetX": null,
      "targetY": null,
      "blocked": false,
      "startedAt": null
    },
    "queueLength": 3
  }
}
```

> **응답은 "큐에 넣었다"는 뜻이지 "실행이 끝났다"는 뜻이 아닙니다.** 실제 실행 시작과 완료는 STOMP 이벤트(`ACTION_STARTED`, `ACTION_FINISHED`)로 알려줍니다.
> AI(aiserver, Jev)는 항상 Action Set 안에서 매칭하므로 "이해하지 못함" 응답은 없습니다. `오늘 날씨 좋다`처럼 명령이 아닌 말도 가장 비슷한 Action 1개로 처리됩니다. `앞으로 3칸`, `앞으로 가서 공격`처럼 반복하거나 이어 쓴 입력도 마찬가지입니다.
> AI 호출이 실패하거나 5초 안에 응답이 없으면 큐에 넣지 않고 에러(`502`/`504`)를 돌려줍니다. 서버는 자동으로 재시도하지 않습니다.
> 서버는 입력 원문을 보관하거나 돌려주지 않습니다. 화면에 표시할 문구는 클라이언트가 자신이 보낸 `text`를 `action.id`와 짝지어 직접 관리합니다.
> `queueLength`는 방금 넣은 동작과 실행 중인 동작을 포함한 해당 캐릭터의 큐 길이입니다.
>

**Errors:**

| Status | code | 설명 |
| --- | --- | --- |
| `400` | `VALIDATION_ERROR` | `text`가 없거나, 공백만 있거나, 500자를 넘음 |
| `403` | `NOT_YOUR_CHARACTER` | 내가 맡은 캐릭터가 아님 (`MULTI`에서 상대 캐릭터) |
| `404` | `CHARACTER_NOT_FOUND` | `characterId`가 이 세션의 캐릭터가 아님 |
| `409` | `WAITING_FOR_OPPONENT` | `MULTI`에서 아직 상대가 참가하지 않음 (`WAITING`) |
| `409` | `ROUND_OVER` | 한쪽이 쓰러져 판이 끝났고 자동 초기화를 기다리는 중 |
| `409` | `SESSION_ENDED` | 이미 종료된 세션 |
| `429` | `QUEUE_FULL` | 실행 중인 동작을 포함해 해당 캐릭터의 큐가 100개로 가득 참 |
| `502` | `AI_UNAVAILABLE` | aiserver 호출에 실패함 (연결 실패, 5xx 응답, Action Set에 없는 값 반환) |
| `504` | `AI_TIMEOUT` | aiserver가 5초 안에 응답하지 않음 |

---

### POST /sessions/{sessionId}/reset

> 두 캐릭터의 실행 중인 동작과 대기 중인 동작을 모두 지우고, 시작 상태(HP 100)로 되돌립니다. `SOLO`의 "초기화" 버튼에 해당합니다.
> `MULTI`는 수동 초기화가 없고, 판이 끝나면 자동으로만 초기화됩니다.
>

**Auth Required:** Yes (`X-Player-Token`)

**Path Params:**

| 파라미터 | 설명 |
| --- | --- |
| `sessionId` | 세션 ID |

**Request Body:** 없음

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "characters": [
      {
        "id": "a8c4e2b1-5f3d-4e7a-b6c9-1d2e3f4a5b6c",
        "type": "PLAYER",
        "x": 6,
        "y": 7,
        "facing": "N",
        "state": "IDLE",
        "hp": 100,
        "queue": []
      },
      {
        "id": "b9d5f3c2-6a4e-4f8b-87da-2e3f4a5b6c7d",
        "type": "ENEMY",
        "x": 6,
        "y": 1,
        "facing": "S",
        "state": "IDLE",
        "hp": 100,
        "queue": []
      }
    ]
  }
}
```

> 세션 ID와 캐릭터 ID는 그대로 유지됩니다. 초기화 후 `SESSION_RESET` 이벤트가 전송됩니다.
> `ROUND_OVER` 중에도 쓸 수 있으며, 이때 예약된 자동 초기화는 취소됩니다.
>

**Errors:**

| Status | code | 설명 |
| --- | --- | --- |
| `403` | `RESET_NOT_ALLOWED` | `MULTI` 세션 |
| `409` | `SESSION_ENDED` | 이미 종료된 세션 |

---

### POST /sessions/{sessionId}/leave

> 게임에서 나갑니다. 화면의 "게임 나가기" 버튼에 해당합니다. 나간 참가자의 토큰은 무효가 되고, 서버가 그 참가자의 STOMP 연결을 닫습니다.
>

**Auth Required:** Yes (`X-Player-Token`)

**Path Params:**

| 파라미터 | 설명 |
| --- | --- |
| `sessionId` | 세션 ID |

**Response `204`:** 본문 없음

> 남은 참가자가 없으면(`SOLO`, 또는 `MULTI`의 마지막 사람) 세션이 종료되고 `SESSION_ENDED`(`CLOSED`)가 전송됩니다.
> `MULTI`에서 한 명이 남으면 진행 중이던 판은 바로 중단되고, 두 캐릭터가 시작 상태로 돌아가며 세션은 `WAITING`이 됩니다. 남은 사람에게 `PLAYER_LEFT`(`LEFT`)가 전송되고, 같은 초대 링크로 새 상대를 받을 수 있습니다.
> 버튼을 누르지 않고 떠나도 연결이 끊긴 뒤 10초 안에 돌아오지 않으면 같은 방식으로 나간 것으로 처리됩니다(→ 재접속 유예).
>

**Errors:** 공통 Errors만 해당

---

### STOMP /ws

> 세션의 진행 상황을 실시간으로 받는 STOMP over WebSocket 채널입니다. v1에서는 서버 → 클라이언트 단방향으로만 사용하며, 명령 입력은 REST로 보냅니다.
>

**연결:** `ws(s)://{host}/ws` (SockJS 없음)

**CONNECT 헤더:**

| 헤더 | 설명 |
| --- | --- |
| `X-Session-Id` | 세션 ID |
| `X-Player-Token` | 참가자 토큰 |

**구독:** `/topic/sessions/{sessionId}` (자기 세션만 구독 가능)

> 하트비트는 10초 간격입니다(서버·클라이언트 모두). 끊긴 연결을 감지하는 데 쓰입니다.
> 클라이언트는 연결 직후 구독하고, `GET /sessions/{sessionId}`로 현재 상태를 맞춘 뒤 그사이 받은 이벤트를 적용합니다.
> 거절되면 서버가 `ERROR` 프레임을 보내고 연결을 닫습니다. `message` 헤더에 사유가, `code` 헤더에 REST와 같은 에러 코드가 들어 있습니다.
>

**재접속 유예:** 참가자의 연결이 모두 끊기면 `PLAYER_CONNECTION`(`connected: false`)이 전송되고, **10초** 안에 같은 토큰으로 다시 연결하지 않으면 나간 것으로 처리됩니다(`PLAYER_LEFT`, `reason: DISCONNECTED`). 새로고침처럼 바로 돌아오면 자리가 유지됩니다. 세션을 만들거나 참가한 뒤 10초 안에 처음 연결하지 않아도 같습니다.

**메시지 형식:**

```json
{
  "type": "ACTION_STARTED",
  "data": { }
}
```

**이벤트 목록:**

| type | 발생 시점 | 설명 |
| --- | --- | --- |
| `ACTION_STARTED` | 캐릭터 큐의 동작 실행이 시작될 때 | 시작 상태(`from`)와 결과 상태(`to`)를 보내고, 클라이언트는 `durationMs` 동안 보간해 애니메이션함 |
| `ACTION_FINISHED` | 동작 실행이 끝났을 때 | 완료된 동작 ID와 확정된 캐릭터 상태를 보냄 |
| `ATTACK_HIT` | 공격이 끝나며 상대에게 적중했을 때 | 공격자, 피해량, 방어 여부, 피해가 반영된 상대 상태를 보냄. 공격자의 `ACTION_FINISHED` 바로 뒤에 옴 |
| `ROUND_OVER` | 공격으로 한쪽이 쓰러졌을 때 | 승자·패자 캐릭터 ID와 자동 초기화 시각을 보냄. `ATTACK_HIT` 바로 뒤에 옴 |
| `SESSION_RESET` | 새 판이 시작될 때 (자동 초기화, `SOLO` 수동 초기화) | 세션 상태와 초기화된 두 캐릭터 상태를 보냄 |
| `PLAYER_JOINED` | `MULTI`에 상대가 참가했을 때 | 참가자가 맡은 캐릭터, 세션 상태, 새 판의 캐릭터 상태를 보냄 |
| `PLAYER_LEFT` | 참가자가 나갔을 때 (`MULTI`에 한 명이 남은 경우) | 나간 캐릭터, 사유, `WAITING` 세션 상태, 초기화된 캐릭터 상태를 보냄 |
| `PLAYER_CONNECTION` | 참가자의 연결이 생기거나 모두 끊겼을 때 | 맡은 캐릭터와 연결 여부를 보냄 |
| `SESSION_ENDED` | 세션이 종료되거나 만료됐을 때 | 이벤트 전송 후 서버가 연결을 닫음 |

**`ACTION_STARTED` 예시:**

```json
{
  "type": "ACTION_STARTED",
  "data": {
    "characterId": "a8c4e2b1-5f3d-4e7a-b6c9-1d2e3f4a5b6c",
    "action": {
      "id": "e3f4a5b6-2222-4c3d-8e4f-5a6b7c8d9e0f",
      "seq": 6,
      "actionType": "roll",
      "status": "RUNNING",
      "targetX": 6,
      "targetY": 5,
      "blocked": false,
      "startedAt": "2026-10-15T12:00:05.000Z"
    },
    "durationMs": 500,
    "from": { "x": 6, "y": 6, "facing": "N" },
    "to":   { "x": 6, "y": 5, "facing": "N" }
  }
}
```

**`ACTION_STARTED` 예시 (충돌로 막힘):**

```json
{
  "type": "ACTION_STARTED",
  "data": {
    "characterId": "a8c4e2b1-5f3d-4e7a-b6c9-1d2e3f4a5b6c",
    "action": {
      "id": "f4a5b6c7-3333-4d4e-9f5a-6b7c8d9e0f1a",
      "seq": 7,
      "actionType": "move_forward",
      "status": "RUNNING",
      "targetX": 6,
      "targetY": 2,
      "blocked": true,
      "startedAt": "2026-10-15T12:00:09.000Z"
    },
    "durationMs": 320,
    "from": { "x": 6, "y": 2, "facing": "N" },
    "to":   { "x": 6, "y": 2, "facing": "N" }
  }
}
```

**`ACTION_FINISHED` 예시:**

```json
{
  "type": "ACTION_FINISHED",
  "data": {
    "actionId": "e3f4a5b6-2222-4c3d-8e4f-5a6b7c8d9e0f",
    "character": {
      "id": "a8c4e2b1-5f3d-4e7a-b6c9-1d2e3f4a5b6c",
      "type": "PLAYER",
      "x": 6,
      "y": 5,
      "facing": "N",
      "state": "IDLE",
      "hp": 100
    },
    "queueLength": 0
  }
}
```

**`ATTACK_HIT` 예시:**

```json
{
  "type": "ATTACK_HIT",
  "data": {
    "attackerId": "a8c4e2b1-5f3d-4e7a-b6c9-1d2e3f4a5b6c",
    "actionId": "a5b6c7d8-4444-4e5f-8a6b-7c8d9e0f1a2b",
    "target": {
      "id": "b9d5f3c2-6a4e-4f8b-87da-2e3f4a5b6c7d",
      "type": "ENEMY",
      "x": 6,
      "y": 1,
      "facing": "S",
      "state": "MOVING",
      "hp": 90
    },
    "damage": 10,
    "defended": false
  }
}
```

> 적중 판정은 [erd.md](erd.md)의 "공격 판정 규칙"을 따릅니다. 빗나간 공격은 `ATTACK_HIT`를 보내지 않습니다.
> 상대가 마주 보고 방어 중이었으면 `defended: true`, `damage: 0`이고 `hp`는 그대로입니다.
> `target`의 `x`, `y`, `facing`은 확정된 값입니다. 상대가 동작 중이면 `state`는 그 동작 그대로이고, 클라이언트는 진행 중인 애니메이션을 유지한 채 `hp`만 반영합니다.
> `target.state`가 `DEAD`면 판이 끝난 것입니다. 바로 뒤에 `ROUND_OVER`가 옵니다.

**`ROUND_OVER` 예시:**

```json
{
  "type": "ROUND_OVER",
  "data": {
    "winnerId": "a8c4e2b1-5f3d-4e7a-b6c9-1d2e3f4a5b6c",
    "loserId": "b9d5f3c2-6a4e-4f8b-87da-2e3f4a5b6c7d",
    "resetAt": "2026-10-15T12:01:03.480Z",
    "delayMs": 3000
  }
}
```

> 판이 끝나면 두 캐릭터의 실행 중인 동작과 대기 동작이 모두 취소되고(취소된 동작의 `ACTION_FINISHED`는 오지 않음), 세션은 `ROUND_OVER`가 되어 명령을 받지 않습니다.
> `resetAt`에 두 캐릭터가 시작 상태(HP 100)로 돌아가며 `SESSION_RESET`이 옵니다. 클라이언트는 `resetAt`으로 카운트다운을 표시합니다.

**`SESSION_RESET` 예시:**

```json
{
  "type": "SESSION_RESET",
  "data": {
    "session": {
      "id": "3f2a1c9e-7b1d-4c2e-9a51-0d6e8f4b2c10",
      "mode": "MULTI",
      "status": "PLAYING",
      "mapCols": 13,
      "mapRows": 9
    },
    "characters": [
      {
        "id": "a8c4e2b1-5f3d-4e7a-b6c9-1d2e3f4a5b6c",
        "type": "PLAYER",
        "x": 6,
        "y": 7,
        "facing": "N",
        "state": "IDLE",
        "hp": 100
      },
      {
        "id": "b9d5f3c2-6a4e-4f8b-87da-2e3f4a5b6c7d",
        "type": "ENEMY",
        "x": 6,
        "y": 1,
        "facing": "S",
        "state": "IDLE",
        "hp": 100
      }
    ]
  }
}
```

> `PLAYER_JOINED`의 `data`는 `controls`(참가자가 맡은 캐릭터)가 추가된 것 말고는 `SESSION_RESET`과 같습니다.

**`PLAYER_LEFT` 예시:**

```json
{
  "type": "PLAYER_LEFT",
  "data": {
    "controls": ["ENEMY"],
    "reason": "DISCONNECTED",
    "session": {
      "id": "3f2a1c9e-7b1d-4c2e-9a51-0d6e8f4b2c10",
      "mode": "MULTI",
      "status": "WAITING",
      "mapCols": 13,
      "mapRows": 9
    },
    "characters": [ "…시작 상태의 두 캐릭터…" ]
  }
}
```

> `reason`은 `LEFT`(나가기 호출) 또는 `DISCONNECTED`(재접속 유예 초과)입니다.

**`PLAYER_CONNECTION` 예시:**

```json
{
  "type": "PLAYER_CONNECTION",
  "data": {
    "controls": ["ENEMY"],
    "connected": false
  }
}
```

**`SESSION_ENDED` 예시:**

```json
{
  "type": "SESSION_ENDED",
  "data": {
    "reason": "EXPIRED"
  }
}
```

> `reason`은 `CLOSED`(마지막 참가자가 나감) 또는 `EXPIRED`(30분 유휴 만료)입니다.
> 이동 목표 칸이 맵 경계를 넘으면 `to`는 경계 칸입니다.
> 목표 칸에 다른 캐릭터가 있거나 다른 캐릭터가 이동 중인 칸이면 `blocked: true`이고 `to`는 `from`과 같습니다. 이때도 동작 시간(`durationMs`)은 그대로 소요됩니다.
> 연결이 끊겼다가 다시 연결하면 `GET /sessions/{sessionId}`로 현재 상태를 먼저 맞춥니다.
>

**Errors:**

| 상황 | `ERROR` 프레임 `code` |
| --- | --- |
| CONNECT의 `X-Session-Id`가 없거나 만료된 세션 | `SESSION_NOT_FOUND` |
| CONNECT의 `X-Player-Token`이 없거나 이 세션의 참가자가 아님 | `INVALID_PLAYER_TOKEN` |
| 다른 세션의 토픽 구독, `SEND` 프레임 | `STOMP_NOT_ALLOWED` |

---

## 부록: 코드 값

| 필드 | 값 |
| --- | --- |
| `session.mode` | `SOLO`, `MULTI` |
| `session.status` | `WAITING`, `PLAYING`, `ROUND_OVER`, `ENDED` |
| `character.type` | `PLAYER`, `ENEMY` |
| `character.facing` | `N`, `E`, `S`, `W` |
| `character.state` | `IDLE`, `MOVING`, `TURNING`, `ATTACKING`, `DEFENDING`, `ROLLING`, `DEAD` |
| `character.hp` | 0 ~ 100. 시작과 초기화 시 100 |
| `players[].controls`, `me.controls` | `PLAYER`, `ENEMY` 중 맡은 캐릭터 (`SOLO`는 둘 다) |
| `action.actionType` | `move_forward`, `move_backward`, `turn_left`, `turn_right`, `attack`, `defend`, `roll` (`aiserver/actions.json`의 키) |
| `action.status` | `QUEUED`, `RUNNING` |

Action별 동작 시간, 이동 규칙, 공격 판정 규칙, 판·참가 규칙, AI 호출 규칙, 캐릭터 시작 상태는 [erd.md](erd.md)의 "4. 코드 고정값"을 따른다.

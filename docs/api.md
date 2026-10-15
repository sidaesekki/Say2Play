# Say2Play API 명세 v1 (싱글플레이)

> 기준 문서: [erd.md](erd.md)

## 공통

- Base URL: `/api/v1`
- 사용자 계정이 없으므로 로그인 인증은 없다. 클라이언트는 `POST /sessions`로 받은 `sessionId`로 이후 요청을 보낸다.
- 모든 게임 상태는 서버 메모리에 있다. 서버를 재시작하거나 세션이 만료되면 상태가 사라진다.
- 세션은 마지막 입력(`lastActiveAt`) 이후 30분이 지나면 만료된다.
- 클라이언트는 명령을 REST로 보내고, 동작 진행 상황은 WebSocket으로 받는다. 이후 멀티플레이에서는 같은 WebSocket 채널로 세션 내 모든 플레이어에게 브로드캐스트한다.
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
| `404` | `SESSION_NOT_FOUND` | `sessionId`에 해당하는 세션이 없거나 만료됨 |
| `500` | `INTERNAL_ERROR` | 서버 내부 오류 |

---

### POST /sessions

> 새 게임 세션을 만들고 플레이어 1명을 입장시킵니다. 게임 화면에 처음 들어올 때 호출합니다.
>

**Auth Required:** No

**Request Body:** 없음

**Response `201`:**

```json
{
  "success": true,
  "data": {
    "session": {
      "id": "3f2a1c9e-7b1d-4c2e-9a51-0d6e8f4b2c10",
      "status": "PLAYING",
      "mapCols": 13,
      "mapRows": 9
    },
    "player": {
      "id": "a8c4e2b1-5f3d-4e7a-b6c9-1d2e3f4a5b6c",
      "x": 6,
      "y": 4,
      "facing": "N",
      "state": "IDLE"
    }
  }
}
```

> 플레이어는 맵 중앙 `(6, 4)`에서 북쪽을 바라본 상태로 시작합니다.
> 응답을 받으면 클라이언트는 바로 [WebSocket](#ws-wssessionssessionid)에 연결합니다.
>

**Errors:** 공통 Errors만 해당

---

### GET /sessions/{sessionId}

> 세션의 현재 상태(플레이어, 대기 중인 동작)를 조회합니다. 새로고침하거나 WebSocket에 다시 연결한 뒤 화면을 복구할 때 호출합니다.
>

**Auth Required:** No

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
      "status": "PLAYING",
      "mapCols": 13,
      "mapRows": 9
    },
    "player": {
      "id": "a8c4e2b1-5f3d-4e7a-b6c9-1d2e3f4a5b6c",
      "x": 6,
      "y": 3,
      "facing": "E",
      "state": "ATTACKING"
    },
    "queue": [
      {
        "id": "c1d2e3f4-0000-4a1b-8c2d-3e4f5a6b7c8d",
        "seq": 4,
        "actionType": "ATTACK",
        "direction": null,
        "label": "칼 휘둘러",
        "status": "RUNNING",
        "startedAt": "2026-10-15T12:00:03.120Z"
      },
      {
        "id": "d2e3f4a5-1111-4b2c-9d3e-4f5a6b7c8d9e",
        "seq": 5,
        "actionType": "DEFEND",
        "direction": null,
        "label": "막아",
        "status": "QUEUED",
        "startedAt": null
      }
    ]
  }
}
```

> `queue`는 `seq` 오름차순입니다. 실행 중인 동작이 있으면 첫 번째 항목의 `status`가 `RUNNING`입니다.
> 완료된 동작은 큐에서 제거되므로 포함되지 않습니다.
>

**Errors:** 공통 Errors만 해당

---

### POST /sessions/{sessionId}/commands

> 사용자가 입력한 텍스트를 보냅니다. 서버는 AI로 Action Set 중 가장 비슷한 Action **1개**를 찾아 큐에 넣고, 매칭 결과를 돌려줍니다.
>

**Auth Required:** No

**Path Params:**

| 파라미터 | 설명 |
| --- | --- |
| `sessionId` | 세션 ID |

**Request Body:**

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `text` | string | Yes | 사용자가 입력한 원문 (1~500자, 앞뒤 공백 제거 후 기준) |

```json
{
  "text": "오른쪽으로 한 발짝 가볼까"
}
```

**Response `201`:**

```json
{
  "success": true,
  "data": {
    "action": {
      "id": "e3f4a5b6-2222-4c3d-8e4f-5a6b7c8d9e0f",
      "seq": 6,
      "actionType": "MOVE",
      "direction": "E",
      "label": "오른쪽으로 한 발짝 가볼까",
      "status": "QUEUED",
      "startedAt": null
    },
    "queueLength": 3
  }
}
```

> **응답은 "큐에 넣었다"는 뜻이지 "실행이 끝났다"는 뜻이 아닙니다.** 실제 실행 시작과 완료는 WebSocket 이벤트(`ACTION_STARTED`, `ACTION_FINISHED`)로 알려줍니다.
> AI는 항상 Action Set 안에서 매칭하므로 "이해하지 못함" 응답은 없습니다. `위로 3칸`, `위로 가서 공격`처럼 반복하거나 이어 쓴 입력도 Action 1개로 처리됩니다.
> `queueLength`는 방금 넣은 동작과 실행 중인 동작을 포함한 큐 길이입니다.
>

**Errors:**

| Status | code | 설명 |
| --- | --- | --- |
| `400` | `VALIDATION_ERROR` | `text`가 없거나, 공백만 있거나, 500자를 넘음 |
| `409` | `SESSION_ENDED` | 이미 종료된 세션 |
| `429` | `QUEUE_FULL` | 실행 중인 동작을 포함해 큐가 100개로 가득 참 |
| `502` | `AI_UNAVAILABLE` | AI 서버 호출에 실패했거나 응답 시간이 초과됨 |

---

### POST /sessions/{sessionId}/reset

> 실행 중인 동작과 대기 중인 동작을 모두 지우고, 플레이어를 시작 위치로 되돌립니다. 화면의 "초기화" 버튼에 해당합니다.
>

**Auth Required:** No

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
    "player": {
      "id": "a8c4e2b1-5f3d-4e7a-b6c9-1d2e3f4a5b6c",
      "x": 6,
      "y": 4,
      "facing": "N",
      "state": "IDLE"
    },
    "queue": []
  }
}
```

> 세션 ID와 플레이어 ID는 그대로 유지됩니다. 초기화 후 WebSocket으로 `SESSION_RESET` 이벤트가 전송됩니다.
>

**Errors:**

| Status | code | 설명 |
| --- | --- | --- |
| `409` | `SESSION_ENDED` | 이미 종료된 세션 |

---

### DELETE /sessions/{sessionId}

> 세션을 종료하고 메모리에서 상태를 지웁니다. 사용자가 게임을 나갈 때 호출합니다.
>

**Auth Required:** No

**Path Params:**

| 파라미터 | 설명 |
| --- | --- |
| `sessionId` | 세션 ID |

**Response `204`:** 본문 없음

> 호출하지 않고 나가더라도 30분 동안 입력이 없으면 세션이 자동으로 만료됩니다.
> 종료 직전에 WebSocket으로 `SESSION_ENDED` 이벤트가 전송되고 연결이 닫힙니다.
>

**Errors:** 공통 Errors만 해당

---

### WS /ws/sessions/{sessionId}

> 세션의 동작 진행 상황을 실시간으로 받는 WebSocket 채널입니다. v1에서는 서버 → 클라이언트 단방향으로만 사용하며, 명령 입력은 REST로 보냅니다.
>

**Auth Required:** No

**Path Params:**

| 파라미터 | 설명 |
| --- | --- |
| `sessionId` | 세션 ID |

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
| `ACTION_STARTED` | 큐의 동작 실행이 시작될 때 | 시작 상태(`from`)와 결과 상태(`to`)를 보내고, 클라이언트는 `durationMs` 동안 보간해 애니메이션함 |
| `ACTION_FINISHED` | 동작 실행이 끝났을 때 | 완료된 동작 ID와 확정된 플레이어 상태를 보냄 |
| `SESSION_RESET` | 초기화했을 때 | 초기화된 플레이어 상태를 보냄 |
| `SESSION_ENDED` | 세션이 종료되거나 만료됐을 때 | 이벤트 전송 후 서버가 연결을 닫음 |

**`ACTION_STARTED` 예시:**

```json
{
  "type": "ACTION_STARTED",
  "data": {
    "action": {
      "id": "e3f4a5b6-2222-4c3d-8e4f-5a6b7c8d9e0f",
      "seq": 6,
      "actionType": "ROLL",
      "direction": null,
      "label": "앞으로 굴러",
      "status": "RUNNING",
      "startedAt": "2026-10-15T12:00:05.000Z"
    },
    "playerId": "a8c4e2b1-5f3d-4e7a-b6c9-1d2e3f4a5b6c",
    "durationMs": 500,
    "from": { "x": 6, "y": 4, "facing": "E" },
    "to":   { "x": 8, "y": 4, "facing": "E" }
  }
}
```

**`ACTION_FINISHED` 예시:**

```json
{
  "type": "ACTION_FINISHED",
  "data": {
    "actionId": "e3f4a5b6-2222-4c3d-8e4f-5a6b7c8d9e0f",
    "player": {
      "id": "a8c4e2b1-5f3d-4e7a-b6c9-1d2e3f4a5b6c",
      "x": 8,
      "y": 4,
      "facing": "E",
      "state": "IDLE"
    },
    "queueLength": 0
  }
}
```

**`SESSION_RESET` 예시:**

```json
{
  "type": "SESSION_RESET",
  "data": {
    "player": {
      "id": "a8c4e2b1-5f3d-4e7a-b6c9-1d2e3f4a5b6c",
      "x": 6,
      "y": 4,
      "facing": "N",
      "state": "IDLE"
    }
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

> `reason`은 `CLOSED`(DELETE 호출) 또는 `EXPIRED`(30분 유휴 만료)입니다.
> 이동 결과가 맵 경계를 넘으면 `to`는 경계 칸입니다. 이동하지 못해도 동작 시간(`durationMs`)은 그대로 소요됩니다.
> 연결이 끊겼다가 다시 연결하면 `GET /sessions/{sessionId}`로 현재 상태를 먼저 맞춥니다.
>

**Errors:**

| 상황 | 처리 |
| --- | --- |
| `sessionId`가 없거나 만료됨 | 연결 수립 단계에서 `404`로 거절 |

---

## 부록: 코드 값

| 필드 | 값 |
| --- | --- |
| `session.status` | `PLAYING`, `ENDED` |
| `player.facing` | `N`, `E`, `S`, `W` |
| `player.state` | `IDLE`, `MOVING`, `TURNING`, `ATTACKING`, `DEFENDING`, `ROLLING` |
| `action.actionType` | `MOVE`, `TURN`, `ATTACK`, `DEFEND`, `ROLL` |
| `action.direction` | `MOVE`: `N`/`E`/`S`/`W`, `TURN`: `LEFT`/`RIGHT`, 그 외 `null` |
| `action.status` | `QUEUED`, `RUNNING` |

Action별 동작 시간과 효과는 [erd.md](erd.md)의 "4. 코드 고정값"을 따른다.

# Say2Play API 명세 v1 (싱글플레이)

> 기준 문서: [erd.md](erd.md)

## 공통

- Base URL: `/api/v1`
- 사용자 계정이 없으므로 로그인 인증은 없다. 클라이언트는 `POST /sessions`로 받은 `sessionId`로 이후 요청을 보낸다.
- 모든 게임 상태는 서버 메모리에 있다. 서버를 재시작하거나 세션이 만료되면 상태가 사라진다.
- 세션 하나에 캐릭터가 2개(`PLAYER`, `ENEMY`) 있고, 한 사용자가 두 캐릭터에 각각 명령을 보낸다.
- 세션은 마지막 입력(`lastActiveAt`) 이후 30분이 지나면 만료된다.
- 클라이언트는 명령을 REST로 보내고, 동작 진행 상황은 WebSocket으로 받는다. 이후 멀티플레이에서는 같은 WebSocket 채널로 세션 내 모든 클라이언트에 브로드캐스트한다.
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

> 새 게임 세션을 만들고 캐릭터 2개(플레이어, 적)를 배치합니다. 게임 화면에 처음 들어올 때 호출합니다.
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

> 플레이어는 아래쪽 `(6, 7)`에서 북쪽을, 적은 위쪽 `(6, 1)`에서 남쪽을 바라본 상태로 시작합니다.
> 이후 명령을 보낼 때는 각 캐릭터의 `id`를 사용합니다.
> 응답을 받으면 클라이언트는 바로 [WebSocket](#ws-wssessionssessionid)에 연결합니다.
>

**Errors:** 공통 Errors만 해당

---

### GET /sessions/{sessionId}

> 세션의 현재 상태(캐릭터별 상태와 대기 중인 동작)를 조회합니다. 새로고침하거나 WebSocket에 다시 연결한 뒤 화면을 복구할 때 호출합니다.
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
    ]
  }
}
```

> `queue`는 캐릭터별로 `seq` 오름차순입니다. 실행 중인 동작이 있으면 첫 번째 항목의 `status`가 `RUNNING`입니다.
> 완료된 동작은 큐에서 제거되므로 포함되지 않습니다.
> `targetX`, `targetY`, `blocked`는 이동 동작(`move_*`, `roll`)이 실행을 시작할 때 정해집니다. `QUEUED` 상태에서는 `null`/`false`입니다.
>

**Errors:** 공통 Errors만 해당

---

### POST /sessions/{sessionId}/characters/{characterId}/commands

> 지정한 캐릭터에게 사용자가 입력한 텍스트를 보냅니다. 서버는 AI로 Action Set 중 가장 비슷한 Action **1개**를 찾아 그 캐릭터의 큐에 넣고, 매칭 결과를 돌려줍니다.
>

**Auth Required:** No

**Path Params:**

| 파라미터 | 설명 |
| --- | --- |
| `sessionId` | 세션 ID |
| `characterId` | 명령을 받을 캐릭터 ID (플레이어 또는 적) |

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

> **응답은 "큐에 넣었다"는 뜻이지 "실행이 끝났다"는 뜻이 아닙니다.** 실제 실행 시작과 완료는 WebSocket 이벤트(`ACTION_STARTED`, `ACTION_FINISHED`)로 알려줍니다.
> AI(aiserver, Jev)는 항상 Action Set 안에서 매칭하므로 "이해하지 못함" 응답은 없습니다. `오늘 날씨 좋다`처럼 명령이 아닌 말도 가장 비슷한 Action 1개로 처리됩니다. `앞으로 3칸`, `앞으로 가서 공격`처럼 반복하거나 이어 쓴 입력도 마찬가지입니다.
> AI 호출이 실패하거나 5초 안에 응답이 없으면 큐에 넣지 않고 에러(`502`/`504`)를 돌려줍니다. 서버는 자동으로 재시도하지 않습니다.
> 서버는 입력 원문을 보관하거나 돌려주지 않습니다. 화면에 표시할 문구는 클라이언트가 자신이 보낸 `text`를 `action.id`와 짝지어 직접 관리합니다.
> `queueLength`는 방금 넣은 동작과 실행 중인 동작을 포함한 해당 캐릭터의 큐 길이입니다.
>

**Errors:**

| Status | code | 설명 |
| --- | --- | --- |
| `400` | `VALIDATION_ERROR` | `text`가 없거나, 공백만 있거나, 500자를 넘음 |
| `404` | `CHARACTER_NOT_FOUND` | `characterId`가 이 세션의 캐릭터가 아님 |
| `409` | `SESSION_ENDED` | 이미 종료된 세션 |
| `409` | `CHARACTER_DEAD` | HP가 0이 되어 쓰러진 캐릭터 (초기화해야 다시 명령 가능) |
| `429` | `QUEUE_FULL` | 실행 중인 동작을 포함해 해당 캐릭터의 큐가 100개로 가득 참 |
| `502` | `AI_UNAVAILABLE` | aiserver 호출에 실패함 (연결 실패, 5xx 응답, Action Set에 없는 값 반환) |
| `504` | `AI_TIMEOUT` | aiserver가 5초 안에 응답하지 않음 |

---

### POST /sessions/{sessionId}/reset

> 두 캐릭터의 실행 중인 동작과 대기 중인 동작을 모두 지우고, 시작 위치로 되돌립니다. 화면의 "초기화" 버튼에 해당합니다.
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

> 세션 ID와 캐릭터 ID는 그대로 유지됩니다. 초기화 후 WebSocket으로 `SESSION_RESET` 이벤트가 전송됩니다.
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

> 세션 내 두 캐릭터의 동작 진행 상황을 실시간으로 받는 WebSocket 채널입니다. v1에서는 서버 → 클라이언트 단방향으로만 사용하며, 명령 입력은 REST로 보냅니다.
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
| `ACTION_STARTED` | 캐릭터 큐의 동작 실행이 시작될 때 | 시작 상태(`from`)와 결과 상태(`to`)를 보내고, 클라이언트는 `durationMs` 동안 보간해 애니메이션함 |
| `ACTION_FINISHED` | 동작 실행이 끝났을 때 | 완료된 동작 ID와 확정된 캐릭터 상태를 보냄 |
| `ATTACK_HIT` | 공격이 끝나며 상대에게 적중했을 때 | 공격자, 피해량, 방어 여부, 피해가 반영된 상대 상태를 보냄. 공격자의 `ACTION_FINISHED` 바로 뒤에 옴 |
| `SESSION_RESET` | 초기화했을 때 | 초기화된 두 캐릭터 상태를 보냄 |
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
> `target.state`가 `DEAD`면 상대의 실행 중인 동작과 대기 동작이 모두 취소된 것입니다. 취소된 동작의 `ACTION_FINISHED`는 오지 않습니다.

**`SESSION_RESET` 예시:**

```json
{
  "type": "SESSION_RESET",
  "data": {
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
> 이동 목표 칸이 맵 경계를 넘으면 `to`는 경계 칸입니다.
> 목표 칸에 다른 캐릭터가 있거나 다른 캐릭터가 이동 중인 칸이면 `blocked: true`이고 `to`는 `from`과 같습니다. 이때도 동작 시간(`durationMs`)은 그대로 소요됩니다.
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
| `character.type` | `PLAYER`, `ENEMY` |
| `character.facing` | `N`, `E`, `S`, `W` |
| `character.state` | `IDLE`, `MOVING`, `TURNING`, `ATTACKING`, `DEFENDING`, `ROLLING`, `DEAD` |
| `character.hp` | 0 ~ 100. 시작과 초기화 시 100 |
| `action.actionType` | `move_forward`, `move_backward`, `turn_left`, `turn_right`, `attack`, `defend`, `roll` (`aiserver/actions.json`의 키) |
| `action.status` | `QUEUED`, `RUNNING` |

Action별 동작 시간, 이동 규칙, 공격 판정 규칙, AI 호출 규칙, 캐릭터 시작 상태는 [erd.md](erd.md)의 "4. 코드 고정값"을 따른다.

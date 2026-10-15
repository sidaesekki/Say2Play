const base = (import.meta.env?.VITE_API_BASE_URL || '/api/v1').replace(/\/$/, '');
export class ApiError extends Error {
  constructor(message, code) { super(message); this.code = code; }
}
export async function request(path, { method = 'GET', body, token, signal } = {}) {
  const headers = {...(body !== undefined ? {'Content-Type':'application/json'} : {}), ...(token ? {'X-Player-Token':token} : {})};
  const response = await fetch(`${base}${path}`, {
    method, signal: signal || AbortSignal.timeout(10000), headers,
    ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
  });
  if (response.status === 204) return null;
  let result;
  try { result = await response.json(); }
  catch { throw new ApiError('서버 응답을 읽을 수 없습니다. API 서버 주소를 확인해 주세요.', 'INVALID_RESPONSE'); }
  if (!response.ok || !result.success) throw new ApiError(result.error?.message || '요청에 실패했습니다.', result.error?.code);
  return result.data;
}
// STOMP endpoint. Events arrive on /topic/sessions/{sessionId}.
export function brokerUrl() {
  const url = new URL(import.meta.env?.VITE_WS_BASE_URL || '/ws', window.location.origin);
  url.protocol = url.protocol === 'https:' || url.protocol === 'wss:' ? 'wss:' : 'ws:';
  return url.href;
}
export const sessionPath = id => `/sessions/${encodeURIComponent(id)}`;
export const topic = id => `/topic/sessions/${id}`;
export const inviteUrl = id => { const url = new URL(window.location.href); url.search = `?session=${encodeURIComponent(id)}`; url.hash = ''; return url.href; };
// Speech-to-text server (aiserver/voice_server.py), proxied under /stt.
const sttBase = (import.meta.env?.VITE_STT_BASE_URL || '/stt').replace(/\/$/, '');
export async function sttAvailable() {
  try { return (await fetch(`${sttBase}/health`, { signal: AbortSignal.timeout(3000) })).ok; } catch { return false; }
}
export async function transcribe(wav) {
  let response;
  try { response = await fetch(`${sttBase}/v1/stt`, { method: 'POST', headers: { 'Content-Type': 'audio/wav' }, body: wav, signal: AbortSignal.timeout(15000) }); }
  catch { throw new ApiError('음성 인식 서버에 연결하지 못했습니다.', 'STT_UNAVAILABLE'); }
  const result = await response.json().catch(() => ({}));
  if (!response.ok) throw new ApiError(response.status === 400 ? (result.detail || '음성 형식이 올바르지 않습니다.') : '음성 인식 서버에 연결하지 못했습니다.', response.status === 400 ? 'STT_BAD_AUDIO' : 'STT_UNAVAILABLE');
  return result.text || '';
}

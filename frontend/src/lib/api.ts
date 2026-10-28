/** 백엔드 API 를 부르는 fetch 래퍼. 오류 응답은 { message } 이므로 그 문장을 그대로 던진다 */
export async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`/api${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init?.headers },
  })
  if (!res.ok) {
    const body = await res.json().catch(() => null)
    throw new Error(body?.message ?? '잠시 후 다시 시도해 주세요.')
  }
  return res.json() as Promise<T>
}

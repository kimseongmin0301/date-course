// 브라우저 저장소. 막혀 있거나(사생활 보호 모드 등) 깨져 있어도 화면은 그대로 돌게 실패는 무시한다
export function readJson<T>(key: string, fallback: T): T {
  try {
    const raw = window.localStorage.getItem(key)
    return raw ? (JSON.parse(raw) as T) : fallback
  } catch {
    return fallback
  }
}

export function writeJson(key: string, value: unknown) {
  try {
    if (value == null) window.localStorage.removeItem(key)
    else window.localStorage.setItem(key, JSON.stringify(value))
  } catch {
    // 저장 못 해도 이번 화면에서는 쓸 수 있다
  }
}

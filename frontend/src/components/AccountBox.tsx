import { useEffect, useState, type FormEvent } from 'react'
import { api } from '../lib/api'

interface AccountStatus {
  linked: boolean
  code: string | null
}

type Mode = 'idle' | 'create' | 'login' | 'delete'

/**
 * 다른 기기에서도 내 일정 보기 (고를 때만). 이메일·전화 없이 비밀번호를 정하면 코드를 받고,
 * 다른 기기에서 코드 + 비밀번호로 로그인하면 그 브라우저에 저장된 일정도 계정으로 합쳐진다.
 * 로그인하지 않으면 지금처럼 이 브라우저(쿠키)에만 저장한다. onChanged: 로그인·로그아웃 뒤 목록을 다시 불러오게
 */
export default function AccountBox({ onChanged }: { onChanged: () => void }) {
  const [status, setStatus] = useState<AccountStatus | null>(null)
  const [mode, setMode] = useState<Mode>('idle')
  const [code, setCode] = useState('')
  const [password, setPassword] = useState('')
  const [password2, setPassword2] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  useEffect(() => {
    api<AccountStatus>('/account')
      .then(setStatus)
      .catch(() => setStatus({ linked: false, code: null }))
  }, [])

  function open(next: Mode) {
    setMode(next)
    setError(null)
    setMessage(null)
    setPassword('')
    setPassword2('')
  }

  async function create(e: FormEvent) {
    e.preventDefault()
    if (password !== password2) {
      setError('두 비밀번호가 달라요.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      const res = await api<AccountStatus>('/account', { method: 'POST', body: JSON.stringify({ password }) })
      setStatus(res)
      setMode('idle')
      setMessage(`코드 ${res.code} 를 만들었어요. 코드와 비밀번호를 꼭 적어 두세요(잊으면 되찾을 수 없어요).`)
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setBusy(false)
    }
  }

  async function login(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const res = await api<{ code: string; moved: number }>('/account/login', { method: 'POST', body: JSON.stringify({ code, password }) })
      setStatus({ linked: true, code: res.code })
      setMode('idle')
      setCode('')
      setMessage(res.moved > 0 ? `로그인했어요. 이 브라우저에 있던 일정 ${res.moved}개도 함께 저장했어요.` : '로그인했어요.')
      onChanged()
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setBusy(false)
      setPassword('')
    }
  }

  /** 코드 지우기 (비밀번호 확인). 일정은 이 브라우저에 남는다 */
  async function remove(e: FormEvent) {
    e.preventDefault()
    if (!window.confirm('코드를 지울까요? 다른 기기에서는 더 이상 로그인할 수 없어요. 일정은 이 브라우저에 남아요.')) return
    setBusy(true)
    setError(null)
    try {
      await api<void>('/account', { method: 'DELETE', body: JSON.stringify({ password }) })
      setStatus({ linked: false, code: null })
      setMode('idle')
      setMessage('코드를 지웠어요. 일정은 이 브라우저에 그대로 있어요.')
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setBusy(false)
      setPassword('')
    }
  }

  async function logout() {
    if (!window.confirm('이 브라우저에서 로그아웃할까요? 일정은 계정에 남아 있고, 코드로 다시 로그인하면 보여요.')) return
    try {
      await api<void>('/account/logout', { method: 'POST' })
      setStatus({ linked: false, code: null })
      setMessage('로그아웃했어요.')
      onChanged()
    } catch (err) {
      setError((err as Error).message)
    }
  }

  if (!status) return null

  return (
    <div className="account-box">
      {status.linked ? (
        <>
          <p>
            <strong>다른 기기에서도 보기</strong> · 코드 <span className="account-code">{status.code}</span>
          </p>
          <p className="muted small">다른 기기의 "내 저장"에서 이 코드와 비밀번호로 로그인하면 같은 일정이 보여요.</p>
          <div className="account-actions">
            <button type="button" className="link-button small" onClick={logout}>
              이 브라우저에서 로그아웃
            </button>
            {mode !== 'delete' && (
              <button type="button" className="link-button small" onClick={() => open('delete')}>
                코드 지우기
              </button>
            )}
          </div>
          {mode === 'delete' && (
            <form className="account-form" onSubmit={remove}>
              <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="비밀번호" autoComplete="current-password" maxLength={64} required aria-label="비밀번호" />
              <div className="account-actions">
                <button type="submit" className="secondary small-button" disabled={busy}>
                  {busy ? '지우는 중…' : '코드 지우기'}
                </button>
                <button type="button" className="link-button small" onClick={() => open('idle')}>
                  취소
                </button>
              </div>
            </form>
          )}
        </>
      ) : (
        <>
          <p>
            <strong>다른 기기에서도 보기</strong> <span className="muted small">(선택)</span>
          </p>
          <p className="muted small">
            지금은 이 브라우저에만 저장돼요. 비밀번호를 정하면 코드를 드려요. 이메일·전화번호는 받지 않아요.
          </p>
          {mode === 'idle' && (
            <div className="account-actions">
              <button type="button" className="secondary small-button" onClick={() => open('create')}>
                코드 만들기
              </button>
              <button type="button" className="secondary small-button" onClick={() => open('login')}>
                코드로 로그인
              </button>
            </div>
          )}
          {mode === 'create' && (
            <form className="account-form" onSubmit={create}>
              <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="비밀번호 (6자 이상)" autoComplete="new-password" minLength={6} maxLength={64} required aria-label="비밀번호" />
              <input type="password" value={password2} onChange={(e) => setPassword2(e.target.value)} placeholder="비밀번호 한 번 더" autoComplete="new-password" minLength={6} maxLength={64} required aria-label="비밀번호 확인" />
              <div className="account-actions">
                <button type="submit" className="primary small-button" disabled={busy}>
                  {busy ? '만드는 중…' : '코드 만들기'}
                </button>
                <button type="button" className="link-button small" onClick={() => open('idle')}>
                  취소
                </button>
              </div>
            </form>
          )}
          {mode === 'login' && (
            <form className="account-form" onSubmit={login}>
              <input value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} placeholder="코드 (예: K7QM-29XD)" autoComplete="username" autoCapitalize="characters" maxLength={9} required aria-label="코드" />
              <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="비밀번호" autoComplete="current-password" maxLength={64} required aria-label="비밀번호" />
              <p className="muted small">로그인하면 이 브라우저에 저장된 일정도 계정에 함께 저장돼요.</p>
              <div className="account-actions">
                <button type="submit" className="primary small-button" disabled={busy}>
                  {busy ? '확인 중…' : '로그인'}
                </button>
                <button type="button" className="link-button small" onClick={() => open('idle')}>
                  취소
                </button>
              </div>
            </form>
          )}
        </>
      )}
      {error && <p className="notice small">{error}</p>}
      {message && <p className="small account-message">{message}</p>}
    </div>
  )
}

import { api } from './api'
import type { Keyword, KeywordKind } from './types'

// 키워드 목록은 거의 안 바뀌어서 한 번만 받아 둔다
let loading: Promise<Keyword[]> | null = null

export function loadKeywords(): Promise<Keyword[]> {
  if (!loading) {
    loading = api<Keyword[]>('/keywords').catch((e) => {
      loading = null
      throw e
    })
  }
  return loading
}

export const KIND_LABEL: Record<KeywordKind, string> = {
  mood: '분위기',
  activity: '활동',
  setting: '실내외',
  budget: '예산 (2인)',
}

/** 실내외·예산은 하나만 고른다 */
export const SINGLE_KINDS: KeywordKind[] = ['setting', 'budget']

export const MAX_KEYWORDS = 3

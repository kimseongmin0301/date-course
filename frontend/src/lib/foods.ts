/**
 * 메뉴 (AI 검색의 "햄버거", "초밥"). 서버 course/util/Foods 와 같은 코드.
 * name: 화면에 보일 이름, search: 코스 상세에서 그 가게를 찾을 때 쓸 말
 */
export const FOODS: Record<string, { name: string; search: string }> = {
  burger: { name: '햄버거', search: '버거' },
  pizza: { name: '피자', search: '피자' },
  pasta: { name: '파스타·스테이크', search: '파스타' },
  sushi: { name: '초밥·회', search: '초밥' },
  meat: { name: '고기', search: '고기' },
  chicken: { name: '치킨', search: '치킨' },
  gopchang: { name: '곱창', search: '곱창' },
  seafood: { name: '해산물', search: '해물' },
  noodle: { name: '면 요리', search: '국수' },
  asian: { name: '아시아 음식', search: '쌀국수' },
  chinese: { name: '중식', search: '중식' },
  korean: { name: '한식', search: '한정식' },
  katsu: { name: '돈가스·덮밥', search: '돈가스' },
  snack: { name: '분식', search: '분식' },
  bakery: { name: '빵·디저트', search: '빵' },
  brunch: { name: '브런치', search: '브런치' },
}

/** 모르는 코드나 null 은 null */
export function foodOf(code: string | null | undefined) {
  return code ? (FOODS[code] ?? null) : null
}

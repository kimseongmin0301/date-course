// 인기 지역 바로가기. 지역 이름 검색은 Geocoding(유료)을 쓰지 않으려고 이 목록과 지도 옮기기로 대신한다
export interface Area {
  name: string
  lat: number
  lng: number
  radius: number
}

export const AREAS: Area[] = [
  { name: '강남역', lat: 37.4979, lng: 127.0276, radius: 2000 },
  { name: '성수', lat: 37.5445, lng: 127.0559, radius: 2000 },
  { name: '홍대', lat: 37.5563, lng: 126.9236, radius: 2000 },
  { name: '익선동·종로', lat: 37.574, lng: 126.9895, radius: 2000 },
  { name: '잠실', lat: 37.5133, lng: 127.1001, radius: 2000 },
  { name: '여의도', lat: 37.5216, lng: 126.9243, radius: 2000 },
  { name: '인천 개항장', lat: 37.474, lng: 126.6199, radius: 2000 },
  { name: '부산 해운대', lat: 35.1631, lng: 129.1636, radius: 3000 },
  { name: '부산 광안리', lat: 35.1532, lng: 129.1186, radius: 2000 },
  { name: '대구 동성로', lat: 35.8693, lng: 128.5951, radius: 2000 },
  { name: '대전 은행동', lat: 36.329, lng: 127.429, radius: 2000 },
  { name: '전주 한옥마을', lat: 35.815, lng: 127.153, radius: 2000 },
  { name: '경주 황리단길', lat: 35.8384, lng: 129.2108, radius: 2000 },
  { name: '강릉', lat: 37.7717, lng: 128.9475, radius: 4000 },
  { name: '제주시', lat: 33.4996, lng: 126.5312, radius: 4000 },
]

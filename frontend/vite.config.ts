import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// 개발할 때 /api 요청은 백엔드(Spring Boot, 18290)로 넘긴다. 운영에서는 nginx 가 같은 일을 한다
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:18290',
    },
  },
})

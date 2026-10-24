import path from 'node:path'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(import.meta.dirname, './src'),
    },
  },
  // Like nginx in production: the dashboard and its backend endpoints share one origin, so the session
  // cookie works without CORS. The bot's /api is not forwarded.
  server: {
    proxy: {
      '/auth': 'http://localhost:8080',
      '/web': 'http://localhost:8080',
    },
  },
})

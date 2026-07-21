import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { fileURLToPath, URL } from 'node:url'

// Le front appelle les APIs via le gateway Traefik (localhost:8081) et MailDev (localhost:1080).
// Le proxy Vite évite tout problème de CORS : le navigateur ne parle qu'à localhost:5173.
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/api/, ''),
      },
      '/maildev-api': {
        target: 'http://localhost:1080',
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/maildev-api/, ''),
      },
    },
  },
})

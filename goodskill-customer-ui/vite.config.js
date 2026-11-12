import {defineConfig} from 'vite'
import vue from '@vitejs/plugin-vue'
import {viteMockServe} from 'vite-plugin-mock'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    viteMockServe({
      mockPath: './src/mock',
      localEnabled: true,
      prodEnabled: false
    })
  ],
  server: {
    host: '0.0.0.0',
    port: 5174,
    proxy: {
      '/ticket-api': {
        target: 'http://127.0.0.1:54870',
        changeOrigin: true,
        rewrite: path => path.replace(/^\/ticket-api/, '')
      },
      '/order-api': {
        target: 'http://127.0.0.1:19081',
        changeOrigin: true,
        rewrite: path => path.replace(/^\/order-api/, '')
      },
      '/api': {
        target: 'http://localhost',
        changeOrigin: true,
        secure: false
      }
    }
  }
})

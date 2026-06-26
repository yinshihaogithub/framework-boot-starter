import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  build: {
    chunkSizeWarningLimit: 1000,
    rollupOptions: {
      output: {
        manualChunks: {
          vue: ['vue'],
          element: ['element-plus', '@element-plus/icons-vue'],
          axios: ['axios']
        }
      }
    }
  },
  server: {
    proxy: {
      '/admin': {
        target: 'http://localhost:8081',
        changeOrigin: true
      },
      '/actuator': {
        target: 'http://localhost:8081',
        changeOrigin: true
      }
    }
  }
})

import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const target = env.API_PROXY_TARGET || 'http://localhost:8080';
  const stt = env.STT_PROXY_TARGET || 'http://localhost:8002';
  return { plugins: [react()], server: { proxy: {
    '/api': { target, changeOrigin: true },
    '/ws': { target, changeOrigin: true, ws: true },
    '/stt': { target: stt, changeOrigin: true, rewrite: path => path.replace(/^\/stt/, '') },
  } } };
});

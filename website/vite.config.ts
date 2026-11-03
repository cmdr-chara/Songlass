import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
export default defineConfig(({mode})=>({
  plugins: [react()],
  base: loadEnv(mode, '.', 'SONGLASS_').SONGLASS_BASE ?? '/',
  server: { host: '127.0.0.1', port: 5173 },
  preview: { host: '127.0.0.1', port: 4173 },
  build: { rollupOptions: { output: { manualChunks(id) {
    if (id.includes('node_modules/gsap')) return 'animation';
  } } } },
}));

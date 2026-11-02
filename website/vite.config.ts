import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
export default defineConfig(({mode})=>({
  plugins: [react()],
  base: loadEnv(mode, '.', 'SONGLASS_').SONGLASS_BASE ?? '/',
  server: { host: '127.0.0.1', port: 5173 },
  preview: { host: '127.0.0.1', port: 4173 },
  build: { chunkSizeWarningLimit: 1100, rollupOptions: { output: { manualChunks(id) {
    if (/node_modules\/(three|@react-three|three-stdlib|maath|@pmndrs)/.test(id)) return 'scene';
    if (id.includes('node_modules/gsap')) return 'animation';
    if (id.includes('node_modules/simple-liquid-glass')) return 'glass';
  } } } },
}));

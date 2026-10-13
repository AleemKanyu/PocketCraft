import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import path from 'path';
import {defineConfig, loadEnv} from 'vite';

export default defineConfig(({mode, isSsrBuild}) => {
  loadEnv(mode, '.', '');
  return {
    plugins: [react(), tailwindcss()],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, '.'),
      },
    },
    build: {
      // The prerender build (--ssr) keeps these packages external, so they cannot be chunked there.
      rollupOptions: isSsrBuild
        ? {}
        : {
            output: {
              manualChunks: {
                'vendor-react': ['react', 'react-dom', 'react-router-dom'],
                'vendor-motion': ['framer-motion'],
                'vendor-icons': ['lucide-react'],
              },
            },
          },
    },
    server: {
      // HMR is disabled in AI Studio via DISABLE_HMR env var.
      // Do not modify—file watching is disabled to prevent flickering during agent edits.
      hmr: process.env.DISABLE_HMR !== 'true',
    },
  };
});

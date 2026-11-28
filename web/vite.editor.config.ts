import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

/** Builds the editor page that the Android app bundles in its assets (loaded in a WebView, offline). */
export default defineConfig({
  base: './',
  publicDir: false,
  plugins: [react()],
  build: {
    outDir: process.env.EDITOR_OUT ?? '../app/src/main/assets/editor',
    emptyOutDir: true,
    rollupOptions: { input: 'editor.html' },
  },
})

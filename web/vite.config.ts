import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'
import pkg from './package.json' with { type: 'json' }

// GitHub Pages serves the app under /<repo>/; set VITE_BASE=/ for a custom domain.
const base = process.env.VITE_BASE ?? '/schedule-based-notebook-app/'

export default defineConfig({
  base,
  define: { __APP_VERSION__: JSON.stringify(pkg.version) },
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['icon.svg', 'apple-touch-icon.png'],
      manifest: {
        name: 'Not Takvimi',
        short_name: 'Not Takvimi',
        description: 'Takvim tabanlı not uygulaması',
        lang: 'tr',
        start_url: base,
        scope: base,
        display: 'standalone',
        background_color: '#f7f7f8',
        theme_color: '#3b6fe0',
        icons: [
          { src: 'icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: 'icon-512.png', sizes: '512x512', type: 'image/png' },
          { src: 'icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: {
        // Only the app shell is cached; Google APIs always go to the network.
        globPatterns: ['**/*.{js,css,html,svg,png}'],
        navigateFallback: 'index.html',
        navigateFallbackDenylist: [/^\/__/],
      },
    }),
  ],
})

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { NaiveUiResolver } from 'unplugin-vue-components/resolvers'
import { fileURLToPath, URL } from 'node:url'

// https://vite.dev/config/
export default defineConfig({
  // naive-ui on-demand: NaiveUiResolver auto-imports only the components each
  // template actually uses (incl. App.vue's providers/layout that previously
  // relied on the global `app.use(naive)` registration). Removing the global
  // registration lets Vite tree-shake unused components (Calendar/DatePicker/
  // Transfer/Cascader/date-fns …) out of the entry chunk. Composables
  // (useMessage/useDialog) are already explicitly imported where used.
  plugins: [vue(), Components({ dts: 'src/components.d.ts', resolvers: [NaiveUiResolver()] })],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:9300',
        changeOrigin: true,
      },
    },
  },
  build: {
    rollupOptions: {
      output: {
        // NFR-3: split the heavy viz library (xterm) into its own vendor chunk so
        // it stays out of the entry / first-screen bundle. Rollup only EMITS a
        // manualChunk when its modules are actually imported somewhere — after
        // the entry-side registration was removed, this chunk is reachable
        // only via the lazy-loaded RunDetail route, so it loads on demand.
        // (ECharts was removed — the resource view is now a plain table.)
        // Matchers are scoped to node_modules paths so app code is unaffected.
        manualChunks(id) {
          if (!id.includes('node_modules/')) return
          if (/node_modules\/@xterm\//.test(id)) return 'xterm'
        },
      },
    },
  },
})

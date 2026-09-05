import { createApp } from 'vue'
import { createPinia } from 'pinia'

// Entry kept dependency-light for NFR-3 (first-screen initial download):
// naive-ui is imported ON-DEMAND — components used in templates are auto-resolved
// by NaiveUiResolver (vite.config.ts), so the full library is NOT registered
// globally and Vite tree-shakes unused components out of the entry chunk.
// xterm CSS lives in LogViewer.vue (lazy RunDetail route). ResourceChart is a
// plain table now (echarts removed). See plan 2026-06-30-2202-1.
import App from './App.vue'
import router from './router'
import './style.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')

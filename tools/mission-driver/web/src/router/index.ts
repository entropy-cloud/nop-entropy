import { createRouter, createWebHistory } from 'vue-router'

// FSD §4.1 — history mode. Both views are lazy-loaded so the initial bundle
// stays small (NFR-3 / NFR-6): RunDetail pulls the xterm chunk, and its
// renderer registry pulls Vue Flow chunks on demand for non-default renderers.
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'run-list',
      component: () => import('@/views/RunList.vue'),
    },
    {
      path: '/runs/:runId',
      name: 'run-detail',
      component: () => import('@/views/RunDetail.vue'),
      props: true,
    },
    {
      // P6 Context & Memory Explorer (FSD §3.5). Lazy-loaded so the three
      // panels' Naive UI chunks stay out of the RunList/RunDetail bundles.
      path: '/context',
      name: 'context-explorer',
      component: () => import('@/views/ContextExplorer.vue'),
    },
  ],
})

export default router

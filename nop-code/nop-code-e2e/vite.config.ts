import { defineConfig } from 'vite';

export default defineConfig({
  // Vite 用于辅助开发：类型检查、未来可扩展 Vitest 单元测试
  // E2E 测试本身由 Playwright 直接执行，不需要 Vite dev server
  build: {
    outDir: 'dist',
  },
});

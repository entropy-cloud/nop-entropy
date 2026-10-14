# E2E 测试开发者指南（本目录已废弃）

> **本文档已废弃，请勿在此基础上编写或调试测试。**

原 `01-e2e-developer-guide.md` 是 **nop-chaos-next 上游手册的副本**，其中关于
「amis/flux 引擎切换」（`E2E_ENGINE`）、`PLAYWRIGHT_APP_MODE` 三种应用模式、
`test:e2e:amis-prototype` 等内容属于 nop-chaos-next 的 mock/prototype 测试形态，
在 nop-entropy-e2e 中**并不存在**；其中「必须访问 4173 前端、不能直接访问后端端口」
的规则也与本项目实际形态不符（nop-entropy-e2e 直连后端托管的 SPA 静态资源）。

这些内容已经过时且与实际代码不一致，本副本把它们一并删除，避免继续误导。

## 当前有效文档

| 主题 | 位置 |
|------|------|
| nop-entropy-e2e 的架构、PageObject 写法、共享库 API 速查 | [`../../README.md`](../../README.md) |
| 上游 nop-chaos-next 的 E2E 手册（mock/prototype/extension-dev 形态） | `~/app/nop-chaos-next/docs/testing/01-e2e-developer-guide.md` |
| 上游 e2e-shared 共享库源码（唯一上游，经 `sync-e2e-shared.sh` 下发） | `~/app/nop-chaos-next/packages/e2e-shared/` |
| 常见失败模式与根因排障 | `docs-for-ai/02-core-guides/e2e-testing-troubleshooting.md` |
| E2E 测试平台使用约定 | `docs-for-ai/02-core-guides/e2e-testing.md` |

## 当前形态要点（速记）

- **单引擎 flux**：`AmisAdapter`、`E2E_ENGINE`、`test:amis` 脚本均已删除；
  `playwright.config.ts` 恒定传 `-Dnop.web.render-mode=flux`。
- **spec 只写业务意图**：不得出现 DOM 选择器、控件类型、引擎分支；业务一律以
  `fieldName` 和业务方法名表达，定位/填充/等待/重试全部收口在页面对象。
- **必须串行跑**：根 `pnpm test` 使用 `--workspace-concurrency=1`，四个
  `mvn quarkus:dev` 并行会争抢固定 JDWP 端口。
- **端口冲突**：默认 8080/8081/8082/8083，被占用时用 `PORT=xxxx` 覆盖；
  不覆盖会被 `reuseExistingServer` 复用成错误进程，表现为 404 `File Not Found`。

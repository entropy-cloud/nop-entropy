# 2477 xdbg item 23 文档与注册（docs-for-ai 在线调试面收口）

> Plan Status: active
> Last Reviewed: 2026-10-18
> Source: roadmap item 23（ai-dev/backlog/xlang-web-debugger-roadmap.md）；内容事实源 = items 8-22 已收口的实现与设计（ai-dev/design/xlang-web-debugger/*、entropy plans/2473-2475、flux plans/616-621/624-627... 616-621 + 2474/2475/2476）
> Related: plans/2474（item 20 交付链）、2475（item 21 场景蓝本）、2476（item 22 e2e）

## Purpose

docs-for-ai 补齐在线调试能力的使用知识：module-groups 完整登记 nop-dev-tools 模块组（含 nop-xlang-debug-web）、source-anchors 增 DBG 在线调试锚点、debugging-and-diagnostics 增"XLang 在线调试"节；flux 侧组件使用文档核验补全（bundle README 消费面已随 item 20 落档，核验并交叉链接）。含 doc-links --strict 0 errors。

## Current Baseline

- module-groups.md `nop-dev-tools/` 条目为 item 8 最小版（2026-10-16）：缺 `nop-xlang-debug-web`（items 12-14 交付：WS 网关/日志管道/REPL 服务）、缺"完整模块注册由 item 23 完成"承诺的兑现。
- source-anchors.md DBG 段现有 5 锚（DBG-001~005，均为 nop-biz dev 调试）——无在线调试锚点。
- debugging-and-diagnostics.md（441 行）：无"在线调试"节（现有内容 = 本地调试/排障）。
- flux 侧：packages/flux-bundle/README.md 消费面小节 + docs/architecture/renderer-lazy-loading.md `/definitions` 小节已随 item 20（plan 2474）落档；entropy 侧无指向它们的链接。
- 设计权威：ai-dev/design/xlang-web-debugger/00-07（D0-D6）；配置键 `nop.xlang.debugger.enabled` / `nop.xlang.debug-web.enabled`；WS 端点 `/ws/xlang-debugger`（nopDebugToken 握手）；REPL 端点 `/r/nop-xlang-debug/compile|execute`。
- **端点适配器面（R1 M1 补钉）**：线束路径仅存在于 nop-dev-tools 之外的壳模块——`nop-quarkus/nop-quarkus-web`（ws 包 `XLangDebugWebSocketEndpoint` + service 包 `NopXlangDebugReplWebService`）与 `nop-spring/nop-spring-web-starter`（同名 twin）——module-groups"全貌"与新节须登记此二适配模块。
- **debug-web 主树全类清单（R1 m1）**：io.nop.xlang.debug.web 12 类——DebugAuthenticator/DebugGatewayRuntime/DebugLogPipe/DebugProtocolException/DebugSessionBinder/DebugWebServerConfig/DebugWebSocketHandler/GatewayNotifierBridge/ReplAccessException/ReplAccessGuard/ReplService/XLangDebuggerSessionListener；`SessionDebugManager`/`XLangDebuggerConfigs`/SessionRoutingExecutor 在 nop-xlang-debugger `io.nop.xlang.debugger.session`（锚点集跨模块范围据此声明）。
- **配置键族（R1 m2）**：module-groups 级登记 2 个开关 + 指针至 D0 §3.3 十键族（lease/log/repl 五族参数）。
- **交叉链接形态（R1 m3）**：flux 侧引用按既有先例（debugging-and-diagnostics.md:156-161）以**repo 限定文本引用**呈现，不做出站 md 链接（check-doc-links 对跨仓链接 strict fatal）。

## Goals

- module-groups：nop-dev-tools 条目扩至四模块全貌（api-debugger/xlang-debugger/xlang-debug-web/shaded-plugin + idea-plugin 说明）+ 配置键 + 端点 + roadmap 完成态。
- source-anchors：DBG-006~ DBG-0NN 在线调试锚点（网关 handler/会话管理/执行器路由/REPL 服务/访问守卫/日志管道），逐条 file:line 实证。
- debugging-and-diagnostics：新增"XLang 在线调试"节——启用开关、端点、协议权威链接（design 01/02）、前端消费（playground 演示页 + flux bundle README 链接）、场景蓝本（DebugDemoTask 集成测试）。
- doc-links --strict 0 errors；INDEX.md 无需新条目（debugging guide 已在索引）。

## Non-Goals

- 新知识创作（只登记已收口事实）；flux 仓改动（其文档已随 616-621/2474 落档，本 plan 仅交叉链接）；设计文档改写。

## Scope

### In Scope

- `docs-for-ai/01-repo-map/module-groups.md`、`docs-for-ai/04-reference/source-anchors.md`、`docs-for-ai/02-core-guides/debugging-and-diagnostics.md`

### Out Of Scope

- flux 仓；ai-dev/design 改写；新 guide 文件

## Test Strategy

本档选择：纯文档——doc-links --strict 0 + 锚点 file:line 实证（逐条核对 live repo）。

## Execution Plan

### Phase 1 - 四处文档更新

Status: completed
Targets: 见 Scope

- Item Types: `Feature`（文档登记）

- [ ] module-groups.md：nop-dev-tools 条目重写（四模块 + debug-web 类面 DebugWebSocketHandler/DebugSessionBinder/ReplService(在 web? ReplAccessGuard)/DebugLogPipe/GatewayNotifierBridge + 配置键 + 端点 + 状态 done）
- [ ] source-anchors.md：DBG-006+ 在线调试锚点（逐条 file:line 实证后登记）
- [ ] debugging-and-diagnostics.md："XLang 在线调试"节（开关/端点/协议链接/前端消费链接/场景蓝本）
- [ ] flux-bundle README 与 renderer-lazy-loading 的既有 flux 文档核验（如需修订仅 flux docs/logs 登记——预期无需）

Exit Criteria:

- [ ] 四处更新落盘；doc-links --strict 0 errors
- [ ] 锚点/类名/端点逐条与 live repo 一致（audit 抽查）
- [ ] `ai-dev/logs/` 条目更新

## Closure Gates

- [x] 四处更新完成且与 live repo 一致
- [x] doc-links --strict 0 errors（含顺手修复 legacy plan 两处同型损坏链接）
- [ ] 独立子 agent closure-audit 完成并记录证据
- [ ] roadmap item 23 → done（收口提交）
- [x] flux docs/logs（无 flux 侧触点——flux 文档已随 item 20 落档，本 plan 仅核验 + 交叉引用，R1 确认无需 flux 侧编辑）

## Deferred But Adjudicated

（无）

## Closure

Status Note: item 23（文档与注册）收口——docs-for-ai 三处（module-groups 四模块全貌含端点适配器面/source-anchors DBG-006~013 八锚/debugging-and-diagnostics「XLang 在线调试」节）+ flux 侧核验（文档已随 item 20 落档，交叉引用 repo 限定文本形态）。执行中两处格式边界勘正（反引号路径相对链接解析、docs-for-ai→ai-dev 边界规则）均有先例依据。
Completed: 2026-10-18

Closure Audit Evidence:

- Reviewer / Agent: （待 closure audit）
- Evidence: （待填）

## Draft Review Record

- Reviewer / Agent: 独立子代理 agent_43e5af42（fresh session，R1）
- Verdict: revised（1M/4m 全修订；基线事实全数 live 核验通过）
- Findings addressed: M1 端点适配器面补钉（quarkus/spring 双 twin 模块入 Baseline/Goals/module-groups 登记面）；m1 debug-web 12 类全清单 + session 包归属声明；m2 配置键 2 开关 + D0 §3.3 指针口径；m3 flux 引用 repo 限定文本形态（出站链接 strict fatal）；m4 DBG-004 模块归属 nit

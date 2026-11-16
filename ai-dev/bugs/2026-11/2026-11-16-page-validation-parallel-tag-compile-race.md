# 2026-11-16 并行页面验证触发 xlib 标签编译单飞竞态（NPE fn==null，demo 启动失败，nop-web-e2e 阻塞）

> Status: 已定位、已临时规避（demo 配置回退串行）、根因修复待 plan-first（nop-xlang protected area）

## Problem

- `nop-web-e2e` 3 用例全失败：demo 应用（nop-quarkus-demo）启动失败 → `GET /` 非 2xx、扩展资源 404、登录表单不渲染。
- 启动失败点：`PageModelValidator.validateAllPages` → `PageProvider.getPage(/nop/ai/pages/NopAiChannelSession/*.page.yaml)` → `x:gen-extends` 执行 `<web:GenPage>` → `LazyCompiledExecutableFunction.execute:51` NPE `fn == null`（`ExecutableFunction.getSlotNames()`）。
- demo 配置 `nop.web.page-validation-thread-count: 4`（平台缺省 1）→ 4 线程并发加载页面 → 并发编译同一 xlib 标签函数。

## Diagnostic Method

- e2e 失败证据：断言消息补 `error=`/`completed=` → `error=null completed=true` = 裸 onComplete（不是错误路径）→ 应用没起来。
- webServer 段日志：`Attempting to start live reload endpoint to recover from previous Quarkus startup failure` + classloader `restart no:0..3` → quarkus:dev 对启动失败的恢复循环（每 ~3s 重启一次）。
- 致命异常定位：`nop.err.ioc.start-failed` ← `PageModelValidator` @PostConstruct ← NopEvalException(call-func-fail, NPE fn==null)。
- 决定性对照实验：`-Dnop.web.page-validation-thread-count=1` → **启动成功**，GET / 200、`/extensions/example-extension-demo/assets/index.js` 200、`data-nop-extension` 锚点在 → e2e 3/3 通过。
- 排除项：`quarkus.dev.watch.enabled` 为无效键（Quarkus 4.0.0.Beta1 日志明示 Unrecognized/ignored，与重启循环无关——循环根因是启动失败恢复，不是文件监视）；H2 残留库的 `add-tenant-col-fail`（重复列）为 WARN 非致命。
- 触发面溯源：该配置 2026-08-08 加入（bd0ea0e4de）；单飞重入路径来自 plan 2504（6a4d0183bf，2026-10-26）；最后一次 e2e 通过记录为 10-16 → 回归窗口与 plan 2504 吻合，与本次会话改动无关（本次改动不触及 xlang/web/page 面面）。

## Root Cause（两层）

1. **直接缺陷**：`XplLibTagCompiler.LazyCompiledFunction.compile()` 的同线程重入路径（`compilingThread == currentThread` → 立即 return）返回后 `compiledFn` 仍为 null，而 `LazyCompiledExecutableFunction.execute()` 经 `getCompiled()` 取 null 后直接 `fn.getSlotNames()` → NPE。对照：同类的 `invoke()` 路径对 `compiledFn == null` 有类型化守卫（`ERR_XPL_TAG_FUNC_IS_COMPILING`）——`execute()` 路径缺失同等守卫。
2. **未定环**：编译同线程重入的触发链未完全钉死（`collectDependsTo` 必执行回调、`compileFunction` 恒返非 null、`awaitCompiledByOther` 超时会抛类型化错误——三条静态排除后，剩余疑点在 `ResourceCacheEntry.executeLoadWithMtimeGuard`/依赖收集与标签编译的交叉重入，需在可观测环境下进一步取证）。

## Fix（临时规避，非根因修复）

- `nop-demo/nop-quarkus-demo/src/main/resources/application.yaml`：`page-validation-thread-count: 4 → 1`（回退到平台缺省的串行验证），注释注明原因。
- e2e 恢复：nop-web-e2e 3/3 通过（合成满载之外的条件即可复现，串行后稳定）。

## Tests

- 既有钉：`TestStreamCancelDeadlineAndDelivery_C3_001` 等 13 例（nop-ai-core）不受影响。
- 缺口：xlang 单飞竞态无并发回归钉（nop-xlang 单测未覆盖「多线程并发编译同一标签函数」）——**根因修复时必须补**（建议形态：N 线程并发 `getCompiledFn()` 同一标签 × M 轮，断言零 NPE 且全部拿到同一编译产物；变异探针：撤掉单飞守卫必红）。

## Affected Files

- `nop-demo/nop-quarkus-demo/src/main/resources/application.yaml`（临时规避）

## Notes For Future Refactors（根因修复待办，plan-first）

- 修复面 1（对称守卫）：`LazyCompiledExecutableFunction.execute()/visit()` 对 `getCompiled() == null` 抛 `ERR_XPL_TAG_FUNC_IS_COMPILING`（与 `LazyCompiledFunction.invoke()` 同型），消除 NPE。
- 修复面 2（根因）：钉死同线程重入的触发链后修单飞语义——重入期间 `execute()` 应等待编译完成（同线程无法 wait，需重构为「编译与执行同线程互斥窗口」或让编译期重入返回占位执行体）。
- 修复面 3（可选）：`PageModelValidator.validateAllPages` 并行验证失败应聚合报错并携带 pagePath 上下文（当前 NPE 直接炸掉 @PostConstruct，定位成本高）。
- 修复后：demo `page-validation-thread-count` 可恢复 4 并以满载 e2e 验证。
- 锁序注意：`LazyCompiledFunction` 监视器 → 消费者侧监视器为既有单向锁序（见 `BoundedStreamPublisher` 同期分析），改动单飞语义时不得引入反向边。

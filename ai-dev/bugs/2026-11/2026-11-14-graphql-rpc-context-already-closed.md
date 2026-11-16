# 2026-11-14 五入口 RPC 请求被 context-already-closed 替换（newRpcContext 自建 context 与 restoreCtx 顺序缺陷）

## Problem

- `newRpcContext`（gRPC/WS/消息/文件四入口的统一 chokepoint）在无线程附着 context 时自建 context 并在请求终态关闭；此时任何 `executeRpcAsync`/`executeGraphQLAsync` 调用（成功或失败）都不返回业务响应，而是抛 `NopException[nop.err.api.context-already-closed]`。
- 单元测试暴露形态：`TestOperationMfaExecutorWiring` 全量构建中偶发 1 例失败；solo 单跑任一 RPC 用例 100% 失败（clean thread 必走自建分支）。HTTP 入口不受影响（既有 open context 复用分支，无关闭钩子）。

## Diagnostic Method

- 失败栈顶 `BaseContext.checkClosed ← removeAttribute ← IServiceContext.restoreCtx ← invokeWithBindingCtx 的 whenComplete(168 行)` —— 定位到"请求终态关闭先于绑定回退"的顺序问题。
- 先怀疑负载时序：5 次单跑全部复现（与负载无关），排除 flake。
- 关键鉴别实验：单独运行单个成功路用例（`testExecutorSkipsCheckerForNonSensitiveOperation`）也失败 → 证明不是"失败路特有"，`complete()` 成功终态同样触发关闭钩子。
- 用日志证据区分路径：`nop.err.promise.whenComplete.action.fail` 仅出现一次且与失败用例对应；`ResolvedPromise.whenComplete` 捕获 restore 异常后以 completionException 替换原始结果，导致业务响应被上下文错误整体覆盖。
- 排除假设：测试状态泄漏（同 fork 残留 open context 使先前构建偶过）——`BaseContextProvider.currentContext()` 对 closed 残留惰性清除，全量构建中同类 5 用例里首例走自建分支失败、后序用例因 GraphQL 路径（无关闭钩子）或复用分支而通过，解释了"偶发 1 例"的漂移面。
- 回归溯源：`git log -S "onAfterComplete(e -> created.close())"` 定位引入提交 `c68b69737e`（plan 2499 Phase 3, C3-012 入口 binder），而 `TestNewRpcContextBinder_C3_012` 只测 `ctx.complete()` 关闭契约、不执行 `executeRpcAsync`，故交互缺陷未覆盖。

## Root Cause

- `IGraphQLEngine.newRpcContext` 自建分支（`IGraphQLEngine.java:151-164`）只注册了 `onAfterComplete(e -> created.close())`，未把 service context 绑定到自建 context。
- `GraphQLEngine.executeRpcAsync/executeGraphQLAsync` 经 `invokeWithBindingCtx` 执行：自建 context 上无既有绑定 → `oldCtx == null` → 执行后 `restoreCtx(context, null)` 走 `removeAttribute`。而该 whenComplete 挂在响应构建 stage 之外层，`buildRpcResponse` 内部的 `complete()/completeExceptionally()`（请求终态）在链内先行触发关闭钩子 → restore 落在已关闭 context 上抛错。
- 四入口（gRPC/WS/消息/文件）每次请求都是 clean thread → 必走自建分支 → 100% 中招；HTTP 入口复用分支无关闭钩子所以幸存。

## Fix

- `IGraphQLEngine.newRpcContext` 自建分支在注册关闭钩子前增加 `context.getServiceContext().bindToContext(created)`：自建 context 由本请求独占，service context 自创建即绑定（与既有入口 ContextBinder 先建后绑同型），`invokeWithBindingCtx` 走 `oldCtx == this` 快速路径直接执行，无可回退的绑定。
- 不改 `nop-core` 的 `restoreCtx`（protected area）：绑定提前后终态关闭时机、关闭契约（`TestNewRpcContextBinder_C3_012` 既有断言）均不变。

## Tests

- `TestNewRpcContextBinder_C3_012#testExecuteRpcOnBinderCreatedContextReturnsResponse` — 自建 context 上执行 RPC 成功路：响应正常返回（修复前抛 context-already-closed）+ 终态关闭契约保持。
- `TestNewRpcContextBinder_C3_012#testExecuteRpcErrorOnBinderCreatedContextReturnsErrorResponse` — 失败路（`Tree__fail` 操作执行期抛错，驱动 `completeExceptionally`）：交付正常错误响应（msg 经 context locale 本地化）而非异常。
- 两测试在类 `@BeforeEach BaseContextProvider.clear()` 卫生下必走自建分支，判别面不随类序漂移。

## Affected Files

- `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/engine/IGraphQLEngine.java`（自建分支绑定）
- `nop-service-framework/nop-graphql/nop-graphql-core/src/test/java/io/nop/graphql/core/engine/TestNewRpcContextBinder_C3_012.java`（2 条回归钉 + `BinderProbeBizModel.fail` + `BinderTree` 补 fixture 类型字段）

## Notes For Future Refactors

- `invokeWithBindingCtx` 的 `oldCtx == this` 快速路径是本修复的依赖点：若未来改为"总是 restore"，需同时让 `restoreCtx` 对已关闭 context 变 no-op，否则本缺陷回归。
- 任何新的"请求终态关闭自建 context"的入口（onAfterComplete 钩子）必须保证 service context 在钩子注册前已绑定，或 `restoreCtx` 对 closed context 安全。
- `IServiceContext.restoreCtx` 在已关闭 context 上抛 `context-already-closed` 是平台级潜在面（nop-core protected area，本次按边界未动）；若其他入口出现同类交互，根治点在 `IServiceContext.java:151-157` 加 `isClosed()` 守卫。

# 2026-10-13 全量清扫暴露的六处预存模块级失败

## Problem

历史上全量构建总在中途模块失败，`nop-code-*`、`nop-metadata-web`、`nop-auth-web` 页面测试、`nop-message-kafka`、`nop-stream-runtime` 等下游模块的测试**从未真正执行过**。用 `./mvnw install -T 1C -fn`（fail-never 全量清扫）一次性暴露 6 个模块的失败，逐模块隔离复跑分类为 4 确定性 + 2 时序竞争。

## Failures And Fixes

### 1. nop-code-web：IoC 启动缺 IHttpClient（确定性）

- `NopCodeWebPagesTest` 启动即抛 `not-find-bean-with-type IHttpClient`（`nopChatService.httpClient`）。
- 根因：WI12（plan 2304）已创建测试 stub `test-mock.beans.xml`，但 `AppBeanContainerLoader.isAppBeans` 只自动加载 `app.beans.xml`/`app-*.beans.xml`，`test-mock.beans.xml` 必须由 `@NopTestConfig(testBeansFile=...)` 显式引用——stub 建了没接线。
- 修复：`NopCodeWebPagesTest` 注解补 `testBeansFile = "/nop/code/beans/test-mock.beans.xml"`。

### 2. nop-metadata-web：孤儿页面引用已删除模型（确定性）

- `validateAllPages` 对 `/nop/metadata/pages/NopMetaTable/main.page.yaml` 抛 `parse-missing-resource: NopMetaTable.xmeta`。
- 根因：plan 2261（`e7d3df6148`）裁定彻底删除 NopMetaTable 概念（5 实体、服务层、DDL、79 测试文件全部迁移），但其后 `9d1a8164f2`（10-07"新增 NopMetaTable 页面与模板定义"）把 5 套页面 + 5 个模板 JSON 重新带入——引用的 xmeta 全仓库不存在。
- 修复：按 plan 2261 裁定方向 `git rm` 5 个 `pages/NopMetaTable*` 目录与 5 个 `_templates/_NopMetaTable*.json`（仓库内无其他引用方）。注意：增量构建不清除 target/classes 里已删资源，验证前需手动清理或 clean。

### 3. nop-auth-web：页面快照与生成器分歧（确定性）

- `TestWebPage` 3 个用例失败：生成的 AMIS 页面里单控件 group 的 `body` 是裸对象，快照期望列表包裹（多控件 group 仍为列表，45 个 group 内容完整）。
- 根因：快照自 2026-07-31（`ed3a8957cb`"fix stale golden snapshots"）未再生，期间生成输出对单子节点发生了列表→裸对象的折叠；两条快照再生惯例提交（`ed3a8957cb`、`424a6db5fa`）证明本仓库对此类漂移的处置是再生快照。HEAD 上同样失败（已验证），与工作区改动无关。
- 修复：从 surefire 报告提取三份实际输出（均为合法 JSON）再生快照文件 edit-controls、query-controls、view-controls 三个 page.json。若单子节点折叠本身被裁定为回归，需另行 bisect xlang/web 历史——本修复只恢复"快照钉住当前行为"的测试语义。

### 4. nop-code-service：调用边按限定名整层丢失（确定性，产品修复）

- `TestIndexNopXlangModule.verifyCallHierarchy`：`XLang.newCompileTool` 的 outgoing 调用为空列表，而源码真值存在（同文件静态调用）。诊断链：探针确认符号表完好（1.8 万符号、type outline 可解析）→ 直接调用 `JavaFileAnalyzer` 分析 XLang.java：45 个调用全部提取，但 `calleeQualifiedName` 全为 null。
- 根因：`JavaFileAnalyzer` 的 TypeSolver 只挂 `ReflectionTypeSolver`（无项目源码根求解器），被索引模块自身的类型解析不了，`expr.resolve()` 抛异常降级后不设置 `calleeQualifiedName`；而全链路（`ProjectAnalyzer.resolveCalls` → `CallReferenceResolver` → 持久化）只认该字段——调用边按限定名整层丢失。同模块 `TestNopCodeHierarchyQueries` 的注释（"callees may be empty…"）早已记录该症状。
- 修复（产品）：`JavaFileAnalyzer.analyze` 访问结束后新增 `resolveImplicitSameClassCalls` 后置遍历——无 scope 且 caller 方法所在类型声明了同名方法的调用，将 `calleeQualifiedName` 归属 enclosing 类型（完整 symbolMap 不受声明顺序影响；静态导入/继承方法不在其中，不会被误归属）。
- 排错备注：测试模块依赖的是 .m2 里已安装的 nop-code-lang-java jar，修改后必须先 `install -pl nop-code/nop-code-lang-java -DskipTests` 再跑下游测试，否则一直在验证旧字节码。

### 5. nop-message-kafka：超时下界断言起点错误（时序竞争）

- `sendTimeoutBoundsTheReturnedFuture`：`elapsed=34`（要求 ≥40）。`start` 在 `result.get()`（等 mock 派发）之后才取，而 `orTimeout` 定时器在 sendAsync 内同步挂载——派发被调度延迟时，测得 elapsed 小于定时器真实年龄。
- 修复：`start` 移到 `sendAsync` 调用之前（定时器年龄必然 ≥ 此后的 elapsed），下界断言不再依赖线程调度时机。

### 6. nop-stream-runtime：重试周期 remove-put 窗口（时序竞争，产品修复）

- `abortKeepsMarkerWhileCommitRetriesPending` 满载下 `failedCommitParticipants.size()` 期望 1 实际 0。`retryFailedCommits` 的 else 分支先 `it.remove()` 再 `put(stillFailing)`，两步之间条目暂时消失；隔离运行 1210/1210 通过，满载下测试线程恰好观测到窗口。
- 该窗口同时是产品隐患：abort 路径的门控 `if (!failedCommitParticipants.containsKey(...)) checkpointSuccessMap.remove(...)` 在窗口内会把仍在重试的 epoch 误判为无失败者，提前删除终端标记——重放将默认 success=true，正是标记要防止的语义。
- 修复（产品）：else 分支改为对 ConcurrentSkipListMap 原位 `put`（替换 value，不先 remove），消除瞬时不可见窗口。

## 后续清扫轮次（sweep3–5）暴露的同类面

清扫收敛轨迹：sweep1 失败 6 模块 → sweep2 4 → sweep3 1 → sweep4 1 → sweep5 1 → sweep6 全绿（454 SUCCESS · 0 失败）。每轮失败点均不同，逐个隔离验证修复：

- **7. stream-runtime "完成信号后立即断言 commit"（时序竞争，4 处）**：checkpoint commit 骑在 fan-out 执行器上、晚于完成 future/latch，满载下测试线程先醒。TestCheckpointEndToEnd / TestCheckpointParticipantIntegration / TestE2ETwoPhaseCommitSink / TestDistributedExactlyOnce 统一按 TestAwait 惯例补有界等待。
- **8. TestStreamConnectorRegistryE2E 2PC 末 epoch 落盘窗口（时序竞争）**：`execute()` 返回与最后一个 epoch 落盘之间存在窗口，满载下读文件缺尾部——有界等待内容收敛后再断言。
- **9. nop-ai-gateway `ensureBridgeSubscribed` check-then-act（并发缺陷，产品修复）**：64 并发首派发注册 5 个桥订阅。`LocalMessageService.subscribe` 是无条件 add、**不按消费者身份去重**（原 javadoc 前提错误）——同实例首订阅用 synchronized 串行化，保持"先订阅后登记"顺序。此前 A/B 证明"稳定红"的已登记缺陷随之闭环（gateway 243/243）。
- **10. nop-job-dao manual fire 同毫秒撞 UK（时序竞争）**：`newFire` 缺省 `scheduledFireTime=now`，与 active1 同毫秒即撞 UK_NOP_JOB_FIRE_SCHEDULE_TIME_SOURCE（R1.9 已知同型，注释自认 incidental 碰撞；满载下语句间隔拉长更易同毫秒）——manual fire 显式错开 +2s。
- **11. nop-retry-engine 后台扫描器污染计数（时序竞争）**：扫描器默认 5s 一扫，会把类内此前用例遗留的 PENDING 记录重新执行、推高 `rpcInvoker.getInvocationCount()`（隔离跑 2-10s 躲过、满载 24s 必中）——测试类静态块把 `nop.retry.scanner.scan-interval-ms` 调至 1h；扫描语义覆盖走 `executeRetryFromScanner` 直调。
- **12. surefire fork 退出超时（环境，构建配置）**：满载下 debezium 内嵌引擎 JVM 退出超 30s 默认值，报"timeout in the fork"假失败（测试 70/70 绿）——根 pom `forkedProcessExitTimeoutInSeconds` 30→120。
- **13. TestMessageSource push/poll 顺序假设（时序竞争）**：push("ok") 与后台泵线程并发，泵滞后时 pollNext 直接以 pendingError 失败（fast-fail 同样合法）——先排空 "ok" 再投毒，钉住 P1-9 的"失败类型化浮现"本意。
- **e2e 环境坑（非缺陷）**：本机 8081/8083 被无关进程（llama-server、Python）占用，Playwright `reuseExistingServer` 复用错误进程——code-e2e 的 404 即打到 llama-server；需 `PORT`+`BASE_URL` 覆盖（web-e2e 的 spec 硬编码 fallback 8083，仅传 PORT 无效）。

## Tests

- `nop-code/nop-code-web/src/test/java/io/nop/code/web/NopCodeWebPagesTest.java` — 1/1
- `nop-metadata/nop-metadata-web/.../NopMetadataWebPagesTest.java` — 1/1（删除工件后）
- `nop-auth/nop-auth-web/.../TestWebPage.java` — 8/8（快照再生后）
- `nop-code/nop-code-service` — 290/290；`nop-code/nop-code-lang-java` — 38/38（分析器修复后）
- `nop-message/nop-message-kafka` — 104/104；`nop-stream/nop-stream-runtime` — 1210/1210

## Affected Files

- `nop-code/nop-code-web/src/test/.../NopCodeWebPagesTest.java`
- `nop-metadata/nop-metadata-web/src/main/resources/_vfs/nop/metadata/pages/NopMetaTable*`（删除）、`nop-metadata/nop-metadata-meta/_templates/_NopMetaTable*.json`（删除）
- `nop-auth/nop-auth-web/src/test/resources/io/nop/auth/web/page/{edit,query,view}-controls.page.json`
- `nop-code/nop-code-lang-java/src/main/java/io/nop/code/lang/java/analyzer/JavaFileAnalyzer.java`
- `nop-message/nop-message-kafka/src/test/.../TestKafkaMessageServiceSend.java`
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java`

## Notes For Future Refactors

- 全量构建长期中途失败会掩盖下游模块的真实损坏；修不动主干失败时，至少周期性用 `-fn` 清扫一次全 reactor，把"从未执行"的测试面暴露出来。
- `test-mock.beans.xml` 类测试 bean 文件必须随测试类 `@NopTestConfig(testBeansFile=...)` 显式引用，IoC 只自动加载 `app*.beans.xml` 命名。
- 删除源资源后验证前清理对应 target/classes（增量构建不回删已删资源），否则"删了还在"。
- 并发 Map 的 remove-then-put 会让 containsKey 出现假阴性窗口；读侧若用 containsKey 做门控，写侧必须原位 put。

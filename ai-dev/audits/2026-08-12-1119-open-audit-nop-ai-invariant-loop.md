> Audit Status: planned
> Audit Type: open-ended
> Mission: nop-ai-invariant-loop

# Open-ended Adversarial Audit — nop-ai-invariant-loop

> 执行日期：2026-08-12。审查范围：`nop-ai/` 全模块（agent/core/gateway/shell/toolkit）跨 Cycle 1 关口的当前 live 状态 + 五族门禁自身（TestInvariantGate*、check-ai-tool-executor-boundary.mjs、check-fix-commit-diff.mjs、gate-gaps.yaml、invariant-catalog.md）。
> 方法：开放式对抗审查（`ai-dev/skills/open-ended-adversarial-review-prompt.md`），视角 = 异常路径侦探 + 死代码清道夫 + 组合爆炸测试者 + IoC 侦探。所有证据均 live 核实（git show / 源码读取），非凭记忆。
> 去重：已浏览 `ai-dev/audits/nop-ai-invariants/`（catalog/red-list/adjudication/gate-gaps）与 `ai-dev/backlog/nop-ai-invariant-loop-roadmap.md`；本报告聚焦**门禁机制自身的盲区与 I4 修复的不完整面**——即"门禁绿但兄弟分支仍裸奔"这一类问题。

---

## 发现

### [P1] [AR-1] `AgentExecutorResolver.resolveExecutor(model, toolAccessChecker)` 两参重载静默丢弃参数（MA4.2-05 拆分回归）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentExecutorResolver.java:129-131`
- **证据片段**:
  ```java
  // 拆分前（git show 2f3251589^:DefaultAgentEngine.java:3250）:
  IAgentExecutor resolveExecutor(AgentModel model, IToolAccessChecker toolAccessChecker) {
      return resolveExecutor(model, toolAccessChecker, this.pathAccessChecker);   // ← 使用参数
  }
  // 拆分后（AgentExecutorResolver.java:129-131）:
  public IAgentExecutor resolveExecutor(AgentModel model, IToolAccessChecker toolAccessChecker) {
      return resolveExecutor(model, config.getToolAccessChecker(), config.getPathAccessChecker());  // ← 参数被丢弃！
  }
  ```
- **严重程度**: P1
- **现状**: MA4.2-05 拆分（commit 2f3251589）后，两参重载把调用方传入的 `toolAccessChecker` 静默替换为 `config.getToolAccessChecker()`。调用方（如 `DefaultAgentEngine.resolveExecutor(model, checker)` 及测试 `TestSubAgentPermissionWiring:306`）传入 wrapped（parent-constrained）checker 时，实际装配的是引擎默认 checker——权限约束被静默绕过。
- **风险**: 任何依赖"传入自定义/包装工具访问检查器"的调用方得到错误的安全装配；当前仅测试路径触发（生产均用三参），但这是安全相关参数的静默 contract drift，且测试 `resolveExecutorPassesEffectiveCheckerToExecutor` 只断言 `assertNotNull(executor)`，根本测不出 checker 是否生效。
- **建议**: 恢复 `resolveExecutor(model, toolAccessChecker, config.getPathAccessChecker())`；两参重载 javadoc 已声明"only override the tool checker behave identically"——现在实现违背声明。补一个断言 wrapped checker 实际生效的测试。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探 + 死代码清道夫

### [P1] [AR-2] `MemberFanOutDispatcher.dispatch` 的 SPAWN 分支无 per-member timeout（I4 R-2-3 修复只覆盖 BOUND 分支，门禁②文件级 marker 检查对此盲区）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/team/flow/MemberFanOutDispatcher.java:233-237, 326, 359-373`；`DefaultMemberSpawner.java:173-174`
- **证据片段**:
  ```java
  // dispatch() 内两分支:
  if (target.isBound()) {
      perMember.add(executeBoundMember(target, task, agentEngine, memberExecTimeoutMs));  // :233 — 有 orTimeout(:326)
  } else {
      perMember.add(spawnOneTarget(...));   // :235 — 无 orTimeout！
  }
  // spawnOneTarget → supplyAsync(spawnAndInterpret) → DefaultMemberSpawner.spawnMember:
  CompletableFuture<AgentExecutionResult> future = agentEngine.execute(execRequest);
  result = future.join();   // DefaultMemberSpawner.java:174 — 无界 join！
  ```
- **严重程度**: P1
- **现状**: I4 R-2-3 只给 BOUND 分支加了 `orTimeout(memberExecTimeoutMs)` + 超时 `cancelSession`；SPAWN 分支的 `spawnMember`（内含 `engine.execute(...).join()` 无界等待）完全无超时。daemon 路径（`TaskDispatchCoordinator.dispatchClaimedTask:213-216`）对 in-flight future 无整体 deadline——`awaitInFlightDispatches:138-154` 只是等待方超时返回，future 本身永不 settle → `inFlightDispatches` 队列条目永不移除（`whenComplete` 不触发），spawn worker 线程被永久占住。
- **风险**: 挂起的 SPAWN 成员 = 与 AUDIT-14-01 同族的无界等待在新增面上复发（门禁②正是为此族而建）；daemon 队列无界增长 + spawn 线程池耗尽；任务永久 CLAIMED。
- **建议**: 给 SPAWN 分支补 `orTimeout`（超时后 cancelSession 或至少完成失败）；门禁②判定标准应从"文件含 orTimeout 标记"细化为"每条执行分支含标记"（或补一个 SPAWN 分支负例测试）。Catalog §3.2 表 15 行"declared"对 BOUND/SPAWN 两种目标形态都是同一条目——需拆成两条或标注分支级。
- **信心水平**: 确定
- **发现来源视角**: 组合爆炸测试者（门禁绿 vs 分支裸奔）

### [P1] [AR-3] `TeamTaskFlowOrchestrator.executeAsync` 整体 deadline 与 per-member deadline 同值 + 超时后不取消底层执行

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/team/flow/TeamTaskFlowOrchestrator.java:614-626`
- **证据片段**:
  ```java
  .orTimeout(memberExecTimeoutMs, TimeUnit.MILLISECONDS)   // :618 — 整体 deadline = 单成员 deadline
  .exceptionally(ex -> { return built.recorder.buildResult(false, built.tasks); });  // 只转换结果，不取消图执行
  ```
- **严重程度**: P1
- **现状**: 两个问题叠加：(a) 整体 deadline 与 per-member deadline 都用 `memberExecTimeoutMs`（默认 120s）——合法多层 DAG（N 层顺序链，每层成员合法跑满 120s）总时长可达 N×120s，会被 120s 的整体 deadline 误杀，即使一切正常；(b) 整体超时后只把结果 future 变 failed，**不 cancel 底层 nop-task 图**——成员 agent 继续执行（zombie 消耗 LLM 配额），与 INV-2 族"超时必须停止工作"语义相悖（对比 BOUND 分支超时会 `cancelSession`、SingleTurnExecutor/AgentToolDispatcher 会 `future.cancel(true)`）。
- **风险**: 合法多层流被误报失败；超时后成员继续跑（资源泄漏）；`execute(String)` 同步入口 `join()` 在整体 deadline 后才返回，实际可能已超时但图仍在跑。
- **建议**: 整体 deadline 应取 `memberExecTimeoutMs × maxDepth`（或独立配置项）；整体超时时尝试 cancel 图/成员会话，至少记录失败路径与底层继续执行的状态。
- **信心水平**: 确定（a）/ 很可能（b）
- **发现来源视角**: 异常路径侦探

### [P1] [AR-4] `ChannelMessageServiceImpl` 模式 1 扇出 timeout 不取消底层 listener——transport 线程不再阻塞，但池线程永久泄漏

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/ChannelMessageServiceImpl.java:368-381`
- **证据片段**:
  ```java
  CompletableFuture<Void> listenerFuture = CompletableFuture
          .runAsync(() -> listener.onInbound(message), executor)
          .orTimeout(dispatchTimeoutMs, TimeUnit.MILLISECONDS);   // 只让 future 超时，listener 继续在池线程上跑
  listenerFuture.whenComplete((v, ex) -> { LOG.error(...); });    // 不 cancel
  ```
- **严重程度**: P1
- **现状**: I3 R-2-1 修复声称"挂起 listener 不再阻塞 transport 线程"——transport 线程确实不阻塞了，但 `orTimeout` 不会中断/cancel 仍在池线程上运行的 listener。池大小 `max(2, availableProcessors)`，若 listener 持续挂起，线程被永久占住，池耗尽后所有后续 inbound 消息在固定池无界队列里无限排队。
- **风险**: 与同一代码库的既有超时模式不一致（`SingleTurnExecutor:146` / `AgentToolDispatcher:250,268` / `MemberFanOutDispatcher:335` 都会在超时后 cancel/取消）；持续挂起 listener 场景下通道 inbound 面瘫痪。门禁② gateway 条目只查 `orTimeout` 字符串存在，同样盲区。
- **建议**: 超时回调里 `listenerFuture.cancel(true)`（runAsync 任务可中断）；或在 whenComplete 中对未完成的 future 做 cancel 并记录。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

### [P2] [AR-5] `AgentCallDelegate` import 块三重复制（import-order 检查器实际报 2 错）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentCallDelegate.java:16-40`
- **证据片段**: 文件含 3 份完全相同的 import 块（`org.slf4j.Logger`、`java.util.List`、`CompletableFuture` 等各 3 次）——`node ai-dev/tools/check-import-order.mjs` 对第 23/32 行报错。
- **严重程度**: P2
- **现状**: MA4.2-05 拆分时 import 块被粘贴 3 份（javac 允许重复 import 所以编译通过）。
- **风险**: 违反 MA4.2-14 import 分组约定；import-order 检查器（若入 CI）会红。
- **建议**: 删除两份重复 import。
- **信心水平**: 确定

### [P2] [AR-6] 拆分出的引擎类 Logger 全部挂名 `DefaultAgentEngine`——日志归属漂移

- **文件**: `AgentExecutorResolver.java:34`、`AgentCallDelegate.java:49`、`AgentSessionLifecycle.java:66`、`AgentTeamBinder.java:39`、`AgentStartupWarnings.java:49`、`SessionLockRenewal.java:34`、`AgentSessionSupport.java:39`、`DefaultAgentEngineConfig.java:102`、`TaskDispatchCoordinator.java:37`（nop-ai-agent）
- **证据片段**: 全部为 `private static final Logger LOG = LoggerFactory.getLogger(DefaultAgentEngine.class);`——copy-paste 后未改类名。
- **严重程度**: P2
- **现状**: 9 个类共用 `DefaultAgentEngine` 作为 logger 名，无法从日志区分实际来源类。
- **live 复核修正（2026-08-12，plan review 轮 2）**: `TaskDispatchCoordinator.java:37` 实为 `LoggerFactory.getLogger(TeamTaskSchedulerDaemon.class)`——**该项为误报**；实际受影响 = 8 个类（AgentExecutorResolver/AgentCallDelegate/AgentSessionLifecycle/AgentTeamBinder/AgentStartupWarnings/SessionLockRenewal/AgentSessionSupport/DefaultAgentEngineConfig）。修正已同步 roadmap `## Follow-up Backlog` 条目。
- **风险**: 生产排障时日志归属误导；门禁③/其他日志类检查无法发现。
- **建议**: 每个类改为 `getLogger(X.class)`。
- **信心水平**: 确定

### [P2] [AR-7] `DefaultAgentEngineConfig` 超时 setter 校验不对称——catalog 声称"setter 拒绝非正数"但仅 2/4 实现

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngineConfig.java:943-953, 965-969`；catalog §2 INV-2 行 43
- **证据片段**:
  ```java
  public void setCallAgentTimeoutMs(long v) { if (v <= 0) throw ...; this.callAgentTimeoutMs = v; }   // 校验 ✓
  public void setLlmTimeoutMs(long llmTimeoutMs) { this.llmTimeoutMs = llmTimeoutMs; }                // 无校验 ✗
  public void setToolTimeoutMs(long toolTimeoutMs) { this.toolTimeoutMs = toolTimeoutMs; }            // 无校验 ✗
  public void setMemberExecTimeoutMs(long v) { if (v <= 0) throw ...; }                               // 校验 ✓
  ```
- **严重程度**: P2
- **现状**: catalog 断言"配置默认值 callAgentTimeoutMs=120000 / llmTimeoutMs=120000 / toolTimeoutMs=300000（均非 0，setter 拒绝非正数）"——live 仅 callAgentTimeoutMs/memberExecTimeoutMs 校验。`setLlmTimeoutMs(0)` 会让 `get(0, TimeUnit)` 立时超时、`setToolTimeoutMs(0)` 会走 `AgentToolDispatcher` 的"<=0 禁用超时"逃生口（:230-231 注释），语义相反。
- **风险**: 文档（catalog）与代码漂移；配置 0 值行为不一致（有的拒、有的禁用、有的立即超时）。
- **建议**: 统一四个 setter 的非正数校验（或明确 toolTimeoutMs<=0 的 escape hatch 语义并在 catalog 中更正）。
- **信心水平**: 确定

### [P2] [AR-8] Gate-1 负例测试把 fixture 写进 `src/main`——中断残留会污染产物并让门禁红

- **文件**: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/gate/TestInvariantGate1SecureDefault.java:247-262, 315-327`；`nop-ai/nop-ai-shell/src/test/java/io/nop/ai/shell/gate/TestInvariantGate1SecureDefaultShell.java:170-192`
- **证据片段**: `writeFixture` 把 `DefaultGateNegativeFixture.java` 写到 `nop-ai-agent/src/main/java/io/nop/ai/agent/gatefixture/`（finally 中删除）。
- **严重程度**: P2
- **现状**: 测试期间在 `src/main` 下临时创建 Java 文件。若测试被中断（kill -9 / surefire 崩溃），文件残留 → 进入产物 jar + 下次 gate-1 表完备性红（phantom unregistered class）。
- **风险**: CI 偶发红且难以理解根因；产物污染。
- **建议**: fixture 改写到 `_tmp/` 或系统临时目录（或 `src/test` 下并调整扫描范围）；负例校验通过后删除目录而非单文件。
- **信心水平**: 确定

### [P2] [AR-9] `dispatchTimeoutMs` 文档声称可由配置项控制，但无任何接线——配置旋钮是死的

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/ChannelMessageServiceImpl.java:96-100`（javadoc："Configurable via `nop.ai.gateway.channel.dispatchTimeoutMs`"）
- **证据片段**: `grep -rn "dispatchTimeoutMs" nop-ai/nop-ai-gateway/src/main/resources/` 零命中；beans.xml 未注入该属性；无 `@InjectValue("@cfg:...")`。
- **严重程度**: P2
- **现状**: setter 存在但生产装配没有任何 `nop.ai.gateway.channel.dispatchTimeoutMs` 绑定——文档承诺的配置项无效，永远是硬编码 30000。
- **风险**: 运维按文档调配置无效；doc-vs-code drift（INV-2 族判定标准 (c) 的配置项同样落空）。
- **建议**: 在 `ai-gateway-defaults.beans.xml` 加 `@InjectValue("@cfg:nop.ai.gateway.channel.dispatchTimeoutMs|30000")` 或 `<property>` 接线；否则更正 javadoc。
- **信心水平**: 确定

---

## 总评

Cycle 1 的飞轮本身（五族门禁 + known-gaps 棘轮 + 表完备性 + 独立子 agent 审查纪律）是有效的、且是仓库里罕见的机制化实践——我没有发现门禁被白名单化的迹象（gate-gaps.yaml 目前只剩 5 条 not-applicable，无 missing-declaration）。但**门禁的声明级判定存在系统性盲区：文件级 marker 检查（orTimeout / @SecureDefault / 成对标记）无法区分"文件里某处有标记"与"每条执行路径都有标记"**。本次审查的三个 P1 全部落在这个盲区里：

1. I4 R-2-3 的修复在 `MemberFanOutDispatcher` 里只覆盖了 BOUND 分支，SPAWN 分支（含 `DefaultMemberSpawner` 的无界 `join()`）完全无超时——而门禁②因为文件里有 `orTimeout` 字符串照样绿。这正是 INV-2 族"同族兄弟漏网"的精确复发形态（catalog §6.3 第六门禁族候选的触发条件 2 已预见到这种"行为级回归但门禁全绿"）。
2. `AgentExecutorResolver` 两参重载静默丢参数是 MA4.2-05 拆分引入的 contract drift，且配套测试只断言 non-null——"拆分语义 0-diff"的宣称在这个点上不成立。
3. `TeamTaskFlowOrchestrator` 整体 deadline 与 per-member deadline 同值 + 超时不取消底层执行，是 I4 修复本身引入的新语义缺陷。

最值得关注的三个方向：(1) 门禁②④从"文件级 marker"升级为"分支级 marker"判定（至少对 team-flow fan-out / channel fan-out 这类有 BOUND/SPAWN 分支的入口）；(2) MA4.2-05 拆分面做一次参数传递完整性复查（本次已抓到 1 例静默丢参 + 1 例 logger 挂名 + 1 例 import 三重复制，同批拆分的其他类大概率还有同类残留）；(3) timeout 语义统一（超时 = 停止工作，不是只停等待）。

## 本次审查的盲区自评

- 未对 `nop-ai-core` / `nop-ai-shell` / `nop-ai-toolkit` 的完整源码做逐文件通读（聚焦在 agent 编排面 + gateway 新代码 + 门禁自身），core 的 deprecated API 面、toolkit 的 fs/ssrf 面可能有漏网。
- 未运行 `./mvnw test -pl nop-ai -am`（时间成本高；I5 已记录全绿），门禁的运行时行为（如 gate-1 fixture 写入的并发窗口）只做了静态分析。
- 门禁⑤（fix-commit diff）的配对算法（`r.trim() === added.trim()` 配对）可能有误判边缘（如同行内多个 -/+ 交错），未构造对抗样例验证。
- 未审计 `nop-ai-*` 的 dao/orm/meta/biz 层（ORM 模型、GraphQL schema、i18n），这些面在 audit-remediation roadmap 已覆盖，本次聚焦 invariant-loop 的机制本身。

## 按严重程度分布表

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 0    | — |
| P1      | 4    | 门禁盲区下的分支级无超时（AR-2/4）、拆分回归静默丢参（AR-1）、整体 deadline 语义缺陷（AR-3） |
| P2      | 5    | import 重复/logger 挂名/校验不对称/doc drift/测试写 src/main（AR-5~9） |

<AI_STEP_RESULT>issues</AI_STEP_RESULT>

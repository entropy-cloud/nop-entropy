# 多 Agent 并行协调与缓存利用设计（nop-ib 服务）

> Status: active
> 归属：nop-incremental-build；与 07（服务模式）/ 09（异步 Job API）同族，定义多 AI agent 共享一台构建服务时的协调契约。

## 一、协调模型：为什么是「单项目串行 lane + 跨项目预算并行」

多 agent 对同一仓的高频操作是并发提交编译/测试。两个极端都被拒绝：

- **全局串行**：不同项目互不依赖时强制排队浪费预算（跨项目并行预算 = CPU 核数一半）。
- **完全并行**：同仓两个构建并发跑 Maven/javac 会互踩 target/ 与锁文件，结果不可信。

因此 lane 模型是唯一可信选择：同一 projectDir（归一后同 reactor 根）FIFO 串行，跨 lane 按全局预算并行。lane 归一依赖「repo 根发现」（mvnw/.mvn 标记优先，pom 链攀升兜底），嵌套独立项目必须 `ib-run --dir` 显式指定，否则归一到父仓（契约见分发包 README）。

## 二、排队者契约（agent 可见性）

排队中的 agent 依赖以下字段决策「继续等还是放弃」：

| 字段 | 语义 | 决策用途 |
|---|---|---|
| `state:"queued"` + `phase:"queued"` | 显式排队态，不是失败 | 误判防护 |
| `queueAhead` / `queueAheadIds` | 前方还有 N 个排队 job 及其 id（FIFO，oldest first） | 拥挤度评估 |
| `runningJobId` + `cmd`（自身命令） | 当前 lane 正在跑谁的什么 | 判断前面工作的性质 |
| `queueWaitMs` | 已等待时长 | 超时放弃的依据 |
| `progress` / `logTail` | **仅 RUNNING 时出现** | 运行进度 |

不变式：**排队 job 的快照绝不携带 `progress`/`logTail`**——二者是 lane 级事实（ProgressRegistry 按 repoRoot 记账、日志按 lane 正在执行的 job 落盘），泄漏给排队者会被误读为「我的构建已在跑」（2026-11-22 实测缺陷，已修并有回归钉）。

`/v1/queue` 语义：`depth` = 仅排队数（不含运行中）；`pending` = 运行 + 排队总数；`runningCmd` = 该 lane 正在执行的命令。

## 三、agent 行为规范（写入 /v1/help 的 etiquette）

1. 提交时带 `clientRequestId`（绑 projectDir 幂等）——超时重试不会重复排队。
2. 排队是常态不是错误：轮询 `GET /v1/job/{id}?waitMs=N` 直到终态；不要看到慢就换 key 重提（会加倍排队）。
3. `ib-run` 薄壳已内置轮询模式：stderr 实时输出 `queued: N job(s) ahead (running: job-K) — waiting` 与 `running: <phase> (Ns)`；`--quiet` 关闭进度通道（仍输出最终结果行）。退出码透传构建结果。
4. 队列容量：单 lane 排队上限 32，满则 HTTP 429——这是「agent 过载」信号，应退避而不是立即重试。

## 四、同参任务合并（coalescing）与缓存边界

不同 agent 常会提交完全相同的构建命令。合并裁定：

- **只合并到 QUEUED（尚未开始执行）的 job**：排队 job 尚未快照输入，启动时会按当时源码分类变更集——合并是结果等价的，省掉一次重复构建。响应带 `replayed:true + coalesced:true`。
- **RUNNING 的 job 绝不吸收新提交**：运行中 job 的输入集已快照，源码若在其启动后变更，合并会返回改码前的结果。相同命令重新排队是正确语义——重跑时若源码实际未变，快路径指纹判定后命中测试缓存（近零成本）；变了则正确重建。**缓存负责"没变就别重跑"，排队合并负责"重复提交别重复排队"，两者互补且都不牺牲新鲜度。**
- 终态 job 不吸收新提交（重提交 = 一次新构建，由缓存阶梯决定成本）。
- `clientRequestId` 幂等（同 key 重试返回同一 job）与合并（不同 key 同命令）是两个独立机制：前者防重试重复，后者防多 agent 撞车。

## 五、缓存利用：让排队成本趋近于零

三级成本阶梯（同一 test 命令重复提交时的实测形态）：

1. **测试缓存命中**（模型新鲜 + 模块指纹未变 + 上轮绿）：`cached:true`，秒级返回（实测 ~1ms vs 全跑 ~25s）。重复提交同一模块 test 的 agent 自动受益；`--force` 显式逃逸。
2. **快路径增量**（代码有变但可增量编译）：javac 增量 + 单测派发，秒~十秒级。
3. **Maven 回退**（模型 stale 等）：每个排队者各付一次全量 Maven 成本（实测同 lane 三连跑各 ~35s，串行 ~100s）——这是最贵的形态。

结论与规范：

- **pom 变更后先做一次 `model refresh` 再发起批量构建**：回退成本是按「每个 job」重复支付的，refresh 一次（大仓分钟级，机器被挤占时更久）把后续所有排队者拉回快路径。refresh 自身也占 lane（提交排队），应在低峰或批量任务前主动做。
- **测试缓存在 lane 串行下天然安全**：同一模块的重复 test，第二个排队者命中第一个的结果缓存（指纹未变时），排队时间退化为队列等待本身。
- 缓存命中依赖模型新鲜度；`fallback.reason` 非 null 即表示本轮未享快路径——agent 看到 fallback 应先怀疑模型过期，而不是重试。

## 六、拒绝的替代方案

- **跨仓全局锁**：被预算并行替代——串行化不同项目浪费核预算。
- **排队预估时间（ETA）**：需要按命令维度的历史耗时统计，当前只暴露 `queueAheadIds`/`runningJobId`；ETA 归入后续增强（依据不足的预估比没有更糟）。
- **对 RUNNING job 合并提交**：见 §四——会返回输入快照后的陈旧结果；**允许排队者中途取消重排**：DELETE /v1/job 已支持 QUEUED/RUNNING 取消；重排语义不引入——FIFO 简单性优先，抢占会饿死长任务。

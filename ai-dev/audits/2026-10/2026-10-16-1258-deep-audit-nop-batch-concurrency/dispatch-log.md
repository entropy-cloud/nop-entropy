# Dispatch Log — nop-batch concurrency audit (plan 2467)

- 主 agent：ZCode 会话 sess_21ba4274（分支 concurrency-audit-remediation，worktree nop-entropy-concurrency-audit）
- 审计对象：nop-batch 聚合器（15 pom 模块；11 个含 Java 的 jar 子模块：api/app/biz/core/dao/dsl/exp/gen/jdbc/orm/service，218 files/15,698 行手写，不含 `_` 前缀与 target/）
- 方法论：ai-dev/skills/concurrency-resource-audit-prompt.md；维度集合 C1/C5/C6/C7/C8 全维度 + C10（roadmap 1.7 裁定；维度集外线索 overlay 为 C2/C3/C4/C9 四形态、CL 前缀 nop-batch-CL-0NN，沿 plan 2464 裁定节口径）
- 基线：_tmp/cra-baseline-nop-batch.txt（附录 C 全部命令 + C7 补充正则（2463）+ C8 队列正则（2463 + ArrayDeque/ReentrantLock/Semaphore 变体）+ C2/C3/C4/C9 维度集外节，src/main 限定，--exclude-dir=target）
- 计数核实：218/15,698 与 plan 2467 Current Baseline 逐模块对账一致

## 盲审纪律（每份派发 prompt 均含）

> 【盲审纪律】禁止读取 ai-dev/audits/、ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/ 下的任何文件（含审计输出目录中其他维度的文件）。只审计当前 live code。例外：深挖轮次可读本维度前序轮次文件（必要输入）。

## 第 1 轮初审（串行 5 批次，2026-10-16）

| 批次 | 产出文件 | 派发 agent |
|------|---------|-----------|
| C1 共享可变状态 | C1-shared-mutable-state.md | （见下结果表） |
| C5 内存有界性 | C5-memory-bounds.md | 同上 |
| C6 资源生命周期 | C6-resource-lifecycle.md | 同上 |
| C7 长时运行 | C7-long-running.md | 同上 |
| C8 背压流控 | C8-backpressure.md | 同上 |

## 第 1 轮初审结果（2026-10-16）

| 批次 | agent | 发现 |
|------|-------|------|
| C1 | agent_830a5868 | 5 条（P0=0/P1=0/P2×3：ThreadLocal 不恢复旧值嵌套清空、BatchGenState 共享游标锁外驱动、swap-on-fire 回调注册守卫缺「已触发」判定；P3×2）+ CL-001~006（C1 结论：守卫质量较高，3 条 P2 均条件化） |
| C5 | agent_c20928af | 5 条（**P1×2：excel-reader 全量物化 50 万行 1.5–3GB、jdbc-reader fetchSize 死配置 MySQL/PG 全量缓冲**；P2×3：generator 生成环全量驻留、provider List 常驻、dispatch 容量无守卫 Semaphore(0)）+ CL-007~011（负发现 10 项登记：orm-reader WARN 后实为 keyset 有界、CSV 流式等） |
| C6 | agent_df5b013b | 6 条（**P1×2：executeChunkLoop 仅 catch Exception→Error 时 future 永不完成 reader/writer/连接永不关闭+任务行永久 RUNNING、executor.execute REE 同步抛→句柄泄漏+永久 RUNNING**；P2×2、P3×2）+ CL-012~015 |
| C7 | agent_ffa5ee2d | 11 条（**P1×2：任务行永久 RUNNING 死区（tryMarkTaskRunning 无 stale 判定，cron 每 5s 错误风暴）、三表零保留无任何 delete/TTL/purge**；P2×8、P3×1）+ CL-016~021（单调增长源 12 项、挂死点 11 项清单） |
| C8 | agent_2fdf4087 | 6 条（**P1×2：loadBatchSize≤0 全装配链无护栏→LIMIT 0 静默成功全量丢数/不守 0 loader 永久挂死、ExportDbTool batchSize=0 缺省→每行一 chunk（与 Import `>0` 守卫 F3 不对称）**；P2×2、P3×2）+ CL-022~025（基线复核终判：CL-007 fetch 异常吞掉 live 已闭合不立案、CL-008 深化立案为 C8-002、CL-009 维持低危） |

维度集外线索累计 25 条（CL-001~025），待 Phase 4 复核确认/驳回（C8 轮已对 CL-007/008/009 作中期终判，Phase 4 沿用）。

## 第 2 轮深挖（串行，2026-10-16）

| 批次 | 产出文件 | 发现 | 终止判定 |
|------|---------|------|---------|
| C1 r2 | C1-round2.md | +2（P2：SequentialBatchRequestGenerator async 组合 subIndex/subContext 双线程驱动错位、P3：ExecutionContextImpl 终态协议双缺口）+ CL-026/027；第 1 轮三条 P2 证据升级维持 | 终止（①~⑤失效面穷举闭合） |
| C5 r2 | C5-round2.md | +4（全 P3：maxCountExpr 三层缺位且不构成 C5-001 兜底、SplitBatchConsumer tag 高基数（理论）、任务级容器无守卫（理论）、batchSize 全链路无上界）+ CL-028~032；C5-001 修复面收窄（物化点唯一在 HeaderListRecordOutput）、C5-002 变体 B（非 MySQL dialect 无 streamingFetchSize） | 终止（家族闭合；Excel 写出侧流式有界——与读侧不对称，为修复提供先例） |
| C6 r2 | C6-round2.md | +5（P2×4：ImportDbTool Hikari 池建于 try 外泄漏（**修正第 1 轮 C6-003 结论**）、saveState×AsyncFetch 组合 processingItems 永滞留、onTaskComplete saveTaskState 异常静默丢失+metrics.endTask 先抛 future 孤儿、provideContext 位于 try 外（catch(Throwable) 亦无法闭合）；P3×1）+ CL-033~035；终态化阻断站点表 S1~S10、关闭出口 F3 16 行主表 | 终止 |
| C7 r2 | C7-round2.md | +5（P2×2：KILLED 永久吸收态无逃生门+恢复入口仅裸 CRUD 无 fencing、exponentialDelay 指数溢出精确归零（rt=61→零延迟紧循环）；P3×3）+ CL-036；状态机 G1~G7 全转移表、清理面二态判定「DB 表清理代码完全不存在」、时钟 6 站点全良性、completedIndex 崩溃窗口 ≤C×B 行（at-least-once 内） | 终止（第 1 轮 P1×2 反向核查维持） |
| C8 r2 | C8-round2.md | +4（全 P2：retry() 零调用+getLoadItemCount 语义错、积压水位不可观测（queue 局部闭包）、30×/maxLockQueueCountPerThread 幽灵旋钮（**修正第 1 轮口径**：内部生效但无入口）、护栏双标（biz 钳制 vs core/exp 无界+batchSize=0 穿透））+ CL-037~039；**参数护栏矩阵 A1~A8/B1~B6/D1~D6/E1~E8 全枚举——三层护栏完整者为 0** | 终止 |

### Phase 3 收敛汇总

- 发现总量：C1 7 + C5 9 + C6 11 + C7 16 + C8 10 = **53 条**（初审 33 + 深挖 20；P0×0；P1×8 全部出自初审）
- 维度集外线索累计 **39 条（CL-001~039）**，待 Phase 4 复核确认/驳回（CL-007/008/009 已中期终判）
- 全部 5 维度显式终止（各 2 轮），无第 3 轮需求
- 跨维度待裁定交叉项：CL-026（executeChunkLoop Error 逃逸）与 C6-001 同位点、CL-012/016/020/036（DaoBatchStateStore/ResourceRecordLoaderProvider 锁内 DB I/O 同族）、CL-024 与 C6-001 修复族、C8-001 与 C5-005 同位点（dispatch 容量零护栏）

### 初审汇总

**33 条发现（P0×0 / P1×8 / P2×17 / P3×8）+ 25 条 CL 线索**。全部 5 维度含 P1 或 ≥3 条 P2/P3 且无终止信号 → Phase 3 全维度派第 2 轮深挖。

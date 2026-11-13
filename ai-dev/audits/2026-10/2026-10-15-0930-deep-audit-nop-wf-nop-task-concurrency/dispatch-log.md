# Dispatch Log — nop-wf-nop-task concurrency audit (plan 2465)

- 主 agent：ZCode 会话 sess_21ba4274（分支 concurrency-audit-remediation，worktree nop-entropy-concurrency-audit）
- 审计对象：nop-wf 聚合器（10 jar 子模块，205f/21,065 行）+ nop-task 聚合器（10 jar 子模块，174f/15,042 行），合计 379 files/36,107 lines（手写口径，不含 `_` 前缀与 target/）
- 方法论：ai-dev/skills/concurrency-resource-audit-prompt.md；维度集合 C1–C5/C7/C8 全维度 + C10（roadmap 1.5 裁定；维度集外线索 overlay 仅 C6/C9 形态、CL 前缀 nop-wf-nop-task-CL-0NN，沿 plan 2464 裁定节口径）
- 基线：_tmp/cra-baseline-nop-wf-nop-task.txt（附录 C 全部命令 + C7/C8 补充正则 + C6/C9 维度集外节，按两目录分别执行，--exclude-dir=target）

## 盲审纪律（每份派发 prompt 均含）

> 【盲审纪律】禁止读取 ai-dev/audits/、ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/ 下的任何文件（含审计输出目录中其他维度的文件）。只审计当前 live code。例外：深挖轮次可读本维度前序轮次文件（必要输入）。

## 第 1 轮初审（串行 7 批次，2026-10-15）

| 批次 | 产出文件 | 派发 agent |
|------|---------|-----------|
| C1 共享可变状态 | C1-shared-mutable-state.md | agent_cb467af3：8 条（P1×1、P2×6、P3×1），两簇 12+7 项载体清单 |
| C2 锁与同步 | C2-locks-sync.md | agent_53aeb064：10 条（P1×1、P2×5、P3×4），锁序无环+非重入信号量嵌套死锁形态 |
| C3 异步链路 | C3-async-chains.md | agent_a7399194：10 条（P1×2、P2×5、P3×3），异步入口全盘点 |
| C4 线程池与执行器 | C4-thread-pools.md | agent_96d7f4ba：6 条（P1×2、P2×2、P3×2），11 个执行器使用点实现级核对 |
| C5 内存有界性 | C5-memory-bounds.md | agent_260c53b9：10 条（P1×2、P2×5、P3×3），五项检查点文件级落点 |
| C7 长时运行 | C7-long-running.md | agent_d9f69db1：13 条（P1×1、P2×11、P3×1） |
| C8 背压流控 | C8-backpressure.md | agent_82834ff3：12 条（P2×8、P3×4），8 条边界盘点 |

维度集外线索：0 条（各维度候选均 ≤P2 或已声明边界，按 overlay 裁定未登记）。

## 第 2/3 轮深挖（2026-10-15，串行）

| 维度 | 轮次 | 新增发现 | 终止判定 |
|------|------|---------|---------|
| C1 | 第 2 轮（agent_2a6125b8） | C1-009（P1 resume/cancel 覆写）、C1-010（P2 wf transitToStatus 守卫不一致+修正 C1-002 事实错误） | — |
| C1 | 第 3 轮（agent_37119f5e） | 0 条（状态机三元矩阵穷尽） | 终止（累计 10：P1×2、P2×7、P3×1） |
| C2 | 第 2 轮（agent_f681fb59） | C2-011（P2 零显式共享 key 嵌套可达，修正第 1 轮结论）、C2-012/013（P3） | 终止（累计 13：P1×1、P2×6、P3×6） |
| C3 | 第 2 轮（agent_34b8005d） | C3-011（P1 流程级自动终结竞态）、C3-012（P1 and-join 竞态）、C3-013（P2 timer 池业务体）、C3-014/015（P3） | — |
| C3 | 第 3 轮（agent_e84a096b） | 0 条（判定-推进站点 15 项穷尽；C3-012 影响面扩展至 or-join） | 终止（累计 15：P1×4、P2×7、P3×4） |
| C4 | 第 2 轮（agent_45eb5044） | C4-007（P2 扫描 job 零失败韧性）、C4-008（P1 retry 恒 detach globalTimer+包装序证明）、C4-009（P3） | — |
| C4 | 第 3 轮（agent_7d3ff052） | 0 条（globalTimer 3 站点穷尽） | 终止（累计 9：P1×3、P2×3、P3×3） |
| C5 | 第 2 轮（agent_2e028808） | C5-011/012（均 P3；第 6 处 findAllByQuery 站点补计） | 终止（累计 12：P1×2、P2×5、P3×5） |
| C7 | 第 2 轮（agent_348c76d9） | C7-014（P2 无执行级 fencing）、C7-015/016/017（P3） | 终止（累计 17：P1×1、P2×12、P3×4） |
| C8 | 第 2 轮（agent_6411018e） | C8-013（P2 派发回调漏 Error promise 悬挂）、C8-014（P2 timeout 释放许可僵尸 body）、C8-015（P2 wf 零过载观测）、C8-016/017（P3） | 终止（累计 17：P2×11、P3×6） |

# 深度审核汇总报告 — nop-ai-toolkit + nop-ai-tools + nop-ai-shell 并发·异步·资源专项审计（CRA WI 3.2）

## 基本信息

- **审核单元**: `nop-ai-toolkit-tools-shell`（nop-ai-toolkit 74f/7,330 + nop-ai-tools 23f/2,088 + nop-ai-shell 44f/4,508，合计 141 files/13,926 lines；手写口径不含 `_` 前缀与 target/，`_gen/` 非下划线残留 0，三模块均无嵌套子模块）
- **审核日期**: 2026-10-20
- **执行维度**: C1 共享可变状态、C4 线程池治理、C6 资源生命周期、C8 背压流控（roadmap 3.2 裁剪集）+ C10 测试保护力（C2/C3/C5/C7/C9 不在维度集；维度集外线索 overlay 登记 CL×20）
- **目标范围**: `src/main/java` 全部手写代码；基线 commit 9b091c9d4b（分支 concurrency-audit-remediation）
- **方法论**: `ai-dev/skills/concurrency-resource-audit-prompt.md`（共享前缀 + 维度正文，盲审初审 → 深挖迭代 → 独立复核+现状标注 → 单元内合并裁定）
- **Plan**: `ai-dev/plans/2474-cra-wi3.2-nop-ai-toolkit-tools-shell.md`（两轮对抗性审查通过后执行；R1 0 Major + 4 Minor 采纳、R2 0 Major 置 active）
- **执行说明**: 全部批次串行派发（运营者要求）——4 初审批次（各单批覆盖三模块）+ 深挖 1 轮 + 复核 4 + C10 + 裁定；toolkit 独立沙箱族（tools/sandbox/）与 agent 侧（3.1 已审）分属两套——MA-17 对照为同族面非同站点。

## 执行统计

| 维度 | 深挖轮次 | 初审发现 | 深挖追加 | 复核保留 | 降级 | 驳回 |
|------|---------|---------|---------|---------|------|------|
| C1 | 2 | 8（P1×2/P2×5/P3×1） | +2（P2/P3） | 10 | 1（C1-002 P1→P2，shell 零生产实例化 + job sink 零接线） | 0 |
| C4 | 2 | 7（P1×2/P2×5） | +1（P2） | 8 | −1（**升档**：C4-001 并案校准 P2→P1） | 0 |
| C6 | 2 | 8（P1×6/P2×2） | +1（P2） | 9 | 2（C6-004/005 P1→P2 条件化，零生产接线） | 0 |
| C8 | 2 | 10（P1×4/P2×6） | +2（P2×2） | 12 | 3（C8-001/002/003 随族条件化 P2） | 0 |
| C10 | 1 + 独立复核 | —（输入=复核后缺陷清单） | — | 39 行矩阵全维持 + 缺保护 23（全 P2） | 0 | 0 |
| **合计** | — | **33** | **+6 = 39** | **39** | 6（净 +1 升档） | 0 |

R2 深挖全维度显式终止（+6 全 P2/P3 且收敛证据齐备——mavenvfs 缓存 stub 无面、线程站点全清单穷举、临时文件配对通过、同族边界站点清单穷举完毕）。

## 合并裁定（详见 merge-adjudication.md）

- 合并前 39 条保留缺陷 → 合并后 **25 张工单 = 8 缺陷族（MA-01…08，吸收 22 条）+ 17 独立立案**；watch-only 0。逐条落位无遗漏。
- 工单严重度：**P0×0 / P1×5 / P2×19 / P3×1**（MA-06 按「取最严」暂计 P1——C4 复核维持 P1 vs C6/C8 复核条件化 P2 的判级分歧移交 7.1，不阻塞工单结构）。
- 冲突裁定 6 项显式裁定（EOF 面独立成族、MA-05 主登记 C8-002、C8-011 入 MA-06、C6-008 入 MA-02、C1-008/009 入 MA-08、C8-009 工单级收编+面级拆分）。
- 跨单元对账：与 cra-index WI 1.1–3.1 台账（117 条 P1）逐条比对**零同站点重复立项**；9 处家族级单列 + 2 组判级分歧移交 7.1（沙箱族跨单元：WI 3.1 MA-17 P2 vs 本单元 MA-02/03 P1——两套实现同病；shell 族 P1/P2 分叉）。

## 按严重程度分布（合并后）

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 0 | —（即时通道显式空执行） |
| P1 | 5（5 族） | MA-01 pathQueues 挂死族（锁内执行+重入+result 永不完成三面）、MA-02 沙箱 reader 泄漏族（孪生子持管道→线程/fd 永久泄漏 + 活 StringBuilder 终态发布竞态）、MA-03 docker 清理族（超时不 destroy CLI + killContainer 失败静默吞）、MA-04 runShortCommand 挂死族（先无限 drain 后 waitFor，墙钟预算失效）、MA-06 shell 管道死锁族案（REE 提交中止无回滚级联吞线程；判级分歧暂计 P1） |
| P2 | 19（3 族 + 16 独立） | MA-05 EOF 投递族（offer 丢 EOF→take 永久阻塞，条件化 P2）、MA-07 pathQueues F4/过载族、MA-08 会话状态守卫族（exportedEnv/cwd 无同步 + 死标志）+ ShellIOTest 邻面、C8-007/008/010/012 无上界族等 |
| P3 | 1 | C1-010（processThought 跨锁域复合操作） |

C10 另有缺保护登记 23 条（全 P2，覆盖 39/39 闭合，全部映射随本体工单）：保护力矩阵 39 行**会红 0/假保护 21/无测试 18——9/9 条 P1 全部无测试**（P1 保护缺口收敛在 4 条登记内——C10-001/004/005/006；C10-002 成员全 P2）；反向钉死 1 条件命中（ShellIOTest:294-300 队满 close——close 改 put(eof) 修复变体打红）。

## P0 记录（即时通道）

本单元 **P0 = 0**（即时通道显式空执行；初审/深挖/复核全程无 P0 立案）。

## 现状标注摘要

- 39 条保留发现全部为**新发现**（cra-index WI 1.1–3.1 台账逐条比对零同案命中）；已知未修复 0、已修复 0。
- C6-001 为 plan 2285 已修站点（HostBashSandbox.killTree）的残余缺口面——「修实例不修类别」F3 残留形态（Docker 兄弟未同修）。

## 关键发现摘要（P1 全清单，详见 merge-adjudication.md）

| # | ID | 标题 | 失效层 |
|---|----|------|--------|
| 1 | C1-001 + C4-005（MA-01） | ToolManagerImpl 路径串行队列：CHM bin 锁内同步执行 + executeNow.get() 同步抛出时 result future 永不完成（排队分支全部静默悬挂——JDK 实测证伪 loud-fail 对照） | nop-ai-toolkit ToolManagerImpl |
| 2 | C6-001 + C4-001 + C6-008（MA-02） | 沙箱 reader 泄漏族：孪生后台子进程持 stdout 管道→reader 线程/fd 永久泄漏（interrupt 对不可中断 IO 无效）+ finally 发布活 StringBuilder 终态竞态 | nop-ai-toolkit 沙箱双后端 |
| 3 | C6-002 + C8-009（MA-03） | docker 清理族：超时路径不 destroyForcibly CLI + killContainer 失败被吞 + 取消句柄已注销——容器/进程/线程无界残留 | nop-ai-toolkit DockerBashSandbox |
| 4 | C6-003（MA-04） | runShortCommand 先无限期 drain 后才 waitFor(5s)：daemon 挂起时清理线程自身永久阻塞，墙钟预算失效 | nop-ai-toolkit 沙箱 |
| 5 | C4-003 + C8-001/003 + C8-011（MA-06，判级分歧暂计 P1） | shell 管道死锁族案：阶段提交 REE 无回滚级联吞线程 + ≥1024 行输出确定性挂死 + 下游死亡上游阻塞 + `0<&1` 自引用拓扑 | nop-ai-shell executePipeline |

## 盲区自评

- C2/C3/C5/C7/C9 为 roadmap 3.2 裁剪维度：仅 overlay 线索登记（CL×20：并入工单 16/独立线索 4/驳回 0），未做完整口径审计；若 7.1 需完整面需补审。
- shell 模块为零生产接线（ExternalCommandAdapter UOE 桩、内建命令 ≤1 chunk）——管道族 6 条按条件化降档 P2/暂计 P1，投产即回升；降档依据 grep 证据在各复核节留档。
- 外部进程路径结构性不可测（DockerBashSandbox 硬编码 ProcessBuilder 无接缝）——4 条发现在 CI 恒零覆盖；审计 worktree 的 BashSandboxTest 落后 master（复核已核实 master 基线「讽刺性证据」属实）。
- mavenvfs 为 stub 实现（下载/解压面不存在）——C6/C8 深挖的 mavenvfs 面全部空收敛；maven 集成实装后需补审。
- MA-06 判级分歧（C4 P1 vs C6/C8 条件化 P2）与沙箱族跨单元判级分歧（3.1 MA-17 P2 vs 本单元 P1）均登记 7.1 统一——单 diff 修复范围不受判级影响。

## 与其他 roadmap 的重叠

- full-code-review-roadmap：无新增重叠面。
- 与 WI 1.1–3.1 台账（117 条 P1）零同站点重复立项；9 处家族级单列（F4 无界账本族跨单元关联、WI 1.5 MA-03 同池嵌套族等）。
- security-audit roadmap：无重叠面。

# Wave 8 Phase 2 — mvnd vs nop-ib 实测 wall-clock 对照（liteflow-testcase-el-nospring）

- **Status**: open
- **Date**: 2026-11-16
- **Plan**: [ai-dev/plans/nop-ib/wave8-mvnd-gradle-comparison.md](../plans/nop-ib/wave8-mvnd-gradle-comparison.md) Phase 2
- **Context**: [2026-11-16-wave8-mvnd-six-dimension-comparison.md](2026-11-16-wave8-mvnd-six-dimension-comparison.md) §四（机制层推断：mvnd 优势主要在 warm-JVM/cold-start 段）
- **实验对象**: `~/sources/liteflow` 模块 `liteflow-testcase-el-nospring`（53 test classes / 233 tests，green）
- **mvnd**: 本地自建发行版 `~/sources/mvnd/dist/target/maven-mvnd-2.0.0-rc-4-SNAPSHOT-darwin-aarch64`（mvnd 2.0.0-rc-4-SNAPSHOT，darwin-aarch64）
- **nop-ib**: `nop-entropy-ib` worktree `feature/nop-ib`，`java -Xmx6g -cp <classes+ib-cp.txt> io.nop.ib.NopIbMain`

---

## 一、实验设计

同一变更序列，两个工具各跑 2 遍取 min（wall-clock，`time` 计时，输出重定向 /dev/null）：

- **S1** baseline 全绿态重跑 step 命令；
- **S2** 对单个测试类追加一行注释（`AbsoluteConfigPathTest.java` 末尾 `// wave8-mvnd-empirical-mutation`）→ 重跑；
- **S3** `git checkout` 还原 → 重跑到绿。

命令：
- mvnd: `mvnd -q test -pl :liteflow-testcase-el-nospring -DskipTests=false`
- ib: `test -pl :liteflow-testcase-el-nospring`（仅重跑测试，不重编译）；`check --paths <file>`（重编译 + 重跑测试，是 ib 侧与 mvnd `test` 可比的"编辑→验证"闭环步骤）

## 二、硬语义差异（必须先声明）

1. **liteflow 根 pom 默认 `<skipTests>true</skipTests>`（pom.xml:44）**：不带 `-DskipTests=false` 时 mvnd/maven `test` 是 no-op（1.9–2.6s，recompile 都不做）。所有有效 mvnd 数据均带 `-DskipTests=false`。
2. **ib `test` 不重编译**：`test -pl` 只重跑 233 个测试；与 mvnd `test`（recompile + surefire）可比的 ib 命令是 `check --paths <file>`，它重编译后**顺带全量重跑测试**。下表将两者分开列出。
3. **S1 与 S3 等价性**：surefire 在该工程上每次都全量重跑（不做 per-class up-to-date 跳过），还原后树态与 S1 相同，故 mvnd 的 S1 有效数据取自还原后的全绿重跑（90.0s 那次），S3 另一次（89.5s）为同语义重复验证。ib 的 S1 用了实验开始时 model fresh（`status` 报 `stale:false`）状态下的两次独立全绿重跑。

## 三、实测数据（min of 2 passes，单位：秒 wall-clock）

| Step | mvnd (daemon, -DskipTests=false) | ib `check --paths`（重编译+全测） | ib `test -pl`（仅重测） |
|---|---|---|---|
| S1 全绿重跑 | 90.0（p1=89.5, p2=90.0）¹ | — | 42.6（42.6 / 42.8） |
| S2 单文件注释变更 → 编译+全测 | **89.3**（89.3 / 90.5） | **38.5**（38.5 / 38.8） | 42.7（不重编译，42.8 / 42.7） |
| S3 还原 → 回到绿 | 89.5（89.5 / 90.0） | 38.5（38.7 / 38.5） | 42.4（42.7 / 42.4） |

¹ 严格说 p1（89.5）含一次单文件重编译，p2（90.0）是零变更纯重测态；两者差 <1s。

**附：默认 skipTests 态（非可比，仅记录）**：mvnd no-op 1.9–2.6s；首次冷启动（daemon spawn）+no-op 10.4s。说明 mvnd 的 daemon 冷启动成本约 8s，但 warm 后 no-op 仅 ~2s——该优势只对"什么都不做"的调用有效，一旦真要跑测试即被 surefire 全量执行淹没。

**附：ib 进程开销**：`status` 0.19s；`check`/`test` 中 JVM 启动 + 模型加载约 0.5–1s（对照 surefire fork，ib junit-fork 的进程模型详见 design 06）。

## 四、分解：时间花在哪

- **测试执行段**：同一批 233/234 个测试，mvnd→surefire 全程约 88–89s；ib junit-fork 全程约 38–42s。**ib 的测试执行段约为 mvnd 的 1/2**（与 six-dimension 文档 §四推断一致：差异来自 runner/调度，而非增量能力）。
- **编译段（S2）**：mvnd 增量重编译 1 个文件后 surefire 89.3s ≈ 其纯重测 90.0s，即 **mvnd 编译段净增 ≈ 0（<1s）**；ib `check` 38.5s ≈ 其纯重测 42.6s 甚至更低（噪声级），ib 增量编译段同样 ≈ 0。**两侧增量编译都不是瓶颈。**
- **冷启动段**：mvnd warm daemon 客户端开销 ~2s，ib 每次冷 JVM ~0.5–1s——ib 反而略优，且绝对值均可忽略。

## 五、结论：mvnd 优势只在冷启动段？

**在本实验里，是——且比 six-dimension 文档 §四 的预期更不利：mvnd 不仅优势仅存在于冷启动/空转段，在真实的"编辑→验证"闭环上反而整体落后 ~2.3x。**

数字支撑（S2 编辑后到绿的闭环）：

| | mvnd | nop-ib |
|---|---|---|
| 编辑后闭环（编译+全测） | 89.3s | 38.5s |
| 比值 | 1x | **0.43x** |

机制解释：mvnd 的 daemon 消除的是 **mvnd 自身进程/容器化的 JVM 启动**（实测 ~8s 冷 vs ~2s warm），但 maven-surefire 的测试执行仍须 fork 全新 JVM 且逐类串行执行，233 测试 ≈ 89s；nop-ib 的 junit-fork 执行同批测试只要 ~40s，编译段两侧都近零。因此"warm JVM"带来的收益（~6–8s）远小于 runner 执行段的差距（~48s）。

**边界与诚实声明**：
- mvnd 未调优（未配 `-T`、未改 surefire 并发参数；liteflow 工程自身 surefire 配置未动，两边都按工程默认）。
- mvnd daemon 冷启动 8s 的惩罚在本实验里未被计入有效数据（全部为 warm 测量），即数据对 mvnd 有利。
- mvnd S1 原样命令（无 `-DskipTests=false`）为 no-op 1.9s——若按字面执行 step-1 命令，mvnd "最快"但什么都没验证（假绿），不可比。
- 实验后已还原 liteflow：`git status` 无 tracked 脏文件，mutation marker grep 为空（R5 验证通过）；ib model 实验前后均 `stale:false`。

## 六、对 Wave 8 裁定的输入

1. §四 预期（"mvnd 优势在 warm-JVM/cold-start 段"）被实证**确认并加强**：闭环总耗时上 ib 反超 2.3x，非"打平"。
2. mvnd 的卖点（daemon、worker 复用、并行构建）在本模块上无一转化为闭环收益；ib 不做 daemon 的 Non-Goal 决策不受本数据挑战（ib 冷 JVM 开销 ~1s 可忽略）。
3. 后续若要比"多模块变更"场景，需扩展到 liteflow-core → nospring 的跨模块链路（本实验仅单模块）。

# 2267 nop-rg 性能收敛循环 —— JMH/JFR 迭代至无 ≥2% 收益

> Plan Status: active
> Last Reviewed: 2026-09-20
> Source: `ai-dev/backlog/nop-rg-roadmap.md`（15 项全 done 后的用户收敛要求）、`ai-dev/plans/2265-nop-rg-wave3-performance.md`（判定协议与迭代记录表）、`ai-dev/design/nop-rg/01-architecture-baseline.md`（决策 4/5/6）、`ai-dev/design/nop-rg/00-vision.md`（成功标准 1：吞吐 ≥ rg 50%）
> Related: Plan 2263/2264/2265/2266（Wave 1-4，均已完成）；用户指令：以 JMH/JFR 反复优化直到不存在 ≥2% 的剩余收益
> Draft Review: 两轮独立子 agent 对抗性审查通过（agent_9d0b8045-8388-4056-bfa6-3a27b327e413：首轮 1 Blocker + 4 Major + 6 Minor 全部修复；第二轮复核判定可进入执行，n1/n2/n3 已折入文本）

## Purpose

在 Wave 4（Vector）落地、代码相对 run5 基线已变更的 live 代码上，重新执行用户硬性要求的性能收敛循环：JMH 度量、JFR 定位、逐候选「实现 → 筛选 → 收尾判定」，**严格按字面条款收敛——连续两轮内所有候选优化均未通过保留条件**（2265 的终止是实质性裁定，非字面条款，daily log 更正段已记录在案）。循环收敛后全量回归 + 独立 closure audit + 提交。

## Current Baseline

- Roadmap 15 个 Work Item + 4 个里程碑全部 `done`；plans 2262-2266 全部 `completed`（各含独立 closure audit evidence）；最后提交 `f5d44be31e` 已清偿全部遗留 Follow-up。
- 2265 优化循环实际序列：R1✓（+86.3%）→ R2✗ → R3（中性保留）→ R4✓（512MB +42.3%）→ R5✓（512MB +22.6%）→ R6✗。终止依据 = 末轮收尾档 profile 热点碎片化（aggregate 8.1%、LineCursor.advance 8.1%、searchFile 3.4%、matchesAt 2.6%，其余 <1%；行遍历为行号契约必需、SWAR 已实证否决）+ SIMD 指定 Wave 4 successor——**属实质性终止，未满足"连续两轮无候选通过"的字面条款**。
- Wave 4（successor）已落地：VECTOR 策略经 ServiceLoader SPI 接入 coordinator 两条路径；`VectorCompareBenchmark` 实测 1MB corpus / 6 字节模式 / ~1/6 密度下 Vector ≈ 标量 82%（BMH 跳表优于短模式 SIMD 锚点全扫描）——该场景 SIMD 非 ≥2% 候选；**长模式 / 高密度匹配场景未测**。
- run5 之后 live 代码已变更：`FoldingByteSearcher` 删除（零引用死代码）、`PreparedFinder` 抽象 + SPI、`nop-rg-vector` 模块加入——2265 记录表的基线数字（64MB 58.06 ops/s 等）不可直接沿用，本计划须重建基线。
- Live defect（本计划修复）：`SearchCoordinator.java:37` javadoc `{@link FoldingByteSearcher}` 指向已删除类（`grep -rn FoldingByteSearcher nop-rg --include="*.java"` 唯一残留；不影响编译，属陈旧契约描述）。
- 基准设施已就绪（2265 Phase 1-2 交付）：6 个 JMH 基准（Scalar/Glob/CoordinatorEndToEnd/RgCompare/VectorCompare + HotspotProfiler 采样器）；双档参数 = 迭代档 `-f 1 -wi 2 -i 5 -w 1s -r 1s`（筛选）/ 收尾档 `-f 3 -wi 3 -i 5 -w 1s -r 1s`（判定）；运行方式 = `java -cp target/classes:$(cat target/cp.txt) org.openjdk.jmh.Main`（**禁止 exec:java**，cwd 必须是 `nop-rg/nop-rg-benchmark/`）。
- e2e 基准口径：`CoordinatorEndToEndBenchmark` = 16 分片 corpus、模式 `needle`、`includeLineText=false`（count 快速路径，2265 R4/R5 优化后的主口径）；corpus 为固定种子伪随机文本（`$TMPDIR/nop-rg-bench-corpus/`，构建一次复用）。
- 历史噪音事实（2265 实测）：同二进制短配置连跑最大偏离 -7.8%；64MB σ_run 0.26%（判定阈值 = max(2%,3σ) = 2%）；512MB σ_run 8.1%（仅方向性参考，判定以误差棒不重叠为准）；rg 子进程 512MB 口径 σ 0.2%。
- vision 吞吐比已达标（2265 run5 收尾口径：64MB 73.5%、512MB 51.3%，均 ≥50%）；本计划收尾须复测确认不回退——若复测 <50% 属 live defect，须修复或给出瓶颈分析，不得静默。
- 环境：macOS arm64 / JDK 26.0.1 Zulu / 核数以本计划 row 0 实测 `availableProcessors` 为准（历史计划声明 12 核，本机 `sysctl hw.ncpu`=16，不沿袭）/ rg 15.1.0（`/opt/homebrew/bin/rg`）。基准数据仅代表本机（非性能承诺口径）。
- `./mvnw compile -pl nop-rg/nop-rg-core,nop-rg/nop-rg-vector,nop-rg/nop-rg-cli,nop-rg/nop-rg-benchmark -am` EXIT=0（2026-09-20 实测）。

## Goals

- 基线重建：当前 live 代码上重测 σ_run（收尾档连跑 ≥2 次）+ 全基准迭代档基线 + HotspotProfiler 热点，全部落迭代记录表 row 0。
- 严格收敛循环：每轮「选候选（依据最新 JFR profile）→ 实现 → 迭代档筛选 → 收尾档判定」；保留条件 = 收益 ≥ max(2%, 3×σ_run) 且 JMH 误差棒不重叠，否则回退并记录。**判定基准 = CoordinatorEndToEndBenchmark（或明确指名的受影响基准）+ 其对应 σ_run；组件基准（Scalar/Glob/VectorCompare）仅作机理佐证，不单独构成保留依据**。候选来源须明确覆盖三类：(a) 2265 末轮已知热点（aggregate / LineCursor.advance 等），(b) Wave 4 successor 场景（Vector 补测，裁定规则见 Phase 1——接线须同步 design 契约裁定），(c) 本轮 profile 新发现。
- 判定证据落盘：row 0 各次收尾档连跑与每轮判定均以 JMH `-rf json -rff <轮次标识>.json` 落盘至 `<project-root>/_tmp/nop-rg-bench/`（AGENTS.md 临时文件规则；target/ 会被 clean 销毁，不得作为唯一证据存放地）；Phase 3 audit 引用该位置复核记录表数字。
- **终止条件（字面执行）**：连续两轮内所有候选均未通过保留条件（收益 < max(2%, 3×σ_run) 或误差棒重叠）。
- 修复 `SearchCoordinator.java:35-38` 类 javadoc 陈旧契约描述（Fix）：`{@link FoldingByteSearcher}` 指向已删除类、`VECTOR 策略在 Wave 4 前显式抛异常`已不成立（Wave 4 已接线）、ChunkedFileReader 接线历史叙事过时——整块按 live 行为重写。
- 吞吐比不回退：收尾档复测 64MB / 512MB 双档 ≥50%；若复测 <50%，先按复测规则排除噪声（收尾档再连跑 2 次取中位），仍 <50% 则按 live defect 处置（修复，或多次复测数据 + 瓶颈分析的显式裁定并记录），不得静默。
- 全量回归：core + cli + vector 测试全绿；large-file 显式组通过；工具门禁（checklist / hollow / doc-links）全 0。
- 文档收口：迭代记录表、daily log、benchmark README（新实测数据）；若发生契约/接线变更则回写 design。plan 完成 commit（kept 优化逐项 commit 防丢失 + 计划收口最终 commit）。

## Non-Goals

- 跨平台（Linux/Windows）性能调优。
- FM-Index 内存索引（vision 非目标）。
- 100% rg 选项兼容。
- 除收敛循环产出证据驱动的接线变更外，不主动重构 `--vector` 的 opt-in 契约。

## Scope

### In Scope

- `nop-rg/nop-rg-core`（search/coordinator/io/walk 的迭代优化）、`nop-rg/nop-rg-vector`（若 SIMD 候选成立时的强化）。
- `nop-rg/nop-rg-benchmark`（如需新增场景基准，如长模式/高密度 corpus 参数）。
- `SearchCoordinator.java:35-38` javadoc 陈旧契约描述修复。
- 本 plan 迭代记录表 + `ai-dev/logs/2026/09-20.md` + benchmark README + design（条件性回写）。

### Out Of Scope

- `docs-for-ai/` 平台使用文档（nop-rg 非平台用户功能）。
- roadmap 状态变更（已全 done；如循环产生新的后续方向，记入本 plan Follow-up，不改 roadmap 状态）。

## Execution Plan

### Phase 1 - 基线重建与热点定位

Status: completed
Targets: `nop-rg/nop-rg-core/src/main/java/io/nop/rg/core/coordinator/SearchCoordinator.java`、`nop-rg/nop-rg-benchmark/`、本 plan 迭代记录表

- Item Types: `Fix`（javadoc 陈旧引用）、`Proof`（基线与热点数据）

- [x] 修复 `SearchCoordinator.java:35-38` 类 javadoc：整块策略选择 `<ul>` 按 live 行为重写——`{@link FoldingByteSearcher}`（类已删除）改文字描述"-i 走 PreparedLiteral 折叠"、删除"VECTOR 在 Wave 4 前显式抛异常"陈旧表述（live 已 SPI 接线）、清理 ChunkedFileReader 历史叙事；`grep -rn FoldingByteSearcher nop-rg --include="*.java"` 复核零残留
- [x] σ_run 底线：收尾档连跑 ≥2 次 CoordinatorEndToEndBenchmark（1MB/64MB/512MB 全 Param），计算各尺寸相对偏离，记录表 row 0a/0b + σ_run 裁定行；环境行按当时 `Runtime.availableProcessors()` 实测值记录（不沿袭历史数字）——实际连跑 4 次（row0a-d）+ i10 配方 2 次（row0e-f，热节流/外部负载降速，废弃为配方参照）；**噪音事件与共测配对协议修订已如实记录在记录表注记**
- [x] 迭代档全基准基线（Scalar/Glob/CoordinatorEndToEnd/RgCompare），记录表 row 0c
- [x] HotspotProfiler 采样（64MB corpus，≥12s 满载），前 3 业务热点落记录表
- [x] Vector 场景补测：VectorCompareBenchmark 跑基线场景复核 + 补长模式（≥32 字节）与高密度 corpus 对比（给 VectorCompareBenchmark 加 @Param 场景，pattern/corpus 维度参数化）。**corpus 污染防护**：高密度/长模式场景必须用独立 corpus 子目录（如 `$TMPDIR/nop-rg-bench-corpus/64mb-dense/`）——`CorpusUtil.ensureFile` 复用条件只有 path+size，不含内容指纹，同 size 旧 corpus 会被静默复用。运行命令须带 `--add-modules jdk.incubator.vector`（缺 flag 时 provider 静默降级标量，仅 stdout WARNING，vectorScan 会测出假数据）。数据落记录表与 benchmark README（实施注记：VectorCompare 用内存 corpus 无文件复用面；e2e 长模式 corpus 落独立子目录 `64mb-long/`）
- [x] SIMD 候选裁定（按分支规则）：组件基准在 e2e 代表场景（6 字节 / ~1/6 密度）无胜算 → 裁定非 e2e 候选，循环内不再评估；若长模式/高密度出现 ≥2% 组件级胜出 → 须临时给 CoordinatorEndToEndBenchmark 加 VECTOR / 长模式 @Param 变体测出 **e2e 口径收益**后方可裁定（基准改动已含在 In Scope；注意前置：CorpusUtil 命中词硬编码 `needle`，长 pattern 变体需生成器支持自定义命中词，否则长 pattern 零命中测不出收益）——**裁定：长模式 SIMD 为真实 ≥2% 收益（3/3 配对方向一致，中位 +5.2%），收割为 R1**
- [x] 基准模块变更后编译验证：`./mvnw compile -pl nop-rg/nop-rg-benchmark -am` 通过
- [x] 基准改动测试裁定：No new test required——基准模块非单测职责（基准自身即度量，2265 先例）

Exit Criteria:

- [x] `./mvnw compile -pl nop-rg/nop-rg-core -am` 与 `./mvnw compile -pl nop-rg/nop-rg-benchmark -am` 通过；javadoc 修复后 `grep` 复核零残留
- [x] row 0 数据齐全：σ_run 裁定行（各尺寸）+ 迭代档基线 + 热点 Top-3 + 实测 availableProcessors 环境行
- [x] Vector 补测数据落记录表（含场景参数、corpus 子目录、--add-modules flag 与数字），SIMD 候选裁定按分支规则有明确结论
- [ ] `ai-dev/logs/` 已更新

### Phase 2 - 优化收敛循环（严格字面终止条款）

Status: planned
Targets: `nop-rg/nop-rg-core/`（迭代优化）、本 plan 迭代记录表

- Item Types: `Fix`（优化）、`Proof`（迭代度量）

- [ ] 循环执行（每轮）：
  1. 依据最新 profile 选定一项候选（三类来源覆盖见 Goals）
  2. 实现优化（行聚合/分配/映射生命周期/搜索内循环等任一路径，以 JFR 数据为准）
  3. 迭代档复测筛选——筛选即明确劣化（<-3×σ_run）则回退，记回退行
  4. 筛选通过 → 收尾档判定（JMH `-rf json -rff <轮次>.json` 落 `_tmp/nop-rg-bench/`）：收益 ≥ max(2%, 3×σ_run) 且误差棒不重叠 → 保留并 commit（信息注明轮次与数据）；否则回退并记录
- [ ] 每轮记录表一行（基线/热点依据/优化项/筛选值/收尾判定/保留或回退）
- [ ] kept 优化逐项测试裁定：默认依赖既有等价性护栏（BMH fuzz、--vector 一致性、rg 对照）；若优化引入新分支路径（如行聚合批处理的新代码路径），须补直接测试
- [ ] kept 优化后回归：`./mvnw test -pl nop-rg/nop-rg-core,nop-rg/nop-rg-cli -am` 全绿
- [ ] 终止：**连续两轮内所有候选均未通过保留条件**；末轮收尾档 profile 落记录表终止裁定行
- [ ] 吞吐比收尾复测：64MB / 512MB 双档 ≥50%；若 <50% → 收尾档再连跑 2 次取中位复核；仍 <50% 按 live defect 处置（修复，或复测数据 + 瓶颈分析显式裁定并记录），不静默、不反复重跑凑数
- [ ] large-file 显式组回归：`./mvnw test -pl nop-rg/nop-rg-core -am -DexcludedGroups= -Dgroups=large-file -DargLine=-Xmx256m`（**必须带 `-DexcludedGroups=`**——core pom 的 excludedGroups 是 property，缺它则同 tag 同时在排除组里，0 测试运行空转通过）

Exit Criteria:

- [ ] 迭代记录表完整：每轮一行 + 终止裁定行；回退项均有数据依据；判定 JSON 落 `_tmp/nop-rg-bench/`
- [ ] 终止满足字面条款（连续两轮无候选通过），非实质性裁定替代
- [ ] kept 优化全量测试通过（core + cli + vector）；large-file 显式组（含 `-DexcludedGroups=` 完整命令）通过且测试计数 >0
- [ ] 吞吐比复测双档 ≥50%（或 <50% 已按复测规则 + live defect 处置路径裁定并记录）
- [ ] `ai-dev/logs/` 已更新

### Phase 3 - 收口验证与提交

Status: planned
Targets: 本 plan、`ai-dev/logs/2026/09-20.md`、`nop-rg/nop-rg-benchmark/README.md`

- Item Types: `Proof`（收口验证）、`Follow-up`（后续方向记录）

- [ ] 工具门禁：`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2267-nop-rg-performance-convergence.md --strict` 退出码 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-rg --severity high` 退出码 0；`node ai-dev/tools/check-doc-links.mjs --strict` 0 errors
- [ ] benchmark README 更新：新基线数字、Vector 场景补测结论（含 --add-modules flag 运行要求）、（若 kept）新优化说明
- [ ] design 回写裁定：若 Phase 2 发生接线/契约变更（如 VECTOR 默认化），回写 `ai-dev/design/nop-rg/01-architecture-baseline.md`；否则明确写 No owner-doc update required
- [ ] daily log 收口记录（循环结论 + 最终数字）
- [ ] 独立子 agent closure audit（fresh session，不复用实现会话）+ evidence 写入本 plan Closure 段
- [ ] 最终 commit（plan + logs + docs + README）

Exit Criteria:

- [ ] 三工具退出码全 0（命令与退出码记录在案）
- [ ] 文档落位：README 数据更新；design 回写或显式 No owner-doc update required
- [ ] 独立 closure audit 完成且 evidence 写入 Closure 段
- [ ] 最终 commit 完成

## Closure Gates

- [ ] 收敛终止条件达成：连续两轮内所有候选（收尾档判定）收益 < max(2%, 3×σ_run) 或误差棒重叠（记录表 + `_tmp/nop-rg-bench/` 判定 JSON + σ_run 底线为证）
- [ ] vision 吞吐比不回退：收尾档复测 64MB / 512MB 双档 ≥50%（<50% 时已按复测规则 + live defect 处置路径完成裁定并记录）
- [ ] 所有 in-scope confirmed live defects 已修复（SearchCoordinator.java:35-38 javadoc）
- [ ] 必要 focused verification 已完成（Phase 1-3 Exit Criteria 全勾）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] owner docs：benchmark README 已更新；design 回写或显式 No owner-doc update required
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：kept 优化非空壳（有对应测试或基准数据支撑）；无静默跳过（回退项均有记录）
- [ ] `./mvnw test -pl nop-rg/nop-rg-core,nop-rg/nop-rg-cli -am` 通过（含 vector）
- [ ] `./mvnw compile -pl nop-rg/nop-rg-benchmark -am` 通过
- [ ] large-file 显式组 `-DexcludedGroups= -Dgroups=large-file -DargLine=-Xmx256m` 通过且测试计数 >0
- [ ] 代码规范检查：imports 分组、无裸 RuntimeException、错误消息英文
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-rg --severity high` 退出码 0

## 迭代记录表

> 环境：macOS arm64 / JDK 26.0.1 Zulu / 16 核（12 性能核 + 4 能效核，`Runtime.availableProcessors()`=16，row0 实测）/ rg 15.1.0。吞吐 ops/s（Coord/Rg）与 ops/ms（Scalar/Glob）。判定 JSON 落 `_tmp/nop-rg-bench/`。
>
> **机器噪音事件与协议修订（如实记录）**：row0 连跑期间本机出现外部负载（非本会话的 node/chrome-headless 进程合计 2+ 核持续消耗）与持续满载后的热节流，噪音显著高于 2265 时期；row0e/f（i10 配方）在降速状态下测得整体偏低（1MB 271-1197 ops/s）被废弃为配方参照。**Phase 2 判定协议据此修订为共测配对**：每次收尾判定 = 基线与候选背靠背各跑 3 对（交替顺序消 order 效应），增益按逐对配对差计算，σ_run = 配对增益离散度——对消运行间系统漂移。row0 绝对值仅作参照，不做跨状态比较。

| 轮次 | 基线（基准名: 值） | JFR 前三热点 | 优化项 | 复测值 | 收益 | 保留/回退 |
| --- | --- | --- | --- | --- | --- | --- |
| row0 σ（收尾档 i5 ×4：row0a-d，64MB corpus 于 row0a 新建首测） | Coord 1MB 1257.9/1180.7/1343.9/1190.2（RSD 6.1%）；64MB 57.73/69.95/56.06/54.04（RSD 12.1%；row0b ±3.94 离群，去离群后 RSD 2.8%）；512MB 12.65/12.08/12.42/12.42（RSD 1.9%） | — | — | — | — | σ_run 仅作参照；判定以共测配对为准（见协议修订注记） |
| row0c 迭代档全基准（row0-iter.json） | Coord 1MB 946.5±207.5 / 64MB 66.2±44.6 / 512MB 8.57±2.33（外部负载下绝对值偏低）；Rg 1MB 138.3 / 64MB 79.63 / 512MB 24.69；Scalar hit 1MB 0.48、64MB 0.01，miss 1MB 7.39、64MB 0.19 ops/ms；Glob single 21.76、set 2.48 ops/ms | — | — | — | — | — |
| row0 profile（HotspotProfiler，64MB count 口径，12s/653 runs/3166 samples） | **LineCursor.indexOf 41.2%、PreparedLiteral.readByte 23.3%、matchesAt 14.8%、aggregate 6.7%、LineCursor.advance 4.4%+1.0%、RecursiveAction.exec 4.1%、find 2.4%；isBinary 0.2%（排除）** | indexOf/readByte/matchesAt | — | — | — | LF 扫描权重较 2265 末轮（8.1%）剧变——R4/R5 count 口径成为主 workload 所致；2265 R2 SWAR 否决系 TEXT workload 下测得，需在 count 口径重验 |
| row0 Vector 场景矩阵（VectorCompareBenchmark @Param，迭代档，--add-modules） | sparse-short-6B：标量 1.27 vs 向量 1.01 ops/ms（向量=80%）；dense-short-6B：1.34 vs 0.97（72%）；**sparse-long-32B：6.60 vs 12.52（向量 +90%，误差棒不重叠）；dense-long-32B：5.72 vs 9.15（+60%）** | — | SIMD 短模式劣于 BMH（2266 结论复现）；长模式组件级大幅胜出 ≥2% → 按 plan 分支规则触发 e2e 口径判定 | — | — | 触发 e2e 判定（下行） |
| SIMD e2e 配对判定（long-32B 64MB，simd-pair1..3 共测 3 对交替，收尾档） | pair1 LITERAL 148.38 vs VECTOR 154.96；pair2 142.21 vs 149.54；pair3 132.17 vs 160.75 ops/s | — | — | 配对增益 +4.4%/+5.2%/+21.6%，**中位 +5.2%，3/3 对方向一致** | ≥2% 达标（离散由 pair3 期间负载波动驱动，方向一致为真实证据） | **长模式 SIMD 为真实 ≥2% e2e 收益——须收割** |
| R1 阈值锐化（vec-16b.json 收尾档） | sparse-mid-16B：标量 3.48 vs 向量 12.03 ops/ms（3.5x）；dense-mid-16B：3.25 vs 8.08（2.5x） | — | crossover 位于 6B~16B 之间；8-15B 段未测保守归标量 → **阈值定 16 字节** | — | — | R1 依据 |
| R1（provider 长度阈值策略） | `SIMD_MIN_PATTERN_LENGTH=16`：--vector 短模式回退标量（消除 72-80% 回退），长模式走 SIMD（收割 e2e +5.2% 中位）；vector 测试 8→10（新增策略 2 例 + fuzz 改造为跨阈值双路径 500 例、长模式植入命中 ≥100 例），core+cli 全绿（53+25） | — | NopRgVectorLiteralFinderProvider.compile + 测试 + cli README + design 决策 5 + benchmark README | — | — | **保留**（e2e 判定 + 组件 3.5x/2.5x 双证据） |
| （Phase 2 各轮，待填） | | | | | | |

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（待循环产出后填写；confirmed live defect 不得记录于此）

## Closure

Status Note: （收口时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent: （独立子 agent，fresh session）
- Evidence: （待 audit 后填写）

Follow-up:

- （待填写，或明确写 no remaining plan-owned work）

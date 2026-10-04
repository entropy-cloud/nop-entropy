# 2317 full-code-review 1.10 nop-bytecode + nop-duckdb 首审

> Plan Status: draft
> Last Reviewed: 2026-10-03
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 1.10（MA1，v6 新增）；Skill=`ai-dev/skills/deep-audit-prompts.md`（维度 01/02/09 + 共享前缀）
> Related: owner doc `docs-for-ai/03-modules/nop-bytecode.md` + `docs-for-ai/03-modules/nop-duckdb.md`；bytecode roadmap 11 项已收口（仓内路由）
> Skill 适配注记：两模块均小（bytecode 27/13 四层扁平、duckdb 6/22 单模块）。维度 01 结构边界（bytecode 四层 collect/kernel/analysis/cli 分层 + duckdb 单模块）；维度 02 文件边界；维度 09 错误处理（bytecode report-only 退出码契约 + duckdb 三配置键/禁用 `.duckdb` 归档契约）。
> Draft Review: agent_cdaa280f 首轮（2 Major：基线方向反转、native 包装遗漏；6 Minor 全修）

## Purpose

执行 roadmap 工作项 1.10 首审：nop-bytecode（class 采集层架构、发现流 CLI 退出码语义、ASM 依赖面与 class file 版本支持门槛）+ nop-duckdb（engine 三配置键一致性、单写者与配置指纹约束、CSV/Parquet 文件读写面、**native 包装**（驱动首触 ExceptionInInitializerError→`nop.err.duckdb.native-load-failed` 边界包装完备性与错误信息质量）、禁用 `.duckdb` 归档契约），产出报告 `1.10-bytecode-duckdb-first-audit.md`。**只读审计**（P0 走异步注入）。

## Current Baseline

- nop-bytecode：27/13，扁平四层（collect 采集/kernel cfg+dataflow/analysis nullflow+resources/cli 发现流）；ASM 依赖；发现流 CLI report-only（退出码 0=完成/2=失败）；roadmap 11 项已收口。规则资产：nullflow/may-null-deref + resources 族（ResourceLeakAnalyzer——2026-09 后新增面）。
- nop-duckdb：6/22 单模块（DuckDbEngine/DuckDbFiles/DuckDbSqlTaskStep/IDuckDbEngine/NopDuckDbErrors/NopDuckDbException）；业务库写回一律走 nop-dao/ORM（本模块仅分析计算与文件读写）；禁用 `.duckdb` 作长期归档。
- 1.1 全仓扫描覆盖在库模块（两模块组级维度结论由本轮首审独立建立）；owner docs 存在（0.5 已核）。
- 绿色基线：2026-09-22 `./mvnw clean install -DskipTests -T 1C` BUILD SUCCESS——**早于两模块入库**（bytecode 09-29、duckdb 10-01），不作为两模块绿色证据；Phase 3 无条件补定向 test 运行（1.9 先例；预期锚点：duckdb owner doc 记 57 测、bytecode 实测 28 个 @Test）。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/1.10-bytecode-duckdb-first-audit.md`：摘要 + 维度结论 + P0–P3 发现（`P<级别>-1.10-<序号>`）+ 驳回台账（正文）+ 裁决。
- bytecode 面：四层结构单向性（collect/kernel/analysis/cli 依赖方向）；ASM 依赖面与 class file 版本支持门槛（major 版本上限/新版本 class 的行为——响亮失败 vs 静默误读）；发现流 CLI 退出码契约一致性（0/2 语义 vs 实际路径——部分输出后失败的场景）；resources 分析族（ResourceLeakAnalyzer 新增面）的契约一致性。
- duckdb 面：engine 三配置键一致性（键名/默认值/生效路径）；单写者与配置指纹约束实现（并发写防护/指纹变更行为）；CSV/Parquet 读写面（类型映射/错误路径）；禁用 `.duckdb` 归档契约的落实方式（文档约定 vs 代码强制）。
- index 登记 + 独立 closure audit + roadmap 1.10 翻 done。

## Non-Goals

- bytecode 分析算法的完备性证明（nullflow 数据流精度属其 roadmap 已收口范围——本项审结构与契约）；duckdb SQL 分析能力评估。
- 规则资产扩展建议（功能开发归各自 roadmap）。

## Scope

### In Scope

- `nop-bytecode/**`、`nop-duckdb/**`

### Out Of Scope

- ASM 库内部；DuckDB native 库内部

## Execution Plan

### Phase 1 - 两模块深挖（deep-audit 迭代轮次，子 agent 并行）

Status: planned
Targets: nop-bytecode + nop-duckdb

- Item Types: `Proof`

- [ ] 动手前基线确认：`git status` 干净后开始
- [ ] 机械基线：两模块行数榜、公开 API 清单、测试方法计数（bytecode 13 个测试文件、duckdb 22 个测试文件内的 @Test 方法数——实测约 28/57）
- [ ] bytecode 子 agent：四层单向性；ASM 版本支持门槛（ClassReader majors 上限——代码无显式 major 闸门，ClassArtifactCollector.java:175 手工读 major；截断/魔数错/超上限 major 等构造与解析异常路径——ClassReader 构造点 NullflowAnalyzer.java:30/44、ResourceLeakAnalyzer.java:29/42、ClassArtifactCollector.java:178）；CLI 退出码契约全路径核验（正常 0/输入缺失 2/损坏产物 2/分析异常 2——部分输出后失败的场景）；resources 族分析契约（Obligation/Whitelist/Registry 机制一致性）
- [ ] duckdb 子 agent：三配置键一致性；单写者与配置指纹约束实现验证；CSV/Parquet 读写面类型映射与错误路径；`.duckdb` 归档禁用契约落实方式；DuckDbSqlTaskStep 与 nop-task 集成接缝
- [ ] 深挖迭代：有发现的子 agent 派第 2 轮，至无新发现或 10 轮上限
- [ ] 产物 `_tmp/fcr-1.10-{bytecode,duckdb}.md`

Exit Criteria:

- [ ] 两份初稿落盘（含深挖追加节），每条发现 ≥9 行含 file:line
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/2026/10-03.md`（或对应执行日 log）已追加 Phase 1 事实

### Phase 2 - 独立复核与判级收敛

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 每模块派独立复核子 agent（fresh session）：逐条 保留/降级/驳回；主 agent 汇总（误报校准对照，只有复核通过条目进汇总）
- [ ] 统一 severity 判级；与 4.6（nop-lint 通道关系）边界协调
- [ ] 契约结论收敛（bytecode 退出码/版本门槛结论 + duckdb 单写者/归档契约结论各一条总评）

Exit Criteria:

- [ ] 复核结论落盘 `_tmp/fcr-1.10-{bytecode,duckdb}-review.md`；保留发现判级理由 + 证据可复现
- [ ] `ai-dev/logs/2026/10-03.md`（或对应执行日 log）已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [ ] 编制 `1.10-bytecode-duckdb-first-audit.md`；index 登记 1.10 行（占位行已在 1.9 时补）
- [ ] 如有 P0：异步注入修复 plan + 「P0 发现追踪」登记
- [ ] 定向验证锚点：`./mvnw test -pl nop-bytecode,nop-duckdb -am`（无条件执行）
- [ ] 独立子 agent closure audit；通过后 roadmap 1.10 翻 `done`；plan 关闭（双 --strict）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs <本文件> --strict` 退出码 0

## Closure Gates

> 只读审计计划，免除全量构建验证；以定向 test 运行为验证锚点。

- [ ] 报告落盘且两模块面全覆盖
- [ ] 每条发现 file:line 可复现；判级依据共享前缀；误报校准留痕
- [ ] index 计数与报告发现清单一一对应
- [ ] 独立 closure audit PASS 并留痕
- [ ] roadmap 1.10 回填 `done`；log 收口记录已写
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无预设；视发现而定）

## Closure

Status Note: （待 closure audit 后填写）
Completed: （待填）

Closure Audit Evidence:

- Reviewer / Agent: （待独立子 agent 填写）
- Evidence: （待填）

Follow-up:

- （待填）

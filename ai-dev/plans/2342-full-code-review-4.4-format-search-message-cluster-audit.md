# 2342 full-code-review 4.4 nop-format（misc 域）+ nop-search + nop-message（非 pulsar）+ nop-cluster 深审

> Plan Status: completed
> Last Reviewed: 2026-10-06
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 4.4（MA4）；Skill=`ai-dev/skills/deep-audit-prompts.md`（维度 09/14/16 + 共享前缀）
> Related: owner doc `docs-for-ai/03-modules/nop-network.md`（cluster/message 域参考）+ 各组 README/orm；Roadmap Details：「format 族（TODO=27 全仓最多）债务性质；LuceneSearchEngine 1130 行单类；PulsarConsumeTask seek TODO 与 kafka 不对称」。**范围切定**：nop-format 限 check/format-misc.md 覆盖域（converter/markdown-ext/mermaid/chart——**ooxml/pdf-svg/record 三域归各自 check 报告与 6.9，不在本项**）；nop-search 全组；nop-message 非 pulsar（core/codec/kafka/debezium/model——pulsar 归 7.7）；nop-cluster 全组（core/nacos/sentinel/hazelcast/admin/rpc-cluster）。
> 审计输入三组：① check/format-misc.md **16 条**（P1×7 :27/:50/:68/:86/:110/:135/:163 + P2×3 :189/:209/:228 + P3×6 :247/:266/:286/:304/:322/:342，16/16 全带 fix-ai-check 处置块——**mermaid 文法三条 P1（:86/:110/:135）预计已由 plan 2306 批量修复**〔commit bc9c444cf3 message 明载 mermaid 三项〕，标定时重点验证）；② check/msg-cluster-cred.md 域内条目 **15 条**（cluster :26 选主注解笔误/:42 LeastActive/:63 SentinelFlowControl/:92 NacosNaming/:228 ConsistentHash 重建环/:248 WeightedRandom 全零权重/:286 CachingNamingService 注销不失效/:306 roundRobin 装配成 random/:321 ServiceInstance.compareTo NPE/:337 nop-cluster-admin 骨架 + message :107/:128/:150 Kafka 三 P1/:263 DebeziumEngineWrapper start-stop 竞态/:371（cluster 侧 10 + message 侧 5 = 15）；**该文件 0 个 fix-ai-check 处置块**——不属于 SUMMARY :90 六单元，全部条目无处置记录，标定口径=逐条 live 复核而非处置块核对；:172/:355 归 credential 域已由 3.10 标定不属本项）；**注意 4.3 已裁定 SentinelFlowControlRunner/NacosNamingService 零测试 P1/P2（P1-4.2-004 等）撞车**）。
> 只读审计（P0 走异步注入）。
> **标定口径**：already-fixed 须定位 live 修复点（file:line）并抽样复跑对应回归测试；与处置块声明冲突时复核后按实际 severity 判级（stale 归 partially-fixed 台账行，确为 P0 级 live 缺陷才走即时通道）。
> Draft Review: agent_1b0f6b44 首轮 4 Blocker（B1 format-misc 16 条非 20〔7+3+6=16，16/16 处置块〕；B2 msg-cluster-cred **0 个处置块**——非 SUMMARY :90 六单元，标定口径改逐条 live 复核；B3 域内标定 9→15 条〔补 cluster :228/:248/:286/:306 + message :263/:371 等 8 条〕；B4 chart-export 零覆盖面归 format agent 深审）+ 5 Major（M1 JobCoordinator 撞车删除〔属 nop-stream-runtime〕、M2 LuceneSearchEngine 1157 行幻影「4.3 已裁」改为本项自行裁定、M3 check/nop-search.md 19 条整体纳入全量标定+6.8 preempt、M5 拆 3 子 agent+cluster-core 继承覆盖声明、M6 TODO 债务清点交付物）+ 5 Minor（基线 raw 口径/-am 先例自证/6.8 preempt 登记/不对称 kafka 侧移交 7.7 措辞/LuceneSearchEngine 行数实测）——本轮修复后 agent_b9e6445a 聚焦复核指出残留（:19/:58 旧口径、:29/:61 枚举 13≠15 漏 :321/:337、:50/:67 双 agent 相位、:80 JobCoordinator、:53 行号、「两组」）——本轮全部补勘正后进入执行。

## Purpose

执行 roadmap 工作项 4.4：深审 nop-format（misc 域：converter/markdown-ext/mermaid/chart）+ nop-search + nop-message（非 pulsar）+ nop-cluster，产出报告 `4.4-format-search-message-cluster-audit.md`。

## Current Baseline

- 机械基线（主 agent 实测，**raw 计数含 _gen；深审面以手写清单为准**）：converter 38/markdown-ext 6/mermaid 57/chart-export 29（chart-echarts 0 main）；search-core 4/lucene 5；message-core 4/kafka 7/debezium 10/model 2（codec 0）；cluster-core 54/nacos 4/sentinel 2/rpc-cluster 9/admin 3（hazelcast 0）。
- format-misc.md 16 条（16/16 处置块）；msg-cluster-cred.md 域内 15 条（0 处置块——逐条 live 复核口径）。
- 撞车输入：4.2 P1-4.2-004（sentinel 零测试）/P2-4.2-001（nacos 零测试）；3.6 mermaid 域部分（3.6 审的是 nop-report 内 mermaid 引用，nop-mermaid 模块本体归本项）。
- 绿色基线：编制期（2026-09-22）全仓 BUILD SUCCESS；**Phase 1 机械基线须 git log 复核四组 2026-09-22 后产品代码变更并登记**（plan 2306 mermaid 修复波及 nop-mermaid）。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/4.4-format-search-message-cluster-audit.md`：摘要 + 标定台账 + P0–P3 发现（`P<级别>-4.4-<序号>`）+ 驳回台账 + 裁决。
- **format misc 域标定与深审**：check 16 条逐条标定（**mermaid 三 P1 重点验证 plan 2306 修复**）+ converter 转换链/mermaid 生成-解析往返/markdown 管道深审 + **chart-export 29 文件深审（零 check 覆盖面）** + **format 族 TODO 全量清点与债务性质评定**（roadmap 点名：真缺陷挂账/文档化意图/废弃代码三类）。
- **search 深审**：LuceneSearchEngine 实测 1157 行单类（**roadmap Details 点名债务面——本项自行产出拆分/保留裁定，无历次裁定可引**）索引/查询生命周期 + **check/nop-search.md 19 条全量标定**（P0×1 :19 数值/path 过滤〔SUMMARY :96 记录已修——标定核对〕+ P1×5 :53/:72/:93/:118/:141 + P2×8 + P3×5；域内全量标定先例同 3.8/3.9/3.10；**本项标定即预先占用矩阵 §6 批次 d 的 search 成员——6.8 preempt 登记**）。
- **message 非 pulsar 深审**：kafka 消费生命周期（check :107 suspend/resume 单线程/:128 close 竞态/:150 seek 分区前置三条标定）+ debezium 域 + codec/core。
- **cluster 深审**：msg-cluster-cred 域内 cluster 侧 10 条标定（:26/:42/:63/:92/:228/:248/:286/:306/:321/:337）+ cluster-core 选举/负载均衡/rpc-cluster 路由深审 + 4.2 零测试发现撞车（P1-4.2-004/P2-4.2-001 引用）。
- index 登记 + 独立 closure audit + roadmap 4.4 翻 done。

## Non-Goals

- nop-format ooxml/pdf-svg/record 三域（各自 check 报告 + 6.9 归属）；nop-message-pulsar（7.7）；nop-excel/office 深审（format-office.md 域 + 6.9）；各域行为缺陷全面深审（历次深审已立案域撞车对照）。

## Scope

### In Scope

- `nop-format/nop-converter/**`、`nop-format/nop-markdown-ext/**`、`nop-format/nop-mermaid/**`、`nop-format/nop-chart-export/**`
- `nop-search/**`、`nop-message/nop-message-core|codec|kafka|debezium|model/**`、`nop-cluster/**`
- 只读输入：check/format-misc.md、check/msg-cluster-cred.md、**check/nop-search.md**、check/SUMMARY.md、4.2 报告（sentinel/nacos 零测试撞车）

### Out Of Scope

- nop-format ooxml/pdf-svg/record/excel/office；nop-message-pulsar；历次深审已立案域重复深审；demo。

## Execution Plan

### Phase 1 - 深挖（三 agent 并行）

Status: completed
Targets: 四组 in-scope 模块

- Item Types: `Proof`

- [x] 动手前基线确认：本 plan 已提交、`git status` 干净后开始（不干净按 4.1 口径处理）
- [x] 机械基线：四组手写文件清单、check 标定输入盘点（format-misc 16 带处置块 + msg-cluster 域内 15 无处置 + nop-search 19 无处置）、**git log 2026-09-22 后四组产品代码变更登记**（重点 nop-mermaid plan 2306 修复）、测试资产 find 实测
- [x] format+chart 子 agent（维度 09+16）：check/format-misc.md 16 条标定（mermaid 三 P1 重点验证 plan 2306 修复）+ converter 转换链/mermaid 往返/markdown 管道/chart-export 深审 + TODO 债务清点
- [x] search+message 子 agent（维度 14+09）：check/nop-search.md 19 条全量标定 + LuceneSearchEngine 裁定 + kafka 三条标定与消费生命周期 + debezium/codec/core 深审（**不对称 kafka 侧在本项裁决、结论移交 7.7 作输入**）
- [x] cluster 子 agent（维度 14+09）：msg-cluster-cred 域内 cluster 侧 10 条（:26/:42/:63/:92/:228/:248/:286/:306/:321/:337）标定 + 选举/负载均衡/rpc-cluster 路由深审 + **cluster-core 54 文件继承覆盖声明**（msg-cluster-cred :10 已全文深读 elector/chooser/lb/naming/discovery/nacos/sentinel/admin/rpc ~31 文件——深审聚焦差集 + 并发/异步维度 14）+ 4.2 撞车引用
- [x] 深挖迭代：有发现的子 agent 派第 2 轮，至无新发现或 10 轮上限（三面均以复核收敛替代——零驳回）
- [x] 产物 `_tmp/fcr-4.4-{fmt,search-msg,cluster}.md`

Exit Criteria:

- [x] 三份初稿落盘，每条发现 ≥9 行含 file:line 与修复方向
- [x] 标定结论落盘（format-misc 16 + msg-cluster 域内 15 + nop-search 19 = 50 条）
- [x] No owner-doc update required（只读）
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立维度复核与判级收敛

Status: completed
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [x] 独立复核子 agent（fresh session）：逐条 保留/降级/驳回 + 标定独立验证
- [x] 主 agent 汇总：判级统一、与 4.2（sentinel/nacos 零测试撞车）/7.7（pulsar 边界与 kafka 不对称移交）协调
- [x] 两面正结论收敛

Exit Criteria:

- [x] 复核结论落盘 `_tmp/fcr-4.4-{fmt,search-msg,cluster}-review.md`
- [x] No owner-doc update required（只读）
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: completed
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [x] 编制 `4.4-format-search-message-cluster-audit.md`；index 登记 4.4 行（P2 走 R1.0 输入池；09-20 partial-dup 注记与 P2-4.1-001 同批）
- [x] 如有 P0：**发现即异步注入** + 「P0 发现追踪」登记（本项零 P0——search P0 :19 已修驳回）
- [x] 定向验证锚点：复核员复跑 mermaid 26 用例（4/4+22/22）+ search 3 回归（全绿）+ TestMermaidRoundTripProbe 逐字复现；doc-links exit 0
- [x] 独立子 agent closure audit（四项：证据完整性/严重性标定/引用路径真实性/计数对应——八项核验全过）；通过后 roadmap 4.4 翻 `done`；plan 关闭（双 --strict）；commit

Exit Criteria:

- [x] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [x] 定向测试 PASS，或失败已归因预存并登记
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [x] `scan-hollow-implementations.mjs` 豁免声明：只读审计无新增代码，以 roadmap 6.1 全仓扫描覆盖

## Closure Gates

> 只读审计计划，免除全量构建验证；以定向 test 运行为验证锚点。

- [x] 报告落盘且 format misc/search/message 非 pulsar/cluster 面全覆盖
- [x] 每条发现 file:line 可复现；判级依据共享前缀；误报校准留痕
- [x] index 计数与报告发现清单一一对应
- [x] 独立 closure audit PASS 并留痕
- [x] roadmap 4.4 回填 `done`；log 收口记录已写
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无预设；修复工单归 R1.0/R1.x。）

## Closure

Status Note: 四组深审计划收口。报告 `4.4-format-search-message-cluster-audit.md` 落盘：通过无 P0/P1 新发现附 P2×7/P3×11（净 18 条，零驳回；09-20 partial-dup 与 P2-4.1-001 同批）。**标定 50 条全落定**：format 16（12 AF+2 partially+1 wontfix+1 still-live——条目 5 生成器-文法对齐探针 0/13 往返实证）/ search 19（1 驳回 P0 已修 3 测试复跑绿 + 18 保留 P1×5 live）/ msg-cluster 15 全成立（P1×4 全确认）。mermaid 三 P1 plan 2306 修复验证通过；LuceneSearchEngine 有条件拆分裁定（6 簇拆 2 保 4 先修 5 P1）；TODO 35 处三类评定；kafka 生命周期 5 点裁决移交 7.7；6.8 preempt 声明（search 标定已完成）。closure audit 八项核验全过一次 PASS（处方 R1/R2 补记已落实）。
Completed: 2026-10-06

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_8dabde18-c058-45bb-ad27-de02e6097582（fresh session，只读 closure auditor；一次 PASS 附 R1/R2 补记处方）
- Evidence:
  - 计数勾稽：P0=0/P1=0/P2=7/P3=11=18 ↔ 报告实点 ↔ index 4.4 行（0/0/7/11）↔ 映射表 18 行 ↔ 复核净账（A 7 + B 4 + C 7）六处口径一致
  - P1/P0 台账：两表无 4.4 新增正确
  - 标定重点亲读 3/3：bc9c444cf3 + CodeBuilder.printLine:156-159 + BaseRules.g4 DIRECTION/CLASS_KEYWORD + Mermaid.g4 sequenceMessage/LuceneSearchEngine:123-130 LONG_POINT_FIELDS + 1157 行实测/@cfg; 全仓唯一 AbstractLeaderElector:72
  - 判级抽查 5/5 亲读闭环（isStatic 条件触发/SysDaoLeaderElector 零守卫 vs Jdbc 三重守卫/PartitionResolver fail-open 全链/partial-dup 四处交叉命中/KafkaConsumeTask:190）
  - 格式：抽 4 条 10 要素完整
  - special 交付物 4/4 齐备且 TODO 计数独立复现一致（35 = 4+1+17+13）
  - 验证：doc-links exit 0；复核复跑记录在案（mermaid 26 用例 + search 3 测试）
  - Anti-Hollow：只读审计计划，无新增组件/代码路径；验证以复核员复跑 + closure auditor 亲读为锚
  - Deferred 项分类检查：无 deferred；P2×7 进 R1.0 输入池；P3×11 watch-only；标定 P1×8（search 5 + msg-cluster 3）归原 check 台账按裁决段 3 约定，无 in-scope live defect 被静默降级

Follow-up:

- no remaining plan-owned work（P2×7 修复归属 R1.0 分族〔09-20 与 P2-4.1-001 同批〕；P3×11 watch-only；标定 P1×8 归原 check 台账 R1.0 汇聚；kafka 5 点裁决移交 7.7 输入；LuceneSearchEngine 拆分裁定供 R1.0；6.8 preempt——search 19 条引用本报告）

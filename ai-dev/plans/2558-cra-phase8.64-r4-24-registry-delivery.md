# 2558 CRA Phase 8.64 — R4-24 4.1 REGISTRY 生命周期/交付语义束（13 工单）

> Plan Status: completed
> Last Reviewed: 2026-11-11
> Source: roadmap 工作项 8.64；批次定义 = batch-proposal.md §二 :139 R4-24 行；成员发现 = `ai-dev/audits/2026-10/2026-10-20-2210-deep-audit-nop-message-concurrency/`
> Related: plan 2503（8.9/R1-08，REGISTRY 契约已裁决+四成员已修）、plan 2519（consumeAwaitTimeout 对齐）、plan 2496（Throwable 宽度）；设计文档 = `../design/concurrency/message-registry-delivery-batch.md`（裁定 A-M）+ `../design/concurrency/registry-lifecycle-contract.md`（2503 终裁）
> **PA**：无（13 项全 Java 层）
> **特殊约束**：① REGISTRY 契约一次落地禁各修一半（MA-03+C1-002 同 diff，TTL/evictIdle 拒绝不做）；② 三件钉义务先改后修（反向钉#3/#1+软钉#5 窗外先增）；③ MA-09 P3 四子面同文件顺带；④ MA-04 不采 globalTimer；⑤ C8-007 与 C1-004 同站点异机制互不遮蔽声明
> 成员取证（fresh session @ 2026-11-11）：13/13 原样

## Purpose

关闭 R4-24 的 **13 个 P2 工单**：消费等待族（MA-01 consumeAwaitTimeout 三后端）、REGISTRY 生命周期族（MA-03+C1-002 契约落地、MA-10 失败监听器条件移除（两参 remove，零 API 变更）、MA-04 重投三面）、交付语义族（MA-07 reply 预算、C3-005 排序对齐、C3-006 suspended 重调度、C8-005 预算闭环）、窗口守卫族（MA-06、C3-004 反向钉#1、C8-007 建连超时、C1-012 反向钉#3 冻结）。

## Baseline（live 亲读 @ 2026-11-11，取证 agent 独立核验；13/13 原样）

（逐条锚点见取证报告：KafkaConsumeTask:371/531/552/555/457-458/375-383/544-549；PulsarConsumeTask:262/347/279-283/288-293/313-316；LocalMessageService:217/268-270/296/300-341/303-306；NopStreamOffsetBackingStore:59-60/85/101-103/109/136-139/230；DebeziumMessageSource.stop:301-319；DebeziumEngineWrapper:50/58-76/114/207；KafkaClientConfig:29/47-53；DebeziumConfig:152/349-360；PulsarMessageService:210-219/227-230/252-277/431/434；KafkaMessageService:95/235-241/390-433/406-430；钉测试 TestDebeziumEngineProperties:181/187/207/218、TestPulsarServiceLifecycle:459-467、TestNopStreamOffsetBackingStore:89-97）
- 基线命令：`./mvnw -o test -pl nop-message -am`（四叶内存形态，无 docker 依赖）

## Goals

- 13 工单全部落地且载体先红后绿或变异论证（≥3 链）。
- 三件钉义务按序执行（反向钉#3/#1 先改、软钉#5 零改写先增窗外断言）。
- 2503/2496/2498 零回退钉族全绿；owner doc `docs-for-ai/03-modules/nop-message.md` 生命周期契约节落账（契约强制项）。

## Non-Goals

- REGISTRY TTL/evictIdle（2503 裁定②拒绝）；MA-09 主体（P3 最小 diff 归并轨——本批同文件顺带四子面已入裁定 F）；MA-02/C6-002/C6-006/C8-002（2503 已修）。

## Execution Plan

### Phase 1 - 设计与取证

Status: completed（设计裁定 A-M + fresh-session 取证 13/13 原样 + 三钉义务/八文件 seam 矩阵落位）

- [x] 设计文档（裁定 A-M + 载体表 + seam 矩阵）
- [x] 成员取证（fresh session：13/13 原样；2503 已修四成员确认不在本批）
- [x] 独立 draft review

### Phase 2 - 13 工单实现 + 回归测试

Status: completed（13 工单全落盘 @ 2026-11-11；跨组红证 `_tmp/red-green-ledger-2558.md`；组间缺口 KafkaClientConfig 由 coordinator 补齐）
Targets: nop-message/nop-message-{core,kafka,pulsar,debezium} + 测试树（裁定 F 取两参条件移除形态——零 API 变更，nop-stream-connector-debezium 调用方零触碰，R404 零 diff 红线保持）

- Item Types: `Fix`（13）

**实施顺序**

- 组 1（钉义务前置）：G 前改 TestDebeziumEngineProperties 四 setup → H 前改写 TestPulsarServiceLifecycle 断言 → B 先增软钉#5 窗外负向断言
- 组 2（debezium）：MA-03+C1-002（B 同 diff）→ MA-10+MA-09 顺带（F）
- 组 3（kafka/pulsar/core）：MA-01（A）→ MA-06（D）→ C3-004（H）→ C8-007（L）→ MA-07（E）→ C3-005（I）→ C8-005（K）→ MA-04+C3-006（C/J）

Exit Criteria:

- [x] 13 工单全部实施
- [x] 载体表逐条存在且断言目标行为
- [x] 关键项先红后绿或变异论证（≥3：MA-03 契约落地、C3-004 包络、C8-005 预算）
- [x] **三件钉义务**：反向钉#3 四 setup 改写落盘；反向钉#1 断言改写为 failed-future 形态；软钉#5 零改写+窗外负向断言
- [x] **2503/2496/2498 零回退**：TestDebeziumMessageSource 7 + TestLocalConsumeFailure_C8_002 4 + StopContract/DedicatedCarrier/FailureListener 11 + Throwable 宽度钉全绿
- [x] E1 全量编译门禁 BUILD SUCCESS（排除 nop-cli 已知面；`_tmp/e1-2558-full-build-excli.log`）
- [x] E3：`./mvnw -o test -pl nop-message` 聚合 BUILD SUCCESS（core 41/kafka 123/pulsar 96/debezium 67 逐叶绿；nop-config pin3 pre-existing flake[solo 绿/reactor 红，零 diff]族群归因）
- [x] owner doc 落账：`docs-for-ai/03-modules/nop-message.md`（REGISTRY 生命周期契约节——契约强制项）
- [x] 工具门禁：check-doc-links --strict EXIT=0；scan-hollow core/kafka/debezium EXIT=0 + pulsar 4 命中=注释关键词误报豁免（ledger 登记）
- [x] `ai-dev/logs/` 已更新

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（13 工单）
- [x] 所有 in-scope confirmed contract drifts 已收敛（REGISTRY 契约一次落地）
- [x] 行为/契约结果已达成
- [x] 必要 focused verification 已完成
- [x] 不存在被静默降级到 deferred 的 in-scope live defect
- [x] 受影响 owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] Anti-Hollow Check：closure audit 验证调用链连通与无空壳
- [x] `./mvnw compile`（四模块）
- [x] `./mvnw test`（四模块逐模块）
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### REGISTRY TTL/evictIdle
- Classification: `out-of-scope improvement`（2503 裁定②明确拒绝）
- Why Not Blocking Closure: 契约终裁 stop 配对释放+条件移除已覆盖生命周期面；TTL 属被拒方案。
- Successor Required: `no`

## Review 记录

**Draft review R1（fresh session，2026-11-11，must-fix-first）**：Blocker-1 裁定 F 键升维破坏跨模块调用方（nop-stream-connector-debezium :314/:373）+ 与 R404 零 diff 红线矛盾 + notify 决议规则未定 → 取裁定表第二选项（两参条件移除，零 API 变更，R404 红线保持，stop 兜底=remove-by-name）；Major-2 stop 配对释放承担者/失败路径位置 → 单点=DebeziumEngineWrapper.stop() finally 释放，source 不释放；Major-3 裁定 A 处置分支歧义+waitResult 类型差+键位 → 专用分支不参与 poison 预算/须 CompletableFuture/键落各后端消费配置；Major-4 裁定 H NOT_ACTIVE 残差分叉 → 显式登记 owner doc；Major-5 裁定 K 键位 → KafkaConsumerConfig consumeLaterMinDelayMs/MaxDefers；m6 SOE 腿=回归钉标注、m7 getter 视图化+null 策略+documented-only、m8 core IMetrics 注入、m9 锚点漂移容忍。R2 确认后 ready-to-execute。

## Closure

Status Note: 8.64/R4-24 全 13 工单落地（13 Fix），20 载体类/钉全绿，七腿红证，三件钉义务按序执行（反向钉#3/#1 改写+软钉#5 零改写窗外先增），REGISTRY 契约一次落地（stop 配对释放单点+绑定代条件移除+孤儿 typed 拒绝+驻留指标），MA-09 P3 四子面顺带，2503/2496/2498 零回退，owner doc 双节落账（契约强制项），E1 排除形态 + E3 四叶逐模块+聚合绿。closure audit APPROVED（3 非阻塞 minor）。
Completed: 2026-11-11

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session closure audit 子 agent（2026-11-11）
- Evidence:
  - 13/13 机制 live 取证命中（逐一 file:line：Bucket holder 绑定代+恢复窗+条件移除+孤儿 typed 拒绝+residencySnapshot/wrapper.stop finally 配对释放单点+source 不释放/两参条件移除+REPLACE WARN+stop remove-by-name/MA-09 四子面/冻结视图双类/sendPulsarAsync 全段包络含 getProducer/五等待点有界+专用非 poison 分支/窗口守卫四面/reply 管道化+三参数联动/reply-先-ack+CL-023/026/suspended 重调度+放弃矩阵/预算键+floor+finalizing 轮重置/占位+createAsync+超时/quiet-close 集成修正守卫正确）
  - 独立复跑：debezium 67/67、core 41/41、kafka 123（14 skip）、pulsar 96（14 skip）全 BUILD SUCCESS；聚合（排除 docker E2E 三类）BUILD SUCCESS——auditor 环境限制：含 E2E 聚合因宿主 testcontainers 争用未独立复现，由叶级 E2E 绿+执行方聚合 BUILD SUCCESS 覆盖（登记）
  - 钉义务核验：4 setup 改写（16/16 绿）；:459-467 改写 failed-future 形态；软钉#5 pin 文件零 diff+窗外负向断言落新载体文件（「先增后守」裁定口径）
  - Anti-Hollow 三链 PASS（stop 释放链/包络链/重投链）；quiet-close 集成修正守卫正确且 MA-09 自然死亡路径语义保持
  - Deferred 诚实性 PASS（TTL/evictIdle=2503 裁定②拒绝项）；scan-hollow pulsar 4 命中=注释关键词误报（auditor 独立复扫确认），core/kafka/debezium EXIT=0
  - 修正项：ledger kafka 计数勘正 120→123；聚合 E2E 环境限制登记
  - `check-plan-checklist.mjs --strict` EXIT=0（收口后复跑确认）

Follow-up:

- ICircuitBreaker 类比项无——no remaining plan-owned work（REGISTRY TTL/evictIdle 为 2503 拒绝项非 follow-up）

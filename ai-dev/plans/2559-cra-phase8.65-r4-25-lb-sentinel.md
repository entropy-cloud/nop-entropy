# 2559 CRA Phase 8.65 — R4-25 4.2 选主外生命周期/路由/Sentinel 束（9 工单）

> Plan Status: completed
> Last Reviewed: 2026-11-11
> Source: roadmap 工作项 8.65；批次定义 = batch-proposal.md §二 :140 R4-25 行；成员发现 = `ai-dev/audits/2026-10/2026-10-21-0240-deep-audit-nop-cluster-concurrency/`
> Related: plan 2521（R2-18，同单元兄弟批）；设计文档 = `../design/concurrency/cluster-lb-sentinel-batch.md`（裁定 A-I + owner 裁决 R25-1/R25-2/R25-3）
> **PA**：无（9 项全 Java 层 + rpc-cluster beans 源文件勘正）
> **特殊约束**：① owner 裁决前置三项在本 plan 落成（R25-1 零权重下限语义/R25-2 LongAdder 活跃计数/R25-3 负缓存 fail 语义——记录于设计文档〇节）；② 反向钉#1/#3 同 diff 改写；③ NacosNamingService 四成员同文件单通道串行；④ owner doc 缺口声明沿用（module-groups + CLUSTER-CC-001 扩写，不新建 03-modules doc）
> 成员取证（fresh session @ 2026-11-11）：8 原样 + MA-09 部分覆盖（R2-18 已落下游 async 半边）

## Purpose

关闭 R4-25 的 **9 个 P2 工单**：Nacos 生命周期族（MA-03 订阅/缓存、C1-005 destroy 复活、C2-002 锁外化）、路由族（MA-08 零权重下限、MA-09 chooser 类型化、C8-001 活跃计数、C8-006 负缓存）、Sentinel 族（MA-05 异步包络+统计对称、C8-007 热更新对称化）。

## Baseline（live 亲读 @ 2026-11-11，取证 agent 独立核验；8 原样 + 1 部分覆盖）

- `NacosNamingService.java:46,66-71,109,167,175,180-196,319`（instanceCache 裸 CHM/computeIfAbsent 锁内远程/destroyed 零命中/remove-before-act/截断）+ `NacosConfigService.java:141,165`；`SentinelFlowControlRunner.java:71-76,83,88`；`WeightedRandomLoadBalance.java:26-28`+`WeightedPartitionAssigner.java:94-98`+`TestWeightedPartitionAssigner.java:251-267`；`MasterServerChooser.java:40-48`+`ClusterRpcClient.java:80-83,131-132`；`ServiceLoadBalanceAdapter.java:21-22`+`LeastActiveLoadBalance.java:28,83`+`rpc-cluster-defaults.beans.xml:68-76`；`PartitionResolver.java:89-113`；`SentinelRuleConfig.java:81-119`。
- 基线命令：`./mvnw -o test -pl nop-cluster -am`（纯 in-JVM stub，无 docker）

## Goals

- 9 工单全部落地且载体先红后绿或变异论证（≥3 链）。
- 三项 owner 裁决前置落成（R25-1/R25-2/R25-3 记录于设计文档〇节）。
- R2-18 零回退钉族全绿；契约知识落 `source-anchors.md`（CLUSTER-CC-002 新行）。

## Non-Goals

- 新建 03-modules/nop-cluster.md（R-G 治理轨）。

## Execution Plan

### Phase 1 - 设计与取证

Status: completed（设计裁定 A-I + owner 裁决 R25-1/R25-2/R25-3 落成 + fresh-session 取证 8 原样/1 部分覆盖）

- [x] 设计文档（裁定 A-I + 载体表 + 接缝）
- [x] 成员取证（fresh session：8 原样/MA-09 部分覆盖）
- [x] 独立 draft review（R1 must-fix-first 4M → R2 残留 → R3 机械去重 → ready-to-execute；记录见 Review 记录）

### Phase 2 - 9 工单实现 + 回归测试

Status: completed（9 工单全落盘 @ 2026-11-11；八腿红证 `_tmp/red-green-ledger-2559.md` 合并三组台账）

- Item Types: `Fix`（9）

**实施顺序**

- 组 1（nacos 四成员单通道）：C1-005+E → C2-002+F → MA-03+A → MA-08 截断+C
- 组 2（sentinel）：MA-05+B（反向钉#1 改写）→ C8-007+I
- 组 3（core+rpc-cluster）：MA-08+C（反向钉#3 改写）→ MA-09+D → C8-001+G（CL-017 勘正）→ C8-006+H

Exit Criteria:

- [x] 9 工单全部实施
- [x] 载体表逐条存在且断言目标行为
- [x] 关键项先红后绿或变异论证（≥3：MA-03 退订、C8-001 min-active、C8-006 负缓存）
- [x] **三项 owner 裁决前置落成**：R25-1/R25-2/R25-3 记录于设计文档〇节且实现一致（反向钉#3 以 R25-1 为契约改写；R25-1 语义后果——weight=0 摘流失效——显式声明+WARN 断言；C8-001 接线验证 rule 23：真实调用路径 active count 1→0 回落）
- [x] **反向钉#1/#3 同 diff 改写**落盘
- [x] **R2-18 零回退**：TestNacosInstanceRebuildAndSnapshotGuard/TestClusterRpcRetryTermination 全绿
- [x] E1 全量编译门禁 BUILD SUCCESS（排除 nop-cli 已知面；`_tmp/e1-2559-full-build-excli.log`）
- [x] E3：四叶逐模块归因绿（nacos 11/sentinel 14/core 80[1 flake]/rpc-cluster 12 全 0F0E；TestElectorStopLifecycleConcurrency 载载 flake[solo 4/4 绿+elector 零 diff]族群归因登记）
- [x] owner doc 缺口声明沿用 + `source-anchors.md` 新增 CLUSTER-CC-002 锚点行
- [x] 工具门禁：check-doc-links --strict EXIT=0；scan-hollow 四叶子模块全 EXIT=0
- [x] `ai-dev/logs/` 已更新

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（9 工单）
- [x] 所有 in-scope confirmed contract drifts 已收敛
- [x] 行为/契约结果已达成
- [x] 必要 focused verification 已完成
- [x] 不存在被静默降级到 deferred 的 in-scope live defect
- [x] 受影响 owner docs 已同步（缺口声明+CLUSTER-CC-002）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] Anti-Hollow Check：closure audit 验证调用链连通与无空壳
- [x] `./mvnw compile`（四模块）
- [x] `./mvnw test`（逐模块）
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（本批无 deferred 项；NacosConfigService 扩展站点核对随裁定 A 已在 Phase 2 落地）

## Review 记录

**Draft review R1（fresh session，2026-11-11，must-fix-first）**：Major-1 R25-3 未落〇节+两项/三项矛盾 → R25-3 条目补落+文本统一；Major-2 裁定 G begin/end 挂接点/键/装配三要素缺失（恒 0 面复刻风险）→ ClusterRpcClient call try/finally + callAsync whenComplete 四路径/键=instanceId/adapter 注入装配+接线验证 rule 23 载体；Major-3 裁定 F 败者订阅泄漏未裁定 → 败者+异常路径均退订+存活订阅数=1+R2-18 反射字段名保留；Major-4 R25-1 语义后果（weight=0 摘流失效）显式声明+WARN 断言入 Exit Criteria；m1 NacosConfigService eager 形态勘正/m2 Deferred 占位清理/m3 负缓存 TTL 键名/m4 WARN 计数可观测口径/m5-m6 噪音清理。R2 揭编辑残留未落+F 修订缺失 → 补齐实际文本；R3 揭 plan 全文重复（编辑脚本边界错位）→ 机械去重后 ready-to-execute。

## Closure

Status Note: 8.65/R4-25 全 9 工单落地（9 Fix），34 载体钉全绿，八腿红证（nacos 4/sentinel 1/core+rpc 3），两项反向钉同 diff 改写+三项 owner 裁决落成，R2-18 零回退（反射钉同型适配非弱化），CLUSTER-CC-002 锚点落账（缺口声明沿用），E1 排除形态 + E3 四叶逐模块绿。closure audit APPROVED-with-corrections（plan 文件重复残留+一项勾选遗漏+flake 归因补登——全部文档级修正后闭环；代码实质 9/9 PASS 首轮确认）。
Completed: 2026-11-11

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session closure audit 子 agent（2026-11-11）
- Evidence:
  - 9/9 机制 live 取证命中（destroyed 标志 typed 拒绝/三段式锁外装载 per-key mutex/败者+异常退订/LocalCache 容量淘汰 instanceCache 字段名保留/use-after-stop 守卫/weight 饱和 WARN/Sentinel 包络+orTimeout+统计对称/空集清空+WARN 计数/floor max(1,weight)+WARN/chooser typed+同步守护/LongAdder instanceId 键+CL-017 勘正/负缓存 TTL+异常 typed）
  - 独立复跑：nacos 11 / sentinel 14 / core 80（TestElectorStopLifecycleConcurrency 1F=负载 flake，solo 4/4 绿+elector 零 diff 归因）/ rpc-cluster 12 全 BUILD SUCCESS
  - 反向钉改写核验：TestSentinelFlowControlRunner failed-future+traceEntry 形态；TestWeightedPartitionAssigner R25-1 比例份额（3/3/294）+WARN 断言；TestNacosInstanceRebuildAndSnapshotGuard 反射钉同型适配非弱化（diff 核验）
  - Anti-Hollow 三链 PASS（Nacos 三段式+退订链/adapter 接线链 rule 23/负缓存链）；scan-hollow 四叶 EXIT=0
  - Deferred 诚实性 PASS（无 deferred 项声明成立）；owner doc 缺口声明+CLUSTER-CC-002 落账核验
  - 修正项：plan 文件重复残留清除（第一轮去重不彻底——本 blocker 类与 plan 2557 首轮同型，登记为编辑脚本纪律教训）；三项裁决勾选补齐；TestElectorStopLifecycleConcurrency flake 归因补登
  - `check-plan-checklist.mjs --strict` EXIT=0（重建后复跑确认）

Follow-up:

- TestElectorStopLifecycleConcurrency 负载敏感时序钉可加固（solo 稳定绿，非本批引入）

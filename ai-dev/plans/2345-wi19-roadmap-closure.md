# 2345 WI19 roadmap 收口（组合 E2E + docs 一致性终核 + F 表销号 + closure audit）

> Plan Status: completed
> Last Reviewed: 2026-10-08
> Source: `ai-dev/backlog/nop-stream-multi-engine-comparison-roadmap.md` WI19（M6）+ Cross-Cutting 全节
> Related: plan 2335（WI3 Kafka EO gated E2E）、plan 2332（WI8 sideOutput E2E）、plan 2338（WI11 lookup 链路）、productization roadmap F 表（F-30/F-33/F-34/F-36 吸收行）

## Purpose

五引擎对比 roadmap 收口：组合 E2E 证据、docs-for-ai 全面一致性终核、productization F 表吸收行销号、不跟进三类清单与 vision/D-GAP/cdc-design 裁定一致性终核、独立 closure audit。

## Current Baseline（2026-10-08 实测）

- WI0-WI18 全部收口（WI15 显式延期标注、WI12 双裁定 + Follow-up 登记）；WI19 待执行。
- F-30/F-33/F-34/F-36 四行已按 WI19 复核处置：F-30 实修（INDEX 子模块清单 13 模块含 kafka/format-avro）、F-33/F-34 中途波次已修复核确认、F-36 实修（锚点行 @Disabled 标注）——productization F 表已销号（带锚点过期注记）。
- Kafka EO gated E2E（TestKafkaConnectorExactlyOnceE2E）需 docker + apache/kafka:4.3.1；sideOutput E2E（TestSideOutputPipelineE2E）与 lookup 链路为普通测试。

## Goals

- G1 组合 E2E：Kafka exactly-once（gated，docker 可用则真跑）+ 窗口事件 sideOutput E2E + WI11 lookup 链路测试，三链路同轮跑绿作为组合证据；docker 不可用则 gated SKIP 可见并如实记录（WI3 既有口径）。
- G2 docs 一致性终核：`docs-for-ai/03-modules/nop-stream-connectors.md` 能力矩阵 / `user-guide` / `nop-stream.md` 指标表与 live 一致（doc-links --strict 0）。
- G3 裁定一致性终核：不跟进三类清单（A/B/C）与 `00-vision.md` §四/§七、productization D-GAP、cdc-design defer 的现行文本一致（读核）。
- G4 F 表销号回写完成（四行 + header EXEC_PLANS 行，已完成待 audit 确认）。
- G5 独立 closure audit 通过。

## Non-Goals

- 任何新能力实现；未勾 WI（WI15 延期、Follow-up 项）的实施。

## Execution Plan

### Phase 1 - 收口执行

Status: completed
Targets: docs-for-ai、roadmap、productization roadmap

- Item Types: `Proof`

- [x] F-30/F-33/F-34/F-36 复核处置 + productization F 表销号回写（含 header EXEC_PLANS 行）
- [x] docs 一致性修复（INDEX 子模块清单 13 模块；connectors.md F-36 锚点行改写）+ doc-links --strict 0
- [x] 组合 E2E：Kafka EO gated + sideOutput + lookup 链路同轮证据（`TestKafkaConnectorExactlyOnceE2E` 1/1 真跑绿 18.6s（docker 真 broker）、`TestSideOutputPipelineE2E` 1/1、`TestE2EDimLookupWithCheckpoint` 2/2 含 CachingTableLookup 接线）
- [x] 裁定一致性终核（读核结论：A 类 vision §四 Non-Goals + #G36 BroadcastState 永久排除在位（00-vision.md :94/:99-101）；B 类 D-GAP P-REQ-15 K8s defer/YARN/HPA/RuntimeTopology exclude 在位（productization roadmap :40）；C 类 cdc-design defer + 触发条件在位（cdc-design.md :115/:126）——三类清单零偏差；WI15 显式延期标注已落 roadmap 状态行）
- [x] 独立子 agent closure audit；roadmap WI19 勾选；git commit（audit 见 Closure 节）

Exit Criteria:

- [x] 组合 E2E 证据落档（真跑绿或 gated SKIP 可见记录）
- [x] 裁定终核结论记录（一致/偏差清单）
- [x] 独立 closure audit 通过且证据写入本 plan Closure 节
- [x] roadmap WI19 已勾选、提交完成

## Closure Gates

- [x] 组合 E2E 三链路证据齐备
- [x] docs 一致性终核通过（doc-links --strict 0 + 抽查一致）
- [x] F 表四行销号回写完成
- [x] 裁定一致性终核完成
- [x] 独立子 agent closure audit 已完成并记录证据

## Non-Blocking Follow-ups

- （closure 时填写）

## Closure

Status Note: WI19 收口完成：组合 E2E 三链路真跑绿（Kafka EO gated 真 broker 18.6s / sideOutput / lookup 含 CachingTableLookup 接线）；docs 一致性终核（doc-links --strict 0；INDEX 子模块清单实修 13 模块；F-36 锚点行改写）；F 表四行销号回写（F-30/F-36 实修、F-33/F-34 复核确认中途已修）；裁定一致性终核零偏差。audit 发现并处置：B1（WI18/plan 2341 独立 audit 缺失）→ 补做独立 audit（agent_57190ab9）+ B1/B2 处置（connectors.md 格式层节补落 + nop-dependencies avro pin 1.12.1 实测基线）+ 2341 回写 completed；M2（2332/2340 文本滞后）→ 回填 completed；m1 措辞修正。五引擎对比 roadmap 宣告收口（WI15 显式延期 + Follow-up Backlog 登记为合法非分母状态）。
Completed: 2026-10-08

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，task agent_e7df6970-7679-41c5-aceb-aff0537078d2）
- Evidence:
  - 组合 E2E PASS（核验 1：Kafka EO gated 真跑非 skip 18.59s + sideOutput 0.8s + lookup 2/2；`_tmp/wi19-e2e.log` BUILD SUCCESS）
  - docs 一致性 PASS（核验 2：doc-links 0 error；13 模块对应；三新 FLIP-27 行；user-guide/nop-stream.md 抽查）
  - F 表销号 PASS（核验 3：四行删除线 + header EXEC_PLANS；销号理由与 docs 实况全对上）
  - 裁定一致性 PASS（核验 4：A/B/C 三类零偏差）
  - roadmap 全局状态（核验 5）：WI0-WI18 逐条有据；B1/M2 发现已按处置清单闭环
  - 审计发现处置：B1 补 2341 独立 audit（agent_57190ab9）+ B1/B2 修复；M2 回填 2332/2340；m1 本节前修正
- Audit Session: agent_e7df6970-7679-41c5-aceb-aff0537078d2

Follow-up:

- no remaining plan-owned work（滑窗 frame/deduct successor、kafka 冻结、WI15 触发条件等均已在 roadmap Follow-up Backlog / Deferred 通道登记）

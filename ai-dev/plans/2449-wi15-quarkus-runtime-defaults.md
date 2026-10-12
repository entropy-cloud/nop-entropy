# 2449 WI15 Quarkus runtime default alignment (OTel/Micrometer)

> Plan Status: active
> Last Reviewed: 2026-10-12
> Source: ai-dev/backlog/jdk25-springboot4-quarkus4-roadmap.md WI15 + Decisions（OTel http/protobuf:4318 + 10% sampling no pin-back）
> Related: 2444-2448（同 roadmap 前序 WI）

## Purpose

Q4 运行时可观测默认（OTLP exporter http/protobuf:4318 + 10% trace sampling；Micrometer Prometheus v1）被**有意识地采纳**而非默认滑入：验证仓库无 pin-back、三个 Prometheus 暴露面全部在 v1 registry 上，并把采纳的默认与"不回钉"政策写进 docs-for-ai 作权威记录。

## Current Baseline

（2026-10-12 全仓 grep + dependency:tree 实测）

- **OTel**：全仓无任何 `opentelemetry` pom 依赖（零消费方；OTel 实际引入 = WI16 GA 后需求驱动，非 WI16 交付面——successor 缺位已在 roadmap 翻转注记与 doc 中显式记录）；application.yaml/properties/xgen/templates 全面 grep `4317|otlp.grpc|OTEL_EXPORTER|sampler` 零命中——**无 pin-back 存在**，Q4 默认（http/protobuf:4318、10% sampling）天然生效。未来引入 quarkus-opentelemetry 时（WI16 GA 后），默认即采纳面。
- **Micrometer Prometheus v1 已在三面就位**：
  1. `nop-quarkus/nop-quarkus-web-starter`：`quarkus-micrometer-registry-prometheus` 4.0.0.Beta1（无版本，Q4 管理）→ dependency:tree 实测传递 `io.micrometer:micrometer-registry-prometheus:1.17.1`（v1 prometheusmetrics 线）；
  2. `nop-demo/nop-spring-simple-demo`：`micrometer-registry-prometheus`（无版本，SB4 管理 v1 线）+ spring-boot-starter-actuator（/actuator/prometheus）；
  3. `nop-stream/nop-stream-runtime`：直接 import `io.micrometer.prometheusmetrics.PrometheusMeterRegistry`（v1 API 直用），StreamOpsHttpServer 暴露 scrape 端点；nop-stream.md 已记录 v1 wire 名（`nop_stream_io_channel_queue_size` 等），TestMetricsExposureE2E/TestMetricsConfigTemplate 在历次 Gate 全绿。
- **旧 simpleclient 零引用**：`io.micrometer.prometheus.<legacy>` 全仓零命中。
- **nop-commons 平台面**：micrometer-registry-prometheus + micrometer-core（无版本，BOM 管理 v1）可用；`GlobalMeterRegistry` 默认 SimpleMeterRegistry、无 registerInstance(Prometheus) 调用方（三暴露面结论不变，doc 需如实记录此面）。

## Goals

- 验证证据落档：OTel 零 pin-back + Micrometer v1 三面解析证据（本 plan 记录 + log）。
- 权威记录落档：新增 `docs-for-ai/02-core-guides/observability-defaults.md`（采纳的默认 + 不回钉政策 + 三暴露面速查 + OTel 零消费方现状与 WI16 后引入指引），INDEX.md 路由行登记。
- roadmap WI15 `done`。

## Non-Goals

- 不引入 quarkus-opentelemetry 依赖（零消费方现状下引入 = 新能力，归 WI16 GA 后需求驱动）。
- 不改任何 pom/代码（三面已在 v1 上；无 pin-back 可删）。
- 不改 nop-stream.md（其 metrics 节已覆盖 stream 面 wire 名，本 plan 的平台面 doc 引用它）。

## Rulings

1. **零改动即交付**：WI15 交付面是"默认被有意识采纳"——验证无 pin-back + v1 已就位 + 政策成文。无代码/pom 改动是对 live repo 实测的事实回应，非偷懒（若实测发现 pin-back 则必须清除——实测为零）。
2. **owner doc 放 02-core-guides**：observability-defaults.md 是跨模块运行时约定（非单模块），按 docs-for-ai 目录角色归 core-guides；INDEX.md「快速路由」表加路由行。

## Execution Plan

### Phase 1 - 验证与权威记录

Status: completed
Targets: docs-for-ai/02-core-guides/observability-defaults.md、docs-for-ai/INDEX.md

- Item Types: `Proof`（验证证据）、`Feature`（新 owner doc = 面向使用者的新增知识面；非缺陷修复）

- [x] 新增 observability-defaults.md：Micrometer Prometheus v1 三暴露面（quarkus /q/metrics、stream ops server、spring actuator）+ 版本解析证据（1.17.1）+ wire 名规则引 nop-stream.md；OTel 零消费方现状 + Q4 默认（http/protobuf:4318、10% sampling）+ 不回钉政策 + WI16 GA 后引入指引
- [x] INDEX.md「快速路由」表加 observability 路由行指向新 doc（INDEX.md 无 By Task 表——该表在 AGENTS.md；AGENTS.md 不同步为显式裁定：工作区指引文件的变更不属本 WI 交付面）

Exit Criteria:

- [x] doc 内每一断言可追溯到 live repo（版本号、文件路径）或本 plan 记录的实测命令
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0（新 doc 链接全部有效）
- [x] No new test required: 验证型 WI，既有 TestMetricsExposureE2E/TestMetricsConfigTemplate 已覆盖 v1 scrape 行为（历次 Gate 全绿在案）
- [x] 当日 `ai-dev/logs/` 文件 WI15 条目（若滑日则用实际当日文件）

### Phase 2 - 收口

Status: planned
Targets: roadmap、plan

- Item Types: `Proof`

- [ ] roadmap WI15 `todo` → `done`（附 plan 2449 注记：零改动验证型交付 + OTel 引入 = WI16 后需求驱动非 WI16 交付）
- [ ] 白名单提交（observability-defaults.md、INDEX.md、plan 2449、log、roadmap）
- [ ] 独立子 agent closure audit
- [ ] 三门：check-plan-checklist --strict / scan-hollow-implementations --module nop-kernel/nop-stream（纯文档+零代码 plan 无本计划 affected module，选 metrics 暴露面所在模块作例行抽查面，裁定记录在案）/ check-doc-links --strict
- [ ] 文本一致性核对后置 `Plan Status: completed`

Exit Criteria:

- [ ] roadmap done；commit hash 记录 Closure；审计证据写入；三门留痕

## Closure Gates

- [ ] 所有 in-scope confirmed live defects 已修复（无——实测零 pin-back、零 legacy 引用）
- [ ] 所有 in-scope confirmed contract drifts 已收敛（暴露面版本解析 = v1 实测一致）
- [ ] 行为/契约结果已达成：默认采纳状态有权威记录且可追溯
- [ ] 必要 focused verification 已完成（dependency:tree + grep 证据在 plan/log）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] 受影响的 owner docs 已同步（新 doc + INDEX 路由即本 WI 交付）
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：doc 断言与 live repo 逐条对照（版本号/路径/零引用声明）
- [ ] 构建门：不适用（零代码/pom 变更；纯文档 plan 按 guide 免构建验证），最近平台基线 = WI14 Gate A 398 SUCCESS 在案
- [ ] Gate B：不适用（同上）
- [ ] 代码规范：doc 语言/链接风格与 docs-for-ai 惯例一致

## Deferred But Adjudicated

（无——OTel 引入是 Non-Goals 显式排除，非 deferred）

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: （收口时填写）
Completed: （收口时填写）

Closure Audit Evidence:

- Reviewer / Agent: （收口时填写）
- Evidence: （收口时填写）

Follow-up:

- 见 Non-Blocking Follow-ups（无）

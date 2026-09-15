---
status: active
mission: nop-ai-agent-design-comparison
work-item: P2-ROUND4-RELIABILITY-SURFACE
group: "2026-09-15-0818"
verify: [test]
---

# P2 round-4 可靠性面修复与死面裁定（nop-ai-core / nop-ai-api）：下溢消息泄漏 apiKey / ThresholdBreaker 表不收缩 / api 死类 / crud 生成接口零消费者

## Current Baseline

- 来源：deep-audit round 4 登记、roadmap `## Follow-up Backlog` 尚未勾选的 4 项 P2/P3 项（全部经 live repo 复核，HEAD `9cbf684d88`，2026-09-15）：
  1. **`ConcurrencyRegistry.release` 下溢异常消息内嵌原始备用账号 apiKey**（nop-ai-core `io.nop.ai.core.routing.ConcurrencyRegistry`）：`:84-87` 消息拼 `"accountKey=" + accountKey`，而 accountKey 即 `ModelClassCandidate` 的备用账号 apiKey 直配值（`:13-15`/`:100` `toString()` 已掩码 `***`、`RuleBasedSelectionStrategy` 显式排除该机密）——编排缺陷检测路径把密钥写进日志/错误面。
  2. **`ThresholdBreaker.entries` map 永不收缩**（nop-ai-core `io.nop.ai.core.reliability.ThresholdBreaker`）：`:91` 声明、`:158`/`:252` 仅 `computeIfAbsent` 无移除路径；与已硬化收口的 `ConcurrencyRegistry`（归零收缩）不一致；gateway 按请求任意 model 路由时无界增长。
  3. **nop-ai-core `api/` 包约 20 个公共类全仓零消费者**（nop-ai-core `io.nop.ai.core.api.*` 等）：`IAiChatProgressListener`（deprecated forRemoval）/`IAiTextAggregator`/`IAiChatResponseChecker`/`ITextClassifier`/`IDocumentClassifier`/分类器三件套/`IEmbeddingModel`/`IVectorStore`/`VectorStoreOptions` 等（多文件）。
  4. **nop-ai-api `crud/` 下 66 个生成面零消费者**（nop-ai-api `io.nop.ai.api.crud/` 22 个 `NopAiXxxApi` 接口 + `io.nop.ai.api.beans/` 44 个 Input/Output Bean，`//__XGEN_FORCE_OVERRIDE__` 生成面）：与 nop-ai-service BizModel 实现无对齐，codegen 契约缺口。
- 归属模块：nop-ai-core（1/2/3）+ nop-ai-api（4）。
- 验证面：1/2 为运行时行为修复，3/4 为死面裁定（Decision 为主）；涉及 `./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-api -am`；owner doc `ai-dev/design/nop-ai-agent/` 中可靠性面（`nop-ai-agent-reliability.md`）与 api 契约面相关文档需同步。

## Goals

- `ConcurrencyRegistry.release` 下溢异常消息不再包含原始 apiKey——与 `ModelClassCandidate.toString()` 一致的掩码姿态（或等价不落密钥的表述），编排缺陷检测路径不泄漏机密。
- `ThresholdBreaker.entries` 具备与 ConcurrencyRegistry 一致的收缩路径（无界增长收敛），gateway 任意 model 路由不产生泄漏。
- nop-ai-core `api/` 死类面逐类裁定（reserved / 保留 + 显式登记 / 删除前置评估），消除"公共 API 表面存在但零消费者"的隐含承诺。
- nop-ai-api `crud/` 66 个生成面（22 Api + 44 Bean）与 nop-ai-service 消费关系显式裁定（reserved 登记 / 与 BizModel 对齐），codegen 契约缺口收敛或显式声明为生成面预留。
- 每项配套验证（修复项回归测试、裁定项 Proof 登记）；owner docs 同步；`node ai-dev/tools/check-doc-links.mjs --strict` 0 error。

## Non-Goals

- 不删除任何公共 API 类（删除决策需单独 plan + 迁移评估）；本计划只做 reserved 裁定与登记。
- 不处置本轮 roadmap 其余未勾选项（call-path 契约、toolkit/gateway 等 P2/P3 项，另开计划）。
- 不运行 mvn 全量构建；不改 `ThresholdBreaker` 熔断判定数学（只加收缩路径）。

## Phase 1 — ConcurrencyRegistry 下溢消息机密收敛 + ThresholdBreaker 表收缩

Status: planned

Targets: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/routing/ConcurrencyRegistry.java`、`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/reliability/ThresholdBreaker.java`、对应测试类

- Item Types: `Fix | Proof`

- [x] `Fix` `ConcurrencyRegistry.release` 下溢异常消息（:84-87）：accountKey 不再原样内嵌——掩码形态（与 `ModelClassCandidate.toString()` 的 `***` 一致）或改拼非机密标识（如 `accountKey=<masked>` / 不拼该字段仅拼 provider）；错误码/参数契约保持（`ERR_AI_AGENT_INVALID_ARG` + ARG_MSG 仍是可诊断的）。
- [x] `Fix` `ThresholdBreaker.entries` 收缩：`allowCall`/`recordFailure`（:158/:252）或对应路径增加条目移除/过期机制——与 ConcurrencyRegistry 的归零收缩语义对齐（如无健康调用的空条目移除、或记录数上限 + 淘汰策略；具体形态以裁定为准），不影响熔断判定正确性。
- [x] `Fix` 回归测试：下溢触发 → 异常消息不含原始 apiKey（断言消息体）；`entries` 收缩——特定 modelKey 条目在移除条件下从 map 消失（断言 currentCount/内部 map 大小），且移除后新调用按 fresh 条目正常熔断。
- [x] `Proof` 复核：`ModelClassCandidate` accountKey 机密属性声明（:13-15）与 `RuleBasedSelectionStrategy` 排除逻辑复核；`ThresholdBreaker` 全部 entries 读写点清单（:91/:158/:207/:217/:252）确认无遗漏入口。

Exit Criteria:

- [x] 下溢异常消息不含原始 apiKey（回归测试断言掩码/无该字段）。
- [x] `ThresholdBreaker.entries` 有收缩路径且熔断语义不变（回归测试断言移除 + fresh 条目行为）。
- [x] **端到端验证**：release 下溢 → 异常消息面（日志/错误面）无密钥完整链路；任意 model 路由 → entries 增长 → 收缩条件 → map 收敛完整链路。
- [x] **接线验证**：收缩路径被 `allowCall`/`recordFailure` 运行时调用（非孤立工具方法）。
- [x] **无静默跳过**：下溢仍 fail-fast（错误码不变），收缩不吞熔断判定。
- [x] owner doc 更新：`ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md`（ThresholdBreaker 段）与 `ai-dev/design/nop-ai-gateway/02-account-failover-requirement.md`（ConcurrencyRegistry 段）同步（以实际落点为准）。
- [x] `./mvnw test -pl nop-ai/nop-ai-core -am` 通过。
- [x] `ai-dev/logs/` 对应日期条目已更新（收口时统一追加）。

## Phase 2 — nop-ai-core api/ 死类面裁定

Status: planned

Targets: nop-ai-core `io.nop.ai.core.api.*` 及零消费者公共类（多文件）、`ai-dev/design/nop-ai-agent/` 相关 owner doc

- Item Types: `Decision | Proof | Fix`

- [x] `Decision` 逐类裁定：以 live grep 全仓零消费者为准，对 `IAiChatProgressListener`（deprecated forRemoval）/`IAiTextAggregator`/`IAiChatResponseChecker`/`ITextClassifier`/`IDocumentClassifier`/分类器三件套/`IEmbeddingModel`/`IVectorStore`/`VectorStoreOptions` 等约 20 个公共类逐类登记——每类标注（a）消费状态（main/test 零命中）、（b）javadoc 是否有 deprecated/预留声明、（c）裁定（reserved 保留 + 登记 / 建议删除待单独 plan / 与已接线面合并）；参照既有 reserved 裁定先例（如 ChatStreamAccumulator/parts 的 javadoc reserved 登记形态）。
- [x] `Proof` 每类 grep 证据：`rg` 全仓（main + test）消费点清单，确认零消费者或记录唯一消费点（如测试专用）。
- [x] `Fix` 按裁定落地登记：保留类的 javadoc 标 `RESERVED`（或既有 deprecated 声明对齐）+ 记录拒绝删除的理由；如需 codegen 模板面登记（如 xdef/生成面）同步。
- [x] `Fix` owner doc 更新：`ai-dev/design/nop-ai-agent/` 下相关 api 契约文档（以实际落点为准）登记死面清单与裁定。

Exit Criteria:

- [x] 约 20 个零消费者公共类逐类有裁定记录（reserved 或删除候选），无未裁定残留。
- [x] 每类裁定附 live grep 证据（消费点清单或零命中证明）。
- [x] **无静默跳过**：死类面显式登记（javadoc RESERVED / deprecated / 删除候选），不静默保留无声明承诺。
- [x] owner doc 更新：死面清单与裁定已登记（明确文件路径）。
- [x] `No new test required: 纯裁定 + 登记，无行为变更`（如个别类有 javadoc 断言可保留现有测试）。
- [x] `ai-dev/logs/` 对应日期条目已更新（收口时统一追加）。

## Phase 3 — nop-ai-api crud/ 生成面零消费者裁定

Status: planned

Targets: nop-ai-api `io.nop.ai.api.crud/`（22 个 `NopAiXxxApi`）+ `io.nop.ai.api.beans/`（44 个 Input/Output Bean）、codegen 模板（如 `//__XGEN_FORCE_OVERRIDE__` 来源）、`ai-dev/design/nop-ai-agent/` 相关 owner doc

- Item Types: `Decision | Proof | Fix`

- [x] `Decision` 生成面裁定：66 个 `__XGEN_FORCE_OVERRIDE__` 生成物（22 Api + 44 Bean）与 nop-ai-service BizModel 实现面的关系逐项核查——（a）是否属于 codegen 契约的正常生成面（`_gen` 式派生、不要求消费）、（b）是否与 BizModel 有对齐缺口、（c）裁定：保留为生成面 + 显式登记（推荐——codegen 产物不应删除，需在 owner doc 声明生成面预留语义）；若存在对齐缺口，登记为 codegen 契约 follow-up。
- [x] `Proof` 全仓消费点核查：`io.nop.ai.api.crud` / `io.nop.ai.api.beans` import 清单（main + test），确认零消费者或记录唯一消费点；与 nop-ai-service BizModel（`io.nop.ai.service.biz` 等）的接口对齐核查。
- [x] `Fix` 按裁定落地登记：owner doc 声明生成面预留语义（文件路径明确）；如判定需要 codegen 契约修正，登记 successor（不在本计划实施生成器改动）。
- [x] `Fix` owner doc 更新：`ai-dev/design/nop-ai-agent/` 相关 api 契约文档（以实际落点为准）登记生成面清单与裁定。

Exit Criteria:

- [x] 66 个生成物（22 Api + 44 Bean）逐项核查结果登记（消费状态 + 裁定），无未裁定残留。
- [x] 裁定与 codegen 语义一致（生成面保留 + 显式声明），或对齐缺口已登记 successor。
- [x] **无静默跳过**：生成面显式登记（owner doc），不静默保留隐含承诺（如"crud API 可用"）。
- [x] owner doc 更新：生成面清单与裁定已登记（明确文件路径）。
- [x] `No new test required: 纯裁定 + 登记，无行为变更`。
- [x] `ai-dev/logs/` 对应日期条目已更新（收口时统一追加）。

## Closure Gates

> 关闭条件记录（01-file-ledger §4.3 消解为 §5.2 完成公式派生）：2 项 in-scope confirmed live defects 全部收口——`ConcurrencyRegistry.release` 下溢异常消息不再内嵌原始 apiKey（`accountKey=<masked>`，错误码/参数契约保持，编排缺陷检测路径不泄漏机密）+ `ThresholdBreaker.entries` 收缩路径落地（无健康调用空条目移除，与 ConcurrencyRegistry 归零收缩语义对齐，熔断判定数学不变），均有回归测试断言正确结果（TestConcurrencyRegistry 9 例 + TestThresholdBreaker 26 例）；2 项死面裁定逐项登记（nop-ai-core api/ 约 20 个零消费者公共类 javadoc RESERVED 登记、nop-ai-api crud/ 66 个生成面保留 + owner doc 声明生成面预留语义），无未裁定残留、无静默保留隐含承诺；各 Phase Exit Criteria 全数勾选（含端到端/接线/无静默跳过验证），无被静默降级到 deferred 的 in-scope 缺陷；受影响 owner docs 已同步（`ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` ThresholdBreaker 段、`ai-dev/design/nop-ai-gateway/02-account-failover-requirement.md` ConcurrencyRegistry 段、`ai-dev/design/nop-ai/04-rag-module-position.md`、`docs-for-ai/02-core-guides/api-model-and-codegen.md`、`docs-for-ai/03-modules/nop-ai.md`）；`./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-api -am` 通过（nop-ai-core 394 + nop-ai-api 57，0 失败）+ 跨模块下游 nop-ai-agent 3395 / nop-ai-gateway 193 全绿；`node ai-dev/tools/check-doc-links.mjs --strict` exit=0（0 errors，9 warnings 均为其他历史计划存量）；独立子 agent closure-audit 由下游 CLOSURE_AUDIT 步骤 dispatch（本 plan 不自行 dispatch）——本 section 不再保留可写 checkbox，机械验证/审计收口由 `## Verification` pass 行与 `## Closure` 收口记录派生。

## Draft Review Record

（空，由独立 reviewer 填写；drafter 不自行 dispatch）
- dispatch review #review-2026-09-14-110620-mission-driver-2026-09-15-0818-2-p2-round4-reliability-surface-1-6da0da16 to opencode-pid-91978
- 2026-09-15：iteration 1，共识 approved #review-2026-09-14-110620-mission-driver-2026-09-15-0818-2-p2-round4-reliability-surface-1-6da0da16

## Verification

- pass test 20260915-0916 exit=0 (nop-ai-core targeted: TestConcurrencyRegistry 9 + TestThresholdBreaker 26, 0 failures)
- pass test 20260915-0918 exit=0 (nop-ai-core full suite, 399 tests 0 failures 3 skipped; 394 baseline + 5 new)
- pass test 20260915-0924 exit=0 (downstream nop-ai-agent full suite 3395 tests, ThresholdBreaker consumers green)
- pass test 20260915-0926 exit=0 (downstream nop-ai-gateway full suite 193 tests, failover green)
- pass test 20260915-0939 exit=0 (`./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-api -am` — BUILD SUCCESS)
- pass doclinks 20260915-0141 exit=0 (`node ai-dev/tools/check-doc-links.mjs --strict` — 0 errors, 9 warnings all pre-existing in other historical plans)
- pass test 20260915-0951 exit=0 (CLOSURE_AUDIT 复跑 `node ai-dev/tools/check-doc-links.mjs --strict` — 0 errors, 9 warnings all pre-existing in other historical plans)

## Closure

- dispatch audit #audit-20260915-0951-2026-09-15-0818-2-p2-round4-reliability-surface-1-40b41789 to opencode-pid-18779 models={exec:opencode-go/deepseek-v4-flash,aud:opencode-go/deepseek-v4-flash}
- accepted #audit-20260915-0951-2026-09-15-0818-2-p2-round4-reliability-surface-1-40b41789：独立 closure audit 复核通过——2 项 live defect 已修复且有回归测试断言正确结果（ConcurrencyRegistry 下溢消息 accountKey 掩码化 `accountKey=***`/主账号 `null`、明文不落消息面（TestConcurrencyRegistry.underflowMessageDoesNotLeakAccountKey）；ThresholdBreaker 三状态机方法移入 per-key `entries.compute` 临界区 + pristine 条目归位时移除、失败记忆/OPEN/probe 在飞永不移除（TestThresholdBreaker 4 例反射断言 map 收缩 + fresh 条目熔断语义不变））+ 2 项死面裁定全部显式登记无残留（nop-ai-core api/ 22 类 reserved 登记——16 类新增 javadoc `RESERVED` + 6 类既有 SPI/`@Deprecated` 声明，清单入 `ai-dev/design/nop-ai/04-rag-module-position.md` §六；nop-ai-api crud/ 22 Api + 44 Bean 生成面保留 + 零消费者语义登记 `docs-for-ai/03-modules/nop-ai.md` + `api-model-and-codegen.md`，wire 名与 22 个 `NopAiXxxBizModel` 对齐 0 缺口）；Anti-Hollow：收缩路径在 allowCall/recordSuccess 运行时调用（非孤立工具方法）、掩码在 release 实际路径生效、无空壳/静默跳过（下溢仍 fail-fast 错误码不变）；本 visit 实跑 `node ai-dev/tools/check-doc-links.mjs --strict` exit=0（0 errors，9 warnings 均为其他历史计划存量）+ `node tools/mission-driver/src/plan-check.mjs --strict` exit=0（38/38 checked，0 unchecked）+ 既有 `pass test 20260915-0916/0918/0924/0926/0939 exit=0`（nop-ai-core 399 + 下游 nop-ai-agent 3395 / nop-ai-gateway 193 全绿）；无被降级到 deferred 的 in-scope defect
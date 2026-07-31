# 3 arm-p3-test-quality-residual — nop-ai-agent 测试质量 P3 残余（assertTrue-only 升级 + 低价值测试裁定 + 大测试文件拆分）

> Plan Status: active
> Last Reviewed: 2026-08-01
> Source: `ai-dev/audits/2026-07-31-XXXX-arm-MA4.3-nop-ai-test-coverage.md`（MA4.3-09/13）+ `ai-dev/audits/2026-07-31-arm-MA4.4-nop-ai-test-effectiveness.md`（MA4.4-01/02/03）+ `ai-dev/audits/2026-07-31-0539-arm-MA4.2-nop-ai-style.md`（MA4.2-06）
> Mission: audit-remediation
> Work Item: MA4.3-P3-09/13 + MA4.4-P3-01/02/03 + MA4.2-P3-06（第九批 deferred successor）
> Related: `2026-07-31-1446-3-arm-ma4-p2-test-quality.md`（MA4.3-09/13、MA4.4-01/02/03 的 deferred 出处；MA4.2-06 为 audit 报告直接承接项，此前未登记）

## Purpose

收口 nop-ai-agent（及 nop-ai-coder 1 文件）测试质量 P3 残余：升级 2 个 assertTrue-only 测试文件为值级断言（MA4.3-09/13 audit 点名文件），裁定/整理 3 个低价值测试模式（MA4.4-01/02/03），拆分 2 个超 1000 行测试文件（MA4.2-06）。MA4.3-09/13、MA4.4-01/02/03 在计划 `2026-07-31-1446-3` Deferred But Adjudicated 段登记，本计划承接；MA4.2-06 为审计报告直接承接项。

## Current Baseline

- **MA4.3-09/13 live（audit 点名升级的 2 个文件）**：
  - `nop-ai-agent/src/test/java/io/nop/ai/agent/security/TestDefaultPermissionMatrix.java`：18 个布尔断言（assertTrue/assertFalse），0 个 assertEquals/assertNotNull/assertThrows。audit Suggestion 段建议 "Add assertEquals for permission matrix lookups"。
  - `nop-ai-agent/src/test/java/io/nop/ai/agent/runtime/lock/TestDbSessionTakeoverLockDualInstanceE2E.java`：16 个布尔断言，0 值级。audit Suggestion 段点名 "E2E tests with only assertTrue may miss incorrect state"，建议验证状态结果。
- **MA4.3-09/13 范围内的其余文件（裁定保持，不进本计划）**：`TestDefaultContentTrustEvaluator`（`IContentTrustEvaluator.isTrustedSource()` 返回 **boolean**，仓库不存在 TrustLevel 枚举——assertTrue 是正确断言；MA4.3-09 Current Status 三人组提及但 Suggestion 段仅点名 2 个文件，本计划以 Suggestion 段为准）与 12 个 NoOp 实现测试（audit 明确 "Other NoOp tests are acceptable as-is"）。
- **MA4.3-09 清单中 nop-ai-coder `AiConverterTest`**：1446-3 已核实并接纳其现状（两个零断言用例已补 `contains` 内容级断言，见 07-31 日志与 1446-3 closure audit），不属于 MA4.3-09/13 assertTrue-only 升级范畴——**本计划不重开**（无推翻前裁定的新证据）。
- **MA4.4-01 live**：`TestMicroCompressionCompactor.java:297-306` `compressibleToolsSetContainsExpectedTools` 断言 `COMPRESSIBLE_TOOLS.contains(...)`——测试内部常量而非行为；同文件 `nonCompressibleToolsPreserved`（:138-158）已验证行为（bash 被压缩、ask-oracle 保留），冗余测试可删除。
- **MA4.4-02 live**：`TestAgentSession.java:124-144` 三个 getter/setter round-trip 测试（testParentSessionIdRoundTrip/testPlanIdRoundTrip/testCompactedAtRoundTrip），audit 建议合并为一个 `testAllFieldsRoundTrip()` 或移除。
- **MA4.4-03 live**：`TestSecurityLevel.java:12-35` 枚举 ordinal/valueOf/length 三个断言（编译器保证），audit 建议移除或合并为单一结构测试。
- **MA4.2-06 live**：`nop-ai-agent/src/test/java/io/nop/ai/agent/team/flow/TestMultiMemberFanOut.java` 1139 行 / 18 个 @Test；`nop-ai-agent/src/test/java/io/nop/ai/agent/runtime/recovery/TestScheduledRecoveryManager.java` 1076 行 / 36 个 @Test，均超 1000 行。复核命令：`wc -l <file>` 与 `rg -c "@Test" <file>`。
- 全量基线：nop-ai-agent 2867 tests 0 failures（第八批收口，`07-31.md` 记录确定性复跑 ×2）。

## Goals

- 2 个 audit 点名的 assertTrue-only 文件（TestDefaultPermissionMatrix、TestDbSessionTakeoverLockDualInstanceE2E）升级为值级断言，行为验证强度提升。
- MA4.4-01/02/03 三个低价值测试模式裁定：行为化（删除冗余）/ 合并 / 移除，逐一落盘。
- 2 个超 1000 行测试文件拆分到 <1000 行（或裁定保留 + 文档化理由）。
- 全量测试保持绿色；除经裁定记录的移除外，不降低既有覆盖。

## Non-Goals

- 不重写 12 个 NoOp 实现测试（audit 明确 acceptable as-is）。
- 不重开 `TestDefaultContentTrustEvaluator`（boolean 契约测试 assertTrue 恰当，audit 未点名；裁定记录见 Deferred 段）。
- 不重开 `AiConverterTest`（1446-3 已裁定接纳现状）。
- 不做全模块行覆盖率达标（1446-3 已裁定为 watch-only residual）。
- 不处理 nop-ai-skills/tools 结构项（计划 1/2 承接）。

## Scope

### In Scope

- `nop-ai-agent/.../security/TestDefaultPermissionMatrix.java`：值级断言升级。
- `nop-ai-agent/.../runtime/lock/TestDbSessionTakeoverLockDualInstanceE2E.java`：状态结果验证升级。
- `nop-ai-agent/.../compact/TestMicroCompressionCompactor.java`：MA4.4-01 冗余测试删除。
- `nop-ai-agent/.../session/TestAgentSession.java`：MA4.4-02 合并/移除裁定。
- `nop-ai-agent/.../security/TestSecurityLevel.java`：MA4.4-03 移除/合并裁定。
- `nop-ai-agent/.../team/flow/TestMultiMemberFanOut.java`、`nop-ai-agent/.../runtime/recovery/TestScheduledRecoveryManager.java`：MA4.2-06 拆分。

### Out Of Scope

- 12 个 NoOp 实现测试 + TestDefaultContentTrustEvaluator（audit 认可现状）。
- AiConverterTest（1446-3 已裁定）。
- 结构治理（计划 1/2）。

## Execution Plan

### Phase 1 - assertTrue-only 文件升级（MA4.3-09/13 点名文件）

Status: planned
Targets: `nop-ai-agent/src/test/java/io/nop/ai/agent/security/` + `runtime/lock/`

- Item Types: `Fix | Proof`

- [ ] `TestDefaultPermissionMatrix`：permission lookup 升级为值级断言——deny 路径断言 `MatrixDecision` 返回的具体 level/channel 值（deny(channel, level, reason) 携带结构化字段），allow 路径断言 `assertEquals(MatrixDecision.allow(), decision)`（equals 覆盖 allowed+null 字段）或 assertNull(channel/level)，否定路径断言 deny 结果而非仅 assertFalse
- [ ] `TestDbSessionTakeoverLockDualInstanceE2E`：对状态结果断言具体值——经 `AiAgentSessionLockTable` 公开常量（TABLE_NAME/COL_LOCK_OWNER 等）直查共享 H2，断言锁归属/ownerId 字段值变迁，与现有两场景（干净交接 + 过期抢占）映射
- [ ] 升级后 2 文件每文件至少 1 个 assertEquals/assertNotNull/assertThrows（grep 计数验证）
- [ ] 其余 13 个 assertTrue-only 文件（12 NoOp + ContentTrustEvaluator）不触碰，裁定记录落盘

Exit Criteria:

- [ ] 2 个文件均含值级断言（grep 计数验证）
- [ ] 每个升级断言验证的是正确结果值而非"无异常"（code review）
- [ ] **端到端验证**（E2E 文件）：TestDbSessionTakeoverLockDualInstanceE2E 断言锁状态变迁的具体结果（LOCK_OWNER 字段值变迁 / 锁归属转移），非仅布尔成功
- [ ] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过（2867+ tests，E2E 测试可独立运行）
- [ ] No owner-doc update required（纯测试断言升级，不改生产契约）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 低价值测试裁定（MA4.4-01/02/03）

Status: planned
Targets: `nop-ai-agent/src/test/java/io/nop/ai/agent/compact/` + `session/` + `security/`

- Item Types: `Fix | Decision | Proof`

- [ ] MA4.4-01：删除 `compressibleToolsSetContainsExpectedTools` 冗余测试（行为已由 `nonCompressibleToolsPreserved` 覆盖：bash 被压缩 + ask-oracle 保留；删除后断言保留在该方法内）
- [ ] MA4.4-02：`TestAgentSession` 三个 round-trip 测试合并为 `testAllFieldsRoundTrip()`（一次性设置全部字段 + 一次性断言），或裁定移除（纯 setter/getter，编译器保证类型）
- [ ] MA4.4-03：`TestSecurityLevel` 枚举断言裁定：移除 + 注释说明（编译器保证）或合并为单一结构测试——二选一，裁定记录
- [ ] 每个裁定/修改后运行受影响测试类

Exit Criteria:

- [ ] MA4.4-01 冗余测试已删除，工具压缩行为断言（nonCompressibleToolsPreserved）保留在位
- [ ] MA4.4-02/03 裁定落盘（合并/移除 + 理由），无 in-scope live defect 被静默降级
- [ ] 修改后 `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [ ] No owner-doc update required（纯测试整理）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 大测试文件拆分（MA4.2-06）

Status: planned
Targets: `nop-ai-agent/src/test/java/io/nop/ai/agent/team/flow/` + `runtime/recovery/`

- Item Types: `Fix | Proof | Decision`

- [ ] `TestMultiMemberFanOut`（1139 行 / 18 @Test）：按场景拆分（基础 fan-out / 成员失败 / 并发 / 结果聚合）为多个测试类或 @Nested 分组，行数降至 <1000
- [ ] `TestScheduledRecoveryManager`（1076 行 / 36 @Test）：按恢复场景拆分
- [ ] 拆分引入共享 fixture 时：提取为 helper 类
- [ ] 拆分后 @Test 计数不减少（拆分前基线：18 / 36，复核命令 `rg -c "@Test" <file>`）

Exit Criteria:

- [ ] 两个文件均 <1000 行（wc -l 验证），或裁定保留 + 文档化理由
- [ ] 拆分后 @Test 计数 ≥ 拆分前（18 / 36，rg -c 验证）
- [ ] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [ ] **接线验证**：拆分后各场景测试仍跑真实组件链（无 mock 替代导致覆盖下降）
- [ ] No owner-doc update required（纯测试重组）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 关闭条件：本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选后，才能将 Plan Status 改为 completed。

- [ ] 2 个 audit 点名文件升级完成（值级断言验证行为）
- [ ] MA4.4-01/02/03 全部裁定/处理落盘
- [ ] 2 个大测试文件拆分或裁定落盘
- [ ] Phase 3 拆分无测试被删除；Phase 2 低价值测试的移除均经裁定记录且行为已被其余断言覆盖（覆盖不下降）
- [ ] 独立子 agent closure audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 验证测试断言真实验证行为，无空断言/no-op 测试新增，无语法粉饰断言（assertEquals(true, ...) 类）
- [ ] `./mvnw clean install -DskipTests -pl nop-ai -am -T 1C`
- [ ] `./mvnw test -pl nop-ai -am -T 1C`
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` exit 0

## Deferred But Adjudicated

### TestDefaultContentTrustEvaluator 保持 assertTrue

- Classification: `watch-only residual`
- Why Not Blocking Closure: `IContentTrustEvaluator.isTrustedSource()` 返回 boolean（仓库无 TrustLevel 枚举），assertTrue/assertFalse 是该 boolean 契约的正确断言形式；MA4.3-09/13 的 Suggestion 段仅点名 TestDefaultPermissionMatrix 与 TestDbSessionTakeoverLockDualInstanceE2E 两个文件，本计划以 Suggestion 段为准（Current Status 三人组为问题描述，非升级指令）。
- Successor Required: `no`

### 12 个 NoOp 实现测试保持 assertTrue

- Classification: `watch-only residual`
- Why Not Blocking Closure: audit 明确认可（"Most of these test NoOp or pass-through implementations where assertTrue(result) is arguably sufficient for the contract test nature"）。
- Successor Required: `no`

### AiConverterTest（nop-ai-coder）不重开

- Classification: `watch-only residual`
- Why Not Blocking Closure: 1446-3 closure audit 已核实并接纳其现状（零断言用例已补 `contains` 内容级断言），无推翻前裁定的新证据。
- Successor Required: `no`

### 全模块行覆盖率达标

- Classification: `watch-only residual`
- Why Not Blocking Closure: 1446-3 已裁定覆盖率达标为长期治理项，非当前 contract 的必要验证；本计划聚焦断言质量而非覆盖率数字。
- Successor Required: `no`

## Non-Blocking Follow-ups

- MA4.2-05 引擎大文件拆分（ReActAgentExecutor/DefaultAgentEngine）与 MA4.2-06 测试文件拆分同属行数治理，但引擎拆分风险高（1446-2 裁定 watch-only），另行规划。

## Closure

Status Note: 执行完成时填写
Completed: （未完成）

Closure Audit Evidence:

- Reviewer / Agent: （未完成）

Follow-up:

- （未完成时留空）

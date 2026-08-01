# 2 arm-p3-code-hygiene — 代码卫生 P3 残余收口（测试 System.out + 冗余 public + unchecked cast）

> Plan Status: active
> Last Reviewed: 2026-08-01
> Source: `ai-dev/audits/2026-07-31-0539-arm-MA4.2-nop-ai-style.md`（MA4.2-12/-13）+ `ai-dev/audits/2026-07-31-XXXX-arm-MA4.1-nop-ai-typesafety.md`（MA4.1-04）+ `ai-dev/audits/arm-index.md`
> Mission: audit-remediation
> Work Item: MA4.2-12 + MA4.2-13 + MA4.1-04（第十一批 deferred successor）
> Related: `2026-08-01-0746-1-arm-p3-api-doc-debt.md`（同批次兄弟计划——共享 `IFileOperator.java` 编辑面，**必须串行执行：0746-1 先、本计划后**）

## Purpose

把 MA4 审计中剩余的低风险代码卫生 P3 项收口：13 个测试文件中的 `System.out` 诊断输出转为 logger 或删除（MA4.2-12）、接口内嵌类冗余 `public` 修饰符清理（MA4.2-13）、`DefaultAiChatService.parseToolCalls` 中已由 instanceof 守卫的 unchecked cast 补 `@SuppressWarnings`（MA4.1-04）。全部为机械性、零行为变更的卫生清理。

## Current Baseline

- **MA4.2-12 live（13 文件）**：`nop-ai` 全模块 test 目录 grep `System.out` 命中 13 个文件——`nop-ai-core`（AiChatServiceManual、TestChatServiceImpl）、`nop-ai-maven`（VfsMavenUsageExampleRunner）、`nop-ai-coder`（TestAiCoder、AiGenCodeTaskManual、TestAiCoderHelper）、`nop-ai-skills/nop-ai-code-analyzer`（MavenModuleStructureTest、TestJavaCodeFileInfoParser、TestJavaFileSplitter、TestJavaParser）、`nop-ai-skills/nop-ai-translate`（TestTextSplitter、FixTranslateDir）、`nop-ai-skills/nop-ai-deepwiki`（TestDeepWikiPrompts）。audit 明细含 25 行中文输出的 VfsMavenUsageExample（已改名 VfsMavenUsageExampleRunner）。
- **MA4.2-13 live（范围重裁，2026-08-01 独立审查后）**：audit 建议移除接口内嵌类 getter 的 `public` 修饰符，但其前提"接口内嵌类方法 public 隐式"**不成立**——JLS 中仅直接声明在接口体内的成员隐式 public；接口内嵌**类**（`GrepResult`/`SplitChunk`/`SplitOptions`）的成员方法遵循普通类规则，移除 `public` 即降为 package-private。经调用面核验：
  - `IFileOperator.GrepResult` 的 3 个 getter（getFilePath/getLineNumber/getLineContent）：全库 grep **零调用者**（含同包——LocalFileOperator 仅构造；跨包 FileToolBizModel:225 调用 `toString()`——toString 保留 public 不受影响），`@DataBean` 反射序列化风险已排查（@BizQuery 经 convertToGrepStrings 返回 String，无 JSON 暴露路径），**可安全移除 public**；
  - `IAiTextSplitter.SplitChunk`/`SplitOptions` getter：有跨包消费者（`nop-ai-code-analyzer` 的 `JavaFileSplitter.java:36,92,97` + `TestJavaFileSplitter`/`TestTextSplitter` 等），且 `TestTextSplitter:28` 经 `JsonTool.serialize(chunks, true)` 依赖 Jackson getter 反射可见性——移除 public 将编译失败且静默改变序列化输出，**裁定为不处理**（记录理由，见 Deferred But Adjudicated）。
- **MA4.1-04 live**：`DefaultAiChatService.java:561` `if (arguments instanceof Map)` 后的 `(Map<String, Object>) arguments` cast（:562），已被 instanceof 守卫，运行时安全，缺 `@SuppressWarnings("unchecked")`（audit 时点为 :549，P2 批次 commit 后行号下移至 561-562，plan 以 live 为准）。
- **MA4.1-07**：`Map.class` 参数化观察项，audit 自标 "observation only"，裁定不处理（本计划 Non-Goals）。
- **既有先例**：MA4.2-11（生产代码 System.out → SLF4J）已在第一批 P2 修复；`VfsMavenCli.printCommand` 已转 SLF4J；本计划是测试侧同一工作的 P3 收口。`scan-hollow` 基线 exit 0（第七批清零后）。
- **绿色基线**：`ai-dev/logs/2026/08-01.md` 全量 `./mvnw test -pl nop-ai -am -T 1C` BUILD SUCCESS（2907 tests 0 failures，第十批时点）。

## Goals

- 13 个测试文件的 `System.out` 诊断输出逐文件裁定处置（转 SLF4J logger / 删除 / 示例保留），grep 命中集合 = 裁定保留集合（逐文件对照表落盘）
- `IFileOperator.GrepResult` 3 个 getter 冗余 `public` 修饰符移除（已核验无跨包调用者）；`SplitChunk`/`SplitOptions` 裁定不处理并记录理由
- `DefaultAiChatService.parseToolCalls` unchecked cast 补局部 `@SuppressWarnings("unchecked")`（先例：MA4.1-01 AnthropicDialect 同款处理）
- 全部零行为变更；arm-index 登记第十一批

## Non-Goals

- 不处理 MA4.1-07（observation-only，audit 自标不行动）
- 不处理 MA4.2-01（blank-line 批量格式化）与 MA4.2-14（import 批量重排，0441-1 裁定保持 deferred）
- 不处理 P3-MA1-023~030、P3-MA1-039（roadmap v10 声明不入 scope）
- 不改测试断言语义与测试数量（纯输出/修饰符/注解变更）

## Scope

### In Scope

- 13 个测试文件（见 Current Baseline 清单）的 `System.out` 清理（逐文件裁定对照表）
- `IFileOperator.GrepResult` 3 个 getter 冗余 public 移除（SplitChunk/SplitOptions 不处理，见 Deferred）
- `DefaultAiChatService.parseToolCalls` 局部 @SuppressWarnings

### Out Of Scope

- 生产代码 System.out（已由 MA4.2-11 修复，grep 复核确认 0 残留即可，不重复处理）
- 非 nop-ai 模块代码

## Execution Plan

### Phase 1 - 测试 System.out 清理（MA4.2-12）

Status: planned
Targets: 13 个测试文件（nop-ai-core/nop-ai-maven/nop-ai-coder/nop-ai-skills 下）

- Item Types: `Fix`

- [ ] 逐文件裁定输出保留价值：有断言意义或回归诊断价值的转 SLF4J logger（测试类加 `static final Logger LOG`）；纯一次性调试/示例输出（如 VfsMavenUsageExampleRunner 的 35 处 System.out 中文演示、FixTranslateDir 工具类）删除或保留按示例用途裁定——示例/手册类文件可保留并注明（audit 原文 "acceptable for manual/exploratory tests" 裁定依据），但必须逐文件给出裁定
- [ ] 产出 13 文件 × 处置（logger/删除/示例保留）对照表（入 plan 或 daily log）；清理后 `grep -rln "System.out" nop-ai --include="*.java"` 命中集合与对照表"保留"集合逐行一致

Exit Criteria:

- [ ] 每文件裁定记录（转 logger / 删除 / 示例保留）对照表落盘 plan 或 daily log，grep 命中集合与对照表"保留"集合一致
- [ ] 处理文件 `./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-maven,nop-ai/nop-ai-coder,nop-ai/nop-ai-skills -am` 相关测试绿（不减少测试断言）
- [ ] No owner-doc update required（测试代码内部变更）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 冗余 public（GrepResult）+ unchecked cast 清理（MA4.2-13 + MA4.1-04）

Status: planned
Targets: `IFileOperator.java`、`DefaultAiChatService.java`

- Item Types: `Fix`

- [ ] `IFileOperator.GrepResult` 3 个 getter（getFilePath/getLineNumber/getLineContent）移除 `public`——已核验全库无跨包调用者，安全；`toString()` 保留 public（`FileToolBizModel:225` 跨包调用）
- [ ] `IAiTextSplitter.SplitChunk`/`SplitOptions` getter **不处理**——跨包消费者（`JavaFileSplitter.java:36,92,97`、`TestJavaFileSplitter`、`TestTextSplitter:28` Jackson 序列化依赖 getter 反射可见性）移除 public 会编译失败且静默改变序列化输出；裁定记录入 Deferred But Adjudicated 段
- [ ] `DefaultAiChatService.parseToolCalls:562` 的 `(Map<String, Object>) arguments` cast 前补局部 `@SuppressWarnings("unchecked")`（与 MA4.1-01 先例同款，局部变量级；:548 已有注解属另一 cast，不重复）
- [ ] 编译复核：`./mvnw compile -pl nop-ai/nop-ai-core,nop-ai/nop-ai-tools -am` PASS（nop-ai-tools 为 `GrepResult::toString` 的真实跨包消费者 `FileToolBizModel:225`，`-pl nop-ai-core -am` 只含上游不含下游）；**下游消费者复核**：`./mvnw test -pl nop-ai/nop-ai-tools -am` 绿（验证 GrepResult 改动无回归）；`./mvnw test -pl nop-ai/nop-ai-skills/nop-ai-code-analyzer -am` 绿（SplitChunk 未改的回归佐证，不声称验证 GrepResult）

Exit Criteria:

- [ ] `GrepResult` 3 个 getter 无显式 `public` 残留；`toString()` 仍 public；SplitChunk/SplitOptions 未改动且裁定记录在案；cast 处 @SuppressWarnings 在位
- [ ] `./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-tools -am` BUILD SUCCESS；`./mvnw test -pl nop-ai/nop-ai-skills/nop-ai-code-analyzer -am` BUILD SUCCESS
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 全量验证 + 登记

Status: planned
Targets: `nop-ai` 全模块 + `arm-index.md` + `ai-dev/backlog/audit-remediation-roadmap.md`

- Item Types: `Proof`

- [ ] `./mvnw test -pl nop-ai -am -T 1C` BUILD SUCCESS（全量回归）
- [ ] `scan-hollow-implementations.mjs --module nop-ai --severity high` exit 0
- [ ] `check-doc-links.mjs --strict` exit 0
- [ ] arm-index 新增「P3 追踪（第十一批 — 代码卫生）」小节登记 3 行（MA4.2-12/-13、MA4.1-04 → fixed）；roadmap 登记第十一批

Exit Criteria:

- [ ] 全量 build + 全量 test 绿
- [ ] scan-hollow exit 0；check-doc-links exit 0
- [ ] arm-index 与 roadmap 登记完成
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 13 个测试文件 System.out 清理完成且 13 文件 × 处置对照表落盘；grep 命中集合 = 对照表"保留"集合
- [ ] GrepResult 3 个 getter 冗余 public 移除 + SplitChunk/SplitOptions 不处理裁定记录 + unchecked cast 清理完成
- [ ] 零行为变更（测试数量与断言不减少，全量测试绿为证）
- [ ] 不存在被静默降级的 in-scope live defect
- [ ] arm-index 与 roadmap 已登记
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 验证清理未删除有效断言/输出（抽查测试文件 diff 不减少测试方法）
- [ ] `./mvnw test -pl nop-ai -am -T 1C`（Phase 3 已执行，此处为 closure 复核）

## Deferred But Adjudicated

### IAiTextSplitter.SplitChunk / SplitOptions 冗余 public

- Classification: `watch-only residual`
- Why Not Blocking Closure: 接口内嵌类成员方法**非**隐式 public（JLS 仅接口体直接成员隐式 public）；移除 public 会降为 package-private，破坏跨包消费者（`JavaFileSplitter.java:36,92,97`、`TestJavaFileSplitter`、`TestTextSplitter:28` Jackson 反射序列化），编译失败 + 静默改变序列化输出。audit 建议前提不成立，裁定不处理。
- Successor Required: `no`

### MA4.1-07 Map.class parameterized

- Classification: `watch-only residual`
- Why Not Blocking Closure: audit 自标 "observation only"，无行为缺陷。
- Successor Required: `no`

### MA4.2-01 / MA4.2-14 批量格式化

- Classification: `optimization candidate`
- Why Not Blocking Closure: 海量无关 diff（469 文件 blank-line / 99 处 import 违规），0441-1 Non-Goals 刚裁定保持 deferred。
- Successor Required: `no`

## Non-Blocking Follow-ups

- MA4.2-07/-10、MA4.5-008/-009 由兄弟计划 `2026-08-01-0746-1-arm-p3-api-doc-debt.md` 承接（同批次，**串行执行：本计划在 0746-1 Phase 1-3 完成后执行**，共享 `IFileOperator.java` 编辑面，0746-1 补 javadoc、本计划删 GrepResult getter public，避免并行冲突；Phase 3 交叉复核两计划约定一致）

## Closure

Status Note: 待执行
Completed: （待填）

Closure Audit Evidence:

- Reviewer / Agent: （待独立子 agent 填写）
- Evidence: （待填）

Follow-up:

- no remaining plan-owned work

## Optional Sections

## Risks And Rollback

- 纯机械变更，回滚 = 单 commit revert；主要风险是误删有诊断价值的输出，mitigation = 逐文件裁定记录 + closure audit 抽查测试方法数不减

# 362 nop-code 索引持久化列截断修复

> Plan Status: active
> Last Reviewed: 2026-09-26
> Source: deepwiki skill 实测（`ai-dev/logs/2026/09-26.md`）、nop-code 服务日志（2026-09-26）、`nop-code/model/nop-code.orm.xml`
> Related: `ai-dev/plans/361-nop-jq-deadcode-and-jpath-fix.md`（共用实测背景）
> Draft Review: 两轮独立子 agent 对抗性审查（2026-09-26，agent_6835c657）：R1 发现 1 Major（"唯一写入汇点"不成立→改口径并裁定 GraphQL 直写 out-of-scope）+Minors，全部修复；R2 复审判定无 Blocker 可执行，1 Minor（N362-1 rationale 字段）已随修

## Purpose

修复 nop-code 代码索引对真实模块全量索引必失败的缺陷（`insert NopCodeCall` sqlState=22001 列截断，事务整体回滚）。收口状态：对 nop-kernel/nop-jq 执行 `triggerFullIndex` 端到端成功（fileCount>0、symbolCount>0），并有防回归测试守护自由文本字段与列宽的匹配。

## Current Baseline（均于 2026-09-26 live 验证）

- 对 `nop-kernel/nop-jq`（127 个 Java 文件）执行 `NopCodeIndex__triggerFullIndex` / `indexDirectory` 恒定失败：服务日志报 `sqlState=22001, sqlName=insert:io.nop.code.dao.entity.NopCodeCall`（值超列宽），事务整体回滚，`getStats` 返回 0/0。 nop-code-app 为 H2 内存库，DDL 由 `nop-code/model/nop-code.orm.xml` 启动时生成（`nop.orm.init-database-schema: true`）。
- 列宽事实：`NopCodeCall.CONTEXT` VARCHAR precision=2000；`NopCodeUsage.CONTEXT` precision=1000；`jsonContent` domain precision=4096（用于 `NopCodeCall.METADATA`、`NopCodeSymbol.EXT_DATA`、`NopCodeSemanticEdge.EXT_DATA` 等）。
- 无界写入点（审查补全后的清单，Phase 1 梳理时以代码实际为准）：`JavaFileAnalyzer.java:566-568`（call.context=scope.toString()，可为长链式调用）、`PythonCodeFileAnalyzer.java:434`（call.context=nodeText）、`TypeScriptCodeFileAnalyzer.java:451`（call.context=getNodeText）、`SpringEventSynthesizer.java:79`（METADATA=JsonTool.stringify(metadata)）；此外 `CodeIndexService.saveFileResultInSession` 还写入 `NopCodeSymbol.extData`（4096，且 ExtDataHelper 会追加内容）、`NopCodeSymbol.signature`（precision=2000）、`documentation`（4000）、`NopCodeFile.imports`（jsonImport 8192）等自由文本字段。analyzer 与持久化边界均无截断防护。
- 写入路径事实（审查证实）：`CodeIndexService` 是 **analyzer→持久化链路的汇点**，但其内部有两个 call.context 写点（`saveFileResultInSession`:1265 与 `synthesizeAndPersistHeuristicEdges`:896——SpringEventSynthesizer 产物落库处）；且 GraphQL 层存在绕过它的直写路径：`NopCodeCall.xbiz`/`NopCodeUsage.xbiz`（orm codegen 默认 CRUD）暴露 `NopCodeCall__save`/`NopCodeUsage__save`，`NopCodeCallInputBean.setContext` 无长度校验，直写超长同样触发 22001。
- 索引服务当前可用性：设计文档（`nop-code/design/ai-code-index-graphql-design.md`）与实现有漂移但不在本计划范围；deepwiki skill 的 `.opencode/skills/nop-deepwiki/references/nop-code-api.md` §5 已记载本缺陷。

## Goals

- 对真实 Java 模块的全量索引从"必失败"变为"成功且可复用"：`triggerFullIndex(nop-jq)` 返回 fileCount>0，`getStats` symbolCount>0。
- analyzer/持久化链路上所有自由文本字段（context/metadata/extData/signature/documentation/imports 类）与目标列宽匹配：超长内容在持久化边界被确定性地截断，不再使整个索引事务回滚。GraphQL 直写路径（`NopCodeCall__save` 等用户显式操作）不在本计划范围（见 Phase 1 范围裁定）。
- 防回归测试覆盖"超长调用表达式"场景。

## Non-Goals

- 不改变索引/符号提取的分析语义（fan-in、调用图的抽取逻辑不动）。
- 不修复 nop-code 设计文档与实现的 API 面漂移（独立治理项）。
- 不做列宽扩大（DDL 变更）为主方案（见 Phase 1 Decision）；不迁移 H2 到其他库。

## Scope

### In Scope

- 持久化边界的自由文本截断防护（CodeIndexService 写入路径）。
- 超长场景回归测试 + 真实模块端到端索引验证。
- deepwiki skill reference 中"已知问题"条目更新。

### Out Of Scope

- `NopCodeCall` 之外实体的历史数据修复（H2 内存库无历史数据）。
- 索引性能、增量索引（triggerIncrementalIndex）链路的截断覆盖仅限同型字段，不做行为重构。

## Execution Plan

### Phase 1 - 持久化边界截断防护

Status: planned
Targets: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`（及其调用的实体组装路径）

- Item Types: `Fix | Decision`

- [ ] Decision：采用"持久化边界统一截断"而非"扩大列宽"——理由：context/metadata 是信息性片段，超长部分信息价值低；扩列只会推迟溢出且引入 DDL 变更；在 analyzer→持久化链路的汇点（CodeIndexService，含 saveFileResultInSession 与 synthesizeAndPersistHeuristicEdges 两个实体组装区）统一防护可覆盖 Java/Python/TypeScript 全部 analyzer。被拒替代：orm 模型加宽 CONTEXT 至 8000（仍非上界）。**范围裁定**：GraphQL 层的 `NopCodeCall__save`/`NopCodeUsage__save` 等直写路径（codegen 默认 CRUD）属**用户输入直写，非 analyzer 产物**，裁定为 out-of-scope——analyzer 链路是自动无人值守路径（失败导致索引整体不可用），直写路径是显式人工操作（超长输入报 22001 属可接受的快速失败）；裁定记录进 design doc，且 baseline 中如实区分
- [ ] Fix: 梳理 CodeIndexService 两个实体组装区（`saveFileResultInSession`、`synthesizeAndPersistHeuristicEdges` 及其共用 helper）全部自由文本字段与列宽映射（call.context=2000、usage.context=1000、metadata/extData=4096、symbol.signature=2000、documentation=4000、file.imports=8192、provenance/callType 等受控短字段记录豁免理由；`NopCodeSemanticEdge.rationale` 列无显式 precision（nop-code.orm.xml:948）——执行时明确其截断策略或写明豁免依据，不留未命名项），在写入前统一按列宽截断
- [ ] Fix: 截断行为可预期：截断不产生半截转义（如 JSON 中间截断时保证字符串合法或该字段置空），且不抛出异常、不静默丢弃整行（debug 级日志记录截断事实）

Exit Criteria:

- [ ] 新增测试（nop-code 模块）：构造含 >2000 字符调用目标表达式的 Java 源码，经 `indexFile` 索引成功；断言对应 NopCodeCall 已持久化且 context 长度 ≤2000（注意：`saveFileResultInSession` 对 callerId/calleeId 空值的 call 直接 skip——测试源码须保证调用处于真实方法体内，断言"已持久化"即是防测空的防护）
- [ ] 新增测试：metadata 超过 4096 字符的场景索引成功且长度合规（覆盖 jsonContent 类字段）
- [ ] **端到端验证**（Rule #22）：启动 nop-code-app（`-Dnop.web.validate-page-model=false`，操作步骤按 `.opencode/skills/nop-deepwiki/references/nop-code-api.md` §1-§3：登录 nop/123、token 落盘、helper 脚本），经 GraphQL `NopCodeIndex__save` + `triggerFullIndex` 对 `nop-kernel/nop-jq` 全量索引，返回值 fileCount>0；`getStats` symbolCount>0（本计划关闭前缺陷的直接反证）。H2 内存库重启即清空——getStats 输出与服务日志证据须在同一服务会话内采集并写入 closure evidence
- [ ] `./mvnw test -pl nop-code/nop-code-service -am` 全绿
- [ ] owner-doc 更新：`.opencode/skills/nop-deepwiki/references/nop-code-api.md` §5"已知问题"第 1 条改写为"已修复（plan 362）+ 截断语义说明"；`nop-code/design/ai-code-index-graphql-design.md` 增补持久化截断契约一小节（含 GraphQL 直写路径 out-of-scope 裁定）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] 所有 in-scope confirmed live defects 已修复（22001 索引失败）
- [ ] 所有 in-scope confirmed contract drifts 已收敛（analyzer→持久化链路的全部自由文本写入点受防护；GraphQL 直写路径已裁定 out-of-scope 并记录）
- [ ] 行为/契约结果已达成（nop-jq 全量索引 e2e 成功）
- [ ] 必要 focused verification 已完成（超长字段回归测试两条）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] 受影响的 owner docs 已同步到 live baseline（skill reference + nop-code design doc）
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）截断防护在真实索引事务路径上被调用（e2e 与测试证明），（b）截断实现无空方法体/静默跳过——截断是记录性行为（debug 日志）而非吞异常
- [ ] `./mvnw compile -pl nop-code/nop-code-service -am`
- [ ] `./mvnw test -pl nop-code/nop-code-service -am`
- [ ] checkstyle / 代码规范检查通过
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-code --severity high` 退出码 0

## Deferred But Adjudicated

（无——in-scope 项全部为 Fix/Decision，不允许延期）

## Non-Blocking Follow-ups

- nop-code 设计文档 API 面与实现的系统性漂移治理（`NopCodeIndex__create` 不存在、富查询面未记载等）——out-of-scope improvement，独立于本缺陷修复。
- `NopCodeUsage`/`NopCodeSemanticEdge` 等实体如未来接入新的 analyzer，截断映射表需随列宽定义同步维护——watch-only residual。

## Closure

Status Note: （关闭时填写）
Completed: （关闭时填写）

Closure Audit Evidence:

- Reviewer / Agent: （独立子 agent）
- Evidence: （每条 Exit Criterion / Closure Gate 的 PASS/FAIL + live 证据）

Follow-up:

- 见 Non-Blocking Follow-ups

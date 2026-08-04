# MA1 结构与依赖审计（api + core + codegen）

> Plan Status: completed
> Last Reviewed: 2026-08-04
> Draft Review: 3 轮独立子 agent 对抗性审查通过（1 Blocker 级 + 3 Major + 6 Minor 全部修复；final round 无 Blocker/Major）。Session: ses_035f80253ffeMLSPnj7L3ksNPN / ses_035f026cdffeCN1ireWwRbLRDo / ses_035e90415ffePF1hTXAhQbyYkg。
> Mission: nop-metadata-audit-remediation
> Work Item: MA1（1.1 依赖图 / 1.2 模块职责 / 1.3 API 契约 / 1.4 Delta 合规）
> Source: `ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`（MA1 里程碑）、`ai-dev/skills/deep-audit-prompts.md`（维度 01/02/03/06）
> Related: 执行顺序 `{2}` of 3 — **启动门禁：M0（执行顺序 `{1}`）必须先完成**（roadmap M0 四行 done、M0.3 未闭包清单已填充非骨架、M0.4 基线数字已记录），否则本 plan 不启动；M0.3 未闭包清单作为历史发现对照输入，M0.4 结果为基线；产物（P0/P1 发现）是 MR1 批量修复的输入。

## Purpose

对 nop-metadata 的 api + core + codegen 三个子模块执行结构与依赖审计（roadmap MA1 的 4 个工作项），产出审计报告并更新 arm-index，把每个发现归入 P0/P1/P2/P3 与修复归属，为 MR1 批量修复提供输入。

## Current Baseline

经 2026-08-04 live repo 核对（引用均与 roadmap 一致，已二次确认文件与计数）：

- 8 子模块：api 32 main / core 2 / codegen 0 / dao 120 / meta 0 / service 128 main + 94 test / web 0 / app 1（`find ... -name "*.java" | wc -l` live 核对一致）
- 历史审计基线（9 个来源，5 个时间戳轮次：07-19 / 07-20-1554 / 07-20-1816 / 07-21-2039 / 07-23-0714，multi+open 双轨）
- **07-23 维度 01 文档内部矛盾已确认**：`2026-07-23-0714-multi-audit-nop-metadata/01-dependency-graph.md` 声称 dao 依赖 "api + nop-orm，codegen test scope ✅"（合规），仅 1 条完整发现（api 未注册 BOM，P2）；而 `summary.md` 统计表记维度 01 有 5 发现且含 P1 dao→core 违规。**对照清单以 `arm-unclosed-findings-nop-metadata.md`（M0.3 产出，轮次限定 ID）为权威来源**，历史文件矛盾在报告中记录不仲裁
- 2026-07-31 抽查验证：dao→core 编译依赖已移除（live `nop-metadata-dao/pom.xml` 仅 api + orm + codegen(test)；修复提交 `git log -S "nop-metadata-core" -- nop-metadata/nop-metadata-dao/pom.xml` 定位 `c3162d4da`（plan-2026-07-23-0838），作回归验证证据）
- 07-20-1554 维度 03 记录 Map 返回型 API：21 方法 / 8 BizModel，**07-20 双报告裁定不一致**：1554 复核记为 P2（"逐步替换 Map 返回"），同日 1816 轮明确列为 P1（"最大架构问题，优先修复建议 P1"）；本 plan 不预设定级——**以 M0.3 清单定级为准：若定 P1 则归 MR1，若定 P2 则默认归 deferred**，报告记录双裁定并说明选择依据
- 07-20 维度 06 结论"无 Delta"（live `_vfs` 无 `_delta/` 目录，属实，需回归确认）；x:extends 出现在生成 `app.orm.xml` 与手写 `view.xml`/`action-auth.xml`（20+ 处，扫描范围见 Phase 4）；仓库实际模式：手写文件一律 `x:extends="_gen/_NopMeta*.view.xml"`（保留层引用生成物），无 `x:extends="super"` 用例（super 用于 delta 层，本模块无 `_delta/`）
- `ai-dev/design/nop-metadata/01-architecture-baseline.md` 存在（roadmap 1.1 owner doc）
- 测试基线：833+ tests / 0 failures（2026-07-23 记录），M0.4 将刷新基线（含 reactor 范围口径）

## Goals

- 产出 MA1 审计报告（4 份：1.1 依赖图 / 1.2 模块职责 / 1.3 API 契约 / 1.4 Delta 合规），发现一律使用轮次限定 ID 或 `P<级别>-<里程碑>-<序号>`
- 每个发现标注 P 级 + 修复归属（MR1/MR2/即时通道/非阻塞）
- 更新 arm-index-nop-metadata.md 报告清单与 P0/P1 追踪
- 无 P0 时保持绿色基线；发现 P0 走即时通道（异步注入修复 plan）

## Non-Goals

- 不修复审计发现（修复归 MR1 批量修复，P0 例外走即时通道）
- 不审计 dao/service 业务逻辑（MA2 承接 ORM/BizModel，MA3 承接运行时）
- 不改任何 `src/` 代码（纯审计计划）

## Scope

### In Scope

- 1.1 api/core 依赖图与模块边界审计（维度 01）：dao→core 历史违规回归验证（含 git 证据定位）、-api 依赖缺失、零引用依赖（判定方法：`mvn dependency:analyze` 输出 + 源码 import 抽样核对，双轨并用以避免单轨误报）
- 1.2 模块职责与文件边界审计（维度 02）：api 32 / dao 120 / service 128 文件职责、core 过轻问题、codegen（0 main，审计点为 pom 依赖面与生成脚本职责）/meta/web 边界
- 1.3 API 表面积与契约一致性审计（维度 03）：DTO/接口与 I*Biz 接口契约收敛性（@BizMutation 声明、Map 返回类型、IServiceContext 参数）
- 1.4 Delta 定制合规性审计（维度 06）：`_delta/` 目录存在性回归 + x:extends 扫描（范围：`nop-metadata/*/src/main/resources/_vfs/**` 下全部 XML，判定标准：生成文件 `x:extends` 目标存在且指向合法源；手写文件 `x:extends="super"` 用于覆盖场景合规）
- 审计报告（`ai-dev/audits/YYYY-MM-DD-HHMM-arm-MA1.<n>-nop-metadata-<dimension>.md`）+ arm-index 更新

### Out Of Scope

- MA2-MA7 审计（后续计划）
- 任何修复（MR1/MR2/MR3 承接）
- `docs-for-ai/` 文档修改（MA5 覆盖；审计发现记录为 finding 即可）

## Execution Plan

### Phase 1 - MA1.1 依赖图与模块边界审计

Status: completed
Targets: `nop-metadata/nop-metadata-api/`、`nop-metadata/nop-metadata-core/`、`nop-metadata/nop-metadata-codegen/` + `pom.xml` 文件

- Item Types: `Proof`

- [x] **启动门禁核查**：确认 M0 已 done（roadmap M0 四行 done、`arm-unclosed-findings-nop-metadata.md` 已填充非骨架、M0.4 基线数字已记录）；未满足则不启动并上报
- [x] 执行维度 01 审计（依赖图与模块边界）：dao→core 历史违规回归验证（`git log -S "nop-metadata-core" -- nop-metadata/nop-metadata-dao/pom.xml` 定位修复提交 + live pom 核实）、-api 依赖缺失、零引用依赖（`mvn dependency:analyze` + import 抽样）
- [x] 运行 `./mvnw dependency:tree -pl nop-metadata/nop-metadata-api,nop-metadata/nop-metadata-core` 获取依赖树基线（注意：多个 `-pl` 需逗号分隔，重复 `-pl` 会互相覆盖）
- [x] 历史对照：以 M0.3 未闭包清单中维度 01 相关条目（轮次限定 ID）逐一核对，含 07-23 summary 的 P1 dao→core 与维度文件的矛盾（见 Current Baseline），07-21 维度 01 的 api parent 指向问题（live 已修复则记 fixed）
- [x] 产出审计报告 `ai-dev/audits/{YYYY-MM-DD-HHMM}-arm-MA1.1-nop-metadata-dependency-graph.md`

Exit Criteria:

- [x] 报告包含：dao→core 回归验证结论（含修复提交证据）、-api 依赖缺失清单、零引用依赖清单（附判定方法说明）
- [x] 每个发现标注轮次限定 ID 或新 ID + P 级 + 修复归属 + 关联文件
- [x] 07-23 维度 01 的 summary/维度文件矛盾已记录在报告"对照说明"段（不仲裁，以 M0.3 清单为准）
- [x] 文档变化：`No owner-doc update required`（审计报告为证据层）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - MA1.2 模块职责与文件边界审计

Status: completed
Targets: 8 子模块 `src/` 目录 + `nop-metadata/*/pom.xml`

- Item Types: `Proof`

- [x] 执行维度 02 审计（模块职责与文件边界）：8 子模块职责划分、core 过轻问题、codegen/meta/web 边界
- [x] 运行 `find nop-metadata -name "*.java" -not -path "*/target/*" -exec wc -l {} + | sort -rn | head -30` 获取文件行数基线（排除 `target/` 构建产物污染）
- [x] 产出审计报告 `ai-dev/audits/{YYYY-MM-DD-HHMM}-arm-MA1.2-nop-metadata-module-boundary.md`

Exit Criteria:

- [x] 报告包含：8 子模块职责表、core 过轻问题结论（存在/不存在）、codegen/meta/web 边界结论
- [x] 每个发现标注轮次限定 ID 或新 ID + P 级 + 修复归属
- [x] 文档变化：`No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - MA1.3 API 表面积与契约一致性审计

Status: completed
Targets: `nop-metadata/nop-metadata-api/` + `nop-metadata/nop-metadata-dao/src/main/java/io/nop/metadata/biz/`

- Item Types: `Proof`

- [x] 执行维度 03 审计（API 表面积与契约一致性）：DTO/接口与 I*Biz 接口契约收敛性、@BizMutation 声明、Map 返回类型、IServiceContext 参数
- [x] Map 返回型 API 对照 07-20-1554 与 07-20-1816 双报告（1554 记 P2 / 1816 记 P1，见 Current Baseline）；**定级以 M0.3 清单为准**：P1 则归 MR1，P2 则默认归 deferred；报告记录双裁定与选择依据
- [x] 产出审计报告 `ai-dev/audits/{YYYY-MM-DD-HHMM}-arm-MA1.3-nop-metadata-api-contract.md`

Exit Criteria:

- [x] 报告包含：I*Biz 接口与实现契约对照、Map 返回类型 API 清单（含 21 方法/8 BizModel 对照与 P2 裁定记录）、@BizMutation 声明清单
- [x] 每个发现标注轮次限定 ID 或新 ID + P 级 + 修复归属
- [x] 文档变化：`No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - MA1.4 Delta 定制合规性审计

Status: completed
Targets: `nop-metadata/*/src/main/resources/_vfs/`

- Item Types: `Proof`

- [x] 执行维度 06 审计（Delta 定制合规性）：`_delta/` 目录存在性回归（07-20"无 Delta"结论确认）+ x:extends 扫描（范围 `_vfs/**` 下全部 XML，含生成 `app.orm.xml` 与手写 `view.xml`/`action-auth.xml`；判定标准：合规模式 = `x:extends` 目标存在且指向合法源，含 `x:extends="_gen/_NopMeta*.view.xml"` 保留层引用生成物的仓库惯例；`_delta/` 不存在时 `x:extends="super"` 不适用）
- [x] 产出审计报告 `ai-dev/audits/{YYYY-MM-DD-HHMM}-arm-MA1.4-nop-metadata-delta.md`

Exit Criteria:

- [x] 报告包含：`_delta/` 目录扫描结果与"无 Delta"回归结论、x:extends 使用扫描结果（含扫描范围与判定标准）、合规/违规清单
- [x] 每个发现标注轮次限定 ID 或新 ID + P 级 + 修复归属
- [x] 文档变化：`No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 纯审计计划（不改代码），构建验证以绿色基线保持为准。

- [x] 4 份 MA1 审计报告全部产出且含 P 级标注 + 修复归属
- [x] 以 M0.3 未闭包清单为对照源，维度 01/02/03/06 相关条目逐一核对（覆盖 5 个时间戳轮次：07-19-1118、07-20-1554、07-20-1816、07-21-2039、07-23-0714，含 07-19 的 dao→core 原始发现与 Map 原始发现），无遗漏；历史文件内部矛盾已在报告中记录
- [x] arm-index-nop-metadata.md 报告清单 + P0/P1 追踪已更新
- [x] P0 发现已走即时通道（若存在）；P1 发现已归入 MR1 修复清单
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步，或明确写明 `No owner-doc update required`
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证报告非空壳（有实际发现清单与可追溯引用，非模板占位）
- [x] `./mvnw compile -pl nop-metadata -am`（绿色基线保持验证）
- [x] `./mvnw test -pl nop-metadata -am`（绿色基线保持验证）
- [x] checkstyle / 代码规范检查通过（无代码变更，以 mvn 默认检查为准）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（修改 `ai-dev/` 下文件后执行）

## Deferred But Adjudicated

### MA1 P2/P3 finding 的修复（含 Map 返回型 API 若定级 P2）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: roadmap 规则 1 明示本 roadmap 只处理 P0/P1；Map 返回型 API 若 M0.3 定级 P2（1554 裁定"逐步替换"）属渐进优化非 live defect；若定级 P1 则不适用本 deferred（归 MR1，见 Phase 3 选择依据）。
- Successor Required: `no`（后续批次另行规划）

## Non-Blocking Follow-ups

- 若审计发现 core 过轻问题，可作为后续模块结构调整输入（需单独 design 裁定，非本 plan scope）
- codegen 模块（0 main）职责若确认空转，作为模块边界调整候选（后续 design 裁定）

## Closure

Status Note: 4 Phase 全部完成；4 份 MA1 审计报告产出并登记 arm-index；1 项新增 P1（P1-MA1-001 NopMetaSearch.xmeta 陈旧类引用）归 MR1，2 项 P2 残余归 MR2，P3 残余 deferred（均为非 live defect 维护项）；Map 双裁定按 M0.3 清单定级 P2 → deferred（记录双裁定与选择依据）；绿色基线保持（813 tests / 0 failures）；独立子 agent closure audit PASS。
Completed: 2026-08-04

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（general，fresh session）task_id: ses_035acf136ffei6O0CbPq09dj3w
- Audit Session: ses_035acf136ffei6O0CbPq09dj3w
- Evidence:
  - Phase 1-4 Exit Criteria 逐条验证 PASS（live 证据：dao pom 无 core + c3162d4da diff、api parent relativePath、BOM 1150-1192、service pom:17、MetaAggregationExecutor=264 行、SqlTableReference 改名、I*Biz 接口 IServiceContext/方法声明、NopMetaSearch.xmeta:7 陈旧引用 + core.dto 包不存在、`_delta/` 0 结果 + 39/39 view extends 目标存在）
  - Closure Gates 12/12 验证 PASS（本审计记录后由 executor 勾选；audit 独立核实全部产物非空壳）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` 退出码 0（本 closure 写入后确认）
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（0 errors）
  - Anti-Hollow 检查：4 份报告含轮次限定 ID + 提交哈希 + live 计数 + 实际发现清单；P1-MA1-001 为 live 可复现 contract drift（全仓唯一陈旧 `io.nop.metadata.core.dto` 引用）→ 非空壳 PASS；scan-hollow N/A（纯文档计划零代码变更）
  - Deferred 项分类检查：Map 返回 P2 裁定有 M0.3 依据 + 双裁定记录；P1-MA1-001 归 MR1 未降级；无 in-scope live defect 被静默 defer PASS
- 绿色基线：`./mvnw test -pl nop-metadata -am -T 1C` → BUILD SUCCESS（2026-08-04 09:10，813/0）

Follow-up:

- P1-MA1-001（NopMetaSearch.xmeta 陈旧类引用）→ MR1 修复（roadmap R1.x 展开时纳入）
- 2 项 P2 残余（`2026-07-23-0714#维度07-004` DTO 动态行 Map、`2026-07-21-2039#维度07-03` queryAggregation 11 参数；`2026-07-19-1118#维度02-01` 残余 *Service 命名 2 处）→ MR2
- P3 维护项（codegen 零引用依赖、dao test-scope 冗余、core 过轻、OrmModelImporter 位置、MetaJoinExecutor 743 行）→ deferred / 后续 design 裁定（roadmap Non-Blocking Follow-ups）
- no remaining plan-owned work

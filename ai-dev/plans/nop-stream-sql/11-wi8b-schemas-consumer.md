# 11 WI8b 步骤1 schemas 声明面消费者

> Plan Status: completed
> Last Reviewed: 2026-10-02
> Source: `ai-dev/backlog/nop-stream-sql-roadmap.md`（WI8b 行、§3.5、Cross-Cuting 2/4）、`ai-dev/design/nop-stream/sql-landing-decision.md` §5、`ai-dev/design/nop-stream/sql-compiler-contract.md` §1/§3
> Related: `ai-dev/plans/nop-stream-sql/10-wi8a-landing-decision.md`
> Owner: 仓库 owner（2026-10-02 执行指令委托）
>
> 审查修订记录：经独立审查一轮修订——M1 完成判定前半句偏离的完整论证与 roadmap 行内措辞同步；M2 delta 机制裁定落地（选项 1：本 plan 内裁定 + 三文档注记）；M3 受管名解析器独立公共类；M4 resolveSchema 返回 FieldSpec 记录；M5 模块构建验证 + .gitkeep 目录入库；M6 错误码复用既有码；M7 消费者挂载与 #23 证据形态厘清；m1-m5 随修。

## Purpose

让 `<schemas>` 声明面从 build 期 fail-fast 转为有消费者：field type 收敛为受管类型名并在构造期解析为 BasicTypeInfo，coder 取 BasicTypeInfo 内建 SimpleTypeSerializer（coders 注册表仍 fail-fast 留 FU-3），解析失败逐项 fail-fast。解除 WI9/WI17 对 schemas 消费面的等待。

## Current Baseline

- `StreamModelDslBuilder.java:266-269`：`model.hasSchemas()` 即抛 `ERR_STREAM_NOT_IMPLEMENTED`；:270-273 coders 同样 fail-fast。既有风格：**首错即抛**（validateDag/validateEdgeDeclarations 同款）。
- xdef（stream.xdef:66-72）：schemas 已建模（StreamSchemaModel：fields[name/type/nullable/defaultValue]，type=`!string`）。模型类 30 个由 nop-stream-flow generate-sources 生成（pom :70 有 exec 插件声明，改 xdef 必触发 30 类重生成——重生成本身是被 Cross-Cuting 2 制裁的常规管线）。
- **xdef 级收敛的偏离裁定（本 plan 内完成，理由如下）**：完成判定前半句「stream.xdef 的 field type 收敛为受管类型名」按 builder 级收敛实现，不触 xdef。理由：(a) xdef 级 `enum:` 收敛生成**闭集**，与 WI8c 起声明面走新模块 delta 扩展的方向冲突（delta 无法扩闭集 enum）；(b) xdef 级收敛把报错点从构造期提前到 parse 期，改变完成判定的语义落点；(c) `sql-landing-decision.md` §5 WI8b 行边界本就是 builder 级措辞（「收敛受管类型名并在构造期解析」）。该偏离属 roadmap 行内措辞修订，随 Phase 3 同步 roadmap WI8b 行与 Cross-Cuting 4 WI8b 条目注记（见 Scope）。
- **受管名→BasicTypeInfo 解析器不存在**：BasicTypeInfo 仅有九实例与开放工厂 `of(Class)`（未知类静默新建——恰为受管收敛要禁止）。解析器需新建且为 **WI9/WI17 复用的公共契约**（contract §1.1「经 WI8b 同一解析通路」）——落点 nop-stream-flow 独立公共类，非 builder 私有。
- 既有测试：`TestStreamModelDslBuilderFailFast.schemasRegistryFailsFast`（:86-90）断言含 schemas 即失败——需改为新行为；类注释（「所有 top-level registry fail-fast 保留在本类」）同步更新。
- 生产调用方 `StreamConfValidator.java:120`、`QuickstartSupport.java:76` 均 `of(model, resolver).build()` 后即弃 builder——**解析器/注册表须独立于 builder 生命周期**（无状态入口 + 不可变注册表对象），builder.build() 是首个调用方。
- 模块骨架：D13 分支 a 新模块 nop-stream-sql 的 pom 由本 WI 创建（依赖 nop-stream-flow + nop-orm-eql）；`_vfs` 目录用 `.gitkeep` 入库。

## Goals

- 新公共解析器（nop-stream-flow，public）：受管名→BasicTypeInfo 严格解析（九名闭集，未知名返回 null 供调用方抛错）+ `resolveSchemas(StreamModel)` 无状态入口返回不可变注册表对象（schemaId → 有序 List<FieldSpec>，FieldSpec 含 name/type(BasicTypeInfo)/nullable/defaultValue）；builder.build() 调用它替代 throw。
- coder 语义：FieldSpec.type 的内建 SimpleTypeSerializer 即字段 coder；九名 identity 断言（assertSame 到 BasicTypeInfo 九实例）+ getTypeClass() 符合 D7 §1.3 映射。
- fail-fast：**首错即抛**（builder 既有风格），StreamException 携带 schemaId/fieldName/type/location；错误码复用既有 `ERR_STREAM_INVALID_ARG` + ARG_DETAIL 定位（零越界，不新增 core 错误码；测试钉住完整码串）。
- coders 注册表保持 fail-fast（FU-3）；resolveSchema 在 build() 前调用的行为契约写死（抛 IllegalStateException 提示先 build）。
- 新模块骨架：pom（parent nop-stream，依赖 nop-stream-flow + nop-orm-eql）+ `_vfs/nop/schema/.gitkeep` 入库 + 父 pom modules 登记；`./mvnw install -pl nop-stream/nop-stream-sql -am -DskipTests` 绿作构建验证。

## Non-Goals

- 不做 delta 扩展（本裁定见 Phase 3；首个需求 WI8c）；不动 stream.xdef；不动 coders 行为；不实现 WI17 SQL 编译消费；不新增 core 错误码。

## Scope

### In Scope

- nop-stream-flow：新公共解析器/注册表类、`StreamModelDslBuilder`（替换 throw + 调用解析器）、`TestStreamModelDslBuilderFailFast`（用例改写 + 注释更新）、新增 `TestStreamSchemaConsumer`
- `nop-stream/nop-stream-sql/` 模块骨架（pom + `.gitkeep` + 父 pom modules）+ `./mvnw install` 构建验证
- nop-stream-core 无变更（错误码复用）
- docs-for-ai：`01-repo-map/module-groups.md` modules 计数 10→11
- roadmap 三处同步：WI8b 状态行与括注、WI8b 行内完成判定措辞按 builder 级收敛改写（owner 授权的重新解释，依审查 (a)(b)(c) 理由）、Cross-Cuting 4 WI8b 条目注记「实际未触 stream.xdef，构造期收敛」；D13 行与 `sql-compiler-contract.md` §3.2、`sql-landing-decision.md` §4.1 补「delta 机制裁定：WI8b 无需 delta（schemas 面在基础 xdef），首个真实需求顺延 WI8c plan，回改条款继续悬置」注记
- 当日日志；plan 收口

### Out Of Scope

- delta 扩展实施（WI8c）；coders/sideInputs 等其它注册表；WI17 SQL 编译消费；xdef 变更；core 错误码新增。

## Execution Plan

### Phase 1 - 红：测试先行

Status: completed
Targets: nop-stream-flow 测试

- Item Types: `Proof`

- [x] 改 `schemasRegistryFailsFast`：合法 schemas 模型 build 成功；未知类型名 build 抛 StreamException（钉住完整码串 `nop.err.stream.invalid-arg` + ARG_DETAIL 含 schemaId/fieldName/type）；coders 用例保持
- [x] 新增 `TestStreamSchemaConsumer`：多 schema 解析、FieldSpec nullable/defaultValue 具体取值往返、resolveSchema build 前调用抛 IllegalStateException、九名 identity+getTypeClass() golden 断言、resolveSchema 未知 schemaId 抛错
- [x] 修复前运行确认红（当前 throw 使合法 schema 必失败）；更新类注释

Exit Criteria:

- [x] 修复前新用例红（失败原因=ERR_STREAM_NOT_IMPLEMENTED）
- [x] coders fail-fast 用例保持绿

### Phase 2 - 解析器与实现

Status: completed
Targets: StreamModelDslBuilder、新解析器类、模块骨架

- Item Types: `Feature`

- [x] 新增 `TestStreamSchemaConsumer`（先行于实现）：多 schema 注册表解析、FieldSpec nullable/defaultValue 具体取值往返、resolveSchema 未知 schemaId 抛 `nop.err.stream.invalid-arg`（码串钉住）、九名 identity+getTypeClass() golden 断言、resolveSchema build 前调用抛 IllegalStateException
- [x] 新公共类（如 `StreamSchemaRegistry`）：`resolveManagedType(String)` 严格九名解析（未知返回 null）+ `resolveSchemas(StreamModel)` 返回不可变注册表（FieldSpec 记录）；builder 替换 throw 并调用之（抛错同时设 ARG_ARG_NAME）；resolveSchema build 前调用契约写死
- [x] 新模块骨架：pom + `.gitkeep` + 父 pom modules
- [x] Phase 1 全部用例转绿；`./mvnw test -pl nop-stream/nop-stream-flow -am` 全量绿；`./mvnw install -pl nop-stream/nop-stream-sql -am -DskipTests` 绿（首次经 -am 拉全上游，plan 内声明）
- [x] ai-dev/logs/ 当日条目已更新

Exit Criteria:

- [x] 合法 schemas 模型端到端 build 成功；resolveSchema 返回 FieldSpec（nullable/defaultValue 取值往返断言）
- [x] 未知类型首错即抛（定位含 schemaId/fieldName/type）
- [x] `./mvnw install -pl nop-stream/nop-stream-sql -am -DskipTests` 绿（实测 BUILD SUCCESS）
- [x] `./mvnw test -pl nop-stream/nop-stream-flow -am` 绿（381+124 测试；TestStreamSchemaConsumer 5/5 单独验证）
- [x] ai-dev/logs/ 当日条目已更新

### Phase 3 - roadmap 同步与收口

Status: completed
Targets: plan 与 roadmap

- Item Types: `Proof`

- [x] roadmap 三处同步（M1/M2）：WI8b 行内完成判定措辞按 builder 级收敛改写 + Cross-Cutting 4 WI8b 条目注记「实际未触 stream.xdef，构造期收敛（理由 a/b/c）」；D13 行、contract §3.2、landing-decision §4.1 补 delta 裁定注记；module-groups.md 计数 10→11
- [x] 独立子 agent closure audit（不同 task_id）：核验解析器公共契约、schemas 声明面由 build 链消费（build 成功用例为证，规则 #23）、fail-fast 首错即抛、coders 保持、模块骨架入库与构建、roadmap 三处同步；证据落 ai-dev/audits/nop-stream-sql/wi8b-closure-audit.md 与 plan Closure 段
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-flow --severity high` 退出码 0（新模块 nop-stream-sql 本阶段 hollow-by-design，scan 覆盖自 WI17 起算——注记）
- [x] audit 通过后 roadmap WI8b `todo` → `done`（括注单层非嵌套、内容无任何圆括号字符；首轮 done 行嵌套全角圆括号被静默丢弃——items 30，重写后复核 31+7）；解析器核对 31 + 7（于仓库根执行）
- [x] Plan Status → `completed`；check-plan-checklist --strict 退出码 0；check-doc-links --strict 退出码 0

Exit Criteria:

- [x] 独立 audit 证据落档两处
- [x] roadmap WI8b = done + 三处同步完成，解析器 31 + 7
- [x] check-plan-checklist --strict 退出码 0；check-doc-links --strict 退出码 0

## Closure Gates

- [x] schemas 声明面由 build 链真实消费（build 成功用例为证，规则 #23；resolveSchema 为查询口，消费归属 WI17 并在 plan 注记）
- [x] 受管名解析器为公共契约类（WI9/WI17 可复用），九名 identity golden 断言
- [x] FieldSpec 契约（name/type/nullable/defaultValue）取值往返断言
- [x] fail-fast 首错即抛 + 码串钉住
- [x] coders 注册表保持 fail-fast
- [x] 新模块骨架 pom 构建绿 + 目录真实入库（.gitkeep）
- [x] roadmap 三处同步完成（WI8b 行措辞、Cross-Cutting 4 注记、D13/contract/landing-decision delta 注记）+ module-groups 11 模块
- [x] `./mvnw test -pl nop-stream/nop-stream-flow -am` 绿
- [x] scan-hollow 高危零发现
- [x] 无静默跳过、无空壳实现

- [x] 独立子 agent closure-audit 已完成并记录证据（不同 task_id）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/11-wi8b-schemas-consumer.md --strict` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

### stream.xdef 的 type 字段 xdef 级收敛

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: (a) xdef enum 闭集与 WI8c delta 扩展方向冲突；(b) 报错点语义落点应为构造期（完成判定原文）；(c) landing-decision §5 边界本为 builder 级措辞。roadmap WI8b 行内措辞随 Phase 3 同步改写（owner 授权重新解释，随 commit 记账）
- Successor Required: `no`
- Successor Path: 如后续需 parse 期报错，随 nop-stream-flow 生成链维护一并处理

## Non-Blocking Follow-ups

- 新模块 nop-stream-sql 本阶段 hollow-by-design，scan 覆盖自 WI17 起算。

## Closure

Status Note: schemas 声明面消费者落地——StreamSchemaRegistry 公共契约（九名严格解析 + 不可变 FieldSpec 注册表）被 build 链真实消费，coders 保持 fail-fast，新模块骨架入库，roadmap 三处同步（含 WI8b 行内措辞构造期收敛改写）。首轮 audit FAIL 的 Blocker-1（done 行嵌套圆括号静默丢弃）与 Major-1（Status 滞留）修正后达成 audit 指明的 PASS 条件。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，与起草审查、执行均不同 task）
- Audit Session: 证据落档 ai-dev/audits/nop-stream-sql/wi8b-closure-audit.md
- Evidence:
  - 消费者接线（规则 #23）：build() → resolveSchemas 代码链实证 + build 成功用例
  - TestStreamSchemaConsumer 5/5 + FailFast 7/7 实测绿；解析器公共契约（public final、不可变、无状态入口）确认
  - 未越界（core 零变更、xdef/_gen 零 diff、coders 保持）；roadmap 三处同步内容核对通过；module-groups 11
  - 回归 cep 381 + flow 124 绿；scan-hollow 0；doc-links strict 0；日志如实
  - 首轮 FAIL 修正：B1 done 行重写（解析器 31+7 复核）、M1 Status 同步、m1 javadoc 归位、m2 断言强化
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/11-wi8b-schemas-consumer.md --strict` 退出码 0

Follow-up:

- 无 plan-owned 剩余工作；delta 机制首个真实需求归 WI8c plan

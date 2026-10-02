# 19 WI16 SQL 子集与不支持清单确认

> Plan Status: completed
> Last Reviewed: 2026-10-02
> Source: `ai-dev/backlog/nop-stream-sql-roadmap.md`（WI16 行）、`ai-dev/design/nop-stream/sql-subset-and-semantics.md`（D4/D5 草案 §3/§4）
> Related: `ai-dev/plans/nop-stream-sql/02-wi0b-subset-and-semantics-decisions.md`、`ai-dev/plans/nop-stream-sql/14-wi9-aggregate-eval.md`
> Owner: 仓库 owner（2026-10-02 执行指令委托）

## Purpose

定稿 SQL 纳入面与不支持清单：确认 D4 的 TUMBLE 语法面与 D5 的排除项、W2 三档策略、错误码表落 sql-subset-and-semantics.md。D4/D5 已在 WI0b 落档，本 WI 只做确认与错误码表（roadmap 明文边界）。

## Current Baseline

- `sql-subset-and-semantics.md`：§3 D4 落 TUMBLE 语法面与 RDBMS T1/T2/T3 三档、§4 D5 不支持清单**草案**（定稿归本 WI）、§6 D15 降级协议。WI0b 落档时明确「清单草案在落档 §4，定稿归 WI16」。
- ** WI9 落地后的实现事实**（确认清单时须对齐 live 代码）：标量子集=列/字面量/算术/比较/AND-OR-NOT/IS NULL/BETWEEN/IN（值列表）；聚合五 id（DISTINCT fail-fast）；除法恒 double；九受管类型（DECIMAL/DATE/TIMESTAMP 编译期报错）；子集外（正则函数/CASE/CAST/子查询/参数标记）fail-fast `nop.err.stream.invalid-arg`。
- WI8c/WI8d 声明面校验：恰一、六项聚合 fail-fast、八项 join fail-fast——错误码全部复用既有 `nop.err.stream.*` 码。
- D4 的 TUMBLE 语法面：EQL grammar 已有伪表函数落点（WI0b 裁定 + WI1 grammar 四能力），流目标映射窗口 assigner；RDBMS 三档。
- 现有错误码：`ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`（方言能力）、`ERR_STREAM_INVALID_ARG`（构造期）、`ERR_STREAM_REF_UNKNOWN`、`ERR_STREAM_UPSTREAM_TYPE`、`ERR_STREAM_EDGE_*`、`ERR_STREAM_NOT_IMPLEMENTED`（join 运行时占位）——无新增码。

## Goals

- **sql-subset-and-semantics.md 定稿**（在本档 §4 草案基础上改写为最终状态，遵循 design guide 规则 14——最终状态非演进叙事）：
  1. 纳入面定稿：SELECT 投影（标量子集逐类）/WHERE（既有 filter 通路 + WI9 编译器形态）/聚合（五 id + COUNT(*) 形态）/GROUP BY（keyBy + WI11 持续聚合与窗口聚合）/TUMBLE(t, INTERVAL)（D4 语法面）/join（WI8d 声明面 + WI13 运行时）/维表 lookup（WI14 桥接形态）/union（WI6）。
  2. 不支持清单定稿（覆盖 D5 排除项 + 实现确认的 fail-fast 面）：全局 ORDER BY/LIMIT（D5）、非等值 join（FU-1）、DECIMAL/DATE/TIMESTAMP 列类型（D7）、DISTINCT 聚合、正则函数/CASE/CAST 标量子集、IN 子查询、 retract/append-only 语义（D1 终值降级）——每项附错误码或 fail-fast 形态与理由。
  3. W2 三档确认：T1 直通/T2 近似（语义不等价强制标注）/T3 拒绝——逐项落点。
  4. **错误码表**：按「声明面校验/编译期/运行时」三段列出 SQL 链路全部错误码（码串/触发条件/ARG 明细），全为既有码——零新增。
- 与 live 代码一致性：清单每项须能在代码/测试中指认（fail-fast 码串钉住的测试为证——WI9/8c/8d 测试已齐，核对即可，不新增代码测试）。

## Non-Goals

- 不新增/修改任何代码；不新增错误码；不做用户文档（WI18）；不改 EQL grammar。

## Scope

### In Scope

- `ai-dev/design/nop-stream/sql-subset-and-semantics.md` 定稿改写（§4 草案→定稿、新增错误码表节、纳入面总表）
- roadmap WI16 行（Phase 2 翻转）；当日日志

### Out Of Scope

- 代码；grammar；WI18 用户文档。

## Execution Plan

### Phase 1 - 定稿与错误码表

Status: completed
Targets: `ai-dev/design/nop-stream/sql-subset-and-semantics.md`

- Item Types: `Decision`

- [x] 逐项核对 live 代码：纳入面/不支持面/错误码每项有代码或测试指认（指认全部引用既有钉码测试——WI9/8c/8d/WI6/WI14）
- [x] 定稿落档：§4a 不支持清单八项、§4b 纳入面总表九行、§4c W2 三档确认、§4d 错误码表三段
- [x] ai-dev/logs/ 当日条目更新

Exit Criteria:

- [x] 清单每项有 live 指认（文档内锚点：类/测试名）
- [x] D5 排除项全覆盖（ORDER BY/LIMIT、非等值 join、retract、方言、D7 类型、DISTINCT、子集外、CEP）+ D4 三档确认
- [x] 错误码表三段完整且与钉码测试一致（零新增码）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] ai-dev/logs/ 当日条目已更新

### Phase 2 - 收口

Status: completed
Targets: plan 与 roadmap

- Item Types: `Proof`

- [x] 独立子 agent closure audit（不同 task_id）：首轮 FAIL——M-1 §4b 行 5 TUMBLE 归属 WI1 失实（W1 四能力由 WI1 交付，TUMBLE grammar 变更尚未落地且无 WI 承接）；M-1 与 M-2（测试名失实+漏列 resolver 钉码）与 M-3（漏列 FULL 窗口 join 排除）修正后复核 PASS；证据落 ai-dev/audits/nop-stream-sql/wi16-closure-audit.md
- [x] audit 通过后 roadmap WI16 `todo` → `done`（括注单层一对）；`parseRoadmapMarkdown` 复核 31 工作项 + 7 里程碑、19 done、无静默丢弃
- [x] Plan Status → `completed`；check-plan-checklist --strict 退出码 0；check-doc-links --strict 退出码 0

Exit Criteria:

- [x] 独立 audit 证据落档两处
- [x] roadmap WI16 = done + 解析器 31 + 7 复核通过
- [x] check-plan-checklist --strict 退出码 0；check-doc-links --strict 退出码 0

## Closure Gates

- [x] 纳入面总表定稿（覆盖 SELECT/WHERE/聚合/GROUP BY/TUMBLE/join/lookup/union；TUMBLE 行带现状锚点——grammar 未落地为 WI17 前置缺口，audit 实证）
- [x] 不支持清单定稿九项且每项有 fail-fast 指认（D5 排除项全覆盖 + FULL 窗口 join 追加）
- [x] W2 三档确认落档（T1 标注义务/T2 矩阵依据/T3 降级建议）
- [x] 错误码表三段（声明面/编译期/运行时）与钉码测试一致（audit 抽验 9 行实测），零新增码（git diff 实证）
- [x] 独立子 agent closure-audit 已完成并记录证据（不同 task_id，首轮 FAIL 修正后 PASS）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/19-wi16-subset-confirmation.md --strict` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Closure

Status Note: 纳入面与不支持清单定稿——§4a 九项不支持清单逐项附 fail-fast 指认、§4b 纳入面九行（TUMBLE 行如实标注 grammar 缺口为 WI17 前置）、§4c W2 三档、§4d 错误码表零新增码逐项对账。独立 closure audit 首轮 FAIL（M-1 TUMBLE 归属失实）修正后复核 PASS。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，首轮 + 复核两轮）
- Audit Session: 证据落档 ai-dev/audits/nop-stream-sql/wi16-closure-audit.md（首轮 FAIL 1 Major → 修正后 PASS）
- Evidence:
  - §4a 八/九项指认逐文件核对（DISTINCT/子集外/类型闭集/等值键集等全部真实）
  - §4d 抽验 9 行码串与钉码测试一致；零新增码（git diff NopStreamErrors/OrmEqlErrors 零改动）
  - D5 六排除项全覆盖 + 2 项合法追加；M-1 修正后 TUMBLE 现状三重 live 证据吻合
  - 门禁：doc-links strict 0、roadmap 解析 31+7、零代码变更
- `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/19-wi16-subset-confirmation.md --strict` 退出码 0

Follow-up:

- TUMBLE grammar 落地项随 WI17 编译器接线承接（§4b 行 5 已锚定）

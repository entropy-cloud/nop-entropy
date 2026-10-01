# WI0b Closure Audit——02-wi0b-subset-and-semantics-decisions.md

- Audit 日期：2026-10-02
- Auditor：独立子 agent（fresh session，与 plan 起草审查 agent 及执行 agent 均不同 task）
- 裁定：首轮 **FAIL**（仅 3 处 BROKEN_LINK error 机械文本），修复后达成——实质内容全部 PASS，批准翻转 WI0b → done

## 1. 实质核验（全部 PASS）

| 项 | 裁定 | 证据 |
|---|---|---|
| 落档六条裁定要素 | PASS | sql-subset-and-semantics.md §1-§6 各含选项集/结论/理由代码证据/负责人/裁定日期（D3=2026-09-30）/落档日期 2026-10-02/受影响 WI |
| 对 D9-D12 的输入约束 | PASS | §1 末三条与 plan 声明逐条一致（retract 关闭→D10 前提消失、A&R 维持 :449 fail-fast、D12 门禁不变） |
| append-only 无误标 | PASS | 全文仅 :15「非 append-only、非 retract」与 :17「不得写成 append-only」两处否定性表述 |
| 代码证据抽查 | PASS | StreamRecord :38-48 字段面；WindowOperator :449 A&R fail-fast；UPSERT_BY_KEY 全仓零调用方；StreamReduceOperator :87-106 逐条 emit；DMLStatement.g4 :131-133/:135/:144-148/:145-146/:150/:191-192/:216-218；oracle.dialect.xml :184-186 date→trunc；EqlTransformVisitor :1477-1483 + OrmEqlErrors :191；TestPostgreDialect :16 |
| roadmap 一致性 | PASS | D1/D3/D4/D5/D6/D15 六行已裁定 + 结论 + 落档指针；D1 行保留否定性约束原文；R1 关闭；re-scope (a) 已生效 (b)(c) 未采纳；Q2 括注指向 §5 |
| 未越界 | PASS | roadmap diff 仅 4 hunk（WI0b 状态行翻转前）；零产品代码改动 |
| 授权链 | PASS | plan Owner 行、落档头部、sql-vision-conflict-resolution.md §2 三处一致 |
| 日志 | PASS | 10-02.md WI0b 条目含关键事实/审查记录/验证/Doc-sync |
| 解析器 | PASS | 翻转后 items 31 milestones 7 |

## 2. 首轮 FAIL 项与修复

- M1：sql-subset-and-semantics.md:40 `DMLStatement.g4` 裸名反引号 → 改全仓路径。
- M2：sql-subset-and-semantics.md:78 三个未来交付物反引号 → 去反引号。
- M3：sql-compiler-contract.md（WI0c WIP）自身 3 处 BROKEN_LINK → 已由 WI0c 侧同步清零。
- 同类问题全量清理：plans 02/03/04 内同类裸名与相对路径引用共 15 处修复；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（2026-10-02 实测）。
- m1：Phase 2 checklist 勾选滞后已补勾，Phase 2 Status → completed。

## 3. Phase 4 收口确认

- [x] roadmap WI0b `todo` → `done`；解析器 31 + 7 复核
- [x] plan Closure 段写入本 audit 证据，Plan Status → completed
- [x] check-plan-checklist.mjs --strict 退出码 0

# nop-deep-analysis — Audit Follow-up Backlog (P2)

> Purpose: 汇总 `nop-deep-analysis` mission 审计中全部 P2 findings（不驱动独立 remediation plan），每条附来源审计路径以保持可追溯。
> 维护规则：新增 P2 追加到对应来源小节；P2 项被处理后在行尾标注 `→ done in <plan/log>`。

来源审计：
- `ai-dev/audits/2026-07-26-0702-multi-audit-nop-deep-analysis.md`（P2 × 12，该审计因含 P1 已转 `closed`——3 个 P1 由 `ai-dev/plans/nop-deep-analysis/2026-07-26-0816-1-doc-contract-drift-remediation.md` 处理完毕，plan Status: completed）
- `ai-dev/audits/2026-07-26-0702-open-audit-nop-deep-analysis.md`（P2 × 4，P2-only 审计已转 `triaged`）

## 来自 multi-audit 的 P2（12 项）

- **[P2-A2-02]** `CoreConstants` 行号 off-by-one（`.annotations` 后缀在 L27 而非 L26）。源：`ai-dev/audits/2026-07-26-0702-multi-audit-nop-deep-analysis.md`（§P2-A2-02）。位置：`ai-dev/analysis/2026-07/2026-07-24-nop-core-engine-deep-dive.md:280`。→ done in 2026-07-26-1000-1-p2-audit-findings-remediation
- **[P2-A2-03]** `graphql:*` 常量「全集」范围 `L26-43` 不完整（实际 L23-45）。源：同上（§P2-A2-03）。位置：`...-nop-core-engine-deep-dive.md:226`。→ done in 2026-07-26-1000-1-p2-audit-findings-remediation
- **[P2-A2-04]** `AppBeanContainerLoader` L170-185 把两段不同逻辑（autoconfig 资源 vs `nop.ioc.app-beans.files`）混在一个范围下。源：同上（§P2-A2-04）。位置：`...-nop-core-engine-deep-dive.md:240`。→ done in 2026-07-26-1000-1-p2-audit-findings-remediation
- **[P2-A4-02]** `INopJobScheduleBiz` 声明 **6** 个额外 `@BizMutation`，文档写 **5**（同句随后又列出 6 个名字，自相矛盾）。源：同上（§P2-A4-02）。位置：`ai-dev/analysis/2026-07/2026-07-24-nop-graphql-service-frontend.md:50`。→ done in 2026-07-26-1000-1-p2-audit-findings-remediation
- **[P2-A5-01]** `nop-plugin` 被列为顶层基础设施模块，实际嵌套在 `nop-core-framework/nop-plugin/` 下。源：同上（§P2-A5-01）。位置：`ai-dev/analysis/2026-07/2026-07-24-nop-module-matrix.md:5, 52`。→ done in 2026-07-26-1000-1-p2-audit-findings-remediation
- **[P2-A6-01]** "`ai-dev/` 七层知识层" 计数错误——A6 自己的表格有 8 行（`logs/plans/design/analysis/discussions/bugs/audits/skills`）。源：同上（§P2-A6-01）。位置：`ai-dev/analysis/2026-07/2026-07-24-nop-engineering-dx-ai-dev.md:5,6,13,29,67,69`。→ done in 2026-07-26-1000-1-p2-audit-findings-remediation
- **[P2-A6-02]** "`docs-for-ai/` 七区结构" 计数错误——`INDEX.md` 实际 9 项，A6 复制表 8 项（漏 `90-maintenance/`）。源：同上（§P2-A6-02）。位置：`...-nop-engineering-dx-ai-dev.md:158`。→ done in 2026-07-26-1000-1-p2-audit-findings-remediation
- **[P2-A6-03]** `source-anchors` "~90 个锚点" 严重少计（实际 ≈180，跨 35 个序列）。源：同上（§P2-A6-03）。位置：`...-nop-engineering-dx-ai-dev.md:200`。→ done in 2026-07-26-1000-1-p2-audit-findings-remediation
- **[P2-A6-04]** §2.3 `events.jsonl` 步骤计数过时（8 个数字中 3 个错误：EXECUTE=12 非 11、CLOSURE_SCRIPT_CHECK=12 非 10、pass=39 非 37，且漏 fail=2）。源：同上（§P2-A6-04）。位置：`...-nop-engineering-dx-ai-dev.md:91`。→ done in 2026-07-26-1000-1-p2-audit-findings-remediation
- **[P2-A7-01]** §8.5 deferred-items 汇总算术不一致（写 22/27，实际 24/29；括号内 5+5+4+5+4+5+1=29 非 27）。源：同上（§P2-A7-01）。位置：`ai-dev/analysis/2026-07/2026-07-26-nop-platform-deep-introduction.md:335-337`。→ done in 2026-07-26-1000-1-p2-audit-findings-remediation
- **[P2-A7-02]** §2 mermaid 覆盖 "23 行覆盖全部 25 节点" 略有夸大——`API` 与 `OPS` 节点缺显式 provenance 行。源：同上（§P2-A7-02）。位置：`...-nop-platform-deep-introduction.md:102`。→ done in 2026-07-26-1000-1-p2-audit-findings-remediation
- **[P2-A7-03]** 标签 `A2 §8(c)` 被用于两个不同条目（L367 `@BizAction` vs L368 启动性能）。源：同上（§P2-A7-03）。位置：`...-nop-platform-deep-introduction.md:167, 316, 329`。→ done in 2026-07-26-1000-1-p2-audit-findings-remediation

## 来自 open-audit 的 P2（4 项）

- **[OA-1]** capstone（`resolved`、已 closure-audit）以 `_tmp/` scratch 文件作为 load-bearing 完整性计数的 provenance，存在可追溯性脆弱（`_tmp/` 被清理后 §8.5 计数不可复现）。建议：把 22 项登记内联进 capstone §8，或把 `_tmp` 草稿迁出 `_tmp/`。源：`ai-dev/audits/2026-07-26-0702-open-audit-nop-deep-analysis.md`（§OA-1）。位置：`...-nop-platform-deep-introduction.md:279, 337, 365`。→ done in 2026-07-26-1000-1-p2-audit-findings-remediation
- **[OA-2]** A6 误引 `INDEX.md:207` 的模块骨架，漏掉其引用的 `codegen` 步骤（`model → codegen → dao → ...` 写成 `model → dao → ...`）。源：同上（§OA-2）。位置：`...-nop-engineering-dx-ai-dev.md:150`。→ done in 2026-07-26-1000-1-p2-audit-findings-remediation
- **[OA-3]** A2 结论自相矛盾：标签写「六大引擎模块」，括号内列出 7 个。源：同上（§OA-3）。位置：`...-nop-core-engine-deep-dive.md:373`。→ done in 2026-07-26-1000-1-p2-audit-findings-remediation
- **[OA-4]** mission `commands.test` 结构上是 no-op：`; echo` 掩盖了 `check-doc-links.mjs` 的退出码（设计如此，analysis mission；记录以防下次「重新发现」）。源：同上（§OA-4）。位置：`missions/nop-deep-analysis.json:15`。→ done in 2026-07-26-1000-1-p2-audit-findings-remediation

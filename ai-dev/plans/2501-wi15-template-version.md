# 2501 WI15 模板版本管理 — 架构裁定（nop-report-capability-roadmap）

> Plan Status: completed
> Last Reviewed: 2026-10-22
> Source: `ai-dev/backlog/nop-report-capability-roadmap.md` WI15；`docs-for-ai/03-modules/nop-report.md`（rptText SSOT 裁定）；`ai-dev/design/nop-report/auth-wiring.md`
> Related: 2489（WI1 rptText=VFS 路径裁定）

## Purpose

本计划由实现计划转为**架构裁定记录**：WI15「模板版本管理」经对抗性审查裁定——**模板版本化由 git 承担，平台不重复建设 DB 内版本管理**。roadmap WI15 据此关闭。

## 裁定（Decision）

1. **版本化对象是 VFS 模板文件**：模板为 repo 内文件（`.xpt.xlsx`/`.xpt.xml`），git 即其版本管理（历史/差异/回滚/协作），具备平台侧无法复刻的完整性。
2. **rptText SSOT 排除 DB 快照方案**：WI1 已裁定 `rptText` = 模板 VFS 全路径（渲染/授权/缓存三链路一致消费）。DB 侧快照「路径字符串」并对路径做「回滚」是无操作；VFS 文件内容回滚则与 git 工作区状态冲突（双头版本管理漂移）。两条实现路线均被否决。
3. **平台保留的关联能力**（已落地或属其他 WI）：`version` 乐观锁列防并发覆盖（既有）；WI6 结果缓存按数据版本键控（已落地）；模板文件级 locale 变体为独立 residual（WI11 登记）。
4. roadmap WI15 条目改写为本裁定并勾选（口径：由 git 承担，非功能交付）。

## 审查证据（裁定依据）

- 对抗性审查（独立子 agent）：确认无任何消费方把 rptText 当模板内容解析（渲染走 VFS 路径加载）；快照路径字符串 + 回滚 = 无操作或与 git 冲突；Blocker 级语义矛盾。
- SSOT：`docs-for-ai/03-modules/nop-report.md`「rptText 约定存模板 VFS 全路径」、`auth-wiring.md`、`IReportAuthChecker` javadoc 三处一致。

## 被否决的替代方案

| 方案 | 否决理由 |
|------|----------|
| DB 快照 rptText + 回滚 | 路径字符串无操作；与 WI1 SSOT 冲突 |
| VFS 模板文件版本副本（平台管理） | 与 git 双头版本管理漂移；二进制/清理/并发复杂度高且 git 已覆盖 |
| 平台内嵌内容型定义 | 无消费路径，为死字段建设 |

## Closure Gates

- [x] roadmap WI15 条目改写并勾选（决议口径）
- [x] owner doc rptText 约定与裁定一致（无改动需要——既有 SSOT 即本裁定依据）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退 0
- [x] `ai-dev/logs` 收口记录
- [x] 独立审查确认（对抗性审查子 agent：Q3 Blocker 裁定依据成立）

## Closure

Status Note: WI15 经审查裁定模板版本化由 git 承担（rptText SSOT 与双头版本管理理由），roadmap WI15 关闭为决议条目；无代码交付。
Completed: 2026-10-22

Closure Audit Evidence:

- Reviewer / Agent: 对抗性审查子 agent（Q3 Blocker 裁定）；本裁定文本经 review agent 确认依据成立
- Evidence: `docs-for-ai/03-modules/nop-report.md` rptText SSOT；无 rptText 内容型消费方（grep 核验）；git 管理模板文件

Follow-up:

- no remaining plan-owned work

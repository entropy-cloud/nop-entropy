# nop-lint 工具替代统一账本（tool replacement ledger）

> Status: active 账本（防腐门禁在档；逐工具终裁行与分面标注待各 wave item 回填）
> Created: 2026-09-28
> 防腐门禁: `node ai-dev/tools/check-lint-tool-replacement-ledger.mjs`（主检查）/ 同命令 `self-test` 子命令（正控）；exit 0 = 账本结构与词表合法
> 书写纪律: 本文件所有表格单元格内禁止 `|` 与 `\|` 字符（分隔一律用 `/` 或 `；`）——checker 列数校验会响亮拒绝违例
> 状态分工：**工具级终裁执行期状态以 [roadmap](../../ai-dev/backlog/nop-lint-tool-replacement-roadmap.md) 为准**（含 wave 进度，本账本不双写）；本文件是行级账本索引 + 终裁汇总出口（roadmap item 20）+ out-of-scope 记录承载（roadmap item 19）。

## 定位

回答"nop-lint 能否替代 Java 社区静态检查工具 X"的唯一汇总出口。**裁定口径 = 各工具的核心缺陷发现面**（正确性/资源/并发/安全/数据流/平台不变式）；风格/可选面按 out-of-purpose 归档，字节码级检测按 out-of-principle（纯源码原则）归档——两轴定义见 roadmap Purpose。判定纪律见 roadmap Hard constraints（裁定必须证据化：对照记录 / 机制面证据 / 重估触发条件，禁止无对照的替代宣称）。

## 工具级终裁表（由 roadmap item 20 回填收敛，现全部待裁）

> 回填机制（门禁强制）：终裁 ≠ `待裁` 时，证据列必须含至少一个仓内文档锚链接（对照记录 / 机制面证据所在），残余范围必须非 `—`（`out-of-scope` 行写划出面清单或 `全工具`）——无证据终裁不可入库。

| 工具 | 终裁 | 残余范围 | 证据 | roadmap items |
|---|---|---|---|---|
| Checkstyle 10.21.1 | 待裁 | — | — | 3 |
| PMD 7.26.0 | 待裁 | — | — | 4 |
| check-\*.mjs ×24 | 待裁 | — | — | 5 |
| SpotBugs 4.9.8.3 | 待裁 | — | — | 9–11 |
| SonarQube | 待裁 | — | — | 12–14 |
| ErrorProne（未接线） | 待裁 | — | — | 15–16 |
| NullAway / 空类型系统族 | 待裁 | — | — | 17 |
| ArchUnit | 待裁 | — | — | 18 |

终裁词表（门禁 enum-set，定义见 roadmap §工具级终裁词表）：

- `core-face-replaced`：该工具的**核心缺陷发现面**已由 nop-lint 承接（行级映射 + 对照通过 + 原接线点按判据处置）；风格/字节码残余按 out-of-purpose / out-of-principle 归档。
- `replaced-partial`：核心面部分承接；未承接部分的归因（机制缺口 vs 原则外）逐行记录。
- `keep-tool`：核心面大头超出 per-file 源码引擎问题域，工具整体保留。
- `out-of-scope`：非 lint 问题域（覆盖率/依赖 CVE/格式化/git 历史），归档不占用工作面。

## 分面裁定登记

> 分面词表（门禁 enum-set，定义见 roadmap Purpose 分面表）：`core`（核心缺陷发现面，职责内）/ `out-of-purpose`（风格/可选面，显式记录不做——这不是替代债，是范围声明）/ `out-of-principle`（字节码级检测，纯源码原则正式记录不追）/ `out-of-scope`（问题域外，账本归档）。分面标注列 = `/` 分隔的 token 串；roadmap item 2 语境下的 "optional" 风格分面落账本时归 `out-of-purpose` 轴。

| 工具 | 分面标注 | 依据 / 证据 | 重估触发 | 状态 |
|---|---|---|---|---|
| Checkstyle 10.21.1 | 待裁 | — | — | 待裁 |
| PMD 7.26.0 | 待裁 | — | — | 待裁 |
| check-\*.mjs ×24 | 待裁 | — | — | 待裁 |
| SpotBugs 4.9.8.3 | 待裁 | — | — | 待裁 |
| SonarQube | 待裁 | — | — | 待裁 |
| ErrorProne（未接线） | 待裁 | — | — | 待裁 |
| NullAway / 空类型系统族 | 待裁 | — | — | 待裁 |
| ArchUnit | 待裁 | — | — | 待裁 |

## 行级账本索引（三本既有账，状态计数以各自门禁为准）

| 账本 | 行数 | 状态分布 | 防腐门禁 |
|---|---|---|---|
| [checkstyle-pmd 迁移映射](./checkstyle-pmd-migration.md) | 26（checkstyle 17 + pmd 9） | landed 7 / keep-checkstyle 12 / keep-pmd 7——两份旧配置现均不可移除 | `ai-dev/tools/check-lint-tool-migration-mapping.mjs` |
| [check-\*.mjs 迁移 manifest](../../ai-dev/design/nop-lint/12-check-scripts-migration-manifest.md) | 24 | maintain-mjs 7 / exclude 7 / migrated-pending-switchover 5 / candidate 2 / deferred 3（与文档分类汇总行一致，2026-09-28 修正） | `ai-dev/tools/check-lint-migration-manifest.mjs` |
| [PMD/EP coverage manifest](../../nop-lint/nop-lint-nop/src/main/resources/manifest/pmd-errorprone-coverage.yml) | 186（去重后） | tier 1 = 33 已落地带 live fixture；tier 2/3 = 机制面前瞻 | `ai-dev/tools/check-lint-coverage-manifest.mjs` |

## out-of-scope 记录（由 roadmap item 19 落稿；先例已裁：PMD CPD → design 06 §7.2，backlog token-shingling 分析器）

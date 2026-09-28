# nop-lint 工具替代统一账本（tool replacement ledger）

> Status: scaffold（骨架已随 [工具替代 roadmap](../../ai-dev/backlog/nop-lint-tool-replacement-roadmap.md) 建立；防腐门禁脚本、design 12 汇总修正、逐工具终裁回填 = roadmap item 1 及各 wave item）
> Created: 2026-09-28
> 状态分工：**工具级终裁执行期状态以 roadmap 为准**；本文件是行级账本索引 + 终裁汇总出口（roadmap item 17）+ out-of-scope 记录承载（roadmap item 16）。

## 定位

回答"nop-lint 能否替代 Java 社区静态检查工具 X"的唯一汇总出口。**裁定口径 = 各工具的核心缺陷发现面**（正确性/资源/并发/安全/数据流/平台不变式）；风格/可选面按 out-of-purpose 归档，字节码级检测按 out-of-principle（纯源码原则）归档——两轴定义见 roadmap Purpose。判定纪律见 roadmap Hard constraints（裁定必须证据化：对照记录 / 机制面证据 / 重估触发条件，禁止无对照的替代宣称）。

## 工具级终裁表（由 roadmap item 17 回填，现全部待裁）

| 工具 | 终裁 | 残余范围 | 证据 | roadmap items |
|---|---|---|---|---|
| Checkstyle 10.21.1 | 待裁（Wave 1 存量收口） | — | — | 2 |
| PMD 7.26.0 | 待裁（Wave 1 存量收口） | — | — | 3 |
| check-\*.mjs ×24 | 待裁（Wave 1 存量收口） | — | — | 4 |
| SpotBugs 4.9.8.3 | 待裁（Wave 2） | — | — | 5–7 |
| SonarQube | 待裁（Wave 3） | — | — | 8–11 |
| ErrorProne（未接线） | 待裁（Wave 4 adopt-or-skip） | — | — | 12–13 |
| NullAway / 空类型系统族 | 待裁（Wave 4） | — | — | 14 |
| ArchUnit | 待裁（Wave 5） | — | — | 15 |

终裁词表：`replaced` / `replaced-partial` / `keep-tool` / `out-of-scope`（定义见 roadmap）。

## 行级账本索引（三本既有账，状态计数以各自门禁为准）

| 账本 | 行数 | 状态分布 | 防腐门禁 |
|---|---|---|---|
| [checkstyle-pmd 迁移映射](./checkstyle-pmd-migration.md) | 26（checkstyle 17 + pmd 9） | landed 7 / keep-checkstyle 12 / keep-pmd 7——两份旧配置现均不可移除 | `ai-dev/tools/check-lint-tool-migration-mapping.mjs` |
| [check-\*.mjs 迁移 manifest](../../ai-dev/design/nop-lint/12-check-scripts-migration-manifest.md) | 24 | 逐行实况：maintain-mjs 7 / exclude 7 / migrated-pending-switchover 5 / candidate 2 / deferred 3（**文档分类汇总行写 3/5/2，与逐行不一致——roadmap item 1 修正**） | `ai-dev/tools/check-lint-migration-manifest.mjs` |
| [PMD/EP coverage manifest](../../nop-lint/nop-lint-nop/src/main/resources/manifest/pmd-errorprone-coverage.yml) | 186（去重后） | tier 1 = 33 已落地带 live fixture；tier 2/3 = 机制面前瞻 | `ai-dev/tools/check-lint-coverage-manifest.mjs` |

## out-of-scope 记录（由 roadmap item 16 落稿；先例已裁：PMD CPD → design 06 §7.2，backlog token-shingling 分析器）

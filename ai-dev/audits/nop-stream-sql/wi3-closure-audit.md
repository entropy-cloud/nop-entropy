# WI3 Closure Audit——08-wi3-dialect-window-matrix.md

- Audit 日期：2026-10-02
- Auditor：独立子 agent（fresh session，与 plan 起草审查 agent 及执行 agent 均不同 task）
- 裁定：首轮 **FAIL**（1 Blocker + 1 Major，均为文本级）→ 修正后达成 PASS 条件（audit 指明「修复后无需重跑代码测试即可翻转」）

## 1. 首轮审计核验（机制与测试面全部 PASS）

| 项 | 裁定 | 证据 |
|---|---|---|
| H2 实跑 | PASS | audit 实测复现 `WI3 matrix h2:` 七组全 PASS；分区累计值精确断言在源码核对 |
| PG16 实跑（docker） | PASS | audit 实测 `-Dnop.test.docker.enabled=true` 下 `WI3 matrix postgresql:16-alpine:` 七组全 PASS；独立数据源、不继承旧套件确认 |
| features 一致性 + XML 合法性 | PASS | h2/postgresql true×3 与实跑一致；xmllint 三 XML 合法；postgresql diff 仅 features 三行 |
| WI2 联动（B1） | PASS | 重写后 TestDefaultDialectWindowFeatures 2/2 绿；fixture 覆盖语义逐字一致 |
| 快照测试 | PASS | 10/10 绿；golden 10 个无日志噪音、形态差异真实（mssql/oracle 大写）；db2 排除经实文件验证 |
| 端到端（M4） | PASS | TestH2WindowFrameCompileEndToEnd 1/1 绿；pom h2 test 依赖在位 |
| 矩阵文档结构 | PASS | §1-§6 齐备、D15 标注、duckdb WI8 边界、selector 9 yaml 实数核对 |
| owner doc / 未越界 / 回归 / 工具 | PASS | 「已实测开启」句一致；git 全集 10 组文件；105+142 全绿；scan-hollow 0；roadmap diff 空 |

首轮 FAIL 项：Blocker-1（owner doc 反引号路径触发「docs-for-ai 不得引用 ai-dev/」BOUNDARY 规则，check-doc-links exit 1）；Major-2（矩阵文档 h2gis/duckdb 能力位表述与 live 合并语义不符——h2 开启后经继承外溢生效 true×3，误记「保持 false」）。

## 2. 修正与达成

- Blocker-1：owner doc 句子改写为无路径措辞（含 H2/PG 版本信息），check-doc-links --strict 复验退出码 0。
- Major-2：矩阵 §3/§4 更正——h2gis 行「经继承生效 true×3（extends h2 无覆盖），执行未实测」、duckdb 行同（经 postgresql）；owner doc 补 h2gis；plan 指示文本同步（含「执行修订」注记）。
- Minor-3：plan 正文同步（10 方言、PG16 独立数据源——执行发现均有据）。
- Minor-4：Phase 1/2 checklist 逐项勾齐（audit 实测项全部以勾选状态确认）。
- audit 指明修复后「无需重跑代码测试」——文本修复不触代码；doc-links 0 error 已复验。

## 3. Phase 4 收口确认

- [x] roadmap WI3 `todo` → `done`（括注含 D15 标注与 PG 实跑升级说明、零圆括号字符）；解析器 items 31 milestones 7
- [x] plan Closure 段写入本 audit 证据，Plan Status → completed
- [x] check-plan-checklist.mjs --strict 退出码 0

# WI0b Closure Audit——02-wi0b-boundary-decisions.md

> Auditor: 独立子 agent（fresh session，与起草审查 R1 agent_1a126ce9 / R2 agent_a5d94b08 均不同 task）：agent_d6981d33-8174-45d1-96ec-ec75395d8544
> Date: 2026-10-08
> Verdict: **APPROVED**（0 Blocker / 0 Major / 2 Minor，处置见文末）

## 验证矩阵

| # | 验证项 | 结果 | 证据 |
|---|---|---|---|
| 1 | sql-subset §4 D5 节修订 | PASS | :45 标题新口径；:50 首裁结论保留；:51 修订结论（语法载体归 WI40）；:52 两支理由逐一处置（全局设施/状态有界 O(每键窗口内元素数)）；:53 边界注记；:54 负责人+日期 |
| 2 | sql-subset §4a#1 行 | PASS | keyed TopN 移出+「放行≠已实施」+ pin 码与 StreamSqlCompiler :248-253 实测一致（CompileErrors.dialectNotSupport → ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE） |
| 3 | sql-subset §4e ASOF 附节 | PASS | :145-154；引用 vision §四排除行（00-vision.md :47 实存）；R6 判例（multi-engine :73 原文）；FU-E4；重开触发条件 |
| 4 | sql-subset 越权检查 | PASS | git diff HEAD 仅 §4/§4a#1/§4e 三处；§4a 其余行、§4b-d、§7 零变化 |
| 5 | sql-compiler-contract §1.1a | PASS | :38-43；WI7 done 事实、重开触发条件（cdc-design defer 撤销且 WI15 启动）、FU-E3；diff 无越权 |
| 6 | vision §六裁决记录补记 | PASS | :89 新注记；StreamJoinSpecModel 实存（nop-stream-flow/flow/model/）；实质理由自给且显式声明不可平移 WI21 理由；边界声明锚定 2026-10-02 授权 (a)(b)；WI21 原注记 :88 零改动 |
| 7 | roadmap 两行 | PASS | 前置裁定表 WI0b=已裁定 owner（委托链）2026-10-08+四结论+落档位置；状态行 done+四分支括注（程序化校验单层无圆括号字符）；diff 仅两行 |
| 8 | 解析器 | PASS | items 18 milestones 4 wi0b done |
| 9 | check-doc-links --strict | PASS | 退出码 0（3 warnings 属既有 nop-bytecode 06 计划，非本次引入） |
| 10 | Anti-Hollow（文档版） | PASS | OverWindowOperator 构件级表述精确（仅测试实例化、无生产接线）；stream.xdef :77 仍单 timeout（leftTimeout/rightTimeout 未实施——与「放行≠已实施」自洽）；PerKeyOrderedBuffer 生产引用实存；survey B4/B5/B7 存在且口径一致；Deferred/Follow-ups 均（无） |
| 11 | 授权一致性 | PASS | 四处落档+roadmap 两行+plan header 委托链同一 2026-10-08 指令原文 |

## Minor 发现与处置

1. **§4 首裁记录保留方式与 plan 字面轻微偏差**：plan 写「选项/结论行不动原文，新增修订行」，实际原结论行行首标签改为「结论（首裁 2026-10-02）」、原理由行替换为「首裁理由两支的修订处置」行。结论内容未动、两支理由以引述完整保留，实质满足「首裁记录保留+逐一处置」意图。——处置：接受，记为已接受措辞偏差。
2. **3 条 doc-link warning**：既有 nop-bytecode 06 计划问题，非本次引入。——处置：不阻塞，不在本 plan 范围。

# WI25 Closure Audit——05-wi25-lateness-declaration.md

> Auditor: 独立子 agent（fresh session，与起草审查 agent_e3170646 均不同 task）：agent_d9f77cc8-d39b-4190-8a2e-c0123c5a8554
> Date: 2026-10-08
> Verdict: **APPROVED**（0 Blocker / 0 Major / 6 Minor——全部随收口处置）

## 验证矩阵（要点）

| # | 验证项 | 结果 | 证据 |
|---|---|---|---|
| 1 | 声明面与贯通 | PASS | stream.xdef `<field lateness>`（Q1 注释）；_StreamSqlFieldModel 再生成 getLateness（git M 属合法构建产物）；ISqlStreamCompiler 5 参抽象 + 4/3 参兼容链；**expandSqlModel L334-335 实测 5 参调用**（接缝编辑未持久化事故已修复）；StreamSqlCompiler 6 参 compile + 5/4/3 参链 + 负值校验 L168-173 + failFastOnFieldLateness 三路径 L358/L454/L515 + 非时间列 L417-421 + nextStrategy 裸 long 写点 L1138-1141 |
| 2 | 判别测试 | PASS | TestSqlLatenessDeclaration 8（写点 20000 裸值/缺省无属性/emit+lateness 组合/非时间列/负值/三门）；E2E 双档（[a=1,a=6,b=8,c=1] vs [a=1,b=8,c=1]）；LateOrdersSourceFunction fixture（FIXED_DATA 未动）；flow 提取断言 |
| 3 | Q1 裁定落档 | PASS | plan Goals 裁定段；roadmap FU-E6 :149；WI25 done 括注（程序化校验单层无圆括号字符、含 Q1 分支+FU-E6+日期）；解析器 items 18 milestones 4 wi25 done |
| 4 | 零运行时改动 | PASS | git 全量核查：WindowOperator/AdvancedTransforms/WindowedStreamImpl/WindowOperatorBuilder/runtime 主代码零出现 |
| 5 | 回归证据 | PASS | **auditor 自跑 `-fn` 完整四模块 BUILD SUCCESS**：core 1754/0、flow 202/0、runtime 1336/0、sql 110/0（含 nop-batch flake 通过；_tmp/wi25-closure-audit-regression-fn.log）。执行者 run1（接缝事故）/run2（batch flake）失败链如实记录于日志 |
| 6 | owner docs | PASS | nop-stream-sql.md :53 WI25 行、sql-subset §4b :108、日志条目——与 live 一致 |
| 7 | plan 状态机 | PASS | Phase 1 completed 全 [x]（audit 后勾选）、Phase 2 completed、Plan Status completed（收口翻转） |

## Minor 处置（6 项）

1. **M-1** 空 map 断言补（TestSqlModelExpansion 无声明路径 assertEquals emptyMap）——已补并绿。
2. **M-2** 日志数字更正（+19→+11）——已更正。
3. **M-3** 回归事故链（run1 接缝事故/run2+audit 轮 batch flake/隔离重跑）如实记录于日志与 plan Closure——已记录。
4. **M-4** roadmap 翻转先于 audit 结论的实际顺序——已在 plan Phase 2 条目与日志注记（audit 对翻转后状态做事后追认核验）。
5. **M-5** Phase 1 剩余 checkbox 凭 auditor 回归证据勾选——已勾。
6. **M-6** gate-inventory.json（WI38 accumulationMode 条目）归属注记——随本提交提交信息注明。

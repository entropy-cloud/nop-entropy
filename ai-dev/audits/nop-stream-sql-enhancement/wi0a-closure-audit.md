# WI0a Closure Audit——01-wi0a-d10-d11-emit-decisions.md

> Auditor: 独立子 agent（fresh session，与起草审查 R1 agent_37fb7796 / R2 agent_f2b54afc 均不同 task）：agent_3c4271e1-33c4-4662-bc74-afc93591ba25
> Date: 2026-10-08
> Verdict: **APPROVED**（0 Blocker / 0 Major / 2 Minor，处置见文末）

## 验证矩阵

| # | 验证项 | 结果 | 证据 |
|---|---|---|---|
| 1 | design doc D10/D11 两节要素 | PASS | §3 :33-44（放行+修订标记 :33/:39；形态锁定 :39；三项随裁 :39-40；反对处置 :41；受影响 WI37 :44；首裁保留+漂移注记+废止标记 :38/:43）；§2 :21-31（生效前提 WI38 done :27；边界三条 :28；WI38 义务 :31；首裁记录 :26/:30）；头部 :4/:5/:7（首裁日期保留/修订日期新增/委托链） |
| 2 | D9/D12 未被改动 | PASS | `git diff HEAD` 仅 3 hunk（头部 :1-11、D10/D11 :17-47），D9/D12 纯上下文 |
| 3 | 代码锚点抽查 | PASS | AdvancedTransforms :241-248 accumulationMode 门、:233-238/:258-263 triggerId 双层门；WindowOperator :492 缺省 ACCUMULATING、:590-596 A&R 门、:1388-1400 DISCARDING purge（skipPurgeForLateness :1394）；WindowingStrategy.java :52/:60 缺省 DISCARDING；stream.xdef :48-56 strategy 属性——全部精确命中 |
| 4 | FU-8 论据复核（Anti-Hollow） | PASS | getAccumulationMode 全文件仅 :241-246（buildWindow 零消费）；WindowOperatorFactoryImpl 零 setAccumulationMode；triggerId 消费点仅 _gen 存取器+fail-fast 门；triggers 包 11 类与修订理由一致 |
| 5 | roadmap 两行 | PASS | 前置裁定表 :70「已裁定 owner（委托链）2026-10-08」+结论短句+落档位置；状态行 :79 done，括注程序化校验单层且无圆括号字符；diff 仅 2 hunk 无越权 |
| 6 | 解析器 | PASS | items 18 milestones 4 wi0a done |
| 7 | check-doc-links --strict | PASS | 退出码 0（3 warnings 均在无关历史计划，不阻塞） |
| 8 | Anti-Hollow（文档版） | PASS | 每条结论有代码证据+语义理由；D10 显式以 WI38 接线为生效前提（恰因声明值当前零消费）；D11 封闭枚举绑定 WI37 钉码义务；Deferred/Follow-ups 均为空，无降级 |
| 9 | 授权一致性 | PASS | 2026-10-08 委托链四处一致：design doc :7、plan Owner 行、roadmap :70/:79、daily log |

## Minor 发现与处置

1. **时序**：plan 文面「audit 通过后翻转」，实际翻转先于 audit 结论——受控：plan item 2 括注预先声明「翻转后状态行复核回填证据」安排，本 audit 已实测翻转后状态合规（REJECTED 时可回退）。处置：接受，证据回填 plan Closure 段。
2. **closure 残留**（audit 时点观察）：plan Phase 2 checkbox 未勾/Closure 占位/audit 文件未写/变更未提交——均为翻转时动作，非审计失败。处置：随本次翻转一并完成（本文件即证据落档），plan completed 后重跑 check-plan-checklist --strict 确认退出码 0，随 closure 提交。

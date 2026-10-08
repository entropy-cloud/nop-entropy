# WI38 Closure Audit——03-wi38-accumulation-mode-wiring-fix.md

> Auditor: 独立子 agent（fresh session，与起草审查 agent_1443da31 均不同 task）：agent_825b8c7a-3c93-48ac-b0fd-d26214550c16
> Date: 2026-10-08
> Verdict: **APPROVED**（0 Blocker / 1 Major-M-1 已修复 / 3 Minor）

## 验证矩阵

| # | 验证项 | 结果 | 证据 |
|---|---|---|---|
| 1 | 接线五环 live 代码 | PASS | IWindowOperatorFactory :261-340 第四代 4 default 方法（向下委托）；WindowedStreamImpl :67 字段/:235-242 setter/getter/四 call site（:318/:338/:357/:375）调第四代传字段值；WindowOperatorFactoryImpl :356-448 覆写第四代、configureBuilder :468/:479 无条件 builder.accumulationMode；AdvancedTransforms :229-232 消费+null 归一 DISCARDING、门 :255-261 仅拒 A&R、类级 javadoc :96-103 同步；WindowOperatorBuilder :110-112 → WindowOperator :198/:492 既有语义原样 |
| 2 | 生产实现唯一性 | PASS | implements IWindowOperatorFactory 全仓 grep：生产仅 WindowOperatorFactoryImpl，其余为测试 fake——无第二实现漏覆写 |
| 3 | 判别测试三层 | PASS | flow TestWindowAccumulationModeDeclaration 4 例（环 1→2：getAccumulationMode 断言 + 缺省档归一 + A&R 拒绝）；core TestWindowedStreamAccumulationModeWiring 5 例（环 2→3：capturing fake 四 call-site + null 直通）；runtime TestWindowOperatorFactoryAccumulationModeWiring 12 执行（环 3→5：四方法×DISCARDING/ACCUMULATING/null，反射断言真实 WindowOperator protected 字段） |
| 4 | 钉码与双层门 | PASS | TestStreamCheckpointAndWindowContract :283 钉 A&R；flow 门 + runtime open() 门（WindowOperator :590-593）双层保持（TestPaneInfoAndAccumulationMode :291-312 原样绿）；skipPurgeForLateness :1394-1395 未动；WindowOperator.java 在 diff 中零出现 |
| 5 | D10 文档三处 | PASS | window-failfast-decisions :32 生效前提达成行（五环+三层测试名与 live 一致）；sql-subset §4b :108；docs-for-ai/03-modules/nop-stream-sql.md :52-53 |
| 6 | gate-inventory + 完备性门 | PASS | gate-inventory.json :50 accumulationMode 登记；TestInvariantTableCompleteness 重跑 11/11 绿 |
| 7 | 回归证据 | PASS | _tmp/wi38-full-regression2.log BUILD SUCCESS；core 138/0s、runtime 1336/10s、flow 103/14s、sql 91/0s；TestTumbleAggregateQueryE2E 与 TestAdvancedPipelineE2E 均 1/1 绿（端到端不变） |
| 8 | 零越权 | PASS | 代码 diff 恰限 core/runtime/flow 六文件+三新测试+两注释更正；文档恰限三 owner doc+gate-inventory+plan/log；roadmap WI38 行 :90 仍为 todo（翻转留待 audit 后，正确） |
| 9 | plan 状态机 | PASS | Plan Status active、Phase 1 completed 全 [x]、Phase 2 in progress |

## Major 发现与处置

- **M-1**：plan :72/:78/:79 的 flow 层证据描述（「三宿主元素」「transformation 中 WindowOperator 字段反射断言」）与 live 测试不符——flow 测试实际只断言 WindowedStreamImpl.getAccumulationMode()（环 1→2；flow 测试类路径无 runtime，XML 无宿主元素）。闭环实质成立：五环每环均有直接钉码（flow 1→2 / core 2→3 / runtime 3→5）。处置：plan 三处文本已按实际三层分工改写（本 audit 提交前完成），改写后 textual-consistency 达成。

## Minor（不阻塞）

1. flow 测试 javadoc 如实标注环分工——代码侧诚实，仅 plan 文本滞后（M-1 同源，已修）。
2. plan 01 Related 行编号更正属 plan 文档族管理性修订，log 有记录，授权范围内。
3. 新 flow 测试 import 组内排序非严格字母序——组间分组正确，不违反仓库组级约定。

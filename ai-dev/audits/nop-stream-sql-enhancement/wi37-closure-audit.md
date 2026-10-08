# WI37 Closure Audit——04-wi37-emit-two-tier.md

> Auditor: 独立子 agent（fresh session，与起草审查 agent_18f92107 / agent_d39b912a 均不同 task）：agent_e48657b7-01b3-4e3d-933c-a6204cd427ca
> Date: 2026-10-08
> Verdict: **APPROVED**（0 Blocker / 0 Major / 5 Minor——M-1/M-3/M-5 已随收口处置，M-2 归属注记入提交信息，M-4 证据采用实测数字）

## 验证矩阵

| # | 验证项 | 结果 | 证据 |
|---|---|---|---|
| 1 | 声明面 live 代码 | PASS | StreamEmitMode :37-41 枚举 + :54-66 @StaticFactoryMethod fromName（大小写不敏感 NAME_MAP + 未知值 NopException nop.err.stream.invalid-arg）；EmitUpdateTrigger :33-43 onElement FIRE+注册窗口末 timer（watermark 已过立即 FIRE 不注册）/:45-49 onEventTime 窗口末 FIRE 非窗口末 CONTINUE/:56-59 clear/:61-72 canMerge+onMerge；stream.xdef :53（strategy）与 :108-112（sql）emit=!enum:CLOSE |
| 2 | 消费链 | PASS | ISqlStreamCompiler :47 4 参抽象 + :53-55 3 参 default 委托 CLOSE；Provider :25-29 覆写；expandSqlModel :327-328 传 sql.getEmit()；AdvancedTransforms :231-234 归一声明档 → :241-244 UPDATE 无条件强制 ACCUMULATING + 装配 EmitUpdateTrigger；buildJoin :583-588 join 引用 UPDATE fail-fast（R2-N1）；StreamSqlCompiler :116-119 4 参重载保留委托 CLOSE（既有 ~20 直调零改动有钉码）+ :153 ctx 透传 + :1085 写点大写 "UPDATE" + :336/:421/:481 三路径 failFastOnEmitUpdate（plain-TUMBLE 同方法覆盖） |
| 3 | 判别测试 29+1 例 | PASS | core 5+7（+收口补 Serializable 1）；flow 8（update 装配/显式 DISCARDING 被覆盖/CLOSE 缺省等价/表面大写+小写被拒 F-1 钉/triggerId 门/非法值）；sql 8（产物写点/缺省无属性/4 参兼容/plain-TUMBLE+plain+连续+join 四门）+ E2E 1（emit=UPDATE 12 发射=逐记录+窗口末终值 vs close 档 4——断言精确到 multiset） |
| 4 | D11 边界 | PASS | triggerId 双层门保留（现行 :257-262/:283-288，内容一致仅行号漂移）；无 trigger 注册表（全仓 grep 无）；WindowOperator/WindowOperatorBuilder 零改动（git diff 无 runtime 文件） |
| 5 | F-1 修正合法性 | PASS | 机制依据 EnumStdDomainHandler.parseProp 常量名字典精确匹配；语义/挂点/triggerId 实质零变化；落档四处一致（plan F-1 节 + D11 §3 修正行 + xdef 注释 + flow 钉码同时断言大写可解析+小写被拒） |
| 6 | 回归 | PASS | _tmp/wi37-full-regression.log BUILD SUCCESS；按包名前缀实测 core 1755（1 skip）/flow 198/sql 100/runtime 1336（10 skip），0 Failures 0 Errors（M-4：闭合格证采用实测数字） |
| 7 | 零越权 | PASS | diff 恰限 core 2 新文件+2 测试、flow 4 文件+1 测试、sql 2 文件+2 测试、xdef、_gen×3（合法再生成，diff 与 xdef 逐字一致）、gate-inventory（plan 03 已声明项漏提交，M-2 归属注记）、文档三处+plan/log；roadmap WI37 行仍 todo |
| 8 | plan 状态机 | PASS | active / Phase 1 completed 全 [x]（含 F-1 节）/ Phase 2 in progress |

## Minor 处置

1. **M-1**（StreamEmitMode/TestStreamEmitMode javadoc 残留 F-1 前小写叙事）——已更正：枚举 javadoc 改为大写表面+F-1 叙事，测试 javadoc 同步、方法名改 lowercaseFormsResolveAtTypedBindingLayer（诚实反映 typed 层容错语义）。
2. **M-2**（gate-inventory.json accumulationMode 条目系 plan 03 已声明项漏提交滞留本工作树）——随 WI37 提交，提交信息与 log 显式归属 WI38。
3. **M-3**（`<sql emit>`→SPI 接缝无断言）——已补：TestSqlModelExpansion 新增 sqlEmitAttributeFlowsToCompiler（容器取 fake bean 断言 lastEmit==UPDATE，防 null 回归被静默归一 CLOSE）。
4. **M-4**（回归小计数字误归属）——Closure Evidence 采用 auditor 实测数字。
5. **M-5**（plan 正文残留小写字样 + trigger Serializable 断言缺失）——plan 加「文本一致性注记」指向 F-1；TestEmitUpdateTrigger 补 triggerIsSerializable（Serializable 断言 + ObjectOutputStream 实际写）。

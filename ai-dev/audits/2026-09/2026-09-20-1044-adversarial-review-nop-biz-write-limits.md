# 对抗性评审：CrudBizModel 批量/by-query 入参上限（plan 2269）的使用者视角质量

> Type: adversarial-review
> Date: 2026-09-20 10:44
> Reviewer: 独立 general-purpose 子 agent（fresh session，agent_22c3de33-2dc7-435d-946b-083f70238388）
> Scope: commit 7bb10960fb（BizConfigs/BizConstants/BizErrors/CrudBizModel/测试/service-layer.md/CHANGELOG）
> Dimensions: 使用者直观性 / 实现漏洞 / 误导性 / 概念清晰度

## 结论

无 P0。安全语义经独立验证全部成立：GraphQL 集合入口 8/8 覆盖、零副作用为真、`@BizAction` 不可达性（ReflectionBizModelBuilder.java:168-186）、cloneInstance 深拷贝隔离、doBatchGet 提取行为等价、树路径无回归、测试 17/17（当时）独立复跑通过。

## 发现与处置

| 编号 | 严重度 | 发现 | 处置 |
|------|--------|------|------|
| R1 | P1 | `updateByQuery` 空 data 本是 no-op（doUpdateByQuery 直接 return 0），但检查前置导致空 data 也执行 COUNT 甚至抛错 | **已修复**：wrapper 加 `isEmptyMap(data)` 早退，与 do\* 语义一致；新增回归测试 testUpdateByQueryEmptyDataIsNoOpWithoutCountCheck |
| R2 | P2 | service-layer.md "ids 直传不再构成对 IN 上限的旁路"与代码事实不符（ids 走 batchGetEntitiesByIds 不过 filter 校验，两条上限独立） | **已修复**：改写为"ids 通道上限是 maxBatchSize 而非 100，两条通道独立"，并补三上限互不联动的辨析段 |
| R3 | P2 | batch-size 错误消息无行动指引、不指明超限参数名（batchModify 双集合无法分辨） | **已修复**：消息补分批/ext:maxBatchSize 指引；`checkMaxBatchSize(paramName, collection)` 带 ARG_PARAM_NAME；新增 testBatchSizeErrorCarriesParamName |
| R4 | P2 | 迁移指南漏掉"显式 limit 分批删除"模式已失效（真实受损模式） | **已修复**：CHANGELOG 与 service-layer.md 均点名该模式，给出替代路径 |
| R5 | P2 | "改用后台批量通道"对前端不可执行（@BizAction 不在 GraphQL schema） | **已修复**：消息改为"由服务端开发使用内部通道" |
| R6 | P2 | asDict 消息称"字典选项被截断"实际是拒绝返回（未截断任何东西），且"缩小字典范围"对调用方不可执行 | **已修复**：改为"已拒绝返回不完整的字典选项，请由对象的维护方调整" |
| R7 | P2 | count==limit 完整执行时 do\* 仍记 truncated warn，误导运维（plan 已裁定 do\* 零 diff 保留该 warn） | **已文档化**：service-layer.md + CHANGELOG 注明已知噪音 |
| R8 | P2 | ext:maxBatchSize 调低被静默忽略 | **接受**（文档已写明"仅允许抬高"，与 ext:maxPageSize 语义一致；一致性优先） |
| R9 | P3 | 额外 COUNT 成本未告知 | **已文档化**（service-layer.md + CHANGELOG） |
| R10 | P3 | 直接构造 CrudBizModel 的第三方单测需补 BizObject 注册 | **已文档化**（CHANGELOG 迁移指南③） |
| R11 | P3 | 100/500/1000/limit 四个数字缺辨析 | **已修复**（并入 R2 的辨析段） |

## 复核后测试状态

- TestCrudBizModelWriteLimits：19/19（新增 2 个针对 R1/R3 的回归测试）
- 修复后 do\* 仍然零 diff（R7 按计划裁定不改代码）

## 评审原文

完整评审文本见当日开发日志关联的子 agent 输出（agent_22c3de33-2dc7-435d-946b-083f70238388）；四维度总评：直观性——配置名自解释但两条错误消息一强一弱；实现漏洞——核心机制干净，问题集中在检查安放位置（R1）；误导性——两处面向前端的消息带偏使用者（R5/R6），文档一处事实性错误（R2）；概念清晰度——三上限边界立得住，交叉辨析不足且唯一交叉说明恰好是错的（R2/R11）。

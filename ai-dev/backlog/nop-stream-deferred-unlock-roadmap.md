# nop-stream 延期项解锁 roadmap（deferred-unlock）

> **立项依据**：2026-10-10 owner 指令「（maxDelay 声明面）这个不是很容易实现吗？容易实现的部分都写计划自动完成」+ 对 transform 级 SQL 的 EQL 支持质疑（「stream sql 不是已经有内置支持了吗，nop orm eql 也做了很多增强，并且有执行器可以直接解析 AST 执行」）。本指令构成 owner 对 WI0(e) 延期项中**易实现部分**的解除裁定；裁定回写见 vision §六裁决记录。
>
> **前置事实核查（2026-10-10，代码实证）**：
> 1. **maxDelay XDSL 声明面早已交付**——`stream.xdef:52` `<strategy maxDelay="long">` + `_WindowingStrategyModel._maxDelay` + `AdvancedTransforms.buildWindow`（:229-232，WI8/plan 2332 消费）+ `TestWindowMaxDelayWiring`（flow，声明→WindowedStream 全链断言）。**WI25（plan 2371）Deferred 段「DSL 声明面属 Follow-up」为失实登记**（其引用的「TestMaxDelayWiring」实为 TestWindowMaxDelayWiring/TestWindowMaxDelay）。U1 修正登记。
> 2. **限速**：引擎面（`SourceRateLimiter`，multi-engine WI5）+ connector 参数面（kafka/file `rateLimitRowsPerSecond`/`rateLimitBytesPerSecond`）+ 校验面（`StreamConfValidator` + `TestRateLimitValidationFace`）均在；缺的是**作业 DSL 泛化声明面**（`<source>` 任意 bean 源的限速属性）。
> 3. **tableId**：引擎面（`StreamRecord.withChangeMetadata`，multi-engine R4）在；缺 DSL 声明属性。
> 4. **transform 级 SQL**：顶层 `<sql>` 模型生成器已内置（nop-stream-sql 基于 EQL 解析器，schema 自携带）；WI16 已锁 `ISqlStreamCompiler` 五参 SPI。缺的是 **transform 级形态**：编译产物从「完整模型替换」变为「子图片段拼接」。EQL 解析器/求值器全部复用，无需新解析设施。
> 5. **E3「引擎路由 defect」**：诊断为**误判反转**（U5）——非引擎缺陷（词表长度算错+copy-tap 语义误读）；测试期望已更正、@Disabled 钉已启用（[3,3]），顺带修复 singleton source 测试隔离缺陷。
> 6. **topN 目录化 / E6 async / 区间谓词 / file tailing**：非易实现（需聚合目录扩展设计 / vision §六决策点 / §四边界解除 / 真连接器工程），**维持延期**，触发条件不变。

> Last updated: 2026-10-10（v2：U2 交付——`<sqlTransform>` transform 级 SQL 声明面（plan 2375，对抗审查 4 Major 并入+独立 audit 首轮 REJECT 2 must-fix 修复后 APPROVE）；sql 165/0 + flow 307/0 + 12 场景 15/0；顺带发现并登记 E1-E12 fixture 的 lint gate 存量缺登记（10 条 WARNING 白名单补齐，与 U2 无关））
> 历史版本：2026-10-10（v1：立项——六项核实 + U1-U5 分解；U1 当日落地（maxDelay 勘误）、U5 当日落地（E3 误判反转+测试隔离缺陷修复））

## 工作项

- [x] **U1** maxDelay 登记失实修正 + E9 声明路径验收补强：plan 2371 Deferred 段勘误注记 ✅ + roadmap v27→v28 header 订正（deferred 清单移除 E9 MAX_DELAY）✅ + `TestXdslScenarioE9.maxDelayDeclarationPathIsConsumedOnScenarioWindow`（fixture 第二条链 `<strategy maxDelay="500">`+独立 source/sink bean；`StreamModelDslBuilder.registeredStream` 放宽 public 供跨模块验收断言）✅ E9 2/0 绿（fork+JDK25）+ user guide 核实本就正确（:75-79/:280 已记载放行面，无需改）✅（Completed: 2026-10-10）
- [x] **U2** transform 级 SQL 声明面（WI16 successor 原方案 A，owner 解除落地）：`<sqlTransform>` 顶点（xdef+再生成）+ `ISqlStreamCompiler.compileTransform` 片段契约 + `StreamSqlCompiler.compileTransform`（UNION/JOIN fail-fast）+ `expandSqlTransforms` 拼接（入度恰一/FORWARD 无 outputTag/全量改名/原位拼接/注册表合并/终点唯一）+ 测试 sql 165/0（fragment 契约 8+E2E 2）+ flow 307/0（expansion 10）+ 12 场景 15/0 + 变异探针红绿闭环 + 文档四处。对抗性审查 4 Major 并入；独立 audit 首轮 REJECT（2 must-fix）修复后 APPROVE（agent_8ee9e38e）。plan 2375。（Completed: 2026-10-10）
- [x] **U3** 限速泛化 XDSL 声明面：`<source rateLimitRowsPerSecond/rateLimitBytesPerSecond>` 属性（xdef+模型）+ `buildSource` 泛化包装（`SourceRateLimiter` 令牌桶，任意 SourceFunction，非 connector 专属）+ typed 校验（负数拒绝）+ runtime E2E 断言 + connector 参数面文档互引。（Deliverable: 声明面 + 泛化包装 + 测试；deps: 无；Item Type: Feature）✅ 2026-10-10 plan 2376（`applyRateLimitDeclaration` 包装在一致性保真校验之后；flow 决策钉 5/0 + runtime E2E 2/0 含 1 rps 接线节流钉；core 1759/0 + flow 312/0 + sql 165/0 零回归；独立 audit APPROVE（agent_0ae8e678）；builder 接线行被 chore 提交 1fa609e31c 提前卷入，收口提交显式记录分裂）
- [x] **U4** tableId XDSL 声明面：`<source tableId="...">` 属性 → 源记录 `withChangeMetadata(INSERT, tableId)` 元数据注入 + 多表路由场景断言（与 WI20 表标识字段 filter 路由降级形态互补——声明面可用时直达 record 元数据）。（Deliverable: 声明面 + 元数据 E2E；deps: 无；Item Type: Feature）✅ 2026-10-10 plan 2377（优先级=值级 `ChangeSemanticView` > 声明 > 无；载体 `SourceTransformation.declaredTableId` 经图生成器至算子，`copyForSubtask` 双变体同传（审查 Blocker 修复）；FLIP-27 路径不盖声明 successor 登记；core 6/0 + flow 3/0 + runtime E2E 2/0（多表归属零交叉经 `<custom>` 观察缝）+ 变异探针红绿；回归 core 1765/0 + flow 315/0 + sql 165/0 + runtime 1372（1 白名单时序抖动隔离绿）；独立 audit APPROVE（agent_c1e05297））
- [x] **U5** E3「引擎路由 defect」诊断反转（非缺陷）：探查代理全链路诊断 + 实测亲证——(1) 词表长度算错（`"cat".length()==3` 非 2，观察值 SHORT=[3,3] 是 cat+dog 的正确 len）；(2) 恒真对照 [3,4,3,4] 是 copy-tap 直通合同本身（SideOutputTapOperator 主路无条件透传），对「路由是否损坏」无判别力；(3) 引擎路由（StreamTaskInvokable.EdgeWriterOutput）静态核验自洽+WI8b main=[1,2,3] 钉佐证。处置：期望更正 [2,3]→[3,3] 并启用 @Disabled 钉（shortWordsStayOnMainPath）；顺带发现并修复真实测试隔离缺陷（e3WordSource singleton 被 cancel 耗尽→`scope="prototype"`）；E3 全类 2/0 绿。plan 2368 勘误注记 + roadmap v29。（Completed: 2026-10-10）
- [ ] **U6** 收口：全量回归（flow+sql+runtime，fork+JDK25）+ compliance.yml xdsl-scenarios 门禁补新测试类 + roadmap 勾选 + 独立 closure audit。（Deliverable: 收口提交；deps: U1-U5；Item Type: Closure）

## 维持延期（触发条件不变）

| 项 | 理由 | 重启触发 |
|---|---|---|
| E6 async 声明面 | vision §四 Non-Goal + §六决策点（线程模型变更） | owner 解除裁定并回写 vision |
| 区间谓词声明面 | vision §四排除项（非等值 join） | owner 解除裁定 |
| file tailing | 真连接器工程（watch/recovery 语义），非声明面接线 | 日志 tail 类真实诉求 |
| topN 目录化 | 聚合目录 fnId+expr 单槽形状无法承载复合描述符，需目录扩展设计（可选方案：aggregator 增 orderBy/limit 槽或独立 topN 注册表） | owner 采纳某扩展设计 |

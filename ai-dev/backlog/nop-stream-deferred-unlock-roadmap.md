# nop-stream 延期项解锁 roadmap（deferred-unlock）

> **立项依据**：2026-10-10 owner 指令「（maxDelay 声明面）这个不是很容易实现吗？容易实现的部分都写计划自动完成」+ 对 transform 级 SQL 的 EQL 支持质疑（「stream sql 不是已经有内置支持了吗，nop orm eql 也做了很多增强，并且有执行器可以直接解析 AST 执行」）。本指令构成 owner 对 WI0(e) 延期项中**易实现部分**的解除裁定；裁定回写见 vision §六裁决记录。
>
> **前置事实核查（2026-10-10，代码实证）**：
> 1. **maxDelay XDSL 声明面早已交付**——`stream.xdef:52` `<strategy maxDelay="long">` + `_WindowingStrategyModel._maxDelay` + `AdvancedTransforms.buildWindow`（:229-232，WI8/plan 2332 消费）+ `TestWindowMaxDelayWiring`（flow，声明→WindowedStream 全链断言）。**WI25（plan 2371）Deferred 段「DSL 声明面属 Follow-up」为失实登记**（其引用的「TestMaxDelayWiring」实为 TestWindowMaxDelayWiring/TestWindowMaxDelay）。U1 修正登记。
> 2. **限速**：引擎面（`SourceRateLimiter`，multi-engine WI5）+ connector 参数面（kafka/file `rateLimitRowsPerSecond`/`rateLimitBytesPerSecond`）+ 校验面（`StreamConfValidator` + `TestRateLimitValidationFace`）均在；缺的是**作业 DSL 泛化声明面**（`<source>` 任意 bean 源的限速属性）。
> 3. **tableId**：引擎面（`StreamRecord.withChangeMetadata`，multi-engine R4）在；缺 DSL 声明属性。
> 4. **transform 级 SQL**：顶层 `<sql>` 模型生成器已内置（nop-stream-sql 基于 EQL 解析器，schema 自携带）；WI16 已锁 `ISqlStreamCompiler` 五参 SPI。缺的是 **transform 级形态**：编译产物从「完整模型替换」变为「子图片段拼接」。EQL 解析器/求值器全部复用，无需新解析设施。
> 5. **E3 引擎路由 defect**：唯一 live defect，3+ 出边 tap vertex 吞无 tag 主路记录（@Disabled 复验钉已备）。诊断进行中，按诊断结果裁定修复或维持延期。
> 6. **topN 目录化 / E6 async / 区间谓词 / file tailing**：非易实现（需聚合目录扩展设计 / vision §六决策点 / §四边界解除 / 真连接器工程），**维持延期**，触发条件不变。

> Last updated: 2026-10-10（v1：立项——六项核实 + U1-U5 分解）

## 工作项

- [x] **U1** maxDelay 登记失实修正 + E9 声明路径验收补强：plan 2371 Deferred 段勘误注记 ✅ + roadmap v27→v28 header 订正（deferred 清单移除 E9 MAX_DELAY）✅ + `TestXdslScenarioE9.maxDelayDeclarationPathIsConsumedOnScenarioWindow`（fixture 第二条链 `<strategy maxDelay="500">`+独立 source/sink bean；`StreamModelDslBuilder.registeredStream` 放宽 public 供跨模块验收断言）✅ E9 2/0 绿（fork+JDK25）+ user guide 核实本就正确（:75-79/:280 已记载放行面，无需改）✅（Completed: 2026-10-10）
- [ ] **U2** transform 级 SQL 声明面（WI16 successor 原方案 A，owner 解除落地）：`ISqlStreamCompiler` 增 `compileTransform` 片段契约（输入 schema 自携带，复用顶层 parse 链，产物=transforms+edges 片段）+ xdef 新 transform 顶点 `<sqlTransform>` + `StreamModelDslBuilder` 拼接（DslModelParser 回灌）+ 顶层 `<sql>` 唯一约束不放宽（双钉保持，generator 语义不变）+ 形态/反退化测试（flow+sql+runtime）+ user guide 场景段。（Deliverable: SPI 契约 + 声明面 + 拼接 + 测试；deps: 无；Item Type: Feature）
- [ ] **U3** 限速泛化 XDSL 声明面：`<source rateLimitRowsPerSecond/rateLimitBytesPerSecond>` 属性（xdef+模型）+ `buildSource` 泛化包装（`SourceRateLimiter` 令牌桶，任意 SourceFunction，非 connector 专属）+ typed 校验（负数拒绝）+ runtime E2E 断言 + connector 参数面文档互引。（Deliverable: 声明面 + 泛化包装 + 测试；deps: 无；Item Type: Feature）
- [ ] **U4** tableId XDSL 声明面：`<source tableId="...">` 属性 → 源记录 `withChangeMetadata(INSERT, tableId)` 元数据注入 + 多表路由场景断言（与 WI20 表标识字段 filter 路由降级形态互补——声明面可用时直达 record 元数据）。（Deliverable: 声明面 + 元数据 E2E；deps: 无；Item Type: Feature）
- [ ] **U5** E3 引擎路由 defect 修复：按探查诊断结论裁定——若为局部路由逻辑则修复 + 启用 `TestXdslScenarioE3.shortWordsStayOnMainPath` 钉；若涉及协议层则维持 Deferred 并登记工程量。（Deliverable: 修复或裁定记录；deps: 诊断；Item Type: Fix）
- [ ] **U6** 收口：全量回归（flow+sql+runtime，fork+JDK25）+ compliance.yml xdsl-scenarios 门禁补新测试类 + roadmap 勾选 + 独立 closure audit。（Deliverable: 收口提交；deps: U1-U5；Item Type: Closure）

## 维持延期（触发条件不变）

| 项 | 理由 | 重启触发 |
|---|---|---|
| E6 async 声明面 | vision §四 Non-Goal + §六决策点（线程模型变更） | owner 解除裁定并回写 vision |
| 区间谓词声明面 | vision §四排除项（非等值 join） | owner 解除裁定 |
| file tailing | 真连接器工程（watch/recovery 语义），非声明面接线 | 日志 tail 类真实诉求 |
| topN 目录化 | 聚合目录 fnId+expr 单槽形状无法承载复合描述符，需目录扩展设计（可选方案：aggregator 增 orderBy/limit 槽或独立 topN 注册表） | owner 采纳某扩展设计 |

# sql-landing-decision——D14 参数化算子面与编译落点裁定落档

> Status: active
> 裁定日期：2026-09-30（owner 裁定）；落档日期：2026-10-02
> 负责人：仓库 owner（裁定记录：`ai-dev/logs/2026/09-30.md` 裁定理由全文——语义留在模型、Delta 可覆盖注册表条目、组合爆炸；落档由 2026-10-02 执行指令委托 ZCode 代理完成）
> Source: `ai-dev/backlog/nop-stream-sql-roadmap.md`（WI8a 行、D14 行、§3.5、Cross-Cuting 4、Assumptions A4）
> 承载 plan: ai-dev/plans/nop-stream-sql/10-wi8a-landing-decision.md
> 与 D7/D8/D13 的边界：D7 定 schema 来源与类型映射（`sql-compiler-contract.md` §1）、D8 定 `<sql>` 接口面（§2）、D13 定宿主模块（§3，分支 a 新模块 nop-stream-sql）；本裁定定**产物形态**，四者结论互不改写。

## 1. D14 裁定记录

| 项 | 内容 |
|---|---|
| 选项 | (a) 给 StreamModel 增加参数化算子面；(b) 预置 bean 家族、模型只引用 bean 名；(c) 绕过 XDSL 直产 StreamGraph |
| 结论 | **(a) 参数化算子面**（owner 2026-09-30 裁定；(b)(c) 不再作为本 roadmap 实施路径，若日后改判须先改 roadmap D14 行再动 WI8c/WI8d） |
| 裁定理由 | 语义留在模型（模型优先，vision §三 #2）；Delta 可覆盖注册表条目；避免预写 bean 组合爆炸（`ai-dev/logs/2026/09-30.md`） |

## 2. 保留面——(a) 保住了什么（附 live 证据）

| 保留面 | (a) 如何保住 | live 证据 |
|---|---|---|
| xdef 校验 | 声明面仍是 xdef 驱动的模型属性（aggregatorRef/joinRef/参数化窗口声明），经 DslModelParser 全量校验 | stream.xdef 既有校验机制 + builder fail-fast 全表（roadmap §3.5） |
| Delta 面 | 注册表条目（aggregators/joins/参数化窗口声明）是模型节点，Delta 可 `x:extends` 覆盖——WI19 将显式验证 §八 10 | vision §八 10（Delta 只改模型不 patch runtime object） |
| builder fail-fast | 构造期 resolveAggregator/joinRef 校验（未知 id、参数个数/类型不符逐项 fail-fast）沿 builder 既有模式（windowingStrategies 先例：AdvancedTransforms.java:242-264） | roadmap §3.5、A4 |

## 3. (b) 与 (c) 各自丢失什么（分化表述，附 live 证据）

### (b) 预置 bean 家族——**不丢失 bean 存在性/类型检查**，丢失三项模型级能力

- **保留**：bean 引用在 build 期经 `resolveBean` 解析且带目标类型检查，缺 bean/类型不符在构造期抛错并锚定模型位置（`nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/AdvancedTransforms.java:284`、`StreamModelDslBuilder.java:687-697`）。**不得把「(b) 丢失 builder fail-fast」写成结论**——它丢失的是更上层的能力：
- (i) **模型级参数契约**：params 不在模型内，xdef 只见 bean 名字符串，「参数个数/类型不符」无法像 (a) 的 resolveAggregator 那样在模型层逐项 fail-fast；
- (ii) **模型级 Delta 再参数化**：参数定制面移到 beans.xml/Delta bean 定义面，模型层 Delta 覆盖注册表条目的能力落空（与 §八 10 的模型优先精神背离）；
- (iii) **组合爆炸**：每种「聚合函数 × 窗口 × 参数组合」需预写一个 bean（owner 2026-09-30 原始理由）。

### (c) 绕过 XDSL 直产 StreamGraph——整体绕过三层保护

- 绕过 xdef 校验（模型不经 DslModelParser，声明面归零）；
- 绕过 builder 全部 fail-fast（构造期校验被跳过，错误下沉到运行期）；
- 绕过 Delta 面（直产对象不可被 Delta 定制，违反 §八 10 与 vision §三 #1/#2 图模型为核）。

## 4. 迁移影响

1. **声明面落点**：新增声明面（aggregators/joins/参数化窗口声明）**按 D13 落新模块 nop-stream-sql 的 schema、经 delta 扩展 stream.xdef**——不直改 `nop-kernel/nop-xdefs`；delta 扩展的具体机制（delta 路径、类型化模型类生成落点、builder 消费方式）归 WI8b plan 实测裁定并附回改条款（`sql-compiler-contract.md` §3.2 机制风险与回改条款继续有效）。
   **delta 机制裁定注记（WI8b，2026-10-02）**：schemas 声明面已在基础 stream.xdef，WI8b 无需 delta——首个真实 delta 需求顺延 WI8c plan 裁定，回改条款继续悬置。
   **回改裁定执行（WI8c，2026-10-02）**：delta 机制经实验证实不可行（typed codegen 绑定 base xdef，证据见 sql-compiler-contract.md §3.2 回改注记与 WI8c plan）——aggregators/joins 声明面改落 base stream.xdef；新模块经 SPI（IAggregatorFunctionResolver，WI8d 为 IJoinResolver 同构）承载解析资产。本条「不直改 nop-kernel/nop-xdefs」的前半句自此修订。
2. **互斥校验**：既有 bean 引用与新 aggregatorRef/joinRef 并存或双缺，构造期 fail-fast（R3 缓解）；用户面兼容与迁移说明归 **WI24**（不与本裁定重复记账）。
3. **报错变更**：WI8c 将「取代现行 bean 缺失时的报错」（现 aggregate 强制 bean 的 `ERR_STREAM_REQUIRED_ATTR` 面）——既有 .stream.xml 用户迁移注意点归 WI24 记账。

## 5. WI8b/WI8c/WI8d 范围边界确认

| WI | 范围边界（本裁定确认） |
|---|---|
| WI8b | `<schemas>` 声明面有消费者：field type 收敛受管类型名并在构造期解析为 BasicTypeInfo；coder 取 BasicTypeInfo 内建 SimpleTypeSerializer；coders 注册表仍 fail-fast（留 FU-3）；解析失败逐项 fail-fast；含模块骨架创建（D13 后果，机制 delta 扩展归其 plan 裁定） |
| WI8c | aggregators 注册表 + aggregate 的 aggregatorRef；形状同 windowingStrategies（纯参数描述符 + 稳定 ID）；bean 属性保留转可选、恰好其一 fail-fast；内置 count/sum/avg/min/max 五 id 与 BaseRule.g4 五聚合对齐，累加实现由 WI9 提供；取代 bean 缺失时报错 |
| WI8d | joins 注册表 + join 的 joinRef（joinType/leftKeyExprs/rightKeyExprs/windowStrategyRef/timeout）；**仅声明与构造期校验，运行时求值归 WI13**；windowStrategyRef 落点是 WI10 的 windowingStrategies 与 WI17 编译器 |

## 6. 正交声明

- **D7 不被改写**：schema 来源（SQL 面自带 schema 为主）与九类型映射、EqlASTParser 入口维持 `sql-compiler-contract.md` §1 结论。
- **D8 不被改写**：`<sql>` xdef 元素接口面维持 §2 结论。
- **D13 不被改写**：新模块 nop-stream-sql 宿主与依赖方向维持 §3 结论；本裁定的声明面落点已按其新模块分支表述。

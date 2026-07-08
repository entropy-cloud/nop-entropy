# Union Schema Validation Design

**日期**：2026-07-07（更新于 2026-07-07）
**范围**：`nop-kernel/nop-xlang` union schema runtime validation
**状态**：active

---

## 一、设计结论

- union schema 的运行时校验必须与现有 XDSL transform 使用同一套 subtype 路由语义。
- 路由依据是 `IUnionSchema.subTypeProp` 对应属性值。
- 子 schema 选择顺序是：先匹配同名 `typeValue`，再回退到 `typeValue="*"`。
- 缺失 subtype 或无法匹配子 schema 时，validator 必须报错，不能静默跳过或退化为 simple-schema 校验。

## 二、背景与动机

`SchemaBasedValidator` 之前没有 union 分支，导致 union schema 在运行时不会进入正确的子 schema 校验链。与此同时，`DslModelToXNodeTransformer` 已经定义了 union subtype 的选择语义。如果 validator 不复用这套契约，同一份 schema 会在 transform 和 validate 阶段表现不一致。

## 三、核心设计

### 1. 单一 subtype 路由契约

union subtype 路由以 `subTypeProp` 为唯一判别字段。validator 与 transform 都必须遵守：

- subtype 值等于某个子 schema 的 `typeValue` 时，选中该子 schema。
- 若没有精确命中，但存在 `typeValue="*"` 的子 schema，则使用该 fallback schema。
- 若 subtype 缺失，或没有可用子 schema，则报错。

### 2. 复用既有子 schema 校验链

选中子 schema 后，不单独实现第二套 object/list/map/simple 校验逻辑，而是重新进入 `SchemaBasedValidator.validate()` 的既有分发链。这样可以保证：

- mandatory/dict/simple schema 校验语义保持一致
- object/list/map 的已有递归校验逻辑不分叉
- union 只负责“选哪个 schema”，不负责重写“怎么校验”

### 3. 失败必须显式可见

union 校验失败属于 live contract gap，不允许通过 no-op 或 fallback-to-simple 的方式隐藏。对调用方可见的结果应该是标准 validation error，而不是被吞掉的路径。

## 四、拒绝了什么

- 拒绝“未知 union 直接按 simple schema 校验”：这会掩盖 schema drift，并让 transform/validate 结果不一致。
- 拒绝“validator 逐个 oneOf 试跑直到通过”：当前平台已有显式 `subTypeProp` 契约，顺序试跑会引入歧义，也与 XDSL transform 现有行为不一致。
- 拒绝“union 分支复制 object/list/map/simple 全套逻辑”：会制造第二套递归校验实现，增加后续漂移风险。

## 五、与已有设计的关系

- 本设计补足 `nop-xlang` runtime validation 契约，不改变 XDef/XDSL 中 union 的建模方式。
- 使用锚点：`SchemaBasedValidator`、`IUnionSchema`、`DslModelToXNodeTransformer.transformUnion()`。

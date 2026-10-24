# Nop 平台对 Jackson 注解的低成本支持调研

> Status: open
> Date: 2026-10-24
> Revised: 2026-10-24（按 live code 实测修订 `@JsonIgnore` / `@JsonAlias` / `@JsonTypeName` 现状与计数）
> Scope: `nop-kernel/nop-core` 的反射元数据（`BeanModelBuilder`）与 Nop JSON 链路（`JsonSerializer` / `BeanCopier` / `TreeBeanBuilder`）；不含直接使用 Jackson databind 的 8 个测试/基准站点
> Conclusion: 低成本可补的注解集中在“元数据层”：`@JsonGetter/@JsonSetter`、`@JsonIgnoreProperties`、`@JsonIgnore` 双向语义、多 `@JsonAlias`、`@JsonTypeName`、`@JsonRawValue`、`@JsonIgnoreType`、`@JsonInclude(NON_ABSENT)`，均可在 `nop-core` 单模块内以 S 级成本完成；`@JsonValue`/`@JsonPropertyOrder`/`@JsonFormat`/`@JsonUnwrapped`/`@JsonNaming` 为 M 级；`@JsonSerialize`/`@JsonDeserialize`、`@JsonView`、对象图引用类为 L 级，不建议投入。

## Context

- Nop 的 JSON 序列化/反序列化默认不经过 Jackson databind，而是走自研反射模型：`JsonTool` → `DefaultJsonTool` → `JsonSerializer` / `BeanTool` → `BeanModel`。
- 但 `nop-core` 的 `BeanModelBuilder` 已经主动识别一部分 `com.fasterxml.jackson.annotation.*` 注解，把它们编译进 `BeanModel` 元数据。
- 因此“是否支持某个 Jackson 注解”等价于：**该注解是否在 `BeanModelBuilder` 中被读取，并在 `JsonSerializer`（写）和 `BeanCopier`（读）中被兑现**。
- 依赖面为零成本：`jackson-annotations` 已由 `nop-kernel/nop-api-core/pom.xml:39-42` 以 compile scope 引入，`nop-core` 通过 `nop-api-core` 传递获得；Jackson 3 迁移计划也明确 annotations 继续留在 Jackson-2 坐标线（`ai-dev/plans/2439-wi7-jackson3-migration.md`）。
- 反射模型按类缓存（`ReflectionManager`），注解只在建模时读取一次，补注解不引入运行时解析开销。

## Analysis

### 1. 现有 JSON 链路锚点

| 环节 | 入口 | 说明 |
|------|------|------|
| 元数据建模 | `nop-kernel/nop-core/src/main/java/io/nop/core/reflect/impl/BeanModelBuilder.java` | 扫描字段/getter/setter/构造器，产出 `BeanModel` |
| 序列化 | `nop-kernel/nop-core/src/main/java/io/nop/core/lang/json/serialize/JsonSerializer.java` | `writeBean()` 按 `BeanModel` 属性表输出 |
| 反序列化 | `nop-kernel/nop-core/src/main/java/io/nop/core/reflect/bean/BeanCopier.java` | `setFieldValue()` 按目标属性名写入 |
| 属性查找/别名 | `BeanModel.getPropertyModel()` (`BeanModel.java:282-291`) | 先精确名，再查别名 |
| 未知属性策略 | `BeanCopier.setFieldValue()` (`BeanCopier.java:536-547`) | 由全局 `BeanCopyOptions.ignoreUnknownProp` 控制 |
| 扩展点 | `@BeanSerializer` / `@BeanDeserializer` + `JsonSerializerFactory` / `JsonDeserializerFactory` | 已有按名称注册的 Nop 原生序列化器/反序列化器 |

### 2. 已支持注解（含现有语义缺口）

| 注解 | 当前支持 | 证据 | 缺口 |
|------|---------|------|------|
| `@JsonProperty` | 显式 JSON 名、构造函数参数名、`access=READ_ONLY/WRITE_ONLY` | `BeanModelBuilder.java:240-244, 421-433, 455-470` | `required` 未实现；任意方法上的 `@JsonProperty` 不会被识别为访问器（`isGetter/isSetter` 只认命名字法，`BeanModelBuilder.java:602-626`） |
| `@JsonIgnore` | 字段/getter 上标记后不参与序列化；默认读路径（`onlySerializableTarget=true`）也已忽略 | `BeanCopyOptions.java:34-36`；`BeanCopier.java:496-503`；`BeanModelBuilder.java:289-293, 435-438` | (a) 覆写方法不继承基类标注（`@JsonIgnore` 非 `@Inherited`，`ClassModelBuilder.inheritAnnotations()` 不传播）；(b) private 字段 + 未标注 getter 时注解从未被读取（`ChatOptions.accountKey/accountBaseUrl` 仍泄漏）；(c) public/`@JsonProperty` 字段标注被未标注 getter 覆盖（`:483`）；(d) setter 位置无效；(e) `value=false` 在 `buildProps` 被误判；(f) 被忽略属性会被 `@JsonAnySetter` 吸收；(g) `onlySerializableTarget=false` 为显式 opt-out，非缺陷 |
| `@JsonInclude` | 类级+属性级；`NON_NULL`/`NON_EMPTY`/`NON_DEFAULT` | `BeanModelBuilder.java:407-419, 497-500`；`JsonSerializer.java:208-233` | `NON_ABSENT`、`CUSTOM` 未实现；`content=` 未实现（`ALWAYS`/`USE_DEFAULTS` 走默认分支，效果等同支持） |
| `@JsonAlias` | 元数据被收集 | `BeanModelBuilder.java:461-470`；`BeanModel.java:282-291` | **实际不可用**：`propAliases` 存的是 `主名 -> 别名`，而查找时按 `输入名 -> 主名` 反查，方向相反；同一属性第二个别名会命中 `ERR_REFLECT_BEAN_PROP_ALIAS_CONFLICT` 抛错（不是静默覆盖）；`getPropAliases()` 还传入 `IBeanModelEnhancer` |
| `@JsonAnyGetter` / `@JsonAnySetter` | 支持 | `BeanModelBuilder.java:562-575, 587-594`；`JsonAnyPropertyGetter/Setter` | `@JsonAnySetter(enabled=false)` 未处理 |
| `@JsonCreator` | 仅构造函数选择 | `BeanModelBuilder.java:248-261` | 静态工厂方法、`mode=DELEGATING` 未支持（仓库内 `GlobalWindow`/`VoidNamespace` 正是静态工厂形态） |
| `@JsonTypeInfo` / `@JsonSubTypes` | 反序列化按 `property` + `name` 分派子类型 | `BeanModelBuilder.java:212-231`；`BeanCopier.java:435-447` | 只读取 `property()`；`use`/`include` 未实现；序列化不会自动写判别字段（`ChatMessage` 靠手写 `getType()` 兜底） |
| `@JsonTypeName` | 未支持 | — | `@JsonSubTypes.Type(name="")` 时无法回退到子类 `@JsonTypeName`；多个空名子类型会以 `""` 为键静默覆盖（`BeanModelBuilder.java:221-232`）；既有 `IJobInstanceState` 是单个无名子类型，需保留可用性 |

### 3. 仓库内注解使用频次（`com.fasterxml.jackson.annotation.*` import 统计；`@JsonIgnore` 另按注解行统计）

| 注解 | 命中数 | 备注 |
|------|--------|------|
| `JsonInclude` | 340 | 主要在 message bean / 生成 Bean |
| `JsonIgnore` | 98 import / 260 处注解 / 97 个类 | 主要用于派生属性；审计按字段/accessor/覆写/setter 四类分层 |
| `JsonProperty` | 80 | DTO / 构造器命名 |
| `JsonCreator` | 20 | 构造器与静态工厂 |
| `JsonAnyGetter` | 12 | 主要在 `CrudInputBase` 体系 |
| `JsonAnySetter` | 9 | 同上 |
| `JsonSubTypes` | 6 | 多态 spec / message |
| `JsonTypeInfo` | 5 | 同上 |
| `JsonValue` | 2 | `GlobalWindow`、`VoidNamespace`（当前 Nop JSON 不兑现） |
| `JsonIgnoreProperties` | 1 | `OAuthTokenResponse`（当前 Nop JSON 不兑现） |
| `JsonAlias` | 1 | 仅 `BeanModelBuilder` 自身 import |

结论：仓库真实高频集中在 `JsonInclude` / `JsonIgnore` / `JsonProperty` 三件套，已覆盖；下一步收益主要来自“补齐已有注解语义”而不是追求 Jackson 全家桶。

### 4. 低成本支持清单（按推荐顺序）

成本口径：S ≤ 0.5 天；M = 0.5–3 天；L > 3 天（含跨模块/跨接口改造与回归）。

#### P0：S 级，建议优先做

| # | 注解/能力 | 现状问题 | 改动点 | 预估 |
|---|----------|---------|--------|------|
| 1 | `@JsonIgnore` 语义补全 | 默认读路径已忽略，但覆写方法不继承、private 字段标注无效、字段标注被 getter 覆盖、setter 无效、`value=false` 误判、被忽略属性会被 `@JsonAnySetter` 吸收 | `BeanModelBuilder` 统一解析字段/getter/setter 标注并合并覆写方法白名单注解；`BeanCopier` 跳过时同时抑制 AnySetter 吸收；`onlySerializableTarget=false` 保留为显式 opt-out | 20–35 行 + 测试 |
| 2 | `@JsonGetter` / `@JsonSetter` | 非 `getXxx`/`setXxx` 命名的方法无法成为属性 | `isGetter()/isSetter()` 增加注解判断；`getPropName()` 支持注解 `value()` 覆盖 | 20–30 行 |
| 3 | `@JsonProperty` 任意方法 | 同上（Jackson 允许把任意方法标成属性访问器） | 与 #2 共用注解识别分支 | 并入 #2 |
| 4 | `@JsonIgnoreProperties` | 类级 `value` 与 `ignoreUnknown` 完全不生效 | `applyClassLevelAnnotations()` 处理 `value`；`BeanModel` 增加 `ignoreUnknownProps` 标志，`BeanCopier` 判断 `options.isIgnoreUnknownProp() \|\| beanModel.isIgnoreUnknownProps()` | 40–60 行 |
| 5 | 多 `@JsonAlias` + 方向修正 | 当前实现方向相反，等价于未支持 | `BeanModel.propAliases` 改为 `别名 -> 主名`（或 `主名 -> List<别名>`），`getPropertyModel()` 直接正查；同步 XML 转换路径 | 30–50 行 |
| 6 | `@JsonTypeName` | `@JsonSubTypes.Type.name` 为空时无法注册子类型 | `initSubTypes()`：`name` 为空时读子类 `@JsonTypeName.value()` | 15–25 行 |
| 7 | `@JsonRawValue` | 原样输出 JSON 片段的能力缺失 | 属性模型增加 `rawValue` 标志；`JsonSerializer.writeBean()` 用 `out.rawValue()` 输出 | 20–30 行 |
| 8 | `@JsonIgnoreType` | 被标注类型的属性仍会被展开 | `BeanModel` 增加 `ignoredType` 标志；序列化跳过；反序列化按“未知/忽略”处理 | 20–30 行 |
| 9 | `@JsonInclude(NON_ABSENT)` | `Optional`/`AtomicReference` 空值仍输出 | `JsonSerializer.shouldInclude()` 增加 `NON_ABSENT` 分支 | 10–20 行 |

#### P1：M 级，按业务需求触发

| # | 注解/能力 | 说明 | 主要改动 | 预估 |
|---|----------|------|---------|------|
| 10 | `@JsonValue` + `@JsonCreator` 静态工厂 | `GlobalWindow`/`VoidNamespace` 已按 Jackson 语义标注但 Nop JSON 不兑现；序列化需把对象写成单值，反序列化需按静态工厂/单参构造器还原 | `BeanModelBuilder` 识别 `@JsonValue` getter 与静态 `@JsonCreator`；`JsonSerializer.writeBean()` 前置分支；`BeanCopier` 构造路径 | S（序列化）+ M（反序列化），合计 1–2 天 |
| 11 | `@JsonPropertyOrder` | 当前 `BeanModel` 用 `TreeMap`，属性恒按字母序输出 | `BeanModel` 保存显式顺序列表；`JsonSerializer` 按顺序遍历；未标注时保持现状 | 40–60 行 |
| 12 | `@JsonFormat`（日期/数字 pattern） | 需要属性级格式元数据 + 写时格式化 + 读时解析 | `BeanPropertyModel` 增加 pattern/locale/timezone；`JsonSerializer` 与 `BeanCopier` 日期分支接入 `DateHelper` | 100–200 行 |
| 13 | `@JsonUnwrapped` | 序列化需把嵌套 Bean 属性摊平；反序列化需把未知键路由到嵌套对象 | 先做序列化（S）；反序列化依赖未知属性路由改造（M） | 序列化 30–50 行；反序列化 M |
| 14 | `@JsonNaming` / `PropertyNamingStrategy` | 需要命名策略 SPI，并只作用于 JSON 契约名 | `BeanModelBuilder` 在属性名确定前套用策略 | 60–100 行；需确认不污染 XML/GraphQL 命名 |
| 15 | `@JsonTypeInfo(use/include)` 完整语义 | 序列化自动写判别字段；`WRAPPER_OBJECT` / `WRAPPER_ARRAY` / `Id.CLASS` | `BeanModel` 保存完整 typeInfo 配置；序列化与反序列化两侧改造 | 1–3 天 |

#### P2：L 级，不建议低成本投入

| 注解 | 不建议原因 |
|------|-----------|
| `@JsonSerialize` / `@JsonDeserialize(using=...)` | `using` 指向 Jackson 自己的 `JsonSerializer`/`JsonDeserializer` 类型，与 Nop 的 `IJsonSerializer`/`IBeanDeserializer` 接口不兼容；要做适配层等于把 Jackson 运行时语义搬进来。Nop 已有等价的 `@BeanSerializer`/`@BeanDeserializer` + 名称工厂扩展点 |
| `@JsonView` | 需要把 view 上下文贯穿 `JsonSaveOptions` / `BeanCopyOptions` / 每个序列化调用，且当前仓库无真实需求 |
| `@JsonFilter` | 需要动态属性过滤器与运行时绑定，收益低 |
| `@JsonManagedReference` / `@JsonBackReference` / `@JsonIdentityInfo` | 需要对象图身份管理与循环引用策略，与 Nop 现有拷贝/序列化模型冲突大 |
| `@JsonMerge`、`@JsonProperty(required=true)`、mixins | `required` 属于校验层而非序列化层；Nop 校验走独立机制；`@JsonMerge` 与 `BeanCopier` 覆盖语义不一致 |

### 5. 推荐落地顺序

1. **先修语义 bug，不扩面**：`@JsonIgnore` 双向语义、`@JsonAlias` 方向/多值（#1、#5）。这两项是“已有承诺但未兑现”，修复风险集中在反序列化行为变化。
2. **补元数据类注解**：`@JsonGetter/@JsonSetter`、`@JsonIgnoreProperties`、`@JsonTypeName`（#2–#4、#6）。
3. **补输出形态**：`@JsonRawValue`、`@JsonInclude(NON_ABSENT)`（#7、#9）。
4. **按真实需求再做 M 级**：#10（`@JsonValue`）优先于 #11–#15，因为仓库已有两处真实标注。

## 风险与约束

- **公共反射接口变更**：`IBeanModel` / `IBeanPropertyModel` 属于 `nop-core` 公共反射 SPI。若通过新增接口方法承载标志，需按 `AGENTS.md` 的 Protected Areas 走 plan-first，并检查 `BeanModel`、`RecordBeanModelBuilder`、`AnnotationBeanModelBuilder`、`StringBeanModelEnhancer` 及测试实现类。优先用已有字段/默认方法降低影响面。
- **默认行为变化**：`@JsonIgnore` 默认读路径本就忽略，修复后变化集中在覆写继承、private 字段、setter 与 `value=false`；这些属性此前实际仍被读写，需在 `nop-core` 回归测试中显式覆盖，并按 260 处注解 / 97 个类分层审计（`ChatOptions.accountKey/accountBaseUrl`、`ChatMessage.role` 为具名验证点）。
- **`@JsonAlias` 修复属于“从无效到有效”**，理论上不破坏现有调用（当前无人使用），但仍应补双向测试（JSON→Bean、Bean→JSON 保持主名）。
- **属性顺序**：`@JsonPropertyOrder` 只在显式标注时改变顺序；默认字母序不动，避免大范围快照/字符串断言抖动。
- **注解版本线**：继续使用 `com.fasterxml.jackson.annotation`（Jackson-2 坐标线），不引入 databind 运行时依赖，不影响 Jackson 3 迁移。
- **测试位置**：`nop-kernel/nop-core/src/test/java/io/nop/core/reflect/`（`TestClassModel`、`TestClassLevelJsonInclude`、`TestBeanTool`）与 `.../lang/json/TestJsonTool.java` 已有同类测试，新增表驱动用例即可。
- **文档 owner**：行为变化需同步 `docs-for-ai/02-core-guides/dto-json-and-message-beans.md`（当前只列了 `@JsonInclude`/`@JsonIgnore`/`@JsonProperty` 三个注解）。

## Open Questions

- [ ] ~~`@JsonIgnore` 是否应严格对齐 Jackson（读写双向忽略）？~~ 已裁定：默认读路径已忽略（`onlySerializableTarget=true`），无需新增兼容开关；`onlySerializableTarget=false` 本身即为显式 opt-out。
- [ ] ~~`@JsonAlias` 的 `propAliases` 结构是否还有其他外部消费者？~~ 已核对：`BeanModel.getPropertyModel()` 与 `IBeanModelEnhancer.enhance(...)` 参数；修改方向时需同时评估后者。
- [ ] 是否把“受支持的 Jackson 注解清单”作为正式契约写进 owner doc，还是仅记录在 analysis？
- [ ] `@JsonNaming` 与 Nop 的 XML/GraphQL 属性命名是否存在冲突场景？若只作用于 JSON 契约，需要确认不会影响 `BeanTool` 的其他用途（如 XML Delta 合并）。
- [ ] `@JsonValue` 的反序列化是否只支持静态工厂/单参构造器即可覆盖仓库需求？

## References

- `nop-kernel/nop-core/src/main/java/io/nop/core/reflect/impl/BeanModelBuilder.java`
- `nop-kernel/nop-core/src/main/java/io/nop/core/lang/json/serialize/JsonSerializer.java`
- `nop-kernel/nop-core/src/main/java/io/nop/core/reflect/bean/BeanCopier.java`
- `nop-kernel/nop-core/src/main/java/io/nop/core/reflect/bean/BeanModel.java`
- `nop-kernel/nop-core/src/main/java/io/nop/core/reflect/bean/IBeanModel.java`
- `nop-kernel/nop-core/src/main/java/io/nop/core/reflect/bean/IBeanPropertyModel.java`
- `nop-kernel/nop-core/src/main/java/io/nop/core/lang/json/serialize/JsonSerializerFactory.java`
- `nop-kernel/nop-core/src/main/java/io/nop/core/lang/json/serialize/JsonDeserializerFactory.java`
- `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/api/CrudInputBase.java`
- `nop-ai/nop-ai-api/src/main/java/io/nop/ai/api/chat/messages/ChatMessage.java`
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/windowing/windows/GlobalWindow.java`
- `nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/oauth/OAuthTokenResponse.java`
- `docs-for-ai/02-core-guides/dto-json-and-message-beans.md`
- `ai-dev/plans/2439-wi7-jackson3-migration.md`

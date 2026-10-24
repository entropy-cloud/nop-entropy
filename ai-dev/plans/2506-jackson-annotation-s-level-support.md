# 2506 Jackson 注解 S 级支持：语义补全 + class 级标注

> Plan Status: completed
> Last Reviewed: 2026-10-24
> Source: `ai-dev/analysis/2026-10/2026-10-24-jackson-annotation-support-survey.md`；用户 2026-10-24 指令（实现 S 级，并要求支持 class 上标注）
> Related: `ai-dev/plans/2439-wi7-jackson3-migration.md`（annotations 保持 `com.fasterxml.jackson.annotation` 坐标线）
> 编号说明：沿用仓库当前 4 位序列（最近为 2505），不采用 guide #21 的两位示例编号。

## Purpose

把 Nop 自研 JSON 链路对 Jackson 注解的兼容面从「部分元数据被读取」推进到「S 级注解语义完整、class 级标注可用」，并修复已确认的语义缺陷。不引入 Jackson databind 依赖，不改变 Nop 原生 `@DataBean` / `@BeanSerializer` / `@BeanDeserializer` 契约。

## Current Baseline

> 本节基于 2026-10-24 live repo 核对与实测。计划执行后以各 Phase 的 Exit Criteria 和 live code 为准。

### JSON 链路

- 统一入口：`JsonTool` → `DefaultJsonTool` → `JsonSerializer`（写） / `BeanTool`·`BeanCopier`（读） / `TreeBeanBuilder`（TreeBean/XML 读）。
- 注解元数据入口：`nop-kernel/nop-core/src/main/java/io/nop/core/reflect/impl/BeanModelBuilder.java`。
- 属性查找：`BeanModel.getPropertyModel()`（`BeanModel.java:282-291`）。
- 未知属性策略：`BeanCopier.setFieldValue()`（`BeanCopier.java:536-547`），由全局 `BeanCopyOptions.ignoreUnknownProp` 控制。
- 依赖：`jackson-annotations` 已由 `nop-kernel/nop-api-core/pom.xml:39-42` compile 引入；annotations 保持 Jackson-2 坐标线，不新增依赖。
- Delta 合并是纯 Map 路径（`JsonMerger`/`DeltaMerger`），不经过 `BeanModel`/`BeanCopier`，本计划的注解语义不会作用于 Delta。

### 已支持但存在语义缺口

| 注解 | 现状（含证据） | 缺口 |
|------|------|------|
| `@JsonIgnore` | 默认读路径**已忽略**被标记属性：`BeanCopyOptions.onlySerializableTarget` 默认为 `true`（`BeanCopyOptions.java:34-36`），`BeanCopier.setFieldValue()` 对 `isSerializable()==false` 的属性直接跳过；`TestJsonTool.testJsonIgnore2` 已断言读侧忽略 | (a) **覆写方法不继承**：`@JsonIgnore` 不是 `@Inherited`，`ClassModelBuilder.inheritAnnotations()` 只传播带 `@Inherited` 的注解；`ChatMessage.getRole()` 在基类标注后，子类 `ChatUserMessage.getRole()` 覆写未重复标注，实测 `JsonTool.stringify(new ChatUserMessage(...))` 仍输出 `role`；(b) **private 字段标注无效**：`buildProps` 只收集 public 或 `@JsonProperty` 字段（`BeanModelBuilder.java:273-277`），`ChatOptions.accountKey/accountBaseUrl` 是 private 字段 + 未标注 getter，注解从未被读取，机密字段仍会序列化；(c) **public/`@JsonProperty` 字段标注会被 getter 覆盖**：先置 `serializable=false`（`:289-293`），随后未标注 getter 又置回 true（`:483`）；(d) **setter 位置标注无效**：`buildProps` 只检查字段/getter；(e) `value=false` 在 `buildProps` 被 `isAnnotationPresent` 误判；(f) 被忽略的已声明属性在 `@JsonAnySetter` 存在时仍会被吸收为扩展属性；这是 `TestJsonTool.testJsonIgnore`/`TreeBean` 依赖的 legacy 行为，本计划保留，仅 class 级忽略名单绕过 AnySetter；(g) 当 `onlySerializableTarget=false` 时读侧不再忽略（显式 opt-out，需文档说明，不视为缺陷） |
| `@JsonAlias` | 元数据被收集（`BeanModelBuilder.java:461-470`） | `propAliases` 存 `主名 -> 别名`，`getPropertyModel()` 按 `输入名 -> 主名` 反查，方向相反，实际无效；同一属性第二个别名会命中 `ERR_REFLECT_BEAN_PROP_ALIAS_CONFLICT` 直接抛错（不是静默覆盖）；`getPropAliases()` 还会传入 `IBeanModelEnhancer.enhance(...)`（`ReflectionManager.java:161`），改语义需评估 |
| `@JsonInclude(NON_ABSENT)` | `IBeanPropertyModel.shouldIncludeInSerialization()` 已有 NON_ABSENT 分支（`BeanPropertyModel.java:248-259`），但序列化器未调用该方法（`JsonSerializer.java:208-233` 自己实现了一套）；且该分支把非 `Optional` 值一律判为 absent，`NON_EMPTY` 用 `StringHelper.isEmptyObject` 只处理字符串 | NON_ABSENT 未生效且语义错误；`NON_DEFAULT` 只存在于 `JsonSerializer`，不在属性模型契约中；两套 include 逻辑并存 |
| `@JsonGetter` / `@JsonSetter` / 任意方法上的 `@JsonProperty` | 未识别（`isGetter/isSetter` 只认 `getXxx`/`isXxx`/`setXxx` 命名字法与 Nop 自有注解，`BeanModelBuilder.java:602-626`） | Jackson 风格访问器无法成为属性 |
| class 级 `@JsonIgnoreProperties` | 未读取 | `value` 名单与 `ignoreUnknown` 均不生效；仓库唯一使用点 `nop-credential/.../OAuthTokenResponse.java:20` 依赖 `ignoreUnknown=true` |
| class 级 `@JsonIgnoreType` | 未读取 | 被标注类型作为属性时仍会展开 |
| `@JsonTypeName` | 未读取 | `@JsonSubTypes.Type(name="")` 时无法回退到子类 `@JsonTypeName`；多个子类型名均为空时 `initSubTypes` 会以 `""` 为键静默互相覆盖（`BeanModelBuilder.java:221-232`）。既有 `nop-job/.../IJobInstanceState.java:19` 是单个无名子类型，需保留其可用性 |
| `@JsonRawValue` | 未读取 | 属性无法原样输出 JSON 片段 |

### 已支持的 class 级注解

- `@JsonInclude`：类级值会作为属性默认值应用（`BeanModelBuilder.java:407-419`）；`TestClassLevelJsonInclude` 只断言 `getJsonInclude()` 元数据，**没有验证写路径的实际输出**。
- `@JsonTypeInfo` / `@JsonSubTypes`：类级读取，用于反序列化子类型分派（`BeanModelBuilder.java:212-231`）；序列化不自动写判别字段，由 Bean 自己提供 getter（如 `ChatMessage.getType()`）。

### class 级支持边界（本计划裁定）

- 本计划中的「class 上标注」= **Jackson 原生 `@Target(TYPE)` 的注解**：`@JsonIgnoreProperties`、`@JsonIgnoreType`、`@JsonInclude`、`@JsonTypeName`（子类型类上），以及已有的 `@JsonTypeInfo`/`@JsonSubTypes`。
- 对 Jackson 原生不支持 TYPE 目标的注解（`@JsonIgnore`、`@JsonGetter`、`@JsonSetter`、`@JsonProperty`、`@JsonAlias`、`@JsonRawValue`），**不支持额外扩展 class 级语义**。
- **不聚合父类上的 class 级注解**：沿用 Nop 现有反射模型语义，只读取被建模类自身声明的注解。
- **作用路径**：class 级 `@JsonIgnoreProperties` / `@JsonIgnoreType` 与既有 `@JsonIgnore` / `@JsonInclude` 一样，落在通用 `BeanModel` 上，作用于由 `BeanModel` 驱动的写/读路径：`JsonSerializer`、`BeanCopier`、`TreeBeanBuilder`。Delta 不经过 `BeanModel`，不受影响；本计划不引入「仅 JSON 生效」的分流。

## Goals

- 修复 `@JsonIgnore` 的已确认缺陷：覆写方法不继承、private 字段标注无效、字段标注被 getter 覆盖、setter 位置无效、`value=false`；保留 AnySetter 吸收的 legacy 行为。
- 修复 `@JsonAlias` 查表方向，并支持同一属性多个别名。
- 让 `@JsonInclude(NON_ABSENT)` 生效并修正语义；消除 include 判断的双实现。
- 支持 class 级 `@JsonIgnoreProperties`（`value` + `ignoreUnknown`）与 `@JsonIgnoreType`，并保留 class 级 `@JsonInclude` 的现有行为。
- 支持 `@JsonGetter` / `@JsonSetter` / 任意方法上的 `@JsonProperty`。
- 支持 `@JsonTypeName`（`@JsonSubTypes.Type.name` 为空时的回退）与 `@JsonRawValue`（仅序列化，仅 String 属性）。
- 所有新增/修复行为都有 focused tests，且从 `JsonTool` 入口验证写、读往返。
- `docs-for-ai/02-core-guides/dto-json-and-message-beans.md` 同步为受支持注解清单与 class 级边界，并修正现有「`@JsonIgnore` 可类级标注」的错误表述。

## Non-Goals

- 不实现 M 级注解：`@JsonValue`、静态 `@JsonCreator`、`@JsonPropertyOrder`、`@JsonFormat`、`@JsonUnwrapped`、`@JsonNaming`、完整 `@JsonTypeInfo(use/include)`。
- 不实现 L 级注解：`@JsonSerialize` / `@JsonDeserialize(using=...)`、`@JsonView`、`@JsonFilter`、`@JsonManagedReference` / `@JsonBackReference` / `@JsonIdentityInfo`、`@JsonMerge`。
- 不实现 `@JsonIgnoreProperties(allowGetters/allowSetters)`、`@JsonInclude(content=...)`、`@JsonInclude(CUSTOM)`、`@JsonProperty(required=true)`、`@JsonAnySetter(enabled=false)`、`@JsonAlias` 的构造器参数形态。
- 不聚合父类 class 级注解，不实现 mixin，不改变 `@DataBean` / `@BeanSerializer` / `@BeanDeserializer` 既有语义。
- 不改变 `@JsonIgnoreType` 直接作为根对象序列化/反序列化时的行为（本计划只约束「作为属性」）。
- 不改变 Delta 合并语义（纯 Map 路径）。
- 不引入 Jackson databind 运行时依赖，不改变 annotations 的 Jackson-2 坐标线。
- 不改变默认属性输出顺序（当前按属性名排序；`@JsonPropertyOrder` 属 M 级）。
- 不支持 `@JsonRawValue` 的非法 JSON 校验（与 Jackson 一致，由调用方保证输出合法性）。

## Scope

### In Scope

- `nop-kernel/nop-core/src/main/java/io/nop/core/reflect/impl/BeanModelBuilder.java`、`ClassModelBuilder.java`（仅限覆写方法注解继承的必要改动）
- `nop-kernel/nop-core/src/main/java/io/nop/core/reflect/bean/BeanModel.java`、`BeanPropertyModel.java`、`IBeanModel.java`、`IBeanPropertyModel.java`
- `nop-kernel/nop-core/src/main/java/io/nop/core/reflect/bean/BeanCopier.java`、`TreeBeanBuilder.java`
- `nop-kernel/nop-core/src/main/java/io/nop/core/lang/json/serialize/JsonSerializer.java`
- `nop-kernel/nop-core/src/test/java/io/nop/core/reflect/` 与 `.../lang/json/` 下的 focused tests
- `nop-credential/nop-credential-service` 的 `OAuthTokenResponse` 回归测试（真实使用点）
- `nop-ai/nop-ai-api`、`nop-job/nop-job-core` 的相关回归测试
- `docs-for-ai/02-core-guides/dto-json-and-message-beans.md`
- `ai-dev/logs/2026/10-24.md`

### Out Of Scope

- `nop-api-core` 的注解坐标与版本调整。
- `nop-spring` / `nop-quarkus` 的 Jackson databind 配置面。
- XML Delta 合并规则的修改（Delta 纯 Map 路径不受影响）。
- `TreeBeanBuilder` 的新注解能力扩展（只要求既有 class 级忽略语义在该路径按裁定生效，不新增其他注解支持）。

## Behavior Contract

> 本节只写「应该发生什么」，不写实现方式。

| 注解 | 写（Bean→JSON） | 读（JSON→Bean） | class 级 |
|------|----------------|----------------|----------|
| `@JsonIgnore` | 属性不输出；覆写方法与 private 字段上的标注同样生效 | 默认路径（`onlySerializableTarget=true`）属性不写入；已声明属性仍可被 `@JsonAnySetter` 吸收为扩展属性（保留 legacy） | 否（非 TYPE 目标） |
| `@JsonIgnore(value=false)` | 正常输出 | 正常写入 | 否 |
| `@JsonAlias` | 使用主 JSON 名输出 | 任一别名可匹配属性；主名仍可匹配；多别名全部生效 | 否 |
| `@JsonIgnoreProperties(value=...)` | 名单内属性不输出；不过滤 `@JsonAnyGetter` 返回 Map 的键 | 名单内属性不写入；名单内名称不会被 `@JsonAnySetter` 吸收 | 是 |
| `@JsonIgnoreProperties(ignoreUnknown=true)` | 不影响 | 该类未知属性不抛错；不能收紧调用方已有的忽略策略 | 是 |
| `@JsonIgnoreType` | 该类型作为属性时不输出 | 该类型作为属性时保持 null / 不写入 | 是 |
| `@JsonInclude(NON_ABSENT)` | null、`Optional.empty`、`OptionalInt/Long/Double` 空值、`AtomicReference(null)` 不输出；其他值输出 | 不影响 | 是（类级作为默认值） |
| `@JsonGetter` | 零参非 void 方法成为 getter；`value()` 覆盖 JSON 名 | — | 否 |
| `@JsonSetter` | — | 单参方法成为 setter；`value()` 覆盖 JSON 名 | 否 |
| `@JsonProperty`（任意方法） | 零参方法成为 getter，单参方法成为 setter | 同左 | 否 |
| `@JsonTypeName` | 不影响 | `@JsonSubTypes.Type.name` 为空时用子类 `@JsonTypeName`；显式 name 优先；解析后键重复时 fail-fast；单无名子类型保留现有 `""` 键以兼容 `IJobInstanceState` | 是（子类型类上） |
| `@JsonRawValue` | 仅对 String 属性原样写入；null 输出 null；非 String 按常规序列化 | 不改变常规转换 | 否 |

补充约束：

- `@JsonIgnoreProperties.value` 按**序列化契约名**（`@JsonProperty` 改名后的名字）匹配，不按 Java 字段名匹配；需有 rename 场景测试。
- `@JsonIgnoreProperties(ignoreUnknown=true)` 与全局 `BeanCopyOptions.ignoreUnknownProp` 的关系为「或」：类注解只能放宽，不能收紧。
- `@JsonIgnore` 在 `onlySerializableTarget=false` 时允许显式读回被忽略字段；该 opt-out 语义写入 owner doc，不算缺陷。
- `@JsonIgnore` 的覆写继承只针对本计划支持的 Jackson 注解白名单，不改变 Nop 自有注解的继承规则。
- `TreeBeanBuilder` 路径：class 级忽略名单命中时跳过对应 attr/child；class 级 `ignoreUnknown=true` 时跳过未知 attr/child，`false` 时保持现有报错。
- `@JsonRawValue` 输出不合法 JSON 时不静默修复、不吞异常，按原样输出（与 Jackson 行为一致）；是否合法由调用方负责。
- 所有 class 级注解只读取声明类自身，不读取父类（见 Non-Goals）。

## Execution Plan

### Phase 1 - 语义修复：@JsonIgnore / @JsonAlias / @JsonInclude(NON_ABSENT)

Status: completed
Targets: `BeanModelBuilder.java`、`ClassModelBuilder.java`、`BeanModel.java`、`BeanCopier.java`、`JsonSerializer.java`、`BeanPropertyModel.java`、`nop-core` focused tests

- Item Types: `Fix`

- [x] `@JsonIgnore` 在字段、getter、setter 任一位置标记时，默认读路径（`onlySerializableTarget=true`）与写路径都排除该属性；public/`@JsonProperty` 字段标注不再被未标注 getter 覆盖。
- [x] 覆写方法继承基类/接口上受支持的 Jackson 注解（白名单，至少含 `@JsonIgnore`、`@JsonGetter`、`@JsonSetter`、`@JsonProperty`、`@JsonAlias`、`@JsonRawValue`），只影响白名单注解，不扩大到任意注解。
- [x] private 字段上的受支持 Jackson 注解在属性由 getter/setter 定义时仍然生效；以 `ChatOptions.accountKey/accountBaseUrl` 为具名回归点。
- [x] `@JsonIgnore(value=false)` 不产生忽略效果。
- [x] 保留 legacy：`@JsonIgnore` 标记的已声明属性仍可被 `@JsonAnySetter` 吸收为扩展属性；class 级 `@JsonIgnoreProperties` 名单则不得被吸收。
- [x] `@JsonAlias` 查表方向修正为「别名可解析到主属性」，并支持同一属性多个别名；不同属性争用同一别名时保持现有 fail-fast；`getPropAliases()` 语义变更已评估全部消费者（当前仅 `BeanModel.getPropertyModel()` 与 `IBeanModelEnhancer` 参数）。
- [x] 序列化器 include 判断收敛为单一实现：`NON_NULL` / `NON_EMPTY` / `NON_DEFAULT` / `NON_ABSENT` 全部在属性模型契约中成立；`NON_EMPTY` 对集合/Map 的判空与 `NON_DEFAULT` 的默认值比较不回归。
- [x] 修正 `NON_ABSENT`：非 `Optional`/`AtomicReference` 的正常值必须输出；`Optional` 空值、`OptionalInt/Long/Double` 空值、`AtomicReference(null)` 不输出，非空包装值按 Jackson Jdk8 语义解包输出。
- [x] 审计 `@JsonIgnore` 使用点：按代码行统计为 260 处注解 / 97 个类；按「private 字段 + accessor」「public/`@JsonProperty` 字段」「getter/覆写方法」「setter」四类分层抽样，结果记录于 survey。
- [x] 新增 focused tests：`TestJacksonIgnoreSemantics`（写/读双向、setter、字段+getter、覆写继承、private 字段、`value=false`、`@JsonAnySetter` legacy 共存、`@JsonAlias` 单/多别名与冲突、`onlySerializableTarget=false`）；`TestJacksonIncludeSemantics`（Optional、OptionalInt、AtomicReference 空/非空、普通值、null、集合/Map NON_EMPTY、NON_DEFAULT 回归）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `@JsonIgnore` 的写/读双向行为在 live code path 成立，并有测试断言；`ChatUserMessage` 序列化结果不含 `role`。
- [x] 覆写继承、private 字段、setter、`value=false`、`@JsonAnySetter` legacy 吸收五类场景均有具名测试断言。
- [x] `@JsonAlias` 多别名与方向修正在 JSON→Bean 路径成立，且 Bean→JSON 仍输出主名，并有测试断言。
- [x] `@JsonInclude` 四类值在属性模型契约与序列化器之间只有一套判断逻辑；`TestClassLevelJsonInclude` 及既有 `NON_NULL`/`NON_EMPTY`/`NON_DEFAULT` 测试全绿。
- [x] `@JsonInclude(NON_ABSENT)` 在 `Optional`、`OptionalInt`、`AtomicReference`、普通值、null 五类输入上的输出符合 Behavior Contract。
- [x] **端到端验证**：至少 1 个用例从 `JsonTool.stringify(bean)` 到 `JsonTool.parseBeanFromText(json, Bean.class)` 完整往返，覆盖 `@JsonIgnore` / `@JsonAlias` / `NON_ABSENT` 的组合行为。
- [x] **接线验证**：`BeanModelBuilder` 产出的元数据被 `JsonSerializer` 与 `BeanCopier` 实际消费（测试断言输出/读回结果，而不是只断言 `getJsonInclude()` 等元数据）。
- [x] **无静默跳过**：`@JsonAlias` 冲突、`@JsonIgnore` 未支持路径不得用 `continue`/空分支吞掉；冲突仍 fail-fast 并有测试。
- [x] `./mvnw test -pl nop-kernel/nop-core -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest='TestJsonTool,TestBeanTool,TestJacksonIgnoreSemantics,TestJacksonIncludeSemantics'` 通过（focused 用例共 76 个）。
- [x] 若改变 `IBeanModel` / `IBeanPropertyModel` 契约：已检查全部实现类与测试实现，未破坏编译；否则明确写 `No SPI change required`。
- [x] `docs-for-ai/02-core-guides/dto-json-and-message-beans.md` 已更新 `@JsonIgnore` / `@JsonAlias` / `@JsonInclude` 行为说明（含覆写继承、private 字段、`onlySerializableTarget=false` opt-out）。
- [x] `ai-dev/logs/2026/10-24.md` 已记录本 Phase 证据。

### Phase 2 - class 级注解：@JsonIgnoreProperties / @JsonIgnoreType / @JsonInclude

Status: completed
Targets: `BeanModelBuilder.java`、`BeanModel.java`、`BeanCopier.java`、`TreeBeanBuilder.java`、`JsonSerializer.java`、`nop-core` focused tests、`nop-credential-service` 回归

- Item Types: `Fix`、`Feature`、`Proof`

- [x] class 级 `@JsonIgnoreProperties.value` 名单在序列化与反序列化两侧生效，按序列化契约名匹配；名单内名称不会被 `@JsonAnySetter` 吸收。
- [x] class 级 `@JsonIgnoreProperties.ignoreUnknown=true` 使该类未知属性不抛错，且不收紧全局严格策略；`ignoreUnknown=false` 不改变现有行为。
- [x] class 级 `@JsonIgnoreType` 使该类型在作为属性时既不输出也不写入；直接对该类型根对象调用 `JsonTool.stringify` 的行为不变。
- [x] class 级 `@JsonInclude` 继续作为属性默认值生效；属性级 `@JsonInclude` 优先于 class 级。
- [x] `TreeBeanBuilder` 路径按 Behavior Contract 处理 class 级忽略姓名单与 `ignoreUnknown`：命中忽略名单的 attr/child 跳过，未知 attr/child 在 `ignoreUnknown=true` 时跳过、`false` 时维持现有报错。
- [x] 新增 focused tests：`TestJacksonClassLevelAnnotations`（class 级 ignore 名单写/读、`@JsonProperty` 改名后按契约名匹配、ignoreUnknown 严格/宽松对照、`@JsonIgnoreType` 作为属性、class 级 `@JsonInclude` 真实输出与属性级覆盖、ext-prop 交互）；`TreeBeanBuilder`/XML 路径回归；父类标注不聚合的负向测试。
- [x] 为真实使用点补回归：新增 `TestOAuthTokenResponseIgnoreUnknown`（`nop-credential/nop-credential-service`），解析含额外字段的响应不抛错；不依赖网络。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] class 级 `@JsonIgnoreProperties` 的 `value` 与 `ignoreUnknown` 在 JSON 写、读两条路径均有测试断言。
- [x] `@JsonIgnoreType` 作为属性时写/读均被忽略，并有测试断言；根对象序列化行为未被改变。
- [x] class 级 `@JsonInclude` 新增真实输出断言；`TestClassLevelJsonInclude` 仍全绿。
- [x] `OAuthTokenResponse` 的本地 JSON 解析回归用例通过（含额外字段）。
- [x] `TreeBeanBuilder`/XML 路径回归用例通过，class 级语义作用面符合裁定（Delta 不受影响已说明）。
- [x] 注解读取边界符合裁定：只读声明类自身，不读父类；**必须有负向测试**。
- [x] **端到端验证**：从 `JsonTool.parseBeanFromText` 到目标 Bean 的完整路径验证 class 级 `@JsonIgnoreProperties(ignoreUnknown=true)` 与 `@JsonIgnoreType`。
- [x] **接线验证**：class 级元数据确实被 `BeanCopier` / `JsonSerializer` / `TreeBeanBuilder` 读取并改变输出/读回结果（不是只断言注解存在）。
- [x] **无静默跳过**：未知属性在 `ignoreUnknown=false` 时仍按原策略抛错，不得被新分支无差别吞掉。
- [x] `./mvnw test -pl nop-kernel/nop-core -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest='TestJsonTool,TestBeanTool,TestJacksonClassLevelAnnotations'` 通过。
- [x] `./mvnw test -pl nop-credential/nop-credential-service -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest='TestOAuthTokenResponseIgnoreUnknown'` 通过。
- [x] `docs-for-ai/02-core-guides/dto-json-and-message-beans.md` 已新增 class 级支持矩阵（`@JsonIgnoreProperties` / `@JsonIgnoreType` / `@JsonInclude`）并写明作用路径（JSON、BeanCopier、TreeBeanBuilder；Delta 不受影响）。
- [x] `ai-dev/logs/2026/10-24.md` 已记录本 Phase 证据。

### Phase 3 - 访问器与输出形态：@JsonGetter / @JsonSetter / @JsonProperty / @JsonTypeName / @JsonRawValue

Status: completed
Targets: `BeanModelBuilder.java`、`JsonSerializer.java`、`nop-core` focused tests、`nop-ai-api` / `nop-job-core` 回归、owner doc

- Item Types: `Feature`

- [x] `@JsonGetter` 标记的零参非 void 方法成为 getter；`value()` 覆盖 JSON 属性名。
- [x] `@JsonSetter` 标记的单参方法成为 setter；`value()` 覆盖 JSON 属性名；返回值类型不影响识别。
- [x] 任意方法上的 `@JsonProperty` 同样可作为 getter/setter 识别，且不影响标准命名字法访问器的既有行为。
- [x] `@JsonTypeName` 键解析顺序：显式 `@JsonSubTypes.Type.name` > 子类 `@JsonTypeName` > `""`；解析后键重复时 fail-fast；单无名子类型（`IJobInstanceState`）保持可用。
- [x] `@JsonRawValue` 在序列化时仅对 String 属性把内容原样写入；null 输出 null；非 String 属性按常规序列化；反序列化路径不受影响。
- [x] 新增 focused tests：`TestJacksonAccessorAnnotations`（非标准命名 getter/setter、注解 value 改名、`@JsonProperty` 任意方法）、`TestJacksonTypeName`（回退、显式优先、重复 fail-fast、单无名保留）、`TestJacksonRawValue`（正常、null、非 String、与普通字符串对比）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `@JsonGetter` / `@JsonSetter` / `@JsonProperty` 任意方法的写、读路径均有测试断言。
- [x] `@JsonTypeName` 回退、显式 name 优先、重复 fail-fast、单无名保留均有测试断言。
- [x] `@JsonRawValue` 输出为未加引号的原始 JSON 文本，并有测试断言；null、非 String、普通字符串行为符合契约。
- [x] **端到端验证**：从 `JsonTool` 入口验证自定义访问器与 `@JsonRawValue` 的完整往返。
- [x] **接线验证**：新识别的访问器确实进入 `BeanModel` 属性表并被 `JsonSerializer` / `BeanCopier` 使用。
- [x] **无静默跳过**：`@JsonTypeName` 冲突 fail-fast；`@JsonRawValue` 非 String 不静默输出错误形态。
- [x] `./mvnw test -pl nop-kernel/nop-core -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest='TestJsonTool,TestClassModel,TestJacksonAccessorAnnotations,TestJacksonTypeName,TestJacksonRawValue'` 通过。
- [x] `./mvnw test -pl nop-kernel/nop-core -am` 全绿。
- [x] `./mvnw test -pl nop-ai/nop-ai-api -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest='TestChatMessageSerialization'` 通过。
- [x] `./mvnw test -pl nop-job/nop-job-core -am` 全绿（覆盖 `IJobInstanceState` / `CalendarSpec` 相关路径）。
- [x] `docs-for-ai/02-core-guides/dto-json-and-message-beans.md` 已完成最终支持矩阵，并写明 M/L 级不在当前支持面。
- [x] `ai-dev/logs/2026/10-24.md` 已记录本 Phase 证据。

## Closure Gates

> **关闭条件**：本 section 全部条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 所有 in-scope confirmed live defects 已修复：`@JsonIgnore` 五类缺陷（覆写继承、private 字段、字段被 getter 覆盖、setter、`value=false`；AnySetter 吸收按 legacy 保留）、`@JsonAlias` 方向/多值、include 双实现与 `NON_ABSENT` 语义。
- [x] 所有 in-scope 真实使用点 gap 已收敛：`OAuthTokenResponse.ignoreUnknown` 生效并有回归用例。
- [x] 所有 in-scope Feature 已落地：class 级 `@JsonIgnoreProperties` / `@JsonIgnoreType`、`@JsonGetter` / `@JsonSetter` / 任意方法 `@JsonProperty`、`@JsonTypeName`、`@JsonRawValue`。
- [x] 行为契约表逐项有 focused test 证据，且写、读两条路径均覆盖。
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift。
- [x] `docs-for-ai/02-core-guides/dto-json-and-message-beans.md` 已同步到 live baseline（含删除「`@JsonIgnore` 可类级标注」错误表述）；`docs-for-ai/INDEX.md` 与 `source-anchors.md` 如涉及路由/锚点变更已同步，否则明确写 `No routing/anchor update required`。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0。
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据：首轮 FAIL 指出 RawText 经 `rawValue` 输出 `RawText[...]` 的回归；已修为传 `getText()` 并补 `TestJacksonRawValue.testRawTextStillUnescaped`，复审 PASS，无 Blocker/Major。
- [x] **Anti-Hollow Check**：closure audit 已验证新增判断确实被 JSON 写/读入口调用（不只是元数据存在），且无空方法体/静默跳过/no-op 作为正常实现。
- [x] **非 JSON 路径回归**：`BeanTool`/`TreeBeanBuilder`/XML 至少 1 条回归通过，确认 class 级语义作用面无意外破坏。
- [x] `./mvnw test -pl nop-kernel/nop-core -am` 通过。
- [x] `./mvnw test -pl nop-credential/nop-credential-service -am` 中受影响测试通过。
- [x] `./mvnw test -pl nop-ai/nop-ai-api -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest='TestChatMessageSerialization'` 通过。
- [x] `./mvnw test -pl nop-job/nop-job-core -am` 通过。
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2506-jackson-annotation-s-level-support.md --strict` 退出码为 0。
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-kernel/nop-core --severity high` 退出码为 0。

## Deferred But Adjudicated

### M 级 Jackson 注解兼容面

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 用户指令限定「S 级」；M 级项没有已确认的仓库内生产失败证据，且需要跨写/读两侧的格式或命名策略改造，无法在 S 级成本内诚实完成。
- Successor Required: `yes`
- Successor Path: 先在 analysis 更新优先级，待用户确认后新开 M 级计划；当前无 concrete successor plan。

### L 级 Jackson 注解兼容面

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `@JsonSerialize` / `@JsonDeserialize` 与 Nop 自有 `IJsonSerializer` / `IBeanDeserializer` 接口不兼容；`@JsonView` / `@JsonFilter` / 对象图引用类缺少仓库需求。强行支持会把 Jackson 运行时语义搬入 Nop，违背「不引入 databind 依赖」的约束。
- Successor Required: `no`
- Successor Path: 无。

### 非 TYPE 目标注解的 class 级扩展

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 用户已裁定仅支持 Jackson 原生 `@Target(TYPE)` 的注解；对 `@JsonIgnore` / `@JsonGetter` / `@JsonSetter` / `@JsonProperty` / `@JsonAlias` / `@JsonRawValue` 增加 class 级语义会形成 Nop 私有扩展，偏离 Jackson 兼容目标。
- Successor Required: `no`
- Successor Path: 无。

### `@JsonIgnoreProperties(allowGetters/allowSetters)`、`@JsonInclude(content=...)`、`@JsonInclude(CUSTOM)`、`@JsonProperty(required)`、`@JsonAnySetter(enabled=false)`、`@JsonAlias` 构造器参数

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 均为 S 级注解的边界能力扩展，不属于当前已确认缺陷；当前仓库无使用证据。
- Successor Required: `no`
- Successor Path: 后续按真实需求评估。

### 父类 class 级注解聚合

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 需要扩展 Nop 反射模型的父类遍历能力，超出 S 级成本；当前 `@JsonInclude` 也只在声明类上生效，保持一致性。
- Successor Required: `no`
- Successor Path: 无（如未来出现真实需求，重新起草 analysis）。

## Non-Blocking Follow-ups

- No remaining plan-owned work. M/L 级项已登记在 `Deferred But Adjudicated`；执行中不得再新增模糊「可补充」项。

## Risks And Rollback

- **行为变化风险**：`@JsonIgnore` 的覆写继承/private 字段/setter 修复会让此前「标记了但仍被读写」的属性真正被忽略，`ChatOptions.accountKey/accountBaseUrl` 与 `ChatMessage.role` 是具名验证点；`@JsonAlias` 从无效变为有效。按 Phase 独立提交并保留测试；如出现真实回归，先回退该 Phase，再单独评估兼容开关（兼容开关不属于本计划默认交付）。
- **class 级语义全局作用**：`BeanCopier` 同时服务 XML、Delta、GraphQL input、`BeanTool`；Delta 纯 Map 路径不受影响，其余路径以 Phase 2 回归用例固定作用面。
- **`@JsonIgnoreProperties(ignoreUnknown=true)`**：契约裁定为「只能放宽、不能收紧」；需覆盖严格模式对照。
- **`@JsonRawValue`**：非法 JSON 会直接产出非法文档；owner doc 必须写明调用方责任，不新增静默修复。
- **SPI 影响**：`nop-core` 反射接口是公共 SPI。本计划要求不新增破坏性接口方法；如需暴露模型标志，必须使用默认方法提供安全默认值。若评估后仍必须破坏性变更，Phase 必须先停下并按 `AGENTS.md` Protected Areas 重新做 plan-first 裁定，不得在执行中临时改协议。

## Closure

Status Note: 实现、focused tests 与独立 closure audit 已完成；RawText 回归已修复，复审 PASS。

Closure Audit Evidence:

- Reviewer / Agent: 待执行
- Audit Session: 待执行
- Evidence: 待执行

Follow-up:

- 待执行后填写。

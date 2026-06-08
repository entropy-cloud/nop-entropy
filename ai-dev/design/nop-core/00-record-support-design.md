# Java Record 类型在 ReflectionManager 中的支持

**日期**：2026-06-08（更新于 2026-06-08）
**范围**：`nop-kernel/nop-core` — `io.nop.core.reflect.impl.BeanModelBuilder`、`ClassModel`
**状态**：active

---

## 一、设计结论

1. **ClassModelBuilder 不需要特殊处理 record**：record 的 accessor 方法（如 `x()`）是普通 public 方法，`discoverDeclaredMethods()` 已经能自动发现。record 的 private final backing field 不需要收集。
2. **BeanModel 侧仿照 AnnotationBeanModelBuilder 模式**：新增 `RecordBeanModelBuilder`，检测 `isRecord()` 后由 `ClassModel.getBeanModel()` 路由到该 builder。通过 `Class.getRecordComponents()` 精确获取属性列表，匹配到已有 accessor 方法作为 getter。
3. **record 实例化走 canonical constructor**：以 `RecordComponent` 名称列表作为构造参数名，用 `MethodBeanConstructor` 包装。
4. **record 自动标记为 DataBean**：record 在 Java 语言设计中就是 data carrier，与普通 class 不同。如果 record 包含不可序列化的属性类型，序列化时类型系统本身会报错，不需要额外的 `@DataBean` 白名单保护。
5. **不新增公共 API**：现有 `IClassModel` / `IBeanModel` 接口不变。

## 二、背景与动机

Java 16 引入 record 类型（`record Point(int x, int y) {}`），其反射语义与普通 class 有区别：

| 特征 | 普通 class | record |
|------|-----------|--------|
| 属性暴露 | getter 方法（`getXxx()`） | 与 component 同名方法（`x()`） |
| 构造函数 | 无参构造 + setter 或 `@JsonCreator` 全参构造 | canonical constructor（与 component 列表一一对应） |
| 可变性 | 默认可变 | 天然不可变，字段 final |
| 反射 API | `getDeclaredFields()`、`getDeclaredMethods()` | 额外有 `getRecordComponents()` |

**当前问题**：

- `ClassModelBuilder.discoverDeclaredMethods()` 能发现 record 的 accessor 方法（如 `x()`），但 `BeanModelBuilder.isGetter()` 只识别 `getXxx`/`isXxx` 前缀，accessor 不会被识别为 bean 属性。
- `BeanModelBuilder.buildFromClassModel()` 无法为 record 创建实例：无默认构造函数，也找不到 `@JsonCreator`。
- record 的 private final 字段被 `discoverDeclaredFields()` 跳过，但这不是问题——record 应该通过 accessor 方法暴露属性，不需要字段。

导致 record 类通过 `ReflectionManager` 获取到的 `IBeanModel` 无法实例化，也无可用属性。

## 三、核心设计

### 3.1 整体策略：仿照 AnnotationBeanModelBuilder

已有 `AnnotationBeanModelBuilder`（`ClassModel.getBeanModel()` 中路由）处理 annotation 类型：
- 不走通用 `BeanModelBuilder` 的 getter/setter 识别。
- 直接从方法中提取属性，用 `MethodPropertyGetter` 作为 getter。
- immutable，无 setter。

record 的处理方式完全相同：一个独立的 `RecordBeanModelBuilder`，在 `ClassModel.getBeanModel()` 中按 `isRecord()` 路由。

### 3.2 ClassModelBuilder：无需修改

record 的 accessor 方法是普通 public 方法，`discoverDeclaredMethods()` 已经自动收集。record 的 backing field 是 private final，不收集是正确行为——record 通过方法暴露属性。

### 3.3 ClassModel.getBeanModel() 路由策略

`getBeanModel()` 按类型三分发：`Annotation` → `Record` → Default。Record 分支判断 `getRawClass().isRecord()`，路由到 `RecordBeanModelBuilder`。

### 3.4 RecordBeanModelBuilder

参照 `AnnotationBeanModelBuilder` 的模式，核心逻辑：

1. **属性识别**：调用 `clazz.getRecordComponents()`，对每个 component 通过 `rc.getAccessor()` 确认 accessor 方法存在，再用 `IClassModel.getMethodByExactType(name, new Class[0])` 从 ClassModel 中获取对应的 `IFunctionModel`（精确匹配零参方法），用 `MethodPropertyGetter` 包装为 getter。无 setter。属性类型取自 `IFunctionModel.getReturnType()`（ClassModelBuilder 阶段已解析为 `IGenericType`），不从 `RecordComponent.getGenericType()` 重新解析。
2. **属性注解**：`BeanPropertyModel.getAnnotation()` 委托给 getter（`MethodPropertyGetter` → `IFunctionModel.getAnnotation()`）。主流注解（如 `@JsonProperty`、`@Description`）标注了 `@Target(METHOD)` 会自动传播到 accessor 方法，无需额外处理。仅标注 `@Target(RECORD_COMPONENT)` 的自定义注解不在 accessor 上，当前不覆盖，作为 Non-Goal。
3. **构造函数**：通过 `classModel.getConstructor(componentCount)` 选择 canonical constructor。`constructorPropNames` 从 `RecordComponent.getName()` 获取（不从 constructor 的 `getArgs().getName()` 获取——后者依赖 `-parameters` 编译选项）。用 `beanModel.setConstructorEx(new MethodBeanConstructor(canonicalConstructor))` 包装。使用 `setConstructorEx()` 而非 `setConstructor()`——`MethodBeanConstructor` 同时实现两个接口，slot 选错会导致无参 `newInstance()` 路径异常。
4. **immutable + DataBean**：`setImmutable(true)` + `setDataBean(true)`。record 是 data carrier，天然可安全序列化。

### 3.5 JSON 序列化/反序列化自动可用

record 的 DataBean 标记作用于两个独立机制，两处修改缺一不可：

| 机制 | 修改点 | 作用 |
|------|--------|------|
| **序列化白名单** | `RecordBeanModelBuilder.setDataBean(true)` → `BeanModel.isDataBean()` | `JsonWhitelistChecker.isAllowSerialize()` 放行 |
| **反序列化类型转换** | `ReflectionManager.getConverterForJavaType()` 增加 `type.isRecord()` 判断 | 走 `DataBeanTypeConverter` → `BeanCopier.buildObject()` → `newTarget()` |

两处就绪后，现有链路无需其他修改即可工作：

- **序列化**：`JsonSerializer.writeBean()` 遍历 `beanModel.getPropertyModels()`，调用 getter 取值 — record 的 `MethodPropertyGetter` 正常工作。
- **反序列化**：`newTarget()` 按 `constructorPropNames` 从 Map 取值，调用 `MethodBeanConstructor.newInstance(args)` → canonical constructor 创建实例。
- **类型安全**：如果 record 包含不可序列化的属性类型（如 `InputStream`），序列化时该类型的转换自然失败。

### 3.6 受影响的代码文件

| 文件 | 修改内容 |
|------|---------|
| 新增 `RecordBeanModelBuilder.java` | record 专用 BeanModel 构建器 |
| `ClassModel.java` | `getBeanModel()` 增加 `isRecord()` 路由分支 |
| `ReflectionManager.java` | `getConverterForJavaType()` 增加 `type.isRecord()` 判断，走 `DataBeanTypeConverter` |

不需要修改的部分：`ClassModelBuilder`、`MethodModelBuilder`、`BeanModelBuilder`、`IClassModel`、`IBeanModel`、`JsonSerializer`、`BeanCopier`。

## 四、拒绝了什么

### 4.1 修改 BeanModelBuilder.isGetter() 以支持无前缀方法

**方案**：在 `isGetter()` 中对 record 也接受无 `get`/`is` 前缀的零参方法。

**拒绝理由**：会导致用户自定义的非属性零参方法被误识别为属性。独立 `RecordBeanModelBuilder` 通过 `getRecordComponents()` 精确确定属性列表，语义清晰且不污染通用逻辑。

### 4.2 在 ClassModelBuilder 中增加 discoverRecordComponents()

**方案**：在 `discover()` 中检测 `isRecord()`，走独立的 record 发现路径。

**拒绝理由**：record 的 accessor 方法已经被 `discoverDeclaredMethods()` 自动发现，不需要额外处理。record 的 private final 字段不应暴露。ClassModelBuilder 无需感知 record 语义。

### 4.3 为 Record 新增独立的 IBeanModel 子接口

**拒绝理由**：过度设计。record 的行为通过已有 API 表达（immutable + DataBean + constructor + read-only props），消费者不需要区分 record 和普通 immutable bean。

## 五、与已有设计的关系

- 与 `AnnotationBeanModelBuilder` 采用相同的路由模式（在 `ClassModel.getBeanModel()` 中按类型分发）。
- `DataBeanTypeConverter` 已有全参构造 + 属性名映射的反序列化路径，标注了 `@DataBean` 的 record 自动复用此路径。
- record 隐式继承 `java.lang.Record`，`ClassModelBuilder.initSuper()` 处理其父类（`Record` extends `Object`），不会引入意外方法或属性。

## 六、测试策略

在已有的 `TestRecordReflection.java` 中增加测试：

1. **BeanModel 构建测试**：record 的 BeanModel 正确识别属性（名称、类型、只读）。
2. **实例化测试**：通过 canonical constructor 创建 record 实例。
3. **属性读写测试**：getter 正常工作，setter 不存在。
4. **dataBean 序列化放行测试**：record 的 `BeanModel.isDataBean()` 返回 true，在 `onlyForDataBean=true` 模式下可正常序列化。
5. **JSON 序列化/反序列化测试**：record 与 `JsonTool` 配合工作（序列化 + 反序列化完整链路）。
6. **带注解的 record 测试**：record component 上的 `@JsonProperty`、`@Description` 等注解正确传播。
7. **泛型 record 测试**：`record Pair<A, B>(A first, B second)` 的类型参数正确解析。
8. **边界情况测试**：零 component 的 record（`record Empty() {}`）正常实例化。
9. **紧凑构造函数测试**：带验证逻辑的紧凑构造函数能正确实例化。

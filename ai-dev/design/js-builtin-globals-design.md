# XScript JS 全局对象/构造器兼容设计

**日期**：2026-09-03
**范围**：`nop-kernel/nop-xlang` + `nop-kernel/nop-core`（`EvalGlobalRegistry`、`CoreConstants`）
**状态**：草案（用户 2026-09-03 调研后形成，需进一步细化映射表）

---

## 一、问题定位

XScript 当前已注册的内置项：

| 类别 | 名称 | 后端实现 | 来源 |
|------|------|----------|------|
| 全局变量 | `$scope`, `$context`, `$out`, `$beanProvider`, `$evalRt`, `$config`, `$genJs`, `$genJava`, `$JSON`, `$Math`, `$Date`, `$String` | `StaticClassGlobalVariableDefinition`（指向静态类） | `EvalGlobalRegistry` |
| 全局变量 | `_` | Underscore 静态类 | `EvalGlobalRegistry` |
| 全局函数 | `now()`, `today()`, `currentDateTime()`, `optional()`, `inject()`, `IF/AND/OR/SWITCH`, `get()`, `getByPropPath()`, `assign()` | 静态方法 | `GlobalFunctions` |
| 全局函数 | debug 类（`debug()`, `log()`, `warn()` 等） | 静态方法 | `DebugHelper` / `LogFunctions` |
| 类型别名 | `Error` → `io.nop.api.core.exceptions.NopScriptError`（fallback，未 import 时） | LexicalScope 别名 | `LexicalScopeAnalysis.resolveType` |

**未覆盖**：

| JS 内置 | XScript 现状 | 是否需要覆盖 |
|---------|-------------|-------------|
| `new Date()` | 必须 `import java.util.Date;` 后 `new Date()` | **应该**（JS 风格短名） |
| `new Array()` / `new Array(n)` / `new Array(a,b,c)` | 必须 `import java.util.ArrayList;` 后 `new ArrayList()` | **应该**（JS 风格短名） |
| `new Map()` | 必须 `import java.util.LinkedHashMap;` 后 `new LinkedHashMap()` | **应该**（JS 风格短名） |
| `new Set()` | 必须 `import java.util.LinkedHashSet;` 后 `new LinkedHashSet()` | **应该**（JS 风格短名） |
| `new RegExp(pattern, flags)` | 必须 `import java.util.regex.Pattern;` | 可选（XLang 业务场景少见） |
| `new WeakMap()` | 必须 `import java.util.WeakHashMap;` | 可选 |
| `new WeakSet()` | Java 无直接对应（`WeakHashMap` 替代） | 可选 |
| `new Promise(executor)` | 无对应（XScript 同步求值） | **不**（破坏同步假设） |
| `new Function(args, body)` | 无对应（不安全） | **不**（安全原因） |
| `new Number(...)` | 必须 `import java.lang.Integer/Long/Double;` | 可选（与 `MathHelper` 常量重叠） |
| `new String(...)` | 必须 `import java.lang.String;` | 可选（极少使用） |
| `new Boolean(...)` | 必须 `import java.lang.Boolean;` | 可选（XScript 自动装箱） |
| `Math.abs/min/max/...` | 必须 `$Math.abs()` 形式 | **应该**（裸名更符合 JS 直觉） |
| `Object.keys/values/assign/...` | 必须 `import java.util.Objects;` 或自实现 | **应该**（高频） |
| `Array.isArray/from/of` | 必须自实现 | **应该**（与 ArrayList 集成） |
| `JSON.parse/stringify` | 必须 `$JSON.parse()` 形式 | **部分**（JS 直觉是裸名） |
| `Number.parseInt/parseFloat` | 必须 `import java.lang.Integer;` | **应该**（高频） |
| `String.fromCharCode` | 必须 `import java.lang.String;` | 可选 |
| `Date.now/parse/UTC` | 必须 `$Date.now()` 形式 | **部分**（JS 直觉是裸名） |

---

## 二、设计结论

### 2.0 关键发现：扩展方法已通过 `ReflectionManager.registerHelperMethods` 注册

调研发现以下扩展方法**已经生效**，XScript 中可直接使用 JS 风格调用：

| 扩展类 | 注册的目标类型 | 已支持的 JS 风格 API |
|--------|--------------|-------------------|
| `io.nop.commons.collections.ListFunctions` | `java.util.List` | `push/pop/shift/unshift/slice/splice/reverse/reduceRight` 等 |
| `io.nop.commons.collections.SetFunctions` | `java.util.Set` / `Collection` | `includes/some/every/filter/map/flatMap/join/reduce/find/findIndex/findLastIndex` 等 |
| `io.nop.commons.collections.MapFunctions` | `java.util.Map` | `set/has/delete/keys/entries` 等 |
| `io.nop.commons.util.StringHelper` | `java.lang.String` | `$` 前缀（仅注册前缀） |
| `io.nop.commons.util.DateHelper` | `LocalDate` 等 | `$` 前缀（仅注册前缀） |

注册机制：`ReflectionManager.registerHelperMethods(clazz, helperClass, null)` 自动将 helperClass 中**第一个参数类型匹配 clazz 的所有静态方法**注册为 clazz 的扩展方法（基于 Java方法类反射分派）。

```java
// ReflectionManager.java:90-95
_instance.registerHelperMethods(List.class, ListFunctions.class, null);
_instance.registerHelperMethods(Set.class, SetFunctions.class, null);
_instance.registerHelperMethods(Map.class, MapFunctions.class, null);
_instance.registerHelperMethods(Collection.class, SetFunctions.class, null);
_instance.registerHelperMethods(String.class, StringHelper.class, "$");
_instance.registerHelperMethods(LocalDate.class, DateHelper.class, "$");
```

**这意味着**：

| JS 写法 | XScript 现状 |
|---------|------------|
| `arr.push(x)` | ✅ 直接可用（ListFunctions.push） |
| `arr.pop()` | ✅ 直接可用（ListFunctions.pop） |
| `arr.includes(x)` | ✅ 直接可用（SetFunctions.includes，虽然 SetFunctions 名字暗示是 Set 但实际扩展了 Collection） |
| `m.set(k, v)` | ✅ 直接可用（MapFunctions.set） |
| `m.has(k)` | ✅ 直接可用（MapFunctions.has） |
| `s.add(x)` | ✅ 直接可用（Set 直接有 add） |
| `s.has(x)` | ✅ 直接可用（SetFunctions.has 不存在但 Set.contains 可用） |

**结论**：本设计中 §8.2/§8.3/§8.4 描述的"不兼容 API"大多数**已经通过扩展方法解决**。设计文档需修订：不再需要新增 `ArrayHelper` / `MapHelper` / `SetHelper` 类。

### 2.1 三层命名空间策略

XScript 中 JS 全局对象/方法按以下三层暴露：

1. **L1 裸名调用**：`Date.now()`、`Math.abs(x)`、`Object.keys(o)`、`JSON.parse(s)`、`Array.isArray(v)`
   - 通过 `EvalGlobalRegistry.registerFunction(name, fn)` 注册为全局函数
   - 直接调用，最符合 JS 直觉
2. **L2 短类名 `new`**：`new Date()`、`new Map()`、`new Set()`、`new Array()`
   - 通过 `LexicalScopeAnalysis.resolveType` 类型名别名机制（与 `Error` 相同模式）
   - 未 import 时 fallback 到对应的 Java 类
3. **L3 完全限定名**：`new java.util.Date()`、`new java.util.LinkedHashMap()`
   - 现有机制，保留作为底层

### 2.2 已实施（commit `a683229725` 残留）— 但 `Error` 别名因 plan 2259 落地

- `Error` → `NopScriptError`（LexicalScope 类型别名，未 import 时 fallback）

### 2.3 建议实施的类型别名（LexicalScope fallback）

| JS 短名 | Java 映射类 | 理由 |
|---------|------------|------|
| `Date` | `java.util.Date` | JS 标准构造器 |
| `Array` | `java.util.ArrayList` | JS 动态数组；ArrayList 提供 `size()`/`get(i)`/`add()`/`remove(i)` |
| `Map` | `java.util.LinkedHashMap` | 保序 |
| `Set` | `java.util.LinkedHashSet` | 保序 |
| `RegExp` | `java.util.regex.Pattern` | XLang 业务场景少见，仅在字符串匹配时使用 |
| `WeakMap` | `java.util.WeakHashMap` | 可选；XScript 业务少见 |
| `Number` | `java.lang.Long`（默认）或包装为 `MathHelper.NUMBER_TYPE` | XScript 数字字面量已自动装箱 |
| `String` | `java.lang.String` | 极少 `new String('x')` 用法 |
| `Boolean` | `java.lang.Boolean` | XScript 已自动装箱 |

**实现位置**：`LexicalScopeAnalysis.resolveType` 在 `else` 分支（找不到 import 时）增加 fallback 映射。

**特殊考虑**：
- 用户显式 `import java.util.Date;` 后 `new Date()` 使用 `java.util.Date`（优先于 fallback）。
- 现有 `Error` fallback 模式可复用：if `"Error".equals(typeName) → load NopScriptError`。

### 2.4 建议实施的全局函数（L1）

#### 2.4.1 Math（覆盖范围最小的最高优先级）

JS `Math` 的静态方法全集：

| JS API | XScript 实现 |
|--------|------------|
| `Math.abs(x)` | 直接调用 `Math.abs()`（Java Math）；提供 Long/Double 重载 |
| `Math.ceil(x)` | `Math.ceil()` |
| `Math.floor(x)` | `Math.floor()` |
| `Math.round(x)` | `Math.round()` |
| `Math.trunc(x)` | `MathHelper.trunc()`（新增） |
| `Math.max(a,b,...)` / `Math.min(a,b,...)` | `MathHelper.max/min`（新增变长参数支持） |
| `Math.pow(x, y)` | `Math.pow()` |
| `Math.sqrt(x)` | `Math.sqrt()` |
| `Math.cbrt(x)` | `Math.cbrt()`（Java 8+） |
| `Math.log(x)` / `Math.log2(x)` / `Math.log10(x)` | `Math.log/log2/log10()` |
| `Math.exp(x)` | `Math.exp()` |
| `Math.sin/cos/tan(x)` | `Math.sin/cos/tan()` |
| `Math.asin/acos/atan(x)` / `Math.atan2(y, x)` | `Math.asin/acos/atan/atan2()` |
| `Math.sinh/cosh/tanh(x)` | `Math.sinh/cosh/tanh()` |
| `Math.sign(x)` | `Math.signum()` |
| `Math.random()` | `MathHelper.random()`（已有 IRandom 实现） |
| `Math.PI / Math.E` 等常量 | `MathHelper.PI / E`（新增常量） |

#### 2.4.2 Object（高频工具方法）

| JS API | XScript 实现 |
|--------|------------|
| `Object.keys(o)` | `CollectionHelper.toStringList(beanModel.getPropertyNames(o))` |
| `Object.values(o)` | 同上，map 到值 |
| `Object.entries(o)` | List<Map.Entry> |
| `Object.assign(target, src)` | 浅合并 |
| `Object.freeze(o)` | DynamicObject.freeze() |
| `Object.isFrozen(o)` | DynamicObject.isFrozen() |
| `Object.create(proto)` | 创建 DynamicObject |
| `Object.getPrototypeOf(o)` | `getClass()` 返回 |
| `Object.hasOwn(o, key)` | `beanModel.hasProperty(key)` |
| `Object.isEmpty(o)` | `CollectionHelper.isEmptyMap(o)` |

#### 2.4.3 Array（静态方法）

| JS API | XScript 实现 |
|--------|------------|
| `Array.isArray(v)` | `v instanceof List` 或 `CollectionHelper.isCollection(v)` |
| `Array.from(iterable)` | `CollectionHelper.toList(iterable)` |
| `Array.of(...items)` | `Arrays.asList(items)` |
| `Array.isEmpty(arr)` | `CollectionHelper.isEmpty(arr)` |

#### 2.4.4 JSON（部分覆盖）

JS `JSON` 静态方法：

| JS API | XScript 实现 | 命名 |
|--------|------------|------|
| `JSON.parse(s)` | `$JSON.parse(s)` 或裸 `JSON.parse(s)` | 两者并存 |
| `JSON.stringify(o)` | `$JSON.stringify(o)` 或裸 `JSON.stringify(o)` | 两者并存 |
| `JSON.stringify(o, replacer, space)` | `$JSON.stringify(o, replacer, space)` | 已有 |

**命名建议**：
- `$JSON` 保留（与 `$scope`、`$Date` 等命名风格一致）
- 同步注册 `JSON.parse / JSON.stringify` 裸名别名（委托 `$JSON`），便于 JS 直觉

#### 2.4.5 Number（parseInt / parseFloat）

| JS API | XScript 实现 |
|--------|------------|
| `Number.parseInt(s, radix)` | `Integer.parseInt(s, radix)` |
| `Number.parseFloat(s)` | `Float.parseFloat(s)` |
| `Number.isNaN(v)` | `v instanceof Double && Double.isNaN((Double) v)` 或 `NumberUtils.isNaN(v)` |
| `Number.isFinite(v)` | `NumberUtils.isFinite(v)` |
| `Number.isInteger(v)` | `v instanceof Long \|\| v instanceof Integer` |
| `Number.MAX_SAFE_INTEGER / MIN_SAFE_INTEGER` | `MathHelper.MAX_JS_LONG / -MAX_JS_LONG`（已有） |

#### 2.4.6 String（fromCharCode）

| JS API | XScript 实现 |
|--------|------------|
| `String.fromCharCode(...codes)` | `new String(new char[]{...})` |
| 其他 `String.prototype.*`（方法） | XScript 字符串字面量已自动暴露 `length`/`charAt`/`indexOf` 等 |

#### 2.4.7 Date（静态）

| JS API | XScript 实现 |
|--------|------------|
| `Date.now()` | `System.currentTimeMillis()` 或 `now()` 已有 |
| `Date.parse(s)` | `$Date.parse(s)` |
| `Date.UTC(...)` | `$Date.UTC(...)` |

#### 2.4.8 RegExp（test/match）

XScript 字符串处理建议不直接暴露 `RegExp.prototype.exec/match`，而是通过 `String.matches(regex)` 实现。

### 2.5 不实施的 JS API

| API | 不实施理由 |
|-----|----------|
| `new Promise(executor)` | XScript 同步求值模型不兼容 Promise 异步链 |
| `new Function(args, body)` | eval 类似风险，XLang 设计不允许动态函数构造 |
| `Symbol()` | Java 无对应类型；XScript 不需要 |
| `Proxy` / `Reflect` | 高级元编程，XLang 业务不需要 |
| `WeakRef` / `FinalizationRegistry` | ES2021/2022 GC 钩子，XLang 不暴露 |
| `Atomics` / `SharedArrayBuffer` | 多线程共享，XScript 单线程求值 |
| `eval(string)` | 安全风险 |

### 2.6 类型推断与重载

- `Math.abs(x)` 需要支持 Long/Integer/Double/Float 多类型重载——通过 `XLangCompileScope.registerFunction(name, FunctionModel)` 支持多函数模型（按参数类型选择）。
- `Object.keys(o)` 接收 Map / Bean / DynamicObject，返回 `List<String>`。

### 2.7 安全考虑

- `new Function` / `eval` 明确**禁用**
- `Object.create(null)` 允许（创建无原型对象）
- `JSON.parse` 限制输入大小（防 DoS）：`ApiConfigs.CFG_MAX_JSON_PARSE_SIZE`

---

## 三、与现有架构的对接

### 3.1 注册入口

```java
// XLangCoreInitializer.java
public void initialize() {
    // 已有：GlobalFunctions / DebugHelper / LogFunctions
    
    // 新增：JS Globals 注册
    cleanup.append(EvalGlobalRegistry.instance().registerStaticFunctions(MathJS.class));
    cleanup.append(EvalGlobalRegistry.instance().registerStaticFunctions(ObjectJS.class));
    cleanup.append(EvalGlobalRegistry.instance().registerStaticFunctions(ArrayJS.class));
    cleanup.append(EvalGlobalRegistry.instance().registerStaticFunctions(NumberJS.class));
    cleanup.append(EvalGlobalRegistry.instance().registerStaticFunctions(DateJS.class));
    cleanup.append(EvalGlobalRegistry.instance().registerStaticFunctions(StringJS.class));
}
```

### 3.2 类型别名

```java
// LexicalScopeAnalysis.resolveType 已有 fallback 模式（Error → NopScriptError）
// 扩展为：
} else if ("Date".equals(typeName)) {
    typeName = "java.util.Date";
    node.setClassModel(scope.getClassModelLoader().loadClassModel(typeName));
} else if ("Array".equals(typeName)) {
    typeName = "java.util.ArrayList";
    node.setClassModel(scope.getClassModelLoader().loadClassModel(typeName));
} else if ("Map".equals(typeName)) {
    typeName = "java.util.LinkedHashMap";
    ...
}
```

或更通用的 Map<String, String> 映射表（避免一连串 if-else）：

```java
private static final Map<String, String> JS_TYPE_ALIAS = Map.of(
    "Date", "java.util.Date",
    "Array", "java.util.ArrayList",
    "Map", "java.util.LinkedHashMap",
    "Set", "java.util.LinkedHashSet",
    "RegExp", "java.util.regex.Pattern",
    "Error", "io.nop.api.core.exceptions.NopScriptError"
);
```

### 3.3 测试覆盖

- 每个 JS 全局函数添加 `*.test.md` 语料（与 `try-catch.test.md` / `delete.test.md` 同格式）
- 每个类型别名添加 `new Xxx()` 创建 + `instanceof` 验证
- 覆盖 `Error → NopScriptError` fallback（已有）

### 3.4 文档同步

- `docs-for-ai/02-core-guides/xlang-and-xpl-basics.md`：
  - 在 `xbiz action source 内置变量` 表格中追加 `$Math.abs` / `$Math.max` 等高频项
  - 新增"JS 兼容全局对象"段落，汇总可用的类型别名与全局函数
  - 明确 `new Date()` / `new Array()` 等 JS 风格写法的支持矩阵

---

## 四、被拒绝的方案

### 4.1 拒绝：自动 import 所有 JDK 类

**理由**：破坏现有 import 语义（import 应该是显式选择），且容易与 XLang 业务命名冲突（如 `Date` 可能是业务类名）。

### 4.2 拒绝：在 LexicalScope 中把 `Date` 替换为 `java.util.Date`

**理由**（plan 2259 反馈）：用户明确希望 AST 中 `TypeName` 保持 `Date`，仅 `classModel` 解析时 fallback。这样错误信息、调试输出仍显示用户原始写的 `Date`。

### 4.3 拒绝：完全模拟 JS 语义（如 `new Array(n)` 创建定长稀疏数组）

**理由**：Java `ArrayList` 语义不同（动态数组）。XScript `new Array(5)` 映射为 `new ArrayList<>()` 而非 `Arrays.asList(new Object[5])`。文档需明确差异。

### 4.4 拒绝：支持 `Promise` 异步链

**理由**：XScript 同步求值模型。`async/await` 是更大的重构，独立 plan。

---

## 五、阶段拆分（建议，修订后）

### Phase 1 - 类型别名（最小侵入）

- `LexicalScopeAnalysis.resolveType` 增加 `Map<String, String> JS_TYPE_ALIAS` 映射（Date/Array/Map/Set/RegExp）
- 测试：`new Date()`、`new Map()`、`new Set()`、`new Array()` 创建成功
- 风险：低
- 工时：~1h

### Phase 2 - SetFunctions.delete 补漏（最小改动）

- 在 `io.nop.commons.collections.SetFunctions` 新增 `delete(Collection, Object)` 静态方法
- 注册到 `Set` 类型的 helper methods
- 测试：`s.delete(x)` 可用
- 风险：极低
- 工时：~15min

### Phase 3 - DateJS（命名差异最大，必须新增）

- 新增 `io.nop.api.core.utils.DateJS` 静态类（12+ 方法）
- 注册到 `Date` / `LocalDate` / `LocalDateTime` 类型的 helper methods
- 测试：`getFullYear/getMonth/getDate/toISOString/fromYMD/parse` 等
- 风险：中（Calendar 用法）
- 工时：~3h

### Phase 4 - Math / Number 静态方法

- 注册 `MathJS` / `NumberJS` 到 `EvalGlobalRegistry`
- 测试：`Math.abs/min/max/round/floor/ceil`、`Number.parseInt/parseFloat/isNaN/isInteger`
- 风险：低（重载冲突需小心）
- 工时：~3h

### Phase 5 - Object 静态方法

- 注册 `ObjectJS`
- 测试：`Object.keys/values/assign`、`isEmpty/hasOwn`
- 风险：中（Object.keys 需要处理 Map / Bean / DynamicObject 三种输入类型）
- 工时：~3h

### Phase 6 - JSON / Date / String 裸名别名

- 注册 `JSON.parse/stringify` 裸名（委托 `$JSON`）
- 注册 `Date.now/parse/UTC` 裸名（委托 `$Date`）
- 注册 `String.fromCharCode`（可选）
- 风险：低
- 工时：~1h

### Phase 7 - RegExp / String.prototype.*（可选）

- `RegExp` 类型别名 + `Pattern.matches` 集成
- `String` 实例方法（substr/indexOf/replace 等已部分支持）
- 风险：低
- 工时：~2h

**修订后总工时**：约 13h（vs 之前估算 7d）。Phase 1+2 简单快速（1.5h），可立即推进。

---

## 六、与已有设计的关系

- `ai-dev/design/xlang-delete-statement-design.md`：本设计文档的姊妹——同属 JS 兼容性扩展。`Error` 别名实现方式可参考。
- `ai-dev/design/xlang-scope-access-design.md`：`$scope` 命名风格延续。
- `ai-dev/plans/2259-xscript-delete-expression.md`：已完成 `delete` 表达式；本设计扩展 JS 全局对象覆盖。
- `docs-for-ai/02-core-guides/xlang-and-xpl-basics.md`：本设计落地后需更新 owner doc。

---

## 七、后续工作（不在本设计范围）

- `async/await` 异步脚本（独立 plan）
- JS `Proxy` / `Reflect` 元编程支持（XLang 业务不需要）
- Node.js `Buffer` / `process` 等服务器端 API（XLang 客户端为主）

---

## 八、容器类型兼容矩阵（详细）—— 含扩展方法覆盖状态

### 8.1 Date / java.util.Date

| JS Date API | Java 对应 | 兼容性 | XScript 适配方案 |
|------------|----------|--------|-----------------|
| `new Date()` | `new Date()` | ✅ | 类型别名（Phase 1） |
| `new Date(ms)` | `new Date(ms)` | ✅ | 类型别名 |
| `new Date(year, month, day, ...)` | `Calendar.set(...)` + `getTime()` | ❌ | `DateJS.fromYMD(y, m, d, ...)`（新增） |
| `new Date(str)` | `DateFormat.parse(str)` | ❌ | `DateJS.parse(str)`（新增） |
| `getFullYear()` | `Calendar.get(YEAR)` | ❌ | `DateJS.getFullYear(date)`（新增） |
| `getMonth()` (0-11) | `Calendar.get(MONTH)` (0-11) | ✅ | `DateJS.getMonth(date)`（新增） |
| `getDate()` (1-31) | `Calendar.get(DAY_OF_MONTH)` | ⚠️ | `DateJS.getDate(date)`（新增） |
| `getDay()` (0-6) | `Calendar.get(DAY_OF_WEEK)` | ⚠️ | `DateJS.getDay(date)`（新增） |
| `getHours/Minutes/Seconds` | `Calendar.get(HOUR/MINUTE/SECOND)` | ❌ | `DateJS.getXxx(date)`（新增） |
| `getTime()` | `getTime()` | ✅ | 直接访问 |
| `toISOString()` | `Instant.toString()` | ❌ | `DateJS.toISOString(date)`（新增） |
| `Date.now()` | `System.currentTimeMillis()` | ✅ | `$Date.now()`（已注册） |
| `Date.parse(str)` | `DateFormat.parse(str)` | ❌ | `DateJS.parse(str)`（新增） |

### 8.2 Array / ArrayList（已通过 ListFunctions 扩展方法解决大部分）

| JS Array API | ArrayList 对应 | 兼容性 | XScript 适配方案 |
|------------|--------------|--------|-----------------|
| `new Array()` | `new ArrayList<>()` | ✅ | 类型别名（Phase 1） |
| `new Array(n)` | `new ArrayList<>()`（不预填 null） | ❌ 语义不同 | 类型别名 + 文档说明 |
| `new Array(a,b,c)` | `Arrays.asList(a,b,c)` | ❌ | 类型别名（行为：可变但固定容量） |
| `[a,b,c]` 字面量 | 已直接生成 ArrayList | ✅ | 现有机制 |
| `arr[i]` | `arr.get(i)` | ❌ | 编译期重写或 `arr.get(i)` |
| `arr.length` | `arr.size()` | ❌ | `arr.size()` |
| **`arr.push(x)`** | **`arr.add(x)`** | **✅ 已通过 ListFunctions.push 扩展** | **直接可用** |
| **`arr.pop()`** | **`arr.remove(arr.size()-1)`** | **✅ 已通过 ListFunctions.pop** | **直接可用** |
| **`arr.shift()`** | **`arr.remove(0)`** | **✅ 已通过 ListFunctions.shift** | **直接可用** |
| **`arr.unshift(x)`** | **`arr.add(0, x)`** | **✅ 已通过 ListFunctions.unshift** | **直接可用** |
| `arr.indexOf(x)` | `arr.indexOf(x)` | ✅ | 直接调用 |
| **`arr.includes(x)`** | **`arr.contains(x)`** | **✅ 已通过 SetFunctions.includes** | **直接可用** |
| **`arr.slice(s,e)`** | **`arr.subList(s,e)`** | **✅ 已通过 ListFunctions.slice** | **直接可用** |
| **`arr.splice(...)`** | **手写** | **✅ 已通过 ListFunctions.splice** | **直接可用** |
| **`arr.concat(b)`** | **`addAll(b)`** | **✅ 已通过 SetFunctions.concat** | **直接可用** |
| **`arr.map(fn)`** | **`stream().map(fn).collect(...)`** | **✅ 已通过 SetFunctions.map** | **直接可用** |
| **`arr.filter(fn)`** | **`stream().filter(fn).collect(...)`** | **✅ 已通过 SetFunctions.filter** | **直接可用** |
| **`arr.reduce(fn, init)`** | **`stream().reduce(init, fn)`** | **✅ 已通过 SetFunctions.reduce** | **直接可用** |
| **`arr.find(fn)`** | **遍历查找** | **✅ 已通过 SetFunctions.find** | **直接可用** |
| **`arr.some/every(fn)`** | **判断** | **✅ 已通过 SetFunctions.some/every** | **直接可用** |
| **`arr.reverse()`** | **`Collections.reverse(arr)`** | **✅ 已通过 ListFunctions.reverse** | **直接可用** |
| **`arr.join(sep)`** | **`String.join(sep, arr)`** | **✅ 已通过 SetFunctions.join** | **直接可用** |
| `arr.sort(fn)` | `Collections.sort(arr, cmp)` | ❌ | ListFunctions.sort 已存在但 comparator 参数化复杂 |
| `arr.fill(v)` | `Collections.fill(arr, v)` | ❌ | 新增（可选） |
| `Array.isArray(v)` | `v instanceof List` | ✅ | ArrayJS.isArray（新增） |
| `Array.from(iter)` | `CollectionHelper.toList(iter)` | ⚠️ | SetFunctions.concat 已类似实现 |
| `Array.of(...items)` | `Arrays.asList(items)` | ⚠️ | 新增（可选） |

### 8.3 Map / LinkedHashMap（已通过 MapFunctions 扩展方法解决大部分）

| JS Map API | LinkedHashMap 对应 | 兼容性 | XScript 适配方案 |
|----------|----------------|--------|-----------------|
| `new Map()` | `new LinkedHashMap<>()` | ✅ | 类型别名（Phase 1） |
| `new Map(iter)` | 手动转换 | ⚠️ | 类型别名 + MapFunctions.entries 解析 |
| **`m.set(k,v)`** | **`m.put(k,v)`** | **✅ 已通过 MapFunctions.set** | **直接可用** |
| `m.get(k)` | `m.get(k)` | ✅ | 直接 |
| **`m.has(k)`** | **`m.containsKey(k)`** | **✅ 已通过 MapFunctions.has** | **直接可用** |
| **`m.delete(k)`** | **`m.remove(k)`** | **✅ 已通过 MapFunctions.delete** | **直接可用** |
| `m.size` | `m.size()` | ❌ | `m.size()` |
| `m.clear()` | `m.clear()` | ✅ | 直接 |
| **`m.keys()`** | **`m.keySet()`** | **✅ 已通过 MapFunctions.keys** | **直接可用** |
| `m.values()` | `m.values()` | ✅ | 直接 |
| **`m.entries()`** | **`m.entrySet()`** | **✅ 已通过 MapFunctions.entries** | **直接可用** |
| `m.forEach(fn)` | `m.forEach((BiConsumer))` | ❌ | 新增（可选） |

### 8.4 Set / LinkedHashSet

| JS Set API | LinkedHashSet 对应 | 兼容性 | XScript 适配方案 |
|----------|-----------------|--------|-----------------|
| `new Set()` | `new LinkedHashSet<>()` | ✅ | 类型别名（Phase 1） |
| `new Set(iter)` | 手动转换 | ⚠️ | 类型别名 + SetFunctions.concat 解析 |
| `s.add(v)` | `s.add(v)` | ✅ | 直接 |
| **`s.has(v)`** | **`s.contains(v)`** | **⚠️ SetFunctions.includes 已扩展到 Collection** | **`s.includes(v)`（JS 名）** |
| **`s.delete(v)`** | **`s.remove(v)`** | **❌ 待实现** | **新增 SetFunctions.delete** |
| `s.size` | `s.size()` | ❌ | `s.size()` |
| `s.clear()` | `s.clear()` | ✅ | 直接 |
| `s.values()` | `s.iterator()` | ⚠️ | SetFunctions 中是否有 values？ |
| `s.union(b)` / `s.intersection(b)` | 无 | ❌ | 新增（可选） |

> 注：SetFunctions 当前扩展的是 `Set` 和 `Collection`，但**没有 `delete` 方法**——这是个真实 gap。`s.delete(v)` 在 XScript 会失败。

### 8.5 总体兼容性统计（修订后）

| 容器 | 完全兼容 API | 已通过扩展方法支持 | 仍需适配 |
|------|------------|----------------|----------|
| Date | `getTime`, `new Date()`, `new Date(ms)`, `Date.now()` | 0 | 12+ 方法（命名差异） |
| Array | `indexOf`, `[..]`, `new Array()`, `push/pop/shift/unshift/slice/splice/reverse/concat/map/filter/reduce/find/some/every/includes/join` | **17+** | sort/fill（可选） |
| Map | `get`, `clear`, `values`, `set/has/delete/keys/entries` | **7+** | forEach（可选） |
| Set | `add`, `clear`, `includes(Collection.includes)` | 1 | **delete/values/union/intersection** |

**重大修订结论**：

| 之前 | 现在 |
|------|------|
| "需要新增 ArrayHelper/MapHelper/SetHelper 共 40+ 静态方法" | **大部分已通过 ListFunctions/SetFunctions/MapFunctions 实现**，设计文档需修正 |
| "新增 4 个适配静态类" | 仅 DateJS 需要新增（12 方法）；SetFunctions.delete 待补 |

### 8.6 设计修正

| 调整项 | 调整前 | 调整后 |
|--------|------|------|
| ArrayHelper | 新增 17+ 方法 | **取消**（ListFunctions 已覆盖） |
| MapHelper | 新增 7+ 方法 | **取消**（MapFunctions 已覆盖） |
| SetHelper | 新增 6+ 方法 | **保留 SetFunctions.delete 一个方法** |
| DateJS | 新增 12 方法 | **保留**（命名差异较大） |

**仍需新增**：
1. ~~`SetFunctions.delete`（一个方法）~~ **已实施（2026-09-03）**：Java 中 `delete` 是关键字无法定义方法名，通过 `@Name("delete")` 注解解决——`MethodModelBuilder.getName` 读取 `@Name` 注解，XLang 反射方法名时使用注解中的名称。实际方法名 `remove`，注解名 `delete`。
2. `DateJS` 类（12+ 方法，覆盖 getFullYear/getMonth/getDate 等命名差异）—— **已实施为 JsDate**（见 §8.7）

### 8.7 JsDate 实施记录（2026-09-03）

- **类名**：`JsDate`（与 `nop-js` 模块 `JsErrors`/`JsConstants` 命名一致）
- **位置**：`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/utils/JsDate.java`（与 `XLangHelper`/`EvalHelper` 同包）
- **继承**：`JsDate extends java.util.Date`（保留 JDK 互操作，`instanceof Date` 为 true）
- **类型别名**：`LexicalScopeAnalysis.resolveType` 增加 `Date` fallback（仅未 import 时解析到 JsDate，AST 中 typeName 保持 "Date" 不变）
- **关键设计决策**（用户 2026-09-03 裁定）：
  - **1 参构造器必须唯一**：`JsDate(long)` 与 `JsDate(String)` 是两个 1 参构造器，导致 `ClassModel.getUniqueMethod(1)` 找不到唯一方法（XLang 反射按参数数量选方法）。
  - **方案**：合并为单一 `JsDate(Object)` 构造器，运行时判断参数类型（Number → 毫秒；Date → 复制；String → parse）。`getUniqueMethod(1)` 返回唯一 `JsDate(Object)`，反射调用成功。
- **已支持方法**：`getFullYear/setFullYear/getMonth/getDate/getDay/getHours/getMinutes/getSeconds/getMilliseconds/setMilliseconds/getTimezoneOffset/toISOString/toJSON/toGMTString/toUTCString/getTime/setTime/getYear/setYear` + 静态 `now()/parse()/UTC()`。
- **测试**：try-catch.test.md #22-#25（JsDate 无 import fallback / new Date(0L) 构造 / Date.now 静态 / 显式 import 优先）。
- **注意（已复核 2026-09-03，问题仍存在）**：显式 `import java.util.Date` 后 `new Date(0L)` 仍失败。根因：
  1. **多 1 参构造器冲突**：JDK `Date(long)` 与 `Date(String)` 两个 1 参构造器，`MethodModelCollection.addMethod` 在遇到第二个同 argCount 构造器时把 `methodByArgCounts[1]` 置 null → `getUniqueMethod(1)` 返回 null。
  2. **类型不匹配**：`getMethodForArgValues` 用 `IGenericType.isAssignableFrom`（=`getRawClass().isAssignableFrom(clazz)`）判断，`long.class.isAssignableFrom(Long.class)` 为 false，基本类型不接受包装类型。
  - **绕过方式**：用 `new Date()`（0 参）、或走 JsDate fallback（`JsDate(Object)` 唯一 1 参构造器）、或 `new java.util.Date(1000)` 显式全限定名 + 框架支持后。
  - 属于 XLang 反射机制对 JDK 多构造器类的固有局限，非 JsDate 问题。

### 8.8 JS 容器类型别名实施记录（2026-09-03）

- **类型别名扩展**：`LexicalScopeAnalysis.resolveType` 增加 `Array → java.util.ArrayList`、`Map → java.util.LinkedHashMap`、`Set → java.util.LinkedHashSet`（与 `Error → NopScriptError`、`Date → JsDate` 同一 fallback 机制，未 import 时触发，AST typeName 保持不变）。
- **SetFunctions.delete 通过 @Name 注解实现**：
  - Java 中 `delete` 是关键字，无法定义 `public static boolean delete(...)` 方法。
  - 使用 `@Name("delete")` 注解（`io.nop.api.core.annotations.core.Name`）标注方法 `remove`：`MethodModelBuilder.getName` 读取注解，XLang 反射方法名时使用注解中的名称 `delete`。
  - ```java
    @Name("delete")
    public static <T> boolean remove(Collection<T> list, T item) {
        return list.remove(item);
    }
    ```
- **已验证**（js-collections.test.md，3 用例）：
  ```js
  let arr = new Array();        // ArrayList，arr.push(3) 可用
  let m = new Map();            // LinkedHashMap，m.set/has/delete 可用
  let s = new Set();            // LinkedHashSet，s.add/includes/delete/size 可用
  ```
- **@Name 注解的既有用途**：`Name.java` 文档注释明确说明"catch 和 finally 是 javascript 中合法的方法名，但是在 java 中是关键字，无法被使用。通过增加这个注解，可以使得脚本中的方法名与 javascript 保持一致"——与 delete 场景完全一致。
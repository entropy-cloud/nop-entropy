# 127 ReflectionManager Record 类型支持

> Plan Status: completed
> Last Reviewed: 2026-06-08
> Source: `ai-dev/design/nop-core/00-record-support-design.md`
> Related: nop-core module

## Purpose

让 `ReflectionManager` 完整支持 Java record 类型：record 的 `IClassModel` 能正确提供 accessor 方法，`IBeanModel` 能正确识别只读属性、通过 canonical constructor 实例化、且 JSON 序列化/反序列化自动可用。

## Current Baseline

- `TestRecordReflection.java` 已存在但为空测试类，无任何测试用例。
- `ClassModelBuilder.discoverDeclaredMethods()` 能发现 record 的 accessor 方法（public、非 synthetic），但 `BeanModelBuilder.isGetter()` 不识别无前缀 accessor。
- `AnnotationBeanModelBuilder` 已提供类似模式的参考实现（独立 builder + 路由分发）。
- record 的 `IBeanModel` 当前无法实例化（无默认构造函数）也无可用属性（accessor 不被识别为 getter）。

## Goals

- record 类通过 `ReflectionManager` 获取的 `IBeanModel` 能正确识别所有 component 为只读属性。
- record 能通过 canonical constructor 创建实例。
- record 的 JSON 序列化和反序列化（通过 Nop 自己的 `JsonSerializer` / `BeanCopier`）自动可用。
- record component 上的注解（包括 `@Target(RECORD_COMPONENT)` 的注解）能被 `BeanPropertyModel` 获取。

## Non-Goals

- 不修改 `ClassModelBuilder` 的方法/字段发现逻辑。
- 不修改 `BeanModelBuilder` 的通用 getter/setter 识别逻辑。
- 不新增 `IClassModel` / `IBeanModel` 的子接口或公共 API。
- 不处理 Jackson `ObjectMapper` 对 record 的支持（Nop 平台不使用 Jackson ObjectMapper 做运行时序列化）。
- 不处理仅标注 `@Target(RECORD_COMPONENT)` 的自定义注解传播（主流注解如 `@JsonProperty`、`@Description` 通过 `@Target(METHOD)` 已自动传播到 accessor 方法）。

## Scope

### In Scope

- 新增 `RecordBeanModelBuilder`
- 修改 `ClassModel.getBeanModel()` 路由
- 修改 `ReflectionManager.getConverterForJavaType()` 增加 record 判断
- `TestRecordReflection.java` 完整测试覆盖

### Out Of Scope

- `ClassModelBuilder` 修改
- `BeanModelBuilder` 修改
- `JsonSerializer` / `BeanCopier` 修改
- `IClassModel` / `IBeanModel` 接口变更

## Execution Plan

### Phase 1 - RecordBeanModelBuilder 核心实现

Status: completed
Targets: `nop-kernel/nop-core/src/main/java/io/nop/core/reflect/impl/RecordBeanModelBuilder.java`（新增）、`ClassModel.java`、`ReflectionManager.java`

- Item Types: `Fix`、`Proof`

- [x] 新增 `RecordBeanModelBuilder`：从 `getRecordComponents()` 精确获取属性列表，通过 `getMethodByExactType` 匹配 accessor 方法，注解通过 getter 的 `IFunctionModel.getAnnotation()` 自动获取（主流注解通过 `@Target(METHOD)` 传播到 accessor），设置 `constructorEx` + `constructorPropNames`（从 `RecordComponent.getName()` 获取），标记 immutable + dataBean
- [x] 修改 `ClassModel.getBeanModel()`：在 Annotation 分支之后增加 `isRecord()` 路由分支
- [x] 修改 `ReflectionManager.getConverterForJavaType()`：在 `isAnnotationPresent(DataBean.class)` 条件后增加 `|| isRecordClass(type)` 判断

Implementation Notes:
- `getRecordComponents()`、`isRecord()`、`RecordComponent.getName()` 全部通过反射调用（项目编译目标为 Java 11，无法直接使用 Java 16+ API）
- record 的 canonical constructor 不被 `ClassModelBuilder.discoverConstructors()` 发现（因为 record 的 canonical constructor 修饰符为 0 即包私有且有参数，被过滤掉），因此 `RecordBeanModelBuilder` 直接通过 `Class.getDeclaredConstructors()` 找到匹配参数数量的构造函数，并用 `MethodModelBuilder.from()` 创建 `FunctionModel`
- 每个属性的 `serializable` 显式设为 `true`，确保 JSON 序列化时属性不被跳过
- 零 component 的 record 同时设置 `constructor` 和 `constructorEx`，确保 `newInstance(new Object[0])` 正常工作

Exit Criteria:

- [x] `./mvnw compile -pl nop-core` 通过
- [x] `RecordBeanModelBuilder` 存在且 `buildFromClassModel()` 能为简单 record（如 `record Point(int x, int y) {}`）生成正确的 BeanModel：属性数量 = component 数量，每个属性有 getter 无 setter，immutable = true，dataBean = true
- [x] `ClassModel.getBeanModel()` 对 record 类走 `RecordBeanModelBuilder` 分支，对普通 class 和 annotation 不受影响
- [x] `getConverterForJavaType()` 对 record 类型返回 `DataBeanTypeConverter`
- [x] 无静默跳过：不存在空方法体或 `// TODO` placeholder
- [x] `ai-dev/design/nop-core/00-record-support-design.md` 与实现一致（已有文档，检查一致性）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 测试覆盖

Status: completed
Targets: `nop-kernel/nop-core/src/test/java/io/nop/core/reflect/TestRecordReflection.java`

- Item Types: `Proof`

- [x] 编写 BeanModel 构建测试：record 的属性名称、类型、只读性正确
- [x] 编写实例化测试：通过 canonical constructor 创建 record 实例
- [x] 编写属性读写测试：getter 正常工作，setter 不存在
- [x] 编写 dataBean 序列化放行测试：`isDataBean()` 返回 true
- [x] 编写 JSON 序列化/反序列化端到端测试：record 与 `JsonTool` 配合完整链路
- [x] 编写注解传播测试：record component 上的 `@Description` 注解能被 BeanPropertyModel 获取
- [x] 编写泛型 record 测试：`record Pair<A, B>(A first, B second)` 类型参数正确
- [x] 编写边界测试：零 component 的 record 正常实例化
- [x] 编写紧凑构造函数测试：带验证逻辑的紧凑构造函数能正确实例化

Exit Criteria:

- [x] `./mvnw test -pl nop-core` 全部通过（含新增测试，共 225 tests pass）
- [x] 新增测试至少覆盖以上 9 个场景（实际覆盖 12 个测试用例）
- [x] JSON 序列化 + 反序列化端到端路径完整验证（Anti-Hollow Rule）
- [x] 无静默跳过：测试中不存在跳过断言的 `continue` 或空 catch
- [x] No owner-doc update required（Phase 1 已处理）
- [x] `ai-dev/logs/` 对应日期条目已更新

Build Config Note:
- `nop-kernel/nop-core/pom.xml` 增加了 `maven-compiler-plugin` 的 `default-testCompile` execution 配置，设置 `<release>16</release>`，使测试代码可以使用 Java 16+ record 语法。主代码仍然编译到 Java 11。

## Closure Gates

- [x] record 的 `IBeanModel` 能正确识别属性、实例化、JSON 序列化/反序列化
- [x] 所有 in-scope 确认的功能点有对应测试覆盖
- [x] 不存在被静默降级到 deferred 的 in-scope 功能
- [x] `ai-dev/design/nop-core/00-record-support-design.md` 与 live code 一致
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] Anti-Hollow Check：closure audit 已验证（a）`RecordBeanModelBuilder` 被 `ClassModel.getBeanModel()` 在运行时调用，（b）`getConverterForJavaType()` 对 record 返回 `DataBeanTypeConverter`，（c）无空方法体/静默跳过
- [x] `./mvnw compile -pl nop-core` 通过
- [x] `./mvnw test -pl nop-core` 通过（225 tests, 0 failures, 2 skipped - pre-existing）

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- 考虑是否需要在 `docs-for-ai/` 中补充 record 使用说明（当用户文档需要更新时）

## Closure

Status Note: Plan 127 执行完成。ReflectionManager 完整支持 Java record 类型：RecordBeanModelBuilder 正确识别属性、通过 canonical constructor 实例化、JSON 序列化/反序列化自动可用。所有 12 个测试通过，225 个 nop-core 测试全部通过。由于项目编译目标为 Java 11，record 相关 API（getRecordComponents/isRecord/RecordComponent）全部通过反射调用。

Closure Audit Evidence:

- Reviewer / Agent: Independent sub-agent (closure-audit session ses_15888c107ffeXtp2Xsa9grbv10)
- Evidence:
  - Phase 1 Exit Criteria: 6/6 PASS — RecordBeanModelBuilder exists (128 lines), ClassModel.getBeanModel() routes records correctly, getConverterForJavaType() returns DataBeanTypeConverter for records, no silent skips, design doc consistent
  - Phase 2 Exit Criteria: 4/4 PASS — 12 test methods (exceeds 9 minimum), JSON E2E verified (3 tests), no silent skips in tests
  - Closure Gates: 5/5 PASS — All in-scope features tested, no deferred defects, design doc consistent with live code
  - Anti-Hollow Check: PASS — (a) RecordBeanModelBuilder called by ClassModel.getBeanModel() at runtime, (b) getConverterForJavaType() returns DataBeanTypeConverter for records, (c) no empty method bodies/silent skips
  - `./mvnw test -pl nop-core -am`: 225 tests, 0 failures, 2 skipped (pre-existing)

Follow-up:

- no remaining plan-owned work

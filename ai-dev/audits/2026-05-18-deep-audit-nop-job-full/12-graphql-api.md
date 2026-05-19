# 维度12：GraphQL与API层

## 第 1 轮（初审）

## 审计范围
- **目录**: `nop-job/nop-job-service/src/main/java/`
- **检查文件**: 8 个 Java 文件

---

### @BizMutation 方法映射检查

**文件路径**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobFireBizModel.java`

**行号**: 46-47, 61-62

**证据代码**:
```java
@Override
@BizMutation
public void cancelFire(@Name("id") String id, IServiceContext context) {
...
@Override
@BizMutation
public void rerunFire(@Name("id") String id, IServiceContext context) {
```

**严重程度**: P3

**现状**: 正确使用 @BizMutation 注解，方法返回 void，参数使用 @Name 映射

**风险**: 无

**建议**: 保持现状

**误报排除**: 符合 Nop 平台标准实践，mutation 方法正确映射到 GraphQL mutation

---

### @BizMutation 方法批量检查

**文件路径**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java`

**行号**: 46-47, 58-59, 73-74, 86-87, 97-98, 108-109

**证据代码**:
```java
@Override
@BizMutation
public void enableSchedule(@Name("id") String id, IServiceContext context) {
...
@Override
@BizMutation
public void disableSchedule(@Name("id") String id, IServiceContext context) {
```

**严重程度**: P3

**现状**: 6 个 @BizMutation 方法全部正确使用注解和参数映射

**风险**: 无

**建议**: 保持现状

**误报排除**: 符合 Nop 平台标准实践，BizModel 返回实体对象是标准模式

---

### 分页查询检查

**文件路径**: 所有 BizModel 文件

**行号**: 全局搜索

**证据代码**:
```java
// 搜索结果：未发现 doFindPage、doFindList、findPage、findList 方法
```

**严重程度**: P3

**现状**: 未发现自定义分页查询方法，模块依赖 CrudBizModel 继承的标准 CRUD 操作

**风险**: 无

**建议**: 保持现状。如需自定义分页查询，应使用 QueryBean + FieldSelectionBean + doFindPage/doFindList 模式

**误报排除**: 简单业务场景不需要自定义分页，标准 CRUD 操作已满足需求

---

### FieldSelectionBean 使用检查

**文件路径**: 所有文件

**行号**: 全局搜索

**证据代码**:
```java
// 搜索结果：未发现 FieldSelectionBean 引用
```

**严重程度**: P3

**现状**: 未发现 FieldSelectionBean 的使用

**风险**: 无

**建议**: 保持现状。由于模块中未定义 @BizQuery 方法，因此不需要 FieldSelectionBean

**误报排除**: FieldSelectionBean 仅在 @BizQuery 方法中使用，本模块无此类方法，符合设计预期

---

### 手动序列化检查（JsonTool.stringify 使用）

**文件路径**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java`

**行号**: 11, 193, 209

**证据代码**:
```java
import io.nop.core.lang.json.JsonTool;
...
fire.setJobParamsSnapshot(JsonTool.stringify(resolveJobParams(schedule, overrideParams)));
...
Map<String, Object> parsed = JsonTool.parseBean(paramsText, Map.class);
```

**严重程度**: P2

**现状**: `NopJobScheduleBizModel` 中使用了 `JsonTool.stringify()` 和 `JsonTool.parseBean()` 对 `jobParams` 字段进行序列化/反序列化。这是将 Map 类型参数序列化为数据库 TEXT 字段的必要操作，不是绕过 GraphQL selection 的场景。

**风险**: 低。这不是绕过 GraphQL selection 的手动序列化，而是数据持久化的必要序列化。但如果未来 jobParams 结构复杂化，可能存在类型安全风险。

**建议**: 保持现状，但建议配合维度03中 triggerNow 方法的 Map<String, Object> 参数改进，一起引入类型安全的 DTO

**误报排除**: 这是将业务数据持久化到数据库的序列化操作，不是绕过 GraphQL field selection 的手动序列化

---

### 硬编码 SQL/HQL 检查

**文件路径**: 所有文件

**行号**: 全局搜索

**证据代码**:
```java
// 搜索结果：未发现硬编码 SQL 或 HQL 字符串
```

**严重程度**: P3

**现状**: 所有数据访问通过 Store 类（JobScheduleStoreImpl、JobFireStoreImpl、JobTaskStoreImpl）使用 ORM dao 进行，未发现硬编码 SQL

**风险**: 无

**建议**: 保持现状

**误报排除**: Nop ORM 的 dao 模式自动生成 SQL，无需手动编写

---

## 总结

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 0 |
| P2 | 1 |
| P3 | 5 |

GraphQL 与 API 层整体健康。所有 @BizMutation 方法正确映射，无分页查询问题，无 FieldSelectionBean 误用，无硬编码 SQL。唯一 P2 问题是 JsonTool 手动序列化的使用，但这是数据持久化的必要操作，风险可控。

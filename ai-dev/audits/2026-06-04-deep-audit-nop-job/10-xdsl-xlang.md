# 维度 10：XDSL 与 XLang 正确性

## 第 1 轮（初审）

### [维度10-01] `NopJobSchedule.xbiz` 中 triggerNow 方法签名缺少 overrideParams 参数声明

- **文件**: `nop-job/nop-job-service/src/main/resources/_vfs/nop/job/model/NopJobSchedule/NopJobSchedule.xbiz:4-6`
- **证据片段**:
  ```xml
  <mutation name="triggerNow">
      <auth roles="admin" permissions="NopJobSchedule:triggerNow"/>
  </mutation>
  ```
  对比 Java 实现：
  ```java
  public void triggerNow(@Name("id") String id, @Name("overrideParams") Map<String, Object> overrideParams,
                         IServiceContext context) {
  ```
- **严重程度**: P2
- **现状**: `triggerNow` mutation 只声明了 auth 规则，没有声明 `overrideParams` 参数。
- **风险**: 如果生成的 `_NopJobSchedule.xbiz` 也未包含该参数，GraphQL schema 将缺少此参数定义，客户端无法传入 overrideParams。
- **建议**: 验证生成的 `_NopJobSchedule.xbiz` 中 `triggerNow` 方法是否包含 `overrideParams` 参数声明。如果未包含，需在手写 xbiz 中补充。
- **信心水平**: 很可能
- **误报排除**: 如果参数声明缺失，会影响 GraphQL API 的实际可用性。
- **复核状态**: 未复核

---

**其余检查项为零发现**：x:schema 引用正确，x:extends 使用正确，beans.xml bean id 与 Java 类路径一致，xbiz 方法名与 Java 方法名兼容。

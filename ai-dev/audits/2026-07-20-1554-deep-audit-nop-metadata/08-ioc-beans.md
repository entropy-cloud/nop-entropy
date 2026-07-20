# 维度 08：IoC 与 Bean 配置 — 审计报告

> 初审结果（待复核）

## 发现条目

### [维度08-01] app-service.beans.xml 缺少 xmlns:ioc="ioc" 命名空间声明

- **文件**: `nop-metadata/nop-metadata-service/src/main/resources/_vfs/nop/metadata/beans/app-service.beans.xml:2`
- **证据**:
  ```xml
  <beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef"
         xmlns="http://www.springframework.org/schema/beans" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://www.springframework.org/schema/beans
           http://www.springframework.org/schema/beans/spring-beans-2.5.xsd">
  ```
  `<beans>` 根元素没有声明 `xmlns:ioc="ioc"`，但文件中的 5 个 bean 定义全部使用了 `ioc:default="true"` 属性。项目中所有其他 19 个模块的 `app-*.beans.xml` 文件均正确声明了此 namespace。
- **严重程度**: P2
- **现状**: 使用了 `ioc:default="true"` 属性但未声明 `xmlns:ioc="ioc"`，XML 语法上属于格式错误。
- **风险**: Nop IoC 容器可能无法正确解析 `ioc:default` 属性，使这些 bean 注册为默认候选者的意图失效。
- **建议**: 在第 2 行 `<beans>` 标签中添加 `xmlns:ioc="ioc"`。
- **信心水平**: 高
- **误报排除**: 这是真实的 XML 命名空间缺失问题，不是平台误报。所有其他模块的对应文件均正确声明。

### 合规确认

| 检查项 | 结果 |
|--------|------|
| 生成文件未被手写修改 | ✅ |
| @Inject 字段均为 protected 或 package-private（未发现 private） | ✅ |
| @InjectValue 使用正确语法 @cfg:key|default | ✅ |
| 零 @Autowired / @Value 误用 | ✅ |
| Bean 命名符合规范 | ✅ |
| 4 个子模块均有 _module 文件 | ✅ |
| beans.xml import 路径正确 | ✅ |
| 无循环依赖 | ✅ |
| 构造函数注入和 setter 注入正确 | ✅ |

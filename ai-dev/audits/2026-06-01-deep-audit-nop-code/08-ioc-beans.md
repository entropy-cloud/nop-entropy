# 维度 08：IoC 与 Bean 配置

## 第 1 轮（初审）

**检查范围**：6 个 beans.xml 文件、12 个 @Inject 用法（4 个 Java 文件）、4 个 _module 文件。

### [维度08-01] _lang-typescript.beans.xml 缺少 xsi 命名空间声明

- **文件**: `nop-code/nop-code-lang-typescript/src/main/resources/_vfs/nop/code/beans/_lang-typescript.beans.xml:1-8`
- **证据片段**:
  ```xml
  <?xml version="1.0" encoding="UTF-8" ?>
  <beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef" xmlns:ioc="ioc"
         xmlns="http://www.springframework.org/schema/beans">

      <bean id="io.nop.code.lang.typescript.TypeScriptLanguageAdapter"
            class="io.nop.code.lang.typescript.TypeScriptLanguageAdapter"
            ioc:bean-type="io.nop.code.core.analyzer.ILanguageAdapter"/>
  </beans>
  ```
- **严重程度**: P3
- **现状**: `_lang-typescript.beans.xml` 的 `<beans>` 根标签缺少 `xmlns:xsi` 命名空间声明和 `xsi:schemaLocation` 属性。对比 `_lang-java.beans.xml` 和 `_lang-python.beans.xml`，它们都包含这两项声明。
- **风险**: NopIoC 使用 `x:schema` 作为权威校验依据，Spring 的 `xsi:schemaLocation` 仅用于 IDE 辅助提示。不会导致功能故障。但使用标准 XML 工具校验此文件会报错。
- **建议**: 补齐 `xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"` 和 `xsi:schemaLocation` 属性。
- **信心水平**: 确定
- **误报排除**: 已排除功能影响。NopIoC 不依赖 Spring schema location 进行 bean 解析。
- **复核状态**: 未复核

## 合规检查结果

| 检查项 | 结果 |
|--------|------|
| 生成 beans.xml 未被手写修改 | 合规 |
| app-service.beans.xml 语法正确 | 合规 |
| @Inject 字段无 private | 合规（全部 protected 或 package-private） |
| 无 Spring 注解误用 | 合规（零 @Autowired/@Value） |
| bean 命名约定 | 合规 |
| _module 文件注册 | 合规 |
| import 路径正确 | 合规 |

## 深挖第 2 轮追加

无新发现。深挖结束。

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 08-01 | P3 | _lang-typescript.beans.xml | 缺少 xsi 命名空间声明 |

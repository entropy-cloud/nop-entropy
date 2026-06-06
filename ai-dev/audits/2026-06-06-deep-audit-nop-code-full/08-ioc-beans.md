# 维度 08：IoC 与 Bean 配置

## 第 1 轮（初审）

### [维度08-01] _lang-*.beans.xml 手写文件使用了 _ 前缀（生成文件约定）

- **文件**:
  - `nop-code/nop-code-lang-java/src/main/resources/_vfs/nop/code/beans/_lang-java.beans.xml`
  - `nop-code/nop-code-lang-typescript/src/main/resources/_vfs/nop/code/beans/_lang-typescript.beans.xml`
  - `nop-code/nop-code-lang-python/src/main/resources/_vfs/nop/code/beans/_lang-python.beans.xml`
- **证据片段**:
  ```xml
  <!-- _lang-java.beans.xml -->
  <?xml version="1.0" encoding="UTF-8"?>
  <beans x:schema="/nop/schema/beans.xdef" ...>
    <bean id="JavaLanguageAdapter" class="io.nop.code.lang.java.JavaLanguageAdapter" ioc:type="io.nop.code.core.adapter.ILanguageAdapter"/>
  </beans>
  ```
- **严重程度**: P3
- **现状**: 这三个文件在 git 历史中经过人工修改，但使用了 `_` 前缀。当前 codegen 模板仅生成 `_dao.beans.xml` 和 `_service.beans.xml`，不会重新生成 `_lang-*.beans.xml`。
- **风险**: 开发者误以为这些文件由 codegen 生成，可能在 mvn install 后认为它们会被覆盖而不敢修改。
- **建议**: 重命名为不带 `_` 前缀的名称（如 `lang-java.beans.xml`），同步更新 `app-service.beans.xml` 中的 import 路径。或在文件头部添加注释标明是手写文件。
- **信心水平**: 确定
- **误报排除**: 根据 ioc-and-config.md，`_` 前缀是 codegen 产物的明确约定。手写文件不应使用此约定。
- **复核状态**: 未复核

### 合规项确认

| 检查项 | 结果 |
|--------|------|
| @Inject private 字段 | **通过** — 全部 protected 或 package-private |
| @InjectValue 语法 | **通过** — 模块未使用 @InjectValue |
| Spring 注解误用 | **通过** — 零匹配 |
| bean 命名约定 | **通过** — FQCN 模式 |
| _module 注册 | **通过** — 7 个 VFS 贡献子模块均存在 |
| beans.xml 语法 | **通过** — x:schema 和命名空间正确 |
| import 路径 | **通过** — 相对路径正确 |
| 循环依赖 | **通过** — 无循环 |
| 生成文件手动修改 | **通过**（除 08-01） |

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| [维度08-01] | P3 | lang-*/beans/_lang-*.beans.xml | 手写文件使用 _ 前缀违反生成文件命名约定 |

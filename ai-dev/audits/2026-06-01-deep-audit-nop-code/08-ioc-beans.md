# 维度08：IoC 与 Bean 配置 -- nop-code 模块审计报告

## 第 1 轮（初审）

### [维度08-01] lang 子模块缺少 `_module` 文件，beans 可能无法被 VFS 发现

- **文件**: `nop-code-lang-java/`, `nop-code-lang-python/`, `nop-code-lang-typescript/`
- **证据片段**:
  ```
  nop-code-lang-java/src/main/resources/_vfs/nop/code/beans/_lang-java.beans.xml  -- 存在
  nop-code-lang-java/src/main/resources/_vfs/_module                               -- 缺失
  ```
  对比：`nop-code-dao`、`nop-code-meta`、`nop-code-service`、`nop-code-web` 均有 `_module` 文件。
- **严重程度**: P2
- **现状**: lang-java、lang-python、lang-typescript 三个子模块有 beans.xml（定义 ILanguageAdapter bean）但没有 `_module` 文件。没有 `_module` 文件，VFS 可能不会扫描到这些模块的资源目录。
- **风险**: 语言适配器 bean 可能无法被 IoC 容器自动发现和注册。不过 `CodeIndexService` 直接 `new` 了这些 Adapter，绕过了 IoC，因此实际运行不受影响。但如果未来改为 IoC 注入，此问题会显现。
- **建议**: 为三个 lang 子模块添加 `_module` 文件（空文件即可，作为 VFS marker）。
- **信心水平**: 85%
- **误报排除**: `CodeIndexService` 通过 `new JavaLanguageAdapter()` 绕过了 IoC，因此当前不会失败。但这意味着 beans.xml 中的 bean 定义实际上是死代码。
- **复核状态**: 未复核

## 无问题确认

- **所有 @Inject 字段可见性正确**: 全部使用 `protected`（非 `private`）。
- **无 Spring 专有注解误用**: `@Value` 和 `@Autowired` 零匹配。
- **beans.xml 语法正确**: `_service.beans.xml`、`_dao.beans.xml`、`app-service.beans.xml` 均格式规范。

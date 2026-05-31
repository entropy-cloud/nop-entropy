# 维度 08：IoC 与 Bean 配置 — nop-code 模块

## 第 1 轮（初审）

### [维度08-01] _lang-*.beans.xml 文件为孤立配置，未被 IoC 容器加载

- **文件**: `nop-code-lang-java/src/main/resources/_vfs/nop/code/beans/_lang-java.beans.xml`, 同目录下 `_lang-python.beans.xml`, `_lang-typescript.beans.xml`
- **证据片段**:
  ```xml
  <!-- _lang-java.beans.xml 定义了 bean 但未被 app-service.beans.xml import -->
  <bean class="io.nop.code.lang.java.JavaLanguageAdapter" ioc:bean-type="io.nop.code.core.lang.ILanguageAdapter"/>
  
  <!-- CodeIndexService.java:165-169 硬编码注册 -->
  this.registry.registerAdapter(new JavaLanguageAdapter());
  this.registry.registerAdapter(new PythonLanguageAdapter());
  this.registry.registerAdapter(new TypeScriptLanguageAdapter());
  ```
- **严重程度**: P2
- **现状**: beans.xml 通过 ioc:bean-type 注册 ILanguageAdapter，但 app-service.beans.xml 不包含对它们的 import。Nop IoC 只自动加载 app.beans.xml 和 app-*.beans.xml。同时 CodeIndexService 绕过 IoC 直接 new 适配器。两者互相矛盾。
- **风险**: 未来有人希望注入 ILanguageAdapter 时会发现这些 bean 从未被加载。移除硬编码后功能会静默失败。
- **建议**: (A) 在 app-service.beans.xml 中添加 import，改 CodeIndexService 通过 IoC 获取适配器；或 (B) 删除 _lang-*.beans.xml 文件。
- **信心水平**: 确定
- **误报排除**: 这不是"看起来不优雅"。beans.xml 声明了 bean 但实际未加载，是配置与实现的不一致。
- **复核状态**: 未复核

### [维度08-02] nop-code-lang-* 模块缺少 _module 文件

- **文件**: `nop-code-lang-java/src/main/resources/_vfs/nop/code/`, 同目录下 lang-python, lang-typescript
- **证据片段**: 三个模块的 VFS 路径 `/nop/code/` 与已注册模块相同，但缺少 `_module` 文件。
- **严重程度**: P3
- **现状**: 资源通过 VFS classpath 合并仍可见，但不参与 Nop 模块发现机制。
- **风险**: 与 08-01 关联，如果 beans.xml 被正确导入则无问题；当前孤立状态下使问题更隐蔽。
- **建议**: 添加 _module 文件，或在修复 08-01 时一并处理。
- **信心水平**: 很可能
- **误报排除**: VFS classpath 合并使资源仍可见，但模块发现机制不生效。
- **复核状态**: 未复核

## 合规确认

- 生成文件无手写修改: PASS
- @Inject 字段可见性（全部 protected）: PASS
- 无 Spring 注解误用: PASS
- Bean 命名遵循 Nop 约定: PASS
- _module 注册正确（dao/service/meta/web）: PASS

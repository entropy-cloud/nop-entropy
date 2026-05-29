# 维度08：IoC 与 Bean 配置

## 第 1 轮（初审）

### [维度08-01] 三个 lang 模块缺少 _module 文件

- **文件**: `nop-code/nop-code-lang-java/src/main/resources/_vfs/`、`nop-code-lang-python/...`、`nop-code-lang-typescript/...`
- **行号**: N/A（文件缺失）
- **证据片段**: glob 搜索 `nop-code/nop-code-lang-*/src/main/resources/_vfs/**/_module` 返回空。对比 dao/meta/service/web 四个模块均有 _module 文件。
- **严重程度**: P2
- **现状**: 三个 lang 模块的 _vfs 下没有 _module 文件。beans 文件不被 app-service.beans.xml import。
- **风险**: 如果 Nop VFS 模块发现机制依赖 _module 文件，这三个模块的 beans 可能无法被 IoC 容器自动发现。CodeIndexService 在构造函数中硬编码了 new JavaLanguageAdapter()，所以当前功能不受影响。但 LanguageAdapterRegistry 的 @Inject setAdapters 将无法通过 IoC 接收适配器。
- **建议**: 在三个 lang 模块的 _vfs 下添加空 _module 文件。或确认 Nop 平台的模块发现机制是否严格要求 _module 文件。
- **信心水平**: 很可能
- **误报排除**: 如果 beans 发现仅依赖路径模式而不需要 _module，则无需添加。
- **复核状态**: 未复核

## 通过项

1. 所有 @Inject 字段均为 protected（非 private）
2. 可选依赖使用 @Inject + @Nullable + setter 模式
3. 无 Spring 注解误用
4. beans.xml 结构正确，import 路径正确
5. _dao.beans.xml 无手写修改

# 维度 08：IoC Beans 审查

## 通过检查

- 无手改 `_*.beans.xml` 文件 ✓
- 所有 `@Inject` 使用 public setter 方法（非 private 字段）✓
- 无 Spring 专属注解误用（无 `@Autowired`、`@Value`）✓
- 所有 `@InjectValue` 语法正确 ✓
- `_module` 文件格式可接受 ✓

## 发现

### [08-01] P1 — job-retry-adapter.beans.xml 不匹配 NopIoC 自动发现模式

- **文件**: nop-job-retry-adapter/src/main/resources/_vfs/nop/job/beans/job-retry-adapter.beans.xml
- **现状**: beans 文件命名为 `job-retry-adapter.beans.xml`，不匹配 NopIoC 的自动发现模式。`AppBeanContainerLoader.isAppBeans()` 仅匹配 `app.beans.xml` 和 `app-*.beans.xml` 模式。
- **影响**: `NopRetryJobRetryBridge` bean 定义永远不会被 IoC 容器加载，导致 nop-retry 引擎集成形同虚设。该模块的 retry 桥接功能完全不工作。
- **根因**: 文件命名不符合 NopIoC 约定。
- **建议**: 将文件重命名为 `app-retry-adapter.beans.xml`。

# 维度 17：代码风格审查

## 发现

### [17-01] P2 — 测试代码中使用 System.out.println

- **文件**: TestTrigger.java:97
- **现状**: 测试代码中使用 `System.out.println` 进行输出，而非使用 SLF4J 日志或断言。
- **建议**: 替换为 SLF4J logger 或移除（如果是调试残留）。

### [17-02] P2 — import 分组顺序系统性偏差

- **文件**: 70+ 个文件
- **现状**: 70+ 个文件的 import 分组系统性将 `io.nop.*` 放在 `java.*`/`jakarta.*` 之前。AGENTS.md 规范要求的顺序为 `java.*` → `jakarta.*` → 第三方 → `io.nop.*`。
- **建议**: 使用 IDE 的 import 优化功能批量修正。这是一个大规模变更，建议作为独立的技术债务清理任务执行。

### [17-03] P2 — static import 与普通 import 交错

- **文件**: JobTimeoutCheckerImpl.java:15-16
- **现状**: static import 语句与普通 import 语句交错排列，未按规范分组。
- **建议**: 将 static import 集中放在 import 块末尾或单独分组。

### [17-04] P2 — 13 个类/接口声明缺少左大括号前空格

- **文件**: INopJobFireBiz, INopJobScheduleBiz 等 13 个文件
- **现状**: 13 个类和接口声明在 `{` 前缺少空格（如 `interface Foo{` 而非 `interface Foo {`）。
- **建议**: 统一添加空格以符合代码风格规范。

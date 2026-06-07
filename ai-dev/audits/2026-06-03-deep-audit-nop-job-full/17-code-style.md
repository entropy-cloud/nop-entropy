# 维度 17：代码风格与规范

## 第 1 轮（初审）

### [维度17-01] TestTrigger 中使用 System.out.println

- **文件**: `nop-job/nop-job-core/src/test/java/io/nop/job/core/trigger/TestTrigger.java:97`
- **证据片段**:
  ```java
  System.out.println(StringHelper.join(times, "\n"));
  ```
- **严重程度**: P2
- **现状**: 测试代码中使用 System.out.println 进行调试输出。CI 运行时产生噪音，无法通过日志级别控制。
- **建议**: 替换为 SLF4J Logger 或删除。
- **信心水平**: 高
- **误报排除**: 仓库规范明确要求使用 SLF4J。
- **复核状态**: 未复核

### [维度17-02] NopJobScheduleBizModel/NopJobFireBizModel import 分组顺序不规范

- **文件**: `NopJobScheduleBizModel.java:1-24`, `NopJobFireBizModel.java:1-18`
- **证据片段**:
  ```java
  import io.nop.job.api.spec.TriggerSpec;    // io.nop.*
  import io.nop.job.biz.INopJobScheduleBiz;  // io.nop.*
  import jakarta.inject.Inject;              // jakarta.* 应在 io.nop.* 之前
  import org.slf4j.Logger;                   // third-party 应在 io.nop.* 之前
  ```
- **严重程度**: P2
- **现状**: jakarta.* 和 org.slf4j.* import 出现在 io.nop.* 之后，违反 `java.* → jakarta.* → third-party → io.nop.*` 规范。
- **建议**: 重新排列 import 顺序。
- **信心水平**: 高
- **误报排除**: 同模块其他文件（如 JobFireStoreImpl）的 import 顺序是正确的。
- **复核状态**: 未复核

### [维度17-03] RpcBroadcastTaskBuilder.emptyIfNull 重复且未使用（死代码）

- **文件**: `RpcBroadcastTaskBuilder.java:93-95`
- **严重程度**: P2
- **现状**: emptyIfNull 方法在 RpcBroadcastTaskBuilder 和 DefaultJobTaskBuilder 中重复定义且完全相同。RpcBroadcastTaskBuilder 中该方法未被调用。
- **建议**: 删除死代码，如有需要复用 DefaultJobTaskBuilder 的版本。
- **信心水平**: 高
- **误报排除**: 已确认 RpcBroadcastTaskBuilder.buildTasks 中未引用该方法。
- **复核状态**: 未复核

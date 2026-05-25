# 维度 19：命名与术语一致性

## 第 1 轮（初审）

### 检查范围

检查了以下命名一致性方面：
1. 子模块命名（api/core/runtime/cep/connector/flow/checkpoint/flink/fraud-example）
2. 包名结构（io.nop.stream.core、io.nop.stream.cep、io.nop.stream.runtime 等）
3. 核心接口命名（SourceFunction、SinkFunction、StreamOperator、IStateBackend 等）
4. 错误码前缀（nop.err.cep.*）
5. Bean 命名（无 beans.xml）
6. Timer 相关接口（core.time.TimerService vs cep.time.TimerService）

### 发现

### [维度19-01] 两个同名 TimerService 接口位于不同包中

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/time/TimerService.java` 和 `nop-stream-cep/src/main/java/io/nop/stream/cep/time/TimerService.java`
- **证据片段**:
  ```java
  // core.time.TimerService — @Deprecated, @Internal, 零引用
  @Deprecated @Internal
  public interface TimerService { ... }

  // cep.time.TimerService — 活跃使用
  public interface TimerService {
      long currentProcessingTime();
      void registerProcessingTimeTimer(long time);
      ...
  }
  ```
- **严重程度**: P3
- **现状**: 两个同名接口在不同包中。core 版本已废弃且零引用，cep 版本活跃使用。开发者可能误导入已废弃的版本。
- **风险**: 开发者误导入已废弃的 core.time.TimerService，导致编译通过但语义错误。
- **建议**: 移除已废弃的 core.time.TimerService。
- **误报排除**: 已在维度03中报告为死 API，此处从命名一致性角度重复记录。
- **复核状态**: 未复核

---

### 其他检查项（均合规）

- **子模块命名**: api/core/runtime/cep/connector 均为清晰的职责描述词，合规。
- **包名结构**: `io.nop.stream.core.*`、`io.nop.stream.cep.*`、`io.nop.stream.runtime.*` 分层清晰，合规。
- **核心接口命名**: SourceFunction/SinkFunction 遵循 Function 后缀约定，StreamOperator/IStateBackend/ICheckpointStorage 遵循 Nop 平台 I 前缀约定，合规。
- **错误码前缀**: `nop.err.cep.*` 与模块名一致，合规。
- **实体/字段名**: 无 ORM 实体，不适用。

---

## 维度复核结论

| 编号 | 复核结论 | 理由 |
|------|---------|------|
| 19-01 | **驳回（与03-08完全重复）** | 审核文件自身承认"已在维度03中报告为死 API，此处从命名一致性角度重复记录"。同一事实不应从两个维度开出两条独立发现。合并至03-08。 |

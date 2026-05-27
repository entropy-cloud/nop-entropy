# nop-stream 多维度深度审计汇总

> 日期: 2026-05-27
> 范围: nop-stream 全部子模块（9个子模块，407个main源文件，47,881行）
> 维度: 01(依赖图)、02(模块职责)、09(错误处理)、15(类型安全)、16(测试覆盖)、17(代码风格)

## 健康基线

- 构建: ✅ 通过
- 测试: ✅ 300个测试全部通过
- 无循环依赖
- 无越界 import

## P0/P1 发现汇总（共5条P1）

| 编号 | 维度 | 严重程度 | 文件 | 摘要 |
|------|------|---------|------|------|
| 09-01 | 09 | **P1** | MalformedPatternException.java | 继承裸 RuntimeException 而非 StreamRuntimeException |
| 09-03 | 09 | **P1** | TwoPhaseCommitSinkFunction.java:88-93 | 恢复阶段吞掉 rollback 异常（无日志） |
| 09-05 | 09 | **P1** | GraphModelCheckpointExecutor.java:322-324 | 终端 savepoint 失败被静默吞掉 |
| 17-01 | 17 | **P1** | NopStreamErrors.java | ErrorCode 全部使用中文消息 |
| 17-02 | 17 | **P1** | NopCepErrors.java | ErrorCode 全部使用中文消息 |

**→ 存在 P1 问题，按流程跳过阶段B，直接进入阶段C（规划与执行）**

## P2 发现汇总（共20条）

| 维度 | 数量 | 关键问题 |
|------|------|---------|
| 01 | 2 | api空壳、runtime缺spec规定的依赖 |
| 02 | 6 | core含runtime代码、大文件、窗口算子重叠、GraphModelCheckpointExecutor全静态 |
| 09 | 4 | IllegalStateException混用、checkpoint触发静默失败、关闭异常被吞、ErrorCode中文 |
| 15 | 5 | 状态后端Map<String,Object>、恢复Class<Object>、窗口反序列化无校验、CEP raw cast |
| 16 | 4 | 弱断言、Thread.sleep、ProcessingTimeoutTrigger零覆盖 |
| 17 | 2 | 导入排序（组内/组间） |

## P3 发现汇总（共22条）

各维度均有P3级发现，主要是代码风格、低优先级重构建议等。

## 审计结论

nop-stream 模块整体质量良好——构建通过、测试充分（300个测试覆盖关键路径）、架构无循环依赖。主要问题集中在：
1. **错误处理规范性**（P1）— 中文ErrorCode、异常吞掉
2. **模块边界清晰度**（P2）— core/runtime 职责重叠
3. **类型安全防御**（P2）— Map<String,Object> 弱类型
4. **测试质量**（P2）— Thread.sleep 和弱断言

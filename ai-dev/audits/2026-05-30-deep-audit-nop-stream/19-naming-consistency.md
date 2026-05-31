# 维度 19：命名与术语一致性

## 发现

| 编号 | 严重度 | 类型 | 摘要 |
|------|--------|------|------|
| 19-01 | P2 | 异常类型不一致 | CEP 子模块中 ERR_CEP_* 错误码混用 StreamException 和 StreamRuntimeException |
| 19-02 | P3 | 方法名大小写 | ICheckpointStorage.storeCheckPoint（大写 P）与同接口其他方法不一致 |
| 19-03 | P3 | 实现类命名模式 | DefaultDeploymentPlanProvider vs DeploymentPlanProviderImpl |
| 19-04 | P3 | 裸异常（Flink 遗留） | CEP 子模块中大量裸 IllegalStateException/UnsupportedOperationException |
| 19-05 | P2 | 裸异常（新增代码） | nop-stream-runtime 新增代码中 IllegalStateException 未走错误码体系 |
| 19-06 | P3 | 已有错误码未使用 | ERR_STREAM_UNSUPPORTED 已定义但多处仍用裸异常 |
| 19-07 | P3 | Flink API 遗留 | MalformedPatternException 不支持 ErrorCode |
| 19-08 | P3 | 常量类风格 | StreamConstants 是 class，其他常量聚合均用 interface |
| 19-09 | P3 | 动词不一致 | storeCheckPoint vs storeSavepoint / getLatestCheckpoint vs loadSavepoint |
| 19-10 | P3 | 废弃错误码 | ERR_CEP_NFA_SHARED_BUFFER_ACCESS_FAILED 已定义未使用 |

## 整体评估

命名一致性处于中等水平。核心问题集中在 CEP 子模块（Flink API 迁移遗留）和 runtime 模块新增代码中裸异常未走错误码体系。高优先级修复项为 19-01（CEP 异常类型选择不一致）和 19-05（runtime 新增代码裸异常）。

注：维度 09 已详细报告裸异常问题，此处 19-05 与 09-02/09-07 有重叠。

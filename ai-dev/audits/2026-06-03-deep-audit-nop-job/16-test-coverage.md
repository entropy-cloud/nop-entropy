# 维度 16：测试覆盖与质量

## 第 1 轮（初审）

### [维度16-01] NopJobTaskBizModel 缺少测试覆盖

- **文件**: 缺少 `nop-job-service/src/test/java/io/nop/job/service/entity/TestNopJobTaskBizModel.java`
- **严重程度**: P2
- **现状**: NopJobTaskBizModel 覆写了 delete() 抛出 ERR_JOB_TASK_DELETE_NOT_ALLOWED，但没有对应测试。
- **风险**: 核心安全约束（禁止直接删除 task）可能被静默破坏。
- **建议**: 新增 TestNopJobTaskBizModel，测试 delete() 确实抛出异常。
- **信心水平**: 高
- **复核状态**: 未复核

### [维度16-02] 测试分层合理

- **严重程度**: 信息性
- **现状**: coordinator 的纯单元测试不依赖容器；集成/DB 层面用 JunitBaseTestCase + @NopTestConfig(localDb=true)。分层正确。

### [维度16-03] 核心引擎路径测试覆盖充分

- **严重程度**: 信息性
- **现状**: Planner/Dispatcher/Worker/Completion/Timeout/BizModel/Store层竞态 全部有测试覆盖，包括大量边界条件和错误路径。

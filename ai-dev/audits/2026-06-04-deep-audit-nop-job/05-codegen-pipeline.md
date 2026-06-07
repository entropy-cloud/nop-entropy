# 维度 05：生成管线完整性

## 第 1 轮（初审）

### [维度05-01] _templates/ 目录包含过时实体模板

- **文件**: `nop-job/nop-job-meta/_templates/_NopJobInstance.json`、`_NopJobDefinition.json`、`_NopJobInstanceHis.json`、`_NopJobAssignment.json`、`_NopJobPlan.json`
- **证据片段**:
  ```json
  // _NopJobDefinition.json
  {
    "sid": "",
    "jobName": "",
    "jobGroup": "",
    "jobInvoker": "",
    "status": 0,
    "repeatInterval": 0,
    ...
  }
  ```
- **严重程度**: P3
- **现状**: `_templates/` 目录包含 5 个 JSON 模板文件，引用了不存在的实体名（`NopJobDefinition`、`NopJobInstance` 等），其字段与当前 ORM 模型中的三个实体完全不匹配。
- **风险**: 不会破坏生成管线，但会给开发者造成困惑，误以为模块曾包含或计划包含这些实体。
- **建议**: 删除过时的 5 个模板文件，只保留当前实体对应的 `_NopJobSchedule.json`、`_NopJobFire.json`、`_NopJobTask.json`。
- **信心水平**: 确定
- **误报排除**: 当前 `nop-job.orm.xml` 只有 3 个 entity，没有 `NopJobDefinition` 等 5 个实体的定义。
- **复核状态**: 未复核

---

**其余检查项为零发现**：源模型正确，codegen 脚本引用正确，DAO 生成产物与源模型一致，meta/web 生成脚本正确，pom.xml 插件配置正确。

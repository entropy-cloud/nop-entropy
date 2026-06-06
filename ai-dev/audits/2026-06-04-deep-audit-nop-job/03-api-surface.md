# 维度 03：API 表面积与契约一致性

## 第 1 轮（初审）

### [维度03-01] NopJobSchedule 缺少 delete 保护，允许硬删除导致孤儿数据

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java`（全文）
- **证据片段**:
  ```java
  // NopJobFireBizModel.java:38-42 — Fire 明确阻止 delete
  @Override
  public boolean delete(String id, IServiceContext context) {
      throw new NopException(ERR_JOB_FIRE_DELETE_NOT_ALLOWED)
              .param("jobFireId", id);
  }
  // NopJobScheduleBizModel.java — 无 delete() 覆写，继承 CrudBizModel 默认行为
  ```
- **严重程度**: P2
- **现状**: `NopJobScheduleBizModel` 继承了 `CrudBizModel.delete()` 但没有覆写。ORM 模型中 `NopJobSchedule` 无 `delFlag` 列，执行的是硬删除。而 NopJobFire 和 NopJobTask 都正确覆写了 `delete()` 并抛出异常。
- **风险**: 硬删除调度记录导致关联的 Fire 和 Task 成为孤儿数据。若该调度有正在执行中的 fire，Coordinator 的 CompletionProcessor 会标记 fire 为 FAILED。
- **建议**: 覆写 `delete()` 方法，对含有活跃 fire 的 schedule 阻止删除，或增加 `delFlag` 列启用软删除。
- **信心水平**: 确定
- **误报排除**: Fire 和 Task 的 delete 保护已存在，Schedule 遗漏了该保护。
- **复核状态**: 未复核

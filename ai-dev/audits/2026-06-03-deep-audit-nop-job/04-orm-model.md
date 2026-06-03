# 维度04：ORM 模型与实体设计

## 第 1 轮（初审）

### [维度04-01] NopJobFire 唯一键在快速连续手动触发时可能冲突

- **文件**: `nop-job/model/nop-job.orm.xml:300-304`
- **证据片段**:
  ```xml
  <unique-keys>
      <unique-key name="UK_NOP_JOB_FIRE_SCHEDULE_TIME_SOURCE"
                  columns="jobScheduleId,scheduledFireTime,triggerSource"
                  i18n-en:displayName="Unique Fire Per Schedule Time Source"/>
  </unique-keys>
  ```
- **严重程度**: P2
- **现状**: 唯一键由 `(jobScheduleId, scheduledFireTime, triggerSource)` 三列组成。`triggerNow()` 中 `scheduledFireTime` 取自 `getCurrentTime()`（毫秒级），`triggerSource` 固定为 `TRIGGER_SOURCE_MANUAL = 2`。如果用户对同一 Schedule 在同一毫秒内连续调用 triggerNow，`insertManualFire` 会因 UK 冲突抛出数据库异常。
- **风险**: 同一毫秒对同一 Schedule 连续手动触发应视为幂等丢弃，而非报错。`saveEntityDirectly` 不处理 UK 冲突。
- **建议**: 在 `insertManualFire` 中对 UK 冲突添加 catch 处理，或加入 `triggeredBy` 列增加唯一键区分度。
- **信心水平**: 确定
- **误报排除**: UK 定义确实存在且 saveEntityDirectly 不处理冲突。
- **复核状态**: 未复核

### [维度04-02] NopJobTask 缺少到 NopJobSchedule 的直接关系

- **文件**: `nop-job/model/nop-job.orm.xml:403-411`
- **证据片段**:
  ```xml
  <relations>
      <to-one displayName="触发批次" name="jobFire"
              refEntityName="io.nop.job.dao.entity.NopJobFire" tagSet="pub,ref-pub">
          <join>
              <on leftProp="jobFireId" rightProp="jobFireId"/>
          </join>
      </to-one>
  </relations>
  ```
- **严重程度**: P2
- **现状**: NopJobTask 只定义了到 NopJobFire 的 to-one 关系，到达 Schedule 需两跳。代码中多处需从 Task 关联 Schedule（如 `JobTimeoutCheckerImpl`），必须先拿到 Fire 再查 Schedule。
- **风险**: 增加 Join 开销和代码复杂度。若 Schedule 有被删除的场景，两跳查询可能因中间 Fire 缺失而断链。
- **建议**: 评估是否在 Task 上添加 `jobScheduleId` 冗余字段和直接关系，或确认当前两跳路径是否满足所有业务场景。
- **信心水平**: 很可能
- **误报排除**: Task 不直接持有 scheduleId 外键，关系定义与数据模型一致。
- **复核状态**: 未复核

## 维度复核结论

待复核。

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|----------|
| 04-01 | P2 | nop-job.orm.xml:300 | NopJobFire UK在快速连续手动触发时可能冲突 |
| 04-02 | P2 | nop-job.orm.xml:403 | NopJobTask缺少到NopJobSchedule的直接关系 |

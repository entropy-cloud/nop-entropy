# 维度 04：ORM 模型与实体设计

## 第 1 轮（初审）

### [维度04-01] NopJobFire 唯一键对并发手动/恢复触发可能导致数据库约束异常而非应用级错误

- **文件**: `nop-job/model/nop-job.orm.xml:301-303`
- **证据片段**:
  ```xml
  <unique-key name="UK_NOP_JOB_FIRE_SCHEDULE_TIME_SOURCE"
              columns="jobScheduleId,scheduledFireTime,triggerSource"
              i18n-en:displayName="Unique Fire Per Schedule Time Source"/>
  ```
- **严重程度**: P2
- **现状**: 唯一键由 `(jobScheduleId, scheduledFireTime, triggerSource)` 三列组成。手动触发和恢复触发都使用 `new Timestamp(scheduleStore.getCurrentTime())` 作为 `scheduledFireTime`（见 `NopJobScheduleBizModel.java:220-221` 和 `NopJobFireBizModel.java:121-122`），且 `triggerSource` 分别为固定的 `MANUAL(2)` 或 `RECOVERY(3)`。如果同一 schedule 在同一毫秒内被手动触发两次（或恢复触发两次），第二个 insert 将违反唯一约束。
- **风险**: `insertManualFire` 中的 `fireDao().saveEntityDirectly(fire)` 会抛出数据库级约束违反异常，而非设计的 `ERR_JOB_SCHEDULE_MANUAL_TRIGGER_DISCARDED` 应用级错误。前端用户会看到不可理解的 500 错误而非清晰的业务提示。在 UI 快速双击场景下可复现。
- **建议**: 两层修复：(1) 在 `insertManualFire` 中先做 `hasWaitingFire` 式检查；(2) 或将唯一键中的 `scheduledFireTime` 替换为与 fireId 相关的表达式。
- **信心水平**: 很可能
- **误报排除**: 这不是"并发极端场景无意义"——UI 双击是常见操作；也不是 Nop 平台标准模式问题，是本模块业务逻辑与唯一键设计的结构性冲突。
- **复核状态**: 未复核

---

### [维度04-02] IX_NOP_JOB_TASK_FIRE 索引被 UK_NOP_JOB_TASK_FIRE_NO 唯一键的左前缀覆盖，属于冗余索引

- **文件**: `nop-job/model/nop-job.orm.xml:417-428`
- **证据片段**:
  ```xml
  <unique-key name="UK_NOP_JOB_TASK_FIRE_NO" columns="jobFireId,taskNo"
              i18n-en:displayName="Unique Task Per Fire"/>
  ...
  <index name="IX_NOP_JOB_TASK_FIRE" unique="false">
      <column name="jobFireId"/>
  </index>
  ```
- **严重程度**: P2
- **现状**: `UK_NOP_JOB_TASK_FIRE_NO` 在 `(jobFireId, taskNo)` 上建立了唯一索引。复合索引的左前缀规则使得 `WHERE jobFireId = ?` 查询可以复用该唯一索引。`IX_NOP_JOB_TASK_FIRE` 在 `(jobFireId)` 上单独建立了普通索引，功能完全被唯一键覆盖。
- **风险**: 写入时需维护两个索引，增加不必要的写入开销和存储空间。
- **建议**: 删除 `IX_NOP_JOB_TASK_FIRE` 索引，保留唯一键即可满足 `findTasksByFireId` 查询需求。
- **信心水平**: 确定
- **误报排除**: 复合唯一键的前缀覆盖是通用数据库原理，在 MySQL/PostgreSQL/Oracle 均成立。冗余索引是可量化的维护成本问题。
- **复核状态**: 未复核

---

### [维度04-03] 9 个域定义未被任何实体列引用，形成死代码

- **文件**: `nop-job/model/nop-job.orm.xml:58-76`
- **证据片段**:
  ```xml
  <domains>
      <domain name="userName" precision="50" stdSqlType="VARCHAR"/>   <!-- 未使用 -->
      <domain name="image" precision="100" stdSqlType="VARCHAR"/>     <!-- 未使用 -->
      <domain name="email" precision="100" stdSqlType="VARCHAR"/>     <!-- 未使用 -->
      <domain name="phone" precision="100" stdSqlType="VARCHAR"/>     <!-- 未使用 -->
      <domain name="roleId" precision="100" stdSqlType="VARCHAR"/>    <!-- 未使用 -->
      <domain name="userId" precision="50" stdSqlType="VARCHAR"/>     <!-- 未使用 -->
      <domain name="deptId" precision="50" stdSqlType="VARCHAR"/>     <!-- 未使用 -->
      <domain name="json-1000" precision="1000" stdDomain="json" stdSqlType="VARCHAR"/>  <!-- 未使用 -->
      <domain name="delFlag" stdDomain="boolFlag" stdSqlType="TINYINT"/>  <!-- 未使用 -->
      ...
  </domains>
  ```
- **严重程度**: P3
- **现状**: 16 个域定义中有 9 个未被任何实体列引用：`userName`、`image`、`email`、`phone`、`roleId`、`userId`、`deptId`、`json-1000`、`delFlag`。
- **风险**: 增加模型阅读和维护的认知成本；暗示模型可能从模板复制而来未清理。
- **建议**: 删除未使用的域定义。如果部分域是为 Delta 定制预留的，应添加注释说明意图。
- **信心水平**: 确定
- **误报排除**: 已逐一在三个实体的全部列中搜索引用，确认无匹配。
- **复核状态**: 未复核

---

### [维度04-04] NopJobSchedule 缺少 delFlag 软删除支持，偏离 Nop 平台配置实体的标准模式

- **文件**: `nop-job/model/nop-job.orm.xml:83-202`
- **证据片段**:
  ```xml
  <entity className="io.nop.job.dao.entity.NopJobSchedule" createTimeProp="createTime" createrProp="createdBy"
          displayName="调度定义" name="io.nop.job.dao.entity.NopJobSchedule" registerShortName="true"
          tableName="nop_job_schedule" updateTimeProp="updateTime" updaterProp="updatedBy" versionProp="version"
          ext:icon="calendar-clock" i18n-en:displayName="Job Schedule">
  ```
- **严重程度**: P3
- **现状**: NopJobSchedule 是用户管理的配置实体（有 BizModel 的增删改查操作），但没有 `delFlag` 列和 `deleteFlagProp` 属性。nop-auth 模块中所有配置实体均使用 `deleteFlagProp="delFlag"`。当前 NopJobSchedule 的 `delete` 操作会执行物理删除。NopJobFire 和 NopJobTask 作为运行时数据，不需要软删除是合理的。
- **风险**: 用户误删调度定义后无法恢复；与平台其他模块的行为不一致。
- **建议**: 为 NopJobSchedule 实体添加 `delFlag` 列（使用已定义但未使用的 `delFlag` 域），并在 entity 上设置 `deleteFlagProp="delFlag"`。
- **信心水平**: 很可能
- **误报排除**: NopJobFire/NopJobTask 不需要是正确的。但 NopJobSchedule 作为配置实体，其 CrudBizModel 的 delete 行为与平台其他配置实体不一致。
- **复核状态**: 未复核

---

### [维度04-05] NopJobSchedule→NopJobFire 和 NopJobFire→NopJobTask 缺少反向 to-many 关系定义

- **文件**: `nop-job/model/nop-job.orm.xml:291-299` 和 `:407-415`
- **证据片段**:
  ```xml
  <!-- NopJobFire 仅有 to-one 关系到 NopJobSchedule -->
  <relations>
      <to-one displayName="调度定义" name="jobSchedule"
              refEntityName="io.nop.job.dao.entity.NopJobSchedule" tagSet="pub,ref-pub">
          <join>
              <on leftProp="jobScheduleId" rightProp="jobScheduleId"/>
          </join>
      </to-one>
  </relations>
  <!-- NopJobSchedule 无反向 to-many 关系定义 -->
  ```
- **严重程度**: P3
- **现状**: NopJobFire 到 NopJobSchedule 和 NopJobTask 到 NopJobFire 各定义了一个 `to-one` 关系，但反向没有 `to-many` 关系。代码中通过专用 Store 类执行独立查询替代了 ORM 关系导航。
- **风险**: GraphQL 客户端无法通过标准关联路径查询关联数据。当前不影响功能正确性。
- **建议**: 有意识的设计选择（通过 Store 类控制查询性能），建议在 NopJobSchedule 上添加反向 `to-many` 关系并设置 `tagSet` 控制是否暴露到 GraphQL。
- **信心水平**: 很可能
- **误报排除**: 单向关系本身没问题。但在 Nop 平台中，缺少反向关系意味着生成的 XMeta 不包含关联字段，影响 GraphQL API 的自动映射能力。
- **复核状态**: 未复核

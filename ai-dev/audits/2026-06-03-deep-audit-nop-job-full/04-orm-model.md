# 维度 04：ORM 模型与实体设计

## 第 1 轮（初审）

### [维度04-01] dict option 级别 i18n-en:label 覆盖不完整

- **文件**: `nop-job/model/nop-job.orm.xml:8-55`
- **证据片段**:
  ```xml
  <dict label="调度状态" name="job/schedule-status" valueType="int" i18n-en:label="Schedule Status">
      <option code="DISABLED" label="已禁用" value="0"/>
      <option code="ENABLED" label="已启用" value="10"/>
      <option code="PAUSED" label="已暂停" value="20"/>
      <option code="COMPLETED" label="已完成" value="30"/>
      <option code="ARCHIVED" label="已归档" value="40"/>
  </dict>
  ```
- **严重程度**: P2
- **现状**: 7 个 dict 共 32 个 option。dict 级 label 全部有 i18n-en:label。但 option 级 label 只有 4 个 option 提供了 i18n-en:label（task-status/SUSPICIOUS、executor-kind/test、executor-kind/rpc、executor-kind/rpcBroadcast），其余 28 个 option 均缺少 i18n-en:label。覆盖率为 4/32 = 12.5%。
- **风险**: 当系统以英文 locale 运行时，UI 下拉框/状态展示中这 28 个 option 将显示中文 label，对英文用户不可读。
- **建议**: 为所有 32 个 dict option 补齐 i18n-en:label，或者确认平台有统一的英文 fallback 机制后仅做文档化确认。
- **信心水平**: 高
- **误报排除**: 缺少 option 级 i18n-en:label 会导致英文 locale 下 UI 显示中文文本，属于真实的功能缺陷。entity 级本地化做得好但 dict option 级被遗漏。
- **复核状态**: 未复核

### [维度04-02] NopJobFire.triggerSource 参与唯一键但未标记 mandatory

- **文件**: `nop-job/model/nop-job.orm.xml:227-229, 300-303`
- **证据片段**:
  ```xml
  <!-- 列定义 -->
  <column code="TRIGGER_SOURCE" displayName="触发来源" name="triggerSource" propId="6"
          stdDataType="int" stdSqlType="INTEGER" i18n-en:displayName="Trigger Source"
          ext:dict="job/trigger-source"/>

  <!-- 唯一键定义 -->
  <unique-keys>
      <unique-key name="UK_NOP_JOB_FIRE_SCHEDULE_TIME_SOURCE"
                  columns="jobScheduleId,scheduledFireTime,triggerSource"/>
  </unique-keys>
  ```
- **严重程度**: P2
- **现状**: NopJobFire 的 `triggerSource` 列没有 `mandatory="true"` 属性。该列同时参与唯一键 `UK_NOP_JOB_FIRE_SCHEDULE_TIME_SOURCE`。在 SQL 标准中 NULL 值不参与唯一约束比较。同实体的 `jobScheduleId` 和 `scheduledFireTime` 都标记了 `mandatory="true"`。
- **风险**: 若后续新增代码路径忘记设置 `triggerSource`，唯一键将无法阻止同一 schedule + 同一时间的重复 fire 记录。
- **建议**: 在 `triggerSource` 列上添加 `mandatory="true"`，与唯一键中其他两列保持一致。
- **信心水平**: 高
- **误报排除**: 唯一键的语义依赖于所有参与列为 NOT NULL。业务代码当前不出错不代表模型定义正确。
- **复核状态**: 未复核

## 无问题确认清单

| 检查维度 | 结论 |
|---------|------|
| 主键设计 | 三实体均使用 VARCHAR(32) + tagSet="seq"，合规 |
| 域（domain）使用 | 审计字段使用标准 domain，JSON 字段使用 json-4000，布尔字段使用 boolFlag |
| to-one 关系定义 | Fire->Schedule、Task->Fire 各定义了正确的 to-one 关系 |
| to-many 关系缺失 | 有意设计，store 层通过 QueryBean 直接查询 |
| displayName 本地化 | 3 个 entity + 所有 column 的 displayName 均有 i18n-en:displayName |
| 审计字段 | 三实体均完整配置 |
| 字典值一致性 | 7 个 dict 的 option code/value 与 Java 常量完全一致 |
| 索引覆盖 | 针对 planner/dispatcher/worker 三类扫描场景精确覆盖 |
| 未使用实体 | 无 |
| 表/列命名规范 | 符合 snake_case 规范 |
| 保留层 xmeta | 正确限制引擎字段为 insertable=false updatable=false |

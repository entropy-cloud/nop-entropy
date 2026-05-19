# 维度04：ORM模型与实体设计

## 第 1 轮（初审）

### 审计范围
- **目标模块**: nop-job
- **审核文件**: `nop-job/model/nop-job.orm.xml`
- **实体列表**: NopJobSchedule, NopJobFire, NopJobTask（核心实体）
- **检查项**: 主键设计、域使用、关系定义、displayName 本地化、审计字段、索引、字典定义、级联行为

---

## 完整审计发现（7 项）

---

### 发现 1：外键列缺少独立索引

**文件路径**: `nop-job/model/nop-job.orm.xml`

**行号范围**: 217-218, 291-299

**证据代码片段**:
```xml
<column code="JOB_SCHEDULE_ID" displayName="调度ID" mandatory="true" name="jobScheduleId"
        precision="32" propId="2" stdDataType="string" stdSqlType="VARCHAR"
        i18n-en:displayName="Job Schedule Id"/>
<to-one displayName="调度定义" name="jobSchedule"
        refEntityName="io.nop.job.dao.entity.NopJobSchedule" tagSet="pub,ref-pub">
    <join>
        <on leftProp="jobScheduleId" rightProp="jobScheduleId"/>
    </join>
</to-one>
<!-- 只有组合索引 (jobScheduleId, scheduledFireTime)，无独立外键索引 -->
```

**严重程度**: P2

**现状**: NopJobFire 有 jobScheduleId 外键和 to-one 关系，但只有组合索引 IX_NOP_JOB_FIRE_SCHEDULE (jobScheduleId, scheduledFireTime)，无独立外键索引。

**风险**:
1. 违反"外键列必须建索引"规范
2. 查询只涉及外键时无法有效利用索引
3. 级联删除性能差，可能全表扫描

**建议**: 添加独立外键索引 `<index name="IX_NOP_JOB_FIRE_JOB_SCHEDULE_ID"><column name="jobScheduleId"/></index>`

**误报排除**: 不是误报。组合索引不能替代单一外键索引，违反数据库设计规范第 7.1 条。

---

### 发现 2：remark 域定义精度与实际使用不一致

**文件路径**: `nop-job/model/nop-job.orm.xml`

**行号范围**: 69, 187-188

**证据代码片段**:
```xml
<domain name="remark" precision="1000" stdSqlType="VARCHAR"/>
<column code="REMARK" displayName="备注" domain="remark" name="remark" precision="200" propId="34"
        stdDataType="string" stdSqlType="VARCHAR" i18n-en:displayName="Remark"/>
```

**严重程度**: P3

**现状**: remark 域定义 precision="1000"，但实体中显式指定 precision="200" 覆盖域定义。

**风险**: 降低域复用价值，不同实体 remark 精度可能不一致。

**建议**: 统一使用域定义精度或调整域定义为 200。

**误报排除**: 不是误报。违反域定义设计初衷，每个地方显式指定 precision 降低复用性。

---

### 发现 3：NopJobTask 缺少到 NopJobFire 的关系定义

**文件路径**: `nop-job/model/nop-job.orm.xml`

**行号范围**: 325-328, 334-335

**证据代码片段**:
```xml
<entity className="io.nop.job.dao.entity.NopJobTask" ...>
    <columns>
        <column code="JOB_FIRE_ID" displayName="批次ID" mandatory="true" name="jobFireId" precision="32"
                propId="2" stdDataType="string" stdSqlType="VARCHAR" i18n-en:displayName="Job Fire Id"/>
    </columns>
    <!-- 没有 <relations> 定义 -->
</entity>
```

**严重程度**: P2

**现状**: NopJobTask 有 jobFireId 外键列但无 to-one 关系定义。NopJobFire 定义了到 NopJobSchedule 的关系。

**风险**:
1. 业务语义不完整：Task 属于 Fire 的关系未在 ORM 层表达
2. 无法通过 entity.getJobFire() 直接访问，需手动 DAO 查询
3. GraphQL API 不完整，无法自动生成关联查询字段

**建议**: 添加 to-one 关系定义：
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

**误报排除**: 不是误报。ORM 关系定义是 Nop 平台模型驱动开发核心机制，缺失导致生成的代码、GraphQL API、XMeta 不完整。

---

### 发现 4：缺少逻辑删除字段

**文件路径**: `nop-job/model/nop-job.orm.xml`

**行号范围**: 58-76, 83-202

**证据代码片段**:
```xml
<domain name="delFlag" stdDomain="boolFlag" stdSqlType="TINYINT"/>
<!-- NopJobSchedule 实体有完整审计字段，但没有 del_flag -->
<column code="CREATED_BY" displayName="创建人" domain="createdBy" mandatory="true" name="createdBy" .../>
<column code="CREATE_TIME" displayName="创建时间" domain="createTime" mandatory="true" name="createTime" .../>
<column code="UPDATED_BY" displayName="修改人" domain="updatedBy" mandatory="true" name="updatedBy" .../>
<column code="UPDATE_TIME" displayName="修改时间" domain="updateTime" mandatory="true" name="updateTime" .../>
<!-- 缺少 del_flag 字段 -->
```

**严重程度**: P2

**现状**: 域定义包含 delFlag 域，但三个核心实体（NopJobSchedule、NopJobFire、NopJobTask）都未定义 del_flag 字段。

**风险**:
1. 物理删除导致历史数据丢失，无法追溯
2. 调度历史被删除后无法恢复，影响审计和问题排查
3. 删除 Schedule 可能导致关联的 Fire 和 Task 变成孤儿记录

**建议**: 为核心实体添加 del_flag 字段：
```xml
<column code="DEL_FLAG" displayName="删除标志" domain="delFlag" name="delFlag" propId="..." stdSqlType="TINYINT"/>
```

**误报排除**: 可能是误报。NopJobFire 和 NopJobTask 是历史记录表，通常不会删除；NopJobSchedule 有 scheduleStatus 字段控制启停，物理删除可能是业务设计决策。需与业务方确认。

---

### 发现 5：外键关系缺少级联行为定义

**文件路径**: `nop-job/model/nop-job.orm.xml`

**行号范围**: 291-299

**证据代码片段**:
```xml
<relations>
    <to-one displayName="调度定义" name="jobSchedule"
            refEntityName="io.nop.job.dao.entity.NopJobSchedule" tagSet="pub,ref-pub"
            i18n-en:displayName="Job Schedule">
        <join>
            <on leftProp="jobScheduleId" rightProp="jobScheduleId"/>
        </join>
    </to-one>
</relations>
```

**严重程度**: P1

**风险**: 删除 NopJobSchedule 时关联的 NopJobFire 记录如何处理不明确，可能导致孤儿记录或数据不一致。

**建议**: 明确级联行为，添加 cascade="delete" 或在业务层添加校验逻辑。

**误报排除**: 不是误报。违反数据库设计规范关于级联行为的要求，缺少定义会导致数据完整性问题。

---

### 发现 6：部分字段缺少域定义

**文件路径**: `nop-job/model/nop-job.orm.xml`

**行号范围**: 92-110

**证据代码片段**:
```xml
<column code="NAMESPACE_ID" displayName="命名空间" name="namespaceId" precision="50" propId="2"
        stdDataType="string" stdSqlType="VARCHAR" i18n-en:displayName="Namespace Id"/>
<column code="GROUP_ID" displayName="分组" name="groupId" precision="100" propId="3"
        stdDataType="string" stdSqlType="VARCHAR" i18n-en:displayName="Group Id"/>
<column code="JOB_NAME" displayName="作业名" mandatory="true" name="jobName" precision="100" propId="4"
        stdDataType="string" stdSqlType="VARCHAR" i18n-en:displayName="Job Name"/>
```

**严重程度**: P3

**风险**: 需在多处重复定义类型和精度，不同实体相同语义字段可能定义不一致，无法利用域级别约束。

**建议**: 为标准化字段使用域定义，namespaceId 可使用 tenantId 域或定义新域。

**误报排除**: 不是误报。违反域定义设计初衷，直接使用 stdSqlType 降低代码复用性和可维护性。

---

### 发现 7：字典 option label 缺少实体前缀

**文件路径**: `nop-job/model/nop-job.orm.xml`

**行号范围**: 8-33

**证据代码片段**:
```xml
<dict label="调度状态" name="job/schedule-status" valueType="int" i18n-en:label="Schedule Status">
    <option code="DISABLED" label="已禁用" value="0"/>
    <option code="ENABLED" label="已启用" value="10"/>
    <option code="PAUSED" label="已暂停" value="20"/>
</dict>
<dict label="执行任务状态" name="job/task-status" valueType="int" i18n-en:label="Task Status">
    <option code="WAITING" label="等待执行" value="0"/>
    <option code="CLAIMED" label="已认领" value="10"/>
</dict>
```

**严重程度**: P3

**风险**: 多字典同时显示时可能混淆，国际化时需确保不同字典 label 可区分。

**建议**: 添加实体前缀提高清晰度，如"调度-已禁用"、"任务-等待执行"，或保持现状。

**误报排除**: 可能是误报。实际使用中字典通过 name 区分（job/schedule-status vs job/task-status），label 只是展示文本，且 dict 有 i18n-en 国际化。

---

## 审计通过项

- **主键设计**: 所有实体使用 `sid` 域（varchar(32) UUID），符合规范
- **审计字段**: createTime/updateTime/createdBy/updatedBy 配置正确
- **displayName 本地化**: 所有字段和关系均有 i18n-en 国际化
- **字段命名**: 遵循 snake_case 数据库命名规范
- **字典定义**: 状态字段有对应字典定义

## 深挖第 2 轮追加

**无新发现。** 深挖验证范围：
- ORM 模型仅包含 3 个实体（NopJobSchedule、NopJobFire、NopJobTask），提示中提到的 NopJobDefinition、NopJobInstance、NopJobInstanceHis、NopJobAssignment 在 ORM 文件中不存在
- 已对现有 3 个实体逐一检查了关系定义完整性、索引覆盖、域定义一致性
- 所有第一轮发现（7 项）已覆盖主要问题，深挖未发现新的结构性缺陷

## 维度复核结论

| 编号 | 标题 | 判断 | 理由 |
|------|------|------|------|
| 发现1 | 外键列缺少独立索引 | 保留 P2 | 证据准确。`nop-job.orm.xml:311-314` 确实只有组合索引 `IX_NOP_JOB_FIRE_SCHEDULE(jobScheduleId, scheduledFireTime)`，无独立外键索引。 |
| 发现2 | remark域精度不一致 | 保留 P3 | 域定义 `precision=1000`（行69），三个实体均覆盖为 `precision=200`（行188/288/400），属实但低优先级。 |
| 发现3 | NopJobTask 缺少到 NopJobFire 的关系定义 | 驳回 | 证据不准确。实际 `nop-job.orm.xml:403-410` 中 NopJobTask **已有** `<to-one name="jobFire" refEntityName="io.nop.job.dao.entity.NopJobFire">` 关系定义。审计遗漏了这一段。 |
| 发现4 | 缺少逻辑删除字段 | 降级为 P3 | 审计自身已标注"可能是误报"。NopJobFire/NopJobTask 是历史记录表不应删除，NopJobSchedule 有 scheduleStatus 管理生命周期。实际影响低。 |
| 发现5 | 外键关系缺少级联行为定义 | 降级为 P2 | NopJobFire → NopJobSchedule 的 `to-one` 无 cascade 属性属实，但 NopJobFire 是历史记录表，有独立生命周期，不应随 Schedule 级联删除。缺少 cascade 可能是正确设计，但需确认意图。 |
| 发现6 | 部分字段缺少域定义 | 保留 P3 | `namespaceId`、`groupId`、`jobName` 等确实直接用 `stdSqlType` 而非 `domain`。属实但低优先级。 |
| 发现7 | 字典 option label 缺少实体前缀 | 驳回 | dict 通过 `name` 区分（`job/schedule-status` vs `job/task-status`），label 仅展示用，且有 `i18n-en` 国际化。非实际问题。 |

### 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| 发现1 | P2 | `nop-job.orm.xml` | 外键列 jobScheduleId 缺少独立索引 |
| 发现2 | P3 | `nop-job.orm.xml` | remark 域 precision 不一致（1000 vs 200） |
| 发现4 | P3 | `nop-job.orm.xml` | 缺少逻辑删除字段（可能是正确设计） |
| 发现5 | P2 | `nop-job.orm.xml` | 外键关系缺少级联行为定义（需确认意图） |
| 发现6 | P3 | `nop-job.orm.xml` | 部分字段缺少域定义，直接用 stdSqlType |

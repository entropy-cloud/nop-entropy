# 维度 04：ORM 模型与实体设计

## 第 1 轮（初审）

### [维度04-01] remark 域 precision=1000 与列声明 precision=200 矛盾

- **文件**: `nop-job/model/nop-job.orm.xml:69`（域定义）、行 187/288/400（列引用）
- **证据片段**:
```xml
<domain name="remark" precision="1000" stdSqlType="VARCHAR"/>
```
vs
```xml
<column code="REMARK" displayName="备注" domain="remark" name="remark" precision="200" .../>
```
- **严重程度**: P3
- **现状**: 域 `remark` 声明 `precision="1000"`，但三个实体的 REMARK 列都显式写了 `precision="200"`。column 级别覆盖 domain，实际 DDL 为 VARCHAR(200)，行为正确。
- **风险**: 低风险。阅读时会困惑。
- **建议**: 将 domain `remark` 的 precision 改为 200，或在 column 上移除 `precision="200"` 依赖 domain 默认值。
- **信心水平**: 95%
- **误报排除**: 已确认生成 DDL 正确（VARCHAR(200)），不影响运行。
- **复核状态**: 未复核

### [维度04-02] 9 个域声明从未被任何列引用

- **文件**: `nop-job/model/nop-job.orm.xml:59-75`
- **证据片段**:
```xml
<domain name="image" precision="100" stdSqlType="VARCHAR"/>
<domain name="email" precision="100" stdSqlType="VARCHAR"/>
<domain name="phone" precision="100" stdSqlType="VARCHAR"/>
<domain name="roleId" precision="100" stdSqlType="VARCHAR"/>
<domain name="userId" precision="50" stdSqlType="VARCHAR"/>
<domain name="deptId" precision="50" stdSqlType="VARCHAR"/>
<domain name="userName" precision="50" stdSqlType="VARCHAR"/>
<domain name="json-1000" precision="1000" stdDomain="json" stdSqlType="VARCHAR"/>
<domain name="delFlag" stdDomain="boolFlag" stdSqlType="TINYINT"/>
```
- **严重程度**: P3
- **现状**: 9 个域（image, email, phone, roleId, userId, deptId, userName, json-1000, delFlag）从未被任何列引用。可能是模板复制残留。
- **风险**: 增加维护负担和阅读干扰。
- **建议**: 删除未使用的域定义。`delFlag` 的存在可能暗示设计时考虑了软删除但最终没有实现。
- **信心水平**: 99%
- **误报排除**: 已对全部列定义和 Java 代码做搜索确认无引用。
- **复核状态**: 未复核

### [维度04-03] NopJobSchedule 实体缺少 delFlag（软删除）字段

- **文件**: `nop-job/model/nop-job.orm.xml:83-202`
- **证据片段**: NopJobSchedule 有审计字段（version, createdBy, createTime, updatedBy, updateTime, remark）但没有 `delFlag` 字段。域定义中有 `delFlag`（行 75）但未被使用。
- **严重程度**: P3
- **现状**: 三个实体都没有逻辑删除标记。如果需要"删除"一条调度定义，只能物理删除。
- **风险**: 物理删除后数据无法恢复。但 scheduleStatus=ARCHIVED 可能是有意的替代方案。
- **建议**: 明确业务意图：如果通过 scheduleStatus=ARCHIVED 替代，在注释中说明并删除未使用的 delFlag 域。
- **信心水平**: 80%
- **误报排除**: 可能是刻意设计：通过 scheduleStatus=ARCHIVED(40) 来归档而非删除。
- **复核状态**: 未复核

### [维度04-04] NopJobSchedule propId 顺序不连续（28→35，跳过 29-34）

- **文件**: `nop-job/model/nop-job.orm.xml:164-171`
- **证据片段**: `propId="28"` → `propId="35"` → `propId="36"` → `propId="37"` → `propId="38"` → `propId="29"`
- **严重程度**: P3
- **现状**: propId 分配表明统计字段（35-38）是后加的。框架不要求连续，但阅读时困惑。
- **风险**: 低。框架层面无问题。
- **建议**: 后续增字段按当前最大 propId (38) 继续递增。
- **信心水平**: 95%
- **误报排除**: 已确认 propId 无重复。
- **复核状态**: 未复核

### [维度04-05] NopJobSchedule 缺少反向 to-many 关系到 NopJobFire

- **文件**: `nop-job/model/nop-job.orm.xml:83-202`
- **证据片段**: NopJobSchedule 无 `<relations>` 节点，NopJobFire 有 to-one 到 Schedule 但 Schedule 无反向 to-many。
- **严重程度**: P3
- **现状**: 只定义了"子到父"的 to-one 关系，没有"父到子"的 to-many。业务代码通过 DAO 独立查询实现关联。
- **风险**: 缺少 to-many 关系导致 GraphQL 自动嵌套查询无法生成。
- **建议**: 如果不需要 GraphQL 自动嵌套查询，保持现状。否则增加 to-many 关系。
- **信心水平**: 90%
- **误报排除**: 已检查业务代码，Fire 的查询确实通过独立查询实现。
- **复核状态**: 未复核

### 正向确认（无问题）

- 字典与 Java 常量完全一致（7 个字典逐一校验）
- 主键设计规范（VARCHAR(32) + tagSet="seq"）
- 审计字段完整（createTime/updateTime/createdBy/updatedBy）
- 乐观锁配置正确（三个实体均有 versionProp="version"，并发更新场景需要）
- i18n 本地化到位（中英文全覆盖）
- 唯一键设计合理（UK_NOP_JOB_FIRE_SCHEDULE_TIME_SOURCE 包含 triggerSource 允许同时间不同来源触发）
- useDefaultCalendar 的 boolFlag + TINYINT 是平台标准用法

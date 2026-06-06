# 维度 04：ORM 模型与实体设计

## 第 1 轮（初审）

### [维度04-01] NopJobSchedule 的 delete 未防护，删除后子记录成为孤儿

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java:37-49`
- **证据片段**:
  ```java
  @BizModel("NopJobSchedule")
  public class NopJobScheduleBizModel extends CrudBizModel<NopJobSchedule> implements INopJobScheduleBiz{
      static final Logger LOG = LoggerFactory.getLogger(NopJobScheduleBizModel.class);
      private static final java.util.Set<String> ENGINE_FIELDS = java.util.Set.of(
              "activeFireCount", "fireCount", "totalFireCount", "successFireCount", "failFireCount",
              "lastFireTime", "lastEndTime", "lastFireStatus", "lastDurationMs"
      );
      protected IJobScheduleStore scheduleStore;
      public NopJobScheduleBizModel(){
          setEntityName(NopJobSchedule.class.getName());
      }
      // 注意：没有 override delete() 方法
  ```
  对比 Fire/Task 均已 override delete 并抛异常：
  ```java
  // NopJobFireBizModel.java:39-41
  @Override
  public boolean delete(String id, IServiceContext context) {
      throw new NopException(ERR_JOB_FIRE_DELETE_NOT_ALLOWED).param("jobFireId", id);
  }
  ```

- **严重程度**: P2
- **现状**: NopJobScheduleBizModel 继承 CrudBizModel 但未 override delete()。ORM 模型中无 relations 段、无 refsNeedToCheckWhenDelete、无 delFlag 列。Fire/Task 的 delete 均已防护。
- **风险**: 通过 GraphQL delete mutation 删除 Schedule 后，nop_job_fire 中引用该 jobScheduleId 的记录成为孤儿。后续对孤儿 Fire 的操作（cancelFire、rerunFire、completeFireAndUpdateSchedule）会抛 EntityNotFoundException。
- **建议**: Override delete() 方法抛异常，与 Fire/Task 保持一致，提示用户使用 archiveSchedule。
- **信心水平**: 确定
- **误报排除**: 已排除 CrudBizModel 默认保护（需 refsNeedToCheckWhenDelete 或 ORM relations，Schedule 两者皆无）、已排除 xbiz 层面禁止（未定义）、已排除数据库外键（Nop ORM 不自动生成物理外键）。
- **复核状态**: 未复核

### [维度04-02] delFlag domain 已声明但未被任何实体列引用

- **文件**: `nop-job/model/nop-job.orm.xml:75`
- **证据片段**:
  ```xml
  <domain name="delFlag" stdDomain="boolFlag" stdSqlType="TINYINT"/>
  ```
  三个实体（NopJobSchedule、NopJobFire、NopJobTask）的 columns 中均无任何列引用此 domain。

- **严重程度**: P3
- **现状**: delFlag domain 在 domains 段中声明但从未被任何列使用。暗示最初设计时考虑过软删除但最终未采用。
- **风险**: 极低。纯死配置。可能轻微误导开发者。
- **建议**: 移除未使用的 delFlag domain 声明。
- **信心水平**: 确定
- **误报排除**: 已通过 grep 确认整个 orm.xml 中只有 domain 定义处出现 delFlag，无列引用。
- **复核状态**: 未复核

## 审计通过项

- **主键设计**: 三个实体均使用 VARCHAR(32) + tagSet=seq，规范一致
- **域使用**: boolFlag、json-4000、version、审计字段 domain 均正确引用
- **字典定义**: 7 个 dict 的 code/value 与 _NopJobCoreConstants 完全一致
- **关系定义**: Fire→Schedule、Task→Fire 的 to-one 关系正确定义
- **displayName 本地化**: 所有实体和列均有 i18n-en:displayName，zh-CN 和 en 两个 i18n 文件完整
- **索引覆盖**: 8 个索引覆盖了 Store 层全部主要查询路径
- **手写实体类**: 均为空壳继承 _gen 基类，无自定义逻辑冲突
- **级联行为**: 无 cascade 定义，Fire/Task delete 被阻止，Schedule archive 模式，设计正确

# 维度 02：模块职责与文件边界

## 第 1 轮（初审）

### [维度02-01] Biz 接口放置在 nop-job-dao 而非 nop-job-api

- **文件**: `nop-job/nop-job-dao/src/main/java/io/nop/job/biz/INopJobScheduleBiz.java`、`INopJobFireBiz.java`、`INopJobTaskBiz.java`
- **证据片段**:
  ```java
  package io.nop.job.biz;
  public interface INopJobScheduleBiz extends ICrudBiz<NopJobSchedule>{ ... }
  ```
- **严重程度**: P2
- **现状**: `I*Biz` 接口定义了面向外部消费者的 CRUD + 自定义 mutation 契约，但放置在 `nop-job-dao` 模块中，而非 `nop-job-api`。
- **风险**: 其他模块需要引用这些接口时必须依赖 `nop-job-dao`，引入了 ORM 实体类的传递依赖。
- **建议**: 将 `INopJob*Biz` 接口迁移到 `nop-job-api` 模块中。
- **信心水平**: 很可能
- **误报排除**: 按 Nop 平台惯例，I*Biz 接口放在 dao 模块是代码生成的 CRUD 契约标准模式，不应作为审计发现。**经复查，这属于平台标准模式，应驳回。**
- **复核状态**: 待复核

---

### [维度02-02] `_gen/` 下生成 view.xml 文件存在手写修改

- **文件**: `nop-job/nop-job-web/src/main/resources/_vfs/nop/job/pages/NopJobFire/_gen/_NopJobFire.view.xml`、`NopJobSchedule/_gen/_NopJobSchedule.view.xml`、`NopJobTask/_gen/_NopJobTask.view.xml`
- **证据片段**: 三个 `_gen/` 文件有未提交的本地修改（共 272 行 diff），包括列删除、列重排、布局调整。
- **严重程度**: P1
- **现状**: `_gen` 前缀意味着下次 codegen 会被覆盖，丢失手写修改。
- **风险**: 重新生成后 UI 视图变更丢失，影响用户体验和功能可用性。
- **建议**: 将视图定制移到非 `_gen` 的 view.xml 文件中（利用 `x:extends="_gen/..."` 继承后再覆盖）。
- **信心水平**: 确定
- **误报排除**: `_gen/` 文件的 git diff 确认是生成后的手写修改。
- **复核状态**: 未复核

---

### [维度02-03] `_app.orm.xml` 生成文件存在手写修改

- **文件**: `nop-job/nop-job-dao/src/main/resources/_vfs/nop/job/orm/_app.orm.xml`
- **证据片段**:
  ```xml
  +                <index name="IX_NOP_JOB_TASK_STATUS_WORKER" unique="false">
  +                    <column name="taskStatus"/>
  +                    <column name="workerInstanceId"/>
  +                </index>
  ```
- **严重程度**: P1
- **现状**: `_app.orm.xml` 本地新增了 `IX_NOP_JOB_TASK_STATUS_WORKER` 索引定义。下次 codegen 会覆盖此文件。
- **风险**: 索引丢失，导致查询性能退化。
- **建议**: 将索引定义移到源 ORM 模型文件 `model/nop-job.orm.xml` 或 `app.orm.xml` 中。
- **信心水平**: 确定
- **误报排除**: `_app.orm.xml`（下划线前缀）是生成文件。
- **复核状态**: 未复核

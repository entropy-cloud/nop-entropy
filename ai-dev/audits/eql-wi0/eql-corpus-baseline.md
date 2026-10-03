# EQL 语料基线快照（WI0 Phase 3）

- Plan: `ai-dev/plans/02-eql-wi0-verification-skeleton-and-gates.md` Phase 3
- 生成/校验: `nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/parse/TestEqlCorpusBaseline`
- 重新生成: `./mvnw test -pl :nop-orm-eql -Dtest=TestEqlCorpusBaseline -Dnop.orm.eql.updateCorpusSnapshot=true`，人工审阅 diff 后提交
- 棘轮范围: 仅 `EQL-CORPUS-BASELINE-BODY-START/END` 标记区间逐字节比对；本头为手写区

## 归一化规则（先归一化再解析）

1. 抽取每个 `<eql>` 元素的 `<source>` 文本（`<query>`/`<sql>` 语句类型不在本基线范围）；`<eql>` 无 `<source>` 时取元素内容并剔除 `<arg>`/`<validate-input>`/`<batchLoadSelection>` 声明子元素。
2. 递归把 source 内嵌套的动态子元素（如 `<sql:filter>`）替换为其内部文本（剥标签留内容；自闭合元素如 `<c:import/>` 剥除后为空）。
3. `${...}` XPL 占位符统一替换为哑字面量 `1`（参数化语句因此可解析，不退化为 parse-error 记录）。
4. XML 注释已由 XNodeParser 剥离；SQL 层 `--` 行注释保留，由解析器处理。
5. 首尾空白 trim，内部空白/换行保留。

## 红线事实

1. **`EqlASTKind.ordinal()` 不稳定**：当前 67 个常量，`add-stream-sql` 合并已对其重编号（插入点 30、53-56）。ordinal 禁止持久化、禁止跨版本比较；基线只记录回显文本与确定性错误文本，不记录 AST kind 序数。
2. **nop-stream-runtime 3 项既有测试是基线豁免**（与本 roadmap 无关，源自 commit `3c542f7046`；WI0 各交付的验证命令不计这 3 项）。2026-10-03 本工作区实测（`./mvnw test -pl :nop-stream-runtime -am -Dtest='TestE2EJdbcTwoPhaseCommitSink,TestCheckpointInheritedTwoPhaseCommitRestore,TestStreamConnectorRegistryDiscovery' -Dsurefire.failIfNoSpecifiedTests=false`，共 13 个测试、1 失败——roadmap 预期的"3 项失败"与实测不符，以实测为准）：
   - `TestE2EJdbcTwoPhaseCommitSink`：PASS（Tests run: 4, Failures: 0）
   - `TestCheckpointInheritedTwoPhaseCommitRestore`：PASS（Tests run: 1, Failures: 0）
   - `TestStreamConnectorRegistryDiscovery`：FAIL（Tests run: 8, Failures: 1）— `testCapabilityMatrixAlignedWithDesignAdjudication`：`debezium-cdc ==> expected: <SINGLE_INSTANCE> but was: <PARALLEL>`

<!-- EQL-CORPUS-BASELINE-BODY-START -->
## `nop-job/nop-job-dao/src/main/resources/_vfs/nop/job/sql/NopJobTask.sql-lib.xml`

statements: 2

### sumReservedCost

normalized source:

```sql
select sum(o.costCpu) as cpu, sum(o.costMemory) as memory
                from io.nop.job.dao.entity.NopJobTask o
                where o.workerInstanceId = 1
                  and o.taskStatus in (1)
```

echo:

```sql
select 
  sum(o.costCpu) as cpu,
  sum(o.costMemory) as memory
from 
  io.nop.job.dao.entity.NopJobTask as o
where 
  (o.workerInstanceId =  1 ) and o.taskStatus in (
     1 
  )

```

### sumReservedCostByWorker

normalized source:

```sql
select o.workerInstanceId as workerInstanceId,
                       sum(o.costCpu) as cpu,
                       sum(o.costMemory) as memory
                from io.nop.job.dao.entity.NopJobTask o
                where o.taskStatus in (1)
                  and o.workerInstanceId is not null
                group by o.workerInstanceId
```

echo:

```sql
select 
  o.workerInstanceId as workerInstanceId,
  sum(o.costCpu) as cpu,
  sum(o.costMemory) as memory
from 
  io.nop.job.dao.entity.NopJobTask as o
where 
  o.taskStatus in (
     1 
  ) and o.workerInstanceId is not null 
group by 
  o.workerInstanceId

```

## `nop-demo/nop-spring-simple-demo/src/main/resources/_vfs/nop/demo/sql/demo.sql-lib.xml`

statements: 1

### findFirstByName

normalized source:

```sql
select o from DemoEntity o where o.name like 1
```

echo:

```sql
select 
  o
from 
  DemoEntity as o
where 
  o.name like  1 

```

## `nop-demo/nop-orm-demo/src/test/resources/_vfs/database/system/concepts/sql/chapter3.sql-lib.xml`

statements: 24

### exercise_1b

normalized source:

```sql
select distinct t1.studentId
                from Taking t1
                inner join Teaching t2 on t1.section = t2.section
                where t2.instructor.name = 1
```

echo:

```sql
select 
  distinct t1.studentId
from 
  Taking as t1 
    join Teaching as t2 on t1.section = t2.section
where 
  t2.instructor.name =  1 

```

### exercise_1c

normalized source:

```sql
select max(o.salary)
                from Instructor o
```

echo:

```sql
select 
  max(o.salary)
from 
  Instructor as o

```

### exercise_1d

normalized source:

```sql
select o
                from Instructor o
                where o.salary = (
                select max(i.salary)
                from Instructor i
                )
```

echo:

```sql
select 
  o
from 
  Instructor as o
where 
  o.salary = (
    select 
      max(i.salary)
    from 
      Instructor as i
    
  )

```

### exercise_1e

normalized source:

```sql
select s as section,
                (select count(t.studentId)
                from Taking t
                where t.section = s
                ) as studentCount
                from Section s
                where s.year = 2017 and s.semester = 'Fall'
```

echo:

```sql
select 
  s as section,
  (
    select 
      count(t.studentId)
    from 
      Taking as t
    where 
      t.section = s
    
  ) as studentCount
from 
  Section as s
where 
  (s.year =  2017 ) and (s.semester =  'Fall' )

```

### exercise_1f

normalized source:

```sql
select max(o.studentCount) as maxCount
                from (
                select t.section, count(t.studentId) as studentCount
                from Taking t
                where t.section.year = 2017 and t.section.semester = 'Fall'
                group by t.section
                ) o
```

echo:

```sql
select 
  max(o.studentCount) as maxCount
from 
  (
    select 
      t.section,
      count(t.studentId) as studentCount
    from 
      Taking as t
    where 
      (t.section.year =  2017 ) and (t.section.semester =  'Fall' )
    group by 
      t.section
    
  ) as o

```

### exercise_1g

normalized source:

```sql
with SectionCount as
                (
                select t.section, count(t.studentId) as studentCount
                from Taking t
                where t.section.year = 2017 and t.section.semester = 'Fall'
                group by t.section
                )
                select o.section, o.studentCount
                from SectionCount o
                where o.studentCount = (
                select max(sc.studentCount) from SectionCount sc
                )
```

echo:

```sql
with SectionCount as (
  select 
    t.section,
    count(t.studentId) as studentCount
  from 
    Taking as t
  where 
    (t.section.year =  2017 ) and (t.section.semester =  'Fall' )
  group by 
    t.section
  )
select 
  o.section,
  o.studentCount
from 
  SectionCount as o
where 
  o.studentCount = (
    select 
      max(sc.studentCount)
    from 
      SectionCount as sc
    
  )

```

### exercise_3a

normalized source:

```sql
update Instructor o
                set o.salary = o.salary * 1
                where o.deptName = 1
```

echo:

```sql
update
  Instructor
set 
  salary=(salary *  1 )
where 
  deptName =  1 

```

### exercise_3b

normalized source:

```sql
delete from Course c
                where c.courseId not in (
                select s.courseId from Section s
                )
```

echo:

```sql
delete
from
  Course
where 
  courseId not  in (
    select 
      s.courseId
    from 
      Section as s
    
  )

```

### exercise_3c

normalized source:

```sql
insert into Instructor(id, name, deptName, salary)
                select s.id, s.name, s.deptName, 10000
                from Student s
                where s.totCred > 100
```

echo:

```sql
insert into Instructor(
  id,
  name,
  deptName,
  salary
)select 
  s.id,
  s.name,
  s.deptName,
   10000 
from 
  Student as s
where 
  s.totCred >  100 

```

### exercise_6

normalized source:

```sql
select o
                from Department o
                where lower(o.deptName) like 1
```

echo:

```sql
select 
  o
from 
  Department as o
where 
  lower(o.deptName) like  1 

```

### slide_001

normalized source:

```sql
(
                select s.course
                from Section s
                where s.year = 2017 and s.semester = 'Fall'
                )
                union
                (
                select s.course
                from Section s
                where s.year = 2018 and s.semester = 'Spring'
                )
```

echo:

```sql
(
  select 
    s.course
  from 
    Section as s
  where 
    (s.year =  2017 ) and (s.semester =  'Fall' )
  
)
 union 
(
  select 
    s.course
  from 
    Section as s
  where 
    (s.year =  2018 ) and (s.semester =  'Spring' )
  
)
```

### slide_002

normalized source:

```sql
(
                select s.course
                from Section s
                where s.year = 2017 and s.semester = 'Fall'
                )
                intersect
                (
                select s.course
                from Section s
                where s.year = 2018 and s.semester = 'Spring'
                )
```

echo:

```sql
(
  select 
    s.course
  from 
    Section as s
  where 
    (s.year =  2017 ) and (s.semester =  'Fall' )
  
)
 intersect 
(
  select 
    s.course
  from 
    Section as s
  where 
    (s.year =  2018 ) and (s.semester =  'Spring' )
  
)
```

### slide_003

normalized source:

```sql
(
                select s.course
                from Section s
                where s.year = 2017 and s.semester = 'Fall'
                )
                except
                (
                select s.course
                from Section s
                where s.year = 2018 and s.semester = 'Spring'
                )
```

echo:

```sql
(
  select 
    s.course
  from 
    Section as s
  where 
    (s.year =  2017 ) and (s.semester =  'Fall' )
  
)
 except 
(
  select 
    s.course
  from 
    Section as s
  where 
    (s.year =  2018 ) and (s.semester =  'Spring' )
  
)
```

### slide_005

normalized source:

```sql
select avg(o.salary)
                from Instructor o
                where o.deptName = 1
```

echo:

```sql
select 
  avg(o.salary)
from 
  Instructor as o
where 
  o.deptName =  1 

```

### slide_006

normalized source:

```sql
select count(distinct o.instructor)
                from Teaching o
                where o.year = 1 and o.semester = 1
```

echo:

```sql
select 
  count( distinct o.instructor)
from 
  Teaching as o
where 
  (o.year =  1 ) and (o.semester =  1 )

```

### slide_007

normalized source:

```sql
select o.department, avg(o.salary) as avgSalary
                from Instructor o
                group by o.department
                having avg(o.salary) > 42000
```

echo:

```sql
select 
  o.department,
  avg(o.salary) as avgSalary
from 
  Instructor as o
group by 
  o.department
 having 
  avg(o.salary) >  42000 

```

### slide_008

normalized source:

```sql
select o
                from Instructor o
                where o.salary > any (
                select salary from Instructor i
                where i.deptName = 'Biology'
                )
```

echo:

```sql
select 
  o
from 
  Instructor as o
where 
  o.salary >  ANY (
    select 
      salary
    from 
      Instructor as i
    where 
      i.deptName =  'Biology' 
    
  )

```

### slide_009

normalized source:

```sql
select o
                from Instructor o
                where o.salary > all (
                select salary from Instructor i
                where i.deptName = 'Biology'
                )
```

echo:

```sql
select 
  o
from 
  Instructor as o
where 
  o.salary >  ALL (
    select 
      salary
    from 
      Instructor as i
    where 
      i.deptName =  'Biology' 
    
  )

```

### slide_010

normalized source:

```sql
select s
                from Student s
                where not exists ( -- 若差集为空，说明该学生参加了所有课程
                -- Biology 系的所有课程集合
                (select c.courseId from Course c where c.deptName = 'Biology')
                except -- 求差集
                -- 该学生参加课程的集合
                (select t.courseId from Taking t where t.student = s)
                )
```

echo:

```sql
select 
  s
from 
  Student as s
where 
   not (
   exists (
      (
        select 
          c.courseId
        from 
          Course as c
        where 
          c.deptName =  'Biology' 
        
      )
       except 
      (
        select 
          t.courseId
        from 
          Taking as t
        where 
          t.student = s
        
      )
    )
  )

```

### slide_011

normalized source:

```sql
select o.deptName, o.avgSalary
                from (
                select i.deptName, avg(i.salary) as avgSalary
                from Instructor i
                group by i.deptName) o
                where o.avgSalary > 42000
```

echo:

```sql
select 
  o.deptName,
  o.avgSalary
from 
  (
    select 
      i.deptName,
      avg(i.salary) as avgSalary
    from 
      Instructor as i
    group by 
      i.deptName
    
  ) as o
where 
  o.avgSalary >  42000 

```

### slide_012

normalized source:

```sql
with MaxBudget as (
                select max(d.budget) as maxValue
                from Department d
                )
                select o
                from Department o, MaxBudget m
                where o.budget = m.maxValue
```

echo:

```sql
with MaxBudget as (
  select 
    max(d.budget) as maxValue
  from 
    Department as d
  )
select 
  o
from 
  Department as o,
  MaxBudget as m
where 
  o.budget = m.maxValue

```

### slide_013

normalized source:

```sql
with DepartmentTotal as (
                select i.department, sum(i.salary) as totalSalary
                from Instructor i
                group by i.department
                ),
                AvgTotal as (
                select avg(dt.totalSalary) as avgValue from DepartmentTotal dt
                )
                select d.department
                from DepartmentTotal d, AvgTotal a
                where d.totalSalary > a.avgValue
```

echo:

```sql
with DepartmentTotal as (
  select 
    i.department,
    sum(i.salary) as totalSalary
  from 
    Instructor as i
  group by 
    i.department
  )

,AvgTotal as (
  select 
    avg(dt.totalSalary) as avgValue
  from 
    DepartmentTotal as dt
  )
select 
  d.department
from 
  DepartmentTotal as d,
  AvgTotal as a
where 
  d.totalSalary > a.avgValue

```

### slide_014

normalized source:

```sql
select d as department,
                (select count(1)
                from Instructor i
                where i.department = d) as instructorCount
                from Department d
                order by d.deptName
```

echo:

```sql
select 
  d as department,
  (
    select 
      count( 1 )
    from 
      Instructor as i
    where 
      i.department = d
    
  ) as instructorCount
from 
  Department as d
 order by 
  d.deptName asc 

```

### slide_015

normalized source:

```sql
delete from Instructor i
                where i.department in
                (
                select d.deptName from Department d
                where d.building = 1
                )
```

echo:

```sql
delete
from
  Instructor
where 
  department in (
    select 
      d.deptName
    from 
      Department as d
    where 
      d.building =  1 
    
  )

```

## `nop-demo/nop-orm-demo/src/test/resources/_vfs/database/system/concepts/sql/chapter4.sql-lib.xml`

statements: 3

### exercise_2a

normalized source:

```sql
select i as instructor,
                count(t.secId) as teachingCount
                from Instructor i
                left join Teaching t on i = t.instructor
                group by i
                order by teachingCount desc
```

echo:

```sql
select 
  i as instructor,
  count(t.secId) as teachingCount
from 
  Instructor as i 
    left join Teaching as t on i = t.instructor
group by 
  i
 order by 
  teachingCount desc 

```

### exercise_2b

normalized source:

```sql
select i as instructor,
                (
                select count(1)
                from Teaching t
                where t.instructor = i
                ) as teachingCount
                from Instructor i
                order by teachingCount desc
```

echo:

```sql
select 
  i as instructor,
  (
    select 
      count( 1 )
    from 
      Teaching as t
    where 
      t.instructor = i
    
  ) as teachingCount
from 
  Instructor as i
 order by 
  teachingCount desc 

```

### exercise_2d

normalized source:

```sql
select d as department, count(i.id) as instructorCount
                from Department d
                left join Instructor i on d = i.department
                group by d
```

echo:

```sql
select 
  d as department,
  count(i.id) as instructorCount
from 
  Department as d 
    left join Instructor as i on d = i.department
group by 
  d

```

## `nop-demo/nop-orm-demo/src/test/resources/_vfs/database/system/concepts/sql/chapter5.sql-lib.xml`

statements: 1

### slide_01

normalized source:

```sql
with recursive RecPre as (
                (select p.preReqId as courseId
                from PreReq p where p.course.title = 1)
                union
                (select p.preReqId as courseId
                from RecPre r join PreReq p on r.courseId = p.courseId)
                )
                select c
                from RecPre r join Course c on r.courseId = c.courseId
```

echo:

```sql
with  recursive RecPre as (
  (
    select 
      p.preReqId as courseId
    from 
      PreReq as p
    where 
      p.course.title =  1 
    
  )
   union 
  (
    select 
      p.preReqId as courseId
    from 
      RecPre as r 
        join PreReq as p on r.courseId = p.courseId
    
  ))
select 
  c
from 
  RecPre as r 
    join Course as c on r.courseId = c.courseId

```

## `nop-auth/nop-auth-service/src/test/resources/_vfs/nop/auth/sql/test.sql-lib.xml`

statements: 2

### findFirstByName

normalized source:

```sql
select u from NopAuthUser u where u.userName like 1
```

echo:

```sql
select 
  u
from 
  NopAuthUser as u
where 
  u.userName like  1 

```

### findParent

normalized source:

```sql
select o.deptId, o.deptName, o.parentId, o.parent
                from NopAuthDept o
```

echo:

```sql
select 
  o.deptId,
  o.deptName,
  o.parentId,
  o.parent
from 
  NopAuthDept as o

```

## `nop-persistence/nop-orm/src/test/resources/_vfs/nop/test/sql/test.sql-lib.xml`

statements: 3

### testComplexParams

normalized source:

```sql
select o
                from io.nop.app.SimsClass o
                where 1=1
                and o.classId in (:data.ids)and o.classId = :data.unknownParam
```

parse-error: code=nop.err.antlr.common.parse-fail expected={'!', '~', '+', '-', '(', '?', POSITION, CASE, CAST, TRIM, SUBSTRING, CROSS, IF, NULL, TRUE, FALSE, EXISTS, ORDER, GROUP, BEGIN, INTERVAL, DATE, TIME, TIMESTAMP, LOCALTIME, LOCALTIMESTAMP, YEAR, MONTH, MAX, MIN, SUM, COUNT, AVG, CURRENT_USER, NAME, TYPE, RECURSIVE, CATALOG_NAME, CHARACTER_SET_CATALOG, CHARACTER_SET_NAME, CHARACTER_SET_SCHEMA, CLASS_ORIGIN, COBOL, COLLATION_CATALOG, COLLATION_NAME, COLLATION_SCHEMA, COLUMN_NAME, COMMAND_FUNCTION, COMMITTED, CONDITION_NUMBER, CONNECTION_NAME, CONSTRAINT_CATALOG, CONSTRAINT_NAME, CONSTRAINT_SCHEMA, CURSOR_NAME, DATA, DATETIME_INTERVAL_CODE, DATETIME_INTERVAL_PRECISION, DYNAMIC_FUNCTION, FORTRAN, LENGTH, MESSAGE_LENGTH, MESSAGE_OCTET_LENGTH, MESSAGE_TEXT, MORE92, MUMPS, NULLABLE, NUMBER, PASCAL, PLI, REPEATABLE, RETURNED_LENGTH, RETURNED_OCTET_LENGTH, RETURNED_SQLSTATE, ROW_COUNT, SCALE, SCHEMA_NAME, SERIALIZABLE, SERVER_NAME, SUBCLASS_ORIGIN, TABLE_NAME, UNCOMMITTED, UNNAMED, BIT_LENGTH, COALESCE, CURRENT_DATE, CURRENT_TIMESTAMP, END, EXTRACT, FOLLOWING, GROUPS, INDICATOR, INSENSITIVE, LANGUAGE, LEVEL, LOWER, NULLIF, OCTET_LENGTH, PRECEDING, RANGE, ROW, SECTION, SESSION, TUMBLE, UNBOUNDED, UPPER, USER, VALUE, WINDOW, WORK, ZONE, IDENTIFIER_, STRING_, NUMBER_, HEX_DIGIT_, BIT_NUM_} offendingToken=: source=...       and o.classId in ([:]data.ids)and o.classId =...

### findBySqlFilter

normalized source:

```sql
select o
                from io.nop.app.SimsClass o
                where 1=1
                and o.classId in (:ids)and o.classId = :unknownParam
```

parse-error: code=nop.err.antlr.common.parse-fail expected={'!', '~', '+', '-', '(', '?', POSITION, CASE, CAST, TRIM, SUBSTRING, CROSS, IF, NULL, TRUE, FALSE, EXISTS, ORDER, GROUP, BEGIN, INTERVAL, DATE, TIME, TIMESTAMP, LOCALTIME, LOCALTIMESTAMP, YEAR, MONTH, MAX, MIN, SUM, COUNT, AVG, CURRENT_USER, NAME, TYPE, RECURSIVE, CATALOG_NAME, CHARACTER_SET_CATALOG, CHARACTER_SET_NAME, CHARACTER_SET_SCHEMA, CLASS_ORIGIN, COBOL, COLLATION_CATALOG, COLLATION_NAME, COLLATION_SCHEMA, COLUMN_NAME, COMMAND_FUNCTION, COMMITTED, CONDITION_NUMBER, CONNECTION_NAME, CONSTRAINT_CATALOG, CONSTRAINT_NAME, CONSTRAINT_SCHEMA, CURSOR_NAME, DATA, DATETIME_INTERVAL_CODE, DATETIME_INTERVAL_PRECISION, DYNAMIC_FUNCTION, FORTRAN, LENGTH, MESSAGE_LENGTH, MESSAGE_OCTET_LENGTH, MESSAGE_TEXT, MORE92, MUMPS, NULLABLE, NUMBER, PASCAL, PLI, REPEATABLE, RETURNED_LENGTH, RETURNED_OCTET_LENGTH, RETURNED_SQLSTATE, ROW_COUNT, SCALE, SCHEMA_NAME, SERIALIZABLE, SERVER_NAME, SUBCLASS_ORIGIN, TABLE_NAME, UNCOMMITTED, UNNAMED, BIT_LENGTH, COALESCE, CURRENT_DATE, CURRENT_TIMESTAMP, END, EXTRACT, FOLLOWING, GROUPS, INDICATOR, INSENSITIVE, LANGUAGE, LEVEL, LOWER, NULLIF, OCTET_LENGTH, PRECEDING, RANGE, ROW, SECTION, SESSION, TUMBLE, UNBOUNDED, UPPER, USER, VALUE, WINDOW, WORK, ZONE, IDENTIFIER_, STRING_, NUMBER_, HEX_DIGIT_, BIT_NUM_} offendingToken=: source=...       and o.classId in ([:]ids)and o.classId = :unk...

### demo_dict

normalized source:

```sql
select o.collegeId as value, o.collegeName as label
                from SimsCollege o
```

echo:

```sql
select 
  o.collegeId as value,
  o.collegeName as label
from 
  SimsCollege as o

```

## Summary

- generatedBy: TestEqlCorpusBaseline
- files: 7 (7/7 mandatory)
- statements: 36
- echo ok: 34
- parse error: 2

<!-- EQL-CORPUS-BASELINE-BODY-END -->

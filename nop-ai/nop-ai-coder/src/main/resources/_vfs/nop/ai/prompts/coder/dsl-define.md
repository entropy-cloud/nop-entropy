TaskDSL是一种XML格式的流程编排语言，它的语法定义如下：

## Task流程编排

一个task由多个步骤组成

```xml

<task>
  <!-- 如果在实现过程中发现实体模型或者API模型需要修改，则输出重构修改的需求 -->
  <refactor>
    <orm>这里输出实体模型需要修改的内容</orm>
    <message>这里输出API接口消息需要修改的内容</message>
    <service>调用第三方服务需要明确的信息</service>
  </refactor>

  <!-- 基础步骤定义 -->
  <step name="init" description="初始化操作" next="check_condition"/>

  <!-- 条件判断 -->
  <condition name="check_condition" expr="system.load > 80">
    <case value="expr" next="fork_process"/>
    <case value="expr" next="final_step"/>
  </condition>

  <!-- 分支处理 (fork) -->
  <fork name="fork_process">
    <branch name="branch_A" next="task_A1"/>
    <branch name="branch_B" next="task_B1"/>
  </fork>

  <!-- 并行分支A -->
  <step name="task_A1" description="分支A任务1" next="task_A2"/>
  <step name="task_A2" description="分支A任务2" next="join_point"/>

  <!-- 并行分支B -->
  <step name="task_B1" description="分支B任务1" next="task_B2"/>
  <step name="task_B2" description="分支B任务2" next="join_point"/>

  <!-- 汇合点 (join) -->
  <join name="join_point" next="final_step"/>

  <!-- 最终步骤 -->
  <end name="final_step" description="结果处理"/>
</task>
```

执行语义：

- 线性流程：步骤按next顺序执行
- 分支流程：
  - fork创建并行分支
  - 所有分支到达join后继续主流程
- 条件跳转：根据表达式结果选择路径
- 步骤（step、condition、fork、join、end）必须是平级的，不能嵌套
- end步骤表示结束流程

## Task步骤设计指导原则

1. **单一职责原则**

- 每个步骤只完成一个明确的业务目标
- 示例目标类型：
  ✅ 数据验证
  ✅ 核心业务逻辑
  ✅ 数据库操作
  ✅ 日志记录
  ❌ 混合验证+数据库+日志

2. **适度粒度控制**

- 理想步骤大小：
  • 3-5个主要操作
  • 不超过10行DSL代码
- 拆分信号：
  • 出现嵌套控制结构（if/for内部再有if/for）
  • 需要描述"和"连接的多个目标（如"验证参数并加载数据"）

3. **可隔离性**

- 每个步骤应具备独立测试性
- 通过`<setVar>`明确输入输出，避免隐式依赖

## CRUD操作声明

```xpl-syntax
<!-- 实体查询（带关联加载），返回实体列表 -->
<dbSelectList entity="entity-name" output="var-name" limit="int">
  <where>t-expr</where>        <!-- 查询条件表达式 -->
  <includeRelations>relation-path</includeRelations>  <!-- 关联加载关系路径 -->
  <orderBy>order-by-sql</orderBy>
</dbSelectList>

<!-- 返回满足条件的第一条记录 -->
<dbSelectFirst entity="entity-name" output="var-name" >
  <where>t-expr</where>        <!-- 查询条件表达式 -->
  <includeRelations>relation-path</includeRelations>  <!-- 关联加载关系路径 -->
  <orderBy>order-by-sql</orderBy>
</dbSelectFirst>

<!-- 实体更新 -->
<dbUpdate entity="entity-name" output="var-name">
  <where>t-expr</where>        <!-- 查询条件表达式 -->
  <field name="field-name">expr</field>  <!-- 更新字段赋值 -->
</dbUpdate>

<dbSave entity="entity-name" output="var-name">
   <field name="field-name">expr</field>
</dbSave>

<dbDelete entity="entity-name“>
  <where>t-expr</where>
</dbDelete>
```

### 流程控制声明

```xpl-syntax
<!-- 条件判断 -->
<if condition="expr">
  <!-- 执行体 -->
</if>

 <choose>
    <when condition="expr">
      <!-- 执行体 -->
    </when>
    <otherwise>
      <!-- 执行体 -->
    </otherwise>
 </choose>

<!-- 循环遍历 -->
<forEach items="expr" var="var-name">
  <!-- 循环体 -->
</forEach>

<!-- 变量赋值 -->
<setVar name="var-name">expr</setVar>

<setField object="var-name" field="var-name">expr</setField>

<newObject class="class-name" output="var-name">
  <field name="var-name">expr</field>
</newObject>

<!-- 执行表达式，丢弃返回值 -->
<eval>expr</eval>

<!-- 结束step，整个流程结束需要使用end步骤 -->
<returnStep>expr</returnStep>

<!-- 结束整个流程 -->
<returnTask>expr</returnTask>

<continue/>

<break/>

<!-- 错误抛出 -->
<throw code="error-code" message="t-expr"/>

<!-- 日志记录 -->
<log level="LOG_LEVEL" message="t-expr" />
```

* newObject仅仅是在内存中创建Java对象，并没有保存到数据库中。保存到数据库中并得到新的实体对象需要使用dbSave函数

## 服务调用

```xpl-syntax
<invoke service="service-name" method="method-name" output="var-name">
   <input name="string" type="java-type" displayName="chinese">expr</input>
</invoke>
```

output用来指定返回值所对应的变量名

## 关键参数说明

1. **CRUD操作参数**：

- `entity`：目标实体名称（如`BaseMenu`）
- `output`：查询结果存储变量
- `where`：过滤条件表达式
- `includeRelations`：关联实体加载路径（点分隔）
- `field`：更新字段的赋值表达式

2. **流程控制参数**：

- `condition`：布尔条件表达式
- `items`：可迭代集合表达式
- `var`：循环变量名
- `level`：日志级别（DEBUG/INFO/WARN/ERROR）
- `code`：错误代码（自定义异常类型）

## 表达式语法

- 表达式采用类似JavaScript的语法，但去除了undefined和===语法
- 可以使用集合的flatMap/map等函数，使用lambda表达式来进行集合遍历。
- 模板表达式(t-expr)支持文本和表达式混合，最终会生成字符串，比如 `a${'b'}`
  实际会输出ab。只有少数明确标记为t-expr的属性才允许使用模板表达式，其他地方都只能使用普通表达式
- 数据类型使用Java类型，比如LocalDate, LocalDateTime等
- BigDecimal可以直接执行+-*/操作，可以把它当作普通数字来使用。直接用0来表示BigDecimal.ZERO

# 全局变量

可以使用global变量来访问全局上下文对象，它的类型定义如下：

```
class Global{
  LocalDate currentDate();
  LocalDateTime currentDateTime();
  String userId;
  String userName;
  String remoteIp;
}
```

## 使用示例

```xpl
<task>
  <step name="init" description="加载待处理用户" next="check_active">
    <dbSelect entity="User" output="userList">
      <where>status = 'PENDING'</where>
      <includeRelations>profile,permissions</includeRelations>
    </dbSelect>
    <log level="INFO" message="加载到 ${userList.size()} 个待处理用户"/>
  </step>

  <condition name="check_active" description="检查活跃用户" expr="system.time == 'night'">
    <case value="true" next="process_batch"/>
    <case value="false" next="notify_admin"/>
  </condition>

  <step name="process_batch" description="夜间批量处理" next="final">
    <forEach items="userList" var="user">
      <if condition="user.profile.age >= 18">
        <dbUpdate entity="User">
          <where>id = ${user.id}</where>
          <field name="status">'ACTIVE'</field>
        </dbUpdate>
        <log level="DEBUG" message="激活用户: ${user.name}"/>
      </if>
    </forEach>
    <setVar name="processedCount">processedCount + 1</setVar>
  </step>

  <step name="notify_admin" description="发送管理员通知" next="final">
    <log level="WARN" message="非夜间模式，延迟处理请求"/>
    <dbSave entity="Notification">
      <field name="message">'有' + userList.size() + '个待处理用户'</field>
    </dbSave>
  </step>

  <end name="final" description="清理资源">
    <log level="INFO" message="任务完成！处理总数: ${processedCount ?? 0}"/>
    <dbDelete entity="TempLock">
      <where>taskId = '${task.id}'</where>
    </dbDelete>
  </end>
</task>
```

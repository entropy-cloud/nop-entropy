FlowDSL是一种XML格式的流程编排语言，它的语法定义如下：

## FlowDSL格式

```xml

<xpl:exec>
  <ai:IncludeXDefForAi path="/nop/ai/schema/coder/flow.xdef" xpl:lib="/nop/ai/xlib/ai.xlib"/>
</xpl:exec>
```

## 执行模型

1. **线性流程**：通过`next`属性顺序执行
2. **分支流程**：

- `decision`：条件选择路径
- `fork-join`：并行执行分支

3. **终止条件**：

- `end`：正常结束
- `<throw>`：异常终止

4. **错误处理**： `nextOnError="step"`出错时跳转到处理步骤，如果不设置nextOnError则会直接结束流程

## 操作标签集

```xml

<xpl:exec>
  <ai:IncludeXDefForAi path="/nop/ai/schema/coder/xml-dsl.xdef" xpl:lib="/nop/ai/xlib/ai.xlib"/>
</xpl:exec>
```

## 设计原则

1. **单一职责**
   每步只完成一个业务目标（验证/逻辑/DB操作）

2. **粒度控制**

- 理想：3-5个操作，≤10行DSL
- 拆分信号：嵌套控制结构、"和"连接的目标描述

3. **显式依赖**
   通过`<setVar>`传递数据，避免隐式依赖

## 表达式规范

```javascript
// 类JS语法（无undefined/===），使用Java类型系统
user.age >= 18  // age可能是BigDecimal

// 特殊类型处理：
price * 0.9     // BigDecimal直接运算
$global.currentDate().plusDays(1)  // LocalDate调用Java方法

// 集合操作（支持lambda）：
items.map(item => item.price * 0.9)

// 模板字符串（仅限t-expr属性）：
`用户${user.name}激活`
```

## 全局变量

```javascript
$global = {
  currentDate(): LocalDate,    // 当前日期
  currentDateTime(): LocalTime,// 当前时间
  userId: String,              // 当前用户ID
  userName: String,            // 用户名
  remoteIp: String             // 客户端IP
}

// 错误对象
$error = {
  code: String,    // 错误代码
  message: String  // 错误信息
}
```

## 使用示例

```xpl
<flow>

  <parameters>
    <input name="taskId" type="String" mandatory="true" description="当前任务ID"/>
  </parameters>

  <steps>
    <!-- 初始化步骤：加载待处理用户 -->
    <step name="init" description="加载待处理用户" next="check_active">
      <dbSelectList entity="User" output="userList">
        <where>status = 'PENDING'</where>
        <includeRelations>profile,permissions</includeRelations>
      </dbSelectList>
      <log level="INFO" message="加载到 ${userList.size()} 个待处理用户"/>
    </step>

    <!-- 决策步骤：检查系统时间 -->
    <decision name="check_active" description="检查活跃用户">
      <option eval="$global.currentDateTime().getHour() &gt;= 22 || $global.currentDateTime().getHour() &lt; 6"
              next="process_batch"/>
      <default next="notify_admin"/>
    </decision>

    <!-- 批量处理步骤 -->
    <step name="process_batch" description="夜间批量处理" next="final" nextOnError="handle_error">
      <setVar name="processedCount">0</setVar>
      <forEach items="userList" var="user">
        <if condition="user.profile.age &gt;= 18">
          <dbUpdate entity="User">
            <where>id = ${user.id}</where>
            <field name="status">'ACTIVE'</field>
          </dbUpdate>
          <log level="DEBUG" message="激活用户: ${user.name}"/>
          <setVar name="processedCount">processedCount + 1</setVar>
        </if>
      </forEach>
    </step>

    <!-- 通知步骤：使用dbSave直接创建通知 -->
    <step name="notify_admin" description="发送管理员通知" next="final" nextOnError="handle_error">
      <log level="WARN" message="非夜间模式，延迟处理请求"/>
      <dbSave entity="Notification" output="notification">
        <field name="message">'有' + userList.size() + '个待处理用户'</field>
        <field name="createdBy">$global.userId</field>
        <field name="createTime">$global.currentDateTime()</field>
      </dbSave>
    </step>

    <!-- 错误处理步骤 -->
    <end name="handle_error" description="错误处理">
      <log level="ERROR" message="流程执行错误: ${$error.message}" error="$error"/>
      <throw error="$error"/>
    </end>

    <!-- 结束步骤 -->
    <end name="final" description="清理资源">
      <log level="INFO" message="任务完成！处理总数: ${processedCount}"/>
      <dbDelete entity="TempLock">
        <where>taskId = '${taskId}'</where>
      </dbDelete>
    </end>
  </steps>
</flow>
```

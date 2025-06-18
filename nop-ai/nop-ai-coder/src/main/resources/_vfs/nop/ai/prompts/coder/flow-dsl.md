FlowDSL是一种XML格式的流程编排语言，它的语法定义如下：

## FlowDSL格式

```xml

<xpl:exec>
  <ai:IncludeXDefForAi path="/nop/ai/schema/coder/flow.xdef" xpl:lib="/nop/ai/xlib/ai.xlib"/>
</xpl:exec>
```

## 操作标签集

```xml

<xpl:exec>
  <ai:IncludeXDefForAi path="/nop/ai/schema/coder/xml-dsl.xdef" xpl:lib="/nop/ai/xlib/ai.xlib"/>
</xpl:exec>
```

* 使用<setField>设置实体属性即可更新数据库（底层基于ORM引擎），无需dbUpdate标签
* **不允许**使用未定义的操作标签

## EQL对象查询语法

统计汇总可以使用`<dbQueryList>`标签，它执行EQL语法返回`List<Map>`数据。`<dbQueryFirst>`执行EQL返回Map数据。

EQL=SQL+AutoJoin，在SQL语言基础上补充了关联属性路径语法，例如

```sql
SELECT
    o.id,
    o.customer.department.name AS deptName  -- 自动JOIN customer→department
FROM Order o
WHERE o.customer.department.company.status = 1  -- 3级关联
ORDER BY o.customer.joinDate DESC

SELECT
    d.name,
    (SELECT COUNT(*) FROM Employee e WHERE e.department.id = d.id) AS empCount
FROM Department d

/* 产品关联分类、供应商和仓库 */
SELECT
    p.name,
    cat.name AS category,
    s.name AS supplier,
    w.location AS warehouse
FROM Product p
LEFT JOIN p.category cat      -- 多对一
LEFT JOIN p.supplier s        -- 多对一
LEFT JOIN p.warehouse w       -- 多对一
WHERE
    cat.id IS NOT NULL OR     -- 至少有关联分类
    s.country = 'US'          -- 或美国供应商
ORDER BY p.stockQuantity DESC
```

* `<dbSelectList>`和`<dbSelectFirst>`也支持关联属性路径语法
* 优先使用`<dbSelect*>`

## 设计原则

整体采用单体架构设计，服务函数都是本地调用

1. **单一职责**
   每步只完成一个业务目标（验证/逻辑/DB操作）

2. **粒度控制**

- 理想：3-5个操作，≤10行DSL
- 拆分信号：嵌套控制结构、"和"连接的目标描述

3. **显式依赖**
   通过`<setVar>`传递数据，避免隐式依赖

4. 底层框架自动处理角色权限、乐观锁、事务管理等问题，业务代码不需要考虑这些内容

5. **逻辑线性化**: 先做业务检查，检查失败直接抛出异常，底层框架会自动处理异常。一般的异常不用通过nextToError跳转。

6. 发现异常时，应直接使用`<throw>`标签抛出异常码，避免使用`Response(code, message)`等返回值对象包装。

## 表达式规范

* 类JS语法（无undefined/===），使用Java类型系统， 可以调用java方法，比如 `$global.currentDate().plusDays(1)`
* **BigDecimal增强**：直接使用+-*/和>=等运算符，比如`price*0.9>100.0`。直接使用0而不是BigDecimal.ZERO。不要使用BigDecimal的multiply等函数。
* **集合操作**： 支持lambda，比如`items.map(item => item.price * 0.9)`
* **模板字符串**（仅限t-expr属性）: 比如 `用户${user.name}激活`
* **对象字面量**：使用 `{key: value}` 创建Map，`[elem1, elem2]` 创建List。"

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

$JSON = {
  parse(str): Object, // 解析JSON
  stringify(obj): String  // 生成JSON文本
}

// 提供所有字符串相关的帮助函数
$String = {
  camelCase(str):String,
  // 其他函数
}

// 提供所有日期和时间计算相关的帮助函数
$Date = {
  parseDate(str,fmt): LocalDate, // 解析日期字符串
  formatDate(date,fmt): String // 日期格式化
  // 其他函数
}

$Math = {
  abs(num:Number): Number, // 绝对值
  // 其他数学函数
}

```

## 全局函数

```javascript
uuid():String // 返回长度32的随机字符串

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
          <setField object="user" name="status">'ACTIVE'</setField>
          <log level="DEBUG" message="激活用户: ${user.name}"/>
          <setVar name="processedCount">processedCount + 1</setVar>
        </if>
      </forEach>
    </step>

    <!-- 通知步骤：使用dbSave直接创建通知 -->
    <step name="notify_admin" description="发送管理员通知" next="final" nextOnError="handle_error">
      <log level="WARN" message="非夜间模式，延迟处理请求"/>
      <dbInsert entity="Notification" output="notification">
        <field name="message">'有' + userList.size() + '个待处理用户'</field>
        <field name="createdBy">$global.userId</field>
        <field name="createTime">$global.currentDateTime()</field>
      </dbInsert>
    </step>

    <!-- 错误处理步骤 -->
    <end name="handle_error" description="错误处理">
      <log level="ERROR" message="流程执行错误: ${$error.message}" error="$error"/>
      <rethrow error="$error"/>
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

生成的FlowDSL必须包含完整注释，注释内容需直接嵌入用例文档中的所有关键细节，确保程序员无需查阅外部文档即可理解整个流程

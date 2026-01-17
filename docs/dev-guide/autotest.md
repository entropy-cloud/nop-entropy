# 自动测试

## 0. Maven 依赖

AutoTest 框架基于 **JUnit 5**，需要添加以下 Maven 依赖：

```xml
<dependency>
    <groupId>io.github.entropy-cloud</groupId>
    <artifactId>nop-autotest-junit</artifactId>
    <version>${nop-entropy.version}</version>
    <scope>test</scope>
</dependency>
```

## 一. 数据驱动测试

NopAutoTest测试框架是一个数据驱动的测试框架，这意味着一般情况下我们不需要编写任何准备输入数据和校验输出结果的代码，只需要编写一个骨架函数，并提供一批测试数据文件即可。具体来看一个示例

[nop-auth/nop-auth-service/src/test/io/nop/auth/service/TestLoginApi.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-auth/nop-auth-service/src/test/java/io/nop/auth/service/TestLoginApi.java)

[nop-auth/nop-auth-service/_cases/io/nop/auth/service/TestLoginApi](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-auth/nop-auth-service/_cases/io/nop/auth/service/TestLoginApi)

```java
class TestLoginApi extends JunitAutoTestCase {
    // @EnableSnapshot
    @Test
    public void testLogin() {
        LoginApi loginApi = buildLoginApi();

        //ApiRequest<LoginRequest> request = request("request.json5", LoginRequest.class);
        ApiRequest<LoginRequest> request = input("request.json5", new TypeReference<ApiRequest<LoginRequest>>() {
        }.getType());

        ApiResponse<LoginResult> result = loginApi.login(request);

        output("response.json5", result);
    }
}
```

测试用例从JunitAutoTestCase类继承，然后使用`input(fileName, javaType)`
来读取外部的数据文件，并将数据转型为javaType指定的类型。具体数据格式根据文件名的后缀来确定，可以是json/json5/yaml等。

调用被测函数之后，通过output(fileName, result)将结果数据保存到外部数据文件中，而不是编写结果校验代码。

### 1.1 录制模式

testLogin在录制模式下执行时会生成如下数据文件

```
TestLoginApi
    /input
       /tables
          nop_auth_user.csv
          nop_auth_user_role.csv
       request.json5
    /output
       /tables
          nop_auth_session.csv
      response.json5
```

/input/tables目录下会记录读取过的所有数据库记录，每张表对应一个csv文件。

> 即使是没有读取到任何数据，也会生成对应的空文件。因为在验证模式下需要根据这里录制的表名来确定需要在测试数据库中创建哪些表。

如果打开response.json5文件，我们可以看到如下内容

```
{
  "data": {
    "accessToken": "@var:accessToken",
    "attrs": null,
    "expiresIn": 600,
    "refreshExpiresIn": 0,
    "refreshToken": "@var:refreshToken",
    "scope": null,
    "tokenType": "bearer",
    "userInfo": {
      "attrs": null,
      "locale": "zh-CN",
      "roles": [],
      "tenantId": null,
      "timeZone": null,
      "userName": "auto_test1",
      "userNick": "autoTestNick"
    }
  },
  "httpStatus": 0,
  "status": 0
}
```

可以注意到，accessToken和refreshToken已经被自动替换为了变量匹配表达式。这一过程完全不需要程序员手工介入。

至于录制得到的nop\_auth\_session.csv，它的内容如下

```csv
_chgType,SID,USER_ID,LOGIN_ADDR,LOGIN_DEVICE,LOGIN_APP,LOGIN_OS,LOGIN_TIME,LOGIN_TYPE,LOGOUT_TIME,LOGOUT_TYPE,LOGIN_STATUS,LAST_ACCESS_TIME,VERSION,CREATED_BY,CREATE_TIME,UPDATED_BY,UPDATE_TIME,REMARK
A,@var:NopAuthSession@sid,067e0f1a03cf4ae28f71b606de700716,,,,,@var:NopAuthSession@loginTime,1,,,,,0,autotest-ref,*,autotest-ref,*,
```

第一列\_chgType表示数据变更类型，A-新增，U-修改,D-删除。随机生成的主键已经被替换为变量匹配表达式`@var:NopAuthSession@sid`
。同时，根据ORM模型所提供的信息，createTime字段和updateTime字段为簿记字段，它们不参与数据匹配校验，因此被替换为了\*，表示匹配任意值。

### 1.2 验证模式

当testLogin函数成功执行之后，我们就可以将测试用例从录制模式转换为验证模式：

```java
@NopTestConfig(
    localDb = true,
    initDatabaseSchema = OptionalBoolean.TRUE,
    snapshotTest = SnapshotTest.CHECKING  // 验证模式
)
public class TestLoginApi extends JunitAutoTestCase {
    // 验证实际结果与录制数据是否匹配
}
```
在验证模式下，测试用例在setUp阶段会执行如下操作:

1. 调整jdbcUrl等配置，强制使用本地内存数据库（H2）
2. 装载input/init\_vars.json5文件，初始化变量环境（可选）
3. 收集input/tables和output/tables目录下对应的表名，根据ORM模型生成对应建表语句并执行
4. 执行input目录下的所有xxx.sql脚本文件，对新建的数据库进行自定义的初始化（可选）。
5. 将input/tables目录下的数据插入到数据库中

测试用例执行过程中如果调用了output函数，则会基于MatchPattern机制来比较输出的json对象和录制的数据模式文件。具体比较规则参见下一节的介绍。
如果期待测试函数抛出异常，则可以使用error(fileName, runnable)函数来描述

```java
@Test
public void testXXXThrowException(){
        error("response-error.json5",()->xxx());
        }
```

在teardown阶段，测试用例会自动执行如下操作：

1. 比较output/tables中定义的数据变化与当前数据库中的状态，确定它们是否吻合。
2. 执行sql\_check.yaml文件中定义的校验SQL，并和期待的结果进行比较（可选）。

### 1.3 测试更新

如果后期修改了代码，测试用例的返回结果发生了变化，可以通过以下两种方式更新output目录下的录制结果。

**方式一：只更新输出数据**

```java
@NopTestConfig(
    localDb = true,
    initDatabaseSchema = OptionalBoolean.TRUE,
    forceSaveOutput = true  // 使用录制的input数据，只更新output数据
)
public class TestLoginApi extends JunitAutoTestCase {
    // 适用于：业务逻辑变化，但测试输入场景不变的情况
}
```

**方式二：重新录制所有数据**

```java
@NopTestConfig(
    localDb = true,
    initDatabaseSchema = OptionalBoolean.TRUE,
    snapshotTest = SnapshotTest.RECORDING  // 重新录制读取的table数据以及所有输出数据
)
public class TestLoginApi extends JunitAutoTestCase {
    // 执行测试后自动生成 _cases/ 目录下的数据文件
}
```

**重要**：录制模式（`snapshotTest = SnapshotTest.RECORDING`）运行完成后，会抛出异常码 `nop.err.autotest.snapshot-finished`，提示 "录制快照过程正常结束. 现在可以通过@NopTestConfig的snapshotTest属性来控制录制/校验快照数据"。这是正常流程，表示录制完成，并非错误。

## 二. 基于前缀引导语法的对象模式匹配

在上一节中，用于匹配的数据模板文件中匹配条件只包含固定值和变量表达式`@var:xx`
两种，其中变量表达式采用了所谓的前缀引导语法（详细介绍可以参加我的文章[DSL分层语法设计及前缀引导语法](https://zhuanlan.zhihu.com/p/548314138)
），这是一种可扩展的领域特定语法（DSL）设计。首先，我们注意到`@var:`前缀可以被扩展为更多情况，例如 `@ge:3`
表示大于等于3。第二，这是一种开放式的设计。\*\*
我们随时可以增加更多的语法支持，而且可以确保它们之间不会出现语法冲突\*\*。第三，这是一种局域化的嵌入式语法设计，`String->DSL`
这一转换可以将任意字符串增强为可执行的表达式，例如在csv文件中表示字段匹配条件。我们来看一个更加复杂的匹配配置

```json
{
  "a": "@ge:3",
  "b": {
    "@prefix": "and",
    "patterns": [
      "@startsWith:a",
      "@endsWith:d"
    ]
  },
  "c": {
    "@prefix": "or",
    "patterns": [
      {
        "a": 1
      },
      [
        "@var:x",
        "s"
      ]
    ]
  },
  "d": "@between:1,5"
}
```

这个示例中通过`@prefix`引入了具有复杂结构的and/or匹配条件。类似的，我们可以引入`if`,`switch`等条件分支。

```json
{
  "@prefix": "if"
  "testExpr": "matchState.value.type == 'a'",
  "true": {
    ...
  }
  "false": {
    ...
  }
}
{
  ”@prefix":"switch",
"chooseExpr": "matchState.value.type",
"cases": {
"a": {...},
"b": {
...
}
},
"default": {
...
}
}
```

testExpr为XLang表达式，其中matchState对应于当前匹配上下文对象，可以通过value获取到当前正在匹配的数据节点。根据返回值的不同，会选择匹配true或者false分支。

这里”@prefix“对应于前缀引导语法的explode模式，它将DSL展开为Json格式的抽象语法树。如果因为数据结构限制，不允许直接嵌入json，例如在csv文件中使用时，我们仍然可以使用前缀引导语法的标准形式。

```
@if:{testExpr:'xx',true:{...},false:{...}}
```

只要把if对应的参数通过JSON编码转化为字符串，再拼接上`@if:`前缀就可以了。

前缀引导语法的语法设计方式非常灵活，并不要求不同前缀的语法格式完全统一。例如`@between:1,5`
表示大于等于1并且小于等于5。前缀后面的数据格式只有前缀对一个的解析器负责识别，我们可以根据情况设计对应的简化语法。

如果只需要验证对象中的部分字段满足匹配条件，可以使用符号`*`来表示忽略其他字段

```json
{
  "a": 1,
  "*": "*"
}
```

## 三. 多步骤相关测试

如果要测试多个相关联的业务函数，我们需要在多个业务函数之间传递关联信息。例如登录系统之后得到accessToken，然后再用accessToken获取到用户详细信息，完成其他业务操作之后再传递accessToken作为参数，调用logout退出。

因为存在共享的AutoTestVars上下文环境，业务函数之间可以通过AutoTestVariable自动传递关联信息。例如

```java
    @NopTestConfig(
    localDb = true,
    initDatabaseSchema = OptionalBoolean.TRUE,
    snapshotTest = SnapshotTest.CHECKING
)
@Test
public void testLoginLogout(){
        LoginApi loginApi=buildLoginApi();

        ApiRequest<LoginRequest> request=request("1_request.json5",LoginRequest.class);

        ApiResponse<LoginResult> result=loginApi.login(request);

        output("1_response.json5",result);

        ApiRequest<AccessTokenRequest> userRequest=request("2_userRequest.json5",AccessTokenRequest.class);

        ApiResponse<LoginUserInfo> userResponse=loginApi.getLoginUserInfo(userRequest);
        output("2_userResponse.json5",userResponse);

        ApiRequest<RefreshTokenRequest> refreshTokenRequest=request("3_refreshTokenRequest.json5",RefreshTokenRequest.class);
        ApiResponse<LoginResult> refreshTokenResponse=loginApi.refreshToken(refreshTokenRequest);
        output("3_refreshTokenResponse.json5",refreshTokenResponse);

        ApiRequest<LogoutRequest> logoutRequest=request("4_logoutRequest.json5",LogoutRequest.class);
        ApiResponse<Void> logoutResponse=loginApi.logout(logoutRequest);
        output("4_logoutResponse.json5",logoutResponse);
        }
```

其中2\_userRequest.json5中的内容为

```json
{
  data: {
    accessToken: "@var:accessToken"
  }
}
```

我们可以用`@var:accessToken`来引用前一个步骤返回的accessToken变量。

### 集成测试支持

如果是在集成测试场景下，我们无法通过底层引擎自动识别并注册AutoTestVariable，则可以在测试用例中手工注册

```java
public void testXXX(){
        ....
        response=myMethod(request);
        setVar("v_myValue",response.myValue);
        // 后续的input文件中就可以通过@var:v_myValue来引用这里定义的变量
        request2=input("request2.json",Request2.class);
        ...
        }
```

在集成测试场景下，我们需要访问外部独立部署的测试数据库，而不再能够使用本地内存数据库。此时，我们可以配置localDb=false来禁用本地数据库

```java
@Test
@NopTestConfig(
    localDb = false,
    snapshotTest = SnapshotTest.CHECKING
)
public void integrationTest(){
        ...
        }
```

@NopTestConfig 具有多种开关控制，可以灵活选择启用哪些自动化测试支持

```java
public @interface NopTestConfig {
    /**
     * 是否强制设置nop.datasource.jdbc-url为h2内存数据库。SnapshotTest设置为Checking的时候总是强制使用localDb运行，这里的配置无效。
     */
    boolean localDb() default true;

    /**
     * 是否自动根据ORM模型定义初始化数据库表结构。如果是快照验证阶段，则缺省为true。但是这里可以强制覆盖这个行为
     */
    OptionalBoolean initDatabaseSchema() default OptionalBoolean.NOT_SET;

    /**
     * 启用nop-config模块的Config管理机制
     */
    OptionalBoolean enableConfig() default OptionalBoolean.NOT_SET;

    /**
     * 启用NopIoc容器, 如果不设置，则平台内置为true
     */
    OptionalBoolean enableIoc() default OptionalBoolean.NOT_SET;

    OptionalBoolean enableActionAuth() default OptionalBoolean.NOT_SET;

    OptionalBoolean enableDataAuth() default OptionalBoolean.NOT_SET;

    /**
     * RECORDING模式下会录制每个测试方法的执行结果，CHECKING模式下会验证录制结果与实际执行结果相匹配
     */
    SnapshotTest snapshotTest() default SnapshotTest.CHECKING;

    boolean forceSaveOutput() default false;

    /**
     * 为单元测试指定的beans配置文件
     */
    String testBeansFile() default "";

    /**
     * 为单元测试指定的config配置文件
     */
    String testConfigFile() default "";

    /**
     * 是否使用测试专用时钟。测试专用时钟总是向前执行，而且每次调用都返回不同的时间
     */
    boolean useTestClock() default true;
}
```

**快照测试模式说明**：

- **`SnapshotTest.RECORDING`**: 录制模式，自动生成output目录下的数据文件（包括table数据），录制完成后会抛出 `nop.err.autotest.snapshot-finished` 异常码（这是正常流程，表示录制完成）
- **`SnapshotTest.CHECKING`**: 验证模式（默认值），验证实际结果与录制数据是否匹配
- **`SnapshotTest.NOT_USE`**: 不使用快照机制，适合简单测试

### SQL初始化
init目录下的`xxx.sql`会在自动建表之前执行，而input目录下的`xxx.sql`会在自动建表之后执行。

在sql文件中，可以使用 `@include: ../init.sql`这种方法引入其他目录下的sql文件。

## 四. 数据变体

数据驱动测试的一个非常大优势在于，它很容易实现对边缘场景的细化测试。

假设我们需要要测试一个用户账户欠费之后的系统行为。我们知道，根据用户欠费额的大小，欠费时间的长短，系统行为在某些阈值附近可能存在着很大的变化。而构造一个完整的用户消费和结算历史是非常复杂的一项工作，我们很难在数据库中构造出大量具有微妙差异的用户数据用于边缘场景测试。如果使用的是数据驱动的自动化测试框架，则我们可以将已有的测试数据复制一份，然后在上面直接做精细化调整就可以了。

NopAutoTest框架通过数据变体(Variant)的概念来支持这种细化测试。例如

```java
    @ParameterizedTest
@EnableVariants
@NopTestConfig(
    localDb = true,
    initDatabaseSchema = OptionalBoolean.TRUE,
    snapshotTest = SnapshotTest.CHECKING
)
public void testVariants(String variant){
        input("request.json",...);
        output("displayName.json5",testInfo.getDisplayName());
        }
```

在增加了`@EnableVariants`和`@ParameterizedTest`
注解之后，当我们调用input函数的时候，它读取的数据是/variants/{variant}/input目录下的数据与/input目录下的数据合并的结果。

```
/input
   /tables
      my_table.csv
   request.json
/output
   response.json
/variants
   /x
      /input
         /tables
            my_table.csv
         request.json
      /output
         response.json
   /y
      /input
     ....
```

首先，测试用户会在忽略variants配置的情况下执行，此时会录制数据到input/tables目录下。然后，在开启variant机制之后，按照每个variant会再次执行测试用例。

以testVariants的配置为例，它实际上会被执行3遍，第一遍`variant=_default`
，表示以原始的input/output目录数据来执行。第二遍执行variants/x目录下的数据，第三遍执行variants/y目录下的数据。

因为不同变体之间的数据往往相似度很高，我们没有必要完整复制原有的数据。NopAutoTest测试框架在这里采用了可逆计算理论的统一设计，可以利用Nop平台内置的delta差量合并机制来实现配置简化。例如在/variants/x/input/request.json文件中

```json
{
  "x:extends": "../../input/request.json"
  "amount": 300
}
```

`x:extends`是可逆计算理论引入的标准差量扩展语法，它表示从原始的request.json继承，只是将其中的amount属性修改为300。

类似的，对于`/input/tables/my_table.csv`中的数据，我们可以只在其中增加主键列和需要被定制的列，然后其中的内容会和原始目录下的对应文件自动合并。例如

```csv
SID, AMOUNT
1001, 300
```

整个Nop平台都是基于可逆计算原理而从头开始设计并实现的，关于它的具体内容可以参见文末的参考文档。

数据驱动测试在某种程度上也体现了可逆计算的所谓可逆性要求，即我们已经通过DSL（json数据以及匹配模板）表达的信息，可以被反向析取出来，然后通过再加工转换为其他信息。例如，当数据结构或者接口发生变化的情况下，我们可以通过编写统一的数据迁移代码，将测试用例数据迁移到新的结构下，而无需重新录制测试用例。

## 五. 作为DSL载体的Markdown

可逆计算理论强调通过描述式的DSL来取代一般的命令式程序编码，从而在各个领域、各个层面降低业务逻辑所对应的代码量，通过体系化的方案落地低代码。

在测试数据表达和验证方面，除了使用json/yaml等形式之外，也可以考虑采用更加接近文档形式的Markdown格式。

在XLang语言的测试中，我们规定了一个标准化的markdown结构用于表达测试用例

```markdown
# 测试用例标题

具体说明文字，可以采用一般的markdown语法，测试用例解析时会自动忽略这些说明
‘’‘测试代码块的语言
测试代码
’‘’

* 配置名: 配置值
* 配置名: 配置
```

具体实例可以参见TestXpl的测试用例 [TestXpl](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-xlang/src/test/resources/io/nop/xlang/xpl/xpls)

## 其他注解

### @NopTestConfig

在测试类上可以通过@NopTestConfig注解控制测试用例中的初始化过程。使用@NopTestConfig注解需要从JunitAutoTestCase或者JunitBaseTestCase类继承。
这两个基类的区别在于JunitBaseTestCase不是使用录制回放机制（相当于 `snapshotTest=NOT_USE`），仅仅是启动NopIoC容器。

```java
public @interface NopTestConfig {
    /**
     * 是否强制设置nop.datasource.jdbc-url为h2内存数据库。SnapshotTest设置为Checking的时候总是强制使用localDb运行，这里的配置无效。
     */
    boolean localDb() default true;

    /**
     * 是否自动根据ORM模型定义初始化数据库表结构。如果是快照验证阶段，则缺省为true。但是这里可以强制覆盖这个行为
     */
    OptionalBoolean initDatabaseSchema() default OptionalBoolean.NOT_SET;

    /**
     * 启用nop-config模块的Config管理机制
     */
    OptionalBoolean enableConfig() default OptionalBoolean.NOT_SET;

    /**
     * 启用NopIoc容器, 如果不设置，则平台内置为true
     */
    OptionalBoolean enableIoc() default OptionalBoolean.NOT_SET;

    OptionalBoolean enableActionAuth() default OptionalBoolean.NOT_SET;

    OptionalBoolean enableDataAuth() default OptionalBoolean.NOT_SET;

    /**
     * RECORDING模式下会录制每个测试方法的执行结果，CHECKING模式下会验证录制结果与实际执行结果相匹配
     */
    SnapshotTest snapshotTest() default SnapshotTest.CHECKING;

    boolean forceSaveOutput() default false;

    /**
     * 为单元测试指定的beans配置文件
     */
    String testBeansFile() default "";

    /**
     * 为单元测试指定的config配置文件
     */
    String testConfigFile() default "";

    /**
     * 是否使用测试专用时钟。测试专用时钟总是向前执行，而且每次调用都返回不同的时间
     */
    boolean useTestClock() default true;
}
```

**快照测试模式说明**：

- **`SnapshotTest.RECORDING`**: 录制模式，自动生成output目录下的数据文件（包括table数据），录制完成后会抛出 `nop.err.autotest.snapshot-finished` 异常码（这是正常流程，表示录制完成）
- **`SnapshotTest.CHECKING`**: 验证模式（默认值），验证实际结果与录制数据是否匹配
- **`SnapshotTest.NOT_USE`**: 不使用快照机制，适合简单测试

**两种更新快照方式的区别**：

1. **`forceSaveOutput = true`**
   - 使用录制的 input 数据（包括 table 数据）
   - 只更新 output 数据
   - 适用于：业务逻辑变化，但测试输入场景不变的情况

2. **`snapshotTest = SnapshotTest.RECORDING`**
   - 重新录制读取的 table 数据以及所有输出数据
   - 适用于：输入数据也需要更新的情况

当测试时可以通过testConfigFile引入测试专用的bean配置，在其中定义mock用的bean。

### @NopTestProperty

在测试类上可以通过@NopTestProperty注解直接指定专门针对本测试类的配置项，这样可以不用修改application.yaml文件，例如

```java
@NopTestProperty(name="my.xxx",value="true")
@NopTestProperty(name="my.yyy",value="123")
class MyTestCase extends JunitBaseTestCase{

}
```

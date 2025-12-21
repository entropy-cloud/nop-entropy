# Automated Testing

## I. Data-Driven Testing

The NopAutoTest testing framework is a data-driven testing framework, which means that in most cases you don’t need to write any code to prepare input data or verify output results. You only need to write a skeleton function and provide a set of test data files. Let’s look at an example:

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

The test case inherits from the JunitAutoTestCase class, then uses input(fileName, javaType) to read external data files and cast the data to the type specified by javaType. The specific data format is determined by the file extension, which can be json/json5/yaml, etc.

After invoking the function under test, you save the result to an external data file via output(fileName, result) instead of writing result verification code.

### 1.1 Recording Mode

When testLogin runs in recording mode, it generates the following data files:

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

Under the /input/tables directory, all database rows that were read will be recorded, with one CSV file per table.

> Even if no data was read, a corresponding empty file will still be generated. This is because in verification mode the recorded table names are used to determine which tables must be created in the test database.

If you open response.json5, you’ll see something like this:

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

Note that accessToken and refreshToken have been automatically replaced with variable matching expressions. This process requires no manual intervention by the developer.

As for the recorded nop_auth_session.csv, its contents are as follows:

```csv
_chgType,SID,USER_ID,LOGIN_ADDR,LOGIN_DEVICE,LOGIN_APP,LOGIN_OS,LOGIN_TIME,LOGIN_TYPE,LOGOUT_TIME,LOGOUT_TYPE,LOGIN_STATUS,LAST_ACCESS_TIME,VERSION,CREATED_BY,CREATE_TIME,UPDATED_BY,UPDATE_TIME,REMARK
A,@var:NopAuthSession@sid,067e0f1a03cf4ae28f71b606de700716,,,,,@var:NopAuthSession@loginTime,1,,,,,0,autotest-ref,*,autotest-ref,*,
```

The first column _chgType indicates the data change type: A - Add, U - Update, D - Delete. The randomly generated primary key has been replaced with the variable matching expression @var:NopAuthSession@sid. Meanwhile, based on information provided by the ORM model, createTime and updateTime are bookkeeping fields and are not involved in data matching verification; therefore, they are replaced with *, meaning “match any value.”

### 1.2 Verification Mode

Once the testLogin function has executed successfully, you can enable the @EnableSnapshot annotation to switch the test case from recording mode to verification mode.
In verification mode, the test case performs the following operations during setUp:

1. Adjusts configurations such as jdbcUrl to force the use of a local in-memory database (H2).
2. Loads the input/init_vars.json5 file to initialize the variable environment (optional).
3. Collects table names under input/tables and output/tables, generates corresponding CREATE TABLE statements based on the ORM model, and executes them.
4. Executes all xxx.sql scripts under the input directory to perform custom initialization on the newly created database (optional).
5. Inserts data from the input/tables directory into the database.

If the test case calls the output function during execution, the output JSON object will be compared with the recorded pattern file based on the MatchPattern mechanism. See the next section for specific comparison rules.
If you expect the test function to throw an exception, you can describe it using error(fileName, runnable):

```java
@Test
public void testXXXThrowException(){
        error("response-error.json5",()->xxx());
        }
```

During teardown, the test case automatically performs the following:

1. Compares the data changes defined under output/tables with the current state in the database to determine whether they match.
2. Executes the validation SQL defined in sql_check.yaml and compares it with the expected results (optional).

### 1.3 Updating Tests

If you later modify the code and the return results of the test case change, you can temporarily set the saveOutput attribute to true to update the recorded results under the output directory.

```java
@EnableSnapshot(saveOutput = true)
@Test
public void testLogin(){
        ....
        }
```

## II. Object Pattern Matching Based on Prefix-Guided Syntax

In the previous section, the matching conditions in the data template files contained only fixed values and variable expressions @var:xx. The variable expression adopts the so-called prefix-guided syntax (for details, see my article [DSL Layered Syntax Design and Prefix-Guided Syntax](https://zhuanlan.zhihu.com/p/548314138)), which is an extensible DSL design. First, note that the @var: prefix can be extended to more cases; for example, @ge:3 means greater than or equal to 3. Second, this is an open-ended design. We can add more syntax at any time and ensure that there will be no syntax conflicts among them. Third, this is a localized embedded syntax: the String->DSL transformation can enhance any string into an executable expression, such as expressing field matching conditions in CSV files. Let’s look at a more complex matching configuration:

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

In this example, @prefix introduces complex and/or matching conditions. Similarly, we can introduce conditional branches such as if and switch.

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

testExpr is an XLang expression, where matchState corresponds to the current matching context object. You can access the currently matched data node via value. Depending on the return value, the true or false branch will be chosen for matching.

Here, "@prefix" corresponds to the explode mode of the prefix-guided syntax, which expands the DSL into a JSON-form abstract syntax tree. If, due to data structure constraints, inline JSON is not allowed (for example, when used in a CSV file), you can still use the standard form of the prefix-guided syntax.

```
@if:{testExpr:'xx',true:{...},false:{...}}
```

Just JSON-encode the parameters for if into a string, then prepend the @if: prefix.

The syntactic design of the prefix-guided syntax is very flexible and does not require different prefixes to share exactly the same format. For example, @between:1,5 means greater than or equal to 1 and less than or equal to 5. The data format following the prefix is recognized only by the parser corresponding to that prefix, allowing you to design simplified syntax as needed.

If you only need to verify that part of an object’s fields satisfy the matching conditions, you can use the symbol * to ignore other fields:

```json
{
  "a": 1,
  "*": "*"
}
```

## III. Multi-Step Correlated Tests

If you need to test multiple related business functions, you must pass correlation information between them. For example, after logging in you obtain an accessToken, then use the accessToken to get detailed user information, and after completing other operations you pass the accessToken as a parameter to call logout.

Because there is a shared AutoTestVars context environment, business functions can pass correlation information automatically via AutoTestVariable. For example:

```java
    @EnableSnapshot
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

The contents of 2_userRequest.json5 are:

```json
{
  data: {
    accessToken: "@var:accessToken"
  }
}
```

You can use @var:accessToken to reference the accessToken variable returned by the previous step.

### Integration Test Support

In integration testing scenarios, if the underlying engine cannot automatically identify and register AutoTestVariable, you can register it manually in the test case:

```java
public void testXXX(){
        ....
        response=myMethod(request);
        setVar("v_myValue",response.myValue);
        // Subsequent input files can reference the variable defined here via @var:v_myValue
        request2=input("request2.json",Request2.class);
        ...
        }
```

In integration testing, you need to access an externally deployed test database and can no longer use a local in-memory database. In this case, you can set localDb=false to disable the local database:

```java
@Test
@EnableSnapshot(localDb = false)
public void integrationTest(){
        ...
        }
```

EnableSnapshot provides multiple switches so you can flexibly choose which automated testing support to enable:

```java
public @interface EnableSnapshot {

    /**
     * If the snapshot mechanism is enabled, by default it forces the use of a local database
     * and uses the recorded data to initialize the database.
     */
    boolean localDb() default true;

    /**
     * Whether to automatically execute SQL files under the input directory
     */
    boolean sqlInput() default true;

    /**
     * Whether to automatically insert data from the input/tables directory into the database
     */
    boolean tableInit() default true;

    /**
     * Whether to save collected output data to the results directory.
     * When saveOutput=true, the setting of checkOutput will be ignored.
     */
    boolean saveOutput() default false;

    /**
     * Whether to verify that the recorded output data matches the current data in the database
     */
    boolean checkOutput() default true;
}
```

### SQL Initialization
Files xxx.sql under the init directory are executed before automatic table creation, while xxx.sql under the input directory are executed after automatic table creation.

In SQL files, you can include SQL files from other directories using @include: ../init.sql.

## IV. Data Variants

One major advantage of data-driven testing is that it makes it easy to refine tests for edge scenarios.

Suppose you need to test system behavior when a user account is in arrears. We know that, depending on the amount owed and how long the arrears have lasted, system behavior can change significantly around certain thresholds. Constructing a complete user consumption and settlement history is very complex, and it is hard to construct a large volume of user data with subtle differences in the database for edge-case testing. With a data-driven automated testing framework, you can copy existing test data and directly make fine-grained adjustments.

The NopAutoTest framework supports such refined testing through the concept of data variants (Variant). For example:

```java
    @ParameterizedTest
@EnableVariants
@EnableSnapshot
public void testVariants(String variant){
        input("request.json",...);
        output("displayName.json5",testInfo.getDisplayName());
        }
```

After adding the @EnableVariants and @ParameterizedTest annotations, when you call the input function, it reads the merged result of /variants/{variant}/input and /input.

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

First, the test will run while ignoring the variants configuration, and data will be recorded under input/tables. Then, after enabling the variant mechanism, the test case will be executed again for each variant.

Using the configuration of testVariants as an example, it will actually be executed three times. The first time, variant=_default, means running with the original input/output directory data. The second run uses the data under variants/x, and the third run uses variants/y.

Because data among different variants is often highly similar, there is no need to fully copy the original data. The NopAutoTest testing framework here adopts a unified design based on Reversible Computation and leverages the Nop platform’s built-in Delta merge mechanism to simplify configuration. For example, in /variants/x/input/request.json:

```json
{
  "x:extends": "../../input/request.json"
  "amount": 300
}
```

x:extends is the standard Delta extension syntax introduced by Reversible Computation theory. It means to inherit from the original request.json while only changing the amount property to 300.

Similarly, for the data in /input/tables/my_table.csv, you can include only the primary key column and the columns you need to customize; its contents will be automatically merged with the corresponding file under the original directory. For example:

```csv
SID, AMOUNT
1001, 300
```

The entire Nop platform has been designed and implemented from the ground up based on the principles of Reversible Computation. For details, see the references at the end.

To some extent, data-driven testing also embodies the so-called reversibility requirement of Reversible Computation: the information expressed via DSL (JSON data and matching templates) can be factored back and then transformed into other information. For example, when data structures or interfaces change, we can write unified data migration code to migrate test case data to the new structure without re-recording test cases.

## V. Markdown as a DSL Carrier

Reversible Computation emphasizes using declarative DSLs to replace general imperative programming, thereby reducing the amount of code corresponding to business logic across domains and layers and implementing low-code through systematic solutions.

For expressing test data and verification, in addition to formats such as JSON/YAML, you can also use documentation-like Markdown.

In XLang tests, we define a standardized Markdown structure to express test cases:

```markdown
# Test Case Title

Explanatory text can use regular markdown syntax; the test case parser will automatically ignore these explanations
‘’‘language of the test code block
test code
’‘’

* Setting Name: Setting Value
* Setting Name: Configuration
```

For concrete examples, see TestXpl test cases [TestXpl](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-xlang/src/test/resources/io/nop/xlang/xpl/xpls)

## Other Annotations

### @NopTestConfig

On the test class, you can control the initialization process in the test case via the @NopTestConfig annotation. Using @NopTestConfig requires inheriting from either JunitAutoTestCase or JunitBaseTestCase.
The difference between these two base classes is that JunitBaseTestCase does not use the record-and-replay mechanism; it only starts the NopIoC container.

```java
public @interface NopTestConfig {
    /**
     * Whether to forcefully set nop.datasource.jdbc-url to an H2 in-memory database
     */
    boolean localDb() default false;

    /**
     * Use a randomly generated server port
     */
    boolean randomPort() default false;

    /**
     * By default, run unit tests in lazy mode
     */
    BeanContainerStartMode beanContainerStartMode() default BeanContainerStartMode.ALL_LAZY;

    String enableActionAuth() default "";

    String enableDataAuth() default "";

    /**
     * Whether to automatically load xxx.beans configurations under /nop/auto-config/
     */
    boolean enableAutoConfig() default true;

    boolean enableMergedBeansFile() default true;

    String autoConfigPattern() default "";

    String autoConfigSkipPattern() default "";

    /**
     * Whether to automatically load app.beans.xml under modules
     */
    boolean enableAppBeansFile() default true;

    String appBeansFilePattern() default "";

    String appBeansFileSkipPattern() default "";

    /**
     * Beans configuration file specified for unit tests
     */
    String testBeansFile() default "";

    /**
     * Config configuration file specified for unit tests
     */
    String testConfigFile() default "";

    boolean initDatabaseSchema() default false;
}
```

During testing, you can include test-specific bean configurations via testConfigFile and define beans for mocks there.

### @NopTestProperty

On the test class, you can directly specify properties specific to this test class using the @NopTestProperty annotation, so you don’t need to modify application.yaml. For example:

```java
@NopTestProperty(name="my.xxx",value="true")
@NopTestProperty(name="my.yyy",value="123")
class MyTestCase extends JunitBaseTestCase{

}
```
<!-- SOURCE_MD5:d45f55685d8eda9120dfcd1117be1b5c-->

  
  # Automatic Testing
  
  ## 1. Data-Driven Testing
  
  The NopAutoTest framework is a data-driven testing framework, which means that in most cases, you do not need to write any code for preparing input data or verifying output results. Instead, you only need to write a skeleton function and provide a set of test data files. Let's look at an example:

  [nop-auth/nop-auth-service/src/test/io/nop/auth/service/TestLoginApi.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-auth/nop-auth-service/src/test/java/io/nop/auth/service/TestLoginApi.java)

  [nop-auth/nop-auth-service/cases/io/nop/auth/service/TestLoginApi](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-auth/nop-auth-service/cases/io/nop/auth/service/TestLoginApi)

  ```java
  class TestLoginApi extends JunitAutoTestCase {
      // @EnableSnapshot
      @Test
      public void testLogin() {
          LoginApi loginApi = buildLoginApi();

          // ApiRequest<LoginRequest> request = request("request.json5", LoginRequest.class);
          ApiRequest<LoginRequest> request = input("request.json5", new TypeReference<ApiRequest<LoginRequest>>() {
          }).getType();

          ApiResponse<LoginResult> result = loginApi.login(request);

          output("response.json5", result);
      }
  }
  ```

  Test cases are inherited from the JunitAutoTestCase class and use `input(fileName, javaType)` to read external data files and convert them into the specified type. The data format is determined by the file extension, which can be json/json5/yaml等。

  After calling the tested function, the result data is saved to an external data file using `output(fileName, result)` instead of writing validation code.

  ### 1.1 Recording Mode

  When testLogin is executed in recording mode, it generates the following data files:

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

  The `/input/tables` directory will record all database records, with each table corresponding to a CSV file.

  > Even if no data is read, it will generate corresponding empty files. This is because in validation mode, the table names recorded during recording need to be created in the test database.

  If response.json5 is opened, you can see the following content:

  
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

Notice that `accessToken` and `refreshToken` have been automatically replaced with variable matching expressions. This process requires no manual intervention by developers.

Regarding the recorded `nop_auth_session.csv`, its content is as follows:

```csv
_chgType,SID,USER_ID,LOGIN_ADDR,LOGIN_DEVICE,LOGIN_APP,LOGIN_OS,LOGIN_TIME,LOGIN_TYPE,LOGOUT_TIME,LOGOUT_TYPE,LOGIN_STATUS,LAST_ACCESS_TIME,VERSION,CREATED_BY,CREATE_TIME,UPDATED_BY,UPDATE_TIME,REMARK
A,@var:NopAuthSession@sid,067e0f1a03cf4ae28f71b606de700716,,,,,@var:NopAuthSession@loginTime,1,,,,,0,autotest-ref,*,autotest-ref,*,
```

The first column `chgType` indicates change types: A - addition, U - modification, D - deletion. The primary key, which is randomly generated, has been replaced with a variable matching expression `@var:NopAuthSession@sid`.

Additionally, based on the ORM model, fields like `createTime` and `updateTime` are recorded in the system as log fields and thus do not require data matching validation, hence they are marked with an asterisk (*) to indicate any value.

### 1.2 Validation Mode

After the `testLogin` function successfully executes, we can enable the `@EnableSnapshot` annotation to convert the recorded test case from recording mode to validation mode.

In validation mode:
- During the `setUp` phase, perform the following actions:

1. Adjust configurations such as `jdbcUrl` and enforce the use of a local H2 database.
2. Load the `input/init_vars.json5` file to initialize variable environments (optional).
3. Collect table names from the `input/tables` and `output/tables` directories, generate corresponding creation statements based on the ORM model, and execute them.
4. Execute all `xxx.sql` scripts in the `input` directory for customized database initialization (optional).
5. Insert data from the `input/tables` directory into the database.

If the `output` function is called during test execution, the matching will be performed based on the `MatchPattern` mechanism using the recorded JSON object and the `nop_auth_session.csv` file. Specific matching rules are detailed in the next section.

If an exception is expected to be thrown by the `testXXXThrowException` method, it can be described using the `error` function:

```java
@Test
public void testXXXThrowException() {
    error("response-error.json5",()->xxx());
}
```

In the `tearDown` phase, the test case will automatically perform the following actions:

1. Compare the data changes in the `output/tables` directory with the current database state to ensure consistency.
2. Execute SQL checks defined in `sql_check.yaml`, comparing them with expected results (optional).

### 1.3 Test Update

If the code is later modified, the test result will change accordingly. We can temporarily set `saveOutput` to `true` and update the recorded output in the `output` directory.

```java
@EnableSnapshot(saveOutput = true)
@Test
public void testLogin() {
    // ... 
}
```

## 2. Object Matching Based on Prefix Syntax

In the previous section, the data template file used for matching only contained fixed values and variable expressions `@var:xx`. This section will elaborate on object matching based on prefix syntax.

  
  Two types of variable expressions were adopted, utilizing so-called prefix-based syntax (detailed explanation can be found in my article [DSL Layer Design and Prefix Syntax](https://zhuanlan.zhihu.com/p/548314138))
），which is a highly customizable domain-specific syntax (DSL) design. First, we observe that the `@var:` prefix can be extended to accommodate more scenarios, such as `@ge:3`, which means "greater than or equal to 3". This is an open-ended design.
  
  We can add more syntax support at any time and ensure no syntax conflicts arise between them***. Thirdly, this is a localized embedded syntax design, where `String->DSL` converts any string into executable expressions, for example, field matching conditions in CSV files. Let's examine a more complex matching configuration:
  
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
  
  This example introduces a complex structure of `@prefix`-based matching conditions. Similarly, we can introduce condition branches like `if`, `switch`, etc.
  
  ```json
  {
    "@prefix": "if",
    "testExpr": "matchState.value.type == 'a'",
    "true": {
      // True case implementation
    },
    "false": {
      // False case implementation
    }
  }
  ```

  
  `testExpr` is an XLang expression, where `matchState` corresponds to the current matching context and can be accessed via `value`. Depending on the return value, either the true or false branch will be selected.
  
  Here, `@prefix` corresponds to the prefix-based syntax's `explode` mode, which unfolds the DSL into a JSON-formatted abstract syntax tree. Due to data structure limitations, direct embedding of JSON is not allowed, but we can still use the standard form of prefix-based syntax in configurations like CSV files:
  
  ```json
  @if:{testExpr:'xx', true:{...}, false:{...}}
  ```
  
  By encoding the `if` parameters into JSON strings and prepending `@if:`, we can achieve this. The prefix-based syntax offers highly flexible grammar design, where no strict unification of different prefixes is required. For example, `@between:1,5` means "greater than or equal to 1 and less than or equal to 5".
  
  If only specific fields need to be validated against matching conditions, we can use `*` to indicate ignoring other fields:
  
  ```json
  {
    "a": 1,
    "*": "*"
  }
  ```
  
  ## Three. Multi-step Related Tests
  
  To test multiple interconnected business functions, we need to pass association information between multiple business functions. For example, after logging in, we obtain `accessToken`, then use it to retrieve detailed user information, perform other business operations, and pass `accessToken` as a parameter for logout.
  
  Since shared context environments like `AutoTestVars` exist, business functions can automatically pass association information via `AutoTestVariable`. For example:
  
  ```
  // Example of passing test variables
  ```

  
  ```java
  @EnableSnapshot
  @Test
  public void testLoginLogout() {
      LoginApi loginApi = buildLoginApi();

      ApiRequest<LoginRequest> request = request("1_request.json5", LoginRequest.class);

      ApiResponse<LoginResult> result = loginApi.login(request);
      output("1_response.json5", result);

      ApiRequest<AccessTokenRequest> userRequest = request("2_userRequest.json5", AccessTokenRequest.class);
      ApiResponse<LoginUserInfo> userResponse = loginApi.getLoginUserInfo(userRequest);
      output("2_userResponse.json5", userResponse);

      ApiRequest<RefreshTokenRequest> refreshTokenRequest = request("3_refreshTokenRequest.json5", RefreshTokenRequest.class);
      ApiResponse<LoginResult> refreshTokenResponse = loginApi.refreshToken(refreshTokenRequest);
      output("3_refreshTokenResponse.json5", refreshTokenResponse);

      ApiRequest<LogoutRequest> logoutRequest = request("4_logoutRequest.json5", LogoutRequest.class);
      ApiResponse<Void> logoutResponse = loginApi.logout(logoutRequest);
      output("4_logoutResponse.json5", logoutResponse);
  }
  ```
  
  Where `2_userRequest.json5` contains:

  ```json
  {
    "data": {
      "accessToken": "@var:accessToken"
    }
  }```

  We can use `@var:accessToken` to reference the `accessToken` variable returned from the previous step.

### Integration Test Support

In an integration test scenario, if we cannot rely on the underlying engine to automatically identify and register `AutoTestVariable`, we can manually register it within the test case:

```java
public void testXXX() {
    ....
    response = myMethod(request);
    setVar("v_myValue", response.myValue);
    // Subsequent input files can then reference this variable using @var:v_myValue
    request2 = input("request2.json5", Request2.class);
    ...
}
```

In an integration test scenario, we need to access an externally deployed test database rather than using the local in-memory database. At this time, we can configure `localDb = false` to disable the local database:

```java
@Test
@EnableSnapshot(localDb = false)
public void integrationTest() {
    ....
}
```

`EnableSnapshot` offers multiple controls, allowing flexible selection of automated test support.
  
  The `init` directory's `xxx.sql` will be executed before the database initialization, while the `input` directory's `xxx.sql` will be executed after the database initialization.
  
  In SQL files, you can use the `@include: ../init.sql` method to include other directories' SQL files.
  
  ## Four. Data Variant
  
  One of the most significant advantages of data-driven testing is its ability to easily perform detailed testing of edge cases.
  
  Suppose we need to test the system behavior when a user account falls into arrears. We know that the size and duration of the overdue amount can significantly affect the system's behavior near certain thresholds. Constructing a complete history of a user's consumption and settlement is a very complex task, and it's difficult to create a large number of user data with subtle differences in databases for edge case testing. If a data-driven automated testing framework is used, we can simply copy existing test data and fine-tune it as needed.
  
  The NopAutoTest framework supports this kind of detailed testing by leveraging the concept of `data variant` (variant). For example:
  
  ```java
  @ParameterizedTest
  @EnableVariants
  @EnableSnapshot
  public void testVariants(String variant) {
      input("request.json", ...);
      output("displayName.json5", testInfo.getDisplayName());
  }
  ```
  
  After adding `@EnableVariants` and `@ParameterizedTest` annotations, calling the `input()` function will read data from `/variants/{variant}/input/` and `/input/` directories.
  
  Below is an example of how data is organized and merged:
  
  ```plaintext
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
        /tables
          my_table.csv
        request.json
      /output
        response.json
  ```
  
  First, the test runs without considering `variants` settings, and data is recorded into `input/tables` directory. Then, with `EnableSnapshot` enabled, each variant will trigger a separate test case.
  
  For example, `testVariants("default")` will execute three times:
  1. With `variant = "default"` (using the default value), merging data from `/variants/default/input/tables/` and `/input/tables/`.
  2. With `variant = "x"`, merging data from `/variants/x/input/tables/` and `/input/tables/`.
  3. With `variant = "y"`, merging data from `/variants/y/input/tables/` and `/input/tables/`.
  
  Since the data across different variants often has high similarity, there's no need to create an extensive dataset. The NopAutoTest framework here employs a unified design based on reversible computing theory and utilizes the platform's built-in delta merging mechanism for configuration simplification. For instance:
  
  ```json
  {
    "x:extends": "../../input/request.json",
    "amount": 300
  }
  ```
  
  Here, `x:extends` is a reversible computing theory-introduced extension syntax that allows inheritance from the original `request.json`, but modifies the `amount` attribute to 300.

 
 Similar to this, for data in `/input/tables/my_table.csv`, we can simply add the primary key column and any custom columns needed, after which the data will automatically merge with the corresponding files in the original directory. For example:

```csv
SID, AMOUNT
1001, 300
```

The entire Nop platform is designed and implemented from scratch based on the principles of reversible computing. For detailed information about it, please refer to the reference document at the end of this document.

In some way, data-driven testing also demonstrates the so-called reversibility requirement of reversible computing. The information expressed through DSL (JSON data and matching templates) can be reversed back, and through further processing, it can be converted into other types of information. For instance, when the data structure or interface changes, we can write unified data migration code to move test case data to the new structure without re-recording test cases.

## Five. Markdown as a DSL Carrier

Reversible computing theory emphasizes using descriptive DSL (data description language) to replace general command-driven programming. This reduces the amount of business logic-related code across various domains and levels. By standardizing the implementation through systemic approaches, we can achieve low-code development.

In addition to using formats like JSON/YAML, we can also consider using Markdown, which is more akin to document-like structures.

In testing data expression and validation, besides using JSON/yaml formats, Markdown format can be a better choice for certain scenarios. For example, in XLang language testing, we have established a standardized Markdown structure for expressing test cases:

```markdown
# Test Case Title

Specific descriptions are written using general Markdown syntax. During test case analysis, these explanations will be automatically ignored.
```

`Testing code block's language`
`Test code`
```

* Configuration Name: Configuration Value
* Configuration Name: Configuration
```

For specific examples, please refer to the TestXpl test cases [TestXpl](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-xlang/src/test/resources/io/nop/xlang/xpl/xpls).

## Other Annotations

### @NopTestConfig

The `@NopTestConfig` annotation can be used on test classes to control the initialization process of test cases. Using this annotation requires inheriting from either `JunitAutoTestCase` or `JunitBaseTestCase`. The main difference between these two base classes is that `JunitBaseTestCase` does not use recording-and-replay mechanisms; it only initializes the NopIoC container.

  
  ```java
  public @interface NopTestConfig {
      /**
       * Whether to set nop.datasource.jdbc-url to an H2 in-memory database
       */
      boolean localDb() default false;

      /**
       * Whether to use a randomly generated server port
       */
      boolean randomPort() default false;

      /**
       * Whether to execute unit tests with the lazy mode by default
       */
      BeanContainerStartMode beanContainerStartMode() default BeanContainerStartMode.ALL_LAZY;

      String enableActionAuth() default "";

      String enableDataAuth() default "";

      /**
       * Whether to automatically load configurations from the /nop/auto-config/ directory under xxx.beans
       */
      boolean enableAutoConfig() default true;

      boolean enableMergedBeansFile() default true;

      String autoConfigPattern() default "";

      String autoConfigSkipPattern() default "";

      /**
       * Whether to automatically load app-beans.xml configuration files from modules
       */
      boolean enableAppBeansFile() default true;

      String appBeansFilePattern() default "";

      String appBeansFileSkipPattern() default "";

      /**
       * Whether to automatically load module-based app.beans.xml configurations
       */
      boolean enableModuleConfigurations() default true;

      String testBeansFile() default "";

      String testConfigFile() default "";

      boolean initDatabaseSchema() default false;
  }
```

### @NopTestProperty

The `@NopTestProperty` annotation can be used directly on test classes to specify configuration options tailored to the test class, allowing you to avoid modifying the `application.yaml` file. For example:

```java
@NopTestProperty(name = "my.xxx", value = "true")
@NopTestProperty(name = "my.yyy", value = "123")
class MyTestCase extends JunitBaseTestCase {
}
```


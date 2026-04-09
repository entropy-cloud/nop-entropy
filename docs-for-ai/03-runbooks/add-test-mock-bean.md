# 在测试中补充 mock bean

## 适用场景

- 测试启动 IoC 容器时，缺少某个接口实现。
- 需要替代 HTTP、搜索、消息等外部依赖。

## AI 决策提示

- 优先在 `src/test/resources/_vfs/...` 下增加测试专用 beans 文件。
- bean id 使用 `testMock` 前缀，避免污染生产命名。
- 用 `@NopTestConfig(testBeansFile = ...)` 引入，不要改生产 beans。
- 如果待测对象支持 setter，测试里直接注入 mock 往往更稳。

## 最小闭环

### 1. 在测试源码中创建 mock 类

```java
public class MockHttpClient implements IHttpClient {
    // 返回固定结果
}
```

### 2. 在测试资源中创建 beans 文件

典型位置：`src/test/resources/_vfs/.../test-mock.beans.xml`

```xml
<beans xmlns:x="/nop/schema/xdsl.xdef"
       xmlns:ioc="urn: nop-ioc:1.0">
    <bean id="testMockHttpClient"
          class="io.nop.xxx.mock.MockHttpClient"
          ioc:type="io.nop.http.api.client.IHttpClient"/>
</beans>
```

### 3. 在测试类中引用

```java
@NopTestConfig(testBeansFile = "/nop/xxx/beans/test-mock.beans.xml")
public class MyTest extends JunitBaseTestCase {
}
```

### 4. 真实仓库参考

当前仓库里已经有这套模式：

1. `nop-ai/nop-ai-toolkit/src/test/resources/_vfs/nop/ai/beans/test-mock.beans.xml`
2. `nop-ai/nop-ai-toolkit/src/test/java/io/nop/ai/toolkit/tools/HttpRequestExecutorTest.java`

## 常见坑

1. bean id 与生产 bean 重名。
2. 使用 `ioc:default="true"` 导致默认 bean 冲突。
3. 把 mock 类和 test beans 放进生产目录。
4. 忘记在测试注解里声明 `testBeansFile`。

## 相关文档

- `./write-integration-test-with-noptestconfig.md`
- `./write-tests.md`
- `../02-core-guides/testing.md`

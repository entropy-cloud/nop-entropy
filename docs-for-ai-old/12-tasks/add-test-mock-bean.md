# 在测试中补充 mock bean

## 适用场景

- 测试启动 IoC 容器时，缺少某些接口实现
- 需要在测试中替代外部依赖，如 HTTP、搜索、消息等

## AI 决策提示

- ✅ 优先用测试专用 beans 文件注册 mock bean
- ✅ Bean ID 用 `testMock` 前缀
- ✅ 测试内优先 setter 手动注入 mock，便于控制行为
- ❌ 不要污染生产 beans 配置

## 最小闭环

### 1. 创建 mock 类

```java
public class MockHttpClient implements IHttpClient {
    // 返回固定结果
}
```

### 2. 创建测试 beans 文件

```xml
<beans>
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

## 常见坑

- ❌ Bean ID 与生产 bean 重名
- ❌ 使用 `ioc:default="true"` 导致默认 bean 冲突
- ❌ 把 mock 类放进生产源码目录

## 相关文档

- `07-best-practices/testing.md`
- `12-tasks/write-integration-test-with-noptestconfig.md`

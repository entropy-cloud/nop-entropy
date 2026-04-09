# Testing

本页只保留 Nop 平台测试的默认原则和入口，不再承载大段通用测试教程。

---

## 默认原则

1. 需要容器、数据库、配置、`_vfs` 时，优先 `@NopTestConfig`
2. 需要快照录制/校验时，优先 `JunitAutoTestCase`
3. 普通进程内集成测试，优先 `JunitBaseTestCase`
4. `@Inject` 字段不能是 `private`
5. 外部依赖优先用测试专用 bean 或手写 stub/fake

---

## 首选入口

- `../11-test-and-debug/autotest-guide.md`
- `../12-tasks/write-unit-test.md`
- `../12-tasks/write-integration-test-with-noptestconfig.md`
- `../12-tasks/add-test-mock-bean.md`
- `../04-core-components/ioc-container.md`

---

## 不再推荐写进 docs-for-ai 的内容

1. 大段通用 JUnit 教程
2. 没有 Nop 平台上下文的 fixture/DAO 示例
3. 把第三方 mock 框架当成 Nop 默认方案

---

## 相关文档

- `../11-test-and-debug/autotest-guide.md`
- `../12-tasks/write-unit-test.md`

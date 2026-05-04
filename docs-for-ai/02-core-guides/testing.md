# 测试默认模式

本页只保留当前仓库里最适合 AI 使用的测试结论。

## 默认原则

1. 需要容器、数据库、配置、`_vfs` 时，优先 `@NopTestConfig`。
2. 需要快照录制/校验时，优先 `JunitAutoTestCase`。
3. 普通进程内集成测试，优先 `JunitBaseTestCase`。
4. `@Inject` 字段不能是 `private`。
5. 外部依赖优先使用测试专用 bean、stub 或 fake，而不是默认从 HTTP E2E 开始。

## 何时选哪种基类

| 场景 | 默认基类 |
|------|---------|
| 需要录制和校验 `_cases/` 快照 | `JunitAutoTestCase` |
| 不需要快照，只需要容器内测试 | `JunitBaseTestCase` |

## `@NopTestConfig` 的关键点

边界先记住：

1. `JunitAutoTestCase` 需要类级别 `@NopTestConfig`。
2. `JunitBaseTestCase` 不强制要求 `@NopTestConfig`；仓库里也存在不加该注解的普通测试。
3. 只有需要本地库、测试配置、测试 beans 或快照相关能力时再加。

当前仓库里的 `@NopTestConfig` 至少控制这些能力：

- `localDb`
- `initDatabaseSchema`
- `enableConfig`
- `enableIoc`
- `snapshotTest`
- `forceSaveOutput`
- `testBeansFile`
- `testConfigFile`

## 快照测试的默认工作流

1. 首次录制：`snapshotTest = SnapshotTest.RECORDING`
2. 日常验证：默认 `CHECKING`
3. 只更新输出时：`forceSaveOutput = true`

实操里最常用的 helper：

1. `input(...)`
2. `request(...)`
3. `output(...)`
4. `outputText(...)`

录制模式下，框架在保存快照后会抛出一个表示“录制完成”的专用异常，这是正常流程，不要误判成普通业务失败。

## 测试数据位置

| 测试类型 | 数据位置 |
|---------|---------|
| `JunitAutoTestCase` 快照测试 | `_cases/...` |
| `JunitBaseTestCase` 普通资源 | `src/test/resources/...` |

## 当前仓库里的真实入口

| 能力 | 路径 |
|------|------|
| `JunitAutoTestCase` | `nop-autotest/nop-autotest-junit/src/main/java/io/nop/autotest/junit/JunitAutoTestCase.java` |
| `JunitBaseTestCase` | `nop-autotest/nop-autotest-junit/src/main/java/io/nop/autotest/junit/JunitBaseTestCase.java` |
| `@NopTestConfig` | `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/annotations/autotest/NopTestConfig.java` |

## 常见坑

1. `JunitAutoTestCase` 忘记加 `@NopTestConfig`。
2. `@Inject private` 导致注入失效。
3. 快照测试把数据放错目录。
4. 明明是进程内服务测试，却先去搭 HTTP E2E。

## E2E 测试（Playwright）

`nop-code/nop-code-e2e/` 是基于 pnpm + Vite 8 + Playwright 的 E2E 测试模块。

### 启动被测应用

```bash
# 1. 先构建 nop-code-app
cd nop-code && ../mvnw clean install -DskipTests -T 1C

# 2. 启动（免认证模式）
java -Dquarkus.profile=dev -Dnop.auth.service-public=true \
  -jar nop-code-app/target/quarkus-app/quarkus-run.jar
```

### 运行 E2E 测试

```bash
cd nop-code/nop-code-e2e
pnpm install
pnpm exec playwright install chromium
pnpm test
```

### Nop RPC 调用模式（Playwright 中）

```typescript
// POST /r/{operation}，JSON body 参数平铺（不包裹 data）
const resp = await request.post('/r/NopCodeTypeHierarchy__getTypeHierarchy', {
  data: { indexId: 'test', qualifiedName: 'io.nop.Foo', direction: 'super', maxDepth: 3 },
});
const json = await resp.json();
// json.status === 0 表示成功, json.data 是返回数据
```

### 页面 JSON 获取

```typescript
// 获取 AMIS 页面 schema
const resp = await request.get('/p/PageProvider__getPage?path=/nop/code/pages/xxx/main.page.yaml');
```

### 浏览器 E2E 测试要点

1. **登录**：SPA 前端需要登录才能访问页面（`nop`/`123`）
2. **页面 URL**：`/#/type-hierarchy-main`（不是 `/#/page?pagePath=...`），URL 格式为 `/#/{pageId}`
3. **API 路由**：AMIS 表单 `@query:` API 走 `/graphql`（POST），RPC 测试走 `/r/{operation}`
4. **字段名**：`editMode="query"` 自动加 `filter_` 前缀，`editMode="edit"` 不加前缀
5. **无 meta 表单**：必须在 view.xml `<cells>` 中配置 `domain` 和 `label`，否则字段渲染为 `static` 且无标签
6. **BizModel 命名**：方法名不得与标准 CRUD（`get`/`findPage`/`save`/`update`/`delete` 等）重名，否则前端 `@query:` API 参数被忽略

## 相关文档

- `../03-runbooks/write-tests.md`
- `../03-runbooks/write-integration-test-with-noptestconfig.md`
- `../03-runbooks/add-test-mock-bean.md`
- `../04-reference/source-anchors.md`

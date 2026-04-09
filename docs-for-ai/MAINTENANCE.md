# docs-for-ai 文档维护指南

本文档规定 `docs-for-ai` 的维护原则、文档角色、模板要求和校验方式。

如果你是 AI 助手，请不要把本文档当成开发入口。开发入口是：`INDEX.md`

---

## 一、维护目标

`docs-for-ai` 的目标不是覆盖一切知识，而是为 AI 提供：

1. 最短路径的任务入口
2. 源码可校验的默认规范
3. 不会误导 AI 的参考资料

因此，维护优先级始终是：

1. 规范正确
2. 路由清晰
3. 示例辅助
4. 目录整洁

---

## 二、文档角色

每篇文档都应有明确角色，避免示例反向覆盖规范。

| 角色 | 含义 | 是否可作为 AI 默认做法 |
|------|------|--------------------------|
| Runbook | 具体任务操作步骤 | 可以 |
| Canonical Pattern | 平台默认规范与推荐模式 | 可以 |
| Concept / Architecture | 原理说明 | 不单独作为默认做法 |
| Reference / Example | 参考、速查、样例 | 不可以 |

当前目录的推荐角色：

| 目录 | 角色 |
|------|------|
| `12-tasks/` | Runbook |
| `03-development-guide/` | Canonical Pattern |
| `04-core-components/` | Canonical Pattern |
| `11-test-and-debug/` | Canonical Pattern |
| `01-core-concepts/`、`02-architecture/`、`05-xlang/` | Concept / Architecture |
| `08-examples/`、`09-quick-reference/`、`06-utilities/` | Reference / Example |
| `13-reference/` | Reference（源码锚点） |

---

## 三、文档编写原则

### 1. 只引用 `docs-for-ai` 内部文档

不得引用仓库外部文档，也不得依赖主 `docs/` 目录。

### 2. 以源码为准

如果文档与源码冲突：

1. 先校验源码
2. 再改文档
3. 不要用“历史经验”覆盖当前实现

### 3. Java 类只写全限定名

提到源码对象时，优先写：

`io.nop.biz.crud.CrudBizModel`

不要写长源码路径作为正文说明。

### 4. 生成链路必须可核对

涉及 codegen、xmeta、xbiz、view/page 生成流程时，必须能在模板目录、生成脚本或真实模块中找到对应锚点。

### 5. 示例不能反向定义默认做法

如果一个示例使用了：

- 直接 `dao()`
- `saveEntityDirectly()`
- `@Transactional(REQUIRES_NEW)`
- store / infra 层特有写法

则必须明确说明该示例的边界，不得让 AI 误认为这是普通 BizModel 模板。

---

## 四、推荐文档模板

### 1. Runbook 模板

```markdown
# 标题

## 适用场景

## AI 决策提示

## 最小闭环

## 常见坑

## 相关文档
```

### 2. Canonical Pattern 模板

```markdown
# 标题

## 默认规则

## 推荐写法

## 反模式

## 边界场景

## 源码锚点

## 相关文档
```

### 3. Concept / Architecture 模板

```markdown
# 标题

## 这个概念解决什么问题

## 关键机制

## 与 AI 开发的关系

## 相关文档
```

### 4. Reference / Example 模板

```markdown
# 标题

> 本页是参考材料，不单独作为 AI 默认生成规范。

## 适用范围

## 内容

## 相关文档
```

---

## 五、最低质量要求

每次修改文档前，至少检查：

- [ ] 这篇文档的角色是否清晰
- [ ] 是否和 `INDEX.md` / `12-tasks/` / 规范主干冲突
- [ ] 代码示例是否与当前源码一致
- [ ] 是否包含误导 AI 的默认写法
- [ ] 内部链接是否可访问

---

## 六、重点巡检规则

### 1. 高风险模式巡检

建议定期搜索：

```bash
rg -n "dao\(\)\.(getEntityById|find(All|Page|First)?ByQuery|saveEntity|updateEntity|deleteEntity)" docs-for-ai
rg -n "@Inject\s+private|@Value\(" docs-for-ai
rg -n "gen-service\.xgen|gen-web\.xgen" docs-for-ai
```

命中后要判断：

1. 这是不是普通 BizModel 默认示例？
2. 如果不是，边界是否写清楚？

### 2. 断链巡检

此前仓库中曾存在若干历史断链，例如旧的 `common-tasks.md`、`../codegen/...`、`../delta/...` 路径。当前主路径已完成清理；后续如再发现同类旧链接，应继续按现有目录结构修正。

---

## 七、代码风格补充

文档内涉及代码示例时，遵循：

- PascalCase：类/接口
- camelCase：方法/变量
- UPPER_SNAKE_CASE：常量
- 包名：`io.nop.<module-name>.*`
- 避免 Spring 专用默认写法
- 不硬编码中文业务错误文本，优先错误码 + 参数
- 日志使用 SLF4J，不用 `System.out` / `System.err`

---

## 八、维护顺序

如需继续重构 `docs-for-ai`，优先顺序应为：

1. `INDEX.md`
2. `12-tasks/`
3. 规范主干文档
4. `source-anchors.md`
5. 示例和 quick reference
6. 最后才是目录物理迁移

---

## 九、相关文档

- `INDEX.md`
- `REFACTORING_PLAN.md`
- `DIRECTORY_STRUCTURE_OPTIONS.md`
- `13-reference/source-anchors.md`

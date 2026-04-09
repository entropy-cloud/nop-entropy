# docs-for-ai 修改维护方案

本文档用于指导 `docs-for-ai/` 的后续修改与维护。目标不是单纯调整目录名称，而是把 `docs-for-ai` 建成一套可以被 AI 稳定消费、并且能够约束 AI 生成出符合 Nop 平台最佳实践代码的文档体系。

核心判断只有一句话：

**当前 `docs-for-ai` 的首要问题不是“目录不够漂亮”，而是“权威规则没有收敛，且部分文档与源码或彼此之间存在冲突”。**

因此，推荐执行顺序是：

1. 先校准内容
2. 再收敛 AI 入口
3. 再扩充任务手册
4. 最后才考虑大规模目录迁移

---

## 一、目标

`docs-for-ai` 需要同时满足以下四个目标：

1. **AI 可路由**：AI 在 1 到 2 次文档跳转内，能找到当前任务的默认做法。
2. **源码可校验**：关键规则必须能在源码中找到稳定锚点，而不是只停留在经验描述。
3. **默认安全**：AI 按最短路径读文档时，不会被误导去写 `dao().getEntityById()`、`@BizMutation @Transactional`、`@Inject private` 这类违背平台默认约束的代码。
4. **持续可维护**：后续文档新增、修改、迁移时，不会再次出现多个“看起来都像权威入口”的文档互相打架。

---

## 二、调研结论

### 2.1 当前最主要的问题

#### 1. 权威规则分散

AI 编码时最需要的默认规则，当前分散在多个位置：

- `INDEX.md`
- `01-core-concepts/ai-development.md`
- `03-development-guide/bizmodel-guide.md`
- `03-development-guide/service-layer.md`
- `03-development-guide/crud-development.md`
- `12-tasks/write-bizmodel-method.md`

结果是：AI 不是在读取“唯一规范”，而是在多个文档之间自行裁决。

#### 2. 部分文档存在实质性冲突

已确认的高风险冲突包括：

- `INDEX.md` 将 `dao().getEntityById(id)` 定义为反模式，但 `09-quick-reference/api-reference.md` 大量把 `dao()` 访问写成默认做法。
- `12-tasks/write-bizmodel-method.md` 明确要求 BizModel 使用 `requireEntity()` / `doFindList()`，但 `04-core-components/exception-handling.md`、`09-quick-reference/api-reference.md`、`08-examples/graphql-example.md` 中仍有大量直接 `dao()` 示例。
- `01-core-concepts/ai-development.md` 与 `03-development-guide/project-structure.md` 对代码生成流向的描述不一致，而且其中一部分 `gen-service.xgen` / `gen-web.xgen` 说法已经无法在仓库中找到对应脚本。

这类冲突的危险性很高，因为 AI 往往会优先吸收“短文档”和“示例代码”。

#### 3. `INDEX.md` 过载

当前 `INDEX.md` 同时承担：

- 路由入口
- 反模式清单
- 长篇教程
- 场景讲解
- 代码示例
- 目录映射

它已经不只是“索引”，而是“把半套文档又复制了一遍”。这会导致：

- 路由能力下降
- 重复内容增多
- 修订成本升高
- AI 读完 `INDEX.md` 之后仍然需要再次判断哪部分才是规范

#### 4. 任务型文档覆盖面仍然不足

`12-tasks/` 是当前最接近“AI 直接可执行手册”的目录，但覆盖面还不够。缺少至少以下高频场景：

- 如何判断逻辑放在 Entity / BizModel / Processor / Step 哪一层
- 模型改动后如何重新生成并验证
- 如何新增跨模块 `I*Biz` 接口
- 如何创建 `@RequestBean` / `@DataBean` DTO
- 如何优先使用 Delta 而不是改基础实现
- 如何新增 dict / 常量 / 领域枚举
- 如何在测试里补齐缺失 Bean 的 mock/stub

#### 5. 目录问题存在，但不是第一优先级

当前目录确实存在边界模糊、命名不一致等问题，但如果在内容仍互相冲突时直接做大规模改名，结果只会是：

- 链接大量迁移
- 历史 blame 被打散
- 旧问题原样搬家

因此：

**目录重构要做，但必须排在“内容校准”和“权威入口收敛”之后。**

---

## 三、源码校准后的 AI 硬规则

以下规则必须在 `docs-for-ai` 中收敛为统一口径，且任何规范文档都不得与之冲突。

| 规则 | 源码锚点 | 文档要求 |
|------|----------|----------|
| 先模型，再 Delta，最后 Java | 模块内 `gen-orm.xgen`、`/nop/templates/orm`、代表性类 `io.nop.job.dao.entity.NopJobSchedule extends _NopJobSchedule` | 所有任务手册都必须优先给出模型/生成路径，而不是直接让 AI 手写 DAO / Entity / BizModel 骨架 |
| 不得手改 `_gen/` 和 `_` 前缀生成物 | 代表性模式：手写类继承 `_Xxx`，以及 `_app.orm.xml` / `_gen/*.view.xml` 被覆盖 | 在 `INDEX.md`、任务手册、示例文档中反复强调，并覆盖 Java、ORM、View 等非 Java 生成物 |
| 实体型服务默认写法是 `@BizModel + extends CrudBizModel<T> + setEntityName(...)` | `io.nop.biz.crud.CrudBizModel`、代表性实现 `io.nop.job.service.entity.NopJobScheduleBizModel` | `03-development-guide/bizmodel-guide.md` 必须成为此模式的唯一规范入口 |
| BizModel 查询/取数默认走 `requireEntity()`、`doFindList()`、`doFindPage()` | `io.nop.biz.crud.CrudBizModel#requireEntity`、`#prepareFindPageQuery` | 任何面向 AI 的默认示例都不能把 `dao().getEntityById()`、`dao().findAllByQuery()` 写成普通 BizModel 的首选写法 |
| `@BizMutation` 默认已带事务 | `io.nop.biz.service.BizActionInvoker` | BizModel 规范和事务规范必须统一为“普通 BizModel 写操作不要叠加 `@Transactional`” |
| 跨 BizModel 协作通过 `I*Biz` 接口，不直接注入另一个 BizModel 类 | 代表性模式：`INopJobScheduleBiz` 与 `NopJobScheduleBizModel` | 需要新增专门 runbook，避免 AI 直接互相注入 BizModel 实现类 |
| NopIoC 不支持 private 字段注入，配置值用 `@InjectValue` | `io.nop.core.reflect.impl.ClassModelBuilder#discoverDeclaredFields` 会跳过 `private` 字段 | IoC、测试、BizModel 示例里不得出现 `@Inject private Foo foo;` 和 Spring 风格 `@Value` 作为默认写法 |
| Delta 是默认定制机制，不是补充技巧 | `_vfs/_delta/...` 下的大量真实文件模式 | “改已有产品行为”的文档必须先给 Delta 方案，再给 Java 覆盖方案 |
| 测试优先采用 Nop 集成方式和 AutoTest/Junit 基类 | `11-test-and-debug/autotest-guide.md`、仓库内集成测试模式 | 测试文档要优先给出 Nop 的进程内集成测试/AutoTest 路径，而不是泛化的外部 HTTP 示例 |

---

## 四、文档体系的目标分层

后续 `docs-for-ai` 应按“AI 决策顺序”组织，而不是按“作者写作习惯”组织。

### 4.1 逻辑分层

| 层级 | 作用 | 当前主要承载位置 | AI 优先级 |
|------|------|------------------|-----------|
| 路由层 | 告诉 AI 先看什么 | `INDEX.md` | 最高 |
| 任务层 | 告诉 AI 这件事怎么做 | `12-tasks/` | 最高 |
| 规范层 | 告诉 AI 为什么默认这么做 | `03-development-guide/`、部分 `04-core-components/`、部分 `07-best-practices/` | 高 |
| 原理层 | 告诉 AI 平台为何这样设计 | `01-core-concepts/`、`02-architecture/`、`05-xlang/` | 中 |
| 参考层 | 提供 API、工具、示例、源码锚点 | `06-utilities/`、`08-examples/`、`09-quick-reference/`、`13-reference/` | 低 |

### 4.2 AI 文档查找顺序

后续必须统一成下面的查找顺序：

1. `INDEX.md`
2. `12-tasks/` 对应任务手册
3. `03-development-guide/` 对应规范文档
4. `01-core-concepts/` / `02-architecture/` 补原理
5. `08-examples/` / `09-quick-reference/` 只做辅助参考

换句话说：

**示例文档和快速参考文档不能再抢“默认规范”的位置。**

---

## 五、目录策略

### 5.1 第一阶段不建议大规模改目录名

建议先保留大部分现有物理路径，只调整每个目录的“职责边界”。原因：

1. 当前最大问题是内容冲突，不是路径名。
2. 直接迁移 10 个以上目录，会先制造大量断链和无意义 diff。
3. 等内容收敛之后，再做目录收缩，风险更低。

### 5.2 现有目录的目标职责

| 目录 | 目标职责 | 处理策略 |
|------|----------|----------|
| `INDEX.md` | 纯路由入口 | 大幅缩短，只保留决策树、反模式总表、首选文档入口 |
| `00-quick-start/` | 初学者快速进入 | 保持轻量，不再堆 AI 规范细节 |
| `01-core-concepts/` | 纯概念、平台不变量 | 删除过时构建细节，保留模型优先、Delta、可逆计算等概念 |
| `02-architecture/` | 框架内部实现原理 | 只保留“为什么这样实现”，不承担 AI 默认写法 |
| `03-development-guide/` | 规范主干 | 作为 AI 编码规范的主要承载层 |
| `04-core-components/` | Nop 特有组件约束 | 去掉泛化教程，保留 IoC、事务、异常等与 Nop 差异强相关的部分 |
| `05-xlang/` | XLang / XDef / XPL 相关能力 | 保持独立 |
| `06-utilities/` | 工具类参考 | 降级为参考层，不再作为默认开发规范入口 |
| `07-best-practices/` | 跨主题最佳实践 | 保留，但避免与 `03-development-guide/` 重复 |
| `08-examples/` | 示例 | 明确标注“示例不等于默认规范” |
| `09-quick-reference/` | 安全速查 | 必须重写为不误导 AI 的“安全参考” |
| `10-meta/` | 文档维护元信息 | 已合并进 `MAINTENANCE.md`，不再保留独立目录 |
| `11-test-and-debug/` | Nop 测试与调试 | 保留独立目录 |
| `12-tasks/` | AI 主入口 runbook | 扩容并成为核心目录 |
| `13-reference/` | 源码锚点与辅助索引 | 扩充 `source-anchors.md`，建立“规范到源码”的映射 |

### 5.3 长期目录收缩建议

等内容稳定后，再考虑如下物理收敛：

1. `10-meta/` 已合并进 `MAINTENANCE.md`
2. `06-utilities/`、`09-quick-reference/`、`13-reference/` 视情况收敛为统一参考层
3. 若 `04-core-components/` 与 `03-development-guide/` 分工稳定，可再决定是否继续拆分或合并

这里的重点是：

**先形成稳定的逻辑分层，再决定是否做物理迁移。**

---

## 六、文档分类与权威级别

后续每篇文档都应显式归类，避免 AI 把“示例”当“规范”。

建议采用四类角色：

| 角色 | 含义 | AI 是否可直接当默认做法 |
|------|------|--------------------------|
| Runbook | 具体任务操作步骤 | 可以 |
| Canonical Pattern | 平台规范和默认模式 | 可以 |
| Concept / Architecture | 原理说明 | 需要结合规范理解 |
| Reference / Example | API、工具、样例 | 不可单独作为默认做法 |

建议每篇核心文档统一包含以下章节：

1. 适用场景
2. 默认选择
3. 最小闭环
4. 常见反模式
5. 源码锚点
6. 相关文档

---

## 七、优先改造清单

### 7.1 P0：先去掉会误导 AI 的内容

以下内容需要最先改：

| 文件 | 当前问题 | 改造动作 |
|------|----------|----------|
| `INDEX.md` | 过长、重复、兼任教程 | 改成真正的路由页 |
| `01-core-concepts/ai-development.md` | 混入过多实现细节，且部分 codegen 流向已过时 | 收缩为“AI 默认开发原则” |
| `03-development-guide/project-structure.md` | 代码生成描述与仓库现状不一致，且存在断链 | 按真实 `gen-orm.xgen` / `gen-meta.xgen` 重写 |
| `09-quick-reference/api-reference.md` | 将大量 `dao()` 直接调用写成默认模式 | 下线或重写为“安全 API 速查” |
| `04-core-components/exception-handling.md` | 大量直接 `dao()` / `@Transactional` 示例 | 改成 BizModel 安全写法 |
| `08-examples/graphql-example.md` | 示例容易被 AI 误判为默认服务层写法 | 增加醒目标注或重写 |
| `MAINTENANCE.md` | 已收纳模板与维护规则 | 继续作为唯一维护入口 |

### 7.2 P1：补齐高频任务手册

建议新增以下 `12-tasks/` 文档：

1. `choose-entity-vs-bizmodel-vs-processor.md`
2. `change-model-and-regenerate.md`
3. `add-cross-module-biz-interface.md`
4. `create-request-response-dto.md`
5. `prefer-delta-over-direct-modification.md`
6. `add-dict-and-constants.md`
7. `add-bizloader-field.md`
8. `write-integration-test-with-noptestconfig.md`
9. `add-test-mock-bean.md`
10. `debug-codegen-and-generated-files.md`

这些 runbook 的目标不是讲概念，而是给 AI 一个可以直接执行的默认路径。

### 7.3 P2：建立规范文档主干

以下文档应成为长期稳定的规范主干：

1. `03-development-guide/bizmodel-guide.md`
2. `03-development-guide/crud-development.md`
3. `03-development-guide/service-layer.md`
4. `03-development-guide/project-structure.md`
5. `04-core-components/ioc-container.md`
6. `07-best-practices/error-handling.md`
7. `11-test-and-debug/autotest-guide.md`

处理原则：

- 保留一个主题的主规范文档
- 其他文档只做补充，不再重复定义默认规则

### 7.4 P3：参考层降权

以下目录和文档在完成清洗前，不应出现在 AI 的首选入口中：

- `08-examples/`
- `09-quick-reference/`
- `06-utilities/`

它们可以保留，但必须明确：

**这是参考材料，不是 AI 的默认生成规范。**

---

## 八、维护机制

### 8.1 建立源码锚点制度

`13-reference/source-anchors.md` 需要从“类名速查表”升级为“规范锚点表”。

建议至少覆盖以下主题：

1. `CrudBizModel` 的安全查询/取数路径
2. `BizActionInvoker` 的事务边界
3. `ClassModelBuilder` 的 private 注入限制
4. 代表性 BizModel 实现模式
5. 代表性 Delta 文件结构
6. 代表性的生成类与手写扩展类关系

每条锚点都要回答两个问题：

1. 这条源码说明了什么平台规则？
2. 文档里据此应该禁止或推荐什么写法？

### 8.2 建立“规范文档优先”约束

所有 runbook 必须引用对应的主规范文档；
所有参考文档不得重新定义与规范冲突的默认写法。

例如：

- `12-tasks/write-bizmodel-method.md` 应引用 `03-development-guide/bizmodel-guide.md`
- `09-quick-reference/api-reference.md` 如果保留，就只能收录“在 BizModel 场景下安全使用的 API”，不能反向覆盖 `bizmodel-guide`

### 8.3 建立反模式巡检

维护 `docs-for-ai` 时，应定期扫描以下高风险模式：

```bash
rg -n "dao\(\)\.(getEntityById|find(All|Page|First)?ByQuery|saveEntity|updateEntity|deleteEntity)" docs-for-ai
rg -n "@Inject\s+private|@Value\(" docs-for-ai
rg -n "gen-service\.xgen|gen-web\.xgen" docs-for-ai
```

扫描命中后不一定都要删除，但必须判断：

1. 它是不是面向 AI 的默认写法？
2. 它是不是应该被挪到“示例/框架内部/直接 DAO 场景”而不是规范层？

### 8.4 建立断链巡检

当前已经确认存在的断链/陈旧引用包括：

- 旧的 `common-tasks.md` 路径
- `crud-example.md`
- 若干旧的 `gen-service.xgen` / `gen-web.xgen` 叙述

后续要求：

1. `INDEX.md` 不得引用不存在文件
2. 任务手册目录和规范主干目录的链接必须定期巡检
3. `MAINTENANCE.md` 已不再依赖已删除的 `10-meta/`

---

## 九、推荐执行阶段

### Phase 1：内容校准

目标：消除最危险的错误默认值。

交付物：

1. 精简版 `INDEX.md`
2. 重写后的 `project-structure.md`
3. 重写后的 `api-reference.md` 或将其降权
4. 重写后的 `exception-handling.md`
5. 扩充后的 `source-anchors.md`

### Phase 2：任务入口扩容

目标：让 AI 面对高频任务时，优先命中 `12-tasks/`。

交付物：

1. 补齐缺失 runbook
2. 重写 `12-tasks/README.md`
3. 在 `INDEX.md` 中按任务直接路由到 runbook

### Phase 3：规范主干收敛

目标：一个主题只保留一个规范主入口。

交付物：

1. 明确每个主题的 canonical doc
2. 其他文档只保留补充说明
3. 示例/参考文档降权并加醒目标注

### Phase 4：目录收缩

目标：在不引入大范围断链的前提下，收敛目录数量。

交付物：

1. `10-meta/` 已合并到 `MAINTENANCE.md`
2. 视情况收敛参考层目录
3. 更新全部内部链接

---

## 十、验收标准

### 10.1 AI 可用性

1. 给定常见开发问题，AI 应在 1 到 2 次跳转内命中正确文档。
2. `INDEX.md` 只承担路由，不再承担长篇教程职责。
3. `12-tasks/` 能覆盖至少 15 个高频开发场景。

### 10.2 规范一致性

1. 面向 AI 的规范文档中，不再把直接 `dao()` 访问写成普通 BizModel 默认模式。
2. 不再把 `@BizMutation @Transactional` 写成普通 BizModel 推荐做法。
3. 不再出现 `@Inject private`、Spring `@Value` 这类与 NopIoC 冲突的默认示例。

### 10.3 源码一致性

1. 关键规范都能在 `source-anchors.md` 中找到对应源码锚点。
2. 代码生成流程文档与实际 `gen-orm.xgen` / `gen-meta.xgen` 现状一致。
3. 反模式清单与 `CrudBizModel`、`BizActionInvoker`、`ClassModelBuilder` 等核心实现保持一致。

### 10.4 文档维护性

1. `INDEX.md`、`12-tasks/README.md`、`MAINTENANCE.md` 无断链。
2. 任何新增规范文档都必须声明其角色是 Runbook、Canonical Pattern、Concept/Architecture 还是 Reference/Example。
3. 目录迁移只在内容稳定后进行，不为重命名而重命名。

---

## 十一、结论

`docs-for-ai` 下一阶段最重要的工作，不是继续堆更多内容，也不是先改一轮目录名，而是做三件事：

1. **把源码已经证明的硬规则收敛成唯一口径**
2. **把 `12-tasks/` 扩成 AI 的主入口**
3. **把示例和参考材料从“默认规范”位置降下来**

只有完成这三件事，后续目录重构才真正有意义。否则，目录再漂亮，AI 依然会被冲突文档带偏。

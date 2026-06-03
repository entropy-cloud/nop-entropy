# 应用开发工作规程

本页回答一个高频问题：

**当你基于 Nop 平台开发业务应用时，默认应该按什么顺序推进 requirement、design、模型、生成、服务实现、测试与联调。**

本文是工作规程，不替代具体专题文档。遇到单点问题时，仍应回到对应的 core guide 或 runbook。

## 适用范围

适用于：

1. 基于 Nop 平台构建独立业务应用。
2. 需要从业务需求落到 ORM / API 模型、生成链、BizModel、页面和测试的常规开发任务。

不适用于：

1. 修改 `nop-entropy` 平台内核本身。
2. 只做局部页面微调、单个 bug 热修或纯文档维护且不涉及完整开发闭环的场景。

## 默认总顺序

1. 先确认应用项目本地 requirement / design / architecture。
2. 先改源 ORM 模型，必要时补 `model/*.api.xml`。
3. 首次建骨架时用 `nop-cli gen`；后续迭代优先用 `./mvnw` 触发增量生成。
4. 只在非生成保留层文件中补定制。
5. 标准 CRUD 优先复用生成结果和 `CrudBizModel<T>`。
6. CRUD 之外的业务动作默认落在 BizModel；复杂编排再拆 Processor。
7. 平台内置模块定制优先走 Delta。
8. 补服务测试与必要的页面 / RPC 验证。
9. 启动应用的 `*-app` 模块做联调。
10. 更新应用项目本地 owner docs、日志和验证记录；如果暴露出平台通用高频规则缺口，再回补 `docs-for-ai/`。

## 第 1 步：先确认业务语义

在外部应用项目中，业务事实不写在 `docs-for-ai/`，而是先读应用项目本地文档：

1. requirement
2. design
3. architecture
4. backlog / plan

目标不是先找代码入口，而是先回答这些问题：

1. 这次变更影响哪个业务对象。
2. 业务状态、资格、流程语义是否已经被 owner doc 说清楚。
3. 这是标准 CRUD 扩展，还是复杂业务编排。

如果业务语义还没沉淀，不要直接跳到 Java 或页面。先补应用项目本地 requirement/design，再决定模型和实现路径。

## 第 2 步：优先设计 ORM 模型

默认先看源 ORM 模型，因为它是结构、字典、关系和大量派生产物的源头。

对外部应用来说，源 ORM 模型既可以是 `model/*.orm.xml`，也可以是 Excel 形式的 ORM 模型；具体以当前应用项目采用的源模型形式为准。

适合先改 ORM 模型的场景：

1. 新增实体、字段、关系、字典。
2. 标准 CRUD 已经足够，只需要让实体、页面、元数据和基础服务自动生成。
3. 页面/API 结构可以从模型派生。

默认不要先手写 Java 的场景：

1. 只是新增字段或校验。
2. 只是标准列表、表单、详情、分页查询。
3. 只是实体关系、字典、默认值、菜单图标等模型可表达内容。

## 第 3 步：按需维护 API 模型

当业务应用需要稳定的 API / GraphQL 契约时，优先维护 `model/*.api.xml`。

默认理解：

1. 源 ORM 模型负责实体、结构、关系、标准 CRUD 源头。
2. `api.xml` 负责对外或稳定的 API 契约建模。
3. BizModel 是默认业务入口；GraphQL / RPC 能力主要建立在 BizModel 之上。

不要把 `api.xml` 理解成“替代 BizModel 逻辑实现”的地方。它定义契约，不替代服务实现。

## 第 4 步：首次生成和后续迭代要分开

### 首次建骨架

首次创建业务模块时，使用：

```bash
nop-cli gen model/{appName}.orm.xml -t=/nop/templates/orm -o=.
```

这个命令用于生成标准模块骨架，不是每次改模型都重跑的日常入口。

### 后续模型迭代

模型已经存在后，优先用 Maven Reactor 触发增量生成与构建，例如：

```bash
./mvnw clean install -T 1C
```

应用项目也可以按模块范围执行 `compile` / `install`，但原则不变：

1. 改源模型。
2. 通过 Maven 触发生成链。
3. 不手改任何生成物。

## 第 5 步：只改保留层，不改生成物

这些文件默认不能直接修改：

1. `_gen/` 目录下所有文件。
2. `_*.java`、`_*.xml`、`_*.xmeta`。
3. `_app.orm.xml`。
4. `_service.beans.xml`。

如果要改变生成结果，只能：

1. 改源模型。
2. 改 Delta。
3. 改非下划线保留层文件。
4. 必要时改 codegen 模板。

外部应用最常见的保留层位置包括：

1. `*.view.xml` 对生成页面做覆盖。
2. `*.xmeta` 补局部字段元数据。
3. BizModel / Processor 补业务逻辑。
4. `*.beans.xml` 补装配。

## 第 6 步：标准 CRUD 优先复用生成链

默认不要把“能生成的 CRUD”重写成手工 Java。

优先顺序：

1. ORM 模型自动生成实体、元数据、页面骨架与标准能力。
2. 普通实体服务默认 `@BizModel + extends CrudBizModel<T>`。
3. 查询优先 `doFindList()` / `doFindPage()`。
4. 修改优先 `save()` / `updateEntity()` / `delete()`。

只有当业务超出标准 CRUD 时，才继续补 BizModel 方法或 Processor。

## 第 7 步：CRUD 之外的逻辑默认放哪

### Entity

适合放稳定领域事实、只读计算、状态判断，例如：

1. `isXxx()`
2. `canXxx()`
3. `calculateXxx()`

不要在 Entity 里做持久化、跨聚合编排、外部系统调用。

### BizModel

BizModel 是默认业务入口，负责：

1. 暴露 `@BizQuery` / `@BizMutation`
2. 使用 `CrudBizModel` 安全 API
3. 承担事务入口和对外动作
4. 组织请求/返回 DTO

跨模块协作时，默认注入 `I*Biz` 接口，不直接注入其他 BizModel 实现类。

### Processor

当一个动作已经是明显的多步骤流程、需要协调多个聚合或外部系统时，再拆 Processor。

默认不要为了“更像 DDD”而过早新增大量 Java 类。

## 第 8 步：平台默认优先 Delta

如果你的目标是：

1. 覆盖平台已有模块。
2. 在不改平台源码的前提下做升级友好的差量定制。

默认优先使用 Delta，而不是直接修改平台内置资源或复制整份文件。

典型场景：

1. 覆盖 `nop-auth` 的页面、ORM、行为。
2. 给既有页面 / API / 元数据补差量字段。

## 第 9 步：常见模块集成建议

在业务应用里，如果需要复用现成的用户、权限和系统基础能力，默认通常会引入平台模块，而不是从零自建。

高频情况：

1. 需要通用用户与认证能力时，通常引入 `nop-auth-service`、`nop-auth-web`。
2. 需要系统序列号、注册表等基础系统能力时，通常也会引入 `nop-sys-service`、`nop-sys-web`。

这类引入属于平台能力复用，不改变 `Model -> Delta -> Java` 的默认开发顺序。

## 第 10 步：测试默认闭环

默认原则：

1. 需要录制/校验快照时，优先 `JunitAutoTestCase`。
2. 不需要快照、但需要容器内测试时，优先 `JunitBaseTestCase`。
3. 纯逻辑、无容器依赖的代码，直接写 JUnit 5 测试。

对于服务层开发，推荐优先在 GraphQL / RPC 入口层固化行为，而不是先从 HTTP E2E 起步。

高频做法：

1. 用 `@NopTestConfig` 打开本地库、测试配置和快照能力。
2. 用 `JunitAutoTestCase` 录制 `_cases/` 快照。
3. 复杂业务数据可以持续沉淀为回放用例。
4. bug 修复后优先补 API 级回归测试；若是纯通用逻辑，则补对应单元测试。

测试目标不是只验证 Java 方法本身，而是尽量把业务动作在平台默认服务入口上固化下来。

## 第 11 步：联调验证通常通过 `*-app`

外部应用完成模型和服务改动后，通常通过应用的 `*-app` 模块启动联调。

默认验证顺序：

1. 先做模块级构建或测试。
2. 再启动应用的 `*-app` 模块验证页面、RPC、GraphQL 和集成行为。

不要把“能编译”当作完整验证，也不要一开始就依赖重量级全链路 E2E 才判断改动是否正确。

## 第 12 步：最后回写文档和日志

在外部应用项目中，完成开发后通常还需要：

1. 更新应用项目本地 owner docs。
2. 记录验证结果。
3. 追加当天开发日志。

如果这次任务暴露出 `docs-for-ai/` 缺少高频默认规则，则应在同一任务内顺手修正 `docs-for-ai/`，不要把平台通用工作方法长期留在口头解释里。

## 一页决策表

| 问题 | 默认答案 |
|------|---------|
| 先写 Java 还是先改模型 | 先改模型 |
| 先改应用本地业务文档还是直接写代码 | 先确认本地 requirement/design |
| 标准 CRUD 要不要手写 service/controller | 不要，优先生成 + `CrudBizModel<T>` |
| 什么时候补 `api.xml` | 需要稳定 API / GraphQL 契约时 |
| 改平台模块用什么 | 优先 Delta |
| 跨模块怎么调 | 注入 `I*Biz` |
| 复杂多步骤流程放哪 | Processor |
| 服务行为怎么测 | 优先 `JunitAutoTestCase` / `JunitBaseTestCase` |
| 最终怎么联调 | 启动应用的 `*-app` 模块 |

## 常见反模式

1. 业务设计还没沉淀，就直接写 Java。
2. 只改页面或服务，不回到源模型。
3. 手改 `_gen/`、`_app.orm.xml`、`_service.beans.xml`。
4. 把所有业务逻辑都塞进 Entity，或把所有流程都堆进一个 BizModel 方法。
5. 直接注入其他 BizModel 实现类，而不是 `I*Biz`。
6. 复杂业务没有 API 级测试，只靠手工点页面验证。
7. 为了局部定制直接复制平台模块或大段生成页面，而不是用 Delta 或保留层覆盖。

## 相关文档

- `../00-start-here/application-project-defaults.md`
- `./model-first-development.md`
- `./external-app-development.md`
- `./service-layer.md`
- `./domain-logic-and-ddd.md`
- `./api-and-graphql.md`
- `./delta-customization.md`
- `./testing.md`
- `../03-runbooks/change-model-and-regenerate.md`
- `../03-runbooks/write-bizmodel-method.md`
- `../03-runbooks/implement-complex-business-flow.md`
- `../03-runbooks/write-tests.md`

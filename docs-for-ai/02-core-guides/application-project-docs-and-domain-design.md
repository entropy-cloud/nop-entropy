# 应用项目文档边界与领域设计

本页定义所有 Nop 应用项目通用的 owner-doc 边界和领域设计写法。

具体业务事实属于应用项目本地文档；本页只描述通用方法。

## 文档边界

| 问题 | 默认 owner |
|------|------------|
| 这个应用要支持什么业务行为 | 应用项目本地 requirement 与 design owner docs |
| 稳定 app-layer 业务语义、角色、流程、页面行为、状态含义 | 应用项目本地 design owner docs |
| 技术结构、模块边界、事务、锁、缓存、调度、集成、框架机制 | 应用项目本地 architecture owner docs |
| 持久化实体、字段、关系、字典、状态码、生成契约 | 应用项目 `model/*.orm.xml` 与 `model/*.api.xml` |
| 实施顺序、当前计划、闭环证据 | 应用项目本地 backlog、plans、logs 等执行记录 |
| Nop 平台通用开发方法 | `nop-entropy/docs-for-ai/` |

设计文档可以描述业务-facing 的实体名称、状态含义和迁移规则，但不应复制字段清单、表目录、字典 code 列表或生成 API 契约。

## 领域区域

应用项目可以把领域区域作为轻量 bounded context 使用。

每个领域 owner doc 应说明：

- 它拥有哪些业务术语。
- 相邻领域的术语由哪个 owner doc 管理。
- 跨领域流程的主 owner 是谁。
- 哪些规则只是导航摘要，真实规则在哪个 owner doc。

跨领域工作流应放在主业务过程的 owner doc 中，其他文档只引用或摘要，不重复维护完整规则。

## 业务语言

优先使用业务人员能识别的稳定术语，而不是表名、字段名、Java 类名或框架术语。

一个设计 doc 引入重要概念时，至少说明：

- 业务含义。
- 所属角色或参与者。
- 生命周期或状态含义。
- 关键资格、前置条件或拒绝条件。
- 用户可见或运营可见的结果。

如果精确字段集、枚举 code 或 API shape 重要，应引用 `model/*.orm.xml` 或 `model/*.api.xml`，不要在 prose 中复制。

## 结构概念与流程行为

结构概念描述信息空间，例如：商品和 SKU、用户和地址、订单和订单行、优惠券和领取关系。

流程行为描述状态如何变化，例如：下单、支付确认、发货、收货、退款、审批、任务执行、通知发送。

不要把一个聚合式概念写成拥有所有跨领域规则的 god object。跨领域行为应在业务层描述为 workflow，再由 architecture 或 Nop 实现 guide 决定编排落位。

## 状态和迁移

设计文档可以定义业务状态含义和迁移规则。

重要迁移至少写清：

- 触发人或系统事件。
- 前置条件。
- 结果状态或业务结果。
- 重要拒绝、超时、重试、回退、取消、退款或异常路径。

存储状态码、字典 code、字段名和生成 API 仍由模型/API 文件负责。

## Nop 实现落位桥接

当设计需要给实现落位提供提示时，使用下列桥接语言：

| 设计含义 | 默认实现落位 |
|----------|--------------|
| 稳定领域事实、状态判断、简单派生值 | Entity |
| 对外查询或修改动作、权限边界、事务入口 | BizModel |
| 跨聚合、跨模块、多步骤或外部系统编排 | Processor |
| 多个 Processor 稳定复用的单一动作 | Step |
| 可配置、长流程、人工节点或状态驱动流程 | Workflow / Rule / State machine |

设计文档只保留这种简短桥接。事务传播、锁、缓存、集成协议、调度机制、模块 wiring 和框架细节属于 architecture 或具体 `docs-for-ai` 技术 guide。

## 应用项目 owner doc 更新规则

当变更影响支持的业务行为时，更新应用项目本地 design owner doc。

当变更影响技术结构、集成方式或跨模块实现策略时，更新应用项目本地 architecture owner doc。

当变更影响持久化模型、字典、状态码或生成 API 契约时，更新源模型并重新生成，不要用 prose 文档替代模型真相。

当变更只影响实施顺序、当前 blocker、验证证据或历史记录时，更新 backlog、plan、log、testing 或 bug 记录，不要写入稳定 design doc。

## 反模式

- 复制表目录、字段清单、字典 code 目录或 API 契约到设计文档。
- 在稳定 owner doc 中维护 roadmap 状态、当前计划或 blocker。
- 用 mock-only、demo、临时等标签弱化正式产品行为。
- 把平台实现机制写成业务语义。
- 多个 owner doc 竞争同一业务规则的维护权。
- 把 raw input、原型、历史 audit 或 chat 记忆直接当成设计真相。

## 相关文档

- `../00-start-here/application-project-defaults.md`
- `./external-app-development.md`
- `./domain-logic-and-ddd.md`
- `./architecture-principles.md`
- `./model-first-development.md`

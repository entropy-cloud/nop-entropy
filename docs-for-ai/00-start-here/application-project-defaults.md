# Nop 应用项目默认规则

> 受众：基于 Nop 平台构建独立业务应用的 AI agent 和开发者。

本页回答一个问题：

**当你在 `nop-app-*` 这类外部应用项目中工作，而不是开发 `nop-entropy` 平台本身时，默认应该怎样判断、查文档和落改动。**

## 默认边界

- `docs-for-ai/` 负责所有 Nop 应用项目通用的平台使用规则、开发模式和反模式。
- 应用项目本地文档树负责该应用自己的业务事实、产品语义、计划、日志、验证命令和项目级约束。
- 不要把某个应用的订单、商品、支付、权限等业务 baseline 上移到 `docs-for-ai/`。
- 不要在每个应用项目中重复维护通用 Nop 开发规则；本地文档应引用 `docs-for-ai/`。

## 默认决策顺序

1. 先看应用项目本地 requirement、design、architecture、backlog 或 plan，确认这次要改的业务事实。
2. 再看 `docs-for-ai/`，确认 Nop 平台默认做法和落位规则。
3. 优先修改源模型、Delta、非生成保留层文件。
4. 只有模型、元数据、Delta 和已有扩展点不足时，才新增 Java 代码。
5. 完成后更新应用项目本地 owner docs、验证记录和日志；如果暴露出通用规则缺口，再回补 `docs-for-ai/`。

## 应用项目本地应保留什么

- 产品目标、商业 baseline、角色、业务流程和领域术语。
- 具体 requirement、backlog、plan、audit、log、testing、bug 记录。
- 本项目的 protected areas、验证命令、模块可信度和运行环境约束。
- 本项目特有的 domain area 划分和 owner doc 路由。
- 与具体外部系统、部署方式、客户约束有关的事实。

## 应上移到 docs-for-ai 的内容

- Model -> Delta -> Java 的默认开发路线。
- 应用项目常见模块结构和文件类型职责。
- 应用项目本地 design、architecture、model、plans、logs 等 owner 的通用边界。
- 通用领域设计写法：业务语言、domain area、结构概念、流程行为、状态语义和实现落位桥接。
- Entity、BizModel、Processor、Step、Workflow、Rule、State machine 的默认职责。
- 通用反模式：手改生成物、复制 schema 到设计文档、把实施状态写入稳定 owner doc、绕过 Delta、从原型直接跳代码。

## 默认开发闭环

1. 读应用项目本地 owner docs，确认业务语义。
2. 读 `docs-for-ai/INDEX.md` 路由到对应 guide 或 runbook。
3. 改源模型、Delta、保留层页面、BizModel 或 Processor。
4. 运行应用项目本地定义的验证命令。
5. 更新应用项目本地日志和 owner docs。
6. 仅当通用 Nop 规则被澄清或改变时，更新 `docs-for-ai/`。

## 常见反模式

| 反模式 | 正确做法 |
|--------|----------|
| 在应用项目本地复制一套 Nop 平台开发规范 | 本地引用 `docs-for-ai/`，只补项目事实 |
| 把应用业务 baseline 写进 `docs-for-ai/` | 业务 baseline 留在应用项目本地 owner docs |
| 从原型、截图或 raw input 直接写代码 | 先沉淀 requirement/design，再按 `docs-for-ai` 选实现路径 |
| 因为是外部应用就直接改 nop-entropy 平台源码 | 优先模型、Delta、保留层和应用模块扩展 |
| 在稳定设计文档里维护实施状态和当前 blocker | 实施状态放 backlog、plan、log |

## 相关文档

- `../INDEX.md`
- `../02-core-guides/external-app-development.md`
- `../02-core-guides/application-project-docs-and-domain-design.md`
- `../02-core-guides/domain-logic-and-ddd.md`
- `../02-core-guides/model-first-development.md`

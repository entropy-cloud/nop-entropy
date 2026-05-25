# 架构设计原则

本页汇集跨切面的设计决策原则。这些原则不是语法规则（语法规则见各自的 core-guide），而是帮助判断"应该怎么做"的推理基础。

---

## 一、聚合根原则上有对应数据库表

### 规则

**聚合根（`@BizModel` 对应的实体）原则上应该有 ORM 数据库表。** 不允许创建无 ORM 实体、无 xmeta 的"伪 BizModel"。

### 推理链

1. 重要的业务概念几乎总需要持久化状态，因此有对应的数据库表。
2. Nop 的 GraphQL 引擎通过 xmeta 构建 object definition，xmeta 与 ORM 实体绑定。
3. 无表的"聚合根"意味着无实体、无 xmeta，GraphQL 前端查询会报"未定义的对象"。
4. RPC `/r/` 路径不校验 GraphQL schema，所以伪 BizModel 在 RPC 测试中能侥幸通过，但浏览器页面会失败。

### 例外：索引级 / 编排型 BizModel

少数 BizModel 的聚合根没有独立数据库表，但**必须明确声明并满足以下条件之一**：

| 例外类型 | 条件 | 典型例子 |
|---------|------|---------|
| 编排型聚合根 | 编排多个真实聚合根的流程入口，无独立表 | `NopJobScheduleBizModel`（跨任务、日志、触发器的调度入口） |

**即使是无表 BizModel，仍然必须有 xmeta 文件**（定义 GraphQL schema 可暴露的字段和方法）。xmeta 可以定义纯计算字段（`@BizLoader`），不需要每字段都映射到 ORM 列。

### 判断方法

创建新 BizModel 时问自己：

1. 这个 BizModel 操作的核心实体有数据库表吗？ → **有** → 正常聚合根
2. 没有独立表，但它是编排多个真实聚合根的流程入口？ → 编排型例外，需 xmeta
3. 没有独立表，也没有明确的操作实体？ → **不应创建 BizModel**，逻辑应归入已有 BizModel

---

## 二、模块依赖方向必须单向

### 规则

模块间依赖方向必须形成有向无环图（DAG）。默认方向：

```
core → dao → meta → service → web → app
```

下游模块不能反向依赖上游模块。具体：

| 不允许 | 正确做法 |
|--------|---------|
| `dao` 依赖 `service` | `service` 调用 `dao` |
| `core` 依赖 `service` | `service` 依赖 `core` |
| `web` 直接调 `dao` | `web` 通过 `service` 访问数据 |
| 算法模块依赖语言特定模块 | 语言适配器依赖算法接口 |

### 跨模块调用

跨模块通过 `I*Biz` 接口（在 `api` 模块定义），不直接注入实现类。部署时由 IoC 容器注入具体实现。

---

## 三、DSL 优先

### 规则

能用 DSL（XDef / XDSL / ORM 模型 / xmeta / beans.xml）表达的配置和元数据，不要用 Java 硬编码。

### 推理链

1. DSL 文件可通过 Delta 机制定制（覆盖/扩展），Java 代码不行。
2. DSL 驱动代码生成，修改 DSL 后重新生成比手改 Java 可靠。
3. DSL 文件是可 diff、可 review 的声明式描述，比 Java 代码更直观。

### 常见反模式

| 反模式 | 正确做法 |
|--------|---------|
| 硬编码模式字典（如框架注解列表） | 通过 Nop IoC 注册 `I*Provider`，用户通过 Delta 扩展 |
| 将 XDSL→运行时桥接器标 `@Deprecated` 并推荐绕过 DSL | 修复桥接器 bug，保持 DSL 优先路径 |
| 用 Java 常量定义本应是字典的业务状态值 | 用字典（dict），支持租户/场景定制 |

---

## 四、不自建已有能力

### 规则

Nop 平台已提供的能力，不在业务模块中重复实现。

| 已有能力 | 已有模块 | 业务模块的做法 |
|---------|---------|---------------|
| 全文搜索 + 向量搜索 | `nop-search` | 添加 `nop-search-api` 依赖，调用 `ISearchEngine` |
| 权限控制 | `nop-auth` | 使用 `@BizAuth` + xmeta 权限配置 |
| 定时任务 | `nop-job` | 使用 `@BizAction` + 任务配置 |
| 工作流 | `nop-wf` | 调用 `IWorkflowEngine` |

判断标准：如果一个功能在 Nop 平台其他模块已有完整实现，业务模块应依赖该模块的 API 接口，而不是自己写。

---

## 五、模型是源头

### 规则

ORM 模型（`model/*.orm.xml`）是数据结构和实体关系的唯一权威来源。数据库表、Java 实体、xmeta 字段、GraphQL schema 都从模型派生。

### 操作顺序

```
修改 model/*.orm.xml → 重新生成 → 补充 Delta → 补充 Java
```

不要跳过模型直接改生成物（`_gen/`、`_*.java`、`_*.xml`、`_app.orm.xml`、`_service.beans.xml`）。

---

## 相关文档

- `./service-layer.md` — BizModel 编写规范（聚合根对应规则的语法层面）
- `./domain-logic-and-ddd.md` — DDD 落位与 Entity/BizModel/Processor 分层
- `./ioc-and-config.md` — IoC 注入与配置
- `./delta-customization.md` — Delta 定制机制
- `../03-runbooks/choose-entity-bizmodel-processor.md` — 选择落位位置的 runbook

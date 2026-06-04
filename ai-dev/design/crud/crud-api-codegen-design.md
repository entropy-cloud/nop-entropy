# CRUD API 代码生成设计

**日期**：2026-06-03（更新于 2026-06-04）
**范围**：`nop-api-core`、`nop-codegen`、`*-meta`、`*-api`
**状态**：active

---

## 一、设计结论

1. CRUD typed API 的定位是**客户端调用契约**，不是服务端真实发布入口。
2. 服务端真实发布链保持为 **BizModel -> GraphQL / RPC / REST adapter**，不因 CRUD typed API 的存在而改变。
3. CRUD typed API 从实体 `xmeta` 生成，而不是直接从 ORM 模型生成。
4. 生成触发点位于 `*-meta/postcompile`，输出到对应 `*-api` 模块。
5. CRUD typed API 在客户端契约层坚持同构表达：单条输入、批量输入和公共 patch 都使用同一个强类型输入模型，而不是向调用方暴露 `Map<String,Object>`。
6. 实体级 API 发布使用独立标签 `no-api` 控制；它只影响 CRUD typed API 生成，不影响 BizModel 注册。
7. `not-pub` / `published=false` 继续表示字段在 API 视角下不存在，`no-web` 继续表示页面/前端生成控制；两者都不承担 CRUD typed API 的实体级发布语义。
8. CRUD typed API 采用项目既有 `*Bean` DTO 命名，并保留树形实体的独立树操作扩展语义。
9. CRUD typed API 的 Bean 与 Api 都采用单层公共契约，不再保留 `_gen._*Bean` 或 `_{Entity}Api` 作为额外对外层。

## 二、背景与动机

当前平台里，标准 CRUD 服务的服务端能力已经由 BizModel 提供，客户端也可以通过通用 adapter 调用。但这种方式对跨模块或外部调用方不够友好：

- 缺少稳定的强类型客户端契约
- 无法从 `*-api` 模块直接获得实体输入/输出的类型边界
- CRUD 能力与手写 `*.api.xml` 接口在调用体验上不一致

因此需要一层专门面向调用方的 CRUD typed API，把实体的输入输出语义稳定地投影到 `*-api`，同时避免把这层契约误当成服务端发布入口。

## 三、核心设计

### 3.1 分层边界

- **BizModel 层**：服务端真实发布面，负责 GraphQL、RPC 和通用 REST adapter 的运行时能力。
- **CRUD typed API 层**：客户端契约层，负责为调用方提供稳定的强类型输入/输出与接口命名。
- **XMeta 层**：字段是否对 API 存在、字段可写性、关系结构和树结构的语义来源。

这一分层要求 CRUD typed API 不能反向决定服务端是否发布；否则会把“客户端便利层”错误提升为“服务端发布控制层”。

### 3.2 生成来源

CRUD typed API 以实体 `xmeta` 为唯一语义来源。

原因：

- 字段可写性由 `insertable` / `updatable` 定义
- 输出可见性由 `published` 定义
- 关系结构由 xmeta 的关系 prop 定义
- 树结构由 xmeta 的 tree 元信息定义

因此，CRUD typed API 的输入输出边界应追随 xmeta，而不是追随 ORM 列表面或页面层配置。

### 3.3 生成时机

CRUD typed API 在 `*-meta/postcompile` 触发生成。

原因：

- 它依赖的是 meta 产物，而不是更早期的 ORM 源模型视图
- 该时机与其他依赖 meta 结果的后置生成任务一致
- 生成结果直接落到 `*-api`，形成客户端依赖面

### 3.4 输入与输出契约

- 输入 Bean 表达“客户端可提交什么”，以 xmeta 的可写字段为边界。
- 输出 Bean 表达“客户端可见什么”，以 xmeta 的已发布字段为边界。
- `batchModify` 虽然对应服务端的批量增删改实现，但在客户端视角下仍然是同一实体输入的重复组合；逐项 `_chgType` 与批量级 `common` 都属于该输入模型的控制语义，不构成退回 `Map<String,Object>` 的理由。
- 字段级 `published=false` 表示该字段在外部 API / GraphQL 视角下不存在；它不影响实体是否存在，也不影响服务端是否发布该对象。
- ORM component 的 backing prop 不属于客户端契约，应排除在 CRUD typed API 之外。
- 由于 InputBean / OutputBean / Api 都完全由 `xmeta` 驱动生成，CRUD typed API 不保留独立的“生成基类 + 公共包装层”双层结构；公共非下划线类型本身就是最终客户端契约。

### 3.5 标签职责边界

- `not-pub`：字段级 API 不存在控制。
- `no-web`：页面/前端生成控制。
- `no-api`：CRUD typed API 实体级生成控制。

这三个标签分别对应字段层、页面层、客户端契约层，不能互相替代。

尤其是：

- 不应复用 `not-pub` 表达“实体不生成 typed API”
- 不应复用 `no-web` 表达“实体不对客户端提供 typed API”
- `no-api` 不应被解释为“服务端不发布该对象”

### 3.6 服务端发布边界

`no-api` 不影响 BizModel 注册，也不直接改变 GraphQL / RPC / REST adapter 的服务端发布链。

当前服务端的可调用性仍由两层决定：

- 是否存在对应 BizModel 发布面
- 权限与认证控制是否允许调用

如果未来需要表达“该对象在服务端根本不发布”，应新增独立的服务端发布标签，并单独接入 BizModel / GraphQL 发布链，而不是复用 `no-api`。

## 四、拒绝了什么

### 方案 A：让 CRUD typed API 控制服务端发布

拒绝理由：

- 破坏客户端契约层与服务端发布层的职责分离
- 把 codegen 开关误提升为运行时发布开关
- 会让“有没有客户端接口”和“服务端是否可调用”耦合到一起

### 方案 B：复用 `no-web` 作为 CRUD typed API 发布控制

拒绝理由：

- `no-web` 在现有仓库中已经明确承担页面层语义
- 页面不生成不等于不需要客户端 API 契约
- 复用 `no-web` 会让页面层和 API 契约层难以独立演进

### 方案 C：复用 `not-pub` 作为实体级 API 开关

拒绝理由：

- `not-pub` / `published=false` 是字段级 API 不存在语义，不是实体级存在性语义
- 现有 GraphQL / xmeta / OutputBean 都已经依赖它表达字段不可见
- 复用后会造成“字段不公开”和“实体不生成类型”的语义混淆

### 方案 D：直接暴露 ORM 实体作为客户端契约

拒绝理由：

- 会把持久化模型直接泄漏给外部调用方
- 无法稳定表达字段可见性与输入输出边界
- 会把 ORM 特有结构与类型带入 `*-api`

### 方案 E：继续只靠通用 adapter，不生成 typed CRUD API

拒绝理由：

- 不能为调用方提供稳定的强类型契约
- 与手写 API 接口的使用体验不一致
- 不利于跨模块和外部系统复用 `*-api`

### 方案 F：在 `batchModify` 等批量接口上继续向客户端暴露 `Map<String,Object>`

拒绝理由：

- 违背同构表达：同一实体输入不应在单条场景使用 Bean、在批量场景退回 Map
- `_chgType` 和公共 patch 只是输入模型上的控制语义，不要求放弃强类型契约
- 服务端底层继续使用 `Map` 是框架内部实现选择，不应直接泄漏到客户端契约层

### 方案 G：保留 `_gen._*Bean + *Bean extends _*Bean`、`_{Entity}Api + {Entity}Api extends _{Entity}Api` 双层契约

拒绝理由：

- CRUD typed API 的 Bean / Api 完全由 `xmeta` 决定，不存在真实的手工扩展需求
- 双层结构会把 `_` 前缀内部生成类型泄漏到关系字段和接口泛型，制造额外契约噪音
- 单层公共类型更符合调用方预期，也更符合“生成物即最终契约”的定位

## 五、与已有设计的关系

- `ai-dev/design/crud/relation-write-mode-design.md`：定义 CRUD 写入语义，与本设计共同决定输入契约边界。
- `docs-for-ai/02-core-guides/api-and-graphql.md`：定义 BizModel 作为服务端默认发布面的使用规则。
- `docs-for-ai/02-core-guides/api-model-and-codegen.md`：定义调用方可见的 CRUD typed API 使用规范。
- `docs-for-ai/03-runbooks/debug-codegen-and-generated-files.md`：定义如何排查 CRUD typed API 生成链。

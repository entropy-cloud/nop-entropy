# 标准业务模块骨架

当前仓库里的大多数业务模块都遵循同一套骨架：

```text
model -> codegen -> dao -> meta -> service -> web -> app -> api
```

这比单个类名更重要。AI 一旦理解这条链路，就能快速判断“该改哪里、谁负责生成谁、哪些文件不能手改”。

## 标准结构

```text
{app}/
├── model/
│   └── {app}.orm.xml
├── {app}-codegen/
├── {app}-dao/
├── {app}-meta/
├── {app}-service/
├── {app}-web/
├── {app}-app/
└── {app}-api/
```

可选模块常见有 `-core/`、`-ext/`、`-queue/`、`-worker/`、`-coordinator/`、`-delta/` 等，但不会改变主链路。

## 当前仓库里的代表例子

| 模块 | 参考路径 |
|------|---------|
| 认证 | `nop-auth/` |
| 作业调度 | `nop-job/` |
| 任务 | `nop-task/` |
| 工作流 | `nop-wf/` |
| AI | `nop-ai/` |

这些模块都能看到 `model/`、`*-codegen/`、`*-dao/`、`*-meta/`、`*-service/`、`*-web/`、`*-app/` 的重复结构。

## 各层职责

| 模块 | 职责 | 典型内容 |
|------|------|---------|
| `model/` | 业务源模型 | `*.orm.xml` |
| `*-codegen/` | 代码生成入口 | `postcompile/gen-orm.xgen` |
| `*-dao/` | ORM、Entity、DAO、`I*Biz` 等派生产物 | `_app.orm.xml`、Entity、接口 |
| `*-meta/` | 基于 ORM 生成 XMeta 与 i18n | `precompile/gen-meta.xgen`、`postcompile/gen-i18n.xgen` |
| `*-service/` | BizModel、xbiz、beans、Processor | `XxxBizModel.java`、`*.xbiz` |
| `*-web/` | 页面与视图资源 | `precompile/gen-page.xgen`、`*.view.xml`、`*.page.yaml` |
| `*-app/` | 应用打包与启动 | 启动类、配置、部署相关文件 |
| `*-api/` | 外部系统调用本模块的 RPC 接口契约 | Typed Service Interface（如 `WorkflowService`）、Message Bean（如 `WfStartRequestBean`） |

**关键区分：`*-api/` vs `*-dao/` vs `*-service/`**

| 产物 | 放在哪里 | 用途 | 例子 |
|------|---------|------|------|
| `I*Biz` 接口 | `*-dao/.../biz/` | BizModel 之间跨模块调用的内部契约接口，由 BizModel 实现 | `INopAuthUserBiz`、`IOrderBiz` |
| BizModel 内部使用的局部 DTO | `*-dao/.../dto/` 或 `*-service/` | BizModel / Processor 共享的局部数据结构，仅模块内部使用 | `CohesionBreakdownDTO` |
| 外部 RPC Service Interface | `*-api/` | 外部系统通过 HTTP/RPC 调用本模块时使用的强类型接口，包装 `ApiRequest<>`/`ApiResponse<>` | `WorkflowService`、`IJobScheduler` |
| 外部 RPC Message Bean | `*-api/.../beans/` | 上述 RPC 接口的请求/响应消息类，通常由 codegen 生成 | `WfStartRequestBean`、`ChatRequest` |

**`*-api/` 的明确边界：**

1. `*-api/` 只放外部系统调用本模块的接口和消息类。不是所有 DTO 都该放这里。
2. BizModel 方法使用的局部 DTO（汇总数据、简化视图、组合数据等）放 `*-dao/.../dto/` 或 `*-service/`，**不属于** `*-api/`。实体能表达的优先用实体，字段可见性由 xmeta 控制。
3. `I*Biz` 接口放在 `*-dao/`，不在 `*-api/`。它们是内部 BizModel 间调用的契约，不是外部 API。
4. 很多模块的 `*-api/` 可以为空（如 `nop-auth-api`），只有需要给外部系统提供强类型 RPC 接口时才有内容。
5. 平台回避 Controller / Service 这类命名（容易与 Spring 混淆），优先用 BizModel / Processor / Api 等名称。

## 真实生成链路

当前仓库中可以直接看到这条链路：

```text
model/{app}.orm.xml
  -> {app}-codegen/postcompile/gen-orm.xgen
  -> {app}-dao / {app}-service / {app}-meta / {app}-web 的基础产物
  -> {app}-meta/precompile/gen-meta.xgen
  -> XMeta
  -> {app}-meta/postcompile/gen-i18n.xgen
  -> i18n
  -> {app}-web/precompile/gen-page.xgen
  -> view/page 文件
```

## 首次生成 vs 后续迭代

### 首次生成骨架

首次创建模块时，用 `nop-cli gen` 从 ORM 模型生成项目骨架。

### 后续迭代

后续改模型时，默认走 Maven 构建触发再生成，不要手工重搭目录。

## 哪些文件默认不能手改

| 类型 | 例子 |
|------|------|
| `_gen/` 目录 | `entity/_gen/_Xxx.java` |
| `_` 前缀 Java / XML | `_NopAuthUser.xbiz`、`_service.beans.xml` |
| 聚合 ORM 生成物 | `_app.orm.xml` |
| 页面生成物 | `_gen/_Xxx.view.xml` |

## AI 默认修改位置

| 任务 | 默认修改位置 |
|------|-------------|
| 新增表 / 字段 / dict | `model/*.orm.xml` |
| 扩展实体辅助方法 | `*-dao/src/main/java/.../Xxx.java` |
| 新增 BizModel 逻辑 | `*-service/src/main/java/.../XxxBizModel.java` |
| 扩展 xbiz | `*-service/src/main/resources/_vfs/.../Xxx.xbiz` |
| 扩展页面 | `*-web/src/main/resources/_vfs/.../*.view.xml`、`*.page.yaml` |
| 定制基础产品 | `_vfs/_delta/...` |

## 这套骨架如何帮助 AI 决策

1. 想改数据结构，先回到 `model/`。
2. 想改实体默认行为，先看保留层 Entity 或 BizModel，而不是 `_gen`。
3. 想改页面，先看 `web` 下非下划线文件和 Delta。
4. 想解释生成关系，先看 `codegen`、`meta`、`web` 下的 `.xgen`。

## 相关文档

- `./where-things-live.md`
- `../02-core-guides/model-first-development.md`
- `../03-runbooks/create-new-entity.md`
- `../04-reference/source-anchors.md`

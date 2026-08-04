# 平台总览（Platform Overview）

> 本页只给**全局坐标**：Nop 是什么、分层怎么搭、一个请求怎么走完、Delta 是什么。
> 具体规则见 `02-core-guides/`，具体步骤见 `03-runbooks/`，模块怎么选见 `03-modules/`。

## 一、Nop 是什么

Nop 是基于**可逆计算**的语言导向编程（LOP）平台。它不靠手写代码堆功能，而是：先用模型（DSL）描述业务，再用生成器产出基础代码，最后用 Delta 差量做定制。核心公式 `App = Δ x-extends Generator<DSL>` 贯穿 ORM、IoC、GraphQL、工作流、页面等所有子系统。同一套模型 + Delta 机制，让"二次开发"与"基础产品升级"在统一框架内共存。

## 二、分层结构

模块分组以 `01-repo-map/module-groups.md` 为准。主要分组自下而上：

```
测试与示例          demo / autotest（非生产）
集成与运行时外围     Spring / Quarkus / 网关 / 网络集成（宿主适配）
─────────────────────────────────────────────
服务框架            BizModel / GraphQL / Gateway   ← 业务 action 在此
持久化              ORM / DAO / DB Migration        ← 数据在此
核心框架            IoC / Config / Boot / Security  ← 运行基座
基础内核            XLang / 代码生成 / 核心 API / 工具 ← 元语言与生成
─────────────────────────────────────────────
典型业务模块         nop-auth / nop-job / nop-task / nop-wf（标准骨架样板）
可复用业务模块       nop-sys / nop-report / nop-rule / nop-batch / nop-dyn / ...
AI 子系统           nop-ai（LLM / Agent / RAG / 工具）
Runner / CLI        nop-runner（gen/convert 命令入口）
```

> WIP 实验模块（nop-code）、通用图算法库（nop-graph）、流处理引擎（nop-stream）等专题分组见 module-groups.md 原表。

**框架主干**（回答"框架默认怎么做"时优先查）：基础内核 → 核心框架 → 持久化 → 服务框架。

## 三、请求流

所有 HTTP 入口底层都经 **GraphQL Engine** 分发到同一个 BizModel 方法，差异仅在响应封装：

```
HTTP 入口                分发                      业务层            数据层
───────────            ──────                    ──────           ──────
/graphql   ─┐
/r/{op}    ─┼──>  IGraphQLEngine  ──>  BizModel action  ──>  ORM / Session  ──>  DB
/p/{op}    ─┘     (统一调度)            @BizQuery  读
/px/{svc}/{op} ──> (分布式代理转发)      @BizMutation 写 (自动事务)
```

- `/r/` 返回 JSON；`/p/` 内容感知（文件/二进制）；`/px/` 跨服务代理。
- `@BizMutation` 自动进入事务，无需手动叠加事务注解。
- 标准实体服务默认继承 CrudBizModel，开箱即有 findPage/findList/save/delete。

## 四、Delta 概念

定制不改基线，只加差量层：

```
基础产品（JAR / 生成物）
   ⊕  Delta 差量层（_delta/ 下的 x:extends 覆盖）
   ─────────────────────────
   =  运行时合并后的应用
```

- 下划线前缀（`_gen/` 目录及所有以 `_` 开头的生成文件，如 `_app.orm.xml`、`_service.beans.xml`）是**生成物，禁止手改**；要改结果只能改源模型、Delta 或保留层，再重新生成。
- Delta 机制的理论与完整主线见 `06-extensibility/platform-extensibility-mechanism.md`。

## 下一步

- 要干活：`00-start-here/ai-defaults.md`（默认规则）。
- 要定位改哪里：`01-repo-map/where-things-live.md`。
- 已知任务类型：`03-runbooks/README.md`。
- 查术语：`04-reference/glossary.md`。

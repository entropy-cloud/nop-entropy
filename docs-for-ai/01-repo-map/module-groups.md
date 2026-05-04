# 仓库模块分组

当前仓库是一个根 `pom.xml` 驱动的 Maven 多模块工程。

AI 不需要一开始记住所有模块名，但必须知道应该先去哪个层级找答案。

## 根模块的主要分组

| 分组 | 主要路径 | 作用 |
|------|---------|------|
| 基础内核 | `nop-kernel/` | 代码生成、XLang、核心 API、基础工具 |
| 核心框架 | `nop-core-framework/` | IoC、Config、Boot、Plugin、Security、Log |
| 持久化 | `nop-persistence/` | DAO、ORM、DB Migration、DBTool |
| 服务框架 | `nop-service-framework/` | BizModel、GraphQL、Gateway |
| 典型业务模块 | `nop-auth/`、`nop-job/`、`nop-task/`、`nop-wf/` | 最标准的业务骨架样板 |
| AI 子系统 | `nop-ai/` | AI 相关的 model/codegen/dao/service/web/app 以及 agent、skills、toolkit |
| Runner / CLI | `nop-runner/`、`scripts/` | CLI、runner、命令入口 |
| 集成与运行时外围 | `nop-spring/`、`nop-quarkus/`、`nop-network/`、`nop-integration/` | 宿主集成与运行环境支持 |
| 测试与示例 | `nop-autotest/`、`nop-demo/`、`demo/` | 测试基建、demo、模板 |
| WIP 实验模块 | `nop-code/` | 代码分析模块（不在根 pom.xml modules 中，需单独构建） |

## 最值得先理解的模块

### 1. 框架主干

- `nop-kernel/`
- `nop-core-framework/`
- `nop-persistence/`
- `nop-service-framework/`

当你要回答“框架默认怎么做”时，通常应该先从这四层找依据。

### 2. 业务骨架样板

- `nop-auth/`
- `nop-job/`
- `nop-task/`
- `nop-wf/`

当你要回答“一个标准业务模块通常如何建模、生成、分层和扩展”时，优先看这几个目录。

### 3. AI 专属子系统

- `nop-ai/`

当任务直接涉及 AI agent、tool、skill、RAG、shell、MCP、AI service 时，再深入 `nop-ai/`。

## 常见任务应该先看哪里

| 任务 | 优先路径 |
|------|---------|
| 理解代码生成链路 | `nop-kernel/`、业务模块下的 `*-codegen/` |
| 理解 ORM / DAO / 迁移 | `nop-persistence/` |
| 理解 BizModel / GraphQL | `nop-service-framework/` |
| 找标准业务实现参考 | `nop-auth/`、`nop-job/`、`nop-task/`、`nop-wf/` |
| 找 CLI 或生成入口 | `nop-runner/`、`scripts/nop-cli.cmd` |
| 找测试基类与快照机制 | `nop-autotest/` |
| 找可运行示例 | `nop-demo/`、`demo/` |

## 与文档配套的阅读顺序

1. 先看本页建立模块分组。
2. 再看 `domain-module-pattern.md` 理解业务模块骨架。
3. 最后看 `where-things-live.md` 快速定位具体文件。

## 相关文档

- `./domain-module-pattern.md`
- `./where-things-live.md`
- `../02-core-guides/model-first-development.md`

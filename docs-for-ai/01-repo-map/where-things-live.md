# 当前仓库里重要文件都在哪里

本页是一个面向 AI 的定位表，用来快速回答“我该去哪个目录找东西”。

## 源模型与生成入口

| 你要找什么 | 典型位置 | 说明 |
|-----------|---------|------|
| ORM 源模型 | `*/model/*.orm.xml` | 这是首选编辑入口 |
| 项目骨架生成脚本 | `*-codegen/postcompile/gen-orm.xgen` | 从模型驱动项目级生成 |
| XMeta 生成脚本 | `*-meta/precompile/gen-meta.xgen` | 基于 ORM 生成 XMeta |
| i18n 生成脚本 | `*-meta/postcompile/gen-i18n.xgen` | 基于 ORM / meta 生成 i18n |
| 页面生成脚本 | `*-web/precompile/gen-page.xgen` | 基于 XMeta 生成页面文件 |

## ORM、实体、服务与页面

| 你要找什么 | 典型位置 | 是否默认可编辑 |
|-----------|---------|----------------|
| 生成后的 ORM 聚合文件 | `*-dao/src/main/resources/_vfs/.../_app.orm.xml` | 否 |
| 保留层 Entity | `*-dao/src/main/java/.../entity/Xxx.java` | 是 |
| 生成的 `I*Biz` 接口 | `*-dao/src/main/java/.../biz/I*Biz.java` | 通常读多写少 |
| BizModel | `*-service/src/main/java/.../entity/*BizModel.java` | 是 |
| 局部 DTO（BizModel/Processor 共享） | `*-dao/src/main/java/.../dto/` | 是 |
| 外部 RPC Service Interface | `*-api/src/main/java/.../XxxApi.java` | 是 |
| 外部 RPC Message Bean | `*-api/src/main/java/.../beans/` | 通常由 codegen 生成 |
| xbiz 基类 | `*-service/src/main/resources/_vfs/.../_Xxx.xbiz` | 否 |
| xbiz 扩展文件 | `*-service/src/main/resources/_vfs/.../Xxx.xbiz` | 是 |
| service beans 生成文件 | `*-service/src/main/resources/_vfs/.../beans/_service.beans.xml` | 否 |
| 页面视图 | `*-web/src/main/resources/_vfs/.../*.view.xml` | 非 `_gen` 文件可编辑 |
| 页面 YAML | `*-web/src/main/resources/_vfs/.../*.page.yaml` | 是 |

## Delta 定制入口

| 场景 | 典型位置 |
|------|---------|
| 覆盖基础 ORM / beans / xbiz / view | `src/main/resources/_vfs/_delta/{deltaDir}/...` |
| 常用 delta 目录名 | `default` |

## 测试与 AutoTest

| 你要找什么 | 典型位置 |
|-----------|---------|
| AutoTest JUnit 基类 | `nop-autotest/nop-autotest-junit/src/main/java/io/nop/autotest/junit/` |
| 测试注解 `@NopTestConfig` | `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/annotations/autotest/` |
| 模块内单元测试 | 各模块 `src/test/java` |
| 快照数据 | `_cases/...` |
| 普通测试资源 | `src/test/resources/...` |

## Runner、CLI、Demo

| 你要找什么 | 典型位置 |
|-----------|---------|
| CLI 聚合模块 | `nop-runner/` |
| CLI 核心 | `nop-runner/nop-cli-core/` |
| CLI 打包模块 | `nop-runner/nop-cli/` |
| Windows 命令入口 | `scripts/nop-cli.cmd` |
| Demo 聚合 | `nop-demo/` |
| 额外模板与示例 | `demo/` |

## 最适合用来观察骨架的真实路径

| 模块 | 推荐观察路径 |
|------|-------------|
| `nop-auth` | `nop-auth/model/`、`nop-auth/nop-auth-codegen/`、`nop-auth/nop-auth-dao/`、`nop-auth/nop-auth-service/`、`nop-auth/nop-auth-web/` |
| `nop-job` | `nop-job/model/`、`nop-job/nop-job-codegen/`、`nop-job/nop-job-dao/`、`nop-job/nop-job-service/` |
| `nop-task` | `nop-task/model/`、`nop-task/nop-task-service/` |
| `nop-wf` | `nop-wf/model/`、`nop-wf/nop-wf-meta/` |
| `nop-ai` | `nop-ai/model/`、`nop-ai/nop-ai-codegen/`、`nop-ai/nop-ai-service/`、`nop-ai/nop-ai-agent/` |

## 编辑前的判断口诀

1. 数据结构改 `model/`。
2. 生成链路看 `*.xgen`。
3. 服务逻辑看 `BizModel`。
4. 页面扩展看非下划线 `view.xml` / `page.yaml`。
5. 差量覆盖看 `_vfs/_delta/`。

## 相关文档

- `./domain-module-pattern.md`
- `../02-core-guides/model-first-development.md`
- `../02-core-guides/view-and-page-customization.md`
- `../02-core-guides/testing.md`

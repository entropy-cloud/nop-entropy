# 源码锚点

本页不是类名速查表，而是 `docs-for-ai` 的规则锚点表。

用途：

1. 证明某条 AI 开发规则确实有源码依据
2. 帮助维护者在文档与源码冲突时，以源码为准校正文档
3. 让 runbook 和规范文档能够回链到稳定实现

---

## 一、核心锚点表

| 规则 ID | 锚点 | 这条锚点说明什么 |
|---------|------|------------------|
| `GEN-001` | `/nop/templates/orm` | ORM 模型是项目骨架和多模块生成的起点 |
| `GEN-002` | `*-codegen/postcompile/gen-orm.xgen` | `*-codegen` 负责从源模型驱动项目级生成 |
| `GEN-003` | `*-meta/precompile/gen-meta.xgen` | `*-meta` 负责生成 XMeta |
| `GEN-004` | `*-meta/postcompile/gen-i18n.xgen` | `*-meta` 负责生成 i18n |
| `GEN-005` | `*-web/precompile/gen-page.xgen` | `*-web` 基于 xmeta 生成页面文件 |
| `GEN-006` | `io.nop.job.dao.entity.NopJobSchedule` | 手写实体类继承 `_gen._NopJobSchedule`，说明生成物与保留层分离 |
| `BIZ-001` | `io.nop.orm.biz.ICrudBiz` | 标准 CRUD 业务接口是 AI 生成代码的重要契约 |
| `BIZ-002` | `io.nop.biz.crud.CrudBizModel` | 实体型服务默认基类是 `CrudBizModel<T>` |
| `BIZ-003` | `io.nop.biz.crud.CrudBizModel#requireEntity` | 普通 BizModel 取实体应走安全访问路径 |
| `BIZ-004` | `io.nop.biz.crud.CrudBizModel#prepareFindPageQuery` | `doFindList` / `doFindPage` 会追加权限、逻辑删除和对象元数据过滤 |
| `BIZ-005` | `io.nop.job.biz.INopJobScheduleBiz` + `io.nop.job.service.entity.NopJobScheduleBizModel` | 跨 BizModel 协作应通过 `I*Biz` 接口，而不是直接注入实现类 |
| `TXN-001` | `io.nop.biz.service.BizActionInvoker` | 非 query 的 Biz 操作默认会进入事务 |
| `IOC-001` | `io.nop.core.reflect.impl.ClassModelBuilder#discoverDeclaredFields` | private 字段会被跳过，因此 `@Inject private` 不会工作 |
| `INFRA-001` | `io.nop.job.dao.store.JobScheduleStoreImpl` | 原始 DAO、`saveEntityDirectly`、`REQUIRES_NEW` 属于 infra/store 层边界写法 |

---

## 二、规则到文档的映射

| 规则 ID | 主要影响文档 |
|---------|-------------|
| `GEN-001` ~ `GEN-005` | `01-core-concepts/ai-development.md`, `03-development-guide/project-structure.md`, `12-tasks/create-new-entity.md` |
| `GEN-006` | `01-core-concepts/ai-development.md`, `03-development-guide/project-structure.md` |
| `BIZ-001` ~ `BIZ-005` | `03-development-guide/bizmodel-guide.md`, `12-tasks/write-bizmodel-method.md`, `09-quick-reference/api-reference.md` |
| `TXN-001` | `04-core-components/transaction.md`, `04-core-components/exception-handling.md`, `12-tasks/transaction-boundaries.md` |
| `IOC-001` | `04-core-components/ioc-container.md`, `07-best-practices/testing.md`, `12-tasks/write-unit-test.md` |
| `INFRA-001` | `09-quick-reference/api-reference.md`, `04-core-components/exception-handling.md` |

---

## 三、使用方式

当你要新增或修改规范文档时，至少检查：

1. 这条规则是否已经有锚点
2. 如果没有，是否能在源码中找到稳定实现
3. 如果文档与锚点冲突，是否应先改文档而不是解释源码

---

## 四、当前最重要的校准点

1. 不要再把 `gen-service.xgen` / `gen-web.xgen` 写成通用生成链路
2. 不要在普通 BizModel 示例里把直接 `dao()` 访问写成默认做法
3. 不要把 `@BizMutation @Transactional` 写成普通服务层模板
4. 不要在 IoC 示例里出现 `@Inject private Foo foo;`
5. 不要把 infra/store 层的 DAO 直接操作误写成通用业务层模式

# 代码风格

本页只保留当前仓库里最稳定、最适合 AI 记住的风格约束。

## 命名与结构

- 类、接口：PascalCase
- 方法、变量：camelCase
- 常量：UPPER_SNAKE_CASE
- 接口命名：`I` + PascalCase
- 包名：`io.nop.<module-name>.*`

## 格式

- 4 空格缩进
- 行宽大致控制在 80-120 字符
- 保持 import 分组清晰：`java.* -> jakarta.* -> third-party -> io.nop.*`

## 当前仓库里的重要风格点

1. 避免 noisy refactor，diff 尽量聚焦。
2. 日志使用 SLF4J，不使用 `System.out` / `System.err`。
3. 错误处理采用两档策略（详见 `./error-handling.md`）：公共 API 用 `NopException + ErrorCode + .param(...)`；模块内部可用模块级异常类 + 英文字符串消息。
4. 不要把 Spring 专有注解、AOP 或注入模式当默认模板。

## ORM 命名规范

本节适用于 `model/*.orm.xml` 中的所有定义。以下规范来自全平台六模块（nop-auth/nop-job/nop-wf/nop-task/nop-batch/nop-dyn）的实际对齐。

### 根节点属性

| 属性 | 格式 | 示例 |
|------|------|------|
| `ext:appName` | `nop-{模块}` | `nop-job` |
| `ext:basePackageName` | `io.nop.{模块}` | `io.nop.job` |
| `ext:entityPackageName` | `{basePackageName}.dao.entity` | `io.nop.job.dao.entity` |
| `ext:dialect` | `mysql,oracle,postgresql` | — |
| `ext:registerShortName` | `true` | — |

### 实体与表名

| 维度 | 格式 | 示例 |
|------|------|------|
| entity name | `io.nop.{模块}.dao.entity.Nop{模块Pascal}{实体Pascal}` | `io.nop.job.dao.entity.NopJobSchedule` |
| entity className | 与 name 完全相同 | 同上 |
| entity shortName | `Nop{模块Pascal}{实体Pascal}` | `NopJobSchedule` |
| tableName | `nop_{模块}_{实体snake_case}` | `nop_job_schedule` |
| registerShortName | `true`（所有实体） | — |

### 列与字段

| 维度 | 格式 | 示例 |
|------|------|------|
| column code | UPPER_SNAKE_CASE | `JOB_SCHEDULE_ID` |
| column name | camelCase | `jobScheduleId` |
| 主键 | `id`, VARCHAR(36), `tagSet="seq"` | — |
| propId | 从 1 开始连续递增 | — |

### Dict

| 维度 | 格式 | 示例 |
|------|------|------|
| dict name | `{模块简称}/{kebab-case名称}` | `job/schedule-status` |
| dict option code | UPPER_SNAKE_CASE | `RUNNING`, `AUTO_CANCEL` |
| dict option value | 整数 10/20/30 递增 | `10`, `20`, `30` |
| dict valueType | `int`（状态/枚举字段统一） | — |
| 字段引用 | `ext:dict="{模块简称}/{kebab-case名称}"` | `ext:dict="job/schedule-status"` |
| 禁止 | boolean 型字段不设 dict | — |

> **关键约束**：dict name 斜杠后的部分**必须用 kebab-case**（如 `schedule-status`），不能用 snake_case（如 ~~`schedule_status`~~）。全平台无一例外。

### Domain

- domain name 全局唯一，不能重名
- 优先复用已有 domain，不重复定义
- 只有具有重要业务含义的字段才需要 domain

### 关系

| 维度 | 规范 |
|------|------|
| to-one | `tagSet="pub"` |
| to-many | `tagSet="pub,cascade-delete,insertable,updatable"` |
| refEntityName | 全限定名 `io.nop.{模块}.dao.entity.NopXxxYyy` |

## IoC Bean 命名

- 当前仓库里的平台内置 bean 大量使用 `nop` 前缀，这是强约定而不是 IoC 硬性保留规则。
- 业务自定义 bean 默认避免复用 `nop*` 命名，除非你明确要接入或替换框架的命名型扩展点。
- 测试 bean 可使用 `test` / `testMock` 前缀。

## 相关文档

- `./model-first-development.md`
- `./service-layer.md`
- `./error-handling.md`
- `../00-start-here/ai-defaults.md`

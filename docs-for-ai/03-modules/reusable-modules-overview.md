# Nop 平台可复用业务模块总览

Nop 平台内置了大量开箱即用的业务模块。在应用项目中**不要重复造轮子**，应优先评估平台模块是否已满足需求。

## 模块总表

| 模块 | 一句话描述 | 有 Web UI | 有 ORM 实体 | 依赖 |
|------|-----------|-----------|------------|------|
| [nop-auth](nop-auth.md) | 用户认证、RBAC 权限、多租户、SSO、OAuth2 | Yes | Yes | nop-service-framework |
| [nop-sys](nop-sys.md) | 序列号、数据字典、国际化、审批流、分布式锁、事件队列 | Yes | Yes | nop-auth |
| [nop-report](nop-report.md) | 报表引擎，支持 Excel/PDF/DOCX 输出 | Yes | Yes | nop-service-framework |
| [nop-rule](nop-rule.md) | 规则引擎，决策树 + 决策矩阵 | Yes | Yes | nop-service-framework |
| [nop-task](nop-task.md) | 通用任务/逻辑流引擎，多步骤编排 | Yes | Yes | nop-service-framework |
| [nop-wf](nop-wf.md) | 工作流/BPM 引擎，复杂审批、会签、加签、委托 | Yes | Yes | nop-task |
| [nop-batch](nop-batch.md) | 批处理引擎，chunk 处理、断点续传、幂等 | Yes | Yes | nop-service-framework |
| [nop-job](nop-job.md) | 分布式定时任务调度，CRON/固定频率/一次性触发 | Yes | Yes | nop-service-framework |
| [nop-ai](nop-ai.md) | AI 集成，LLM Chat、Prompt 管理、Agent、RAG、MCP | Yes | Yes | nop-service-framework |
| [nop-dyn](nop-dyn.md) | 动态表单/实体，运行时定义业务模型 | Yes | Yes | nop-service-framework |
| [nop-file](nop-file.md) | 文件上传/下载/管理，Hash 去重 | Yes | Yes | nop-service-framework |
| [nop-retry](nop-retry.md) | 分布式重试引擎，可配置退避策略 | Yes | Yes | nop-service-framework |
| [nop-tcc](nop-tcc.md) | TCC 分布式事务协调器 | Yes | Yes | nop-service-framework |
| [nop-code](nop-code.md) | 多语言代码索引与智能分析 | Yes | Yes | nop-service-framework |

## 基础设施模块（无 ORM，按需集成）

| 模块 | 一句话描述 |
|------|-----------|
| `nop-message` | 消息抽象层，Kafka/Pulsar/Debezium 连接器 |
| `nop-cluster` | 集群基础设施，服务发现、负载均衡、限流（Sentinel）、Nacos 集成 |
| `nop-search` | 搜索引擎抽象，Lucene 实现 |
| `nop-integration` | 外部集成适配器：邮件、短信（腾讯/云片）、文件存储（本地/OSS/SFTP）、二维码 |
| `nop-network` | 网络通信：HTTP、Netty、RPC、Socket、Vert.x |

## 按业务场景选择模块

| 场景 | 推荐模块 | 说明 |
|------|---------|------|
| 用户登录/注册/权限 | **nop-auth** | 完整 RBAC + 多租户，缺省用户 nop/123 |
| 菜单/按钮级权限控制 | **nop-auth** | NopAuthResource 资源树 |
| 数据级权限（行级过滤） | **nop-auth** | NopAuthRoleDataAuth |
| 单点登录(SSO)/OAuth2 | **nop-auth** + `nop-oauth` | 子模块 nop-auth-sso、nop-oauth |
| 数据字典 | **nop-sys** | nop_sys_dict / nop_sys_dict_option |
| 序列号/编号生成 | **nop-sys** | nop_sys_sequence，支持多种重置策略 |
| 国际化(i18n) | **nop-sys** | nop_sys_i18n |
| Maker-Checker 审批 | **nop-sys** | nop_sys_checker_record |
| 分布式锁 | **nop-sys** | nop_sys_lock |
| 事件队列（进程内） | **nop-sys** | nop_sys_event，分区扫描 |
| 扩展字段(EAV) | **nop-sys** | nop_sys_ext_field |
| 业务编码规则 | **nop-sys** | nop_sys_code_rule |
| 报表/导出 | **nop-report** | Excel 模板 → XLSX/PDF/DOCX |
| 规则/决策 | **nop-rule** | 决策树 + 决策矩阵，版本管理 |
| 异步任务/编排 | **nop-task** | 多步骤逻辑流，信号、重试、超时 |
| 审批工作流 | **nop-wf** | 基于 nop-task，会签/加签/委托/转办/驳回 |
| 批量导入/导出 | **nop-batch** | Chunk 处理、断点续传、记录级幂等 |
| 定时任务 | **nop-job** | CRON 表达式，协调器/工作者架构 |
| 失败重试 | **nop-retry** | 固定间隔/指数退避，命名空间隔离 |
| 分布式事务 | **nop-tcc** | TCC 模式，分支事务管理 |
| 文件上传/下载 | **nop-file** | Hash 去重、业务对象关联 |
| AI/大模型集成 | **nop-ai** | Prompt 模板、Agent、RAG、MCP Server |
| 动态表单/低代码 | **nop-dyn** | 运行时定义实体/页面/SQL |
| 消息队列集成 | **nop-message** | Kafka/Pulsar 适配 |
| 全文搜索 | **nop-search** | Lucene 实现 |
| 邮件/短信发送 | **nop-integration` | 邮件（Java/腾讯）、短信（腾讯/云片） |
| OSS 文件存储 | **nop-integration` | 阿里 OSS、SFTP |

## 快速启用方式

在应用项目的 `pom.xml` 中引入对应模块的 `-web` 依赖即可启用：

```xml
<!-- 示例：启用报表模块 -->
<dependency>
    <groupId>io.nop.report</groupId>
    <artifactId>nop-report-web</artifactId>
</dependency>
```

启用后：
- ORM 实体会自动建表（H2 模式）或通过 DB Migration 同步
- GraphQL API 自动注册
- AMIS 管理页面自动挂载到菜单
- 相关 beans 自动装配

## 相关文档

- `../01-repo-map/module-groups.md` — 仓库模块分组
- `../01-repo-map/where-things-live.md` — 文件位置定位
- `../02-core-guides/auth-and-permissions.md` — 权限体系详解

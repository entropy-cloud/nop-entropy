# Nop 平台最佳实践合规审计 · 总结（2026-09-12 21:30）

> 方法：`ai-dev/skills/nop-platform-conformance-audit-prompt.md` 模板驱动。规则手册（R1-R16）提炼自 `docs-for-ai/` 权威文档并经平台参照模块（nop-auth/wf/job/task/sys）实地校准。
> 覆盖：全仓 22 项自动化扫描（evidence/）+ 人工深审 nop-ai（1047 文件）、nop-stream（651）、nop-metadata、参照模块基线；nop-datav 等未深审区见 06。
> 报告清单：`01` 规则与基线 · `02` 全仓扫描 · `03` nop-ai · `04` nop-stream · `05` nop-metadata · `06` 未深审热点 · `evidence/` 扫描证据。

---

## 裁决：**失败**（整体未达平台最佳实践要求；但分层差异极大，正面样板与重灾区并存）

## 一个重要的基线澄清（先读这个）

任务描述中"每个业务方法对应一个 Processor"经基线校准**不是平台参照系的实际形态**：五个参照模块 73 个 BizModel 对 **0 个业务 Processor**，编排由「BizModel 方法 + private helper」「-core 引擎 Manager」「-dao store/helper」三种形态承担（实证见 01 §2.1）。文档规范（`domain-logic-and-ddd.md`）的准确表述是**复杂多步流程才拆 Processor**。因此本审计对"过程式编程"的定性口径是：**超长多步大方法（>80 行）、贫血实体（0 领域方法）、状态判断散落**——按此口径，nop-ai-agent 与 nop-metadata 的贫血模型、nop-ai 的 49 个超长方法均构成系统性偏离（并非"缺 Processor 类"这一形式本身）。

## 维度合规率（业务模块视角）

| 维度 | nop-ai | nop-stream | nop-metadata | 参照模块（基线） |
|------|--------|-----------|--------------|----------------|
| BizModel/I*Biz 规范（R4/R7/R8/R9） | ~85%（service 层满分，tools/mcp 契约缺陷） | —（无 BizModel 层） | ~95% | ~98% |
| 领域逻辑落位/防过程式（R10） | **~50%**（49 超长方法，22 实体 0 领域方法） | ~75%（24 超长方法 + 巨型协调器） | ~75%（拆分优秀但 39 实体全贫血） | ~90% |
| 跨实体访问管道（R5/R6） | ~85% | ~85% | **~55%（唯一 P1：85 处 BizModel 直连 DAO）** | ~90%（有登记例外） |
| 平台能力复用（R3） | **~50%**（7 项自建：重试/熔断/消息/调度/向量/raw-JDBC/状态机） | ~85%（ops 面 2 处手写重试） | ~90% | ~95% |
| 异常与 ErrorCode（R13） | ~70%（157 处裸消息 + ~150 裸 IAE/ISE） | **~60%**（293 处裸异常） | ~95%（模范） | ~95%（11 处 IAE 偏差） |
| 工具约定（R14） | ~80%（裸时间 102 处、VFS/getBytes 残留） | **~65%**（裸时间 95 处） | ~95%（裸时间 3 处） | ~90%（MFA store 26 处裸时间） |
| DSL 优先/状态机（R2/R12） | ~85%（dict 有，enum 固化 57 个） | ~90%（xdef/SPI/转换表，模范） | ~80%（字面量双标准） | ~95% |
| IoC（R9） | ✅ 100% | ✅ 100% | ✅ 100% | ✅ 100% |

**全仓满分项**：`@Inject private` = 0、`@BizMutation+@Transactional` = 0、跨层 DAG 违规 = 0、Spring 注解泄漏 = 0、直连 Jackson/Gson = 0（benchmark 除外）。

## P1 发现（6 项，编号可溯源到分报告）

| # | 发现 | 模块 | 修复方向 |
|---|------|------|---------|
| P1-1 | raw-JDBC 持久化栈与 ORM 管理表**双写**（`nop_ai_session_message` 两条写入路径，14 类绕过 ORM） | nop-ai-agent（03 AI-2 ✅verified） | 写路径改 I*Biz/DAO；自建表入模或登记 store 边界 |
| P1-2 | `ReActAgentExecutor.execute` **706 行**过程式主循环 + 10 处 if-else 状态流转；模块 >80 行方法 49 个、22 实体 0 领域方法 | nop-ai-agent（03 AI-1/AI-12 ✅verified） | 按 nop-task-core 阶段化拆分；实体补 isXxx/canXxx |
| P1-3 | BizModel 层 **85 处** `daoFor().getEntityById()` 直连跨聚合实体（I*Biz 注入仅 1 处），静默绕过数据权限管道 | nop-metadata（05 MD-1 ✅verified） | 逐文件改 I*Biz/关系 getter，或 owner doc 登记边界 |
| P1-4 | 裸时间 API 面状扩散：全仓 202 文件（ai-agent 42、stream-runtime 18、datav 16、auth 15...），写库时间戳直接破坏 IClock/TestClock 时间线 | 全仓（02 §F-A；04 ST-1） | 机械替换 CoreMetrics.*，过期/租约/超时路径优先 |
| P1-5 | 裸异常 293 处，REST 边界退化成 `instanceof ISE` + 消息字符串嗅探 | nop-stream（04 ST-2 ✅verified） | 统一 StreamException + NopStreamErrors |
| P1-6 | 对外 API 契约缺陷：`@BizQuery clearHistory()`（写操作走查询）、`@Name("String")`、AiFileTool 方法无 Biz 注解且错误用字符串返回 | nop-ai-tools/mcp（03 AI-3/4/6 ✅verified） | 三处低成本修复（注解/参数名/ErrorCode） |

P2 共 21 项、P3 共 19 项——逐条含证据与修复方向见分报告 03/04/05。

## 自建重复能力清单（"应系统化使用平台内置能力"维度的实证）

| 自建能力 | 位置 | 平台已有 |
|---------|------|---------|
| 重试框架 + 熔断器（20 类，退避/抖动/熔断状态机全量重实现） | nop-ai-core/reliability | nop-retry `IRetryEngine` |
| DB 消息传输（660 行：建表+轮询+抢占+清扫） | nop-ai-agent DBMessageService | nop-message-core |
| 调度守护线程 ×3 + DB 互斥选主 | nop-ai-agent | nop-job（调度/分布式锁） |
| 向量/嵌入检索抽象（17 文件，平行于已复用的 ISearchEngine） | nop-ai-agent/memory | nop-search |
| raw-JDBC 持久化栈（14 类） | nop-ai-agent | nop-orm |
| webhook 手写重试循环 + 手写 JSON 拼接 | nop-stream WebhookAlertChannel | nop-retry / JsonTool |
| 业务状态机 Java enum 固化（57 个 enum） | nop-ai-agent | dict/状态机 DSL（模块自身已用 dict 管 session-status，双标准） |

## 做对的（应作为扩散样板）

- nop-metadata：失败路径显式化（0 静默吞错）、10 文件分组 ErrorCode、接口完整性守卫测试、owner doc 裁定登记文化。
- nop-stream：xdef 元模型 + beans.xml SPI 注册、声明式健康状态机转换表。
- nop-ai-service：22 个 CrudBizModel + I*Biz 契约 + nop-credential 引用计数接线，~90% 合规。

## 残留风险

1. **多租户权限旁路面**：MD-1 的 85 处 + nop-datav 待定性的 25 文件，在租户 UK 变体派生启用后整体成为数据权限漏洞面。
2. **时钟不可测**：202 文件裸时间使 TestClock 对过期/租约/调度断言失效——这正是近期多次时序敏感测试 flaky 修复（git log 2026-09）的同类根因。
3. **双写路径**：P1-1 在软删/租户/审计字段演进时必然产生数据不一致。
4. **审计盲区**：nop-datav 等未深审区（06）。

## 建议整改顺序

1. P1-6 契约三连修（小时级）→ 2. P1-3 metadata DAO 收口（含 owner doc 裁定）→ 3. P1-5 stream 异常 ErrorCode 化 → 4. P1-4 全仓 CoreMetrics 机械替换（可脚本化）→ 5. P1-1/P1-2 nop-ai-agent 双写与主循环重构（需立项，见 `ai-dev/plans/`）→ 6. 06 §下一轮动作（nop-datav 深审 + 基线结论回写 docs-for-ai）。

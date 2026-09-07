# docs/lessons、docs/bugs、ai-dev/lessons、ai-dev/bugs 内容吸收决策分析

> Status: open
> Date: 2026-09-07
> Scope: nop-app-erp（外部仓库）docs/lessons（18 条）+ docs/bugs（46 条）；本仓 ai-dev/lessons（16 条）+ ai-dev/bugs（17 条）→ 是否吸收进 docs-for-ai（自包含、使用层面知识）
> Conclusion: open — 决策已定（见 §决策），实施范围待用户确认

## Context

- 用户要求：仔细分析上述四个来源的教训/缺陷内容，判断是否有必要吸收到 `docs-for-ai/` 作为"使用层面需要知道的内容"，并做出选择。
- 前置澄清：`docs/lessons` 与 `docs/bugs` 是 **nop-app-erp 外部项目仓库**（`/Users/abc/app/nop-app-erp/`）的目录，本仓不存在；docs-for-ai 中对它们的引用是坏链（已修复，见 §坏链修复）。
- 约束：docs-for-ai 必须**自包含**，不得引用 `docs/` 或 `ai-dev/`（含外部项目的 docs 路径）。吸收 = 把通用规则**提纯**后落到 docs-for-ai 对应 owner doc，不是逐条搬运原文。

## 来源盘点

| 来源 | 数量 | 性质 |
|---|---|---|
| nop-app-erp/docs/lessons | 18 | ERP 实战返工教训（部分已提纯进 pitfalls 速查页） |
| nop-app-erp/docs/bugs | 46 | ERP 回归 bug 记录（根因多为"Nop 平台/前端/测试框架用法"） |
| 本仓 ai-dev/lessons | 16 | nop-entropy 平台本体开发教训 |
| 本仓 ai-dev/bugs | 17 | nop-entropy 平台本体 bug 修复记录 |

**关键发现**：docs-for-ai 的 `00-start-here/application-project-pitfalls-{backend,frontend}.md`（铁律 1-10）已把多条 erp 教训提纯吸收（标注"源自 nop-app-erp 实战返工"）。因此不少 erp 内容**已在 docs-for-ai**，本决策重点在"未覆盖/已过时/可升级"项，避免重复。

## 判定标准

- **A 平台使用通用教训** → 吸收（任何 Nop 应用开发者需要，与业务无关或弱相关）
- **B 半通用** → 提炼通用规则后吸收（核心是 Nop 机制，例子是 erp）
- **C 不吸收** → erp 业务语义 / 平台本体开发过程纪律 / 外部仓库协议域，保留原地

---

## 一、本仓 ai-dev/lessons（16 条）决策

| # | 主题 | 判定 | 决策 |
|---|---|---|---|
| 01 | 分批处理≠流式处理（内存累积 OOM） | C 通用工程 | 保留 |
| 02 | Metrics 三件套（禁直接注入 MeterRegistry） | C 平台开发 | 保留 |
| 03 | Plan Guide 强制 | C 过程 | 保留（AGENTS.md 已有） |
| 04 | git checkout 目录回退丢修改 | C 过程 | 保留 |
| 05 | Overclaimed Closure 证据漂移 | C 过程 | 保留 |
| 06 | 凭证字段跨层收敛必须回 ORM 源模型 | A | **已吸收**（`model-first-development.md:355-380` 凭证/敏感字段多层收敛约定，含历史教训注） |
| 07 | Zero-test 模块 CI 不可见 | C 过程 | 保留 |
| 08 | Tool executor 安全边界 | C 平台开发 | 保留（nop-ai 内部） |
| 09 | unique-key 缺 constraint → DDL 静默缺失 | A | **已吸收**（`invariant-guards.md` INV-UK 门禁 + 规则） |
| 10 | 补日志≠修根因 | C 过程 | 保留 |
| 11 | 审计计数口径勘误 | C 过程 | 保留 |
| 12 | xwf *end listener 不判结束原因（驳回即通过） | A | **部分吸收**（`enable-approval-on-entity.md:151-165` wf-approval `*end` 回调 + `build-approval-flow.md` 驳回处理）；可补强显式一条（见 Open Questions） |
| 13 | 空洞断言测试 | C 过程 | 保留（testing.md 已有 unit-test-antipatterns） |
| 14 | 条件激活旁路面三方实证 | C 过程 | 保留 |
| 15 | optional 依赖禁类签名引用 → ioc:collect-beans | A | **部分吸收**（`ioc-and-config.md` §collect-beans）；"optional+类签名=启动崩溃"反模式未显式记录（可补强） |
| 16 | XLang DSL 调用点对 Java grep 不可见 | C 平台开发 | 保留 |

**结论**：ai-dev/lessons 无新增独立吸收项——06/09/12/15 的使用层规则均已（或大部分）在 docs-for-ai。可选补强：12、15 各加一条显式反模式（低优先）。

## 二、本仓 ai-dev/bugs（17 条）决策

- **需 docs-for-ai 行动（3 处）**：
  - `#7/#8 VarCollector`（08-12/08-14）：`AutoTestCase` 已由"置 null 不恢复"改为恢复 no-op 实例（`AutoTestCase.java:232`），**`testing.md:25-31` 描述的是修复前行为，属 docs bug，需同步修正**。
  - `#9 missing-tenant-id`（08-17）：`assignConfigValue` 的 ref 值跨测试类存活、`reset()` 只恢复 System-property 来源 → **testing.md 新增"测试配置隔离"规则**（per-class 用 `@NopTestProperty` + `@AfterAll` 显式恢复）。
  - `#10 nop-retry double-save`（08-18）：部分使用层教训（`orm_disableAutoStamp`、callbackPolicyId 自指递归）可选吸收进 model-first-development.md / nop-retry.md。
- **并入 ai-dev/lessons（通用工程教训，不吸收进 docs-for-ai）**：`#1`（正则取首/取尾语义）、`#6`（CAS 去重 flag 末尾清）、`#14`（时序断言同源时钟）、`#15`（对比基准交错采样）。
- **保留原地**：`#2/#3/#5/#11/#12/#13/#16/#17/#18`（平台引擎内部竞态/编译器 bug/引擎契约，无使用者可绕规则；`#3` 使用层知识已吸收进 `concurrency-and-transactions.md`）。

## 三、nop-app-erp/docs/lessons（18 条）决策

**P0 正式补规范（当前仅速查一行或缺失，值得完整条目）**

| # | 主题 | 提炼的通用规则 | 落点 |
|---|---|---|---|
| 15 | xbiz XScript 编排下沉 | **前提已过时（勘误）**：erp 2026-08 写成时 try/catch 尚不可执行（plan 2258 未落地，`TryStatement` 无 AST→Executable 构造点）；**master 已于 `c6d8f66a12`（plan 2258）支持 XScript try/catch/finally（JS 语义：catch 吞异常/显式 throw 重抛/finally 必执行）**。正确做法：向 xlang-and-xpl-basics.md 写"XScript 支持 try/catch/finally"文档（已写入），并保留"跨实体事务/多路失败隔离重逻辑下沉 Java Bean 是架构偏好、非能力限制" | `02-core-guides/xlang-and-xpl-basics.md`（已写入正确表述） |
| 01 | 跨模块外部实体 tableName 双重前缀 | `notGenCode="true"` 外部实体引用的 `tableName` 必须与被引用源域完全一致（完整物理表名），codegen 不二次拼接，禁加引用方域前缀 | `02-core-guides/cross-module-entity-reference.md`（补反模式） |
| 05 | E2E 日志优先六步法 | Playwright 超时/空白是"果"：自起干净 server + 最小复现（pass+fail 对照）→ 倒查服务端 `errorCode=`/`@_loc=[行:列]`/`Caused by:`；后端 mvn 全绿≠前端渲染绿 | `02-core-guides/e2e-testing-troubleshooting.md`（补系统性因果链法） |

**P1 升级为正式规范（速查已有，受益于完整条目）**

| # | 主题 | 落点 |
|---|---|---|
| 10 | dict 死状态：每个状态值须有 ≥1 个可达写入路径（grep writer，无则删/实现/Deferred 三选一） | `02-core-guides/orm-model-design.md`（状态机/dict 建模节） |
| 16 | 跨层 schema 契约失效是**静默降级**：验证必须读消费端源码解析点 + 深度断言 + 抽样落盘统计 | `02-core-guides/flux-rendering.md`（补强制清单） |
| 18 | 破坏性契约重设计：消费者只读 binding 不读 type；禁 sugar 兼容层；红测试=迁移清单 | `02-core-guides/flux-rendering.md` / `frontend-rendering-pipeline.md` |

**核对即可（已完整吸收）**：`04`（testing.md:35-57 已含直调陷阱与 @SingleSession 误判）、`06`（AGENTS.md Hard Stop + pitfalls 铁律 1 已覆盖）。

**不吸收（C）**：`02/03/07/08/09/11/12/13/14/17`——erp 业务语义（09 业财过账）、erp 自建 checker/追踪体系过程纪律、或通用版已提纯进 pitfalls 铁律 9/10。

## 四、nop-app-erp/docs/bugs（46 条）决策

**8 大吸收主题（按 erp 侧逐条 A/B 归类合并）**

| 主题 | 涉及 bug | 提炼的通用规则 | 落点 |
|---|---|---|---|
| 1. 测试时间冻结三连 | #2/#13/#33 | 生产禁裸 JDK 时钟（走 `CoreMetrics`）；测试冻结 `CoreMetrics`/autotest `TestClock`；自定义冻结时钟不得破坏 TestClock 单调与日期同源 | `02-core-guides/testing.md`（新节"时间可控性"） |
| 2. IoC/delta 三件套 | #17/#22/#23 | `ioc:lazy-property` 创建阶段不可用（@PostConstruct 内开事务会炸）；`bean-init-self-wait` 断环；beans.xml delta 缺 `x:extends="super"` 整文件替换丢平台 bean | `ioc-and-config.md` + `delta-customization.md` |
| 3. 前端双引擎能力面 | #25/#30/#7/#8 | flux 与 AMIS 机制子集不同（download/button-toolbar/NOW()）；手写 GraphQL 串用 `@query:` 免裸 `$var` 模板解析 | `flux-rendering.md` + `api-and-graphql.md` |
| 4. 权限声明层同步 | #27/#29/#4 | 新增 mutation/掩码/字段可见性变更必须同步 action-auth FNPT + XMeta auth + 角色种子，否则 enforcement 下死锁/观察空洞；敏感字段默认全开须显式声明 | `auth-and-permissions.md` |
| 5. 页面 DSL 语法规则组 | #6/#18/#19/#21 | `=` 前缀 group-line 语法；cell 属性 xview 白名单；`<option>` XML→JSON 形态；BizLoader 虚拟字段须 custom cell；页面模型校验是安全网 | `layout-syntax-reference.md` / `view-and-page-customization.md` / `add-bizloader-field.md` |
| 6. autotest 快照与并发 | #20/#24/#14/#34 | surefire parallel=classes 与全局静态容器生命周期冲突（reuseForks=false）；快照并发行用 `@var` 不用字面 id；并发断言用错误码集合；全局注册组件类级注销 | `testing.md`（快照/并发节） |
| 7. ORM 会话边界 | #31 | 同事务"实体写→DB 直查/聚合读回"必须显式 `flushSession()` | `concurrency-and-transactions.md` / `transaction-boundaries.md` |
| 8. 建模与 DAO 纪律 | #1/#3/#9 | 外部实体引用只用 `refEntityName` 继承权威表名；BizModel 写操作走 CrudBizModel 父类 API；生成业务编码对齐目标列 domain precision | `cross-module-entity-reference.md` / `service-layer.md` / `orm-model-design.md` |

**真正缺口（最高价值，多文件 grep 确认 docs-for-ai 未显式记载）**

| 缺口 | 提炼规则 | 落点 |
|---|---|---|
| GraphQL filter 运算符白名单 | 日期范围无 `le/ge`，须用 `dateBetween`/`dateTimeBetween`；`eq/in` 等白名单 | `api-and-graphql.md` / `dql-query.md` |
| findPage 参数位置 | `__findPage` 只收 `query`(QueryBean)+selection+context，`limit` 须置于 `query:{limit}` 内，顶层传参报 `undefined-field-arg` | `api-and-graphql.md`（crud 签名表补反例） |
| 编码规则日期源绕过 IClock | `SysCodeRuleGenerator`←`nopSysCalendar` 默认直读 `LocalDateTime.now()`，绕过冻结时钟；测试须 delta 覆盖该 bean | `testing.md` 冻结时钟节 + `generate-business-code.md` |
| flux data-source 发布时序 | data-source 经 useEffect 注册晚于首渲染，mount 期公式须 undefined-safe 护栏；求值失败不得清空依赖 | `flux-rendering.md` |
| 快照批量改写协议 | 持久化到共享表的运行时字面量=跨模块契约面：批改前全仓 grep `_cases`+`src/test`，验证门=全 reactor `mvn test` 非 `-am` | `testing.md` |
| E2E 收口门禁 | Playwright 收口须跑 fixtures 守卫套件（双 amis 实例/无 pageerror 守卫的调试 spec 不能作收口） | `e2e-testing-troubleshooting.md` |

**C 级保留**：约 8 条（erp 业务口径 #2115/#26/#28/#32 等；09-03 dual-amis 根因在 nop-chaos-next 外部打包协议域，不吸收）。

---

## 决策（结论）

1. **吸收策略 = 提纯通用规则 + 落 owner doc 对应小节**，与 pitfalls 速查页风格一致；不逐条搬运原文。
2. **P0 优先实施**（真正缺口 + 高价值 + 文档一致性修正）：
   - GraphQL filter 运算符白名单 + findPage 参数位置（纯平台 API 契约，缺口最明确）
   - testing.md：VarCollector 过时描述修正（docs bug）+ 测试时间冻结节 + 测试配置隔离（missing-tenant-id）+ 快照/并发规则 + 运行时字面量批量改写协议
   - XScript try/catch/finally 支持文档化（xlang-and-xpl-basics.md；L15 前提已勘误——try/catch 现可执行）
   - 跨模块 tableName 双重前缀（cross-module-entity-reference.md）
   - E2E 日志优先六步法（e2e-testing-troubleshooting.md）
   - 编码规则日期源绕过 IClock（generate-business-code.md）
   - flushSession 同事务读回（concurrency-and-transactions.md）
3. **P1 次之**：dict 死状态（orm-model-design.md）、权限声明层同步（auth-and-permissions.md）、前端双引擎能力面（flux-rendering.md + api-and-graphql.md）、IoC/delta 三件套（ioc-and-config.md + delta-customization.md）、autotest 快照与并发（testing.md）、页面 DSL 语法规则组。
4. **不吸收**：erp 业务语义、平台本体开发/审计过程纪律、外部仓库协议域、以及已在 docs-for-ai 覆盖者。
5. **坏链修复（已完成）**：docs-for-ai 不再引用 `docs/lessons/`、`docs/architecture/i18n-compliance.md`、`deploy/sql/`（外部/生成路径），`check-doc-links.mjs --strict` 已达 0 errors。

## Open Questions

- [ ] P0+P1 吸收涉及约 10 个 owner doc 文件、~15 处编辑，是否本轮一次性实施，还是先 P0？
- [ ] ai-dev/lessons #12（*end listener）、#15（optional 依赖）是否补显式反模式一条？
- [ ] plan 2258（active，另一 worktree 可能执行中）的 8 个源码简写路径 warning 链接，是否等该 plan 收口后再修？

## References

- 分析输入：`ai-dev/analysis/2026-09/2026-09-07-palantir-ontology-on-nop-feasibility.md`（前序任务）
- 来源目录：`/Users/abc/app/nop-app-erp/docs/lessons/`、`/Users/abc/app/nop-app-erp/docs/bugs/`、`ai-dev/lessons/`、`ai-dev/bugs/`
- docs-for-ai 吸收宿主：`00-start-here/application-project-pitfalls-*.md`、`02-core-guides/{testing,api-and-graphql,xlang-and-xpl-basics,cross-module-entity-reference,orm-model-design,ioc-and-config,delta-customization,concurrency-and-transactions,auth-and-permissions,flux-rendering,frontend-rendering-pipeline,view-and-page-customization,layout-syntax-reference,e2e-testing-troubleshooting}.md`、`03-runbooks/{generate-business-code,add-bizloader-field}.md`、`02-core-guides/invariant-guards.md`

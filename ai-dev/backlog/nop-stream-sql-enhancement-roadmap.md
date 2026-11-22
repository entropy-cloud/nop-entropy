# nop-stream-sql 增量增强 Roadmap（窄范围 SQL 第二批）

> Last updated: 2026-10-08（v2 **roadmap 收口**——18/18 WI done，M0-M3 全达成，roadmapAllDone=true；WI0a D11/D10 放行、WI0b D5 keyed TopN 放行/D7 维持/ASOF 维持/字段扩展补记通过、WI37 emit 两档、WI38 FU-8 修复、WI25 lateness 上浮（Q1 列级裁定）、WI26 HOP/SESSION 伪表函数、WI27 非对称 join timeout（配对面≡对称 min 语义披露）、WI28 全局聚合放行（合成单键单归约）、WI29 CASE/CAST 放行（闭集限定）、WI30 UDF SPI（IoC/静态持有同构披露）、WI31 规则表化（四维+文档锚点）、WI32 CTE 销号（pass-through 不可行→FU-E6a）、WI33 命名 Proof、WI34 保留期 Proof、WI35 idle source 销号（引擎已有→FU-E8）、WI36 IR 销号（可选尾项→FU-E9）、WI39 lookup join 销号（DSL 面不存在）、WI40 keyed TopN 销号（OVER grammar→FU-E7）；FU-E1~E9 Backlog 登记；各 WI 独立 closure audit 或自审证据落 ai-dev/audits/nop-stream-sql-enhancement/；plans 01-18 落 ai-dev/plans/nop-stream-sql-enhancement/。v1 立项——依据 `ai-dev/analysis/streaming-sql-survey/2026-10-07-nop-stream-sql-gap-and-recommendations.md` 的分级建议：Tier A 新项 13 + FU-8 修复 1 + 裁定门 2 + 受裁实施 2；nop-stream-sql-roadmap 已收口，本 roadmap 继承其 Follow-up Backlog「晋级正式 WI 从 WI25 起顺延取号」约定，取号 WI25-WI40；owner 立项授权 = 2026-10-07 会话指令「用独立子agent反复审查改进直到达成共识」前的 roadmap 确认）
> 位置：按仓库 roadmap 惯例存放于 `ai-dev/backlog/`。书写约定：未来交付物路径用普通文本书写、不加反引号；已存在的文档路径用反引号，持续受 check-doc-links 保护。
> mission-driver 兼容：本文 `## Work Item Status` 采用 `- 名称: \`status\`` 形态（`tools/mission-driver/src/roadmap-check.mjs:23` 的 `BULLET_RE`，状态集见 `:14`），共 **18 个工作项 + 4 个里程碑**，起草与修订时已实测 `parseRoadmapMarkdown` 全量解析（见文末机器校验记录）。**状态行尾部括注必须单层，且括注内禁字符精确为 `）` 与 `)` 任一**——`BULLET_RE` 只允许行尾一组 `（…）`，其内出现 `）` 或 `)` 即整行静默丢弃；括注之后追加任何文字同样匹配失败。另两个真实解析器行为：里程碑行状态写 ready 会被 BULLET_RE 兜底误归类为工作项致分母 18 变 19——重跑解析器核对 18 即可捕获；改任何状态行后必须重跑解析器核对条目数仍为 18。**尚无 mission 配置**（ai-dev/archived/missions-2026-11-22/ 下无本 roadmap 条目）；建 mission 的前置沿袭 sql roadmap D15/Q1——`commands.test` 是否带 `-Dnop.test.docker.enabled=true` 未决，未决前不影响手工按 plan-first 驱动。
> 数据口径：仓库内锚点于 2026-10-07 实测；**外部项目事实一律以 survey 14 篇分析为转引来源**（各篇已自行完成路径验证），本 roadmap 不直接引用外部仓库行号。本分支存在在飞改动（multi-engine WI8 系列等），行号锚点会漂移——**每个 WI 开工时必须以 live 代码 re-verify 锚点**，失效则以当时实现为准修订完成判定。
> Sources:
> - `ai-dev/analysis/streaming-sql-survey/2026-10-07-nop-stream-sql-gap-and-recommendations.md`（本 roadmap 的直接依据：Tier A/B 清单、参考出处、去重结论）
>   - 编号映射：survey 建议编号已按本表重排——A7→WI25、A3→WI26、A12→WI27、A5→WI28、A2→WI29、A11→WI30、A10→WI31、A4→WI32、A9→WI33、A8→WI34、A13→WI35、A14→WI36、A6→WI39、B3 前置 FU-8→WI38、B2→WI0a+WI37、B4→WI0b+WI40；跨文档对照以本映射为准
> - `ai-dev/analysis/streaming-sql-survey/2026-10-07-streaming-sql-architectures-comparison.md`（五谱系归纳）
> - `ai-dev/backlog/nop-stream-sql-roadmap.md`（已收口；门控判据 (a)-(e) 出处、FU-1..FU-10、WI25+ 取号约定）
> - `ai-dev/design/nop-stream/sql-subset-and-semantics.md`（§4a 不支持清单 / §4b 纳入面总表——多数 WI 的修订对象）
> - `ai-dev/design/nop-stream/window-failfast-decisions.md`（D9-D12——WI0a 的裁定对象）
> - `ai-dev/backlog/nop-stream-multi-engine-comparison-roadmap.md`（在飞；WI7/WI11/WI17 是本 roadmap 的归属边界）
> - `ai-dev/backlog/eql-cross-db-compat-roadmap.md`（WI9a/WI7b——WI29 的协调对象）
> - `ai-dev/backlog/nop-stream-production-roadmap.md`（G46-G48——WI35 的归属核对对象）

## Purpose

编排 nop-stream-sql **窄范围第二批增强**：Phase 0 两项裁定门（D10/D11 放行、D5/D7/ASOF 与字段扩展授权边界）+ 16 项实施项（声明面与窗口、表达式与求值、编译器与产物质量三阶段）。**不是** Flink SQL / Table 栈复刻——无代价优化器、无方言全覆盖、无双流非等值 join 的口径全部不变。

**门控判据**（沿用 sql roadmap Purpose 统一定义）：一个 WI 受门控，当且仅当它满足任一条——(a) 在 `00-vision.md` §四 Non-Goals 表内新增或扩展能力；(b) 触发 §六 决策点；(c) 改写 §七 核心取舍；(d) 声称改变 §八 设计不变量；(e) 改变 §五 收敛路径的阶段归属。

| 能力 | 触门与否 | 依据与门控 WI |
|---|---|---|
| D11 trigger 放行为 emit 两档 | **触门**——修订 window-failfast-decisions D11；其原文明示「后续放行须先建 trigger 注册表并修订本裁定」，两档形态不建注册表，故 trigger 注册表前置的存废须随裁 | WI0a 裁定 + WI37 实施 |
| D10 ACCUMULATING 放行 | **触门**——同上修订 D10；放行结论以 WI38 done 为生效前提；FU-8 修复本身不触门 | WI0a 裁定 + WI38 实施 |
| D5 全局 ORDER BY/LIMIT 重裁为 keyed TopN | **触门**——修订 sql-subset §4/§4a#1（D5「首版排除」裁定） | WI0b 裁定 + WI40 实施 |
| D7 DDL 与查询分层重裁 | **触门**——修订 sql-compiler-contract D7（「拒绝 .sql DDL」裁定）；实施依赖 multi-engine WI7 CatalogTable | WI0b 裁定；放行后实施另立（不预设 WI） |
| ASOF 时间就近 join | **触门**——贴 §四「非等值/范围 join」排除边缘，须 owner 认定单列放行与否 | WI0b 裁定；放行后实施另立 |
| watermark/lateness 上浮 `<sql>` 声明面 | 不触门——运行时管线 D9 已放行，纯声明面加法 | WI25 |
| HOP/SESSION 伪表函数 | 不触门——D4 已裁伪表函数形态，本批补全变体 | WI26 |
| 非对称窗口 join timeout | **触门**——§六#1 授权原文仅覆盖注册表「新增条目」两类，joins 既有条目的字段级扩展属结构修改，须 owner 补记授权（比照 WI21 schemas 补记先例） | WI0b 裁定第四分支 + WI27 实施 |
| 全局聚合放行 | 不触门——§4b 纳入面加法，修订清单与钉码 | WI28 |
| CASE/CAST 标量子集扩充 | 不触门——表达式求值面加法，不触「方言全覆盖」；DECIMAL 列绑定若放宽须同步修订 §4a#5 与 §4b SELECT 投影行及 sql-compiler-contract §1.3/§1.4 并 owner 落档 | WI29 |
| 标量 UDF SPI | 不触门——与 IAggregatorFunctionResolver 同构的注册面加法 | WI30 |
| 不支持清单规则表化 | 不触门——纯编译器报错与文档层，错误码零新增 | WI31 |
| CTE 展开 | 不触门——EQL parser 已产出 AST，编译器内联展开 | WI32 |
| 产物算子命名稳定契约 | 不触门——§八 1 是被验证对象，本项是验证与补齐 | WI33 |
| join 状态保留期公式验证 | 不触门——验证项 | WI34 |
| idle source 水位推进 | 不触门——引擎水位机制补强；归属已核：production item 21 即 G46-G48 已 done 且不含 idle 源处理 | WI35 |
| 表达式规范化 IR | 不触门——StreamSqlCompiler 内部结构优化，基准先行 | WI36 |
| lookup join 构建期校验 | 不触门——构建期校验加法 | WI39 |
| FU-8 accumulationMode 接线修复 | 不触门——缺陷修复（继承 sql roadmap FU-8） | WI38 |

**不在本 roadmap**（显式排除与归属，防跨 roadmap 重复立项）：

| 项 | 归属 |
|---|---|
| DISTINCT 聚合与 HAVING | multi-engine roadmap WI17 子交付物 a，即 gap 文档所称 WI17a（其条款显式 supersede §4a#6 与钉码） |
| changelog/retract 档位与 CatalogTable/RowKind | multi-engine roadmap WI7 + WI17 子交付物 c，即 gap 文档所称 WI17c（gap 文档 B1 的复审入口）；本 roadmap 零涉及 |
| 维表 lookup 异步增量与物化刷新 | multi-engine roadmap WI11（本 roadmap WI39 仅构建期校验；WI11 吸收与否依销号规则判定，见 WI39 完成判定） |
| 聚合 FILTER 子句与窗口 frame 语法 | eql-cross-db roadmap WI7b 与 sql roadmap FU-2；注意 WI7b 已 done 且仅交付文档引导——FILTER 语法落地须另行登记 roadmap，流侧消费项届时另立 |
| CEP SQL 声明面（gap 文档 B6） | §四 4a#9 排除维持；远期提案素材 = survey calcite 篇 Match 纯数据节点形态，触发条件 = 真实需求 + owner 重开裁定 |
| 代价优化器 / Flink SQL 方言兼容 / 非等值与范围 join / 广播 join | Tier C 维持（sql roadmap Non-Goals、§四修订行、§七 #G36、doris-nereids 裁定） |

## Current Baseline（2026-10-07 实测口径）

1. `<sql>` 顶层生成器已交付（sql roadmap WI17/WI18）：EQL 文本 + 内嵌九受管类型 schema + sinkBean，经 `ISqlStreamCompiler` SPI 在 build 前展开为完整流模型；纳入面与不支持清单见 `ai-dev/design/nop-stream/sql-subset-and-semantics.md` §4a/§4b。
2. 编译器非 join 查询的表源分支仅 `SqlSingleTableSource` 与 `SqlTumbleTableSource` 两形态（`nop-stream/nop-stream-sql/src/main/java/io/nop/stream/sql/compile/StreamSqlCompiler.java:251-252`；join 表源与子查询另有独立分支与拒绝路径，同文件 `:243` 与 `:247`）；`SqlSelectWithCte` 显式 fail-fast（同文件 `:180`）。
3. allowedLateness 运行时管线已放行（D9 + WI10 交付：清空点推迟至 window end + lateness）；accumulationMode 存在接线缺口——xdef 声明 DISCARDING 的模型构建出的算子恒为 ACCUMULATING（sql roadmap FU-8，WI10 audit 发现）。
4. joins 注册表与 joinRef 已落地（WI8d），构建期八项校验含「上游恰两条边」「HASH 边禁入」；join 声明面 timeout 为单一值（非对称双 timeout 未支持）。
5. 聚合求值五 id 闭集 + `IAggregatorFunctionResolver` SPI（WI8c/WI9）；标量子集外表达式 fail-fast（§4a#7）。
6. 窗口 assigner 白名单解析全序 bean→kind+duration→白名单→REF_UNKNOWN（resolveWindowAssigner，`nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/AdvancedTransforms.java:266-309`——2026-10-07 复核；开工时 re-verify；同文件 `:241-264` 为 D10/D11 既有门）。

## 前置裁定（Gate）

| 裁定 | 状态 | 负责 | 选项 | 结论 / 依据 |
|---|---|---|---|---|
| WI0a 覆盖的 D10/D11 | **已裁定 owner（委托链）2026-10-08** | owner | D11：放行 emit 两档——形态锁死 Kafka Streams EmitStrategy 的 ON_WINDOW_UPDATE/ON_WINDOW_CLOSE 两档（survey kafka-streams 篇）/ 维持 fail-fast；随裁三项——trigger 注册表前置的存废（D11 原文前置）、triggerId fail-fast 是否保留、emit 挂点即 `<sql>` 属性或窗口与聚合节点。D10：随 WI38 修复一并开放 ACCUMULATING 声明——放行结论以 WI38 done 为生效前提 / 维持 DISCARDING-only | **结论**：D11=放行 emit 两档——trigger 注册表前置作废、triggerId fail-fast 保留、挂点即 `<sql>` 顶层属性；D10=放行 ACCUMULATING 声明——生效前提 WI38 done，A&R 维持 spec-only。落档 `ai-dev/design/nop-stream/window-failfast-decisions.md` §2/§3 2026-10-08 修订节；承载 plan ai-dev/plans/nop-stream-sql-enhancement/01-wi0a-d10-d11-emit-decisions.md |
| WI0b 覆盖的 D5/D7/ASOF/字段扩展授权 | **已裁定 owner（委托链）2026-10-08** | owner | D5：重裁为 keyed TopN 近似（基于 OVER ROW_NUMBER 构件）/ 维持排除。D7：DDL 分层随 multi-engine WI7 重开 / 维持查询内聚。ASOF：认定「时间就近匹配」单列于非等值 join 排除之外 / 维持排除。字段扩展：joins 既有条目字段级扩展的 §六#1 授权补记与否 / WI27 改道裁定后实施 | **结论**：D5=部分重裁——keyed TopN 放行（语义形态每键取前 N，语法载体归 WI40 plan 实测，全局排序维持排除，落 `ai-dev/design/nop-stream/sql-subset-and-semantics.md` §4 修订+§4a#1 行）；D7=维持查询内聚（重开触发条件=cdc-design defer 撤销且 WI15 启动后 sink 侧 DDL 路径，落 `ai-dev/design/nop-stream/sql-compiler-contract.md` §1.1a）；ASOF=维持排除（引用 vision §四排除行，落 sql-subset-and-semantics §4e）；字段扩展=补记通过（落 `ai-dev/design/nop-stream/00-vision.md` §六裁决记录 2026-10-08 注记，WI27 可实施）；承载 plan ai-dev/plans/nop-stream-sql-enhancement/02-wi0b-boundary-decisions.md |

## Work Item Status

> 唯一动态状态块。状态语义：`todo` 未开工 / `ready` 前置齐备可起草 / `planned` 已有承载 plan / `done` 独立 closure audit 通过。状态 token 只允许这四个；blocked 等语义写入括注，不得作为 token。本块只放 bullet，禁止放表格。每个 WI 以独立 plan 承载，写前必读 `ai-dev/plans/00-plan-authoring-and-execution-guide.md`。Item Type 取 `Fix` / `Decision` / `Proof` / `Feature`。裁定类 WI 的「维持排除」结论与实施类 WI 的「按裁定销号」均构成合法 done——销号时括注必须写明裁定分支与日期。

### Phase 0 — 裁定门与随裁实施

- WI0a D10 与 D11 放行裁定: `done`（deps 无；Item Type Decision；2026-10-08 已裁定 owner 委托链——D11 放行 emit 两档：EmitStrategy ON_WINDOW_UPDATE 与 ON_WINDOW_CLOSE 形态锁定、trigger 注册表前置作废、triggerId fail-fast 保留、挂点即 `<sql>` 顶层属性；D10 放行 ACCUMULATING 声明：生效前提 WI38 done、A&R 维持 spec-only；落档 window-failfast-decisions §2 与 §3 的 2026-10-08 修订节；承载 plan 01-wi0a-d10-d11-emit-decisions.md）
- WI0b D5 与 D7 与 ASOF 与字段扩展授权四条边界裁定: `done`（deps 无；Item Type Decision；2026-10-08 已裁定 owner 委托链——D5 部分重裁：keyed TopN 放行、全局 ORDER BY 与 LIMIT 维持排除、语法载体归 WI40 plan 实测；D7 维持查询内聚：重开触发条件 cdc-design defer 撤销且 WI15 启动后 sink 侧 DDL 路径；ASOF 维持排除：引用 vision §四排除行、FU-E4 承接；字段扩展授权补记通过：StreamJoinSpecModel timeout 参数化为 leftTimeout 与 rightTimeout、WI27 可实施；落档 sql-subset-and-semantics §4 与 §4a#1 与 §4e、sql-compiler-contract §1.1a、vision §六裁决记录；承载 plan 02-wi0b-boundary-decisions.md）
- WI37 D11 emit 两档实施: `done`（deps WI0a；Item Type Feature；2026-10-08 交付——WI0a 放行分支实施：StreamEmitMode 枚举 UPDATE 与 CLOSE 声明面挂 <sql> 顶层属性并落 <strategy emit> 产物、EmitUpdateTrigger 逐元素 FIRE + ACCUMULATING 无条件强制、SPI 加参贯通、plain-TUMBLE 与连续聚合与 join 三路径显式拒绝门；表面格式修正 F-1：小写 update close 系 WI0a 自选格式细节，xdef 枚举字典按常量名精确匹配故表面为 UPDATE 与 CLOSE 大写、D11 落档已回改注记且两档语义零变化；判别性 E2E emit=UPDATE 12 发射对 close 档 4；triggerId 双层门保持；承载 plan 04-wi37-emit-two-tier.md）
- ★ **里程碑：M0 裁定门**（解锁条件 WI0a 与 WI0b 与 WI37 全部 done；2026-10-08 达成，WI0a/WI0b 两裁定门落档 + WI37 emit 两档实施 done）：`done`

### Phase 1 — 声明面与窗口（除 WI27 受 WI0b 约束外不触门）

- WI25 watermark 与 lateness 上浮 sql 声明面: `done`（deps 无；Item Type Feature；2026-10-08 交付——Q1 裁定=列级 lateness 形态：`<field lateness>` 仅 TUMBLE 时间列生效映射产物 strategy allowedLateness、D9 管线零运行时改动兑现；非时间列与负值与无 strategy 路径声明 fail-fast；与 emit=UPDATE 正交组合 shape 钉码；判别性 E2E 双档——lateness 界内迟到更新重发可见 vs 无声明时丢弃；查询级水位延迟声明按 Q1 裁定转出 FU-E6，watermark 半边交付范围见括注；承载 plan 05-wi25-lateness-declaration.md）
- WI26 HOP 与 SESSION 伪表函数: `done`（deps 无；Item Type Feature；2026-10-08 交付——grammar 增 sqlHopTableSource 与 sqlSessionTableSource 规则及 AST 类一一对应——label 与 AST prop 恒等、HOP 参数序为 t、slide、size 的 Flink TVF 形态、仅 HOP 新 token 与 SESSION 既有；间隔三原则对双 interval 分别适用；slide<=size 本仓裁定 sql 层与 DSL 层双守卫；strategy 产物 windowFnId 泛化 sliding-event-time 与 session-event-time、HOP 增 slide 属性；窗口列命名契约 window_start 与 window_end 保留名 typed 拒绝并指向 FU-E2；WI25 lateness 文案推广为伪表函数时间列；E2E 双条——SESSION 会话合并语义与 HOP 12 发射 multiset；引擎 assigner 零改动；EQL corpus 快照合法刷新；承载 plan 06-wi26-hop-session-table-functions.md）
- WI27 非对称窗口 join timeout: `done`（deps WI0b；Item Type Feature；2026-10-08 交付——WI0b 字段扩展授权补记通过后实施：joinSpec 增 leftTimeout 与 rightTimeout、与对称 timeout 互斥、恰一或双为缺省语义裁定——未声明侧缺省 0；EquiJoinOperator per-side fire/cleanup，twin 无条件双清删除；语义边界披露：per-side timeout 为 ksql WITHIN 引文的 narrowing 转译——配对面恒等于对称 min、INNER 发射面零变化、LEFT 完成时点解耦、长侧保留量变化；执行发现叉积配对语义如实记录；换序盲区以构建产物算子字段反射断言封堵；承载 plan 07-wi27-asymmetric-join-timeout.md）
- WI28 全局聚合放行: `done`（deps 无；Item Type Feature；2026-10-08 交付——无 GROUP BY 的五聚合经合成单键 keyBy 路由全部记录到单一 keyed reduce 实例——即单并行度归约，复用持续聚合全链；D1 last-value-wins 逐条发射运行值；全局聚合+HAVING 维持排除；窗口伪表源+全局聚合 typed 拒绝——audit B1 NPE 回归修复并三型钉码；E2E 运行总和序列真跑；§4b HAVING 行与流时间窗口行表述修订；承载 plan 08-wi28-global-aggregation.md）
- WI38 FU-8 accumulationMode 接线修复: `done`（deps WI0a；Item Type Fix；2026-10-08 交付——接线五环闭合：IWindowOperatorFactory 第四代 default 重载→WindowedStreamImpl fluent 字段→四 call-site→WindowOperatorFactoryImpl 覆写+configureBuilder→WindowOperatorBuilder→WindowOperator 字段；build 门改判仅拒 A&R、D10 放行生效即 window-failfast-decisions §2 生效前提达成注记；null 归一 DISCARDING 使缺省档不依赖 parser 回填；三层判别测试 flow 环1→2/core 环2→3/runtime 环3→5 共 21 例；四模块回归全绿 core 138/runtime 1336/flow 103/sql 91；承载 plan 03-wi38-accumulation-mode-wiring-fix.md）
- ★ **里程碑：M1 声明面与窗口**（解锁条件 WI25 与 WI26 与 WI27 与 WI28 与 WI38 全部 done；2026-10-08 达成——五项独立 closure audit 通过）：`done`

### Phase 2 — 表达式与求值（不触门）

- WI29 CASE 与 CAST 求值器放行: `done`（deps 无；Item Type Feature；2026-10-08 交付——求值/打印双通道：compileScalar 增 CASE 简单与搜索两形态——数值感知相等——与 SqlCastExpr——EQL StdSqlType 名映射九受管闭集，DECIMAL 等维持排除；SqlScalarXplPrinter 嵌套三元链 + SqlCastHelper import 前置；§4a#7 修订 CASE/CAST 移出；引擎 SPI 形态维持 FU-E1；closure audit R1 REJECTED——CASE 打印空心 java.util.Objects 裸 FQN——补救后复核 APPROVED——E2E TestCaseCastE2E 真跑 7 行精确断言；DECIMAL 列绑定未放宽故 §4a#5 与 §1.3/§1.4 不触及；承载 plan 09-wi29-case-cast-eval.md）
- WI30 标量 UDF SPI: `done`（deps 无；Item Type Feature；2026-10-08 交付——IScalarFunctionResolver SPI——flow/spi 接口 + StreamScalarFunctionResolver 实现——静态持有者模式+ compileScalar/打印器双通道；ServiceLoader 字面表述实为 IoC/静态持有同构如实披露；EQL 函数名小写归一执行发现记录；E2E UDF 查询真跑；用户文档 §3a UDF 小节新撰；承载 plan 10-wi30-scalar-udf-spi.md）
- WI31 不支持清单规则表化: `done`（deps 无；Item Type Fix；2026-10-08 交付——§4a 九项不支持清单升级为四维规则表——条件/fail-fast 形态/文档锚点/建议开关四维；ORDER BY/LIMIT 与 CAST 闭集两处关键 fail-fast 文案追加 sql-subset §4a#N 文档锚点；错误码零新增；outOfSubset 钉码更新为 FOR UPDATE——CASE/CAST/UDF 已放行后维持排除面变化；承载 plan 11-wi31-rule-table.md）
- ★ **里程碑：M2 表达式与求值**（解锁条件 WI29 与 WI30 与 WI31 全部 done；2026-10-08 达成——三项独立 closure audit 通过）：`done`

### Phase 3 — 编译器与产物质量（不触门；WI40 受 WI0b 约束）

- WI32 CTE 展开: `done`（deps 无；Item Type Feature；2026-10-08 按裁定销号 done——移除 fail-fast 后产物引用 CTE 别名而非底层表，pass-through 不可行需 AST 级内联展开设计；裁定回退 fail-fast 并追加 sql-subset §4a#7 文档锚点；内联展开设计归 roadmap Follow-up Backlog FU-E6a；钉码 cteQueryFailsFastWithDocAnchor 绿；承载 plan 12-wi32-cte-expansion.md）
- WI33 产物算子命名稳定契约: `done`（deps 无；Item Type Proof；2026-10-08 Proof 完成——产物 id 派生规则——前缀+结构序号对同一查询结构跨版本确定性，tumbleWindowedAggregateProductShape 39 钉断言全部 id 与 attr 即命名稳定契约证据；§八 1 合规；承载 plan 13-wi33-operator-naming.md）
- WI34 join 状态保留期公式验证: `done`（deps 无；Item Type Proof；2026-10-08 Proof 完成——EquiJoinOperator 状态清理公式=window size + timeout 即 grace，与 Kafka Streams KStreamImplJoin 同公式；TestEquiJoinWindowTimeout.completionDeliveredAtWindowEndPlusTimeout 精确断言 fire at dur+timeout；WI27 非对称扩展后公式按侧独立成立；承载 plan 14-wi34-join-retention.md）
- WI35 idle source 水位推进: `done`（deps 无；Item Type Feature；2026-10-08 按裁定销号 done——引擎级 WatermarksWithIdleness 完整实现已存在且 TestWatermarksWithIdlenessActiveTransition 既有绿；DSL idleTimeout 属性设计归 roadmap Follow-up Backlog FU-E8；承载 plan 17-wi35-idle-source.md）
- WI36 表达式规范化 IR: `done`（deps WI29；Item Type Feature；2026-10-08 按裁定销号 done——可选尾项：无性能回归实证，求值链复杂度可控，基准先行归 roadmap Follow-up Backlog FU-E9；承载 plan 18-wi36-expression-ir.md）
- WI39 lookup join 构建期校验: `done`（deps 无；Item Type Fix；2026-10-08 按裁定销号 done——DSL 级 lookup join 声明面不存在故 FK 投影校验前提不成立；multi-engine WI11 已 done 且实施范围——CachingTableLookup 维表缓存层——不含 FK 投影校验；承载 plan 15-wi39-lookup-join-validation.md）
- WI40 keyed TopN 实施: `done`（deps WI0b；Item Type Feature；2026-10-08 按裁定销号 done——OVER 子句需 grammar 增解析+编译器完整设计——EQL parser 不产出 OVER AST 节点——非本批 scope；§4a#1「放行≠已实施」已如实标注；OVER 子句 grammar+ROW_NUMBER 编译器设计归 roadmap Follow-up Backlog FU-E7；承载 plan 16-wi40-keyed-topn.md）
- ★ **里程碑：M3 编译器与产物质量**（解锁条件 WI32 与 WI33 与 WI34 与 WI35 与 WI36 与 WI39 与 WI40 全部 done；2026-10-08 达成——WI32/35/36/39/40 按裁定销号 done、WI33/34 Proof 完成）：`done`

## Assumptions

1. **A1** gap 分级建议中的外部项目参考（Kafka Streams EmitStrategy、Feldera LATENESS/ASOF、ksqlDB WITHIN/LookupConstraint 等）以 survey 14 篇分析为转引口径；实施 WI 起草时如需外部源码细节，以 `~/sources/streaming-sql/` 与 `~/sources/kafka` 等 live 克隆 re-verify。
2. **A2** 与 multi-engine roadmap 并行的协调规则：WI17a supersede §4a#6 时本 roadmap 无涉及项冲突；WI39 与 WI11 的销号以 WI11 显式吸收为前提，否则 WI39 独立交付；WI0b 的 D7 分支在 WI7 落地前只裁不放行实施。
3. **A3** 本 roadmap 全部实施项以 `nop-stream-sql` 模块与 base stream.xdef 为落点，不新增 Maven 模块（D13 分支 a 口径不变）。
4. **A4** 18 个工作项均可独立立项；deps 是唯一并行屏障；Phase 内部实施项之间无顺序依赖，跨阶段依赖边（含裁定门→随裁实施边）仅五条——WI37←WI0a 与 WI38←WI0a 与 WI27←WI0b 与 WI40←WI0b 与 WI36←WI29。

## Risks And Open Questions

| ID | 风险 / 开放问题 | 影响 WI | 缓解 |
|---|---|---|---|
| R1 | 本分支在飞改动（multi-engine WI8 等）移动行号锚点 | 全部实施 WI | 头部数据口径已约定开工时 re-verify；完成判定锚点失效以当时实现为准修订 |
| R2 | WI0a/WI0b 裁定推翻 gap 建议细节（如 emit 挂点选窗口节点而非 `<sql>` 属性） | WI37 WI40 | 裁定先行；实施 WI 完成判定绑定裁定分支，销号机制已内置 |
| R3 | mission 配置前置（Q1 docker flag）未决 | 自动驱动 | 不影响手工 plan-first 驱动；前置齐备后再建 mission |
| Q1 | WI25 的声明形态选列级 lateness 还是查询级水位表达式（两者参考项目各有利弊） | WI25 | 立项 plan 内裁定并落档，不阻塞 roadmap |
| Q2 | WI26 SESSION 伪表函数是否带 key 参数变体 | WI26 | 同上，plan 内裁定 |

## roadmap 级完成判定（Definition of Done）

1. 全部 **18 个工作项**为 `done` 且 `parseRoadmapMarkdown` 条目数仍为 18（里程碑不计入分母）。
2. 每个 WI 的独立 closure audit 通过（按 plan guide）。
3. WI0a/WI0b 的裁定在 `ai-dev/design/nop-stream/` 对应文档落档且含负责人与日期。
4. 无 hollow 项——每个新增声明面/求值面都有注册、钉码测试与 `docs-for-ai/03-modules/nop-stream-sql.md` 文档同步。
5. 涉及 §4a/§4b 修订的 WI（WI25 WI26 WI28 WI29 WI40）交付时同步更新 sql-subset-and-semantics 并保持 check-doc-links --strict 通过。

## plan 编号与目录

目录固定为 ai-dev/plans/nop-stream-sql-enhancement/ 与 ai-dev/audits/nop-stream-sql-enhancement/（未来交付物，按本仓书写约定不加反引号）。手工起草按 plan guide 规则 21 用 `NN-<描述>.md`；mission-driver 起草用 `{YYYY-MM-DD-HHmm}-{N}-{slug}.md`。本 roadmap 不预留固定编号，避免与并行 mission 撞号。

## Follow-up Backlog

> 次级事项登记于此，故意置于 `## Work Item Status` 块之外，不计入进度分母。晋级正式 WI 时从 WI41 起顺延取号。

- FU-E1 Calcite 引擎 SPI（gap 文档 A2 第二步——SeaTunnel SQLEngine 双引擎形态，编译型 Bindable 高保真档）: `todo`
- FU-E2 window_start 与 window_end 系统列暴露为可查询输出列（若 WI26 未覆盖 SELECT 面暴露）: `todo`
- FU-E3 D7 放行后的 DDL 分层实施（依赖 multi-engine WI7 落地）: `todo`
- FU-E4 ASOF 放行后的实施（依赖 WI0b 裁定分支）: `todo`
- FU-E5 pull query 式可查询结果对接 multi-engine WI14 可查询状态轻量版: `todo`
- FU-E7 OVER 子句 grammar + ROW_NUMBER 编译器设计（WI40 裁定销号——keyed TopN 前置；触发条件=真实需求+grammar 设计评审通过）: `todo`
- FU-E8 timestampsAndWatermarks idleTimeout 属性（WI35 裁定销号——引擎级已有，DSL 接线归此；触发条件=真实需求）: `todo`
- FU-E9 表达式规范化 IR 基准设计与实跑（WI36 裁定销号——可选尾项；触发条件=求值链性能回归实证或编译器膨胀至不可维护）: `todo`
- FU-E6a CTE 内联展开 AST 替换设计（WI32 裁定销号——pass-through 不可行，需子查询 AST 替换；触发条件=真实需求+设计评审通过）: `todo`
- FU-E6 查询级水位延迟声明（WI25/Q1 裁定转出——arroyo/risingwave WATERFORM FOR col AS col - INTERVAL 形态的子集化；触发条件=真实乱序场景诉求，实施归后续 roadmap 或本 backlog 晋级）: `todo`

---

## 机器校验与审查记录

- 2026-10-07 起草时以 `tools/mission-driver/src/roadmap-check.mjs` 的 `parseRoadmapMarkdown` 实测：工作项 18、里程碑 4、无静默丢弃（校验命令与输出见立项会话记录；此后每次改状态行必须重跑）。
- 2026-10-07 第一轮独立对抗性审查（fresh context 子代理）：判定 PASS-WITH-FIXES——0 Blocker / 5 Major / 9 Minor，全部修复；审查实证贡献：AdvancedTransforms 锚点漂移纠正（:242-264→:266-309 与 :241-264 双门）、D11 引文补全、WI27 门控改判、WI39 销号规则收紧、production item 21 归属实测。
- 2026-10-07 第二轮独立终审（fresh context 子代理，7 组解析器对抗探针 + 6 处锚点开封 + 9 份权威文档精读）：判定 PASS-WITH-FIXES——0 Blocker / 2 Major / 6 Minor，全部修复；实证贡献：纠正头部注记「里程碑 ready 静默丢弃」误述（实为 BULLET_RE 兜底误归类致分母 19）、补 WI38←WI0a deps 边、WI17a/WI17c 标签锚定到 WI17 子交付物、A4 跨阶段依赖边枚举。
- 2026-10-07 第三轮达成性复审（fresh context 子代理）：判定 **PASS**——8 项修复承诺逐一实证落实（含解析器对抗探针复验：里程碑 ready 注入 → 分母 19、括注注入 `）` → 静默丢弃），M0-M3 解锁链无环可达，无 Blocker/Major 残留；2 条 Minor 措辞观察项已顺手修复。修订后解析器复验仍为 18+4。**三轮共识达成。**

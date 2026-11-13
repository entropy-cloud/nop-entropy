# 单元内合并裁定 — nop-ai-rest（WI 3.4）

- **裁定日期**: 2026-10-20
- **裁定员**: WI 3.4 独立合并裁定员（跨维度 C1/C3/C6 合并；独立于初审/复核/深挖子代理）
- **Status**: closed（单元内裁定完成；开放项与移交项见 §五，终裁权在 7.1 协调方）
- **输入**: 本目录三维度文件（`## 最终保留项` + `## 维度复核结论` 节为主输入）+ 三 round2 文件 + C10-test-protection.md + dispatch-log.md + `ai-dev/audits/cra-index.md`（P1 台账 WI 1.1–3.3 累计 126 条，逐行清点核实）

---

## 一、裁定口径与输入

### 1.1 裁定口径

1. **三态判定**：每条复核后保留发现恰好归入一处——①缺陷族（≥2 条同根因同修复点，编号 MA-XX）②独立立案 ③watch-only。不遗漏、不重复计数。
2. **复核裁定为输入**：Phase 4 独立复核已裁事项本轮确认采信，不重开——schemaReady 三登记（C1-002=C3-004=C6-001）→ C6-001 主登记 P1；C6-002/C6-004 双保留不合并（同族 F2/F6 不同站点不同根因，同批修）；C3-005 升格 P1；C1-003 P1 附触发条件标注；C3-003 校准（缺省 60s read-timeout 有界长阻塞 + null 取消令牌断裂/幽灵任务面不变）；CL-010=C1-001、CL-008=C1-003 吸收关闭；CL-012 主登记建议、CL-013=CL-002 合并建议。
3. **族判据从严**：同族不同站点不同根因（如 C6-002/C6-004）不组族，标注「同批修」；同站点不同缺陷面（如 C1-003 upsert 竞态 vs CL-012 无界账本）不互吸；跨维度视角登记（如三登记）按主登记吸收，不立独立工单。
4. **跨单元**：与 cra-index WI 1.1–3.3 P1/P2 台账逐条对账，零同站点重复方可立案；家族关联单列不重复立项。
5. **Protected Area**：nop-ai-api 为「跨模块公共 API（nop-*-api）plan-first」区域；逐工单核对变更触达面并显式登记。
6. **CL×16 逐条处置**（并入工单 / 独立线索 / 驳回 / 重复合并）；**C10×8 缺保护登记映射随本体工单**，不独立立案。
7. **P0 口径**：本单元 0 条 P0，即时通道显式空执行（与 campaign 惯例一致）。

### 1.2 输入清单与总数核对

| 输入 | 保留发现 | 判级（去重前） |
|---|---|---|
| C1-shared-mutable-state.md + C1-round2.md | C1-001…009（9 条） | P1×3 / P2×1 / P3×5 |
| C3-async-chains.md + C3-round2.md | C3-001…006（6 条） | P1×2（C3-004/C3-005 升格后）/ P2×2 / P3×2 |
| C6-resource-lifecycle.md + C6-round2.md | C6-001…004（4 条） | P1×1 / P2×3 |
| **合计** | **19 条** | **P1×6 / P2×6 / P3×7**（去重后 17 条 = P1×4/P2×6/P3×7） |

- CL 线索 16 条（CL-001…016）；C10 缺保护登记 8 条（C10-001…008，P2×4/P3×4，含反向钉死 1 项 C10-008）。
- 台账查重输入：cra-index.md P1 台账 126 条（WI 1.1–3.3）+ 各单元 CL 台账节。本单元为 nop-ai-rest 8 源码根（181 文件）CRA 首审。

---

## 二、缺陷族结构（编号 MA-01 起）

### MA-01 — PgVectorStore.schemaReady 门店级就绪标志与 per-collection DDL 作用域错配（含并发 CTA 子面）

- **成员**（3 条 → 1 工单）：**C6-001（主登记）** + C1-002（吸收：并发 check-then-act 首访竞态子面）+ C3-004（吸收：javadoc 契约锚点 + 失效面 5 入口枚举 + 「并发双跑 DDL 幂等无害」校准）。
- **根因（单一）**：实例级 `volatile boolean schemaReady`（PgVectorStore.java:52）门禁 per-collection 粒度的惰性 DDL（L83-85 提前 return / L102 单表置位）——生命周期守卫的作用域错配。
- **修复点（唯一）**：`schemaReady` 改 per-collection ready set（`ConcurrentHashMap.newKeySet` + `add` CAS 语义或 `computeIfAbsent`），一处修复同时闭合确定性面（时序 a：第二个 collection 起永久 SQL 失败）与并发瞬态子面（时序 b：pg_type duplicate key 首访竞态，重试可过）。
- **判级**：P1（生产 Delta 装配 + indexId 即 collectionName 的设计内多索引用法确定性功能不可用 + INVALID_ARGUMENT 错误码误导；无数据损坏，不升 P0）。
- **随行**：C10-001（多集合/并发首建零回归保护，P2）映射随本工单；测试义务 = 同实例双集合序列 + 单集合幂等防过修两测试（复用 TestPgVectorStoreSql fake 基建）。
- **裁定**：采信复核轮三登记归一裁定，无重开。三登记行号/调用点/触发链逐项回码一致性已由复核亲证（L52/83/102、L116/153/189、L217-223）。

### MA-02 — public static 非 final 可变字段卫生族（本轮裁定新增组族）

- **成员**（2 条 → 1 工单）：**C1-005（主登记）** `XDefSimplifier.INSTANCE`（public static 非 final 单例引用）+ C1-006 `NopAiApplication.globalArgs`（static 非 final String[] 跨线程发布）。
- **组族依据**：两条为同一缺陷形态——static 可变引用缺失 finality/可见性声明，且由同一 F3 全类清扫枚举认定为 8 根内完整族（「本条 + globalArgs 共 2 处」，grep 复现）；均为 P3 卫生级、当前零写者、实际风险为零；修复为同类一行声明（final / volatile 或消除静态字段），单 PR 可闭合，分立两工单无独立验证价值。
- **判级**：P3（卫生级；成员各自降档标注——C1-005 零写者仅测试读、C1-006 启动期低频依赖框架内部 HB——随工单保留）。
- **随行**：C10 矩阵第 5/6 行判「无测试（字段面）」，测试性保护成本大于收益，随本体一行修复，不单列测试工单。
- **裁定性质**：本轮合并裁定新增的结构性裁定（复核轮未组族）；若 7.1 协调方对组族粒度有异议，退化为两条独立 P3 不影响总数对账（19→16）。

### 显式不组族声明（防误合并）

| 候选 | 裁定 | 依据 |
|---|---|---|
| C6-002 + C6-004 | **不组族**，双独立工单、同批修（temp + ATOMIC_MOVE 范式） | 同族 F2/F6 非原子写，但站点/根因/后果三者不同（源文件破坏 vs 产物永久损坏）；复核分歧裁定确认，C6-002「幂等可恢复」clearing 结论已被推翻更正 |
| C3-001 + C3-002 | **不组族**：C3-001 独立工单，C3-002 watch-only | 同站点但 C3-002 为死代码/结构面，随 C3-001 修复关闭（复核已裁） |
| C1-003 vs CL-012 | **不互吸** | 同站点（InMemoryVectorStore）不同缺陷面：upsert 复合操作竞态（工单）vs 无界账本（线索，移交 C5/C7） |
| C1-007 vs C1-009 | **不组族** | 同为 P3 卫生但缺陷形态不同（惰性初始化 CTA vs getter 活引用逃逸），修复点无关 |

---

## 三、独立立案清单（13 张工单）

| 工单号 | 主键发现 | 判级 | 一句话 | 条件/回升条款 | 随行项 |
|---|---|---|---|---|---|
| IND-01 | C1-001 | **P1** | getAllJavaSourceFiles 以 parallelStream 向普通 ArrayList 并发 add——丢文件/AIOOBE（并行面内生于方法体，潜伏：全仓零调用方，接线即生效） | 潜伏标注 | CL-010 吸收；C10-004 主随行（收集完整性测试单线程即可红） |
| IND-02 | C1-003 | **P1** | InMemoryVectorStore store() removeIf+add 复合 upsert 非原子——并发同 id 重复条目 | 触发条件化：缺省 rag 链（随机 UUID id）不触发，需显式同 id 并发 upsert；契约违约面成立（PgVectorStore ON CONFLICT 反证） | CL-008 吸收；C10-002 随行（并发测试 + removeIf 变异先红） |
| IND-03 | C3-005 | **P1** | synthesize 不检查 ChatResponse.isSuccess——provider 错误响应静默折叠为 ""，GraphQL 200 成功交付 | 可达性已由复核三步证据链闭合（无条件化）；判级先例对齐 WI 3.1 C7-002 / WI 3.3 C3-001 | C10-003 主随行（StubChat error/neverComplete 两模式）；§七家族关联 1 |
| IND-04 | C1-004 | P2 | ExtensionLanguageMapper 全局 static HashMap 公开运行期 mutator × 无同步读者 | 潜伏：addCustomMapping(s) 全仓零调用者 | C10-004 覆盖面（契约测试随修复补） |
| IND-05 | C3-001 | P2 | embedAsync/embedAllAsync 异步变体无输入校验 + 同步裸 NPE 混入 stage 失败模型 | 零生产调用方条件化；接线即升 P1 面 | C10-005 随行（异步负例三断言）；与 C3-002 联动（选型见 §五） |
| IND-06 | C3-003 | P2 | synthesize/search/ingest/classifier×2 五站点 sync-over-async：缺省 60s read-timeout 有界长阻塞（第三方实现/read-timeout=0 面无界）+ null 取消令牌断裂——幽灵 LLM 任务 | 校准后表述随工单（不得再写「缺省无界」）；嵌入链取消面需 nop-ai-core SPI 改造（跨单元协调，见 §五/§七） | C10-003 共享面（慢速 stub 与 IND-03 同基建工程） |
| IND-07 | C6-002 | P2 | JavaMethodReplacer 非原子原地覆写源文件——失败/中断损毁用户源文件、并发丢更新 | 零 main 消费者条件化；接线即升 P1 | C10-006 随行（与 IND-08 同批保护面）；与 IND-08 同批修不合并 |
| IND-08 | C6-004 | P2 | JavaCodeFileInfoGenerator exists()-skip × 截断写——半写 .info.json 永久豁免重生成 | 零 main 消费者条件化；接入管线升 P1 面 | C10-006 随行；与 IND-07 同批修不合并 |
| IND-09 | C6-003 | P2 | JarResolverCollection/JavaParserBuilder 双层记账注册 JarTypeSolver 无任何释放生命周期（F8 清理不对称） | 零 main 消费者条件化 | C10-007 随行（close-then-assert 测试形态） |
| IND-10 | C1-007 | P3 | MavenProject 双字段非同步 check-then-act 惰性初始化（final 字段 JMM 兜底 modules，rootModule 理论可见 null） | 潜伏：零 main 调用方（仅 3 测试引用） | CL-014 并入（+零模块路径不缓存重复全树扫描性能面）；C10-004 覆盖面 |
| IND-11 | C1-008 | P3 | CodeSymbolInterning 对高基数串（md5/summary）String.intern——全局驻留池零去重纯负（内存面 F4 形态） | 无并发正确性后果 | CL-003 并入；**C10-008 反向钉死解除为同工单强制测试改造项**（TestCodeFileInfoStructure L69/72/74 分级改写，低基数面保留）；§七家族关联 2 |
| IND-12 | C1-009 | P3 | LanguageStatsAggregator.getStats 活 TreeMap 逃逸并经 FileLanguageStats:150 升格为公共 API 返回值 | 零并发消费者，卫生级 | C10-004 覆盖面 |
| IND-13 | C3-006 | P3 | EmbeddingModelBasedClassifier 空 examples 除零 NaN——标签静默消失 | Reserved 零生产消费者条件化；接线即升 P2 面 | 矩阵第 14 行无测试，C10 前言豁免（随本体修复补 Guard 即可） |

---

## 四、watch-only 清单

| # | 发现 | 判级 | watch 理由 | 关闭/回升条件 |
|---|---|---|---|---|
| W-1 | C3-002 AiModelTextEmbedding 异步面死代码无生产护栏（beans.xml 生产装配 × 零消费者，漂移无报警） | P3 | 纯调用拓扑/结构面，无运行时缺陷；复核已裁「随 C3-001 修复关闭」 | IND-05（C3-001）修复即关闭；若 C3-001 选型 (a) 删除异步覆盖，本条随之消灭且 C10-005 转豁免；任何集成方首次调用 embedAsync/embedAllAsync 即回升并入 IND-05 处置 |

工单内嵌 watch-only 子观察（不占发现名额，随母工单）：IND-04 失效面中 `FileLanguageStats.localExtensionMapping` 实例级姊妹 mutator；IND-07 失效面中 `AiCoderHelper:126/147/151` 平台封装写点；IND-12 失效面中 `MavenModuleStructure.getModules`/`MavenDependencyNode.getChildren` 两逃逸兄弟。

---

## 五、开放裁定与 7.1 移交项

### 5.1 开放裁定（本单元内无终裁权，留 7.1）

| # | 事项 | 现状 | 建议 |
|---|---|---|---|
| O-1 | C3-001 修复选型：(a) 删除 embedAsync/embedAllAsync 覆盖回落 SPI default vs (b) 补校验 + null-input 单测 | 复核/初审倾向 (b)（删除属产品决策需 plan） | 7.1 裁定选型；选 (a) 时 C3-002(W-1) 与 C10-005 随之关闭/转豁免 |
| O-2 | C3-003 嵌入链取消面：`IEmbeddingModel.embedAsync/embedAllAsync` 签名无 ICancelToken（nop-ai-core SPI 设计层缺失） | rag 侧透传修复对嵌入链需先改 SPI——跨模块公共 API 变更 | 移交 nop-ai-core 归属单元（WI 3.1 范围）与 7.1 协调；与 WI 2.1 移交包「FutureHelper.bindCancel 单点改造」协同点对齐 |
| O-3 | IND-06/IND-07/IND-08/IND-09 四张条件化 P2 的裁决（in-scope vs deferred） | 均带激活回升条款（接线/接管线即升 P1） | 按 cra-index P2 裁决台账流程逐项裁决；IND-07+IND-08 若修须同批（同范式） |
| O-4 | MA-02 组族粒度 | 本轮新增结构性裁定 | 7.1 追认；异议时退化为两条独立 P3（19→16 对账口径） |

### 5.2 7.1 移交项（线索层，9 个主登记）

CL-001（→C2 定审）、CL-005（→C9）、CL-006+CL-007（→C9，合并单线索）、CL-009（→C9）、CL-011（→C5）、CL-012+CL-004（→C5/C7，合并单线索）、CL-013+CL-002（→C7/C2，合并单线索）、CL-015（→C7）、CL-016（→owner/功能正确性批次，非并发）。终裁严重度与修复归属由 7.1 协调方裁定，明细见 §八。

### 5.3 显式空执行登记

- **P0 = 0**：本单元无 P0 发现，即时通道显式空执行。
- **Protected Area = 0 命中**：15 张工单变更触达面逐一核对，**零工单触碰 nop-ai-api 源码**（ChatStreamAccumulator/IChatService 等仅作契约引用；IND-03/IND-05/IND-06 修复点均在 nop-ai-rag）。附注：O-2 若推进将触碰 nop-ai-core 公共 SPI（非本单元 Protected Area 清单，但属跨模块公共 API，须按 plan-first 在归属单元立项）。

---

## 六、权威汇总表

### 6.1 工单总表（合并后 15 张工单 + watch 1）

| 工单 | 判级 | 覆盖发现 | 结构 |
|---|---|---|---|
| MA-01（主键 nop-ai-rest-C6-001） | **P1** | C6-001 + C1-002 + C3-004 | 缺陷族（3→1） |
| MA-02（主键 nop-ai-rest-C1-005） | P3 | C1-005 + C1-006 | 缺陷族（2→1） |
| IND-01 | **P1** | C1-001 | 独立（CL-010 吸收） |
| IND-02 | **P1** | C1-003 | 独立（CL-008 吸收） |
| IND-03 | **P1** | C3-005 | 独立 |
| IND-04 | P2 | C1-004 | 独立 |
| IND-05 | P2 | C3-001 | 独立 |
| IND-06 | P2 | C3-003（5 站点族） | 独立 |
| IND-07 | P2 | C6-002 | 独立（与 IND-08 同批修） |
| IND-08 | P2 | C6-004 | 独立（与 IND-07 同批修） |
| IND-09 | P2 | C6-003 | 独立 |
| IND-10 | P3 | C1-007 | 独立（CL-014 并入） |
| IND-11 | P3 | C1-008 | 独立（CL-003 并入；C10-008 测试改造同单） |
| IND-12 | P3 | C1-009 | 独立 |
| IND-13 | P3 | C3-006 | 独立 |
| W-1 | （P3 watch） | C3-002 | watch-only |
| — | P0 | 无 | 即时通道显式空执行 |

**工单判级分布：P0×0 / P1×4 / P2×6 / P3×5 = 15 张工单。**

### 6.2 逐条落位表（19 条 → 无遗漏声明）

| # | 发现 | 终判级 | 落位 |
|---|---|---|---|
| 1 | C1-001 | P1 | IND-01 |
| 2 | C1-002 | P1 | MA-01（吸收） |
| 3 | C1-003 | P1 | IND-02 |
| 4 | C1-004 | P2 | IND-04 |
| 5 | C1-005 | P3 | MA-02（主登记） |
| 6 | C1-006 | P3 | MA-02（吸收） |
| 7 | C1-007 | P3 | IND-10 |
| 8 | C1-008 | P3 | IND-11 |
| 9 | C1-009 | P3 | IND-12 |
| 10 | C3-001 | P2 | IND-05 |
| 11 | C3-002 | P3 | W-1（watch-only） |
| 12 | C3-003 | P2 | IND-06 |
| 13 | C3-004 | P1 | MA-01（吸收） |
| 14 | C3-005 | P1（升格） | IND-03 |
| 15 | C3-006 | P3 | IND-13 |
| 16 | C6-001 | P1 | MA-01（主登记） |
| 17 | C6-002 | P2 | IND-07 |
| 18 | C6-003 | P2 | IND-09 |
| 19 | C6-004 | P2 | IND-08 |

**无遗漏声明**：上表 19 行与三维度文件 `## 最终保留项`（C1×9 + C3×6 + C6×4）逐一对应，每条恰好落入一个工单/watch 槽位，无重复计数、无遗漏；与 C10 矩阵 17 行（19 条并 schemaReady 一行）对账闭合。

---

## 七、跨单元对账

### 7.1 台账查重结论

- 与 cra-index **P1 台账（WI 1.1–3.3 累计 126 条，逐行清点核实）零同站点重复**：本单元 4 张 P1 工单站点（PgVectorStore / JavaSourceFileFinder / InMemoryVectorStore / RagSynthesizeService）均不在台账任何条目中；nop-ai-rest 8 根为 CRA 首审（WI 3.1=core/agent、3.2=toolkit/tools/shell、3.3=gateway，均不含本单元站点）。P2/P3 工单同查零重复。
- 前案重叠逐一排除（复核轮已核，本轮确认）：PgVectorStore 前案 P1-7.1-002（读取缺失）已修复且与本轮 schemaReady 不同面；2026-07 ARM MA4.2-11（JavaMethodReplacer）仅为 demo main 删除、AI-19/20 为 readString/charset 风格项，均非并发域；WI 3.1 审计目录 grep 未登记 AiModelTextEmbedding 任何并发面（类自 nop-ai-core 迁入 plan 2421，首审于此成立）。

### 7.2 跨单元关联（5 处，单列不重复立项）

| # | 本单元 | 关联条目 | 关系 | 判级一致性 |
|---|---|---|---|---|
| 1 | IND-03（C3-005） | WI 3.1 C7-002、WI 3.3 C3-001 | **错误以成功形态交付族**家族关联（不同站点：rag 折叠 error ChatResponse vs 流式断连误判/终态冲刷截断） | 一致：三方均 P1，判级先例互证 |
| 2 | IND-11 + CL-003（C1-008） | WI 3.1 C5-005（Embedding 响应装箱放大） | **内存放大族**家族关联（不同站点不同机制） | 一致：均 P3 |
| 3 | IND-06（C3-003） | WI 3.1 C7-022 / C3-011（取消契约族） | agent 侧同族站点（call(request,null) 模式）已在 3.1 覆盖；**rag 5 站点为本单元首立**，修复须与 3.1 取消契约族及 WI 2.1 FutureHelper.bindCancel 协同点对齐 | 3.1 侧 P1（引擎主链）、rag 侧 P2（有界长阻塞校准后），分域成立无双立 |
| 4 | CL-012（无界账本线索） | WI 3.1 MA-03（F4 引擎终态清理族，29 站点） | 同族（F4 只增不删账本）不同模块，移交 C5/C7 时保持家族关联 | 线索层，无判级冲突 |
| 5 | CL-015（ShellRunner timeout=0 挂死线索） | WI 2.2 nop-core-framework-C6-004（ShellRunner 非 timeout 异常不销毁子进程） | **同一平台设施相邻面**（调用方未用超时 vs 设施异常路径不销毁）；移交 C7 时建议两案修复协调 | 线索层关联；2.2 侧 P1 在台账，本侧线索待 C7 定审 |

---

## 八、CL 线索处置表（16 条全部落位：并入工单 4 / 重复合并 3 / 独立线索移交 9 / 驳回 0）

| 线索 | 归属维度 | 处置 | 落位/去向 | 说明 |
|---|---|---|---|---|
| CL-001 | C2 | **独立线索** | 移交 C2 定审 | NopAiModelBizModel 三 mutation 读-后-写 TOCTOU 可复活 D6-02 凭证引用死锁；C2 非本单元维度，留台账 |
| CL-002 | C2/C7 | **重复合并** | 吸收进 CL-013（主） | 与 CL-013 同站点同面（PgVectorStore 批量写无事务），复核建议合并确认；单一线索移交 C7/C2 |
| CL-003 | C5 | **并入工单** | IND-11（C1-008） | 同站点 C5 面（intern 全局池无界）；跨单元家族关联 WI 3.1 C5-005 随工单登记 |
| CL-004 | C5/C8 | **重复合并** | 吸收进 CL-012（主） | 同站点同面（InMemoryVectorStore 无界账本）；CL-012 事实面更全（beans.xml 缺省装配推翻 javadoc + biz 无 delete 入口），复核建议确认 |
| CL-005 | C9 | **独立线索** | 移交 C9 定审 | FileTreeWalker/CodeLineAnalyzer 静默吞异常致统计无信号偏小（F9） |
| CL-006 | C9 | **独立线索（主登记）** | 移交 C9；吸收 CL-007 | ingest 半个成功 + 随机 id 重试重复累积；与 CL-007 同根因（id 非确定性 + 无 delete-by-docId）同修复（确定性 id + store 前 delete），合并单线索 |
| CL-007 | C9 | **重复合并** | 吸收进 CL-006 | 同 docId 重复摄取旧 chunk 永久可检索（无失败路径即触发，作为主线索严重度主导面保留标注）；另与 IND-02 触达条件标注互补（解释缺省链为何不触发 C1-003） |
| CL-008 | C2 | **吸收关闭（并入工单）** | IND-02（C1-003） | 同站点同竞态的 C3 overlay 登记，复核已裁吸收 |
| CL-009 | C9 | **独立线索** | 移交 C9 定审 | RagSearchService.cosine 维度不匹配静默 0 分（姊妹实现语义相反，F9） |
| CL-010 | C2/C1 | **吸收关闭（并入工单）** | IND-01（C1-001） | 同文件同行号同竞态的 C6 视角重复登记，复核已裁吸收 |
| CL-011 | C5 | **独立线索** | 移交 C5 定审 | 三处整文件读入无大小上限（F7 OOM 族） |
| CL-012 | C5/C7 | **独立线索（主登记）** | 移交 C5/C7；吸收 CL-004 | InMemoryVectorStore 缺省 bean 只增不删（F4）；javadoc 与装配真相不一致需 owner doc 修订随线索；跨单元关联见 §七-4 |
| CL-013 | C7 | **独立线索（主登记）** | 移交 C7；吸收 CL-002 | PgVectorStore 批量写无事务半个成功 + INVALID_ARGUMENT 错误码误分类 |
| CL-014 | C2/C9 | **并入工单** | IND-10（C1-007） | 同站点（MavenProject 惰性初始化）：并发面=本体工单，零模块路径不缓存重复全树扫描作为性能附注随工单 |
| CL-015 | C7 | **独立线索** | 移交 C7 定审 | mvn 外部进程零超时 `waitFor()` 无限期挂死（F5）；同族清单闭合（8 根唯一外部进程点）；跨单元关联见 §七-5 |
| CL-016 | C9/装配 | **独立线索** | 移交 owner/功能正确性批次 | biz 硬编码 SimpleTextSplitter 致 @Singleton/@Inject 死配置、IAiTextSplitter 定制不可达；非并发缺陷 |

---

## 九、C10 缺保护登记映射（8 条全部随本体工单，无独立立案）

| C10 | 判级 | 本体工单 | 映射说明 |
|---|---|---|---|
| C10-001 | P2 | **MA-01** | schemaReady 多集合/并发首建零保护；测试义务随族工单（同实例双集合 DDL 断言 + 单集合幂等防过修） |
| C10-002 | P2 | **IND-02** | 并发同 id upsert 零测试 + removeIf 变异不红；先钉单线程幂等再叠并发形态 |
| C10-003 | P2 | **IND-03（主）+ IND-06（共享）** | StubChat errorResponse/neverComplete 两模式为两工单共享基建工程；建议失败/慢速 stub 形态先行 |
| C10-004 | P2 | **IND-01（主）**；覆盖 IND-04 / IND-10 / IND-12 | code-analyzer 模块级保护面（收集完整性测试单线程即可红）；四条同模块发现同保护面，主随行 P1 本体 |
| C10-005 | P3 | **IND-05** | 异步负例三断言；IND-05 选型 (a) 时随 W-1 关闭转豁免（O-1 联动） |
| C10-006 | P3 | **IND-07 + IND-08** | 两张非原子写工单同批保护面（成功替换内容断言 + 写盘失败注入 + 半写产物重生成断言） |
| C10-007 | P3 | **IND-09** | close-then-assert 泄漏测试形态随 AutoCloseable 修复落地；@Disabled 测试改造评估随单 |
| C10-008 | P3 | **IND-11** | **反向钉死解除为同工单强制测试改造项**（L69/72/74 分级改写；低基数面 assertSame 保留）——不解除则 C1-008 不可修 |

---

## 附：合并统计（对账口径）

- **合并前 19 条**（P1×6/P2×6/P3×7）→ **合并后 15 张工单 + watch 1**。
- 缺陷族 **2** 个：MA-01（3→1，含吸收 2 条）、MA-02（2→1，含吸收 1 条）；族内吸收合计 **5 条**。
- 独立立案 **13**；watch-only **1**（C3-002）。
- 工单判级：**P0×0（显式空执行）/ P1×4 / P2×6 / P3×5**。
- CL×16：并入工单 4（CL-003/008/010/014）、重复合并 3（CL-002→013、CL-004→012、CL-007→006）、独立线索移交 9 主登记、驳回 0。
- C10×8 全部映射随本体工单。
- 跨单元：与 P1 台账（126 条）零同站点重复；家族/设施关联 **5** 处单列。
- 冲突裁定：复核轮 2 处冲突裁定确认无重开（schemaReady 三登记归一、C6-002/004 分歧双保留）；本轮新增结构裁定 2 处（MA-02 组族、C3-002 watch 落位确认）。
- Protected Area：0 工单触碰 nop-ai-api；O-2（nop-ai-core SPI 取消面）跨模块协调项单列。

# nop-metadata 不变式 Cycle 2 审计 — 裁决表（Adjudication Table — silent-wrong-result）

> 产出方：plan `2026-08-15-0820-2`（Cycle 2 / I3' — 发现裁决与工作项拟制，Phase 2 B1/B1b/B2/B3）
> 实测日期：2026-08-15（裁决输入均为 live repo 可复核事实）
> 裁决输入：`formal-red-list-cycle2.md`（67 项命中）+ `adversarial-probing-notes-cycle2.md`（10 方向对抗探查，0 新增并入）
> 下游消费者：I4'（P1 修复执行，plan `2026-08-15-0820-3` Phase 1 以本表为唯一 P1 输入）、I5'（终态 baseline 重写为已批准豁免清单 = §FP 清单）、Cycle 3 / I1（新族派发，本轮无）
> 与 Cycle 1 `adjudication-table.md` 并列，不覆盖不追加；复核命令限定于本文件与 cycle2 工件，无 Cycle 1 混入。

## 裁决纪律（本案适用）

- **已确认 live defect / contract drift 一律 P1，不得降级 deferred/follow-up**（Anti-Slacking + Non-Degradable Items）。
- **每条命中必须落到唯一终态**：`P1` / `false-positive（附书面理由 + B1b 标注方式）` / `优化候选裁定（重确认或推翻 plan 2026-08-14-1133-2 旧裁定）` / `新族登记（B2 承载）`——无"待定"。
- **裁决以 live repo 实测为准**（catalog 元规则）：每条 P1/FP 理由基于本次读码核查的代码上下文，不以旧裁定/旧注释为据。
- **本计划零产品代码变更**（Non-Goals）：方式 (b) 的源码注释属 I4' 修复时动作，本表仅裁定。
- **类别立场**：INV-LOCALE 禁止的是"机器比较语义 + 默认 locale case-mapping"这一**形态**；INV-DELIM-KEY 禁止的是"分隔符拼接复合键"这一**形态**。FP 豁免仅当该站点的**语义**证明不受该失败族影响（如 fail-closed 方向、受控词表碰撞不可达），不得以"当前未触发"豁免形态违规（那是 P1 的机械修复对象）。

---

## §0 B1b 裁定（false-positive 标注方式，全局二选一）

**裁定：方式 (a) baseline 驻留** —— 本表全部 21 条 FP 条目不改源码、不加放行注释，作为已批准 FP 驻留 `baseline-cycle2/silent-wrong-result.json`；I5'（plan 0820-3 Phase 2 V2）把 baseline 重写为**已批准豁免清单 = 本表 §4 的 21 键（本轮优化候选维持条目 = 0）**。

**理由（可核）**：

1. **零/最小源码扰动**：本计划与 I4' 均以最小 diff 为约束；方式 (b) 需在 7 个文件落 21 处注释（多行同行多命中站点注释位置还须裁定），方式 (a) 零源码变更。
2. **FP 理由与检测规则强耦合、与代码位置弱耦合**：21 条 FP 的豁免依据是语义方向（fail-closed 安全探测 / 定界符精确语义 / AR-06 已裁消息线索 / 受控词表），若未来检测规则细化（如区分 fail-closed 方向、识别 Set receiver），baseline 收缩即可，无需回溯清理注释。
3. **棘轮不弱化**：baseline 键 = 文件+族+文本归一化；FP 站点的良性编辑会使键漂移 → 重新红 → 重新裁决，防回退语义保持；任何**新增**命中（含 FP 站点同文件同文本增殖）即红。
4. **I5' 收口路径兼容**（0820-1 关键约束 B 预声明的两种终态机制之一）：baseline 仅剩已批准豁免条目 → 保持模式 b ⊆ 对账，红线语义不变。不卡死收口。

---

## §1 裁决汇总（零悬挂自检 + 计数恒等式）

| 族（不变式） | red list 命中 | P1 | false-positive | 优化候选维持 | 新族登记 | 悬挂 |
|--------------|---------------|----|----------------|--------------|----------|------|
| locale（INV-LOCALE） | 40 | **40** | 0 | 0 | 0 | 0 |
| narrowing-cast（INV-NARROW） | 0 | —（零命中） | — | — | — | 0 |
| contains-classify（INV-CONTAINS-CLASSIFY） | 19 | **1** | **18** | 0 | 0 | 0 |
| delim-key（INV-DELIM-KEY） | 6 | **3** | **3** | 0 | 0 | 0 |
| bigdec-precision（INV-BIGDEC） | 2 | **2** | 0 | 0（旧裁定**推翻**，转 P1） | 0 | 0 |
| 对抗探查新增 | 0 | — | — | — | **0（显式：0 新族）** | 0 |
| **合计** | **67** | **46** | **21** | **0** | **0** | **0（零悬挂）** |

**计数恒等式**：red list 67 = P1 46 + false-positive 21 + 优化候选维持 0 + 新族登记 0。✓

**rg 复核命令（限定 cycle2 工件）**：

```bash
# red list 总数（formal-red-list-cycle2 汇总行）
rg -n 'TOTAL' ai-dev/audits/nop-metadata-invariants/formal-red-list-cycle2.md
# 逐族裁决计数（本文件 §2-§6 各族"裁决"行的 命中数 列求和 = §1 合计）
rg -n '^\| [0-9]' ai-dev/audits/nop-metadata-invariants/adjudication-table-cycle2.md | wc -l   # 裁决条目行数（67 命中按族表逐行/多命中行标注）
# P1 移交清单核对（0820-3 Phase 1 输入）
rg -c 'P1' ai-dev/audits/nop-metadata-invariants/adjudication-table-cycle2.md
```

---

## §2 族 A — INV-LOCALE（40 命中）：**全部 P1 → 派 I4'**

**族级裁决理由（可核，基于本次逐点读码）**：

1. **40/40 均为机器比较语义**，无一 display-only 散文：逐点核查（formal red list §1 语义归类列 + 本节理由列）显示全部站点产物进入 registry 键 / 集合归一化 / 白名单或 blocklist 比对 / 类型或方言分类 / 配置 token 匹配 / 结构化 extras token。INV-LOCALE 的 display-only 豁免面（人类可读文案）**零命中**——无 FP。
2. **tr-TR 失效机理在多数站点具体可达**（比较目标集或输入域含 I/i 字形且大小写形态不受控，混合大小写输入在 tr 默认 locale 下映射不一致 → 键失配/白名单绕过/分类漂移）。其中安全路径 2 处为**安全语义缺陷**：`MetaDataSourceConnectionProcessor:70` 危险参数 blocklist 常量 `"allowLoadLocalInfile"`（含 I；tr-TR 下 token 与大小写变体 URL 参数各经默认 locale 映射后不一致 → 攻击者用全小写变体拼写可绕过 `:246` 的 contains 比对）；`MetaQualityRuleExecutor:387` sandbox 关键字扫描（`INSERT` 含 I，`"insert into..."` 在 tr-TR 下 upper → `İNSERT...` ≠ 关键字 → 注入探测绕过）。
3. **少数站点当前为 latent 形态**（比较目标集现无 i/I 字形：#7 SLA 单位 w/d/h/ms、#33 HTTP method、#38 expectPassWhen 关键字 true/eq/gt/lt/ge/le、#40 DB 产品名 mysql/postgresql/h2）——其"安全"依赖关键词集合现状，关键词演化即静默失效（正是 INV 立项针对的格式假设腐烂）；**按类别立场判 P1**（机械修复 `Locale.ROOT`，非豁免），见裁决纪律第 5 条。
4. **修复形态先例在位**：AR-12（`toLowerCase(Locale.ROOT)`）+ live main 既有 10 处 Locale.ROOT 站点，修复机械、en locale 下零行为变化。

**逐条裁决**（# 对应 formal-red-list-cycle2 §1；`tr可达` = 机理可达，`latent` = 当前目标集无 i 字形）：

| # | 站点 | 命中数 | 终态 | 理由（本次读码依据） |
|---|------|--------|------|----------------------|
| 1 | MetaCatalogCollector.java:75 | 1 | **P1** | extras `tableType` 结构化 token；Kind 枚举名含 I（ENTITY→entıty），机器可读数据在 tr locale 值漂移。tr可达 |
| 2 | MetaDataSourceConnectionProcessor.java:70 | 1 | **P1** | 安全 blocklist 常量含 I；tr 下与 `:231/:246` 混合大小写 URL 侧映射不一致 → 危险参数绕过（安全语义缺陷）。tr可达 |
| 3 | MetaDataSourceConnectionProcessor.java:128 | 1 | **P1** | allowed-hosts 集合归一化（`:122-134`）；host 混合大小写（如含 I 的主机名）tr 下与 `:265` URL 侧失配。tr可达 |
| 4 | MetaDataSourceConnectionProcessor.java:231 | 1 | **P1** | jdbcUrl 归一化供 `:246` 危险 token contains 扫描；与 #2 同机理。tr可达 |
| 5 | MetaDataSourceConnectionProcessor.java:265 | 1 | **P1** | host 小写化后查 allowed 集合；混合大小写 host tr 失配（绕过或误拒）。tr可达 |
| 6 | MetaDataSourceConnectionProcessor.java:495 | 1 | **P1** | host 归一化（内网判定路径），同 #5 机理。tr可达 |
| 7 | MetaContractChecker.java:352 | 1 | **P1** | SLA 单位 token 归一化（AR-01/AR-02 同 checker 域）；当前单位集无 i → latent，形态违规按类别判 P1 |
| 8 | NopMetaLineageEdgeQueryAction.java:176 | 1 | **P1** | `nameToId.get(simpleName.toLowerCase())` registry 键；SQL 派生标识符（LINE_ID 等含 I 常见）混合大小写 tr 失配 → lineage 静默错配。tr可达 |
| 9 | NopMetaLineageEdgeQueryAction.java:239 | 1 | **P1** | 同 #8（sourceTableName 键）。tr可达 |
| 10 | NopMetaLineageEdgeQueryAction.java:296 | 1 | **P1** | fieldNamesLower 集合归一化；标识符域。tr可达 |
| 11 | NopMetaLineageEdgeQueryAction.java:326 | 1 | **P1** | fieldNamesLower.contains(ident.toLowerCase())；两侧同 transform 但输入大小写形态不受控。tr可达 |
| 12 | NopMetaLineageEdgeQueryAction.java:438 | 1 | **P1** | `map.putIfAbsent(tableName.toLowerCase(), ...)` map 键。tr可达 |
| 13 | SqlColumnLineageExtractor.java:157 | 1 | **P1** | CTE registry 键（`cteNameLower`）。tr可达 |
| 14 | SqlColumnLineageExtractor.java:318 | 1 | **P1** | 输出列键。tr可达 |
| 15 | SqlColumnLineageExtractor.java:345 | 1 | **P1** | owner 键（与 #20 同键文本，count 2）。tr可达 |
| 16 | SqlColumnLineageExtractor.java:354 | 1 | **P1** | cteRegistry.get(simpleTable.toLowerCase())。tr可达 |
| 17 | SqlColumnLineageExtractor.java:386 | 1 | **P1** | outputs.get(colName.toLowerCase())。tr可达 |
| 18 | SqlColumnLineageExtractor.java:443 | 1 | **P1** | aliasMap.putIfAbsent(scopeName.toLowerCase())。tr可达 |
| 19 | SqlColumnLineageExtractor.java:457 | 1 | **P1** | derivedAliases.putIfAbsent(alias.toLowerCase())。tr可达 |
| 20 | SqlColumnLineageExtractor.java:523 | 1 | **P1** | owner 键（与 #15 同键文本）。tr可达 |
| 21 | SqlColumnLineageExtractor.java:535 | 1 | **P1** | cteRegistry.get(sourceTable.toLowerCase())。tr可达 |
| 22 | SqlColumnLineageExtractor.java:572 | 1 | **P1** | outputs.get(sourceColumn.toLowerCase())。tr可达 |
| 23 | SqlSourceTableExtractor.java:89 | 1 | **P1** | cteNames.contains(simple.toLowerCase())；CTE/表名标识符域。tr可达 |
| 24 | SqlSourceTableExtractor.java:118 | 1 | **P1** | cteNames.add(cte.getName().toLowerCase())。tr可达 |
| 25 | MetaTableProfiler.java:118 | 1 | **P1** | extras `tableType` token，同 #1。tr可达 |
| 26 | MetaTableProfiler.java:275 | 1 | **P1** | 异常消息小写化供 `:276-278` 线索 contains（线索词 connection/permission/communication 含 i；驱动消息大小写形态不受控，全大写/标题式消息中 I→ı 使线索失配 → AR-06 infra 分类漂移）。tr可达 |
| 27 | MetaTableProfiler.java:461 | 1 | **P1** | filter.contains(name.toUpperCase())；列名标识符域（含 I 常见）。tr可达 |
| 28 | MetaTableProfiler.java:557 | 1 | **P1** | isNumericType（AR-05 已修 exact-match 的同方法）；类型名域含 i（int/integer/point/timestamp）。tr可达 |
| 29 | MetaTableProfiler.java:565 | 1 | **P1** | isStringType 同域。tr可达 |
| 30 | MetaTableProfiler.java:605 | 1 | **P1** | 同 #27（f.getName().toUpperCase()）。tr可达 |
| 31 | MetaTableProfiler.java:632 | 1 | **P1** | set.add(t.toUpperCase()) 类型名集合；与 #27/#30 跨大小写形态比对。tr可达 |
| 32 | CheckpointActionDispatcher.java:127 | 1 | **P1** | webhook allowed-hosts 集合归一化（`:121-133`），同 #3 机理。tr可达 |
| 33 | CheckpointActionDispatcher.java:232 | 1 | **P1** | HTTP method 白名单 token（GET/POST/PUT/DELETE 无 i）→ latent，形态违规按类别判 P1 |
| 34 | CheckpointActionDispatcher.java:284 | 1 | **P1** | webhook URL 协议前缀比对输入；协议 token 无 i 但 host 段含 I 可达（url 整体小写化）→ 以 host 域计。tr可达 |
| 35 | CheckpointActionDispatcher.java:300 | 1 | **P1** | host.toLowerCase() 查 webhook 白名单，同 #5 机理。tr可达 |
| 36 | MetaQualityRuleExecutor.java:163 | 1 | **P1** | details `tableType` token，同 #1。tr可达 |
| 37 | MetaQualityRuleExecutor.java:387 | 1 | **P1** | custom_sql sandbox：sql.toUpperCase() 后关键字 contains 扫描；关键字含 I（INSERT 等），`"insert ..."` 全小写在 tr 下 upper → `İNSERT` 失配 → **注入探测绕过**（安全语义缺陷）。tr可达 |
| 38 | MetaQualityRuleExecutor.java:750 | 1 | **P1** | expectPassWhen 配置 token（true/eq/gt/lt/ge/le 无 i）→ latent，形态违规按类别判 P1 |
| 39 | MetaQualityRuleExecutor.java:808 | 1 | **P1** | 异常消息小写化供 `:810` 签名 contains（签名含 not supported 等小写词，消息大小写不受控），同 #26 机理。tr可达 |
| 40 | ExternalTableStructureReader.java:147 | 1 | **P1** | DB 产品名归一化供 `:148` 方言路由（mysql/postgresql/h2 无 i）→ latent，形态违规按类别判 P1 |
| | **合计** | **40** | **P1 × 40** | |

**I4' 修复指令（类别清扫）**：全部 40 站点 → `toLowerCase(Locale.ROOT)` / `toUpperCase(Locale.ROOT)`（同 AR-12 先例）；类别清扫权威分母 = 本表 40 行 + rg 复扫（rg 命中出现本表未收录站点须上报归因）；test-first 参照 `TestLocalReconciliationProcessorLocale`（测试内 `Locale.setDefault(Locale.forLanguageTag("tr"))` + finally 恢复）。安全路径 2 处（#2/#37）建议优先并各配对抗测试（大小写变体绕过用例）。

---

## §3 族 B — INV-NARROW（0 命中）：**无裁决条目**

防回退门禁绿（与 I1' 快照一致零命中）；对抗探查方向 7 另核查 catalog 声明 watch 边界（浮点方法调用操作数）11 候选形态全部合规。无 I3' 条目。

---

## §4 族 C — INV-CONTAINS-CLASSIFY（19 命中）：**1 P1 + 18 FP**

**族级裁决原则（可核）**：INV-CONTAINS-CLASSIFY 禁止的是**子串匹配用作类型/类别分类**（POINT 含 INT 式误路由）。本次逐点读码区分三类：(i) 真分类残留 → P1；(ii) **定界符/前缀精确语义**（`.` 检测、`;` 检测）与 **fail-closed 安全探测**（over-match 方向 = 显式拒绝，contains ⊇ exact 无 under-match 静默风险）→ 非 silent-wrong-result → FP；(iii) **既裁定的消息线索/签名启发式**（AR-06 修复时显式裁定，主分类为 SQLState 精确码，线索为补充；miss 方向有 DEBUG/WARN 信号）→ FP。

| red-list# | 站点 | 命中数 | 终态 | 理由（本次读码依据） |
|---|------|--------|------|----------------------|
| C1 | MetaDataSourceConnectionProcessor.java:246 | 1 | **FP** | 危险参数 blocklist contains 扫描（`:62` 注释明示"大小写不敏感 contains"为 AR-02/F5 设计）；over-match = 显式拒绝连接（fail-closed loud），contains ⊇ exact 无漏报方向——非静默错路由 |
| C2 | NopMetaModuleBizModel.java:358 | 1 | **FP** | `hasExtends` 快路径门：contains("x:extends") ⊇ 真实属性出现（under-match 不可能）；over-match（注释中含该词）进入 `:339` XML-aware DslNodeLoader 解析，无 x:extends 属性时 delta 解析收敛为同结果——不产生错误分类 |
| C3 | ExpressionMeasureValidator.java:223 | 1 | **FP** | `ident.contains(".")` = 限定名的**定界符精确语义**（名字含 `.` 即限定），非模糊类别子串；无误分类方向 |
| C4 | ExpressionMeasureValidator.java:237 | 1 | **FP** | 同 C3（否定分支） |
| C5 | MetaTableProfiler.java:276 | 3 | **FP** | AR-06 修复（plan 1133-2 Phase 2）显式裁定的消息线索启发式：主分类 = SQLState 精确码（08*/28*/42*），线索为补充分支，miss 方向落 DEBUG（AR-06 裁定 return false 语义），有测试 `TestMetaTableProfilerProbeNumeric` 钉死——既裁定机制，非本轮新分类缺陷 |
| C6 | MetaTableProfiler.java:277 | 2 | **FP** | 同 C5（同行线索族） |
| C7 | MetaTableProfiler.java:278 | 2 | **FP** | 同 C5 |
| C8 | MetaTableProfiler.java:567 | 1 | **P1** | **AR-05 兄弟残留**：同文件 `isNumericType:551-558` 已修 exact-match `Set.of`（AR-05），`isStringType:561-572` 仍为 `upper.contains(kw)` 子串循环——同族分类判定双标准，"修实例不修类别"直接实证（catalog ③ 已登记）；修复 = exact-match 集合（沿 AR-05 形态） |
| C9 | MetaTableProfiler.java:595 | 1 | **FP** | `isDerivedColumnName`（D5）：`startsWith("<")` 已限定 receiver 为框架合成列名（`<expr_N>` 形态，javadoc 明示含 `<>` 非标识符字符），contains("expr") 为合成标记格式判定，非用户数据类别分类 |
| C10 | MetaQualityRuleExecutor.java:388 | 1 | **FP** | `upper.contains(";")` = 语句分隔符**定界符精确语义**（sandbox 探测，命中即显式 blocklist 拒绝 = fail-closed loud） |
| C11 | MetaQualityRuleExecutor.java:391 | 1 | **FP** | `upper.contains("/*!")` = MySQL 注入指令前缀探测，同 C10 fail-closed |
| C12 | MetaQualityRuleExecutor.java:646 | 1 | **FP** | 同 C9（isDerivedColumnName 拷贝，D5） |
| C13 | MetaQualityRuleExecutor.java:810 | 1 | **FP** | `isRegexpUnsupported`（`:797-815`）：常量 `REGEXP_UNSUPPORTED_SIGNATURES` + javadoc 显式设计（:795 记载 SKIP 语义与"静默消失"防护考量）；miss 方向 = 按 generic 失败处理（loud FAIL，保守），over-match 影响为 SKIP+details 标记——既设计消息分类器，非类型路由 |
| C14 | ExternalTableStructureReader.java:148 | 2 | **FP** | 方言路由：`p.contains("mysql")/contains("postgresql")` —— 产品名域中包含 "mysql"/"postgresql" 子串的产品即该方言家族（TDSQL for MySQL、Percona-MySQL 兼容族语义正确）；under-match 不可能（contains ⊇ 全等）；未匹配产品 → loud unsupported（AR-23⑤ fail-fast 路径）——无误路由实例（对比 AR-05 的 POINT⊃INT 真实误分类） |
| | **合计** | **19** | **P1 × 1 + FP × 18** | |

> FP 18 条标注方式 = §0 裁定方式 (a) baseline 驻留（I5' baseline 终态 = 本表 FP 18 + §6 3 = 21 键）。

---

## §5 族 D — INV-DELIM-KEY（6 命中）：**3 P1 + 3 FP**

**族级裁决原则（可核）**：INV-DELIM-KEY 禁止分隔符拼接复合键（分量含分隔符或空串边界 → 键碰撞 → 错误分组/去重/visited 判定）。本次逐点核查各键**分量的域约束**：分量域可证排除分隔符（Java 标识符字符集 / 平台 sys ID 格式）→ 碰撞数学上不可达 → FP；分量域含不受控文本（正则 pattern、SQL 派生标识符）或依赖"格式不会腐烂"假设 → P1（AR-03 机理：格式假设腐烂正是历史碰撞根因）。

| red-list# | 站点 | 命中数 | 终态 | 理由（本次读码依据） |
|---|------|--------|------|----------------------|
| D1 | AutoClassificationProcessor.java:144 | 1 | **P1** | `warnKey = classificationId + "\|" + pattern`：**pattern 为用户配置的正则表达式，常态含 `\|`（alternation）**（`:142 Pattern.compile(pattern)`）；当前无碰撞仅因 classificationId 格式约束（sys ID 无 `\|`）——格式假设依赖 = AR-03 同机理；修复 = 结构性键（record / 对偶键） |
| D2 | LineageTagPropagationProcessor.java:85 | 1 | **FP** | visited 防环键 `entityType + "#" + entityId`：entityType 域 = 实体类型名（Java 类简单名/受控常量，标识符字符集**不可能含 #**）；entityId = 平台 sys ID（无 #）；两分量域均可证排除分隔符 → 碰撞数学不可达 |
| D3 | LineageTagPropagationProcessor.java:136 | 1 | **FP** | 同 D2（`ENTITY_TYPE_NOP_META_TABLE` 编译期常量 + targetTableId sys ID） |
| D4 | LineageTagPropagationProcessor.java:152 | 1 | **FP** | 同 D2（visited.contains 内联拼接，分量同域） |
| D5 | NopMetaLineageEdgeQueryAction.java:258 | 1 | **P1** | `key = sourceId + "\|" + sourceColumn + "\|" + targetColumn`：sourceColumn/targetColumn = **SQL 解析派生标识符**（用户 SQL 产物，带引号标识符方言下域不受控）；该键兼作 `:260 seenKeys.add` 与 `:259 existingEdgeMap.get` —— 碰撞 = 边去重失败 → 重复 INSERT / 漏更新（MA7.4-02 注释明示该键守护 INSERT 去重）；AR-03 类别残留 |
| D6 | NopMetaLineageEdgeQueryAction.java:487 | 1 | **P1** | `map.put(sourceTableId + "\|" + sourceColumn + "\|" + targetColumn, e)`：existing-edge map 键，分量同 D5（SQL 派生标识符）；碰撞 = 不同边静默塌缩 → 错误 update/insert 路由 |
| | **合计** | **6** | **P1 × 3 + FP × 3** | |

> FP 3 条（D2-D4）标注方式 = §0 方式 (a) baseline 驻留。P1 3 条（D1/D5/D6）修复 = 结构性键（record/`Map.entry` 对偶/`List<Object>`，沿 AR-03 修复先例 `AggregationHelper.memoryGroupBy`）。

---

## §6 族 E — INV-BIGDEC（2 命中）：**旧 optimization-candidate 裁定推翻 → 全部 P1**

**重裁依据（plan 2026-08-14-1133-2 closure 旧裁定**：*"ORDER BY/WHERE 比较路径（非 SUM/AVG 聚合），Long>2^53 精度丢失在排序/过滤上下文中影响较小——裁定为 optimization candidate"***，本次推翻）**：

1. **家族定义是正确性而非影响度**：本循环 silent-wrong-result 家族的判据 = 静默错算是否存在，不是影响大小。`MemoryFilterEvaluator.toBigDecimal:356` 供 WHERE 内存过滤：两个仅在第 54 位后不同的 Long（>2^53）经 `doubleValue()` 塌缩相等 → **等值过滤静默错配结果行**——与 AR-10（SUM 错算）同为家族定义内缺陷，仅危害面不同；"影响较小"是影响评估，不构成非缺陷结论。
2. **缺陷对完整携带**：两处私有拷贝均同时携带 AR-10 缺陷对——① `((Number) v).doubleValue()` 无整数路由精度丢失；② 非 Number 非 BigDecimal 输入（String 数值）静默 `return null`（`MemoryOrderByComparator:134` / 同型）→ 调用方回退字符串比较/过滤不命中——String 静默跳过正是 AR-10 修复明细之一。
3. **修复形态已在库内**：`AggregationHelper.toBigDecimal`（AR-10 修复：整数 longValue 无损路由 + 浮点 doubleValue + String 解析 + benign-miss DEBUG）为规范实现，两私有拷贝改走该形态（或委托该 helper）机械且 en 行为不变。
4. **类别立场**：AR-10 只修了聚合路径一处，两处比较路径私有拷贝留存——"修实例不修类别"直接实证（catalog ③ 已如此记载），本循环立项目的即消灭此类残留。

| red-list# | 站点 | 命中数 | 终态 | 理由 |
|---|------|--------|------|------|
| E1 | MemoryFilterEvaluator.java:356 | 1 | **P1** | WHERE 过滤路径 toBigDecimal：Long>2^53 等值错配 + String 静默 null；修复沿 AR-10 路由形态 |
| E2 | MemoryOrderByComparator.java:132 | 1 | **P1** | ORDER BY 比较路径同型私有拷贝：超精度长整型比较塌缩相等（错序/并列）+ String 静默 null 回退字符串序；修复沿 AR-10 路由形态 |
| | **合计** | **2** | **P1 × 2** | |

> **终态豁免清单核数**：优化候选维持 = **0** 条（旧裁定 2 条全部推翻转 P1）→ I5' baseline 终态 = FP 18（§4）+ FP 3（§5）= **21 键**，无优化候选驻留项。

---

## §7 新族判定（B2）：**0 新族（显式）**

对抗探查（`adversarial-probing-notes-cycle2.md`）10 方向结论：

| 对抗方向 | 是否新族 | 处置 |
|----------|----------|------|
| 1【方言族专属】 | 否 | 全部方言发射点在 SUPPORTED_DIALECTS fail-fast 门内或为已修先例 |
| 2【竞态族专属】 | 否 | static-init-only + AR-07 修复形态在位 |
| 3【toUpperCase 对称】 | 否 | 33/7 双向同规则覆盖 |
| 4【format/equalsIC/CIO/Collator】 | 否 | 0 / Character 级安全 ×26 / 0 |
| 5【contains 不可判定 receiver】 | 否 | 2 处均为 Set receiver（集合成员查询，出界正确） |
| 6【静默 NFE】 | 否 | 15 合规 + 1 **latent-form 观察**（`MetaTableProfiler.toLong:548`，调用面 COUNT-only 不可达）→ watch-only 登记 roadmap backlog（含源审计路径），不构成 live defect；若未来 queryLong 接入可空/非整数聚合须先 ErrorCode 化 |
| 7【narrow watch 边界】 | 否 | 11 候选全部 widening/整值/括号正确 |
| 8【BigDecimal 边界】 | 否 | BigInteger 精确 ×2 + 受保护 ×1 + double 语义域 ×3 |
| 9【lambda/String.join】 | 否 | 全部 SQL/消息拼接，零键用途 |
| 10【跨子模块】 | 否 | api/core/dao/app/web 0 命中 |

**不触发 Cycle 3 / I1 派发**（触发条件 = 发现目录外新失败族；本轮 0）。**无随新族 watch-only 的站点需要逐条落终态**（B2 尾款仅适用于存在新族登记的情形；方向 6 的 latent 观察非新族登记，属 roadmap watch 条目，其 Why-Not-Blocking 已在探查笔记与 backlog 条目中写明：调用面 COUNT-only，两风险路径当前均不可达）。

---

## §8 端到端链条完整性自检（Anti-Hollow）

| 链条环节 | 完整性证据 |
|----------|------------|
| 门禁命中 → red list | `formal-red-list-cycle2.md` 67 项，每项含 `文件:行` + 族 + 复现命令；与 I1' 快照零漂移（三层证据） |
| red list → 裁决归属 | 本表 §2/§4/§5/§6 逐条 67/67 命中均有唯一非"待定"终态（46 P1 / 21 FP / 0 维持 / 0 新族） |
| 裁决 → I4' 可执行描述 | §2（locale 40 → Locale.ROOT + 安全 2 处优先 + 对抗测试指令）、§5（delim 3 → 结构性键沿 AR-03）、§6（bigdec 2 → AR-10 路由形态）、§4（C8 → exact-match 沿 AR-05）；权威分母 = 本表 |
| FP → 终态处置 | §0 方式 (a) 裁定 + 21 条 FP 各有书面理由；I5' baseline 重写 = 21 键豁免清单（0820-3 V2 消费） |
| 对抗盲区 → 新族派发 | §7 十方向 0 新族（显式），不触发 Cycle 3；latent 观察入 backlog |
| 已知 live defect 降级检查 | §1 零悬挂；全部 P1 无 deferred；FP 条目均有语义级理由（非"暂不处理"）；优化候选旧裁定经重裁**推翻升级** P1（无降级方向项） |

**链条无断点。**

---

## §9 deferred 项（本案）

**本裁决表无 deferred 项**。46 P1 全部派 I4'（0820-3），21 FP 全部有方式 (a) 终态处置，0 新族。无任何条目以"暂不处理/optimize-later/if-time"挂起（Anti-Slacking 合规）。

> 唯一非阻塞观察 = 对抗方向 6 的 `MetaTableProfiler.toLong:548` latent-form（watch-only，非缺陷非 deferred 工作项），登记于 roadmap Follow-up Backlog 并附 Why-Not-Blocking。

---

## §10 P1 移交清单（I4' 唯一输入，plan 0820-3 Phase 1）

| 批次 | 子族 | 站点数 | 修复形态 | 测试要求 |
|------|------|--------|----------|----------|
| P1-A（优先，安全语义） | locale | 2（MDCP:70 blocklist、MQRE:387 sandbox） | `Locale.ROOT` | 各配大小写变体绕过对抗测试 |
| P1-B | locale 其余 | 38 | `Locale.ROOT`（机器比较语义全量） | tr-TR 默认 locale 回归（先例 `TestLocalReconciliationProcessorLocale`；端到端先例 `TestNopMetaLineageEdgeBizModel`）+ 类别清扫分母核对（本表 40 行） |
| P1-C | contains-classify | 1（MTP:567 isStringType） | exact-match `Set.of`（AR-05 形态） | 分类测试扩展（先例 `TestMetaTableProfilerClassification`） |
| P1-D | delim-key | 3（ACP:144、NMLEQA:258/:487） | 结构性键（AR-03 形态） | 键碰撞回归（含正则 pattern 含 `\|` 用例） |
| P1-E | bigdec-precision | 2（MOBC:132、MFE:356） | AR-10 路由形态（整数 longValue / 浮点 doubleValue / String 解析） | Long>2^53 等值/排序测试 + String 数值接线测试 |
| **合计** | | **46** | | |

---

## 引用

- `formal-red-list-cycle2.md`（裁决输入：67 项命中，零漂移定稿）
- `adversarial-probing-notes-cycle2.md`（对抗探查：10 方向 0 新族，latent 观察 1 项）
- `invariant-catalog.md`（5 条不变式陈述与检测边界，裁决依据）
- `initial-red-list-cycle2.md` + `baseline-cycle2/silent-wrong-result.json`（棘轮零点与 FP 驻留载体）
- `ai-dev/plans/2026-08-14-1133-2-nop-metadata-profiler-precision-silent-wrong-result.md`（被推翻的 optimization-candidate 旧裁定出处）
- `ai-dev/plans/2026-08-15-0820-3-nop-metadata-silent-wrong-result-fix-and-cycle2-closure.md`（I4'–I6' 消费方）
- `ai-dev/skills/invariant-loop-audit-prompt.md`（类别清扫强制 + Loop Rule 预授权派生）

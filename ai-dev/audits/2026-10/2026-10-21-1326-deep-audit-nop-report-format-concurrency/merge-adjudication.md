# merge-adjudication — WI 5.4 nop-report + nop-format 并发审计合并裁定

- 裁定人：CRA WI 5.4 独立合并裁定子代理（非四维复核人、非 C10 复核人）。裁定日期：2026-10-22。
- 裁定输入（只读，未修改）：C1-shared-mutable-state.md、C5-memory-bounds.md、C6-resource-lifecycle.md、C9-hot-path.md（各含「维度复核结论」+「最终保留项」）、C10-test-protection.md（25 行矩阵 + 复核结论）、cra-index.md（WI 1.1–5.3 台账）、dispatch-log.md、WI 5.3 merge-adjudication.md（家族先例对照）。
- 裁定纪律：逐条三态终裁定；不新增发现、不重写发现实体；每条一句理由并引 file:line 或维度文件节。

## §一 裁定输入与实体对账

| 维度文件 | 初审编号 | R2 深挖 | 复核补写 | 复核保留 | 驳回 | 降级 |
|---|---|---|---|---|---|---|
| C1 共享可变状态 | 5（C1-001~005） | 1（C1-R2-001） | 1（C1-006） | 7 | 0 | 0 |
| C5 内存有界性 | 6（C5-001~006） | 2（R2-001/002） | 0 | 8 | 0 | 2（C5-002/003，P2→P3） |
| C6 资源生命周期 | 4（C6-001~004） | 2（R2-001/002） | 0 | 5 | 1（C6-R2-002） | 0 |
| C9 热路径 | 5（C9-001~005） | 1（C9-R2-001） | 0 | 5 | 1（C9-004） | 0 |
| **合计** | **20** | **6** | **1** | **25** | **2** | **2** |

- 实体链：初审 20 + R2 6 = 26 编号实体，C1 复核补写 C1-006 +1 → **27 编号实体 = 保留 25 + 驳回 2**。
- P0：**0**（四维无 P0 判级；P0 即修通道显式空执行，对齐 campaign 惯例）。
- 单元规模与 Protected Area：894f/102,861（19 源码根）；nop-report-api 27f/2,782 plan-first，本轮 0 张发现触碰 api 叶（dispatch-log §Protected Area）。

## §二 跨维度终裁定（25 条三态权威表）

三态定义：**维持** = 终局保留（含最终严重度）；**跨维度调整** = 跨维度合并/主次化；**移交** = 移出本单元（P0 即修 / 7.1 裁决通道归档）。通道列按 campaign 惯例：P1 → cra-index「P1 发现汇总」台账待 7.1；P2 → 「P2 裁决台账」7.1 逐项裁决（修 / deferred+Why Not Blocking）；P3 → 登记级。

| 编号 | 终态 | 终严重度 | 通道 | 一句裁定理由 |
|------|------|---------|------|------------|
| C5-001 | 维持 | **P1** | **移交 7.1 P1 台账登记**（roadmap Phase 7 统一裁定；非本单元 P0 即修） | 分配先于校验 + 文件/netty 双生产路径可达 + 同族三处保护（CountArray 1M/DynLV 64MB/LV 相对）反衬唯此无界（ModelBasedBinaryRecordDeserializer.java:116-123、AbstractModelBasedRecordDeserializer.java:208-218；C5 §复核）——危害与可达性双闭合，本单元唯一 P1。 |
| C1-001 | 维持 | P2 | P2 裁决台账 | 内层裸 HashMap/TreeSet 结构缺陷确定（DocumentConverterManager.java:154-161），注册点唯一启动期、风险窗口由 public 再注册 API 维持——条件性 P2 恰当。 |
| C1-002 | 维持 | P2 | P2 裁决台账 | 数据竞争确定但 addFontAlias 生产零调用方、后果条件性（FontManager.java:141-143,123-134）——与 C9-002 同站点互补面（补同步 vs 收锁，方向相反），**不合并**，改单设计联合闭合注记（§四 G4）。 |
| C1-003 | 维持 | P2 | P2 裁决台账 | public addFormat 无运行时守卫确定（ExcelFormatHelper.java:271-273），当前仅类初始化触达——与 C9-001 同站点（守卫 vs 缓存，缺陷面独立），**不合并**，改单设计联合闭合注记（§四 G5）。 |
| C1-004 | 维持 | P3 | 登记级 | 消费点对 Shape 只读、无现网变异点（JFreeChartStyleAdapter.java:119-121；条目 13→12 更正不影响），理论风险 P3。 |
| C1-005 | 维持 | P2 | P2 裁决台账 | detach 契约违背确定、父/子交错读即静默错位（RandomAccessFileBinaryDataReader.java:172-179），现网顺序使用未触发——detach 契约族主登记（§四 G2）。 |
| C1-R2-001 | 维持 | P3 | 登记级 | isDetached 恒 true、detach=duplicate 语义同化，生产零消费方（ByteBufBinaryDataReader.java:430-442）——随 detach 契约族一并收口。 |
| C1-006 | 维持 | P3 | 登记级 | 补写立案成立：public static 非 final 单例引用、全库无再赋值点（FieldCodecRegistry.java:26），一行 `final` 修复——约定风险 P3。 |
| C5-002 | 维持 | P3（降级终态） | 登记级 | 降级成立：初审主调用 EOLFieldCodec 被证伪、全 repo 零生产调用方（C5 §复核）——潜伏 API 缺陷，maxLength 修复随 C5-001 同族处理。 |
| C5-003 | 维持 | P3（降级终态） | 登记级 | 降级成立：反序列化主流程无 detach/readAvailableBytes 生产触发点（C5 §复核）——潜伏结构性缺陷，兼属 detach 契约族物化面。 |
| C5-004 | 维持 | P2 | P2 裁决台账 | 流式接口全表物化实证且批量导入主路径可达（ExcelRecordInput.java:87-98；行号更正已入档）——WI 1.7 C5-001 同缺陷第二实例，独立登记、修复联动（§四）。 |
| C5-005 | 维持 | P3 | 登记级 | 默认 maxSizePerPage=-1 无界 + 加载前一次性 byte[] 驻留（PdfDoc.java:62-101、ResourceDocumentParser.java:203；streamCache 事实修正已入档）——库 API 暴露面 P3 恰当。 |
| C5-006 | 维持 | P3 | 登记级 | 链式转换中间结果三重驻留属语义固有物化（ChainedDocumentConverter.java:44-56）——性能/规模候选口径维持。 |
| C5-R2-001 | 维持 | P2 | P2 裁决台账 | rawVarName peek+hex 4-5× 放大驻留至请求结束，两条生产解码路径可达（ModelBasedBinaryRecordDeserializer.java:52-54 等）——与 C5-001 同 diff 最经济。 |
| C5-R2-002 | 维持 | P3 | 登记级 | 单文档物化后即释放、OOM 需 GB 级文本输入超出声明场景（C5 §复核）——登记级维持。 |
| C6-001 | 维持 | P2 | P2 裁决台账 | 构造异常路径 fd 泄漏确定且生产可达（RandomAccessFileBinaryDataReader.java:54-61、ModelBasedResourceRecordIO.java:89；close 幂等备注修正不影响）——构造泄漏族第 5 例（§四）。 |
| C6-002 | 维持 | P3 | 登记级 | 一次性 globalTimer 单点 + 重启永久残留 + 删除失败静默（ExcelReportHelper.java:116-134；清扫钩子全库零命中）——慢性泄漏 P3。 |
| C6-003 | 维持 | P3 | 登记级 | 共享 PDPage 无关闭序契约但生产零外部调用方、唯一内部调用序安全（PdfDoc.java:104-142,152,154）——理论风险 P3。 |
| C6-004 | 维持 | P3 | 登记级 | 现网全部调用面 close 纪律完备，登记为回归守备缺口（OfficePackage.java:70-97,123-134,153-157）——守备面确认 P3。 |
| C6-R2-001 | 维持 | P3 | 登记级 | 顶层 reader 所有权不对称当前调用序不触发（ModelBasedPacketCodec.java:166-179、PacketCodecHandler.java:91-92）——回归地雷 P3；随 detach/reader 契约族。 |
| C9-001 | 维持 | P2 | P2 裁决台账 | 每单元格 5-7 次 replaceAll + SimpleDateFormat/DecimalFormat 构造，频率×代价量化链成立（ExcelFormatHelper.java:94-142,144-230）——与 C1-003 同站点单设计闭合。 |
| C9-002 | 维持 | P2（附校准） | P2 裁决台账 | 首次 CJK 回退解析在 JVM 单例锁内做秒级字体 I/O 阻塞全部并发导出（FontManager.java:90-108,248-296）——锁内 I/O 族第 4 例；一次性 + per-document fontCache 缓解（复核校准）支持 P2 不升格，7.1 登记须附校准注记。 |
| C9-003 | 维持 | P3 | 登记级 | 交替格式下单项 ThreadLocal 缓存 100% 失效、每格 6 正则重匹配（ExcelDateHelper.java:371-395,481-512）——与 C9-001 同修面。 |
| C9-005 | 维持 | P3 | 登记级 | 本单元热路径零 JMH 基准（nop-benchmark 仅 json/orm/stream/xlang/xpl）——缺口即发现，随修复轮次闭合。 |
| C9-R2-001 | 维持 | P3 | 登记级 | 二进制 trailer lambda 每记录 prefix.toByteArray() 分配、同函数文本分支已示范正确写法（RecordFileMeta.java:137-145）——P3 维持。 |

**跨维度调整：0 条。** 唯二候选组（C1-002↔C9-002、C1-003↔C9-001）经裁定为**同站点互补缺陷面而非同根因**：前者的两组修复方向相反（补同步 vs 收窄锁），后者分属守卫缺失与缓存缺失——合并任一方都会丢失另一缺陷面，故全部维持独立立案，降为「单设计联合闭合」协同注记（§四 G4/G5）。同维度内的 C5-001↔C5-R2-001 同理维持双立案（无界分配 vs raw 放大两机制），仅注记同 diff。

**驳回项终局确认（2 条，均不进入 25）：**
- C6-R2-002 **驳回确认**：use-after-close 在全部实际调用序不可达（XLang 编译仅持变量名 ScopeIdentifierExecutable.java:17,35 + 唯一消费链 OfficeDocXmlBuilder.java:174-176 不求值表达式，C6 §复核）——前提证伪型驳回。watch-only 残留：OfficeDocModelWriter 渲染作用域未提供 ofcPkg，未来求值面落地须补变量注入契约。
- C9-004 **驳回确认**：readBytesTerm 全仓零生产调用方、调用频率=0 量化链不成立（RecordFileMeta.java:110-114 对 field.terminator 直接抛错，C9 §复核）——误报校准型驳回。watch-only：terminator 字段语义未来接入解码路径须重新立项。

## §三 终态统计与数字对账

### 终态统计表（P1/P2/P3 × 维度）

| 维度 | P1 | P2 | P3 | 小计 |
|------|----|----|----|------|
| C1 共享可变状态 | 0 | 4（C1-001/002/003/005） | 3（C1-004、C1-R2-001、C1-006） | **7** |
| C5 内存有界性 | 1（C5-001） | 2（C5-004、C5-R2-001） | 5（C5-002/003/005/006、C5-R2-002） | **8** |
| C6 资源生命周期 | 0 | 1（C6-001） | 4（C6-002/003/004、C6-R2-001） | **5** |
| C9 热路径 | 0 | 2（C9-001/002） | 3（C9-003、C9-005、C9-R2-001） | **5** |
| **合计** | **1** | **9** | **15** | **25** |

### 对账过程（七步，全部闭合）

1. **维度最终保留项行数**：C1 §最终保留项 7 行、C5 8 行、C6 5 行、C9 5 行 → 25，与各文件「最终保留合计」自述（7/8/5/5）逐一相符。
2. **实体链闭合**：27 编号实体（§一）= 保留 25 + 驳回 2；C1-006 补写使清单表行 10 引用可解析，无表-实体悬挂。
3. **降级折入**：C5-002/C5-003 P2→P3 已折入终态 P3×15（C5 行 P3=5）；C1/C6/C9 复核零降级，与各文件自述一致。
4. **严重度逐行对 C10 矩阵**：§二表 25 行的严重度与 C10 矩阵「严重度」列逐行一致（C10 复核已独立核对「严重度逐行与四维文件一致」）。
5. **编号集合对 C10 矩阵**：§二 25 行编号集合与矩阵 25 行差集为空；驳回两项（C6-R2-002/C9-004）在矩阵缺席 = 正确（C10 复核同判）。
6. **缺保护登记闭合**：C10 会红 0 / 不会红 25；缺保护登记 P1×1 + P2×9 + P3×15 = 25（C10 复核修正 C6-002 漏登、聚合组 4→5 后成立）。排版注记：C6-002 补登位置在 C10 §缺保护登记 P2 小节末，但其严重度计入 P3×15——属排版位置，非计数差异。25 行中 12 行存在相邻功能面保护（假保护形态：功能面绿不保护并发/内存/契约缺陷面），13 行完全无相关测试。
7. **dispatch-log Phase 4 汇总核对**：「保留 25 = P1×1 + P2×9 + P3×15；驳回 2；降级 2；全维零 P0」与本裁定终态完全一致。

### 口径一致性裁定（不改变任何终态）

- **C5-002 保留 P3 vs C9-004 驳回**（同一 readBytesTerm 代码面）：不矛盾。C9 口径要求热路径量化链（频率>0），频率=0 即驳回；C5 口径允许「无界实现属实但零调用方」按潜伏 API 缺陷降级登记。两维立项口径不同、各自自洽，修复均归并到 C5-001 同族（maxLength 参数）。
- **C1-005 维持 P2 vs C5-003 降级 P3 vs C6-R2-002 驳回**：三者证据强度不同。C1-005 契约违背确定、detach 生产调用方存在于 reader 族内部，新调用方按接口契约使用即触发；C5-003 物化语义属实但生产流完全无触发点；C6-R2-002 经执行序验证在任何调用序不可达。三维按各自口径判定，互相不矛盾，均予确认。
- **C1-006 补写 vs C1 round2 清扫表**：round2「安全」结论针对内部三 map（ConcurrentHashMap），补写针对 DEFAULT 引用可变性，两结论不冲突（C1 §复核已裁定），确认。

## §四 跨单元同族关联（对照 cra-index，零同站点重复立项）

### §4.1 必核条目（4+1 条，家族判定）

| 本单元发现 | cra-index 关联行（file:line 于 cra-index.md） | 家族判定 |
|-----------|------------------------------------------|---------|
| **C5-001**（数据驱动长度无界分配，随族 C5-R2-001/C5-002） | WI 5.3 rowLimit 族 = 5.3 merge-adjudication §三 MA-03「无界载入/物化」（CL-009 主 P1，:31）+ §四 rowLimit P1 裁定（:39）；cra-index WI 5.3 计数口径「P1×2（W-01/W-04 dashboard 行上界）」（:74）。stream 族先例 = `FAM-1 \| 1.1` materialization store 无界（:112）、`C5-001 \| 1.5` 扫描无 LIMIT 全量载入（:133）、`C8-001 \| 3.3`（自注与 WI 2.3 C3-003/WI 3.1 C5-001 跨单元同族 P1 对齐，:228） | **同主题既有族（无界物化/无上界读，跨 ≥5 单元）的机制新成员**：既有成员均为查询行上界/store/缓冲形态，长度前缀协议驱动的「分配先于校验」为本单元独有新机制——无既有家族编号可继承，判**同主题新成员**。修复对齐模块内既有保护惯例（CountArrayBinaryCodec.checkCount ≤1M）与 5.3 MA-03 上界补齐模式；7.1 台账登记时关联行 1.7/1.5/1.1/5.3 |
| **C6-001**（构造器 fd 泄漏） | `nop-network-integration-C6-018` SftpClient 构造期失败会话无人回收（:260）、`C6-017` bind 失败不关事件循环组（:259）、`nop-core-framework-C6-001` loadPluginFromJar 失败点 try 外（:170）、`nop-host-C6-001` 上传流失败路径 fd 泄漏（:249） | **既有跨单元同型族第 5 例：构造/获取失败路径资源泄漏族**（cra-index 无正式族编号，P1 台账 4 例先例）；修复模式对齐 4.3 C6-018 构造安全序（先建后换/finally 兜底） |
| **C1-002**（FontManager 裸写） | 最近先例 `C1-001（MA-01）\| 1.3` UDF 注册表公开 register() 任意线程写 × 无锁读（:123）；次近 `C1-001 \| 4.2` Nacos 事件线程原地改写共享实例（:237）、`C1-007 \| 2.2` 配置三字段裸写裸读（:163） | **核实结论：不属「锁内 I/O 族」**（缺陷方向相反——该同步处零同步）；归**「全局单例可变注册表裸写族」**新成员，与单元内 C1-001/C1-003 同族（§4.2 G1）。锁内 I/O 的正确族归属是 C9-002（下行） |
| **C9-002**（持锁字体 I/O） | `C2-002（MA-04）\| 1.2` computeIfAbsent 锁内 HTTP 挂死+锁毒化（:115）、`C1-001（MA-01）\| 3.2` CHM bin 锁内同步执行（:220）、`C2-001 \| 2.1` 持 entry 监视器加载模型（:159） | **既有「锁内 I/O / 持锁长操作」族第 4 例**（P1 先例×2）；本单元 P2 判级成立（一次性 + fontCache 缓解），7.1 登记附校准注记防误升格 |
| **C5-004**（Excel 导入全表物化） | `C5-001 \| 1.7` excel-reader beforeRead 全 sheet 物化 List：50 万行 1.5–3GB（:152）——C5 复核已判「同一缺陷族在 nop-report-core 侧的第二实例，修复应联动」（C5 §复核 C5-004 行） | **同缺陷跨单元双轨**（同族不同模块不同站点）：按 campaign 惯例独立登记（先例：4.1「WI 1.2 debezium 同族双轨」独立），修复联动收集器分批化 + 行数上限配置 |

### §4.2 本单元内同族分组（修复协同视图，非台账家族编号、非新发现）

| 组 | 成员 | 协同内容 |
|----|------|---------|
| G1 全局注册表容器非并发安全 | C1-001、C1-002（alias 面）、C1-003、C1-006（约定面） | 同一修复范式：CHM 化 / final / 守卫，单批次处理 |
| G2 detach/reader 契约族 | C1-005（主）、C1-R2-001、C5-003（物化面）、C6-R2-001（所有权面） | 契约声明一次 + 四实现各自对齐，单设计批 |
| G3 数据驱动长度信任边界 | C5-001（主）、C5-R2-001、C5-002（潜伏 API 面） | 同 diff：getFieldLength 校验（复用 ERR_RECORD_DECODE_LENGTH_IS_TOO_LONG，RecordErrors.java:71）+ maxLength 参数 |
| G4 FontManager 站点（强制联合设计） | C1-002 + C9-002 | 修复方向相反（补同步 vs 收锁），单设计同时闭合，分开修必然互相回退 |
| G5 ExcelFormatHelper 站点（联合设计） | C1-003 + C9-001（+C9-003 同包格式化面） | 格式工厂缓存（CHM + 有界）一次设计同时闭合守卫与分配两面 |
| G6 全量物化登记级 | C5-004、C5-005、C5-006、C5-R2-002 | 上界配置/分批/落盘临时文件范式，独立工单化 |
| 单点 | C1-004、C6-001、C6-002、C6-003、C6-004、C9-R2-001、C9-005 | 无须组化 |

### §4.3 其余发现的家族判定（一句话）

C5-005/C5-006/C5-R2-002：全量物化主题族弱关联（`C3-003 \| 2.3` :175、`C8-001 \| 3.3` :228 流式无界缓冲族），机制各异，独立登记。C1-004/C1-006、C6-003/C6-004、C1-R2-001/C6-R2-001（netty 帧缓冲 reader 面）、C9-001/C9-003/C9-R2-001/C9-005、C6-002（弱关联 5.3 MA-04 临时资源清理不对称，5.3 merge-adjudication :32）：**均判「新家族」或单元局部登记级，cra-index 无同型先例**。

**查重结论**：cra-index P1 台账与 P0 通道中 nop-report / nop-format 单元条目 **0 命中**——本单元 25 条全部为新单元线索，无跨单元重复编号；§4.1 五组为同族主题/同型关联（修复方案层复用，不合并工单）。

## §五 覆盖缺口与盲区（单列登记，本轮不补审）

### 5.1 四维重合零痕迹（交集，最高优先缺口）

| 源码根 | 规模（dispatch-log 实测） | 四维痕迹 |
|--------|--------------------------|---------|
| nop-tablesaw | 9f/1,276 | C1/C5/C6/C9 全零 |
| nop-office-model | 17f/756 | 四维全零 |
| nop-office-doc-model | 17f/343 | 四维全零（C6-R2-002 仅讨论 OfficeDocModel 类语义，模块本体源文件未审） |
| nop-record-netty | 9f/529（io.nop.netty.ext.* 本体） | 四维全零 |

命名陷阱（C9 §复核观察）：round2 所称「netty 解码面」的 ModelBasedPacketCodec/ByteBufBinaryDataReader 物理位于 nop-record 的 io.nop.record.netty 包，**非** nop-record-netty 模块本体——四维对 record-netty 本体的零痕迹判定不受该行影响，但后续补审不得再把 io.nop.record.netty 误计为该源码根覆盖。

### 5.2 各维额外缺口（并集）

- C1：ooxml-common/docx/markdown/pptx 无显式核查痕迹（批B 仅 xlsx 有痕）。
- C5：mermaid（39f）、svg（12f）、chart-export（29f）。
- C6：mermaid、svg + converter（35f）、markdown-ext（6f）、ooxml-pptx（chart-export 对 C6 **非**缺口）。
- C9：ooxml（含 pptx）、markdown-ext + mermaid、svg（converter 对 C9 有痕，非缺口）。
- 并集（交集 4 根之外）：mermaid、svg、chart-export（仅 C5）、converter（仅 C6）、markdown-ext（C6/C9）、ooxml-pptx（C1 部分/C6/C9）、ooxml-common/docx/markdown（C1）。

### 5.3 owner doc drift

- **nop-format 无 owner doc**（占单元 84% 行数：86,231/102,861，dispatch-log §owner doc 漂移登记）；C5 因无声明规模包络被迫按事实包络评估（C5 文件头）。
- nop-report.md 58 行/0 处提及 format/excel/ooxml/pdf——nop-report 侧 owner doc 同样未覆盖并发/资源语义。
- 处置建议：修复轮次落地上界/守卫配置时同步补 owner doc 条目（SSOT 规则，先例：WI 5.3 §九 owner doc 欠账条款）。

### 5.4 C10 会红=0 结构性结论

- **会红 0 / 不会红 25**：无任何保留发现的立案缺陷面被现有测试套保护；结构性根因 = 全单元 0 多线程测试、0 JMH 基准、3 个关键测试 @Disabled（TestPdfDoc/TestPptxToMarkdownConverter/TestModelBasedTcpServer）（C10 §总体证据 1–3，复核全数重演通过）。
- 这是 campaign 继 WI 4.5（第四次同型确认）、WI 5.1（第 5 例）、WI 5.2（第 6 例）之后又一次全域会红=0 确认（WI 5.3 落盘未记序数；本单元按序列计为**第 7 例**——closure audit 裁定）；并发审计发现与测试套之间的系统性盲区再次成立。
- C10 复核两处修正已入档：缺保护登记补登 C6-002（Major）、C9-002 引用不存在的 TestPdfFontFidelity 改为 TestPdfRenderDefects（Minor）——均不影响矩阵结论。
- 修复准入建议（沿 C10 §缺保护登记）：每条修复须附使其变红的回归测试；C5-001 的 lengthExpr 恶意长度测试列为该 P1 修复的准入门槛。

## §六 通道与后续

1. **P0**：0，即修通道显式空执行（沿 campaign 惯例记录）。
2. **P1×1（C5-001）**：移交 7.1 裁决通道——由主代理在 cra-index「P1 发现汇总」台账登记（Finding ID 建议 `nop-report-format-C5-001`），关联行 1.7 C5-001 / 1.5 C5-001 / 1.1 FAM-1 / 5.3 MA-03；不在本单元就地即修。
3. **P2×9**：按 cra-index「P2 裁决台账」规则待 7.1 逐项裁决（修 in-scope / deferred + Why Not Blocking + successor）；C1-002/C9-002 与 C1-003/C9-001 两组登记时携带「单设计联合闭合」约束。
4. **P3×15**：登记级，不阻塞；随同族修复批顺带闭合（G2/G3/G6）。
5. **watch-only×2**：C6-R2-002 残留（ofcPkg 注入契约）、C9-004 残留（terminator 语义立项条件）——不占工单。
6. **cra-index 登记待办（主代理执行，本裁定不代改）**：新增 WI 5.4 计数口径注记 + CL 编号段说明（本单元未启用 CL 前缀，发现编号沿用 C{维度}-{序号}/R2 体系）+ P1 台账 1 行 + P2 裁决台账 9 行；5.1–5.2 缺口与 owner doc drift 登记随注记收录。
7. **closure audit 输入**：本文件 §二/§三 为后续 independent closure audit 的编号与计数权威表。

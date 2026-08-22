# 344 Nop JSON Parser/Writer 快路径优化（基准验证驱动）

> Plan Status: completed
> Last Reviewed: 2026-08-22
> Source: 本次会话对 nop-core 的 io.nop.core.lang.json 包与 nop-commons 的 TextScanner（io.nop.commons.text.tokenizer）源码调研，以及 `nop-benchmark/nop-benchmark-json` 的 JMH 实测基线；对标 Apache Fory JSON（~/sources/fory）的实现思路
> Related: 无活跃前置计划

## Purpose

在不改变 JSON 解析/序列化对外行为契约（loose 语法、注释支持、SourceLocation 保留、JObject/delta 语义）的前提下，为 Nop 自研 JSON parser 与 writer 增加局部快路径，缩小与 fastjson2/Jackson 的吞吐差距，并用 `nop-benchmark-json` 的 JMH 基准逐阶段验证收益与无回归。

## Current Baseline

以下均为 2026-08-22 在本机核实的 live repo 事实：

**性能基线（JMH，EishayParseTreeString，forks=1, wi=3/w=2s, i=3/r=2s, JDK26 Zulu 26.0.1）：**

| 实现 | 吞吐 ops/s | 相对 fastjson2 |
|---|---|---|
| fastjson1 | 756,271 | 0.49x |
| fastjson2 | 1,544,079 | 1.00x |
| jackson | 965,064 | 0.63x |
| **xlang (JsonTool.parseFromText)** | **428,868** | **0.28x（慢 ~3.6 倍）** |

历史注释数据（`EishayParseTreeString.java:111`，zulu17/forks=5）同样显示 xlang 慢 fastjson2 约 3.5 倍，两者一致。

**已确认的实现瓶颈（live code 锚点）：**

1. `nop-kernel/nop-commons/src/main/java/io/nop/commons/text/tokenizer/TextScanner.java`（1429 行）
   - `nextJsonString()`：无转义字符串也逐字符 `buf.append()`，每字符伴随 `col++/pos++` 簿记；没有先扫描转义区间再整段切片的快路径。
   - 数字解析（~L900-1008）：所有数字先进 MutableString buffer，再 `buf.toString()` 分配 String，最后 `Long.parseLong`/`Double.parseDouble` 并装箱返回。
   - 该类同时被 YAML 与 tokenizer 复用。
2. `nop-kernel/nop-core/src/main/java/io/nop/core/lang/json/handler/BuildObjectJsonHandler.java:109`
   - `addEntry()` 先 `map.containsKey(deferredName)` 判重（ERR_JSON_DUPLICATE_KEY），再经 `addToMap()` `put(...)`，同一 key 双重哈希定位。该类有两个覆写子类 `BuildXNodeJsonHandler`、`BuildJObjectJsonHandler`，改动必须覆盖其行为。
3. `nop-kernel/nop-core/src/main/java/io/nop/core/lang/json/parse/JsonParser.java`
   - 每个值/键调用 `sc.location()` 分配 `SourceLocation`，默认 handler 不消费该对象时属于纯浪费分配。**但惰性化需要 handler 侧声明机制的先行设计（6 类 handler 消费行为各异），已移出本计划范围（见 Deferred But Adjudicated）。**
4. `nop-kernel/nop-core/src/main/java/io/nop/core/lang/json/handler/CollectTextJsonHandler.java:390-416`

**共享面事实：** `TextScanner` 的下游消费方不止 JSON——实测包括 `nop-kernel/nop-xlang`（`AbstractExprParser` XLang 表达式词法）、`nop-report-core`、`nop-ai-core` 等 10+ 模块。因此本计划的测试门禁显式包含 nop-xlang 等下游模块，不能只跑 commons+core。

**输入表示事实：** `TextScanner` 基于 `ICharReader` 读字符；底层可能是 CharSequence 包装（可整段切片）也可能是流式 Reader（需经内部缓冲）。字符串快路径必须同时正确处理两种表示。
   - `numberValue()`/`booleanValue()` 通过 `value.toString()` 为每个标量产生临时 String 再 append。

**测试现状：**

- `nop-kernel/nop-core/src/test/java/io/nop/core/lang/json/TestJsonTool.java` 覆盖 JsonTool 入口行为。
- `nop-kernel/nop-commons/src/test/java/io/nop/commons/text/TestTextScanner.java` 含 `testNumber` 等用例，但未覆盖溢出回退、hex、指数等边界组合。
- 无针对 JSON 字符串转义边界（控制字符、`\uXXXX`、非法低代理）的专项扫描测试。

**基准设施现状：**

- 本机 JDK 26 下 `./mvnw compile -pl nop-benchmark/nop-benchmark-json` 不生成 `META-INF/BenchmarkList`（JDK 23+ 默认禁用隐式注解处理），导致 benchmark 类 `main()` 与 `org.openjdk.jmh.Main` 都无法启动；当前需手工 `javac -proc:full` 补编译才能跑 JMH。
- `nop-benchmark/nop-benchmark-json/pom.xml` 未配置 maven-compiler-plugin 的注解处理策略。

## Goals

- xlang 树解析路径（`EishayParseTreeString.xlang`）在本机同等 JMH 设置下相对 2026-08-22 基线获得可测量提升（aspirational 目标 ≥30%；该数字不作为验收线，验收口径见 Phase 5——实测值必须记录，未达标必须解释差距）。
- 序列化路径 Integer/Long/Boolean 标量写出不再产生临时 String（行为输出不变）。
- 全程保持既有公共契约不变：`JsonTool` API、JSON/YAML 共用的 TextScanner 语义、SourceLocation 行号列号正确性、loose 语法与注释支持。
- 每个 Phase 有聚焦回归测试保护新增边界行为，且按 Phase 1 固化的基准判读规则证明无回归。

## Non-Goals

- 不做 Bean 序列化的 JIT codegen / 反射替换（后续独立 plan）。
- 不引入 UTF-8 byte[] 直写的新序列化 API，不改变 `Appendable` 输出模型。
- 不做 TextScanner 的整体 SWAR 重写，不改变 tokenizer/YAML 复用面的现有语义。
- 不引入第三方 JSON 库，不改变 `JsonTool` 统一入口约定。
- 不处理池化 parser/writer 状态、AST-free 直接绑定等架构级改造。

## Scope

### In Scope

- `TextScanner` 中 JSON 字符串（strict 与 loose 两个变体）与数字词法的快路径（增量方法或内部改写，语义兼容）。
- `BuildObjectJsonHandler` 及其子类的重复键检测开销削减（行为规格见 Phase 3）。
- `CollectTextJsonHandler` 标量写出优化。
- `nop-benchmark-json` 注解处理配置修复、基准判读规则固化与结果记录。

### Out Of Scope

- `SourceLocation` 惰性化机制设计（移入 Deferred，见下）。
- `nop-core` 以外模块的 JSON 相关代码改造（nop-xlang 等仅作为回归门禁范围）。
- XLang 整体执行性能（见已有 xlang-execution-optimization 计划目录）。
- 公共 API 签名变更。

## Execution Plan

### Phase 1 - 基准设施修复与正式基线固化

Status: completed
Targets: `nop-benchmark/nop-benchmark-json/pom.xml`, `ai-dev/logs/`

- Item Types: `Fix | Proof`

- [x] 在 `nop-benchmark/nop-benchmark-json/pom.xml` 显式配置编译期注解处理（proc=full 或等效 processor 配置），使 JDK 23+ 下 `mvn compile` 后自动生成 `META-INF/BenchmarkList`，benchmark 类 `main()` 可直接运行
- [x] **固化基准判读规则并记入 daily log**：正式对照统一使用 forks=3、固定迭代参数与 JVM 选项；判定"无回归"= 均值差在两轮误差范围重叠且降幅 <5%；判定"提升"= 均值差超出误差上界。快速档（forks=1, wi=3/w=2s, i=3/r=2s）仅用于开发中观察，不作为门禁依据
- [x] 用固化规则在本机完整跑通 `EishayParseTreeString` 四项对比（fastjson1/fastjson2/jackson/xlang），将命令与结果记入 daily log 作为正式基线
- [x] 验证修复后的 pom 在 JDK 11 目标字节码下不引入新的编译警告或行为差异

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 干净环境下 `./mvnw clean compile -pl nop-benchmark/nop-benchmark-json` 后 `META-INF/BenchmarkList` 存在于该模块 target/classes 下
- [x] `java -cp ... org.openjdk.jmh.Main "EishayParseTreeString.*"` 可直接启动并产出四行吞吐结果
- [x] 正式基线数据（含 JMH 参数与本机环境说明）写入执行当日的 `ai-dev/logs/` 条目
- [x] No owner-doc update required（纯构建配置，不改变产品行为）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Parser 字符串快路径（无转义整段切片）

Status: completed
Targets: `nop-kernel/nop-commons/.../text/tokenizer/TextScanner.java`, `TestTextScanner.java`

- Item Types: `Fix`（性能缺陷）+ `Proof` + `Decision`

- [x] **Decision**：裁定 loose 变体 `nextLooseJsonString()`（TextScanner.java:1141，与 strict 版几乎复制粘贴）是否同步加快路径；若不同步，在此记录理由并确认 loose 路径不因本次改动变得更慢。**裁定：不同步。** 理由：(a) loose 闭引号需前瞻判定且"后跟非终止符的引号被丢弃"的既有语义使快路径分支复杂；(b) 剖析证实字符串扫描仅占解析热点 ~4%，loose 主要消费场景是 DSL 配置加载（启动期一次），非吞吐热路径；(c) 本次未触碰 loose 方法，行为不变性由新增 `testNextLooseJsonStringUnchanged` 回归测试锁定（含终止符前瞻与引号丢弃语义）
- [x] 为 JSON 字符串读取增加快路径：从引号后开始定位首个终止符（引号/反斜杠），区间内无转义时一次性构造结果字符串；含转义时回落到现有逐字符逻辑。两种底层输入表示（CharSequence 可直接切片 / 流式 Reader 经内部缓冲）都必须正确。**实现要点**：序列仅用于前瞻定位；被跳过字符仍经真实 `read0()` 消费以保持底层 reader 同步（抽象不破坏）；`consumeDigits` 补记 pos（修复其破坏 "pos=序列绝对索引" 不变量的存量缺陷，该不变量已被 XDefCommentParser 依赖）；pos 与序列不对齐时防御性回落慢路径
- [x] 快路径命中与回落两条路径的行列号簿记结果必须与现实现逐位一致（testNextJsonStringLocationBookkeeping 验证）
- [x] 新增聚焦测试覆盖 8 类边界：纯 ASCII 无转义、含 `\uXXXX`、含控制字符报错、跨行报错（JSON 串内 CR/LF）、空字符串、超过内部缓冲区长度的字符串、未闭合字符串、loose 变体行为不变
- [x] 确认 YAML 与 tokenizer 依赖的相邻方法（如 `nextJavaString`）行为不受影响（现有 TestTextScanner 全部通过）

**执行发现（诚实记录）**：字符串快路径单独收益 ≈0（415.7K vs 基线 423.6K ops/s，误差范围内）。stack profiler 实测热点分布：LinkedHashMap.newNode ~19.6%（树构建分配，属行为契约范围外）、skipBlank ~18.7%（safepoint 采样偏差放大，已做谓词内联微调）、nextJsonString 仅 ~3.9%。结论：词法层优化天花板有限，≥30% aspirational 目标需要结构性改造（codegen/直接绑定，已在 Deferred）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 新增测试全部通过，且明确覆盖上列 8 类边界
- [x] `./mvnw test -pl nop-kernel/nop-commons,nop-kernel/nop-core -am` 通过（含 TestJsonTool 集成回归）
- [x] 按 Phase 1 判读规则复测 `EishayParseTreeString.xlang`：无回归（415,674 ±7,239 vs 基线 423,608 ±12,246，误差重叠、降幅 1.9% < 5%）
- [x] No owner-doc update required（内部实现，公共契约不变）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - Parser 数字直接累加与重复键检测削减

Status: completed
Targets: `TextScanner.java`, `BuildObjectJsonHandler.java`, `TestTextScanner.java`, `TestJsonTool.java`

- Item Types: `Fix` + `Proof`

- [x] 整数（十进制）主路径改为数值直接累加，仅在检测到溢出/小数/指数/hex 后缀时回落到现有字符串解析逻辑；两路径产出的 Number 类型与值完全一致（Integer vs Long 判定规则不变）。**实现要点**：与字符串快路径同构的两层设计——序列前瞻扫描判定"纯数字+干净终止符"，只有完全干净才经 read0() 排空消费并按 consumeDigits 方式结算状态；溢出/后缀/小数/指数/流式输入一律原样交还旧逻辑（状态零扰动）。**调试记录**：初版用混合装箱类型三元表达式 `cond ? Integer.valueOf(..) : Long.valueOf(..)`，被 JLS §15.25 二元数值提升统一为 long 恒返回 Long，已由字节码反编译定位并改为显式 if/else
- [x] **重复键检测削减（行为规格，实现方式不限）**：`BuildObjectJsonHandler.addEntry()` 在保持现有可观测行为的前提下减少哈希定位次数——(a) 本条仅约束基类路径：基类路径上任何值类型（含显式 JSON null 存为 Java null 的情形）的重复键都必须抛 ERR_JSON_DUPLICATE_KEY；(b) 两个覆写子类的既有行为不回归：`BuildXNodeJsonHandler` 完全覆写了 addEntry（重复键抛 ERR_XML_DUPLICATE_ATTR_NAME，属设计行为，不得改变）；`BuildJObjectJsonHandler` 只覆写 addToMap——**任何实现必须继续以 addToMap 作为唯一写入钩子**（禁止绕过该覆写直连 Map 写入方法，否则 JObject/delta 语义静默回归）；(c) 若实现方案无法安全处理 null 值语义，允许保留 containsKey+put 并在 plan 中记录原因。**裁定：保留 containsKey+put（依据 (c)）**。理由：(1) 单次 put 返回值判重对 null 值歧义不安全；(2) sentinel 包装会泄漏进可观测的解析结果 Map；(3) profiler 显示 beginObject/判重不在热点帧内（beginObject ~0.9%），实测收益趋近于零，不值得引入正确性风险
- [x] 新增/扩展测试：Long.MAX_VALUE/MIN_VALUE 边界、溢出回落、前导零、hex（0x）、l/L/f/F/d/D 后缀、指数、重复键报错——其中必须包含 `{"a":null,"a":1}` 抛错用例、XNode 解析重复属性报错用例、JObject/delta 解析往返用例各至少一例。JObject 往返由既有 TestJsonTool.testYamlArrayComment/testJObjectComment 等用例覆盖（本轮全绿）

Exit Criteria:

- [x] 新增数字边界与重复键测试全部通过（含 null 值重复键用例）
- [x] `./mvnw test -pl nop-kernel/nop-commons,nop-kernel/nop-core -am` 通过
- [x] 按 Phase 1 判读规则复测 `EishayParseTreeString.xlang`：相对 Phase 2 结果提升或持平（425,175 ±4,892 vs 基线 423,608 ±12,246，误差重叠、持平）
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - Writer 标量直写

Status: completed
Targets: `nop-kernel/nop-core/.../lang/json/handler/CollectTextJsonHandler.java`, 相关测试

- Item Types: `Fix` + `Proof`

- [x] `numberValue()` 对 Integer/Long 主类型不经 `toString()` 直接把十进制字符写入 Appendable；Boolean 同理写 `true`/`false` 字面量；其余 Number 子类型保留现有路径。**实现要点**：StringBuilder 输出时走 JDK 的 `append(long)`（内部 Long.getChars 零临时 String）；同时覆盖树序列化实际路径 `writeValue()` 的 Number/Boolean 分支
- [x] 输出文本与现实现逐字符一致（含 htmlSafe 模式）
- [x] 新增测试断言各类标量（正负零、极值、htmlSafe 开关）序列化文本不变

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 新增序列化文本一致性测试通过；`TestJsonTool` 等现有测试通过
- [x] `./mvnw test -pl nop-kernel/nop-core -am` 通过
- [x] 若仓库存在 stringify 对比基准则复测无回归；不存在则以微基准/人工对比代替并说明理由。**裁定：仓库仅有 parse 基准无 stringify 基准**；以新增的逐字符文本一致性断言 + 全套回归作为等价验证（本阶段目标是消除分配而非提升可测吞吐）
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - 收尾验证与结论固化

Status: completed
Targets: `nop-benchmark/nop-benchmark-json`, `ai-dev/logs/`, 本计划

- Item Types: `Proof`

- [x] 以 Phase 1 固化的同一命令与判读规则复测四项基准，记录前后对照表（含误差范围）。**执行说明**：最终四项组合复测两次均被环境负载作废（load average 14~22，fastjson2 自身劣化 37%、jackson ±26%，机器级干扰非代码因素）；权威对照采用三次隔离 xlang 运行（同命令形态、误差紧凑）：

| 时点 | xlang 吞吐 ops/s | 误差 | 备注 |
|---|---|---|---|
| 基线（P0） | 423,608 | ±12,246 | Phase 1 正式基线 |
| P2 后 | 415,674 | ±7,239 | 无回归判定成立 |
| P3 后 | 425,175 | ±4,892 | 持平偏正（+0.4%，误差重叠） |

- [x] 对照表写入 daily log；若累计提升 <30%，在 plan 内写明实测值与差距原因分析。**差距分析**：实测累计提升 ≈0%（持平）。原因有 profiler 实证：EishayParseTreeString 热点为 LinkedHashMap.newNode ~19.6%（树构建分配，属行为契约范围外，本计划 Non-Goal）、skipBlank ~18.7%（含 safepoint 采样偏差）、nextJsonString 仅 ~3.9%、consumeDigits ~2.9%——词法层可优化面合计 <10%。≥30% aspirational 目标需要结构性改造（codegen 直接绑定/免 AST），已显式列入 Deferred。本次交付的实际价值：(1) JMH 设施修复；(2) consumeDigits pos 不变量存量缺陷修复（XDefCommentParser 已依赖）；(3) writer 标量零临时 String 分配（降低 GC 压力，吞吐中性）；(4) 词法快路径与边界测试资产沉淀
- [x] **受影响模块全量验证**：`./mvnw test -pl nop-kernel/nop-commons,nop-kernel/nop-core,nop-kernel/nop-xlang -am` 通过（TextScanner 下游直接消费方）；`./mvnw compile -pl nop-report/nop-report-core,nop-ai/nop-ai-core -am` 通过（其余共享面消费方编译冒烟）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 前后对照表存在于 `ai-dev/logs/` 且数据可由记录的命令复现
- [x] `./mvnw test -pl nop-kernel/nop-commons,nop-kernel/nop-core,nop-kernel/nop-xlang -am` 通过
- [x] No owner-doc update required（无契约/API 变更）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复或经裁定收敛（JMH 设施不可用已修复；瓶颈点 1（字符串逐字符）、4（writer 标量 toString）已修复；瓶颈点 2（addEntry 双哈希）依行为规格 (c) 裁定保留原实现并记录理由；瓶颈点 3（SourceLocation）显式裁定 deferred，见 Deferred But Adjudicated）
- [x] 所有 Phase Exit Criteria 已勾选
- [x] 性能结果达成或差距已被显式记录并解释
- [x] 必要 focused verification 已完成（Phase 2-4 各自新增测试）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] No owner-doc update required（全计划无公共契约变更，此判定经 closure audit 复核）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] Anti-Hollow Check：closure audit 已确认快路径真实被 JSON 主链路调用（非死代码），且无静默吞错/占位实现
- [x] `./mvnw test -pl nop-kernel/nop-commons,nop-kernel/nop-core,nop-kernel/nop-xlang -am` 通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-kernel/nop-core --severity high` 对**本计划改动文件零命中**；模块级扫描的 8 个存量 hit（IValueMapper/ProxyCell/RecordBeanModelBuilder/AbstractResource/SimpleResourceStore/ZipResourceStore 的 UnsupportedOperationException）位于本计划未触碰文件，经审计核实为既有基线（非 344 引入），登记为 pre-existing baseline 不阻塞本次收口

## Deferred But Adjudicated

### SourceLocation 惰性化（JsonParser 每值/键分配位置对象）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 惰性化需要先设计 handler 侧的位置声明机制（6 类 handler 消费行为各异：错误上报需要精确 loc、XNode 构建依赖 loc 等），涉及公共接口演进与错误路径语义裁定，属于设计决策而非局部快路径；当前分配开销不阻塞词法层优化的收益成立
- Successor Required: `yes`
- Successor Path: 待新建 successor plan（需先行 design doc 裁定 marker 接口方案、错误路径 loc 是否可丢失、是否触碰 IJsonHandler 公共契约）

### Bean 序列化 JIT codegen（替代反射 getPropertyValue）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 属于架构级改造，需要独立的 codegen 设计与安全评审，不影响本次词法层快路径收口
- Successor Required: `yes`
- Successor Path: 待新建 successor plan（建议命名 NNN-nop-json-bean-codegen）

### UTF-8 byte[] 直写序列化 API 与 parser/writer 池化

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 需要新增公共 API 与并发模型设计，超出"行为不变的快路径优化"边界
- Successor Required: `yes`
- Successor Path: 待新建 successor plan

## Non-Blocking Follow-ups

- `skipBlank`/空白判断查表化与 chunk 簿记（收益中等、涉及共享面较宽，观察 Phase 3 后的剩余 profile 再定）

## Risks And Rollback

- `TextScanner` 被 YAML/tokenizer 复用：所有改动必须保持既有公共方法语义，Phase 2/3 以现有 TestTextScanner 全绿为门禁；必要时用新增私有方法而非修改原方法签名
- 性能优化引入边界 bug：每个快路径必须有可回退的慢路径分支，且分支选择条件有测试覆盖
- JMH 数据波动：所有前后对照使用 Phase 1 固化的判读规则（forks=3、误差范围判定）；结论只引用同轮次数据

## Closure

Status Note: 全部 5 个 Phase 完成。词法层快路径与 writer 标量直写落地，行为契约零变更；性能实测持平（aspirational ≥30% 未达成，差距经 profiler 实证归因于结构性热点并已列入 Deferred successor）。JMH 设施修复与 consumeDigits pos 不变量缺陷修复为本计划的额外确定性收益。

Completed: 2026-08-22

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，只读审查）
- Audit Session: task ses_fd6732a27ffeOlxcCQ3K0gfABu
- Evidence:
  - Phase 1 Exit Criteria：PASS（审计员独立重跑 clean compile 后 BenchmarkList 存在；基线数据与 log 逐字一致）
  - Phase 2 Exit Criteria：PASS（TestTextScanner 15/15 实跑绿；8 类边界逐一对应测试方法核实；无回归数据一致）
  - Phase 3 Exit Criteria：PASS（数字边界/null 值重复键/XNode 重复属性断言逐条核实；addEntry 保留原实现符合裁定 (c)）
  - Phase 4 Exit Criteria：PASS（文本一致性断言含极值与 htmlSafe；无 stringify 基准的替代裁定成立——仓库仅有 parse 基准）
  - Phase 5 Exit Criteria：PASS（三模块门禁 201/233/551 由审计员独立重跑复现；对照表 plan↔log 逐字一致）
  - Anti-Hollow 检查：调用链追踪确认 tryNextSimpleJsonString/tryNextDecimalInteger 在 JsonTool.parseMap→JsonParser 主链路（TextScanner.java:1190/981）；writer 直写在 stringify→CollectTextJsonHandler.value/writeValue 链路（:449-453）；无空方法体/吞异常/no-op
  - Deferred 分类检查：SourceLocation 惰性化（6 个 handler 消费行为各异需接口级 marker 设计）、Bean codegen、UTF-8 API 均属设计先行机制，分类诚实无 in-scope defect 降级
  - 行为规格遵守：BuildXNodeJsonHandler.addEntry 完全覆写未被触碰；BuildJObjectJsonHandler.addToMap 唯一写入钩子保持；git diff 范围恰为声称的 5 个代码文件无越界
  - `node ai-dev/tools/check-plan-checklist.mjs --strict` 退出码 0（勾选完成后复核）
  - scan-hollow-implementations：本计划改动文件零命中；模块级 8 个存量 hit 为既有基线（非 344 引入），已在对应 Gate 登记
  - 遗留 Minor 项：Long.MIN_VALUE 量级整数走慢路径（行为正确，纯信息）；"控制字符报错"类别实际覆盖为 CR/LF 拒绝（新旧语义相同）

Follow-up:

- Bean 序列化 codegen / UTF-8 直写 API / SourceLocation 惰性化：见 Deferred But Adjudicated 的 successor 要求
- skipBlank 深度重写（chunk 簿记/SWAR）：保留在 Non-Blocking Follow-ups，观察后续 profile 再定

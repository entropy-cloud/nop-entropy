# nop-rg Glob 性能对齐 globset 设计

> 定位：规范文档（设计决策 + 使用契约）。只写最终结论与理由，不写实现代码、不写演进历史。
> 参照实现：`~/sources/globset-src/crates/globset/src/{lib.rs,glob.rs,pathutil.rs}`（ripgrep 仓库内 crate，globset 0.x）。
> 关联：`ai-dev/backlog/nop-rg-globset-parity-roadmap.md`（执行分解）、`ai-dev/backlog/nop-rg-roadmap.md`（Wave 5 item 20 已完成 GlobSet 策略分类首版）。

## 一、问题与瓶颈定位

nop-rg 的 GlobSet 首版（Wave 5 item 20）已做**编译期策略分类**：`*.ext` → 扩展名集、无通配符 → 字面量 basename/path 集、其余 → 两指针回溯匹配（`GlobMatch_strategy`）。实测微基准（2026-11-20，`GlobBenchmark`）：

| 基准 | 吞吐 |
| --- | --- |
| `singleGlob`（`*.java` 单模式） | ≈23.3k 次/s |
| `globSet`（3 条规则集 accept） | ≈1.65k 次/s |

对照 globset 的设计预期（注释中的百万级 path/s），差距 2–3 个数量级。**瓶颈不在策略分发，而在热路径分配**：

1. **每条 SLOW glob 每次匹配都重新切分路径**：`CompiledGlob.splitPath` 用 `path.split("/")`（分配 `String[]` + 每段 substring）加一个 `ArrayList`。基准规则集 3 条模式（2 include + 1 exclude）全落 SLOW，同一条路径被切分 **3 次**。
2. **basename 重复计算**：`matchesAny` 先算 `basename()`（`substring` 分配），SLOW 路径随后又各自切分，basename 结果被丢弃。
3. **`singleGlob` 自身也泄漏一次 `substring`**：这正是 23k/s 而非百万级的直接原因。
4. 次要：`matchComponents` 每个组件每次尝试都 `pc.equals("**")`（接口分发 + 字符串比较）；`components` 为 `List<String>`（装箱外的接口调用与边界检查）。

结论：**在不改变任何匹配语义的前提下，仅消除每次调用的重复分配，即可获得数倍提升**——这是第一阶段，且零语义风险。

## 二、设计决策

### 决策 1：Candidate 式路径预分解（不是"再把路径切一遍"）

globset 的核心高效点不是更快的匹配算法，而是**每条路径只准备一次**，所有策略共享（`lib.rs` `Candidate` / `is_match_candidate`）。nop-rg 采用同构做法：

- 一个路径上下文对象，**只持有 String 引用与 int 偏移**（basename 起点、各段边界偏移），零拷贝、零 substring；
- `accept()` 一次调用内，全部 include/exclude 策略共享同一上下文；
- 切片匹配改为 `(String, from, to)` 区间的索引运算，不产生中间 String。

理由：Java 的 `String.substring` 在 JDK 7+ 必分配新对象；索引运算是唯一能同时消除 split 与 substring 的手段。不使用 byte[]/MemorySegment——路径在 walker 侧是 `String`，逐路径转字节的成本高于索引匹配的收益。

### 决策 2：`**/` 前缀分解为 basename 策略

globset 的 `Glob.ext()` 允许前导 `RecursivePrefix`，因此 `**/*.rs` 仍归入扩展名策略；`**/foo` 归 basename。nop-rg 首版把 `**/*.java` 判为 SLOW，这是基准里最贵的一条。

设计：`classify()` 增加一条规则——`**/REST`（`REST` 不含 `/`）分解为 `REST` 的对应形态：
- `REST = *.ext` → 扩展名策略；
- `REST` 无通配符 → basename 字面量；
- 其余 → **basename 单段匹配**（现有的 `segmentMatches` 直接作用于 `Candidate.basename` 区间，无需任何切分）。

语义约束（必须由测试钉死）：`**/foo` 必须命中裸 `foo`（`**` 消耗零段）；空段跳过规则不变；尾部 `**` 要求至少一段的一贯语义不受影响。

### 决策 3：扩展名集用候选 ext 单次哈希查找

首版用 `endsWith` 线性循环。改为：每路径计算一次 ext（含前导 `.`，位于 basename 内），`HashSet<String>` 一次查找。语义等价前提：保留 "`*` 可匹配空串" 导致 basename 恰为 `.ext` 时也命中的行为。

### 决策 4：SLOW 模式的必需扩展名预滤

globset 的 RequiredExtension：慢速 glob 若尾部有字面量 `.ext`，仅当候选 ext 命中时才跑完整匹配器（`lib.rs`）。这是**必要非充分**条件，构造上不可能产生假阴性。

设计：`of()` 时提取 SLOW 模式的尾部字面量扩展名；`matchesAny` 先判 ext 再调 `matches()`。收益随 SLOW glob 与文件语料并存程度放大。

### 决策 5：编译期 token 元数据替代每次重解析

`CompiledGlob` 内部把 `List<String> components` 改为 `String[]` + `boolean[] isDoubleStar`（编译期一次性判定），热循环不再做字符串相等判断与接口分发。

### 决策 6：字面量长度位掩码

字面量 basename/path 集合在哈希查找前用长度位掩码（`1L << (len & 63)`）快速排除——globset 的 `lengths: u64` 思路。零风险、零分配。

## 三、明确不移植（低 ROI）

| globset 机制 | 不移植理由 |
| --- | --- |
| Aho-Corasick 前后缀匹配（`lib.rs`） | 仅在数百条字面量时胜出；rg 规模的 `-g` 数量（数十）在 Java 中用朴素循环更快。除非剖析显示前缀数量暴增，否则不做。 |
| regex-automata RegexSet / PatternSet / Pool | globset 的单模式快路径本质是一个 regex 引擎；在 Java 中 `java.util.regex` 慢于手写两指针匹配器，RegexSet 等价物工程量巨大而我们的策略分发已消除该需求。 |
| byte[]/arena + FNV 哈希 | 路径以 String 到达；逐路径转字节的成本高于索引匹配收益。Java `String.hashCode` 已缓存。 |
| `matches() -> Vec<usize>` / matches_all | nop-rg 只需要布尔 accept/exclude。 |
| ThreadLocal scratch 池化 | 先用 Candidate + 索引消除分配，再以 JMH 判定是否需要；预计不需要。 |

## 四、语义保真约束（不可协商）

以下为 plan 2263/2273 钉死的 rg 兼容语义，任何优化不得变更，逐条由既有测试 + 差分对拍守护：

- 不含 `/` 的模式隐式匹配 basename（`*.java` 命中任意深度）；
- `**` 作为独立段：中间 `**` 可消耗 0..n 段（`a/**/b` 命中 `a/b`）；尾部 `a/**` 要求至少一段；
- `?` 单字符、`[...]` 字符类（前导 `!`/`^` 反转等价）、`\` 转义；
- 空模式不匹配任何路径；`!` 前缀为排除规则，include 语义与优先级不变；
- `*` 可匹配空串（影响 `.ext` basename 与扩展名命中）。

**差分对拍契约**：优化后路径必须与"全 SLOW 参考实现"逐路径结果一致（随机规则集 × 随机路径 fuzz），这既是回归保护也是验收证据。

## 五、性能目标与度量

- 阶段一（决策 1+5）：`singleGlob` 与 `globSet` 均提升数倍（预期 ≥4×），**重测后再定后续阶段**；
- 阶段二（决策 2+3+4）：`globSet` 对基准规则集（`**/*.java`/`**/*.md`/`!**/File7*`）应提升 10× 以上；
- 度量口径：`GlobBenchmark` JMH（`-wi 2 -i 5`，`ops/ms`），每次改动前后配对跑，安静环境。

不设"追平 globset"的硬指标：globset 是 Rust 字节域零分配实现，Java/String 域存在模型差异；目标是**消除数量级差距中的可消除部分**，并把不可消除部分（字符串域开销）明确记录为模型成本。

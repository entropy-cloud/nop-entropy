# XLang 三后端执行基准报告（I12）——解释器 / java 生成类 / truffle × stock JVM

> Mission: xlang-execution-optimization / Work Item: I12（性能基准 + 全量三后端对拍收口）
> 载体：`nop-benchmark/nop-benchmark-xlang`（JMH 1.33 + main() runner，`nop-benchmark-xpl` 先例同构）
> 复跑入口：见 §复跑命令；Phase 1 全量对拍套件直跑证据并入 §七
> Q1/Q4 watch-only 量化触发口径专章：§八（Phase 3 落定）

## 一、环境

| 项 | 值 |
|---|---|
| 日期 | 2026-08-21 |
| OS / Arch | macOS, arm64（Apple Silicon） |
| JDK（运行时实测） | **Zulu 26.0.1（Azul Systems, build 26.30+11-CRaC-CA, 26.0.1+8）**——stock JVM 形态 |
| 其余在机 JDK | OpenJDK 25.0.1（Oracle）、Microsoft OpenJDK 17.0.17（未参与基准） |
| GraalVM 发行版 | **无**（live 核验 2026-08-21：无 graalvm JDK / `native-image` 不在 PATH / `GRAALVM_HOME` 未设；`/usr/libexec/java_home -V` 与 `~/.sdkman` 均无）——GraalVM 形态裁定见 §五 D3 |
| JMH | 1.33（`@Fork(1)` / `@Warmup(3×2s)` / `@Measurement(5×2s)`；gc 补充 run `2×1s + 3×1s -prof gc`） |
| 基准 JVM 日志 | 模块内 `logback.xml` ROOT=WARN（语料含 `debug()` 单元，逐执行 INFO 会淹没输出并放大 IO 噪声） |

**stock JVM 上 truffle 为解释执行稳态**（运行时引擎自证，原始输出在案）：

```
[engine] WARNING: The polyglot engine uses a fallback runtime that does not support runtime
compilation to native code. ... JVMCI is required to enable optimizations.
```

→ 本报告所有 truffle 数据行均为 **stock JVM 解释执行形态**；JIT 生效形态仅在 GraalVM JVM 上成立（§五 D3 裁定缺席 + 复跑入口）。

## 二、基准载体与语料裁定（D2 落地记录）

- **载体** = `nop-benchmark` 下新 JMH 模块 `nop-benchmark-xlang`（main() runner 接线；**JMH 冒烟裁定**：live JDK 26 起 javac 缺省禁用隐式注解处理（JDK 23+ 行为），JMH 1.33 注解处理器需模块 pom 显式 `annotationProcessorPaths` 接线方可产出 `META-INF/BenchmarkList`——非版本不兼容，**不升版钉线**，1.33 保持）。
- **静态语料（裁定一）** = e2e 模块 main classpath 生产形态单元全集的 xpl 子集：52 单元（Phase 1 物化 corpus 48 + e2e 原生 4），与对拍套件同一供给；**mode 口径同 D1**（全部生产形态 html 模式，5 个非 html 语义单元为 html 变体树——见 Phase 1 记录）。
- **耗时子集裁选（显式记录；套件侧仍全量 74 单元不受影响）**：排除 6 个异常单元（`exception-method` / `exception-prop` / `exception-convert` / `exception-fn-throw` / `exception-throw` / e2e `exception.xpl`——主路径抛异常，构造成本主导）与 3 个 xlib 标签单元（invoker 帧协议形态，非独立编译单元计时形态）→ **46 计时单元**；setup 期断言全部 52 xpl 键 ∈ 生产扫描清单（55 条目 = 52 xpl + 3 tags）。
- **依赖形态（裁定二）** = benchmark main 域经 e2e 模块（compile）消费生产形态闭环；动态语料 = 载体自带定义（10 单元：simple/full/template 三出口形态，预期值 setup 断言）；**零 test-jar 消费**。
- **三列身份核验（setup 期 fail-fast）**：java 列 = `XLang.parseXpl` 绑定 hook + `EvalStaticBoundExecutable` + 确定性生成类 FQN 断言 + `XLang.execute` choke point 直通；truffle 列 = 池租借求值 + 翻译 `XLangRootNode.sourceTree` 同一性断言（静态列）/ 路由裁决 `RouteKind.DYNAMIC` 断言（动态列经 choke point 真实出口 → truffle 后端池运行时）；解释器列 = 干净解析取树（非 bound 断言）+ 全局执行器直驱（**显式旁路**——静态语料为清单成员会被路由到 java 后端、动态语料会被路由到 truffle，旁路即 D2 裁定形态）。
- **梯度机制（分列）**：池大小经 `XLangContextPool.open(int)` 真实路径内参数化（`@Param 1/2/4/8/16`）；翻译缓存容量经 `TranslationCache(Integer)` 显式构造隔离基准（`@Param 16/64/256/1024/4096`，64 键固定工作集），未启用 in-situ 配置键形态（隔离形态已产出所需数据行）。
- **依赖方向**：`nop-benchmark-xlang → {nop-xlang-java-e2e, nop-xlang-truffle}` 单向；内核模块零新增依赖边（`org.graalvm.*` 仅经 truffle 模块既有依赖传递，不泄漏进内核其他模块）。

## 三、原始数据（stock JVM 全量 run，2026-08-21）

原始文件（repo 内）：
- 全量 run：`ai-dev/analysis/2026-08/2026-08-21-xlang-backend-benchmark-jmh-stock-full.txt`（含逐 iteration）
- gc 补充 run：`ai-dev/analysis/2026-08/2026-08-21-xlang-backend-benchmark-jmh-stock-gc.txt`
- JMH JSON：`ai-dev/analysis/2026-08/2026-08-21-xlang-backend-benchmark-jmh-stock-result.json`

### ① 静态单元三向对比（46 单元/批，avgt，µs/op 每批）

| 列 | 执行路径 | Score ± Error | 折算 µs/单元 |
|---|---|---|---|
| interpreter | 干净树直驱全局执行器（旁路） | 5.180 ± 0.130 | 0.113 |
| **java** | 生产绑定 + choke point 直通 | **2.842 ± 0.206** | **0.062** |
| truffle（stock 解释稳态） | 池租借 + 翻译 CallTarget | 52.959 ± 44.660 | 1.151 |

gc 补充 run（3 iter）：interpreter 5.352 µs / 17905 B/op；java 2.795 µs / 13369 B/op；truffle 44.637 µs / 49803 B/op。

**结论（机会成本量化，同单元三向）**：
- **java vs 解释器 = 1.82× 快**（2.842 vs 5.180；分配低 25%——13369 vs 17905 B/op）——静态资源走生成类直通在 stock JVM 上即有实质收益，**I11 §14(5) "e2e fixture 单元 java 列 vs 解释器列耗时对比" 就此闭合**。
- **truffle（stock）vs java = 18.6× 慢**、vs 解释器 = 10.2× 慢——stock JVM 上静态资源借道 truffle 无收益；"静态资源 JVM 形态生成物缺失不借道 truffle" 的决策依据数据行在案（决策树静态分支路由 java / 缺失时解释器兜底，不路由 truffle——正确性由生产语义保证，性能由本数据行支撑）。

### ② 动态单元对比（10 单元/批，avgt，µs/op 每批）

| 列 | 执行路径 | Score ± Error | 折算 µs/单元 |
|---|---|---|---|
| interpreter | 同树直驱（旁路） | 0.431 ± 0.027 | 0.043 |
| truffle（stock 解释稳态） | `XLang.execute` choke point → 决策树动态分支 → truffle 后端池运行时（稳态，翻译缓存命中） | 12.917 ± 16.647 | 1.292 |

gc 补充 run：interpreter 0.432 µs / 2952 B/op；truffle 9.168 µs / 12281 B/op。→ **stock JVM 上动态路径 truffle 慢 ~30×**（AST 解释执行 + handoff 开销）；truffle 动态列的价值载体 = GraalVM 形态 JIT（§五缺席裁定 + 复跑入口）。

### ③ Context 池创建/销毁成本 + 池大小敏感性（avgt）

| poolSize | open+close 全周期（µs/op） | 折算 µs/Context | lease→eval→return 单线程（µs/op） | 4 线程并发 lease（µs/op） |
|---|---|---|---|---|
| 1 | 2.816 ± 0.653 | 2.82 | 1.011 ± 0.581 | 5.618 ± 2.619 |
| 2 | 5.954 ± 0.953 | 2.98 | 1.027 ± 0.757 | 3.852 ± 2.031 |
| 4 | 11.720 ± 5.752 | 2.93 | 1.055 ± 0.693 | **2.457 ± 0.966** |
| 8 | 24.407 ± 8.143 | 3.05 | 1.053 ± 0.768 | 2.901 ± 0.795 |
| 16 | 50.570 ± 14.146 | 3.16 | 1.022 ± 0.471 | 2.697 ± 0.386 |

**结论**：Context 创建+预热+销毁 ≈ **2.8–3.2 µs/个**（线性，无规模异常）；租借求值稳态 ~1.0 µs 与池大小无关（单线程无争用）；4 线程并发下 poolSize < 4 时有界阻塞等待可见（5.6 → 2.5 µs，2.3× 改善），≥ 4 后饱和（8/16 无进一步收益，回落噪声带内）。

### ④ 翻译缓存容量敏感性（隔离基准，avgt）

| maxEntries | 64 键工作集扫（µs/op 每扫 64 键） | 形态 |
|---|---|---|
| 16 | 93.660 ± 176.054 | 全 miss + LRU 淘汰再翻译 |
| 64 | 2.864 ± 0.043 | 全命中 |
| 256 | 2.891 ± 0.040 | 全命中 |
| **1024（现缺省）** | **2.847 ± 0.017** | 全命中 |
| 4096 | 2.860 ± 0.236 | 全命中 |

翻译单成本（全 miss 直译，独立 65536 容量排除淘汰噪声）：**1.38–1.68 µs/单元**（各容量参数下一致——missCache 与 @Param 无关，数据行为设计预期）。

**结论**：命中查询 ≈ 44.6 ns/键（2.86 µs / 64）；再翻译代价 = 每淘汰单元 ~1.5 µs（miss 态扫 93.7 µs/64 键 ≈ 1.46 µs/键）；**容量 ≥ 工作集即无惩罚**（64→4096 全命中带宽内平坦）；容量 < 工作集时惩罚 ≈ 33×（93.7 vs 2.85 µs/扫）。Q4 前置条件数据行（翻译成本与缓存收益）见 §八。

## 四、Q1/Q4 锚点数据供给（用例⑤，可复算数据行）

- **Q1（Bytecode DSL 重评——"AST 解释开销占比"）**：stock 形态占比 = (truffle 解释稳态 − java 直编) / truffle 解释稳态 = (52.959 − 2.842) / 52.959 = **94.6%**（按批折算同口径 94.6%；gc run 同式 93.7%）。**该行按形态显式标注：stock JVM 上 truffle 全解释执行，占比平凡趋近 100%，不构成 Q1 数值判据基础**（判据仅锚定 GraalVM 形态数据行——§八）。
- **Q4（共享 Engine 重评前置——编译线程预算口径）**：翻译单成本 ~1.5 µs/单元 + 命中查询 44.6 ns/键（收益比 ≈ 34×/单元）；淘汰再翻译惩罚 33×/扫（③④数据行）——操作化数据在案（§八定口径）。

## 五、GraalVM JVM 形态裁定（D3——尝试获取后不可得，非"未尝试"）

- **获取尝试（ask-first，2026-08-21 执行中）**：已向用户提出安装请求（"请安装 GraalVM for JDK（建议 25.x LTS 线，arm64 macOS），安装后置 `GRAALVM_HOME` 或加入 `/usr/libexec/java_home` 可发现路径，即可复跑双形态基准"）——执行窗口内未获得安装/同意响应。
- **live 核验记录**：`/usr/libexec/java_home -V` 仅 Zulu 26.0.1 / OpenJDK 25.0.1；`~/.sdkman/candidates/java/` 仅 ms-17.0.17 / openjdk-25.0.1 / zulu-26.0.1；`native-image` 不在 PATH；`GRAALVM_HOME` 未设；`brew list` 无 graal 条目（与 I11 裁定 + plan 起草复核一致）。
- **裁定**：按 I11 先例显式裁定 GraalVM JVM 形态数据**缺席**（尝试后不可得——非未尝试、非静默降级）；truffle 列 JIT 生效形态数据行待环境可得后补齐，本报告所有 truffle 行均为 stock 解释执行形态（§一引擎自证文本 + 各表行标注）。
- **复跑入口（环境可得后）**：`GRAALVM_HOME=<graalvm-jdk> PATH=$GRAALVM_HOME/bin:$PATH` 后执行 §复跑命令 全量 run（同一载体与语料；truffle 行自动成为 JIT 生效形态——引擎 `WarnInterpreterOnly` 警告消失为形态自证）。

## 六、复跑命令（可复跑入口，repo-observable）

```bash
# 构建（reactor 内）
./mvnw clean install -DskipTests -pl :nop-benchmark-xlang -am -T 1C

# 全量默认运行（四组用例 + JSON 结果）
cd nop-benchmark/nop-benchmark-xlang
CP=$(../../mvnw dependency:build-classpath -Dmdep.outputFile=/tmp/bmcp.txt -q; cat /tmp/bmcp.txt):target/classes
java -cp "$CP" io.nop.benchmark.xlang.XlangBackendBenchmarks

# 选择性运行（JMH CLI 正则 + 参数覆盖；示例：gc profiler / 单组）
java -cp "$CP" io.nop.benchmark.xlang.XlangBackendBenchmarks StaticThreeWayBenchmark -prof gc
java -cp "$CP" io.nop.benchmark.xlang.XlangBackendBenchmarks ContextPoolBenchmark -p poolSize=4
```

（`./mvnw exec:java -pl :nop-benchmark-xlang -Dexec.mainClass=io.nop.benchmark.xlang.XlangBackendBenchmarks` 亦可；带 `-Dexec.args` 的参数透传受 shell 引号影响，建议 java -cp 形态。）

## 七、Phase 1 全量三后端对拍套件直跑证据（并入本报告）

- **命令（mission.json `test` 原样）**：`./mvnw test -pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle,:nop-xlang-java-e2e -am -T 1C`（2026-08-21，环境同 §一）。
- **结果**：BUILD SUCCESS，**1713/1713 全绿**（nop-xlang 551（2 skip 既有基线）+ nop-xlang-java 493 + nop-xlang-truffle 584 + nop-xlang-java-e2e 85）；I11 基线 1633 全保持 + 80 新增（聚合套件 79 + 反漂移 1），零断言削弱。
- **聚合套件计数**：74 单元三列直跑 = 48 静态物化单元 × 3 列 + 26 动态单元 × 2 列；入口 `io.nop.xlang.e2e.suite.TestFullCompareSuite`（e2e 模块）；同轮全家桶含既有矩阵（`TestExecTranslationCoverageMatrix`/`TestTruffleCoverageMatrix`）、路由（`TestJavaBackendRoutingScenarios`/`TestTruffleBackendRoutingScenarios`）、生产绑定（`TestProductionBindingRoutingScenarios`）、e2e（`TestEndToEndGeneratedBinding` 5/5）、并发（`TestCorpusConcurrentTruffleColumn` 75 用例）。
- **物化与适配事实**：55 manifest 条目（48 corpus + 7 e2e 原生）；gen task 48/48 生成零 fail-fast 零指纹失配；反漂移字节一致 48/48；5 个非 html 单元 html 变体预期值重声明（oracle = 解释器实测 + 三列交叉验证）；3 个 TAG_NONE collect 核验点实测返回值不变；loc 路径重映射落地；e2e 计数断言 7→55。
- **html 变体单元显式记录**（套件内 `E2eCorpusUnits.HTML_VARIANT_UNITS` / `COLLECT_VERIFY_UNITS` + `testHtmlVariantAndCollectVerifyUnitsRecorded`）：TAG_TEXT ×1 / TAG_XML ×2 / TAG_NODE ×2 为 html 变体树（TEXT 扁平化输出），TAG_NONE ×3 collect 返回值不变——原 per-unit mode 语义覆盖由既有列测试保持（全家桶同轮运行在案）。

## 八、Q1/Q4 watch-only 量化触发口径（Phase 3 落定）

> 状态：**watch-only 维持**（本节为触发条件的数值化定义，非重评实施）。设计文档冻结纪律
> （roadmap L42 + 设计 truffle 02 §九 L167 自述量化载体 = 本报告）——量化口径承载 =
> 本专章 + roadmap I12 条目回链，设计文档零改动，I5-I11 Follow-up 链就此闭合。

### 8.1 Q1 触发口径（Bytecode DSL 重评，设计 §九 Q1）

**重评触发 = 生态判据 ∨ 数值判据**（满足其一即触发重评评估，非直接实施）：

- **生态判据**（现状可查）：Oracle 官方 `bytecode_dsl` 生态成熟——具体口径 = bytecode_dsl
  发布稳定版（非 EA/sandbox）且与本模块 truffle 组件线（25.x LTS）版本兼容矩阵成立
  （`01-truffle-knowledge.md` §十既定生态跟踪面）。
- **数值判据**（形态锚定——**仅 GraalVM JVM 形态数据行**）：truffle 稳态 AST 解释开销占比
  超阈值。可复算公式（数据行 = 本报告 §三①同批语料三向数据）：

  ```
  AST 解释开销占比 = (truffle_steady_ns − java_direct_ns) / truffle_steady_ns
  ```

  其中 `truffle_steady_ns` = GraalVM 形态预热后稳态批耗时（JIT 生效 + 翻译缓存命中），
  `java_direct_ns` = 同语料 java 生成类直编列批耗时。**阈值 = 50%**（裁定依据：占比过半
  意味着 AST 解释开销超过语义本体，翻译器结构改写的收益空间大于迁移成本的分界线；
  阈值本身为重评触发口径而非实施承诺）。
- **数值判据当前状态：显式降级（推迟）**——D3 GraalVM 形态尝试后不可得（§五），无有效
  数据行可锚定。stock 形态占比 94.6%（§四）**平凡趋近 100%（全解释执行），不构成判据
  基础、禁止冒充**。判据维持"生态判据有效 + 数值判据待 GraalVM 数据补齐"状态，补齐
  入口 = §五复跑命令（环境可得后同一载体全量 run，§三①表自动获得 GraalVM 行）。

### 8.2 Q4 触发口径（与 nop-js 共享 Engine 重评，设计 §八/§九 Q4）

**重评前置条件（编译线程预算基准数据）操作化**——数据口径与当前读数：

| 口径项 | 数据定义 | 当前读数（stock 形态） | 数据行 |
|---|---|---|---|
| 翻译单成本 | `translateUnitCost`（全 miss 直译） | ~1.4–1.7 µs/单元 | §三④ |
| 缓存命中查询 | `cacheSweep64` 全命中态 / 64 键 | ~44.6 ns/键 | §三④ |
| 淘汰再翻译惩罚 | miss 态扫 / 命中态扫（容量<工作集） | 93.7 / 2.85 µs ≈ **33×** | §三④ |
| 首次翻译总占比上界 | 翻译成本 / 稳态执行成本（单单元粒度） | ~1.5 µs vs 0.06–1.3 µs 执行——单次翻译 ≈ 1–25 次执行 | §三①②④ |

**触发口径 = 编译线程成为吞吐瓶颈的量化信号**：当部署形态满足
「动态语料基数 × 重翻译频率 × 翻译单成本」构成的翻译吞吐需求逼近单线程预算
（即 `翻译需求吞吐 ≥ 1/1.5µs ≈ 67 万单元/秒/线程` 的量级），或淘汰惩罚 33× 在实测
生产负载中可观测（缓存容量 < 工作集且命中率 < 95%），共享 Engine 统一编译线程预算的
收益论证才具备数据基础。**当前读数结论：I12 基准域内翻译成本为 µs 量级、缓存收益 34×/单元，
远未构成编译线程瓶颈信号——Q4 维持不共享（独立 Engine）裁定，重评前置条件未触发。**
（跨语言互操作需求仍为另一触发面——设计 §八既定，无变化。）

### 8.3 池/缓存缺省值裁定（数据支撑，I8 移交收口）

| 配置 | 缺省 | 裁定 | 数据依据（§三③④数据行） |
|---|---|---|---|
| `nop.xlang.truffle.context-pool.max-size` | `availableProcessors` | **保持** | 4 线程并发在 poolSize≥4 饱和（2.46 µs，8/16 无进一步收益）；租借稳态 ~1.0 µs 与池大小无关；创建成本 2.8–3.2 µs/Context 线性——`availableProcessors` 与工作线程规模对齐即落在饱和区，无数据支撑其它取值更优 |
| `nop.xlang.truffle.translation-cache.max-entries` | `1024` | **保持** | 容量 ≥ 工作集（64 键）时 64→4096 平坦（~2.85 µs/扫，零惩罚）；容量 < 工作集惩罚 33×——1024 对 corpus 规模工作集（74 单元）有 >13× 余量；抬升容量零收益（平坦）、压缩容量有惩罚风险，保持 |

（裁定不改配置代码——`XLangTruffleConfigs` 缺省即上述值；如后续生产负载数据触发调整，
须经同口径基准数据支撑 + 全量回归。）


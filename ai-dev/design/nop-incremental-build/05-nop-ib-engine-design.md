# nop-ib 增量编译与测试引擎 — Architecture Baseline

> 本文是 [`01-architecture-baseline.md`](01-architecture-baseline.md) 的落地细化：把"基于 javac 自行实现增量引擎"的**形态、事实来源、两级依赖分析、命令面、状态与正确性门**写成可实施的契约。
> 不改写 `00`–`04` 的裁定；与之冲突处以 `01` 的不变量为准，并在本文对应条目注明。
> 实现计划与分期见 `ai-dev/backlog/nop-ib-roadmap.md`（时效性文档，不复制其状态到本文）。

## 一、设计结论

| 编号 | 决策 | 理由（摘要） |
|---|---|---|
| IB-D1 | `nop-ib` 是**独立轻量可执行体**（一次性 CLI + `serve` 常驻模式），不是 Maven 扩展、不塞进 `nop-cli`（Quarkus） | Maven 扩展仍要为每次调用付骨架成本；Quarkus 入口的启动与依赖面与"亚秒级反馈"目标相悖 |
| IB-D2 | 依赖分析**两级**：Maven reactor 图只做模块粗筛，字节码类图做模块内与测试细选 | 模块图砍不掉模块内改动；类图砍不掉跨模块。二者缺一必漏或必慢 |
| IB-D3 | 构建事实**只从 Maven 自身导出**（一次性 `model.json`），不手写 pom 解析器 | effective POM 的 profile/继承/`dependencyManagement`/属性插值只能由 Maven 解释；手写解析必然漂移成假绿 |
| IB-D4 | 编译与测试的传播**方向相同、判据不同**：编译只在 ABI/签名变化时跨模块扩散；测试在**任何行为变化（含方法体）**时扩散 | 二进制兼容可以跳过重编，但不能跳过行为回归 |
| IB-D5 | 编译底座复用 `nop-kernel/nop-javac` 的文件式编译；固定 `-implicit:none`、按 execution 取 `--release`；产物直接写模块 `<module>/target/classes` 与 `<module>/target/test-classes` | 避免第二套诊断语义；避免隐式产出覆盖生成物（G10/§12.2 已裁定） |
| IB-D6 | 字节码底座复用 `nop-tools/nop-bytecode`（ASM） | 仓内已有零 nop 运行时依赖的 class 文件采集与 ASM 栈，不新增第二套字节码技术栈 |
| IB-D7 | classpath 只用 **reactor 目录**（`<module>/target/classes`、`<module>/target/test-classes`）与外部 jar；**禁止**任何 intra-project jar 条目 | intra-project jar 是冻结快照，会把增量结论锁死在旧产物（I1/§11.3） |
| IB-D8 | 测试执行**两阶段**：先委托 Maven Surefire（语义保真），再自研 JUnit Platform 启动器（去骨架成本） | 首版最大的风险是复刻错 Surefire 的 cwd/argLine/系统属性语义；先委托可在不引入假绿的前提下拿到编译收益 |
| IB-D9 | 与 Maven 共用 `.mvn/build.lock`（mvnq 同一把锁）：**写产物取排他锁**，只读命令不取锁 | 同一 worktree 内 `nop-ib` 与 `mvn` 并发写 `target/` 必然互相破坏 |
| IB-D10 | 回退是**显式状态**而不是异常：模块能力分级 L0–L3，回退原因写入状态与 JSON；`partial` / `wideImpact` 必须显式 | 静默回退或静默截断都会产生假绿；调用方必须能编程决策 |
| IB-D11 | 动态覆盖（JaCoCo 等）只能做**收窄的减法**，且映射必须与当前版本一致；缺失/过期即回退静态图 | 动态覆盖会漏掉反射/DI/数据驱动路径，单独使用不安全 |
| IB-D12 | 命令面固定为 `status/plan/compile/test/check/explain/model/serve/cache/bench`，统一 `--json` 与固定退出码 | AI 需要"先看 plan、再执行、可解释"的稳定协议，而不是自由拼接 `mvn` 参数 |

## 二、范围与非目标

**替换范围**：AI 辅助开发过程中反复调用的 `mvn build` / `mvn test` 的**日常反馈回路**。

**不替换**：

- CI、发布、`mvn install`/`deploy`、镜像与集成打包；
- 代码生成、AOP 生成、全量冷构建（仍由 `./mvnw` 承担）；
- IDE 编译与实时诊断（归 `nop-lint`/IDE）；
- 任何"快路径产物直接进入发布链"的用法。

**非目标**：跨语言构建（Node/pnpm）、远程构建缓存服务、Java 以外的增量编译、替代 `nop-code` 的符号索引。

## 三、系统形态与分层

```
调用方（AI agent / 开发者）
  ├─ 一次性 CLI：nop-ib <command>
  └─ 常驻：nop-ib serve（stdin/stdout JSONL）
        │
   ┌────▼────────────────────────────────────────────┐
   │ 请求层：参数校验 / 批量变更集 / 去抖 / 超时       │
   ├─────────────────────────────────────────────────┤
   │ 范围层：模块图剪裁 → 类图闭包 → 回退判定          │
   │         （IRebuildScopeAnalyzer）                │
   ├─────────────────────────────────────────────────┤
   │ 执行层：编译调度（nop-javac） / 测试调度（Surefire│
   │         委托或 JUnit Platform）                   │
   ├─────────────────────────────────────────────────┤
   │ 状态层：model.json / 增量单元 / 哈希缓存 / 对拍   │
   └─────────────────────────────────────────────────┘
        │ 冷路径（按需委托）
   ./mvnw（经 mvnq 取 .mvn/build.lock）
```

要点：

- 一次性 CLI 与 `serve` 共享同一内核与状态目录；`serve` 只是去掉进程启动与模型/索引装载。
- 冷路径委托必须是**显式动作**（`--on-fallback=maven`），且必须走 `mvnq`/`build.lock`，不得绕开既有并发治理。

## 四、模块归属与依赖方向（待裁 IB-G1）

推荐落位 `nop-tools` 下的 `nop-ib`（与 `nop-bytecode` 同组：开发期工具、零 DB、零 IoC）。

依赖方向（单向）：

```
nop-ib → nop-javac（编译能力）
nop-ib → nop-bytecode（class 文件采集与 ASM）
nop-ib → JDK（javax.tools / JUnit Platform，测试执行阶段）
```

不变式：

- **不新增 `-api` 模块**，对外契约保持内部接口 + CLI/JSON；
- **不得依赖 `nop-code-service`**（26 依赖 + IoC + ORM），理由同 `01 §四`；
- 不得进入任何发布型模块的运行时 classpath；
- 若最终裁定落位其它模块组，上述依赖方向不变。

## 五、构建事实模型（`model.json`）

### 5.1 来源

由 Maven 自身一次性导出（候选形态：开发期小 mojo / extension 或 `mvn` 的 effective-pom 批量 dump；最终形态由 IB-G2 裁定）。**禁止** `nop-ib` 直接解析 `pom.xml`。

### 5.2 必需事实

| 类别 | 内容 |
|---|---|
| 模块 | 坐标、basedir、packaging、parent、`<modules>`、激活 profile |
| 依赖边 | 目标模块/外部坐标、scope、type（含 `test-jar`）、classifier |
| 编译 | `sourceDirectory`/`testSourceDirectory`、resources、`outputDirectory`/`testOutputDirectory`、每个 compiler execution 的 `release`/`proc`/`compilerArgs`/`annotationProcessorPaths` |
| 插件 | `exec-maven-plugin`（codegen/AOP 的 execution id）、`jandex`、`build-helper`、shaded 类插件 |
| 测试 | Surefire 配置（`argLine`、系统属性含 `<property>` name/value 形态、includes/excludes、`groups`/`excludedGroups` tag 面、`environmentVariables`、`additionalClasspathElements`（仅 repo 外条目入 fork classpath）、非默认 `workingDirectory`/`additionalClasspathDependencies` → 记录为不支持事实并回退 surefire；fork/reuse 语义不消费，fork 隔离严格强于 surefire=documented-nonequivalence，wave6 6A-2） |
| 工具链 | JDK/toolchain、编码、Maven 版本 |

### 5.3 刷新与失效

- `model.json` 带内容哈希与 Maven/JDK 版本；JDK 版本变化在每次 staleness 检查中比对（wave6 6A-1 F8）；Maven 版本与 dumpSha256 只在显式 `model refresh`/`cache verify` 时重验（per-command 子进程成本不可接受）；
- 任一 `pom.xml`/profile/属性变化、模块增删、source root 变化 → 标记过期。磁盘上存在但不属于 reactor 的 pom（profile-gated/aggregator）不构成过期（wave6 修订：假阳性族）；新增模块以 plan 时"reactor 外源码变更 → 强制 Maven fallback"覆盖（ScopePlanner）；
- 过期状态下的**任何**快路径结论无效，必须先刷新模型（或显式回退冷路径）；
- 模型不得与 `01 §12.4` 的构建侧增量状态混写。

## 六、两级依赖分析

### 6.1 第一级：Maven reactor 图（粗筛）

- 候选**编译**模块 = 变更模块 ∪ 其**反向依赖闭包**（谁依赖我）；
- 候选**测试**模块 = 同上 ∪ 变更模块自身；
- pom 变更：沿 parent→child 与依赖边扩散（父 pom 改变编译/测试参数）；
- 边必须包含 `compile/provided/runtime/test/system` 与 `test-jar`；
- 模型缺失/不完整 → **不剪裁**（全仓候选），不得"尽力而为"。

模块图只回答"哪些模块可能有份"，不回答"模块内哪些文件/测试"。

### 6.2 第二级：字节码类图（细选）

每个模块维护增量单元状态：

| 数据 | 语义 |
|---|---|
| `class → contentHash` | 类内容是否变化（免疫"文件集合闸门"） |
| `class → dependents(private/accessible)` | 反向引用闭包 |
| `class → ABI digest` | 可访问面（含 package-private）指纹，判定跨模块是否需重编 |
| `class → inlineConstantHashes` | `static final` 常量（名字+值），常量变化 → 保守扩散 |
| `sourcePath → classes` | 编译器 source→class 映射；缺失时按文件名推导 |
| `testClass → referencedClasses` | 测试选择 |
| `annotationProcessing / generated` | 仅 L0 可省；L1/L2/L3 参与回退判定 |

索引来源与刷新：

- 已存在产物：直接读 `<module>/target/classes`、`<module>/target/test-classes` 的 class 文件；
- 本轮新编：编译完成后重扫受影响产物；
- 读取/解析失败：该模块 `indexUnhealthy` → 回退，**不得降级为部分索引**。

### 6.3 编译作用域（语义契约）

```
changed   := { c | contentHash(c) 变化 }            // 删除 = 出现在旧快照、不在新快照
abiChanged := { c ∈ changed | ABI digest 变化
                             ∨ module-info 变化
                             ∨ source-retention 注解变化
                             ∨ inlineConstant 变化 }
compileSet(M) := changed(M)
             ∪ (abiChanged(M) 的反向闭包 ∩ M)
             ∪ (跨模块：下游模块中直接引用 abiChanged 的源文件)
```

约束：

- 一个源文件含多个类时，同文件其余类必须一并纳入删除/重编（对应 Groovy/Spock 类错误的历史教训；`01 §九` 的"独立类"规则）；
- 删除类必须按其旧记录反查源文件并清理 `.class`/`.h`，不得只按当前索引；
- `abiChanged` 为空时，下游模块**不重编**；
- 任何回退信号（见 §6.5）优先于上述计算。

### 6.4 测试选择（语义契约）

```
affectedTests(M) := { t ∈ tests(M) | closure(t) ∩ changed(MainClasses) ≠ ∅ }
                    ∪ { t | t 消费的测试数据/快照/资源命中规则 }
```

- `closure(t)` 沿**正向**引用边传播（等价于 `changed` 的反向闭包 ∩ 测试类）；
- **方法体变化也计入 `changed`**（行为变化必须回归）；
- 抽象基类、`@Test` 继承、参数化/动态测试：按"整类全跑"处理；
- `test-jar` 依赖边参与跨模块传播；
- 编译回退到 Maven 后，测试选择集必须**重新判定**，不得沿用回退前结果（`01 §十三`）。

### 6.5 放宽矩阵（宁可多跑，不可漏跑）

| 信号 | 处置 |
|---|---|
| 资源/配置（`bootstrap.yaml`、beans、服务注册、properties、日志） | 该模块全量测试 |
| XLang 模型 / codegen 模板 / `_gen/` | 回退 Maven；反查消费方 → 全量测试 |
| L2 注解处理器（`annotationProcessorPaths` / 显式 `proc`） | 该模块回退 Maven |
| L3 AOP / 特殊 `argLine` / `skipTests` | 恒回退 |
| 测试数据/语料/期望文件 | 按规则反查消费方 → 全量测试 |
| DI/反射/ServiceLoader/序列化/映射类 | 该模块或消费方全量测试（首版保守） |
| 索引/模型不健康、classpath 不完整、分析异常 | 不剪裁，全量 |
| 波及面过大（如核心模块） | 如实报告 `wideImpact`；截断须 `--allow-partial` 且 `partial=true` |

### 6.6 动态覆盖的定位

动态覆盖（`test → 实际覆盖类`）只允许在**版本一致**时用于收窄静态上界；缺失、过期、或测试使用了反射/DI/数据驱动 → 以静态图为准。禁止用动态覆盖单独决定"不跑"。

## 七、执行模型

### 7.1 编译执行

- 输入：受影响源文件集合 + 已物化 classpath + 每 execution 的编译器选项；
- 引擎：`nop-javac` 文件式编译（`-implicit:none`、`-proc:none` 默认、`--release` 按 execution）；
- 输出：模块 `<module>/target/classes` / `<module>/target/test-classes`（测试编译使用**定序** classpath：测试输出 → 主输出 → 依赖，禁止跨模块合并）；
- 删除策略：先删除本轮将被重写/淘汰的类与资源，再编译；编译失败 = **fail-loud**（结构化诊断 + 模块判败，exit≠0），无事务化回滚（wave8 审计裁定：Gradle 式 stash/restore 在"零假绿+不替换构建系统"约束下不成立——失败后冷路径 mvn 全量重建即权威恢复，比自实现事务更可信；Gradle 经验裁定详见 analysis wave8-mvnd-six-dimension-comparison）；
- 拓扑：按 reactor 依赖序执行；同层可并行，输出目录互斥。

### 7.2 测试执行

阶段一（首版）：`mvn -pl <modules> surefire:test -Dtest=<classes>` 委托执行，保真 Surefire 语义；`cwd = 模块 basedir`、`classpath 定序`、`argLine/系统属性` 从 `model.json` 对齐。

阶段二（去骨架）：自有 JUnit Platform 启动器，逐模块 fork；契约继承 [`02-test-execution.md`](02-test-execution.md)：cwd 硬约束、classpath 定序、禁止跨模块合并、快照录制禁用、三层超时。切换前必须有与阶段一的对照测试（同语料结果一致）。

### 7.3 并发与锁

- 写产物（fast-path compile）/ Maven 委托 → `.mvn/build.lock` 排他锁（与 `mvnq` 同一文件、同一语义；Maven 委托不外层嵌套取锁——mvnq 自己取同一把锁，嵌套即自死锁）；
- junit-fork 测试路径不取锁（wave6 6A-5 B-1 裁定：只读 target/classes、写每进程独立临时文件，非共享资源写者；取锁会串行化并行模块 sweep。compile-vs-test 跨进程竞争只可能产生假红，方向 fail-safe）；
- 只读（`status/plan/model show`）→ 不取锁，但必须容忍并报告"检测到 Maven 构建持有锁"；
- 超时与 `mvnq` 对齐（默认 3600s，`75` 退出码）；
- 进程内并发：模块级串行 + 模块间有界并行；编译执行器实例按构建侧做法池化（编译器/file manager 非线程安全）。

## 八、状态、缓存与失效

状态目录（每 worktree）：

| 路径 | 内容 | 失效条件 |
|---|---|---|
| `.nop-ib/model.json` | 构建事实（§5） | Maven/JDK/pom/profile 变化 |
| `.nop-ib/units/<module>.bin` | 类图、ABI、常量、source→class、测试引用、生成指纹 | 模块重编/产物被外部改写 |
| `.nop-ib/inputs.bin` | 文件 stat/内容哈希缓存 | 默认按 (size,mtime) 复用；不可信时重算 |
| `.nop-ib/last-check.json` | 上次成功状态（用于 `--changed` 默认基线） | 失败不推进基线 |
| `.nop-ib/test-cache.json` | opt-in `--cache` 的模块级绿结果缓存（wave9） | 指纹不命中即视为过期 |

### 测试结果缓存契约（opt-in `--cache`）

- **只缓存绿**：`junit-fork` 执行且 exit=0 才写入；红结果写入即作废既有绿条目（预存红天然每次真跑）。
- **命中条件**：模块指纹逐字节一致。指纹 = 模块单元索引全部 (class, contentHash, abiDigest) + 传递依赖模块的 ABI digest 表 + externalArtifacts + surefire 全部 facts（argLine/skip/includes/excludes/groups/excludedGroups/系统属性/env）。任一成分缺失（索引缺失、模型过期、依赖模块无索引）→ 指纹 null → **不命中**（fail-safe，不静默跳过）。
- **显式性**：命中输出 kind=`cached-up-to-date` + JSON `cached:true` + advisory warning（点名 `--force` 逃逸）；不冒充执行结果。
- **不可见输入披露**：外部环境（DB/容器/host 状态、环境变量）不在指纹内——cached 绿的环境失效由 `--force` 兜底；依赖模块无索引时其 ABI 变化不入指纹（reactor 内依赖正常有索引；外部 jar 由 externalArtifacts 兜底）。
- 适用面：`-pl` 显式模块与 plan scope 分支；`--class`/`--rerun-failed`（显式执行请求）与 Maven fallback 路径永不缓存。

跨 worktree 共享（用户级）：jar 的 ABI digest / 内容哈希缓存，键为内容哈希，路径无关。

失效与对拍：

- `clean`、模块产物缺失、`target/` 被外部改写、模型过期 → 状态失效；
- **构建期写入**（codegen/AOP/测试写工作树）必须与用户写入区分，否则回退循环；
- `cache verify` 抽样用干净 `mvn compile` 对拍：类集合 + 逐类 ABI digest（可选字节对比）；差异 → 状态失效 + 报告。

## 九、命令面与反馈契约

| 命令 | 语义 | 关键参数 |
|---|---|---|
| `nop-ib status` | 脏模块/脏文件、模型/索引健康、锁状态 | `--json` |
| `nop-ib plan` | 只算不做：编译集、测试集、回退原因 | `--paths`、`--since`、`--json` |
| `nop-ib compile` | 选择性编译 | `--paths`、`-pl`、`--changed`、`--all`、`--on-fallback=maven\|fail` |
| `nop-ib test` | 选择并执行测试 | `--affected`、`--class`、`-pl`、`--rerun-failed` |
| `nop-ib check` | 编译 + 受影响测试（AI 主入口） | `--paths`、`--json`、`--on-fallback` |
| `nop-ib explain` | 解释某个文件/类的依赖者、选中原因、回退原因 | `<file\|class>` |
| `nop-ib model refresh\|show` | 重建/查看 `model.json` | `--json` |
| `nop-ib serve` | 常驻 JSONL 协议 | `--stdio` |
| `nop-ib cache verify\|prune\|size` | 对拍/清理/占用 | — |
| `nop-ib clean` | 快路径产物失效（mvn-clean 同义面）：删选中模块 target 输出目录 + 单元索引 + 测试缓存条目；取 `.mvn/build.lock`；模型过期显式拒绝（禁部分清理） | `-pl`、`--json` |
| `nop-ib bench` | 成本基线（骨架/编译/测试三段） | `--out` |

反馈契约（`--json`）：

```json
{
  "ok": true,
  "fallback": {"required": false, "reason": null},
  "scope": {"changed": [], "compileModules": [], "compileFiles": 0,
            "abiChanged": false, "testModules": [], "testClasses": 0,
            "wideImpact": false, "partial": false},
  "compile": {"modules": [], "diagnostics": []},
  "tests": {"selected": 0, "run": 0, "passed": 0, "failed": 0,
            "skipped": 0, "timeouts": 0, "failures": []},
  "timings": {"totalMs": 0},
  "state": {"modelAge": "", "cache": "ok"}
}
```

退出码：`0` 成功；`1` 编译或测试失败；`2` 需要回退但被 `--on-fallback=fail` 拒绝；`3` 用法/配置错误；`6` 结果不完整（`partial=true`）；`75` 锁等待超时。

`serve` 协议：每行一个请求/事件；事件至少包含 `plan`、`compile`、`test`、`final`；崩溃后重启必须能恢复状态（状态在磁盘，不依赖进程内存）。

## 十、正确性门

与 `01 §十` 的映射：

| 不变量 | 本引擎的强制点 |
|---|---|
| I1 跨模块污染 | ABI digest 判定 + 反向闭包；禁止 intra-project jar |
| I2 `clean` 失效 | 产物缺失/模型过期即状态失效 |
| I3 AOP 产物 | L3 模块恒回退；生成指纹参与失效 |
| I4 删除残留 | 旧索引反查 + 显式删除；不得只依据当前索引 |
| I5 数据/模型驱动测试 | 放宽矩阵优先于选择集 |
| I6 索引召回无上界 | 索引不健康 → 全量，不返回部分结论 |

额外硬门：

1. 未执行的测试不得计为通过；`partial` / `wideImpact` 必须同时出现在人类输出与 JSON。
2. 任何回退/部分/宽影响都必须携带**原因**并可通过 `explain`/`status` 复查。
3. `--max-tests` 默认拒绝；显式 `--allow-partial` 才截断，且退出码 `6`。
4. 模型过期、索引异常、锁冲突、外部改写产物 → 一律不得给出"成功"结论。
5. 对拍差异未解释前，该模块不得继续使用快路径。

## 十一、性能目标与测量

指标定义（基线必须在目标机器实测，禁止引用他机数字）：

- `noop`：无变更 `status/check` 的 p50/p95；
- `singleMethod`：单文件方法体改动 → 编译完成的 p50/p95；
- `singleAbi`：单文件签名改动 → 下游编译闭包完成的 p50/p95；
- `affectedTests`：测试选择耗时与测试执行耗时分别统计；
- `regression`：同等编辑集合下与 `mvnw` 的端到端对比。

优化顺序：**不启动 Maven** > **不编译** > **少编译** > **编译更快**。任何优化不得以放宽 §6.5/§十 为代价。

## 十二、拒绝了什么

| 方案 | 拒绝理由 |
|---|---|
| 写 Maven 扩展/生命周期钩子 | 每次调用仍付 Maven 骨架成本；且扩展只能影响 Maven 自身执行，不能提供 plan/JSON/测试选择协议 |
| `mvn -pl ... -am` 作为日常快路径 | 骨架成本与并发放大仍在（`00 §2` 实测）；无法做模块内/测试类级收窄 |
| 手写 `pom.xml` 解析器 | effective POM 语义不可靠；漂移 = 假绿 |
| 只用源码符号索引（`nop-code`） | 无方法级引用索引，无法支撑测试选择；字节码常量池是更稳的上界 |
| 只用动态覆盖做测试选择 | 反射/DI/数据驱动会漏；只能做收窄减法 |
| 一步到位自研 JUnit Platform 启动器 | 复刻 Surefire 语义（cwd/argLine/系统属性/reuse fork）风险高；先委托可先拿到编译收益 |
| 把 `nop-ib` 并入 `nop-cli` | Quarkus 启动与依赖面过大，违背亚秒级目标 |
| 每次重新哈希全部 classpath | 465 个 pom 规模下成本不可接受；必须用 (size,mtime)+内容哈希的两级缓存 |
| 用 intra-project jar 作为编译 classpath | 冻结快照，天然产生陈旧产物（Maven 自身都会告警） |

## 十三、与既有子系统的关系

- **`nop-javac`**：唯一编译能力归属方；本引擎只做选项装配、作用域与调度。
- **`nop-bytecode`**：class 文件采集与 ASM 底座；不引入第二套字节码栈。
- **`nop-code`**：可选的源码解析来源（签名 diff/文件分类）；不依赖其服务层。
- **`nop-lint`**：互补；本引擎不做实时诊断。
- **`mvnq` / `.mvn/build.lock`**：并发治理的唯一入口；本引擎写产物必须共用。
- **`00`–`04`**：`05` 细化其 12.x/§九/§十三；若冲突，以 `01` 的不变量为准。

## 十四、必须由人裁定的决策点

| 编号 | 决策 | 推荐 | 阻塞范围 |
|---|---|---|---|
| IB-G1 | 模块落位与命名（`nop-tools` 下的 `nop-ib` vs 其它开发期工具组） | `nop-tools` 下的 `nop-ib` | Wave 1 开工 |
| IB-G2 | 构建事实导出形态（开发期 mojo/extension vs effective-pom 批量 dump） | 小 mojo/extension，一次 reactor 导出 | Wave 1 item 6 |
| IB-G3 | 测试执行路线与切换门 | 先委托 Surefire；自研启动器须对照通过后切换 | Wave 3 / Wave 4 |
| IB-G4 | 动态覆盖是否进入 v1（还是仅作为后续收窄） | v1 不做，静态优先 | Wave 3 验收 |
| IB-G5 | 是否允许 `nop-ib` 直接依赖 `nop-bytecode`（含 ASM） | 允许（同组工具依赖） | Wave 1 item 7 |
| IB-G6 | 波及面过大时的默认策略（全量 vs 显式 partial） | 默认全量；partial 必须显式 | 命令面冻结前 |

## wave7 性能实验结论（2026-11-16，live baseline）

- bench harness：`nop-tools/nop-ib/tools/bench-e2e.sh`（五项目基线：小测试重模块 ib 1.6–5.5x，zeebe ~30s 模块 setup 主导无优势，spring-batch 大模块失败路径 0.39x——rerun/重试族为杠杆，见 analysis bench-baseline/experiment-verdicts）。
- 实验开关（默认关闭）：`nop.ib.moduleRerunMode=failed-only`（E1）、`nop.ib.moduleSlots`（E4 对照钮）、`nop.ib.rerunBatchSize`（E5）。E2/E3/E6 无新代码（数据裁定：无 ≥20% 空间/目标开销 <1%/已被单元索引覆盖）。
- follow-up 登记：exit-2 重试"仅补缺失类"语义（失败大模块 wall −67% 候选）。

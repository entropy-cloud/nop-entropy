# Nop Lint — 性能与功能平衡：执行档位（Profiles）

> 日期: 2026-09-19（修订 2026-09-20）· 状态: 设计草案（索引见 [00-nop-lint-design.md](./00-nop-lint-design.md)）
> **定位**：本文档是性能决策的**唯一权威来源**。03 §1.3、06 §5.6、06 §6.6 的性能表述均以本文为准；其他文档只引用不另立数字。
> 设计原则：**一条执行管线 + 惰性挂接的分析器**，不是多个引擎拼凑。功能的丰富度通过"档位 + 预算 + 降级阶梯"协调，而不是把所有分析器永远全开。

## 1. 核心矛盾与统一解法

| 昂贵能力 | 代价 | 全开的后果 | 协调方式 |
|---------|------|-----------|---------|
| L2 Java symbol solver | solver 初始化秒级 + 每文件二次解析 | 编辑器无法实时 | 惰性 + 项目级缓存 + 仅 deep/standard |
| tsc bridge | `ts.createProgram` 秒级 + 常驻进程 | 单文件检查 >10s | 进程复用 + program 缓存 + watch 模式 + 失效降级 |
| 数据流/Scope（Phase 3） | 方法级图构建 | 大仓库分钟级 | 仅 deep 档；规则声明 `requires:` |
| xscript | 每 match 解释执行 | 规则雪崩时放大 | 排在约束之后 + deadline（07 §3/§4） |

**解法三要素**：
1. **规则声明依赖**：规则（经 10 xdef）携带 `requires: [L2, dataflow, scope, tsc...]`，引擎按档位聚合判断可运行性，跳过的规则计入 `skippedByProfile` 统计（绝不静默丢失）
2. **固定管线顺序**（便宜先行，任一环失败即短路）：kind 位过滤 → pattern 匹配 → 约束（Phase 2）→ xscript → 分析器按需查询 → 抑制判定（09）
3. **预算与降级阶梯**：每文件软时限，超时按"价值/成本"倒序逐级关闭分析器并标记结果为 `degraded`

## 2. 执行档位定义

| 档位 | 场景 | 启用能力 | 单文件预算（软） | 典型延迟 |
|------|------|---------|----------------|---------|
| **fast** | 编辑器实时 / pre-commit | kind + pattern + 单层 any + L1 声明类型 + 抑制判定全量（内联注释 + @SuppressWarnings，09 v1）；xscript 全部可运行但 deadline 收紧为 20ms/match（07 §3，非规则过滤） | 20ms | <50ms |
| **standard** | CI 默认 | + 约束/关系/复合规则 + 全部 xscript + fix 生成 + L2（Java symbol solver / tsc，惰性+缓存）+ baseline + exemptions（09 v2） | 500ms | 全仓库分钟级（并行） |
| **deep** | 夜间全量 / 深度审计 | + 数据流 + 常量传播 + Scope + Metrics + 全部 L3/L4 | 5s | 小时级可接受 |

- fast 档跳过 JavaParser/tsc：L2 相关规则自动跳过（`skippedByProfile`），**不降级模拟**（不拿 L1 冒充 L2 结果，避免真假阳性漂移）
- 档位是**运行参数**而非规则属性；同一规则库三种档位共用，行为差异只来自分析器可用性

**L2 供给注入位（落地增注，2026-09-22，roadmap item 20 Phase 2，live 以源码为准）**：profile 的能力集是**上限声明**而非运行承诺——`fast` 上限只含 L1；`standard` 上限声明 L2，运行时引擎另需 resolver 就绪（`TypeResolver` 注入 + 其无副作用环境探测通过 + 运行对象具名文件路径）。上限内、环境不就绪的规则走**降级**出口（`LintStats.rulesDegraded` + `degradedRuleIds` + 警告日志），上限外的规则维持 `skippedByProfile`。降级对规则是整条粒度：门控期降级（规则不编译、不执行）与求值期降级（typeOf 查询中途失败，已匹配结果全弃）共用同一计数；两种降级都绝不产出诊断，绝不以 L1 结果顶替。

**deep 档与 capability 词表（落地增注，2026-09-24，roadmap item 31 Phase 1，live 以源码为准）**：`deep` 档位落地（`LintProfile.DEEP`），能力上限 = {L1, L2, L3, L4, SCOPE, METRICS}；单文件软预算 fast 20ms / standard 500ms / deep 5s 以 profile 常量承载（`fileBudgetMs()`）。**capability 词表裁定**：deep 专属分析器采用 design 06 §4.6 分层名 `L3`（数据流）/`L4`（语义分析）+ 独立 `SCOPE`/`METRICS` token；本节 §1 示例中的小写 `dataflow`/`tsc` token **不引入**（`tsc` 是 L2 的后端实现细节，规则只声明 L2；`dataflow` 与 L3 同物不双名）。`requires` token 匹配大小写不敏感。deep 分析器的可用性走 `AnalyzerAvailability` 探针接口（按 capability 注册；探测廉价、绝不启动后端——同 `TypeResolver.isAvailable()` 纪律；无探针或探针报否 = 上限内也降级，fail-closed），注入与 TypeResolver 同构（run 装配侧）。L2 门控路径不受 deep 扩展影响。CLI 支持 `--profile deep`；CLI 不接 resolver/探针，故 deep 档命令行下 L2+/deep 规则按设计降级（与 item 20/26 先例一致）。

**CLI provider 拾取演进（落地增注，2026-09-24，roadmap item 32 Phase 2）**：上一段"CLI 不接探针"的裁决自 item 32 起**对 METRICS 维度演进**——`CheckRunner` 经 ServiceLoader（`MetricsResolverDiscovery`）拾取 classpath 上的 `MetricsResolver`（nop-lint-java 提供），CLI `--profile deep` 下 `requires: METRICS` 规则的度量查询真实可用；L2/L3/L4/SCOPE 维持无 provider 即降级的原裁决。

**SCOPE 维度同型演进（落地增注，2026-09-24，roadmap item 33 Phase 2）**：上段裁决自 item 33 起**对 SCOPE 维度同样演进**——`ScopeResolverDiscovery` 拾取 `ScopeResolver`（nop-lint-java 提供），`requires: SCOPE` 规则 deep 档下真实可用；SCOPE 不再走 AnalyzerAvailability 探针路（deep-only 探针路仅余 L3/L4，其分析器由 item 34 落地后裁定接入方式）。

**L3/L4 维度同型演进（落地增注，2026-09-24，roadmap item 34 Phase 2）**：上段裁决自 item 34 起**对 L3/L4 维度收口**——`SemanticResolverDiscovery`/`DataflowResolverDiscovery` 拾取两类 provider（nop-lint-java 提供），`requires: L4`/`L3` 规则 deep 档下真实可用；**四个 deep-only capability（L3/L4/SCOPE/METRICS）全部走 resolver 路，AnalyzerAvailability 探针路不再是任何 capability 的门控通道**（接口与既有实现保留为扩展点）。穿透面收敛为 `DeepResolvers` record（四 resolver，engine 构造期；filePath 为 per-lint-call 参数不入 record）。

## 3. 分析器成本模型与挂接方式

| 分析器 | 初始化成本 | 每文件/每 match | 挂接方式 | 档位 |
|--------|-----------|----------------|---------|------|
| kind 位过滤 | 编译期 | ~0（O(1) 位判断） | 管线第 1 步 | 全部 |
| SourcePatternCompiler 结果 | 编译期（规则加载） | μs–ms | 管线第 2 步 | 全部 |
| 约束求值器 | 编译期 | μs | 管线第 3 步 | standard+ |
| xscript | 编译期 | 0.05–100ms（deadline 截断） | 管线第 4 步 | fast(限)/standard+ |
| L1 DeclTypeResolver | 0 | ~0 | pattern/xscript 内联查询 | 全部 |
| L2 JavaParserFacade | **秒级**（solver：JDK 反射 + Maven jar + 源码三路） | 每文件 5–50ms（二次解析，惰性） | 双 AST 映射按需构建（06 §6） | standard+ |
| L2 tsc bridge | **秒级**（program 加载）+ 常驻 Node 进程 | 每查询 <5ms（缓存后） | 进程复用 + program 缓存 + tsconfig hash 失效 | standard+ |
| 数据流/常量传播 | 0 | 每方法 0.1–10ms | 惰性：仅当规则 requires 且 match 已发生 | deep |
| Scope/CodePath/Metrics | 0 | 每方法 0.1–5ms | 同上 | deep |

**惰性原则**：任何分析器只有在"某条已匹配规则真正查询它"时才初始化；没有 L2 规则命中的文件不产生 JavaParser/tsc 成本。

## 4. 缓存体系（三层，失效规则明确）

| 缓存 | 键 | 失效 | 位置 |
|------|----|------|------|
| CST 缓存 | 文件路径 | `TSParser.parseIncremental`（03 §1.2，EditCalculator 计算增量） | 进程内（编辑器常驻）/磁盘（CI） |
| CompiledRule 缓存 | 规则集内容 hash | 规则文件变更 | 进程内 |
| 类型缓存（L2） | (文件内容 hash + classpath/tsconfig hash) | 源码或依赖变化 | 磁盘，跨运行复用（CI 必须 warm-up 命中） |

- CI 建议：缓存目录随仓库/流水线工件保存；命中率进入 LintStats
- tsc program 缓存额外受 tsconfig `include/paths/references` 影响，hash 覆盖这些字段

## 5. 降级阶梯（预算超限时按序关闭）

```
1. 数据流/Scope/Metrics（deep 内部先关）
2. L2 tsc bridge（退化为 TS 语法规则 + skippedByProfile）
3. L2 Java symbol solver（同上）
4. xscript deadline 收紧（100ms → 20ms）
5. fix 生成（standard 下最后关；fast 本就不开）
—— pattern/kind/约束永不关闭（它们是底线功能）
```

- 每次降级在 LintStats 记录 `degraded: [analyzer...]`，报告可见
- **禁止**的降级：用 L1 结果冒充 L2（语义变化）；静默跳过规则（必须计数）
- 落地口径（item 20 Phase 2，与 §2 增注同源）：L2 降级以规则为粒度计入 `rulesDegraded`/`degradedRuleIds`（L2 分析器维度的聚合由报告层完成）；门控期与求值期失败同归降级，均无诊断输出
- **fast 档预算闭合**：per-match deadline = min(20ms, 文件 xscript 时间片剩余)；时间片（默认 10ms/文件，所有 match 共享）耗尽后，剩余 match 不再执行 xscript，逐条计入 `xscriptBudgetExceeded` 统计并标记 `degraded`（绝不静默丢检；这些 match 仍输出 pattern 层结果）
- **pattern 匹配自身的防线**（xscript 有 deadline 而 matcher 需要等价保护，最坏回溯模式下省略号嵌套是指数级的）：
  1. **编译期静态检查**：嵌套省略号深度上限（默认 2）、超限拒绝编译并报错（规则作者侧拦截）
  2. **运行期软预算**：pattern 匹配计入文件预算（§2 表中"单文件预算"包含 pattern 阶段）；单文件 pattern 阶段超预算时中止该文件剩余规则并标记 `degraded`（计数进 LintStats，不静默）
  3. "pattern/kind/约束永不关闭"指**降级阶梯**（§5）不关闭它们；预算熔断是最后防线，两者不矛盾

**阶梯 v2 + 熔断 + 时间片落地口径（落地增注，2026-09-24，roadmap item 31 Phase 2，plan 2026-09-24-0900-1，live 以源码为准）**：
- **双钟口径（裁定）**：§5 两句原文（"超时按序关闭分析器"与"pattern 阶段超预算中止剩余规则"）在同一触发点上响应相反，实现采用**同一预算值、两座钟**——总钟（整个 lint 过程）耗尽触发阶梯（降级继续），pattern 钟（仅匹配阶段累计）超预算触发熔断（中止剩余）。两钟同时越界时**熔断胜出**（最后防线语义）。此为让两句原文同时为真的最小机制，不引入新数字。
- **阶梯四级形态（裁定）**：§5 五级 → 实现四级：L4 并入第 1 级（deep 专属昂贵分析器 L3/L4/SCOPE/METRICS）；tsc 与 Java solver 合并为一个 L2 级（规则面只声明 L2，无后端区分）。单一耗尽点下"逐级"退化为**有序全闭合 + 有序记录**（`LintStats.degradedAnalyzers`，仅记录真实处于开启态的分析器：有探针的 deep capability、live resolver 的 L2，然后恒有 `xscript`、`fix`（仅 standard/deep）；每级每 lint 调用至多一条）。闭合后未执行规则在**规则边界重判**（`CompiledRule.requires` 与闭合集相交即走既有 DEGRADE 出口）；约束期 `TypeResolutionException` 保留纯原生语义（不存在中途闭合场景）。
- **fast 时间片**：10ms/文件所有 match 共享；per-match deadline = min(20ms, 规则值, 时间片剩余)；剩余 <1ms 即视为耗尽（XScriptDeadline 下限），剩余 match 跳过脚本、仍输出 pattern 层诊断（规则静态 message/severity、match 节点 range——已知假阳性代价，显式接受）；逐条计入 `xscriptBudgetExceeded`，涉及规则 id 记入 `xscriptBudgetExceededRuleIds`（"标记 degraded"的落地面；不复用 L2 语义的 `degradedRuleIds`，避免与"降级规则不产出诊断"契约冲突）。
- **fix 门控**：fast 档从不开启 fix 生成（修正 item 25 的 live 漂移——原实现全档生成）；standard/deep 阶梯关闭后停止生成，被跳过生成逐条计入 `fixesDegraded`；fast 的按档关闭不是降级事件、不计数。
- **预算口径**：每 `engine.lint()` 调用一份预算（`--fix` 多 pass 每 pass 新预算、各 pass 如实报告自身降级；运行级 id 列表 first-seen union、计数求和）；`breakerAbortedRuleIds` 非空即该文件 degraded 标记，运行级以 `filesDegraded` 计数呈现。时钟经包私有构造注入（测试确定性）；`LintDeadlineExecutor` 的脚本内 deadline 强制恒用真实时钟。

## 6. 场景化运行剖面

| 场景 | 档位 | 并行 | 预期 |
|------|------|------|------|
| 编辑器 keystroke | fast | — | <50ms/文件（增量 CST + 增量匹配） |
| pre-commit（staged 文件） | fast | 并行 | 秒级 |
| CI（PR / full） | standard | VirtualThreads 全并行 | 500 文件 ≈ 1–3 分钟（缓存 warm） |
| 夜间 deep | deep | 全并行 + 分片 | 可小时级 |
| GraphQL lint__checkSource（03 §2.3） | fast | — | <100ms（含解析）；不初始化 L2 |

## 7. 与其他文档的一致性契约

| 文档 | 必须遵守的口径 |
|------|--------------|
| 01 §4 | CompiledRule 与档位无关（编译一次，各档位复用） |
| 03 §1.3 | 性能优化表以本文 §3/§4 为准（本表只保留指针） |
| 06 §5.6/§6.6 | Java/TS 延迟数字统一引用本文 §3/§6 |
| 07 §3 | xscript deadline 默认值按档位缩放（fast 20ms / standard 100ms） |
| 08 §2 | 依赖矩阵 = 本文 §2 档位启用的超集来源 |
| 09 §5 | baseline 检查仅在 standard+ 运行 |

## 8. 交付节奏（状态跟踪见 [backlog roadmap](../../backlog/nop-lint-roadmap.md)）

- Phase 1：fast + standard 两档（L2 未到，standard 暂等于 fast + 约束空集）；`skippedByProfile` 统计
- Phase 2：standard 档补齐 L2/tsc 惰性挂接与类型缓存；降级阶梯 v1
- Phase 3：deep 档（数据流/Scope/Metrics）；降级阶梯 v2
- Phase 4：CI 缓存工件化、编辑器 watch 集成、GraphQL fast 档调优（对应 roadmap Wave 6；性能声称的发布门槛 = Wave 2 的 benchmark item）

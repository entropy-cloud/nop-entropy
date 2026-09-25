# Nop Refactor — Architecture Baseline

**日期**：2026-09-25
**范围**：代码修改能力的目标架构、模块边界、GraphQL 契约面、结果反馈载荷契约
**状态**：目标基线（草案）——Vision 见 [00-vision.md](./00-vision.md)；实现未启动，逐操作立项前本文为模块拓扑与契约的权威

---

## 一、设计结论

1. 平台代码工具三件套按"读/查/改"分工，接口形态统一 GraphQL：**nop-code**（读：符号表/依赖图/调用层级查询）、**nop-lint**（查：规则检查/约束/诊断）、**nop-refactor**（改：重构操作/批量改写）。
2. 模块拓扑对齐 nop-lint 模块族：`nop-refactor-core`（操作框架）+ `nop-refactor-java`（Java 语言适配）+ `nop-refactor-graphql`（GraphQL biz 面）；后续语言适配逐语言追加。
3. 修改操作统一四段契约：**check → plan → apply → verify**；返回统一 self-verification 载荷（§4）。

## 二、模块拓扑与依赖方向

```mermaid
flowchart TB
    subgraph 消费面
        GQL["nop-refactor-graphql<br/>Refactor__* 操作"]
        CLI["CLI 批处理形态<br/>同引擎零新语义"]
    end
    subgraph refactor
        CORE["nop-refactor-core<br/>操作框架: check/plan/apply/verify<br/>编辑计划 + 校验器 + 报告载荷"]
        JAVA["nop-refactor-java<br/>Java 符号解析/文本渲染适配"]
    end
    subgraph 既有底座（只读消费）
        TS["nop-treesitter<br/>语法树/重解析验证/ERROR 计数"]
        LINTFIX["nop-lint fix 机制<br/>冲突仲裁/原子写/回滚"]
        JP["nop-java-parser<br/>JavaParser + SymbolSolver"]
        CODE["nop-code<br/>引用搜索/依赖图"]
        LINTQ["nop-lint 引擎<br/>残留诊断复核"]
    end
    GQL --> CORE
    CLI --> CORE
    CORE --> JAVA
    JAVA --> JP
    CORE --> TS
    CORE --> LINTFIX
    CORE -->|"v1 不接线（WI2 裁定：引用搜索落操作器内嵌轻量索引）"| CODE
    CORE --> LINTQ
```

依赖规则：

- `nop-refactor-*` 对既有底座**只读消费**，不改其行为（对齐 nop-lint 对平台"零改动"的既有裁定风格）。
- 语言适配以 SPI/接口注入 core（操作对象：符号解析、作用域语义、文本渲染），core 语言无关；新语言 = 新适配模块，core 零改动。
- 与 nop-lint 的边界：nop-lint 的 fix 管线继续服务"诊断驱动修复"；refactor 的编辑计划（先于诊断存在的 per-file 编辑序列）经**抽出的应用入口**复用其冲突仲裁/原子写/回滚机制，不绕道诊断流。
- 引用搜索（CORE→CODE 边）：WI2 实测裁定 v1 不接线——跨模块源码根解析率 0.3%（18/5292）、第三方模块无 classpath 装配器时解析面崩塌（nop-lint-core 9.0%、nop-lint-java 14.4%、nop-java-parser 30.7%），引用搜索落操作器内嵌轻量索引（证据：`ai-dev/analysis/2026-09/2026-09-25-wi2-symbol-solver-coverage-spike.md`）。

## 三、GraphQL 操作面契约

命名沿平台 `Obj__action` 大写惯例（同 `Lint__checkSource`），前缀 `Refactor__`：

```graphql
type Mutation {
    "预览：计算编辑计划并返回 diff + 自检载荷，不落盘"
    Refactor__previewRewrite(input: RewriteInput!): RefactorResult!
    Refactor__previewRename(input: RenameInput!): RefactorResult!
    "应用：重算编辑计划并原子落盘，返回同一载荷（applied=true）"
    Refactor__applyRewrite(input: RewriteInput!): RefactorResult!
    Refactor__applyRename(input: RenameInput!): RefactorResult!
}
# RewriteInput: pattern/规则集 + 目标文件集合（codemod 形态）
# RenameInput: 目标符号定位（FQN 或 文件+字节偏移）+ 新名 + 符号域范围
```

裁定：

- **按操作类型拆分 action（preview/apply × rewrite/rename），共享同一 `RefactorResult`**：GraphQL 的 union/interface 不能用作 input 位置类型（语言规范限制），"一个泛型 action + union 入参"不可实现；拆分 action 把"恰一操作"校验交给 schema，动作数与操作类型线性增长（当前 4 个），AI 工具面保持自描述。操作类型增加时按同型扩展。
- **preview/apply 两段语义**（dry-run 与落盘分离）而非 plan-token：语义显式，无会话状态（Vision §6）。apply 内部重新计算编辑计划（操作确定性保证两次计算一致），随后原子落盘。
- 操作输入的**目标定位必须机器友好**：符号用 FQN 或"文件+字节偏移"定位，不用光标/选区概念。
- 写类操作不经 GraphQL 暴露给不受信调用方时的资源约束（目标文件上限、源大小上限）沿用 `Lint__checkSource` 的 cap/fail-closed 模式。

> **落地增注（2026-09-25，nop-refactor WI7：CLI 批处理形态，live 以 nop-refactor-core cli 包源码为准）**：
> - **v1 命令面**：`nop-refactor preview|apply --rules <vfs-prefix> [--json] <file|dir>...`——preview/apply 两子命令 + 规则集 VFS prefix + 目标集合（显式文件/目录；glob 不入 v1）+ `--json` 机器可读。手写 fail-closed 解析（非法输入抛模块异常并携带 usage 行）；分发沿 `java -cp` classpath 装配（无 mainClass/shade）。
> - **渲染面**：沿 nop-lint `Reporter` 接口形态先例（render(outcome, Writer) + writer 生命周期归 CLI），定义 refactor 侧双渲染（console + json）于同一 RefactorResult 载荷上——不新增载荷字段；pattern 直给 Non-Goal（由 WI6 RewriteInput 承载其 v1 收窄的对称面）。
> - **退出码三态边界**：0 = 运行完成且 nonApplied 空；1 = 运行完成但 nonApplied 非空（conflict / out-of-scope / unresolved-target 占位 / ROLLED_BACK）；2 = 中止（参数解析、规则集加载或加载门失败、目标不可读、IO 失败、`transformDegraded>0`——降级丢失的改写编辑收敛为中止而非静默）。1 与 2 的分界 = "运行是否完成"。
> - **守卫回滚归类**：rolledBack 文件（preview dryRun 与 apply 同语义）→ edits 剔除 + 每文件一条 `NonApply(ROLLED_BACK)`（additive 第四枚举 + SkippedBuckets 第四桶，见 §四 增注）。
> - **profile 钉死**：生产入口固定 STANDARD（FAST 下 transform 通道结构性关闭——关门而非降级，恒不产出）；加载门 = (a) 仅接受 transform 规则（getTransform()==null 即拒绝）+ (b) transform 规则 requires 为空才接受（非空可能在 profile 门被 SKIP/DEGRADE 致改写永不计算）；加载门恒以 STANDARD 判定。

> **落地增注（2026-09-25，nop-refactor WI6：Refactor__ GraphQL 契约面，live 以 nop-refactor-graphql 源码为准）**：
> - **v1 schema 只声明 rewrite 对**（Refactor__previewRewrite / Refactor__applyRewrite）：rename 对随 WI12 无破坏地加入（schema 增字段是非破坏变更；声明恒抛的 action 是对 AI 工具面的虚假广告）。roadmap 原文"RewriteInput：规则集/pattern"为目标面描述，v1 契约收窄为规则集-only（pattern 直给 Non-Goal，未来过性价比门另立项）；glob 目标展开同理不入 v1。
> - **write 面 path grammar 三分支**（较读面更严）：namespace 前缀拒绝 / `/`-根拒绝（VFS 资源无法供磁盘原子写消费）/ 磁盘路径 toRealPath 工作目录约束（含规范化前置判定 + symlink 重检）。
> - **资源 cap 两键**：`nop.refactor.graphql.max-source-size`（默认 1MB，字节级 read 前置门 + 读后 backstop）+ `nop.refactor.graphql.max-target-files`（默认 512，目标集合解析后、任何读取前拒绝）。
> - **残留 lint 子集改判**：v1 不配子集（residualRuleCount=0 诚实"未配"）——transform 规则自身作子集会使 residualDiagnostics 构造性恒 0 而伪报"已配零残留"；独立可配残留检查面归 Follow-up。
> - **守卫回滚呈现**：rolledBack 文件 → edits 剔除 + 每文件一条 `NonApply(Reason.ROLLED_BACK)`（additive 第四枚举 + SkippedBuckets 第四桶，见 §四 owns 增注）；两阶段纪律——transformDegraded 校验前置到全量计算阶段，降级中止不发生在部分落盘状态；中途 IO 失败 fail-fast 且错误消息枚举已落盘文件（v1 语义，扩展枚举归后续 design）。
> - **加载门共享**：CLI 与 GraphQL 消费 `RefactorRuleGates.verifyRewriteRuleset` 单一实现（仅 transform 规则 + requires 空 + 语言绑定已注册）。

## 四、结果反馈载荷契约（self-verification）

每个修改操作返回统一 `RefactorResult`，字段全部为机器可判读的结构化数据：

```graphql
type RefactorResult {
    applied: Boolean!              # preview=false, apply=true
    edits: [FileEdit!]!            # per-file 结构化编辑：path + 范围 + 变更摘要
    diff: String!                  # unified diff（AI 直接审读面）
    verification: Verification!    # 自检面：AI 判断"是否满足预期"的依据
    stats: RefactorStats!          # 影响文件数/编辑数/跳过分桶/耗时档位
    nonApplied: [NonApply!]        # 未应用项：原因枚举（冲突/超范围/目标未解析）+ 上下文
}
type Verification {
    parseOk: Boolean!              # 每个被改文件修改后重解析（nop-treesitter）
    errorNodeCount: Int!           # 修改后 ERROR/missing 节点总数（>0 即语法破坏信号）
    residualDiagnostics: Int!      # 修改后按可配规则集重跑 lint 的残留诊断数
    symbolIntact: Boolean          # 语义操作（rename 类）：目标符号引用计数改写前后一致
}
```

载荷设计裁定：

- `verification` 是**操作结果的一部分，不是独立操作**：AI 拿到一次响应即可完成大部分自检（Vision 原则 3）。
- `nonApplied` 显式枚举未应用项与原因——批量操作中部分失败不是错误，而是结构化结果的一部分（fail-closed 指文件级失败可归因，不指整体非黑即白）。
- `stats` 中的外部桥调用（若该语言适配使用进程桥）标注成本档位（进程内/进程外），供 AI 调度方决定批量策略。

> **落地增注（2026-09-25，nop-refactor WI5，live 以 `nop-refactor/nop-refactor-core` 源码为准）**——v1 具象语义：
> - **聚合口径**：多文件操作下 `parseOk` = 各文件谓词的 AND；`errorNodeCount`/`residualDiagnostics` 跨文件求和。**单文件 parseOk 谓词 = 重解析后无 ERROR/missing 恢复节点**（文件自身编辑后状态，非与编辑前解析的差值——差值判断属应用入口的守卫回滚语义面，两者显式分立）；解析异常 fail-closed 抛错，永不伪报 parseOk=true。
> - **规则子集未配的可判读形态**：`RefactorStats.residualRuleCount`=0 表示"未配规则集"，与"已配规则集且零残留"（count>0 且 residualDiagnostics=0）机器可区分——不是静默跳过。
> - **symbolIntact null 语义**：codemod 面（rewrite 类）恒为 null；仅 rename 类操作填充（WI9–WI12 接管其正式计算）。
> - **FileEdit 取值语义**（闭合 design 03 的 Fix 字段留白）：`path`=目标文件显示路径；`range`=编辑替换的字节区间（编辑前内容坐标系）；`summary`=取自编辑载体的 description 字段（lint 规则 fix 或 refactor 操作的编辑描述）；来源标识取载体 ruleId（规则 id 或 refactor 操作/编辑类别 id）。
> - **耗时档位**：v1 codemod 全链路纯进程内（vision 原则 7），恒标 `IN_PROCESS`；外部桥（OUT_OF_PROCESS）为未来语言适配预留。
> - **模块落点**：`nop-refactor-core` 依赖 nop-lint-core（UnifiedDiff/LintLanguage/LintEngine/RuleDslModel 只读消费），verification 计算与载荷类型同模块（WI9 操作框架在此之上）。
> - **ROLLED_BACK 扩枚举 owns（2026-09-25，WI6/WI7 裁定 9 + R2）**：baseline §四 的三原因（冲突/超范围/目标未解析）为例示面；守卫回滚（改写破坏语法被 revert）在三分支无可安放语义——`NonApply.Reason` additive 增 `ROLLED_BACK`、`RefactorStats.SkippedBuckets` 同步 additive 第四桶 `rolledBack`（桶是 nonApplied 的计数镜像，漏计即镜像契约破裂）。WI5 契约测试同步 3→4 并断言原三枚举保持。

## 五、与外部形态的关系

- **CLI**：`Refactor__previewRewrite`/`Refactor__applyRewrite`（及 rename 对）的批处理形态，供流水线与无 GraphQL 运行时使用；引擎同一实现，CLI 不引入第二套语义（对齐 nop-lint CLI/Maven/GraphQL 共享 `CheckRunner` 的既有形态）。
- **LSP**：本能力无 LSP 面（Vision non-goals）。nop-lint 既有 LSP v1 原样保留、零扩张。
- **外部引擎**：平台不引入外部大型系统作为能力源（自完备约束，见 [../self-contained-design.md](../self-contained-design.md) 与 [00-vision.md](./00-vision.md) §6）——jdt.ls 桥、OpenRewrite 引擎包装均被否决。大版本迁移场景平台不提供集成面，下游可独立选用外部工具，与本能力无关。

## 六、拒绝了什么

- **编辑计划经诊断流表达**（把重构伪装成"诊断+fix"进 nop-lint 引擎）：破坏 Diagnostic 契约（一个 rename = N 文件 M 编辑，不是每 match 一个 fix），搅乱 baseline/退出码语义——refactor 走独立操作框架，仅复用 fix 的**应用机制**。
- **plan-token 会话形态**：见 [00-vision.md](./00-vision.md) §6。
- **按语言切分顶层模块**（nop-java-refactor 之类）：编排层（check/plan/apply/verify、编辑计划、校验、报告）语言无关，按语言切会复制编排；语言差异收敛在适配模块。
- **外部引擎桥接补全集**（jdt.ls 承接工程级重构、OpenRewrite 包装迁移能力）：违反平台自完备约束——见 [00-vision.md](./00-vision.md) §6 与 [../self-contained-design.md](../self-contained-design.md)。

## 七、约束与边界

- **复杂度预算**：总复杂度不超过 nop-lint 模块族量级（main Java 约 2.2 万行 / 6 模块，2026-09-25 实测 21,927 行；计数口径 = src/main/java 全量、排除生成物与测试）；架构保持薄——四段契约（check/plan/apply/verify）最小实现，扩展机制按需建，优先复用 nop-lint 既有机制而非在本模块重造。
- **性价比门**：每个新操作立项必须过门——AI 使用频率 × 不可替代性 ÷ 新增复杂度。初始裁定：**P0 = codemod 面**（RewriteInput 批量改写，复用 nop-lint pattern/fix 存量，新增量集中在契约面）；**P1 = rename**（单模块符号域内，rename 阶梯：局部变量→字段→非虚方法→类型）；**out = 结构变换类**（extract/inline/change signature/move 族——见 vision §四 价值排序）。
- 本能力不修改 nop-treesitter/nop-lint/nop-code 的既有行为与公开契约；扩展点缺失时优先在自身模块内建，确需上游扩展另立 design。
- 语义级操作（rename 等）的逐操作可行性、classpath 装配与引用搜索来源（已裁定（2026-09-25）：操作器内嵌轻量索引，nop-code 边 v1 不接线——`ai-dev/analysis/2026-09/2026-09-25-wi2-symbol-solver-coverage-spike.md`；逐操作可行性仍留给逐操作 design）在逐操作 design 中裁定；本文只锁接口形态与模块边界。
- 里程碑范围与排期以 backlog/plan 为准，本文不承载执行排期。

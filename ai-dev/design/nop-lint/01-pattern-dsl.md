# Nop Lint — 核心模块设计

> 日期: 2026-09-19（修订 2026-09-20）· 状态: 设计草案（索引见 [00-nop-lint-design.md](./00-nop-lint-design.md)）

## 1. 模块划分

```
nop-lint/                        # 顶层模块组（与 nop-stream/nop-rg 同级）
├── nop-lint-core/               # 核心引擎（依赖 nop-treesitter、nop-xlang；无法做到"零 Nop 依赖"）
│   ├── pattern/                 # SourcePatternCompiler + MetaVar 匹配
│   ├── rule/                    # 规则 DSL 解析与执行（XDSL）
│   ├── constraint/              # 约束求值器（Phase 2）
│   ├── fix/                     # 自动修复引擎（Phase 2）
│   ├── semantic/                # 数据流/Scope/度量（Phase 3）
│   └── xml/                     # XML 规则支持（基于 Nop XNode，非 tree-sitter）
├── nop-lint-java/               # Java 语言支持（依赖 nop-java-parser、nop-ai-code-analyzer 的 solver）
│   ├── java/                    # Java 特定规则
│   └── bizmodel/                # Nop BizModel 规则
├── nop-lint-js/                 # TypeScript/TSX 支持（含 tsc bridge，Phase 2）
└── nop-lint-nop/                # Nop 平台规则库
    ├── rules/                   # YAML 规则文件
    └── fixers/                  # 修复模板
```

说明：
- 生态模块（nop-lint-maven-plugin / nop-lint-graphql / nop-lint-cli）见 `08-migration.md` Phase 4。
- **XML 规则不使用 tree-sitter**（无 XML grammar blob），改用 Nop 自有 XNode 解析器；Pattern DSL 对 XNode 树做结构匹配，meta-var 语义一致。
- nop-lint-core 依赖 nop-treesitter（传递依赖 nop-commons/nop-graphql-core/nop-ioc），**不承诺零 Nop 依赖**。
- **nop-lint-js 已就位（2026-09-22，item 19，plan 2026-09-22-1045-1）**：`TypeScriptLanguage`（id `typescript`）与 `TsxLanguage`（id `tsx`）两个 ServiceLoader 绑定经 `TreeSitterLanguageAdapter` 驱动已出货 blob（shared static adapter + public no-arg ctor，结构同 `JavaLanguage`）；语法级适配已落地，tsc bridge 归 item 20（Phase 2），React/JSX 组件分析归 Phase 3（05 §6）。

## 2. 规则 DSL（YAML 格式）

```yaml
# 规则定义
id: nop-no-raw-exception          # 唯一标识
language: Java                     # 目标语言（Java | TypeScript | TSX | XML）
severity: error                    # hint | info | warning | error | off
message: "禁止直接抛出 RuntimeException，使用 NopException + ErrorCode"

# Pattern 层（匹配目标代码）
rule:
  any:                            # 单层 any 在 Phase 1 即支持
    - pattern: throw new RuntimeException($$$ARGS)
    - pattern: throw new Error($$$ARGS)
    - pattern: throw new Throwable($$$ARGS)

# 约束层（跨节点条件，Phase 2）
constraints: []

# xscript 动态检查（可选，Phase 1 起支持，见 07-xscript-engine.md）
xscript: |
  if (!typeAnalyzer.isSubtypeOf(className, 'CrudBizModel')) return;
  report({ message: '...', severity: 'error' });

# 修复层（Phase 2）
fix:
  description: "替换为 NopException"   # 修复说明（report() 中同名字段）
  template: throw new NopException(ERR_CODES.XXX).param($$$ARGS)
  suggest: false                       # true = 仅作为建议输出，不参与 --fix（ESLint suggestions 对齐）
  # fix template 语法：仅做 meta-var 替换（$VAR/$$$VAR 引用捕获）；
  # 非捕获标识符（如 ERR_CODES.XXX）按字面量保留

> **fix 字段落地增注（2026-09-23，item 25，live 以源码为准）**：`description`/`template` 双必填、
> `suggest` 默认 false；模板引用未声明捕获或未声明 `$TOKEN` 在 `CompiledRule` 编译期拒绝（v1 无
> 转义语法，fail-closed）；`$$$VAR` 渲染取首末捕获节点间原始源码切片；`fix`+`xscript` parse 期拒绝、
> XML 语言路径声明 `fix` compile 期拒绝；模板字面量 v1 拒绝转义需求，deindent/reindent defer（03 §3 增注）。

# 分析器依赖声明（11 §1 档位聚合依据；Phase 2 起由引擎消费）
requires: []          # 可选值：L2 | tsc | dataflow | scope | metrics（详见 10 §2）

# 规则可配置项（对标 ESLint rule.options；xscript 经 rule.options 读取）
options: {}

# 元数据
metadata:
  category: exception-handling
  severity: error
  autoFixable: false
  version: "1.0"                   # 规则版本：Phase 1 起即必填的最小版本戳
                                   # （Phase 1–3 约定：语义/severity/id 变更必须升 version 并记
                                   #   入规则库 CHANGELOG，保证 baseline fingerprint 与
                                   #   @SuppressWarnings 的可追溯；完整 semver 策略 Phase 4 收口）
  source:
    - checkstyle.xml#IllegalThrows

> **版本策略收口增注（2026-09-24，roadmap item 42，plan 2026-09-24-2350-2，live 以源码为准）**：上文"记入规则库 CHANGELOG"的**载体显式收口为各规则文件头注**——不建独立 CHANGELOG 文件，62 条规则的头注即逐条机制/裁定/来源记录。触发枚举原样保留：**语义/severity/id 变更必须升 `version`**。可追溯双面：(a) per-rule 面 = 头注变更记录（服务 @SuppressWarnings 的规则级锚定）；(b) 库级聚合面 = 规则目录再生成 diff（`ai-dev/tools/gen-lint-rule-catalog.mjs --check` 防漂移门禁，`nop-lint/docs/rule-catalog.md` 为确定性生成的在档目录——version/severity/message 列的任何变更都会使目录 diff 非零）。规则目录**按目录名分组**（非 metadata.category——live 的 metadata.category 存在碎片值，目录以文件系统布局为准，碎片值在 source 列尾 note 呈现）。"完整 semver 策略"指针保留，Phase 4 收口不因本增注关闭。
    - service-layer.md §异常处理

# 作用域
files:
  include: ["src/main/java/**/*.java"]
  exclude: ["*_test.java", "*Test.java"]
```

> schema 约定：`language`/`constraints`/`xscript`/`fix`/`requires`/`options`/`metadata`/`files` 一律为**顶层字段**；`rule:` 内部只允许 pattern/kind/regex/any/all/not/matches/inside/has/follows/precedes 等匹配器。
>
> **消息输出裁定（2026-09-22，item 11 执行期承接 plan 1420-1 移交）**：v1 引擎按 `message` 字面输出诊断消息（`RuleDslModel.message` 原文），**不引入** `{{VAR}}` 捕获插值。理由：本批落地规则无一需要静态消息携带捕获内容；需要捕获内容的动态消息由 xscript `report({ message: '...' + captures.X.text() })` 拼接覆盖。若未来需要静态插值，以独立 item 立项——须先定义缺失捕获与转义语义，不得无契约引入。
>
> **元模型**：本 DSL 的权威结构定义是 xdef 元模型（`10-xdef-metamodel.md`）；YAML 经 XDSL（DslModelParser + xdef 校验 + `x:extends` delta 合并）加载，字段名/枚举以 xdef 为准。

## 3. Pattern 语法设计（超越 ast-grep）

### 3.1 Meta-变量系统

| 语法 | 含义 | 示例 |
|------|------|------|
| `$VAR` | 匹配单个命名节点 | `throw new $EX($$$)` |
| `$$$VAR` | 匹配零或多个节点序列 | `method($$$ARGS)` |
| `$$VAR` | 匹配命名或匿名节点 | `binary_expression($L $$OP $R)` |
| `$_VAR` | 非捕获匹配（优化） | `if ($_COND) { $$$ }` |
| `$@TYPE` | **类型化 meta-变量**（Phase 2，依赖 L2 类型推导） | `($@Type $VAR).method()` |
| `$!LITERAL` | **字面量约束** | `return $!null` 匹配 `return null` |

### 3.2 跨节点约束（超越 ast-grep）

```yaml
rule:
  pattern: $A == $B
constraints:
  # 同一文本约束（ast-grep 已有）——YAML 键与 xdef 标签同名 camelCase（10 §5）
  - sameText:
      captures: [$A, $B]

  # 不同文本约束
  - differentText:
      captures: [$A, $B]

  # 正则约束
  - regex:
      capture: $A
      pattern: "^[a-z]+$"

  # 类型约束（Phase 2，依赖类型层级 L2）
  - typeOf:
      capture: $A
      is: String

  # 值域约束
  - inList:
      capture: $A
      values: ["get", "post", "put", "delete"]

  # 跨模式约束（新能力）
  - notExists:
      pattern: |
        method $A($$$) {
          return null;
        }
      message: "方法 $A 不应返回 null"
```

> **约束语义裁定（2026-09-22，item 22，plan 2026-09-22-1045-3）**：
>
> - **Decision（约束极性）**：constraints 是 **per-match 谓词过滤器**——匹配产出的每个 match 独立求值，**全部约束成立才产出诊断**（全称合取）；任一约束不成立 → 该 match 被过滤（不产诊断），进入 `constraintFilteredMatches` 显式计数（`LintStats` 新口径，不静默丢弃）。约束对 xscript 规则同样生效，求值位置在 xscript 之前（03 §1.1 增注）。
> - **Decision（notExists 作用域）**：notExists 按 **per-match 子树**求值——以匹配节点为根（**含匹配节点本身**）的子树内不存在内层 pattern 的任何匹配时成立。§3.2 示例（方法体含 `return null` 的否定场景）的意图即子树语义；全文件作用域不是 v1 契约。notExists 的可选 `message` 在 v1 是**保留字段**：过滤器极性下约束失败不产诊断，message 不被求值消费，仅随模型存储（xdef 表面含它以对齐本节示例；消费面归后续诊断解释层，立项另议）。
> - **Decision（typeOf × L2 门）**：使用 typeOf 的规则**必须**声明 `requires: "L2"`；缺声明由 `RuleDslParser` 加载期 fail-closed 拒绝（报错含规则 id）。L2 能力仍由 profile 决定（v1 全档无 L2 → 整规则 `skippedByProfile` 计数，绝不以 L1 结果冒充评估——roadmap 硬约束）；约束求值器对 typeOf 的求值路径为显式 fail（经引擎不可达的防御分支，不静默返回）。
> - **字段校验权威（RuleDslParser fail-closed，设计 10 §2 同步）**：未知约束名、单元素多个约束键、必填子段缺失（regex 的 capture/pattern、inList 的 capture/values、typeOf 的 capture/is、notExists 的 pattern、withinDepth 的 max）、sameText/differentText 的 captures 空或元素数 <2、inList 的 values 空/含空项、withinDepth 的 max 非整数或负值、capture 引用名非法（`$NAME` 或裸 `NAME`，归一化后须匹配 `[A-Z_][A-Z_0-9]*`）——全部加载期显式抛 `NopLintException`（消息含规则 id）。capture 引用与匹配器 meta-var 集的一致性在**编译期**校验（`CompiledRule`，见 §5 增注）；`withinDepth` 的极性裁定见 §3.3。

### 3.3 关系规则（超越 ast-grep）

> **归属澄清（与 10 xdef 一致）**：`inside/has/follows/precedes/not` 是 **`rule:` 内的匹配器**（与 pattern/kind/regex 同级，可组合于 any/all/not）；`constraints:` 只放对 **capture 的值约束**（sameText/regex/typeOf/inList/notExists/withinDepth/controlFlow）。
>
> **注释/字符串掩码裁定（2026-09-22，item 23 Phase 1，消解 plan 2137-3 deferred 关切）**：树级（AST）匹配下**无需显式注释/字符串掩码**——隔离是模式形态的天然结果：注释是 extra 节点（SMART 锁步中作为 candidate 被跳过，且任何 pattern 形态的 kind 都与 `block_comment`/`line_comment` 不同）；字符串/字符字面量是 `string_literal`/`character_literal` kind 的叶子，identifier/call 等模式形态的 kind+text 双重匹配不可能命中它们。故 `check-silent-swallow.mjs` 的 `maskCommentsAndStrings`/`stripCommentsAndStrings`（文本层掩码，防 javadoc 里的 `catch (` 假块与字符串里的 `ErrorCode.` 假信号）在 AST 内核中由**类型隔离 + SMART 跳过**承接，nop-lint 不引入掩码层。代价与边界：文本层"信号出现即命中"的保守语义（含字符串引用）收窄为 AST 节点语义（字符串字面量中的信号不再计为命中）——这是已裁定的行为 delta，已回写规则对照表（item 23 Phase 3，`ai-dev/logs/2026/09-22.md` silent-swallow 对照节；delta 方向 = nop-lint 严格于 mjs，另有精确标识符 vs 子串匹配、BizException 形态收窄两条同类 delta 同表记录）。

```yaml
# 祖先/后代/兄弟匹配器（rule 级，Phase 2）
rule:
  all:
    - pattern: $BODY
    - inside:
        pattern: try { $$$ } catch ($E) { $$$ }   # 祖先（ast-grep: inside）
    - not:
        has:
          pattern: return null;                    # 后代（ast-grep: has）

rule:
  all:
    - pattern: $STMT
    - follows:
        pattern: LOG.warn($$$);                    # 兄弟（ast-grep: follows/precedes）
```

```yaml
# capture 值约束（constraints 级）
rule:
  pattern: return $V
constraints:
  # 深度约束（新能力）：极性见 §3.2 裁定——成立 = 匹配节点子树深度 ≤ max
  #   （子树深度 = 匹配节点到其任一后代节点的最长边数，匹配节点自身为 0）。
  #   本例为浅层守卫用例：浅层 return 照常报告，深层匹配被过滤并计入
  #   constraintFilteredMatches。注意："报告过深嵌套"类检测在本极性下
  #   不能用 withinDepth 表达（见 §3.2 Decision；withinDepth 表面仅 max
  #   字段，无 message）。
  - withinDepth:
      max: 3

  # 路径约束（新能力，Phase 3 依赖 CodePath 分析）
  - controlFlow:
      from: method_entry
      to: $RETURN
      through: $EXCEPTION
      message: "异常路径到达返回点"
```

### 3.4 组合规则

> **算子分界裁定（2026-09-22，item 23/24）**：item 23 对应 plan 交付 **all/not + 关系四算子**（successor 规则的最小组合面；组合 DSL 嵌套面在 item 24 起递归放开，见下）；item 24 承接 **matches 递归 + utils 共享规则 + any 嵌套 refinement**。此裁定消除 roadmap item 文本（"not 归 24"）与 plan 2137-3 deferred 指针（"not 组合归 23"）的歧义：**not 的解析/组合/内核语义归 item 23**，`matches`/utils 归 item 24；`stopBy=rule` 的 stopByRule 在 item 23 完成解析期校验（缺名 fail-closed），util 规则注册表与引用解析归 item 24（编译期对 stopByRule 显式拒绝，不静默降档）。

```yaml
# AND — 所有子规则匹配同一节点（item 23 交付）
rule:
  all:
    - pattern: $EXPR
    - kind: method_invocation
    - regex: "^.+\\.saveEntity$"

# OR — 任一子规则匹配（单层 any Phase 1 即支持）
rule:
  any:
    - pattern: dao().saveEntity($$$)
    - pattern: dao().save($$$)

# NOT — 排除匹配（item 23 交付；probe env 隔离，xor 语义）
rule:
  all:
    - pattern: $OBJ.method($$$)
    - not:
        pattern: $OBJ instanceof $TYPE

# 递归 — 规则本体引用自身文件的 utils（utils 引用图须无环：matches/stopBy=rule
# 的展开发生在同一节点上，任何环都永不终止，parse 期 fail-closed——
# 见 design 04 §6 item 24 落地注记）
utils:
  is-safe-close:
    any:
      - pattern: IoHelper.safeClose($$$)
      - pattern: IOUtils.closeQuietly($$$)
      - all:
          - pattern: $X.close()
          - inside:
              pattern: try { $$$ } catch ($$$) { $$$ }

rule:
  not:
    matches: is-safe-close       # matches 是字符串 util id（xdef：<matches>!string</matches>）
message: "直接调用 .close() 而非 IoHelper.safeClose"   # message 是顶层字段（schema 见 §2）
```

### 3.5 Nop 特有 Pattern 扩展

> 注意：以下示例的 `language`/`constraints` 均为**顶层字段**（与 §2 schema 一致）。

```yaml
# Nop 注解模式
id: nop-bizmutation-must-declare-throws
language: Java
rule:
  all:
    - pattern: |
        @BizMutation
        public $$$ $METHOD($$$ $PARAM, IServiceContext $CTX) {
          $$$
        }
    - not:
        has:
          pattern: throw new $$$($$$)
message: "@BizMutation 方法应声明可能抛出的异常"

# Nop ORM 模式（XML 规则走 XNode，非 tree-sitter）
id: nop-orm-mandatory-default
language: XML
rule:
  all:
    - pattern: |
        <entity name="$$$" tableName="$$$">
          <columns>
            <column name="$$$" domain="$$$" mandatory="true"/>
          </columns>
        </entity>
    - has:
        pattern: <column name="$$$" domain="$$$" mandatory="true" defaultValue="$$$"/>
message: "mandatory 列应有 defaultValue"

# Nop xbiz 模式
id: nop-xbiz-auth-not-sole-guard
language: XML
rule:
  all:
    - pattern: |
        <action name="$$$">
          <auth>$$$</auth>
        </action>
    - inside:
        pattern: <xbiz entity="$$$"> $$$ </xbiz>
message: "xbiz action auth 声明在测试下不可断言，不应用作唯一守卫"
```

**XNode Pattern 匹配语义（Phase 2 XNode 引擎的设计边界，先立契约）**：

| 维度 | 语义 |
|------|------|
| 节点对应 | XNode（标签 + 属性 + 文本 + 子节点）↔ PatternNode；标签名匹配相当于 kind 匹配 |
| 属性匹配 | 属性值是字面量 → 精确文本相等；是 meta-var（`$$$`）→ 捕获；属性缺席时 pattern 中声明的属性必须存在（缺省属性不匹配，除非用 `$_`） |
| 文本匹配 | 元素文本按 trimmed 文本比较；`$$$` 可匹配任意文本（含空） |
| 命名空间 | `x:` 前缀的 XDSL 内部属性（x:extends 等）**不参与**匹配——匹配的是合并后的最终模型 |
| 严格度 | 复用 §3 的严格度概念：Smart（默认）忽略注释节点；无 CST 层（XNode 无括号/逗号类 trivial 节点） |

> **XNode Pattern 引擎落地增注（2026-09-22，item 21，plan 2026-09-22-1045-2）**——上表的执行口径与两项 Decision 结论：
>
> - **Decision（pattern 编译形态）**：XML pattern 片段经平台自有 `XNodeParser`（doc 模式，恰一根元素，多根/坏形 fail-closed）解析，编译为 `XNodePattern`（nop-lint-core `xml/` 包）：tagName（=kind，经 `XmlTagKinds` 内联出稠密 kind id，既有 kind 位图过滤零特判复用）+ 属性约束集 + 文本约束 + 字面子元素序列。meta-var 语法位与 tree-sitter 路径**共享同一 `MetaVarSyntax` 分类器**，捕获经同一 `MetaVarEnv`（probe-clone/commit-on-success、同名一致性、`$$$` 序列语义）——两条路径的 meta-var 语义由共享实现保证一致，测试矩阵逐格钉死。标量位（属性值/文本）v1 支持字面量 / `$VAR`（捕获；`$_VAR` 按惯例 drop）/ `$_`·`$_VAR`（drop，属性位可缺席）/ 裸 `$$$`（通配：文本含空、属性要求存在）；`$$$VAR`、`$$`·`$$VAR`、裸 `$` 在标量位**编译期拒绝**（标量位无序列语义可供），`$@`/`$!` 同内核拒绝。
> - **维度语义执行口径（表格行的落地读法）**：pattern **未声明的维度不约束**——属性 = 开放世界约束集（声明的属性必须存在且值匹配，未声明的候选属性自由，含 `x:` 内部属性）；文本未声明 = 不约束，声明了按 trimmed 比较；子元素序列一旦声明 = **闭式 lockstep**（逐对等序、元素数相等）——`$$$` 的序列语义因此与内核契约一致；"任意子内容"需求由"不声明子序列"或 `has` 关系算子承担（下文 `nop-xbiz-auth-not-sole-guard` 的 faithful 形态以 `has` 表达"action 含 auth 子节点"）。混合内容 pattern 元素（文本+子元素并存）fail-closed；CDATA 文本按其文本值参与匹配；混合内容中的非空白文本节点以 `#text` unnamed trivia 暴露（匹配步过，文本语义走 trimmed content 维度）。
> - **Decision（relation 算子在 XNode 的语义）**：inside/has/follows/precedes 经 `LintNode` 门面**原样复用** `RelationalMatcher`/`StopBy`（parent/children/兄弟遍历同构）；`stopBy=neighbor|end` 可用，`stopBy=rule` 维持 item 24 拒绝，`field` 与 `context`+`selector` 形态在 XML 路径编译期 fail-closed（XNode 无 field 槽、无上下文解析）。对应关系注明于 design 04 §5。
> - **命名空间/严格度行执行口径**：`x:` 前缀属性在 **pattern 侧**编译期拒绝（请求匹配 `x:` 即目标错层）；源侧 `x:` 属性天然不被查询。Smart = 注释跳过（XNode 注释挂靠后继节点，facade 以 `#comment` extra trivia child 暴露，匹配器与 `CommentSuppressionScanner` 零 XML 特判共享）+ 无 CST 层。根节点后注释被平台解析器丢弃（parser 行为，非 lint 裁定）。
> - **`nop-xbiz-auth-not-sole-guard` faithful 形态裁定（item 21 Phase 3，dogfood 前复核）**：§3.5 示例的 `<action name="$$$">` 标签在真实 xbiz 模型中不存在——xbiz.xdef 的 actions 容器下为 **typed 元素**（query/mutation/subscription/action 四形态），且 v1 组合面拒绝 any 嵌套于 all（item 24 边界）。因 xdef 中 `<auth>` 仅作为 action 级声明存在（loader 等容器无 auth），生产规则的 faithful 形态取 `all: [pattern: <auth>$$$</auth>, inside: <actions>$$$</actions>]`——与示例意图外延等价，诊断精确落在 auth 声明本身；四 typed 形态全覆盖。示例原形的实体级语义由 `nop-orm-mandatory-default` 的 all+not 形态忠实承接（mandatory 列 = `<column name="$$$" mandatory="true"/>` 减去带 `defaultValue` 的同形态；属性开放世界使规则在真实 orm 模型上成立——真实列的 domain 属性可缺席）。

## 4. Pattern 编译管线

> **关键事实**：TSQuery 是 S-expression 查询语言（TSQueryParser 不支持量词、无 `$` meta-var），**不能**直接承载 ast-grep 风格的源码 pattern。Pattern 编译器是新建的 `SourcePatternCompiler`，复用 TSParser 解析能力与 TSQueryCursor 的匹配模型作参考。

### 4.1 决策记录：扩展 TSQuery vs 独立 SourcePatternCompiler

曾评估"给 TSQuery 增加 `$VAR`/`$$$VAR`/量词"的方案，结论是**独立编译器严格更优**：

| 维度 | A：扩展 TSQuery | B：独立 SourcePatternCompiler（采用） |
|------|----------------|--------------------------------------|
| **匹配模型** | TSQuery 游标是"每节点单次结构测试"，**无跨兄弟回溯**。`$$$` 需要 lockstep 遍历 + lookahead probe + aggregator 克隆（04 §3）——把回溯塞进 TSQueryCursor 等于重写其核心循环，并破坏单遍扫描的性能模型 | 新 matcher 按回溯语义从零设计，不拖累 TSQuery 原有路径 |
| **meta-var 一致性** | `$A == $A` 需要 env 记账 + `does_node_match_exactly` 二次精确匹配；TSQuery 的 capture 是单次写、无同名一致性语义 | MetaVarEnv 原生实现（04 §2） |
| **严格度系统** | 无 Smart/AST/CST 分级维度（skip_goal/skip_candidate 逐节点决策） | 6 级严格度是 matcher 内建参数（04 §4） |
| **源码 pattern 体验** | 规则作者要写 `(throw_expression (instantiation ...))`；若保留 `throw new $EX($$$)` 体验，仍需"源码片段→查询"翻译层——**即 SourcePatternCompiler 的大头，并未省掉** | pattern 即代码，翻译层就是编译器本体 |
| **上游演化** | nop-treesitter 的 TSQuery 是 tree-sitter 官方查询语言的 Java 移植；改语法 = 维护方言 fork，上游 predicate/语法改进合并成本永久化 | TSQuery **冻结不扩展**；独立引擎自由演化 |
| **实证先例** | — | ast-grep 与 Semgrep 均构建于 tree-sitter 之上，且都**未扩展** tree-query 而另写 pattern matcher——两个最成熟工具的相同选型 |
| **代价** | 引擎内核改造风险共享给所有 TSQuery 使用方 | 两条匹配路径并存 → 用"TSQuery 冻结、仅作辅助查询（L1 声明类型提取等）；SourcePattern 是规则唯一路径"约束管理 |

**共享与隔离边界**：共享 TSParser（解析 pattern 片段与源码）、LintNode 门面、kind 预计算思想；隔离匹配内核（PatternNode 树形态、MetaVarEnv、lockstep matcher 均为独立实现，不改动 nop-treesitter 既有类）。

```
YAML rule file
  ↓ (XDSL parser)
Rule AST
  ↓ (RuleCompiler)
CompiledRule {
  id, language, severity, message
  matcher: SourcePattern          # 由 SourcePatternCompiler 编译（见下）
  constraints: Constraint[]       # 约束列表
  xscript: CompiledXScript?       # 编译后的 IExecutableExpression（可空）
  fix: FixTemplate?               # 修复模板
  utils: Map<String, CompiledRule>  # 工具规则
  files: FileFilter               # 文件过滤
}
  ↓ (per source file)
LintResult {
  matches: Match[]                # 匹配位置
  diagnostics: Diagnostic[]       # 诊断信息
  fixes: Fix[]                    # 修复建议
}
```

**SourcePatternCompiler 编译步骤**（Phase 1 新建，非 TSQuery 扩展）：

```
pattern 文本 "throw new RuntimeException($$$ARGS)"
  ↓ 1. 预处理：$ → 占位标识符（按语言 expando 规则），保证可被解析
  ↓ 2. TSParser.parse(patternSnippets, language)     ← 复用 nop-treesitter 解析
  ↓ 3. CST → PatternNode 树（leaf → Terminal/MetaVar；internal → Internal{kindId, children}）
  ↓ 4. extractEffectiveNode（自根沿唯一子节点链下降，遇多子 Internal 或叶即停；
       2026-09-22 修订：原"取最内层 >1 子节点的节点"公式不可执行——旗舰 pattern
       的最内层多子节点是 argument_list 而非 throw_statement，叶锚定 pattern 整链
       无 >1 子节点；落地语义见 plan 03 设计偏差声明 1）
    ↓ 5. kind 预计算（any → kind 并集；all → kind 交集）用于 O(1) 节点过滤
```

> **TS/TSX expando 裁定（2026-09-22，item 19，plan 2026-09-22-1045-1）**：`TypeScriptLanguage`/`TsxLanguage` 均取 **identity 预处理**（expando 位传 null），与 Java 同判。依据：ECMA-262 `IdentifierPartChar` 含 `$`，故 `$VAR`/`$_X`/`$$$ARGS`（连续 `$` 合法）在两 grammar 中均按单 `identifier` 词法单元保留——该断言不是假设，由 nop-lint-js 绑定测试以解析级断言钉死（`$$$ARGS` 作形参名单标识符、`$VAR` 作 variable_declarator name，双 grammar 各自验证，`TypeScriptLanguageTest`/`TsxLanguageTest.metaVarTokensParseAsSingleIdentifiers`）。若未来某语言标识符不含 `$`，其绑定经 `TreeSitterLanguageAdapter` 的 expando 位落地替换规则，不改内核。

> **kind 预计算策略裁定（2026-09-22，item 23 Phase 1，扩展至 relational/all/not 顶层）**：kind 贡献按匹配器形态定义——`pattern` → 其 `possibleKindIds`；`kind` → 单元素集；`relational`（inside/has/follows/precedes）与 `not` → **空 = 无意见（通配）**（关系算子的候选节点 kind 由"其它节点"上的内层匹配约束，`not` 的否定语义使 kind 不可用）；`all` → 各子匹配器非空意见的**保守交集**（全为空意见 = 无意见）；`any` → 并集（既有）。无意见的组合顶层**全量放行**（规则照常执行、命中靠匹配器本身），绝不以其它信号冒充 kind 结果、也绝不静默跳过——`RuleSetRunner` 既有 `rulesKindFiltered`/`rulesExecuted` 计数照常如实反映。例：`all: [kind: catch_clause, not: {has: ...}]` 的目标 kind = {catch_clause}（kind 分支贡献、not 通配后交集不变）。

> **kind 预计算策略裁定（2026-09-22，item 24）**：`matches` 顶层/嵌套 → 被引用 util 的 kind 意见（registry 记忆化递归，引用图无环保证终止）；any（含嵌套于 all/not 的 any 与 any 的对象分支）→ 各分支非空意见**并集**（全空 = 无意见）。utils registry 编译期急切构建（util pattern 的捕获并入规则的 capture index，供约束编译期校验）；registry 为空（无 utils）时零成本空转。

## 5. PatternMatcher 设计

> **API 适配说明**：nop-treesitter 的 Java 节点 API 是 cursor 风格（`TSTreeCursor.gotoChildByFieldId/FieldName`），文本需按 byte-range 从源码切片；`compat/TSNode` 提供 `getChildByFieldName` 兼容层。以下示例使用**计划新增的 `LintNode` 门面**（封装 cursor 遍历 + 源码切片 + kind/symbol），避免各文档示例与底层 API 漂移。

```java
// 门面节点（新增）：统一节点访问 API
public interface LintNode {
    String kind();                     // 节点类型名（底层 symbol()/effectiveSymbol() 映射）
    String text();                     // 源码切片（按 byte range）
    LintNode child(String fieldName);  // 底层 gotoChildByFieldName 封装
    List<LintNode> children();
    SourceRange range();
}

// 匹配器接口：CompiledRule.matcher 为语言绑定的匹配器
public interface SourcePattern {
    // language 在编译期绑定，运行期只需 CST 根 + 源码
    List<Match> match(LintNode root, CharSequence source);
}

// Meta-变量匹配器 —— 语义与 ast-grep 对齐（04 §2）：
// $VAR 匹配【任意单个命名节点】（位置语义，无 kind 限制）；
// 类型化 meta-var $@TYPE（Phase 2，L2 类型推导）才做 kind/类型收窄。
public class MetaVarMatcher implements NodeMatcher {
    private final String name;
    private final TypeFilter typeFilter;  // Phase 2 前恒为 ANY

    public boolean matches(LintNode node, MetaVarEnv env) {
        if (!node.isNamed()) return false;          // 只匹配命名节点
        if (!typeFilter.allows(node)) return false; // $@TYPE 收窄（Phase 2）
        return env.insert(name, node);              // 同名变量一致性检查
    }
}

// 跨节点约束求值器（Phase 2）
public interface Constraint {
    boolean evaluate(MatchContext ctx, DiagnosticCollector diag);
}

// 例：SameText 约束
public class SameTextConstraint implements Constraint {
    private final String captureA, captureB;

    public boolean evaluate(MatchContext ctx, DiagnosticCollector diag) {
        String textA = ctx.getCaptureText(captureA);
        String textB = ctx.getCaptureText(captureB);
        return textA.equals(textB);
    }
}
```

> **约束求值契约落地增注（2026-09-22，item 22）**：上方 `Constraint` 接口为伪代码级契约；落地形态是 `io.nop.lint.core.constraint.Constraint`（`boolean holds(ConstraintContext)`）——按 §3.2 极性裁定为**纯过滤器语义**，无 `DiagnosticCollector` 参数（约束失败不产诊断，只过滤 match 并计入 `constraintFilteredMatches`）。求值输入 = match 的 captures（`MetaVarEnv`）+ 匹配节点（`ConstraintContext`）；notExists 的内层 pattern 在规则编译期经 `SourcePatternCompiler` 编译（fail-closed），求值期零编译。**编译期 capture 一致性校验**：约束引用的 capture 必须出现在该规则匹配器的 meta-var 集（单节点捕获 `$VAR`/`$$VAR`；引用 `$$$SEQ` 序列捕获或 `$_VAR` drop 名 → 编译期拒绝并指明原因），未声明即抛 `NopLintException`（消息含规则 id、约束名与 capture 名）。typeOf 的求值分支为显式 fail（`requires: "L2"` 门使规则在引擎层被 `skippedByProfile`，见 §3.2 Decision）。

## 6. 语义分析层（Phase 3）

> Phase 归属以 `08-migration.md` 为准：**数据流分析、常量传播、Scope 分析均为 Phase 3**。

```java
// 数据流分析器（Phase 3）
public class DataFlowAnalyzer {
    // 变量定义-使用链
    public DefUseChain analyzeDefUse(LintNode method);

    // 常量传播
    public Map<String, ConstantValue> propagateConstants(LintNode method);
}

// 度量评估器（Phase 3）——注意：复杂度不是"AST 深度计算"
public class MetricsEvaluator {
    // 圈复杂度 = 决策点计数（if/for/while/case/catch/&&/||/?:）+ 1
    public int cyclomaticComplexity(LintNode method);

    // 认知复杂度 = SonarSource 规范增量表（嵌套增量 + 线性流中断）
    public int cognitiveComplexity(LintNode method);

    // NPath = 各决策结构路径数的乘积
    public long nPathComplexity(LintNode method);
}

// Nop 特有语义分析（Phase 3）
public class NopSemanticAnalyzer {
    public boolean isDirectDaoAccess(LintNode call);
    public boolean usesSafeApi(LintNode method);
    public TransactionBoundary analyzeTransaction(LintNode method);
}
```

> **MetricsEvaluator 落地增注（2026-09-24，roadmap item 32，plan 2026-09-24-1000-1，live 以源码为准）**：`MetricsEvaluator` 落于 `nop-lint-java` semantic 包（JavaParser AST，方法级，item 30 同型）。v1 口径：圈复杂度 = 决策点计数 +1（if/for/while/do/非 default case/catch/三元 + 每个短路算子节点独立计）；认知复杂度 = SonarSource 白皮书 v1.7 Appendix B 增量表（if/else-if/else 平坦 +1 但抬嵌套层级、switch 连全部 case 合计单次 +1、循环/catch/三元 +1+嵌套、lambda 无增量但抬嵌套、逻辑运算符序列 +1/新序列平坦）；NPath = 路径乘积（if/三元/循环 ×2、switch 非 default 标签 n → ×(n+1)、catch 每个 ×2、短路算子节点每个 ×2，long 饱和）——**乘积枚举，非 AST 深度**（§6 红线）。residual：递归增量（需调用图）、labeled jump 增量、NPath 序列修正项。规则消费面 = xscript `metrics` 绑定（见 07 增注），位置键桥接走 `JavaTypeResolver` 同型（自 parse + JavaNodeIndex.minimalContaining + 父链上溯，0-based line/UTF-16 col 契约），ServiceLoader SPI 发现。

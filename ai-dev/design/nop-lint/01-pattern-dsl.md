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
    - service-layer.md §异常处理

# 作用域
files:
  include: ["src/main/java/**/*.java"]
  exclude: ["*_test.java", "*Test.java"]
```

> schema 约定：`language`/`constraints`/`xscript`/`fix`/`requires`/`options`/`metadata`/`files` 一律为**顶层字段**；`rule:` 内部只允许 pattern/kind/regex/any/all/not/matches/inside/has/follows/precedes 等匹配器。
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
  # 同一文本约束（ast-grep 已有）——YAML 键 camelCase ↔ xdef 标签 same-text（10 §5）
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

### 3.3 关系规则（超越 ast-grep）

> **归属澄清（与 10 xdef 一致）**：`inside/has/follows/precedes/not` 是 **`rule:` 内的匹配器**（与 pattern/kind/regex 同级，可组合于 any/all/not）；`constraints:` 只放对 **capture 的值约束**（sameText/regex/typeOf/inList/notExists/withinDepth/controlFlow）。

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
  pattern: $BODY
constraints:
  # 深度约束（新能力）
  - withinDepth:
      max: 3
      message: "嵌套超过 3 层，考虑提取方法"

  # 路径约束（新能力，Phase 3 依赖 CodePath 分析）
  - controlFlow:
      from: method_entry
      to: $RETURN
      through: $EXCEPTION
      message: "异常路径到达返回点"
```

### 3.4 组合规则

```yaml
# AND — 所有子规则匹配同一节点（Phase 2）
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

# NOT — 排除匹配（Phase 2）
rule:
  all:
    - pattern: $OBJ.method($$$)
    - not:
        pattern: $OBJ instanceof $TYPE

# 递归 — 自引用（Phase 2）
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
    matches:
      util: is-safe-close        # matches 的对象形式（xdef：<matches util="!string"/>）
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
          pattern: throw new $$$
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
  ↓ 4. extractEffectiveNode（取最内层 >1 子节点的节点作为匹配根）
  ↓ 5. kind 预计算（any → kind 并集；all → kind 交集）用于 O(1) 节点过滤
```

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

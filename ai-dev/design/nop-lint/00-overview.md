# Nop Lint — 设计目标与原则

> 日期: 2026-09-19（修订 2026-09-20）
> 状态: 设计草案（v3）
> 范围: nop-lint 模块组（nop-lint-core/-java/-js/-nop）的定位、原则与架构概览
> 层级: 本篇承担 Vision + Architecture Baseline 双职（见 [README.md](./README.md) 的层级声明）

## 1. 设计目标

构建一个**在 Nop 技术栈内一体化**的 AST lint 系统，能力对标并选择性超越现有工具（"超越"逐维度可验收，不追求全维度第一；性能声称须经 benchmark item 背书，见 roadmap Wave 2）：

| 维度 | 现有最佳 | 目标 |
|------|---------|------|
| Pattern 表达力 | ast-grep (meta-var + relational) | **+ 跨节点约束 + 类型感知** |
| 语义分析 | Semgrep (taint + const propagation) | **+ Nop 业务语义**（数据流为 Phase 3 子集，taint 不在范围） |
| 性能 | ast-grep (Rust, 43ms/500文件) | **纯 Java 下可接受的档位化性能**（编辑器 <50ms/文件、CI 分钟级，见 11 §6；不以超 ast-grep 为目标） |
| 配置格式 | ast-grep YAML | **+ 规则组合 + 条件编译** |
| 自动修复 | ESLint multipass fixer | **+ pattern-level rewrite** |
| 多语言 | Semgrep (30语言) | **Java 为主 + TypeScript/TSX，XML 走 XNode**（Python 在 backlog） |
| 可扩展 | ESLint plugin | **Nop IoC 注册 + YAML 扩展** |

## 2. 设计原则

### 2.1 匹配查找是独立的 DSL

Pattern Matching 本身是一个独立的 DSL，可以脱离 YAML 独立使用，类似 ast-grep 的 CLI 能力：

```bash
# 独立使用：查找所有 catch 空块
nop-lint match --lang java --pattern 'catch ($E) { }' src/

# 独立使用：查找所有直接抛出 RuntimeException 的代码
nop-lint match --lang java --pattern 'throw new RuntimeException($$$)' src/

# 独立使用：查找 BizModel 中直接调用 dao() 的代码
nop-lint match --lang java \
  --pattern '$OBJ.dao().$METHOD($$$)' \
  --inside 'class $C extends CrudBizModel' \
  src/
```

YAML 只是 DSL 的一种文本表示形式。DSL 本身可以通过多种方式使用：
- **YAML 规则文件**（声明式，适合规则库）
- **CLI 命令行**（交互式搜索）
- **Java API**（编程式集成）
- **GraphQL API**（远程调用）
- **编辑器集成**（实时检查）

### 2.2 xscript 动态检查

Pattern Matching 获得备选节点后，可以用 xscript（XLang 脚本）进行动态判断：

```yaml
id: nop-no-direct-dao-access
language: Java
severity: error
message: "BizModel 中不应直接调用 dao()，请使用 CrudBizModel 安全 API"

rule:
  all:
    - pattern: $OBJ.dao().$METHOD($$$ARGS)
    - inside:
        pattern: class $C extends CrudBizModel

# xscript 动态检查（运行时判断）
xscript: |
  // 获取匹配节点的完整类型信息
  let classDecl = node.ancestor('class_declaration');
  let className = classDecl.child('name').text();

  // 检查是否是 BizModel 子类（需要类型解析）
  if (!typeAnalyzer.isSubtypeOf(className, 'CrudBizModel')) {
    return;  // 非 BizModel 类，跳过
  }

  // 检查 dao() 调用是否在允许的位置
  let methodDecl = node.ancestor('method_declaration');
  let methodName = methodDecl.child('name').text();

  // 允许的特殊方法
  let allowedMethods = ['initConfig', 'setupDefaults'];
  if (allowedMethods.contains(methodName)) {
    return;
  }

  // 报告违规（report 默认作用于当前匹配节点；可用 node:/capture: 指定其他范围）
  // 注意：XLang 反引号字符串是普通字面量（无 JS 式 ${} 插值），动态消息用 + 拼接
  report({
    message: 'BizModel ' + className + ' 中方法 ' + methodName + ' 不应直接调用 dao()，请使用 CrudBizModel 安全 API',
    severity: 'error',
    fix: {
      description: '替换为 requireEntity() 或 doFindList()',
      template: 'requireEntity($OBJ.class, $$$ARGS)'
      // 默认 range = 匹配节点本身；可加 capture: '$OBJ' 覆盖修复目标
    }
  });
```

**xscript 运行时上下文**：
- `node` — 当前匹配的 AST 节点
- `captures` — Pattern 捕获的 meta-变量映射
- `typeAnalyzer` — 类型解析服务（可选）
- `scopeAnalyzer` — 作用域分析服务（可选）
- `report()` — 报告违规的方法（fix 作为 report 参数内嵌，见 07 §2.3）

**xscript 执行时机**：
1. Pattern Matching 先执行（快速过滤，O(1) kind 过滤）
2. 静态 Constraints 执行（跨节点条件检查）
3. xscript 执行（复杂动态判断）
4. 报告最终结果

### 2.3 YAML 是 DSL 的一种表示

```
Nop Lint DSL
  ├── YAML 规则文件（声明式）
  ├── Java API（编程式）
  ├── CLI 参数（交互式）
  ├── GraphQL Schema（远程）
  └── 编辑器配置（实时）
```

DSL 的核心是 **Pattern + Constraint + Action** 三元组，YAML 只是序列化格式。

## 3. 架构概览

```
┌─────────────────────────────────────────────────────────┐
│                    Nop Lint CLI / API                    │
│  ./mvnw nop-lint:check  /  GraphQL: lint__checkSource   │
└──────────────┬──────────────────────────┬────────────────┘
               │                          │
    ┌──────────▼──────────┐   ┌───────────▼───────────┐
    │   Rule Engine        │   │   Fix Engine           │
    │   (规则加载/编译/执行) │   │   (自动修复生成)        │
    └──────────┬──────────┘   └───────────┬───────────┘
               │                          │
    ┌──────────▼──────────────────────────▼───────────┐
    │              Pattern Matching Core               │
    │  ┌─────────────────┐  ┌────────────┐             │
    │  │ SourcePattern    │  │ MetaVar    │             │
    │  │ Compiler (新建)  │  │ Matcher    │             │
    │  └─────────────────┘  └────────────┘             │
    │  ┌────────────┐  ┌────────────┐  ┌──────────┐   │
    │  │ Relational │  │ Composite  │  │ Constraint│   │
    │  │ Rules      │  │ Rules      │  │ Evaluator │   │
    │  └────────────┘  └────────────┘  └──────────┘   │
    │  ┌────────────┐  ┌──────────────────────────┐   │
    │  │ Semantic    │  │ xscript Engine (XLang)   │   │
    │  │ Analyzer    │  │ deadline 超时执行器        │   │
    │  └────────────┘  └──────────────────────────┘   │
    └──────────────────────┬──────────────────────────┘
                           │
    ┌──────────────────────▼──────────────────────────┐
    │  解析层（分层复用已有资产）                         │
    │  · nop-treesitter: TSParser + 增量解析            │
    │    (Java/JS/TS/TSX/Python/JSON grammars)         │
    │  · TSQuery: 仅作辅助 S-expression 查询            │
    │    （不承载 pattern DSL，无 meta-var）             │
    │  · Nop XNode: XML 规则解析                        │
    │  · nop-java-parser + nop-ai-code-analyzer:       │
    │    Java symbol solver + Maven 类型层次 (Phase 2)  │
    │  · tsc bridge: TS 完整类型推导 (Phase 2)          │
    └─────────────────────────────────────────────────┘
```

> **架构事实**：TSQuery（S-expression 查询）不能解析 `$`/`$$$` meta-var，pattern 引擎是新建的 `SourcePatternCompiler`（复用 TSParser 解析 pattern 片段；"为什么不扩展 TSQuery"的决策记录见 `01-pattern-dsl.md` §4.1）。XML 规则走 Nop XNode。YAML 规则文件经平台 XDSL 管线加载（register-model 注册 `DslJsonResourceLoader`，见 `10-xdef-metamodel.md` §4）。阶段划分见 `08-migration.md`，执行状态跟踪见 [backlog roadmap](../../backlog/nop-lint-roadmap.md)。

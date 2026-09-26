# DeepWiki Plan — nop-xlang

> Status: approved
> Target: /Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-kernel/nop-xlang @ HEAD
> Depth: standard（受限页面集：恒含 5 + flows 2 + modules 2 + topics 1 = 10 页；126K 行模块的全量覆盖按需后续生成）
> Language: zh
> Coverage: 模块 896 个源文件 / 126K 行在扫描范围（本轮受限页面集以编译管线核心为证据面）

## 1. 概念分析（两段式压缩结论）

**形态判定**：`compiler-parser`（自研语言族 XLang 的编译器 + 模板/表达式运行时）——叙事=编译管线各阶段（parse→AST→优化→类型推断→执行），管线顺序即章节顺序；代码块密度高；classDiagram 加权（AST 层级是重心）。

**领域概念**：XLang 是 Nop 平台的元语言族——XDef（语义坐标定义）、XDSL（模型合并 Δ x-extends）、XPL（模板）、XScript（脚本）、XT（转换）、XPath（路径语言）。实现核心是统一表达式编译：Expression(185 fan-in)/XLangASTNode(145)/XLangASTKind(119) 构成 AST 中枢，XLangErrors(129) 错误码体系，AbstractExecutable(101) 可执行体基类，IXplCompileScope(61)/IXplTagCompiler(38) 编译上下文与标签编译器。编译管线：antlr grammar（parse/antlr/XLangParser.java 11565 行为 ANTLR 生成物，源头是 .g4）→ _XLangASTBuildVisitor(2217行) 建 AST → XLangASTOptimizer(3028行) 优化 → TypeInferenceProcessor(2096行) 类型推断 → exec(141 文件) 求值。XDef 侧：SimpleStdDomainHandlers(1957行) 标准域处理器。资源面：数十个 xlib 全局函数库（biz!filter/biz!check/bean-gen/filter…）。

**核心数据流**：XLang 源文本 → Lexer/Parser（ANTLR）→ XLangASTNode 树 → Optimizer 化简 → TypeInferenceProcessor 标注类型 → 编译为 AbstractExecutable → 运行期 eval（Expression 体系）→ 输出。
**核心子机制**：类型推断（TypeInferenceProcessor）与 XDef 域处理（SimpleStdDomainHandlers——语义坐标的落点）。
**关键系统**：XPL 模板编译（xpl/ 84 文件，标签编译器 IXplTagCompiler 注册制）、XDSL 合并（xdsl/ 39 文件 + delta/）。

## 2. 模块地图（证据来源，非章节轴）

| 包 | 职责 | 关键类型（fan-in） | 供证页面 |
|----|------|-------------------|---------|
| expr/ast | 表达式与 AST 中枢 | Expression(185), XLangASTNode(145), XLangASTKind(119), Literal(49) | modules/ast-model, flows/expression-eval |
| parse | ANTLR 解析与 AST 构建 | parse/antlr/XLangParser(生成11565行), _XLangASTBuildVisitor(2217行) | flows/compile-pipeline |
| compile | 优化与类型推断 | TypeInferenceProcessor(2096行), IXplCompileScope(61) | flows/compile-pipeline |
| exec | 运行期求值 | AbstractExecutable(101) | flows/expression-eval |
| xdef/xdsl/delta | 语义坐标/模型合并 | SimpleStdDomainHandlers(1957行) | modules/xdef-xdsl |
| xpl | 模板引擎与标签编译 | IXplTagCompiler(38), xpl/ 84 文件 | modules/ast-model 带过 |
| xt/xpath/script/functions | 转换/路径/脚本/全局函数 | xlib 资源数十个 | glossary, architecture 带过 |

## 3. 页面契约（路径锁定）

| 路径 | 所属章 | 标题 | 职责 | 源文件（≥5） | relatedPages | 计划图表 |
|------|--------|------|------|--------------|--------------|----------|
| overview.md | 指南 | nop-xlang 总览：XLang 元语言族的编译器与运行时 | 定位/语言族构成/形态/能力边界 | Expression, XLangASTNode, XLangErrors, AbstractExecutable, XLang | architecture, flows/compile-pipeline | flowchart |
| quickstart.md | 指南 | 快速上手 | 构建/测试/最小表达式编译求值示例 | Expression, XLang, 测试类, XLangErrors | overview | 豁免 |
| glossary.md | 指南 | 术语表 | XDef/XDSL/XPL/XScript/XT/XPath/AST/Executable 等定义与划界 | XLangASTNode, Expression, AbstractExecutable, IXplTagCompiler, XLangConstants | modules/* | 豁免 |
| reading-guide.md | 指南 | 阅读指南 | 三类读者路径（fan-in 排序：Expression 185/ASTNode 145/Errors 129/Kind 119/AbstractExecutable 101） | fan-in 数据+入口 | 全部 | flowchart |
| architecture.md | 指南 | 架构与数据流 | 编译管线分层/包结构=管线阶段/语言族地图 | XLangASTNode, TypeInferenceProcessor, XLangParser(生成物说明), AbstractExecutable, XLangErrors | flows/*, modules/* | flowchart+sequence |
| flows/compile-pipeline.md | 机制 | 编译管线：从 XLang 源文本到可执行体 | parse→build→optimize→type-inference→compile 全链路每步发生什么 | _XLangASTBuildVisitor, XLangASTOptimizer, TypeInferenceProcessor, XLangASTKind, AbstractExecutable, IXplCompileScope | modules/ast-model | flowchart+sequence |
| flows/expression-eval.md | 机制 | 表达式求值：Expression 如何变成值 | 求值模型/Operand 体系/作用域/典型算子语义 | Expression, AbstractExecutable, Literal, Identifier, XLangOperator, exec 代表类 | modules/ast-model | class+sequence |
| modules/ast-model.md | 模块 | AST 节点体系与优化器 | 268 文件的节点分类/Kind 体系/Optimizer 化简规则 | XLangASTNode, XLangASTKind, XLangASTOptimizer, ast 代表节点类, IXLangASTVisitor | flows/compile-pipeline | classDiagram |
| modules/xdef-xdsl.md | 模块 | XDef 语义坐标与 XDSL 合并 | 域处理器/StdDomain 体系/模型合并与 delta | SimpleStdDomainHandlers, xdsl 代表类, delta 代表类, XDef 解析入口 | architecture | flowchart+class |
| topics/error-model.md | 主题 | 错误模型：XLangErrors 129 处引用的体系 | 错误码分组/ErrorCode 机制/编译期 vs 运行期错误 | XLangErrors, NopException(平台), 编译期抛出代表, exec 抛出代表 | modules/* | 无强制 |

## 4. 生成顺序
批 1a：modules/ast-model、modules/xdef-xdsl、flows/compile-pipeline、flows/expression-eval → 批 1b：topics/error-model、glossary → 批 2：architecture → overview、quickstart、reading-guide

## 5. 覆盖缺口与风险
- 896 文件受限集只能覆盖编译管线核心；xpl 模板引擎（84 文件）、xt（62）、xpath（41）、script、functions(xlib 数十个) 仅在架构/术语页带过——全量按需后续生成。
- XLangParser.java 为 ANTLR 生成物——机制页以 .g4 grammar 与 BuildVisitor 为证据，不以生成物为叙事对象。
- fan-in 文件级口径；126K 行模块的概览页必须声明"细节见子页"（层级递进）。

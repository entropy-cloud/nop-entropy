# XDef 描述式校验设计 对抗审查（2026-09-20 08:54）

- **对象**：`ai-dev/design/xdef-declarative-validation-design.md`（当日新建草案）
- **方式**：第一轮 3 个独立子 agent 并行（代码事实核验 / 写作规范与内部一致性 / 架构可行性与完备性），修复后第二轮换新 agent 复审（修复核验 + 新增声称独立验证 + 新问题扫描）
- **终局裁定**：**CONSENSUS（可收口）**——22/22 第一轮发现 RESOLVED；修订新增的 14 条事实声称 13 条 VERIFIED、1 条为审查员转述偏差且文档未受污染；无新 P0/P1；2 条 P2 措辞建议已在收口时修正。

## 第一轮发现（3 agent 裁定均为 RESOLVE-FIRST）

### P0（5 项，全部为"按原稿实现会出事故"级）

| # | 发现 | 证据 | 修复 |
|---|------|------|------|
| 1 | 执行点自相矛盾：原稿称阶段二绑定 `DslNodeLoader` validate 分支，又称 `AiCoderHelper` 等"直接调用 XDslValidator 的路径自动获得新阶段"——直接调用根本不经过 DslNodeLoader，该收益声明（4 处）不成立 | `AiCoderHelper.java:82-102`、`LocalFileOperator.java:360/365`、`ModelBasedPromptTemplate.java:328`、`DslModelParser.java:77-81` 全部不经 DslNodeLoader | 阶段二改挂 `XDslValidator.validate` 公有入口尾部，`defNode instanceof IXDefinition` 触发；`XDefinition.getRootNode()` 返回 this 使全部现存调用方天然满足 |
| 2 | 自举陷阱：过渡报错措施若按字面 `"xdef:"` 前缀实现，`xdef.xdef` 自解析即失败 → `SchemaLoader.loadXDefinition` 全部失败（平台级崩溃） | xdef.xdef:27-40 以 `xdef-meta` 为元模型前缀、`xdef:` 为业务名字空间；`XDefinitionParser.doParseResource:130` 守卫只豁免 validate 不豁免 parseChildren | 判别符定为 `XDefKeys.of(node).NS` + resourcePath 双保险，写入 §3.6.4 与 §3.7 P0 |
| 3 | def-type 挂点错位 + 遗漏全局缓存污染：`XDefTypeDeclParser` 无状态不查注册表；`XDslParseHelper.defTypeCache` 为按类型文本键的静态全局缓存，装饰共享实例会跨文件污染；"XDefAttribute 伴随对象"是 Kotlin 术语误用 | `XDslParseHelper.java:337-347`、`XDefinitionParser.getStdDomainHandler:116-121` | 挂点改 `getStdDomainHandler` 局部表 + 包装 handler / 独立副本，显式写明绕开缓存、收集先于 parseNode |
| 4 | prop 缺省回退缺 defNode 求值方式：阶段二 XPath 输入无 def 上下文，且 key-attr 是父作用域声明，原伪码一笔带过会误导实现 | `XDslValidator.checkUniqueOrKeyAttr:342-363` | §3.2 补"prop 缺省回退的求值方式"：阶段一顺带映射优先 / getChild 下探回退 + 解析顺序 |
| 5 | 遗漏 `_gen` 再生成链路：改 xdef.xdef 后 `_XDefinition`/`_XDefCheck*` 必须重生成，且为受保护区 | `nop-xlang/precompile/gen-xlang-xdsl.xgen` 首行 renderModel xdef.xdef；CodeGenTask 绑定 generate-sources（nop-kernel/pom.xml:297/318/369） | §3.7 P0 展开再生成步骤链 |

### P1（7 项）

scope 省缺值未定义（→ document）；check-ref targetProp/keyProp 双缺省回退链未闭合；errorCode 必填与默认错误码永不可达矛盾（→ 改可选）；§五 提案与 §3.7 落地表双状态冲突 + check-cardinality 无落点（→ 标注接受状态、纳入 P1）；动态 ErrorCode status=500 与现行 ERR_XDSL 系 -1 不一致；提交归因错误（约束语法来自 `bfe3301402`/`f2ba0e7896`，`3f6aa07a92` 仅 1 行 xdef.xdef 变更）；合并/ref 继承语义空白（约束不随 xdef:ref 复制、随 x:extends 继承）。

### P2（8 项）

错误码计数 75→90、transform 行号 :33→:34、错误码后缀 VIOLATION/VIOLATED 混用、mutex/require 被强加 scope 修饰、§4.2.2 时态、§六-2 缺"表达式 vs XPL"边界判据、XPathProvider.instance() 初始化前置风险（→ 改绑 XPathHelper 静态缓存）、性能量级与 JSON 链路边界未声明。

### 结构调整

P0 阶段过重：def-type 全量移入 P1（其与 check-* 无耦合且牵动两处解析点 + 运行点 + 缓存隔离 + 顺序重排）。

## 第二轮复审（CONSENSUS）

- (A) 22/22 发现 RESOLVED，修复方式与源码吻合；
- (B) 修订新增的 14 条事实声称逐条独立核验：13 VERIFIED（含 defTypeCache 三处 computeIfAbsent、IXDefinition 接口确无五类 getter、git 三提交 diff 内容、90 个错误码计数、XPathHelper 静态 cache、VFS getResource 纯文件层等）；1 条 PARTIAL 为第一轮审查员对 cleanRemoved 的转述偏差（实际只处理 REMOVE），但修订稿未采信该细节，无需修改；
- (C) 新问题扫描：无 P0/P1；2 条 P2 措辞（"无 status 重载"易误读为 ErrorCode 无该重载、文档头过程性注记）——收口时已修正。

## 结论

设计骨架（两阶段模型、元模型侧声明、select 绑定既有 XPath、分期收敛、六项拒绝）经两轮对抗审查后成立；修订后的关键架构决策：**集成点 = `XDslValidator.validate` 尾部单点收敛**、**过渡报错判别符 = `XDefKeys.of(node).NS`（自举安全）**、**def-type 必须绕开 `defTypeCache` 全局共享缓存**。文档可作为 P0 实施依据。

# I5 nop-xlang-truffle 模块骨架 + 帧/slot 映射 + 表达式子集翻译 + 翻译缓存

> Plan Status: active
> Last Reviewed: 2026-08-20
> Source: `ai-dev/backlog/xlang-execution-optimization-roadmap.md` I5；设计冻结于 `ai-dev/design/xlang-truffle/02-architecture-baseline.md`（§二钉版/§三 Language/Context/§四帧映射/§七翻译缓存）与 `ai-dev/design/xlang-execution/01-architecture-baseline.md` §五（对拍口径）
> Mission: xlang-execution-optimization
> Work Item: I5
> Related: I1（前置，对拍框架与 corpus v1）；与 I2-I4 并行（无依赖）

<!-- Draft review: round-1（fresh session ses_fe51ff3d0ffeJ3zBBh0hk4VoW0，1 Blocker+3 Minor）→ 修复 → round-2（fresh session ses_fe5155565ffeidxEa4d4xdhayS，B1/M1/M2/M3/M5 全 resolved，GO 0 Blocker/0 Major，共识达成）→ active。 -->

## Purpose

落盘 `nop-kernel/nop-xlang-truffle` 新模块：XLangLanguage/XLangContext + 帧/slot 映射 + 表达式子集 Executable 树 → Truffle AST 翻译 + 翻译缓存，激活对拍矩阵 truffle 列并全绿（允许 EXCLUSIVE 过渡形态）。roadmap 委托本 plan 定稿的两项决策（无 resourcePath 动态源缓存形态、过渡形态载体）在 Phase 1 裁定。

## Current Baseline

- I1 产物存在（前置）：对拍 harness（test-jar）+ corpus v1 + 测试级强制路由 API。I2 非依赖（并行轨道；共享 helper 依赖处理见 Phase 1 决策 D3）。
- `LexicalScopeAnalysis` live：`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/compile/LexicalScopeAnalysis.java`（`analyze` 产 slot 布局，前端既有资产）。
- 设计冻结要点（truffle 02）：树翻译非适配包装；`contextPolicy` 终态 SHARED、EXCLUSIVE 为翻译正确性对拍保守载体（形态切换 = 注解取值变更后重新编译，两形态各自是验证载体，SHARED+池并发验证归 I8）；帧/slot 映射 kind 仅可推断类型不虚构；翻译缓存键 = resourcePath + 树指纹；语义敏感操作走共享 helper（与 java 侧同一裁定）；`ExitMode` → 控制流异常族（I7 全量落实，子集内遇控制流节点 fail-fast 即可）。
- 依赖钉版依据（truffle 02 §二，Q5 已关闭）：`org.graalvm.truffle:truffle-api` + `truffle-dsl-processor` + `org.graalvm.polyglot:polyglot` 钉同一条 25.x LTS 线；Maven Central 25.2.4 实测 class major 61（Java 17）/ multi-release overlay 仅 versions/9 与 /21——JDK 21 兼容确认点已关闭，引入时常规冒烟复核即可（钉线内更新版本则重跑同口径检查）。
- `org.graalvm.*` 坐标仓内既有出现（live 核查）：`nop-frontend-support/nop-js/pom.xml`（`org.graalvm.polyglot:js-community` + `:polyglot` 23.1.2，active——GraalJS 嵌入先例，位于 nop-frontend-support **非 nop-kernel**）；`nop-kernel/nop-kernel-cli/pom.xml`（`org.graalvm.buildtools:native-maven-plugin`，构建插件，非 truffle/polyglot 依赖）；另有注释态出现（nop-dependencies/nop-spring-demo，不计数）。**不泄漏约束的正确口径**（设计 truffle 02 §二"内核其他模块" + roadmap 纪律 5 综合）：`nop-kernel` 域内 `<dependency>` 声明的 `org.graalvm.truffle:*` / `org.graalvm.polyglot:*` 只允许出现在 `nop-xlang-truffle/pom.xml`；nop-js（非内核，roadmap "Already shipped" 基线自认）与 `org.graalvm.buildtools` 构建插件为显式豁免。
- 编译基线 live：`nop-kernel/pom.xml` `maven.compiler.release=11`（根 pom 同）；JDK 21 `--release 11` 编译引用 truffle 25.x major-61 构件实测可过；若 dsl-processor 生成代码面需要更高语言级别，本模块 pom 覆盖 release（≥17）并在 log 记录裁定。
- `nop-kernel` 父 pom 现无 `nop-xlang-truffle` module 条目；mission.json commands 现状口径取决于 I2 是否已执行（本 plan 在既有口径上追加，见 Phase 1）。
- 无 SL 参考实现副本在仓内（`~/sources/graal` 为外部 sparse clone，只读参考，不入仓）。

## Goals

- `nop-xlang-truffle` 模块落盘：pom（truffle 三坐标钉版 25.x LTS + dsl-processor 注解处理器）+ 包结构 + 父 pom 注册；`org.graalvm.*` truffle/polyglot 依赖仅出现在本模块 pom（口径与豁免见 Current Baseline）。
- 钉版冒烟复核：所钉版本按 Q5 口径复核（class major / overlay 版本目录）+ 最小 polyglot 冒烟（空 Context/Engine 构建在 stock JDK 21 可运行）。
- XLangLanguage：id `xl`、无 parser（合成 Source，parse 查翻译缓存）、`NopException` 携带 SourceSection 回映射；以 EXCLUSIVE 过渡形态落地为翻译正确性对拍载体（SHARED 终态切换归 I8，注册结构按"形态 = 编译期常量"组织使 I8 切换不重构）。
- XLangContext：输出缓冲（`IEvalOutput`）线程绑定、本次求值全局作用域句柄；语言实例只存可共享数据（翻译缓存）。
- 帧/slot 映射：`LexicalScopeAnalysis` slot 布局 → `FrameDescriptor`/`FrameSlot`；kind 仅字面量/显式声明可推断处标注，推断不出保持 Object kind；帧访问模式按节点实际用法声明。
- 表达式子集翻译（与 I2 同子集：字面量/slot 标识符/算术/逻辑/比较/简单方法调用）：子集外节点 fail-fast（报节点类名 + SourceLocation）；语义敏感操作走共享 helper。
- 翻译缓存：键 = resourcePath + 树指纹；无 resourcePath 动态源按源内容哈希键（Phase 1 决策 D2 定稿）；缓存淘汰（容量上限/LRU）归 I8 不做。
- 对拍 truffle 列激活：corpus v1 表达式单元 truffle 列 vs 解释器列对拍全绿，含身份断言（翻译 AST 经 CallTarget 执行）。
- mission.json commands 在模块落盘的同一次变更中追加 `:nop-xlang-truffle`（"模块落盘即切换"裁定）。

## Non-Goals

- truffle 翻译覆盖 A/B 类别与覆盖矩阵（I6/I7）、两级内联缓存（I7）。
- 多线程运行时：Context 池、共享 Engine enter/leave 批求值、SHARED 形态与并发验证、缓存淘汰（I8）。
- 生产代码路径的后端注册 SPI 接入与路由（I9）——本 plan 的 truffle 列仅测试域接入 I1 harness。
- native image 兼容（I11；truffle 模块镜像排除机制同归 I11）。
- 与 nop-js 共享 Engine（一期不共享，设计 §八）。

## Scope

### In Scope

- 新模块落盘、truffle 依赖钉版与冒烟复核、commands 追加、不泄漏断言。
- XLangLanguage/XLangContext（EXCLUSIVE 过渡形态）、帧/slot 映射。
- 表达式子集翻译器 + fail-fast + 翻译缓存（含 D2 决策定稿）。
- 对拍 truffle 列接入（测试域）。

### Out Of Scope

- 同 Non-Goals。

## Execution Plan

### Phase 1 - 模块落盘、钉版与决策定稿

Status: planned
Targets: `nop-kernel/nop-xlang-truffle/`（新模块）、`nop-kernel/pom.xml`、`missions/xlang-execution-optimization.json`

- Item Types: `Decision | Proof`

- [ ] D1（形态决策，定稿）：本 plan 以 EXCLUSIVE 过渡形态落地为翻译正确性对拍载体（roadmap I5 验收显式允许）；SHARED 终态切换（`@Registration` 注解取值变更 + 重新编译）与池化并发验证归 I8。语言类注册结构按"形态 = 编译期常量"组织，I8 切换不重构。决策记入当日 log
- [ ] D2（无 resourcePath 动态源缓存形态，roadmap 委托本 plan 定稿）：按**源内容哈希键**入翻译缓存。理由：与"键 = resourcePath + 树指纹"防串用语义同构（不同内容自然分键，不依赖失效通知）；拒绝"由编译出口持有翻译产物"（需改所有动态编译出口签名，侵入面大，且出口统一化归 I9）。决策记入当日 log
- [ ] D3（共享 helper 依赖处理，并行轨道裁定）：truffle 翻译的语义敏感操作统一调用 nop-xlang 共享 helper 基座（共享 helper 纪律）；若执行时 I2 已落地则直接复用，若 I2 未落地则本 plan 落地**子集所需最小 helper 集**（定义在 nop-xlang；解释器全量改用与基座补齐仍归 I2，两 plan 不重复提取同一操作——先落地方为事实源）。决策记入当日 log
- [ ] 创建模块骨架：pom 钉版引入 truffle 三坐标（同一 25.x LTS 版本线）+ `truffle-dsl-processor` 注解处理器配置；依赖仅 `nop-xlang` 及传递依赖，禁止依赖 `nop-xlang-java`
- [ ] 父 pom modules 注册；mission.json commands 同一次变更追加 `:nop-xlang-truffle`（在既有口径模块集上追加——I2 已切换则成三模块口径，未切换则 `:nop-xlang,:nop-xlang-truffle`）
- [ ] 钉版冒烟复核（配方钉死）：抽查对象 = truffle-api 代表 class（如 `com.oracle.truffle.api.TruffleLanguage`）的 class major version（期望 61）+ 三坐标 jar 的 `META-INF/versions` 目录列表（期望仅 `versions/9` 与 `versions/21`）；若钉 25.x 线内更新版本，重跑同口径检查
- [ ] 最小 polyglot 冒烟：空 Context + Engine 构建在 stock JDK 21 可运行（语言注册发现归 Phase 2 验证，本 phase 仓内尚无语言类）
- [ ] 不泄漏断言（口径钉死）：断言/脚本扫描 `nop-kernel` 域内全部 pom 的 `<dependency>` 声明，`org.graalvm.truffle:*` / `org.graalvm.polyglot:*` 仅命中本模块；显式豁免项（nop-js 非内核、`org.graalvm.buildtools` 构建插件）在断言中记录为豁免而非命中——repo-observable

Exit Criteria:

- [ ] D1/D2/D3 三项决策记录 repo-observable（当日 log + 本 plan 勾选）
- [ ] `./mvnw compile -pl :nop-xlang-truffle -am` 通过（模块进 reactor；dsl-processor 在编译链配置生效——annotationProcessorPaths/处理器依赖 repo-observable，生成产物验证归 Phase 2）
- [ ] 冒烟复核记录落 log（版本号 + class major/versions 目录检查结果 + 空 Context/Engine polyglot 冒烟结果；如覆盖 release 基线亦记录）
- [ ] 不泄漏断言存在且通过（口径 = nop-kernel 域内 truffle/polyglot 依赖仅本模块 + 豁免清单）——roadmap I5 验收第二项
- [ ] mission.json commands 已追加且 live 可运行（逐条执行通过）
- [ ] No owner-doc update required（docs-for-ai 新模块开发指南同步归 I11）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - XLangLanguage/XLangContext 与帧/slot 映射

Status: planned
Targets: `nop-kernel/nop-xlang-truffle/`（语言/上下文/帧映射）

- Item Types: `Proof`

- [ ] XLangLanguage（EXCLUSIVE 形态注册，D1）：id `xl`、name `XLang`、defaultMimeType `application/x-xlang`；无 parser——`parse(ParsingRequest)` 查翻译缓存（合成 Source：路径 + 行号）；`isThreadAccessAllowed` 保持默认；`initializeMultipleContexts` 按 SHARED 要求的接口形态预留（实现可为 EXCLUSIVE 下的最小合规形态）
- [ ] XLangContext：每次 Context 持有输出缓冲（`IEvalOutput`，线程绑定，绝不可跨 Context 共享）与本次求值全局作用域句柄；语言实例只存可共享数据（翻译缓存位），节点不存 context 数据或运行时值
- [ ] 帧/slot 映射：`LexicalScopeAnalysis` slot 布局 → 每 RootNode 一份 `FrameDescriptor` + `FrameSlot`；kind 仅可推断处标注（字面量/显式类型声明），推断不出保持 Object kind（不虚构）；帧访问模式（READ/WRITE/MATERIALIZE）按节点实际用法声明
- [ ] `NopException` 语义：语言异常携带 SourceSection（合成 Source 回映射源位置），`.param()` 参数 host 侧可见

Exit Criteria:

- [ ] 语言注册可发现（polyglot `Value`/Engine 层可按 id `xl` 定位语言；dsl-processor 生成 provider 服务文件 repo-observable）
- [ ] 合成 Source 回映射有单测：同一节点异常的 SourceSection 回映射到与解释器一致的源位置（对拍第三层断言的前置）
- [ ] 帧/slot 映射有单测：slot 布局逐 slot 对应（数量/标识/kind 标注规则；子集内可推断比例记录落 log，全量覆盖率归 I6 后续实测）
- [ ] 无静默跳过：kind 推断不出的 slot 保持 Object kind 且不虚构类型（映射单测覆盖一例）
- [ ] **接线验证**：Language.parse → 翻译缓存查找/构建 → CallTarget 获取链路可被触发（最小冒烟树，此 phase 不要求子集全翻译）
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 表达式子集翻译、翻译缓存与对拍 truffle 列激活

Status: planned
Targets: `nop-kernel/nop-xlang-truffle/`（翻译器/缓存）+ `src/test/`（列接入）

- Item Types: `Proof`

- [ ] Executable 树 → Truffle AST 纯函数翻译器：子集（字面量/slot 标识符/算术/逻辑/比较/简单方法调用，与 I2 同子集）逐节点类翻译；语义敏感操作 generic 路径走共享 helper（D3 口径）；子集外节点 fail-fast（报节点类名 + SourceLocation，不部分翻译）
- [ ] 每编译单元一个 `RootNode` + `CallTarget`（JIT 编译粒度）；`ExitMode` 相关控制流节点不在子集内，遇到即 fail-fast（全量落实归 I7）
- [ ] 翻译缓存：键 = resourcePath + 树指纹（resourcePath 单元）；无 resourcePath 动态源按源内容哈希键（D2）；language 实例作用域；缓存本体不做淘汰（I8）
- [ ] 以 I1 harness 注册 truffle 列（test-jar 依赖）：静态 + 动态单元均适用；身份断言 = 翻译 AST 经 CallTarget 执行（非解释器树）
- [ ] corpus v1 表达式单元 truffle 列 vs 解释器列对拍全量执行（三层断言 + 身份断言 + java 列缺席显式记录——动态单元 java 列为"不适用"而非"缺席"，按 I1 列适用性机制区分）

Exit Criteria:

- [ ] corpus v1 表达式单元 truffle 列 vs 解释器列对拍全绿（EXCLUSIVE 过渡形态；含身份断言）——roadmap I5 验收第一项
- [ ] 翻译缓存键语义有单测：同 resourcePath 不同树指纹分键不串用（构造两棵不同树断言两次翻译/两个 CallTarget 或等效证据）
- [ ] fail-fast 有测试：子集外节点翻译报错且错误信息含节点类名与 SourceLocation
- [ ] **端到端验证**：树 → 翻译器 → Truffle AST → CallTarget 执行 → 三层对拍断言全链可运行（stock JDK 21 形态）
- [ ] **接线验证**：truffle 列身份断言通过即证明翻译 AST 真实经 CallTarget 执行（非解释器兜底）
- [ ] `./mvnw test -pl :nop-xlang-truffle -am` 全绿
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 表达式 corpus truffle 列 vs 解释器列对拍全绿（允许 EXCLUSIVE 过渡形态；含身份断言 = 翻译 AST 经 CallTarget 执行）——roadmap I5 验收第一项
- [ ] `org.graalvm.*` truffle/polyglot 依赖仅出现在本模块 pom（不泄漏断言通过，口径与豁免见 Phase 1/Current Baseline）——roadmap I5 验收第二项
- [ ] mission.json commands 已按"模块落盘即切换"追加且逐条可运行——roadmap I5 验收第三项
- [ ] D1/D2/D3 决策定稿并记录（roadmap 委托项闭合）
- [ ] 回归不允许削弱现解释器测试（纪律 3）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] owner-docs：No owner-doc update required（docs-for-ai 同步归 I11）
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] Anti-Hollow Check：truffle 列真实经 CallTarget 执行（身份断言）；无空方法体/静默跳过/no-op
- [ ] `./mvnw compile -pl :nop-xlang-truffle -am`
- [ ] `./mvnw test -pl :nop-xlang-truffle -am -T 1C`
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### SHARED 终态切换与池化并发验证

- Classification: `out-of-scope improvement`（按设计两步走裁定的责任分工，非缺陷延期）
- Why Not Blocking Closure: 设计 truffle 02 §五裁定"翻译正确性（EXCLUSIVE 形态）与共享正确性（SHARED+池）分两步验证"；I5 验收显式允许 EXCLUSIVE 过渡形态。SHARED 切换、Context 池租借协议、并发对拍与缓存淘汰均属 I8 范围。
- Successor Required: yes
- Successor Path: `ai-dev/plans/xlang-execution-optimization/`（I8 plan 起草时引用本条）

### 翻译缓存淘汰（容量上限/LRU）

- Classification: `optimization candidate`
- Why Not Blocking Closure: roadmap I8 范围显式含"翻译缓存淘汰（容量上限/LRU）"；I5 只交付缓存本体与键语义，无淘汰策略不影响正确性（缓存只增不淘汰在测试域无碍）。
- Successor Required: yes
- Successor Path: 同上（I8）

## Non-Blocking Follow-ups

- Q1（Bytecode DSL 重评估）/ Q4（与 nop-js 共享 Engine 重评估）维持 watch-only，触发口径量化归 I12（设计 §九既定归属）。

## Closure

Status Note: <<完成或关闭时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<task id / daily log link / findings 摘要>>

Follow-up:

- <<SHARED/池/缓存淘汰归 I8；Q1/Q4 watch-only 归 I12；其余 no remaining plan-owned work>>

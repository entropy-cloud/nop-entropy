# W4-audit round-1：roadmap workitem 全集独立审计

> Verdict: PASS（0 P0/P1）
> Date: 2026-08-20
> Reviewer: fresh-session audit sub-agent（Task tool 派发；task id：ses_fe538d7ceffeT2ljhmyr1y8beI）
> Methodology: openAuditPrompt（ai-dev/skills/open-ended-adversarial-review-prompt.md）+ W4 plan 固化口径
> 审计对象：`ai-dev/backlog/xlang-execution-optimization-roadmap.md` 阶段二定稿块（I1-I12 全集 L44-86、定稿口径块 L36-42、Stages 表 L122-143、依赖图 L145-158）

## 一、五维度逐维度结论

### 维度 1：粒度 — PASS

- **结论**：12 条目均达"单 plan 可完成（约 5-15 文件 / 200-500 行 / 1-4 phases 量级）"；无超粒度未拆项、无欠粒度漏并项。
- **证据**：
  - 全覆盖条目已按设计 java §三分类表切分为覆盖 A/B 两半（I3/I4 java 侧、I6/I7 truffle 侧；roadmap L52-57/L62-66），拆分口径记录于定稿口径块 L40 与 `ai-dev/logs/2026/08-19.md` "EXECUTE W3-supplement" 拆并裁定（旧 I2 三拆、旧 I4 三拆，理由=137 节点全覆盖超单 plan 粒度）。
  - 加载集成从全覆盖中独立（I10 依赖 I9，依赖链显式化），对拍框架从 java 骨架中独立（I1 可与 I2-I4 并行、不阻塞 truffle 线），均为正确的欠粒度拆出（roadmap L44/L48/L58/L74；log 拆并裁定第 1/2 条）。
  - 上界观察（非缺陷）：I2（模块骨架+子集转译+共享 helper 基座+EvalMethod 约定+包装器契约+mission.json 切换，L48-51）、I5（模块骨架+Language/Context+帧映射+子集翻译+翻译缓存，L58-61）、I11（构建集成+native 兼容+docs 同步，L78-81）为多交付面复合条目，处于量级区间上沿，但均可在 1-4 phases 内组织（见 Findings P2-2 注意项）。

### 维度 2：依赖图无环且与 Stages 表一致 — PASS

- **结论**：mermaid 图（L147-158）、Stages 表 Deps 列（L124-143）、条目依赖声明（L44-86）三处逐边一致（共 19 条工作边 + 2 条里程碑汇入边），图无环。
- **证据**（逐边对照，三处均一致）：
  - W1→W2→W3→W4（mermaid L149 = Stages 2/3/4 Deps = W2/W3/W4 条目声明 L29-31）
  - W4→I1（L151 = Stages 5 Deps "W4-audit" = I1 声明 L44）
  - I1→I2→I3→I4（L152 = Stages 6/7/8 = L48/L52/L55）
  - I1→I5→I6→I7→I8（L153 = Stages 9/10/11/12 = L58/L62/L64/L67；I5 声明"可与 I2-I4 并行"为显式非依赖注记，图中无对应边，一致）
  - I4→I9、I8→I9（L154-155 = Stages 13 "I4, I8" = L70）
  - I9→I10→I11→I12（L156 = Stages 14/15/16 = L74/L78/L82；I12"传递覆盖 I1-I10 全链"经 I9 汇合点拓扑成立：I1-I10 均为 I11 祖先）
  - 里程碑：W4→M1（L150 = ★ L32 "派生：W1-W4"）、I12→M2（L157 = ★ L86 与 Stages ★ 行 "I1-I12"；I1-I12 均为 I12 祖先，派生规则成立）
- **无环**：拓扑序存在（W1<W2<W3<W4<I1<{I2,I5}<…<I9<I10<I11<I12，I9 为双链汇合点），全图 DAG。
- 附带核对：Critical path 列存在 bold/plain "Yes" 混用且无图例（Stages 6-8 为 plain），语义歧义见 P2-1（非依赖错误；truffle 链最长已记录于 W3 log "EXECUTE W3-supplement" 验证段）。

### 维度 3：验收标准可验证 — PASS

- **结论**：12 条目验收均 repo-observable（指向可核查的测试/矩阵/清单/pom/mission.json/基准产物），对拍不变式断言按定稿口径 L38 落实，两类例外口径（I9 显式引用、I12 直接运行）均已显式声明。
- **证据**：
  - 对拍不变式落实：身份断言（I2 "java 列身份断言=生成类实例" L50；I5 "truffle 列身份断言=翻译 AST 经 CallTarget 执行" L60；I10 绑定身份断言 L76；I11 身份断言 L80；I12 三列身份断言 L84）；三层断言/列适用性/列缺席记录/差异注入自检（I1 L46）；单元级降级判 FAIL（I9 路由场景矩阵 "单元级降级判 FAIL" L72）。
  - 例外口径落实：I9 验收 "**以 I1 对拍框架运行结果为验收输入**（显式引用关系：场景用例经框架执行与断言，非独立重写比对）"（L72）；I12 验收 "全量三后端对拍套件**直接运行**全绿（……——不得以引用形式弱化）"（L84）。
  - repo-observable 抽样：I5 "org.graalvm.* 依赖仅出现在本模块 pom（不泄漏断言）"（L60，pom 可核查）；I2/I5/I12 mission.json commands 切换/追加/汇总核验（L50/L60/L83，repo 内文件）；I4/I7 覆盖矩阵 "exec/ 137 文件基线逐节点类注册断言，新增节点类红灯"（L57/L66，live 基线实测 137 见 §五）；I10 "测试以转译器 API 合成双清单产物，生产管线产物归 I11"（L76，测试边界显式）；I11 "构建管线漏跑可观测断言…重生成幂等断言"（L80）；I12 "基准数据落 repo（报告 + 可复跑入口）"（L84）。
  - 定稿口径块 L38 对 execution 01 §五的转述准确（三层断言/列适用性/身份断言/列缺席/单元级降级 FAIL 逐项对应 execution 01 L109-121）。

### 维度 4：复用标注准确 — PASS

- **结论**：8 个有复用行的条目（I1/I2/I5/I9/I10/I11/I12）复用标注与 live 锚点、Framework/platform reuse 表（L98-108）、定稿设计先例一致，无错误标注；无复用行的 5 条目（I3/I4/I6/I7/I8）裁定见 §三 Minor 2（非缺陷）。
- **证据**：全部 live 锚点实测存在（§五）；reuse 表 7 行与条目复用行交叉一致（如 reuse 表 "脚本引擎注册先例 ScriptCompilerRegistry → I9" = I9 复用行 L73；"模型缓存 RCM → Java 后端加载挂点" = I10 复用行 L77；"GraalVM 配置生成 → I11 native 兼容复用" = I11 复用行 L81）；I2 复用行对 nop-javac 的"仅可选诊断性编译校验（不承担产物编译）"限定与设计 java 01 §二裁定（L33）一致。

### 维度 5：与定稿设计无冲突 — PASS

- **结论**：I1-I12 逐条目对照三组 architecture-baseline（execution 01 / java 01 / truffle 02）范围/依赖/验收，无实质冲突；设计文档旧 I 编号按 L42 映射表解读后全部语义正确（抽验记录见 §四）。
- **证据**（逐条目锚点）：
  - I1 ↔ execution 01 §五（harness 矩阵化/三层断言/身份断言/列适用性/单元级降级 FAIL）；I1 落点"由实现 plan 按模块依赖方向合法性定"不与 §二模块边界冲突。
  - I2 ↔ java 01 §三（共享 helper）/§四（SourceLocation 静态常量）/§七（EvalMethod 约定 static + 首参 `$scope`、xpl/xlib 追加 `$out` 隐参、纯表达式单元走 `EvalMethodInvoker`）；设计 §七"包装器契约随 I1（旧）定稿"按映射落 I2 范围（I2 ∈ 旧I1→{I1,I2}），时序语义保持（I2 紧随 I1、先于需要它的 I3/I4）。
  - I3/I4 ↔ java 01 §三分类表 + 覆盖矩阵 + fail-fast；I4 ExitMode 传播边界不变式、可变 slot 闭包 cell 契约均见于 java §三（L50/L63）。
  - I5 ↔ truffle 02 §二（25.x LTS 钉版 + Q5 冒烟复核）/§三（id `xl`、SHARED 终态、EXCLUSIVE 过渡两形态各自是验证载体）/§四（帧/slot 映射、kind 仅可推断不虚构）/§七（缓存键=resourcePath+树指纹、无 resourcePath 形态归 plan）。
  - I6/I7 ↔ truffle 02 §四/§七同构覆盖策略（"与 java 后端同构，节点基线同为 exec/ 137 文件"）；I7 两级内联缓存 ↔ 02 §六（一级 CallTarget 身份/二级直达形态、禁函数实例与运行时值身份）。
  - I8 ↔ truffle 02 §五（池租借协议/共享 Engine/SHARED 并发验证载体/enter-leave 窗口语义）；设计 02 §五"纳入 I4（旧）验收"按映射落 I8 验收（I8 ∈ 旧I4→{I6,I7,I8}，W3 log 显式裁定）。
  - I9 ↔ execution 01 §三（决策树/单跳降级/配置开关/不设全局默认后端）+ §四（显式注册表/@GlobalInstance/不做 classpath 扫描）；"动态路径含单元级翻译失败第三分支"为 R5-4 裁定落点，语义可由 execution 01 §五单元级降级规则推出，非冲突而是操作化补全。
  - I10 ↔ java 01 §五（绑定决策树/树指纹施加对象=Executable 树防 Delta 漏检/绑定结果随 ComponentCacheEntry 缓存）+ execution 01 §三（清单成员资格判定、清单外不记降级）。
  - I11 ↔ java 01 §六（任务输入/双清单分离产物/相同编译前端取树/幂等/不绕过 codegen）+ execution 01 §三/§六；扫描口径"xpl/xlib 确定 + 其余按 live 清点定稿、不引入 live 不存在类型"= round-2/4/5 裁定口径（废弃 `.expr`/`.xbiz` 幻影枚举）。
  - I12 ↔ truffle 00 §三.2 + 02 §五 Q2/§八-§九（池成本实测、Q1/Q4 watch-only 量化触发口径）；机会成本量化 = handover #15 落点。
  - 横切：纪律 5（L166）依赖白名单与 execution 00 §六/01 §二、truffle 02 §二一致，且已补记 `nop-xlang-java → nop-javac` 构建期可选诊断边（handover #16 落点，与 java 01 §二/execution 01 §二虚线边一致）。

## 二、Findings

### [R1-1] Stages 表 Critical path 列 bold/plain 混用无图例

- **严重程度**: P2
- **现状**: `ai-dev/backlog/xlang-execution-optimization-roadmap.md:132-134`（Stages 6-8，java 链 I2/I3/I4）使用 plain "Yes"，L131/L135-142 使用 "**Yes**"；列头无图例说明两种标记的语义差异。实际最长路径为 truffle 链（W3 log "EXECUTE W3-supplement" 验证段记录"truffle 链最长"），java 链不在最长路径上。
- **风险**: 读者无法判断 "Yes" 与 "**Yes**" 是否有意区分（是——bold=最长路径成员、plain=次级汇入链），可能误读关键路径；纯表述层，无依赖语义错误。
- **建议**: roadmap 后续修订时补一行图例（或统一为 "Yes（最长路径）/Yes（汇入链）"）。
- **信心水平**: 确定

### [R1-2] I2 范围含"模板入口包装器契约定稿"但 I2 验收未显式覆盖

- **严重程度**: P2
- **现状**: `ai-dev/backlog/xlang-execution-optimization-roadmap.md:49`（I2 范围含"模板入口包装器契约定稿（xpl/xlib 追加 `IEvalOutput $out` 隐参，设计 java §七）"）；L50（I2 验收仅覆盖表达式子集 corpus，不经 `$out` 输出通路）。handover #9 原要求"W3 确认对应 I 项验收标准包含之"，实际落点是范围行而非验收行。
- **风险**: 包装器契约的正确性验证传递性依赖 I4（输出族 corpus 全类别对拍含模板单元，L57）；若 I4 plan 起草时未意识到这一传递责任，包装器通路可能缺直接验证。不构成验收缺口（I4 验收语义已覆盖），但责任链是隐式的。
- **建议**: I2/I4 plan 起草时显式记账：I2 定契约、I4 验收覆盖契约执行路径（corpus 全类别中含模板单元样例）。
- **信心水平**: 很可能

### [R1-3] W3 log 移交处置压缩清单计数不匹配（14 源 13 目标）

- **严重程度**: P2
- **现状**: `ai-dev/logs/2026/08-19.md:30`："#2/#3/…/#15 已分别落入 I11/I1/I8/I8/I8+I10/I11/I5/I11/I11/I9/I9/I5/I12"——源 14 项、目标 13 个，1:1 对应关系存在转录笔误。
- **风险**: 无实质风险：本审计逐项复核 round-5 移交清单 17 项（#1/#17 见 log finding 段，#2-#16 逐一对照 roadmap 条目范围/验收），**全部包含**（#2→I11 范围；#3→I1 范围；#4→I8 范围+验收；#5→I8 范围+验收；#6→I5+I8 范围；#7→I8/I9/I10（单元级翻译失败观测+第三分支+事件分级）；#8→I11 范围；#9→I2+I5 范围；#10→I11 范围；#11→I11 范围；#12→I9 范围；#13→I9 范围；#14→I5 范围；#15→I12 范围；#16→纪律 5 L166；#1/#17→见 log finding 段与本报告 §四）。log 为历史过程记录，笔误不影响 roadmap 可执行性。
- **建议**: 不改历史 log；本报告记录即为逐项复核证据。后续 log 摘要引用该清单时以本报告逐项对照为准。
- **信心水平**: 确定

### [R1-4] 设计文档 ~27 处旧 I 编号引用（冻结不改，已由映射表缓解）

- **严重程度**: P2
- **现状**: 5 篇正文 + README + knowledge 层共约 27 处旧 I 编号引用（分布见 §四抽验记录），全部可按 L42 映射表正确解读（抽验 5/5 映射分支通过）。设计文档按 W2 round-5 PASS 轮裁定冻结不改，修订归属"下次设计文档修订"。
- **风险**: 未读过 L42 映射表的读者直接按字面 I 编号检索 roadmap 会错位（如设计说"I6 扫描口径"实际是定稿 I11）。缓解已到位（映射表位于 roadmap 定稿口径块，W4-audit 口径强制按映射解读）。
- **建议**: 维持既定归属（下次设计文档修订时统一换算为新编号并加注映射说明）。
- **信心水平**: 确定

（本轮 0 P0、0 P1、4 P2；P3 已按口径归入 P2。）

## 三、W3 移交 3 项 Minor 逐项裁定

1. **I6 无独立范围行（标题引用 I3 类别）— 裁定：确认非阻塞（可接受的类别同构引用）**
   依据：引用链完整可解析——I6 标题"truffle 翻译覆盖 A（数据与作用域族，类别同 I3）"（L62）→ I3 范围"作用域链访问/类型操作/对象集合构造访问/绑定守卫调试/slot 写族（类别划分=设计 java §三分类表）"（L53）→ java 01 §三分类表（L41-52）逐类别定义。I6 相对 I3 类别无超出范围的新增交付（对照 I7："类别同 I4"但因新增两级内联缓存而有独立范围行 L65），故引用即完整定义；粒度证据与 I3 同基线（同一 exec/ 137 文件分类），无证据缺口。改进建议（P2 性质，可选项）：roadmap 后续修订时补一行显式范围行以与其他条目对称。

2. **I3/I4/I6/I7/I8 无复用行 — 裁定：确认非阻塞（不构成复用标注缺失缺陷）**
   依据：(a) 这 5 条目的实质复用物（共享语义 helper）已由定稿口径块"共享 helper 纪律"（L41，全条目适用）+ 各自来源（I2 范围"共享语义 helper 基座…定义在 nop-xlang" L49）统一覆盖，I7 范围更含显式"语义敏感操作 generic/fallback 走共享 helper"（L65）；(b) Stages 表 Reuse 列已为这些条目承载复用信息（Stage 7 "共享 helper"、Stage 11 "共享 helper"、Stage 12 "知识文档 §五"，L133/L137/L138）；(c) 复用标注准确维度的缺陷形态是"标错了"或"指向不存在的锚点"，本案是"未在条目层重复标注但信息存在于口径块与 Stages 表"，无错误声明、无锚点漂移。改进建议（P2 性质，可选项）：roadmap 后续修订时为 I3/I4/I6/I7 补"共享 helper 基座（I2 产物）"、为 I8 补"知识文档 §五"，与 Stages Reuse 列对齐。

3. **冻结设计文档约 30 处旧 I 编号引用（映射充分性）— 裁定：映射表充分，缓解成立（W2 round-5 移交 #17 的处置落点经本审计落实）**
   依据：见 §四抽验记录——实际清点约 27 处（"约 30"为约数，成立），覆盖 5 个被行使的映射分支（旧 I1/I3/I4/I6/I7），逐处按映射解读后语义全部正确；未被行使的 2 个分支（旧 I2→I3+I4+I10、旧 I5→I9）在 xlang 设计文档中无对应引用（grep 无匹配），不存在误读面。映射表与 W3 log 拆并裁定记录逐条一致，且 12 个定稿条目被 7 个旧编号的展开完备、不重叠地覆盖（2+3+1+3+1+1+1=12）。另核验冻结完整性：设计文档中无 I8-I12 新编号引用（grep 零匹配），证明 W2 PASS 后文档未被部分更新，旧编号体系自洽。

## 四、编号映射表抽验记录

映射表（roadmap L42）：旧 I1→I1+I2；旧 I2→I3+I4+I10；旧 I3→I5；旧 I4→I6+I7+I8；旧 I5→I9；旧 I6→I11；旧 I7→I12。

**引用清点**（grep `\bI[1-7]\b`，排除无关的 nop-stream 设计文档同形编号）：共约 27 处——java 01（4 处：L34/L102/L106 旧I6，L114 旧I1）、execution 00（4 处：L14/L50/L52/L53 旧I7）、execution 01（2 处：L53/L135 旧I6）、truffle 02（11 处：L29/L162/L166 旧I3，L117/L152/L158/L159/L167/L168 旧I7，L118 旧I4，L138 旧I3+旧I4）、truffle 00（3 处：L12/L27 旧I7，L28 旧I4）、truffle README（1 处：L13 旧I7）、01-truffle-knowledge（2 处：L253 旧I7，L256 旧I3）。另核验：文档中零处 I8-I12 引用（冻结完整）。

**抽样点清单（10 处抽样，覆盖 5 个被行使映射分支 + 4 份不同文档）**：

| # | 设计文档位置 | 旧编号引用内容 | 映射解读 | 定稿条目语义核对 |
|---|---|---|---|---|
| 1 | java 01:114 | "包装器契约随 I1 对拍框架定稿" | 旧I1→I1+I2 | I2 范围含"模板入口包装器契约定稿"（L49）✓（W3 log 显式裁定落 I2；时序语义保持） |
| 2 | execution 01:53 | "其余 `_vfs` XDSL 类型是否纳入编译单元由 I6 按 live 清点定稿" | 旧I6→I11 | I11 范围含"扫描口径：…其余 `_vfs` XDSL 类型按 live 清点定稿并显式记录纳入/排除及理由"（L79）✓ |
| 3 | execution 01:135 | "GraalVM native image 兼容…`GraalvmConfigGenerator` 管线，归属 roadmap I6" | 旧I6→I11 | I11 范围含"native image 兼容（`GraalvmConfigGenerator` 管线复用…）"（L79）+ 复用行 L81 ✓ |
| 4 | java 01:102 | "由 I6 实现时按 live 清点定稿并显式记录纳入/排除及理由" | 旧I6→I11 | 同 #2，I11 范围逐字对应 ✓ |
| 5 | truffle 02:29/162 | "I3 引入所钉版本时做常规冒烟复核"（Q5） | 旧I3→I5 | I5 范围含"25.x LTS 钉版引入 + 常规冒烟复核——Q5 确认点已关闭"（L59）✓ |
| 6 | truffle 00:28 / truffle 02:118 | "SHARED 形态并发正确性验证…纳入 I4 实现计划验收" | 旧I4→I6+I7+I8 | I8 范围"SHARED 形态并发正确性验证载体"+ 验收"SHARED 形态并发对拍…全绿"（L68-69）✓（I8 ∈ 映射像集；W3 log 裁定落 I8） |
| 7 | truffle 02:138 | "无 resourcePath 动态源缓存形态…具体形态归 I3/I4 实现计划定稿" | 旧I3→I5；旧I4→I6+I7+I8 | I5 范围含"无 resourcePath 动态源缓存形态由 plan 定稿"（L59）✓（落点 I5 ∈ 旧I3 像集；旧I4 像集为覆盖/多线程项，不承载该细节，像集并集包含实际落点，无误导） |
| 8 | truffle 02:117/159 | "Context 创建/销毁成本实测与池大小调优归 I7 基准" | 旧I7→I12 | I12 范围含"Context 池创建/销毁成本与池大小调优实测"（L83）✓ |
| 9 | truffle 02:152/158/167/168 | Q1/Q4 watch-only 重评估触发口径归 I7 | 旧I7→I12 | I12 范围含"Q1/Q4 watch-only 重评估触发口径量化定义"（L83）✓ |
| 10 | execution 00:14/53 | "量化数值由性能基准计划（roadmap I7）落定" | 旧I7→I12 | I12 = 性能基准 + 收口条目 ✓ |

**映射充分性结论**：充分。(a) 全部 27 处旧引用分布在 7 份文档，抽样 10 处覆盖全部 5 个被行使分支与 4 份文档，解读后语义全部正确；(b) 未行使分支（旧I2/旧I5）无引用面，无误读可能；(c) 映射表与 W3 log 拆并裁定逐条一致，像集对 I1-I12 完备划分；(d) 唯一的粒度损失是"旧编号→像集"为超集映射（如 #7 需在像集内定位实际落点），但各实际落点均在 W3 log 或 roadmap 条目文本中显式可查，不构成解读障碍。

## 五、live 复用锚点复核

| 锚点 | 声明位置（roadmap） | live 实测 | 结果 |
|---|---|---|---|
| `ScriptCompilerRegistry`（nop-xlang script/） | I9 复用 L73；reuse 表 L107 | `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/script/ScriptCompilerRegistry.java` | 存在 ✓ |
| `ResourceComponentManager`（nop-core component/） | I10 复用 L77；reuse 表 L103 | `nop-kernel/nop-core/src/main/java/io/nop/core/resource/component/ResourceComponentManager.java` | 存在 ✓ |
| `JaninoScriptCompiler`（nop-xlang janino/） | I2 复用 L51；reuse 表 L104 | `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/janino/JaninoScriptCompiler.java` | 存在 ✓ |
| `EvalMethodInvoker`（nop-core reflect/impl/） | I2 复用 L51（经 java 01 §七） | `nop-kernel/nop-core/src/main/java/io/nop/core/reflect/impl/EvalMethodInvoker.java` | 存在 ✓ |
| `JdkJavaCompiler`（nop-javac） | I2 复用 L51（"仅可选诊断"）；reuse 表 L104 | `nop-kernel/nop-javac/src/main/java/io/nop/javac/jdk/JdkJavaCompiler.java` | 存在 ✓ |
| `GraalvmConfigGenerator`（nop-codegen graalvm/） | I11 复用 L81；reuse 表 L105 | `nop-kernel/nop-codegen/src/main/java/io/nop/codegen/graalvm/GraalvmConfigGenerator.java`（同目录 11 个 graalvm/ 类） | 存在 ✓ |
| `nop-vfs-index.txt` | I11 复用 L81 | 12 个模块 resources 下存在（如 `nop-auth/nop-auth-app/src/main/resources/nop-vfs-index.txt`）——管线产物形态，与"管线复用"声明一致 | 存在 ✓ |
| `LexicalScopeAnalysis`（nop-xlang compile/） | I5 复用 L61；reuse 表 L102 | `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/compile/LexicalScopeAnalysis.java` | 存在 ✓ |
| `nop-benchmark/nop-benchmark-xpl` | I12 复用 L85；reuse 表 L142 | `nop-benchmark/nop-benchmark-xpl/pom.xml`（模块存在） | 存在 ✓ |
| exec/ 137 文件基线 | 定稿口径 L39；I4/I7 验收 L57/L66 | `ls nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/ \| wc -l` = **137** | 计数精确 ✓ |
| Nop AutoTest 机制（I1 复用 L47 补充锚点） | I1 复用 L47 | `nop-autotest/` 模块（nop-autotest-core/junit/dbtool 三子模块） | 存在 ✓ |
| `IResourceLoadingCache`（I10 复用 L77） | I10 复用 | `nop-kernel/nop-core/src/main/java/io/nop/core/resource/cache/IResourceLoadingCache.java` | 存在 ✓ |
| `~/sources/graal` SL 参考实现（I5 复用 L61；外部锚点） | I5 复用 L61 | `~/sources/graal/truffle/` 存在（sparse clone） | 存在 ✓ |

附带复核：`missions/xlang-execution-optimization.json` live commands 仅引用 `:nop-xlang`（已存在模块），与 roadmap L30 "live commands 在落盘前保持仅引用已存在模块"声明一致 ✓；`ai-dev/plans/xlang-execution-optimization/` 下仅 W1-W4 四份 plan，无 I 系列 plan——冻结纪律 2（L163）遵守中 ✓。

## 六、依赖一致性核对

三处逐边对照结果见 §一维度 2（19 条工作边 + 2 条里程碑汇入边，mermaid L147-158 / Stages Deps L124-143 / 条目声明 L44-86 三处逐边一致，含 I9 双前驱 I4+I8、I12 传递覆盖声明、★ 里程碑派生规则 W1-W4 与 I1-I12）。**无环结论**：全图 DAG——拓扑分层 W1<W2<W3<W4<I1<{I2,I5}<{I3,I6}<{I4,I7}<I8<I9<I10<I11<I12（I9 汇合点处 java 链 5 跳 / truffle 链 6 跳，无交叉回边、无自环）。里程碑派生核验：M1 前驱集={W1,W2,W3,W4} 完备；M2 经 I12 祖先传递覆盖 I1-I12 完备（I5-I8 经 I8→I9、I2-I4 经 I4→I9、I1 经双链首）。

## 七、移交后继清单

| # | 发现 | 归属 | 说明 |
|---|---|---|---|
| 1 | 设计文档 ~27 处旧 I 编号引用换算为新编号（含 truffle README:13、01-truffle-knowledge:253/256 两处易漏点） | 下次设计文档修订 | round-5 P2 既定归属；冻结期内由 L42 映射表承担可读性，修订时统一换算并加映射注记 |
| 2 | round-5 遗留 6 项 P2（R5-1 判定时机计数口径、R5-2 vision 依赖枚举补 nop-javac 可选边等） | 下次设计文档修订 | 既定归属，非本次范围；其中 R5-2 的纪律 5 补边部分已在 roadmap L166 落地 |
| 3 | handover #1：design-writing-guide 增补条款（外部知识参考层/委托式 Vision） | design-writing-guide 修订 | 既定移交（R1-10/R1-11/R2-9/R4-5/R4-6/R5-5 根治），独立于本 roadmap |
| 4 | P2-1（Critical path 列 bold/plain 无图例）、P2-3（I3/I4/I6/I7/I8 补复用行）、P2-4（I6 补范围行） | roadmap 后续修订 | 三项均为表述对称性改进，可合并为一次小修订；不阻断任何 I 系列 plan 起草 |
| 5 | P2-2（I2 定包装器契约、I4 传递验证责任链显式化） | I2/I4 系列 plan 起草时 | 解冻后起草 I2/I4 plan 时在验收设计里显式记账（I4 corpus 含模板单元样例覆盖 `$out` 通路） |
| 6 | live 代码发现 | 无 | 本轮未发现 live 代码缺陷；全部复用锚点存在、exec/ 计数精确、mission.json commands 与声明一致 |

## 八、遗留 P2 裁定表（verdict=PASS 时必备）

| P2 编号 | 内容摘要 | 裁定归属 | 理由 |
|---|---|---|---|
| R1-1 | Stages Critical path 列 bold/plain "Yes" 混用无图例（java 链非最长路径但标 Yes） | roadmap 后续修订 | 纯表述歧义，依赖语义三处一致无错；W3 log 已记录"truffle 链最长"事实，不误导执行 |
| R1-2 | I2 范围"包装器契约定稿"未入 I2 验收，验证责任隐式传递至 I4 | I2/I4 系列 plan 起草时 | I4 验收"全类别 corpus 对拍全绿"语义上已覆盖输出族/模板通路，无验收缺口，仅责任链需 plan 层显式化 |
| R1-3 | W3 log 移交处置压缩清单 14 源 13 目标转录笔误 | 不改历史 log；以本报告 §三/§七逐项复核为准 | 逐项语义复核 17/17 包含，笔误无实质后果；历史过程记录保持原样 |
| R1-4 | 设计文档 ~27 处旧 I 编号引用（冻结不改） | 下次设计文档修订 | round-5 PASS 轮既定裁定；映射表已充分缓解（本报告 §四实证）；修订时统一换算 |

---

**总评**：W3-supplement 定稿的 I1-I12 在五个强制维度上全部达标。定稿口径块（对拍不变式/覆盖矩阵/粒度/共享 helper/编号映射）与三组冻结设计逐项对应无冲突；三处依赖表述逐边一致且无环；全部复用锚点 live 存在（exec/ 137 计数精确）；W2 round-5 移交 17 项全部有落点；3 项 W3 Closure Minor 逐项裁定非阻塞。4 项 P2 均为表述/对称性改进，不阻断阶段二解冻。

**盲区自评**：本轮为文档级审计，未对实现可行性做代码级深挖（如 `EvalRuntime`/`ExecutableFunction` 的字段事实——已由 W2 round-3/round-5 以 live 复核背书，本轮未重复）；I2/I5/I11 的"量级上沿"判断基于条目文本拆解而非真实 diff 估算，实际执行若超粒度应在对应 plan 的 draft review 中拦截；对拍 harness 与 Nop AutoTest 机制的对接细节（用例参数矩阵化的具体 API 形态）留给 I1 plan。

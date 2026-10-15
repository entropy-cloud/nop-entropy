# 2465 XDBG D5 前端架构设计与语法方案 spike（roadmap item 6）

> Plan Status: completed
> Last Reviewed: 2026-10-16
> Source: `ai-dev/backlog/xlang-web-debugger-roadmap.md` Work Item 6（Stage detail "6. D5 前端架构设计 + 语法方案 spike"）；`00-overview.md` §一.7/§3.6
> Related: plans/2460-2464（均 done）

## Purpose

产出两份交付物：①`05-frontend-architecture.md`（flux 侧扩展架构：新包结构、与 flux-code-editor/renderer registry 集成、语言注册开放化方案）；②`06-grammar-spike.md`（语法三来源 POC 结论与裁定，含构建链引入评估）。POC 代码留存 `_tmp/xlang-lezer-spike/`（不入主分支）。

## Current Baseline

- **flux 仓（已核验，worktree `/Users/abc/app/nop-chaos-flux-wt/nop-chaos-flux-master`，可构建）**：`flux-code-editor` 的 `EditorLanguage` 为 12 成员封闭联合（types.ts L3-15）；`createLanguageExtension` 硬编码 switch（base.ts L60-88）+ 平行的 `getLanguageParser` switch（L106-133）；总装在 `createBaseExtensions`（L170-249，expression+template 模式走 `createTemplateModeExtension`）。依赖含 9 个 @codemirror/lang-* + @lezer/highlight；**@lezer/generator 全仓不存在**（pnpm-lock 零命中）。库包级 build = 纯 tsc（无 bundler 代码步；flux-bundle 为 vite 打包、playground 走 vite dev），turbo 编排，vitest workspace 聚合。
- **可复用先例**：extensions/expression/template-mode 的 StreamLanguage 手写 `${...}` tokenizer（xpl 文本插值直接同构）；语言分支先例 = 官方 lang 包直调（`case 'yaml': return yaml()`）；`use-code-mirror` 的 extensions compartment 注入点；playground `code-editor-page.tsx` 6 语言演示页（13 槽位去重；EditorLanguage 12 成员的子集——xlang 演示页模板）；renderer 注册 = `codeEditorRendererDefinition` + 手动 register。
- **跨仓交付现状**：`flux-bundle`（@nop-chaos/flux）目前**不包含** flux-code-editor——item 20 的 registry/清单更新面因此更大，D5 只登记影响。
- **后端语法资产（已核验）**：`XLangLexer.g4`（~125 lexer 规则，反引号模板串**双反引号转义**（g4 L201）、`#{` 宏、ANTLR 不处理 `${}`/XML——其在 XML 文本层）；生成物已入库（nop-xlang parse/antlr）；nop-idea-plugin 双轨（XML 手写 + ANTLR 脚本注入）；nop-treesitter **无 xlang grammar**（10 blob 确认），且无 grammar.js 编写先例（vendor 上游 parser.c 模式）。
- **POC 已完成并经审查纠偏（产物 `_tmp/xlang-lezer-spike/`，不入版本库——`.gitignore` L123 已忽略，此为有意选择；06 文档须自含证据）**：`@lezer/generator` 独立目录安装即用；script 层 grammar（import 字符串+点分/let/function/if-else/for C 式+for-of/while/return/块/赋值+三元+二元/一元/后缀含可选链/箭头块体/数组/对象字面量/模板串/`#{}`宏/注释）+ 外部 tokenizer（反引号模板串整词扫描含**双反引号转义**、`#{}` 括号配对引号感知）+ 代表性样例。**真实结果：0 错误节点 + 结构硬断言（function/for-of/ternary）通过**；中途曾出现 LineComment `![]*` 吞文件的假绿（0 错误空洞为真），已修正为 `![\n]*` 并给测试加体量/结构硬断言。作者坑 8 条已探明并记录于 spike README（`![set]` 消费语义+假绿教训、`@skip` 序列陷阱、语句 FOLLOW 歧义消解、关键字 literal+precedence 与 specialize 两法、两层 @precedence 语义、赋值/三元 javascript.grammar 重构模式、外部 tokenizer group 导出、生成器省略扩展名需补 `.js`）。
- **用户裁定（D0 §一.7）**：前端基座 = flux-code-editor 上改进，不引 Monaco。

## Goals

- XDBG-D5-01：`05-frontend-architecture.md`——新包结构裁定（`@nop-chaos/flux-xlang-lang` 语言包 + `@nop-chaos/flux-xlang-debug` 调试包）、flux-code-editor 开放化方案（registry 化的最小侵入设计，既有语言零回归）、与 renderer registry/装配集成图、构建链引入方案（@lezer/generator devDependency + 生成物入库）、诊断对接注入式设计、调试状态 store 与 CodeMirror 扩展桥、跨仓交付影响登记（flux-bundle 不含 editor 的事实）。
- XDBG-D5-02：`06-grammar-spike.md`——三来源 POC 结论与裁定（①新写 Lezer grammar【采纳，POC 证据】②treesitter【拒绝，无资产+链条最长】③ANTLR g4 转译【拒绝为主路径，作 token 语义对照参考】）、grammar 范围裁定（一期 script 层 + xml 外壳复用；混合嵌套 parse 与 `${}` 插值拆分为 follow-up）、生产 grammar 工程注意事项（spike 坑清单）。
- 设计目录 README 职责表状态更新（两行）。

## Non-Goals

- 生产 grammar 的完整实现（item 15；spike grammar 不直接入库主分支）、调试扩展/WS 客户端/面板的实现（item 16-19）、跨仓交付执行（item 20）。
- 不改 flux 仓任何文件（本 plan 仅产出 nop-entropy 侧设计文档；POC 在 _tmp/）。

## Scope

### In Scope

- `ai-dev/design/xlang-web-debugger/05-frontend-architecture.md`（新建）
- `ai-dev/design/xlang-web-debugger/06-grammar-spike.md`（新建）
- `ai-dev/design/xlang-web-debugger/README.md`（职责表两行更新）
- `ai-dev/backlog/xlang-web-debugger-roadmap.md`（item 6 状态 + Stages 表第 6 行 owner plan）
- `ai-dev/logs/` 执行当日条目
- `_tmp/xlang-lezer-spike/`（POC 证据留存，已完成）

### Out Of Scope

- flux 仓代码变更；`docs-for-ai/`；D6 文档。

## Execution Plan

### Phase 1 - 前端架构文档 + 语法 spike 结论

Status: completed
Targets: `ai-dev/design/xlang-web-debugger/05-frontend-architecture.md`、`06-grammar-spike.md`

- Item Types: `Feature`、`Decision`（包结构、注册开放化、构建链、grammar 三来源裁定）

- [x] 写 05-frontend-architecture.md：①包结构裁定（两新包职责边界：flux-xlang-lang = grammar+高亮+折叠+缩进+诊断桥工厂；flux-xlang-debug = 断点 gutter/装饰扩展 + WS 客户端 store + 调试面板装配；拒绝替代方案——单包混合/塞进 flux-code-editor——及理由）；②语言注册开放化（registry Map 化：createLanguageExtension/getLanguageParser 内部改为内置注册表 + 对外 registerLanguage 扩展点；EditorLanguage 联合增 'xlang'；**第三个分发点**——createBaseExtensions L199-246 的 language 特例 if 链（expression 补全/lint/friendlyNames、sql tables）一并收敛进注册表条目（条目携带可选 completion/linter/decoration 扩展）；**类型层开放度显式裁定**（封闭联合加成员 vs 放宽 string vs declaration merging——半开放过渡语义需成文）；既有语言行为零回归的约束）；③集成图（编辑器扩展/面板/renderer registry/playground 演示页与宿主 env 的关系，Mermaid）；④构建链（@lezer/generator 为 devDependency、生成产物落点裁定——**必须避开 `check:src-artifacts` 等既有 fail-fast 门禁**：生成 `.js/.d.ts` 不得入 `packages/*/src`（如落 `generated/` 目录）+ tsc include/eslint/knip/jscpd 配套豁免清单 + `gen:grammar` script、与包级纯 tsc build 的兼容）；⑤诊断对接（语言包提供 linter 工厂，编译服务由宿主 env 注入——flux 不直接依赖后端）；⑥调试状态桥（**双向契约**：store→编辑器下行 = CodeMirror state field/Effect 分类与状态 schema；编辑器事件→store 上行 = gutter 点击等动作/事件命名；store 技术选型 = 仓内惯例 zustand——一次定死使 item 16/17/18 可各自仅依本文档 + mock 并行开工）；⑦跨仓交付影响登记（flux-bundle 现不含 editor——item 20 需新增清单项）
- [x] 写 06-grammar-spike.md：①三来源裁定表（①新写 Lezer grammar【采纳】②treesitter【拒绝：无 xlang 资产、grammar.js+blob 流水线+前端绑定链条最长】③ANTLR 转译【拒绝为主路径：无自动转译、125 规则含编辑器无关 token、手工转译成本不低于新写；价值=token 语义对照】）；②POC 证据（**自含形态**：grammar 关键摘录、真实解析数字 0 错误节点+结构断言、生成/集成形态、假绿纠偏过程——_tmp 不入版本库，文档不得只挂路径）；③grammar 范围裁定（一期 script 层；XML 外壳复用现有 xml 语言；混合嵌套 parse、`${}` 插值拆分、表达式体箭头函数、分号可选化为 follow-up 清单）；④生产工程注意事项（spike 坑清单转移）；⑤item 15 的实现输入清单
- [x] README 职责表：04/05/06 行状态更新并加链接（04 行在上轮已更，本轮补 05/06）
- [x] spike 目录 README 与 06 文档证据一致（06 自含证据 + 指明 _tmp 为本地复现）

Exit Criteria:

- [x] 包结构裁定唯一且职责边界清晰（两新包 + editor 开放化三方职责无重叠）；被拒替代（单包、塞进 flux-code-editor）有记录
- [x] 开放化方案含既有语言零回归约束（三个分发点收敛面）、类型层开放度裁定、registry 条目携带可选扩展的设计，item 15/16 挂载点明确
- [x] 构建链方案与包级纯 tsc build 事实兼容，生成物入库策略**逐项列出**不触碰 `check:src-artifacts`/eslint/knip/jscpd 既有 fail-fast 门禁的落点与豁免
- [x] 三来源裁定各有事实理由（引用 spike/tree-sitter/ANTLR 基线事实，ANTLR 对照以双反引号转义等真实 token 语义为例）；follow-up 清单四项齐备（插值拆分/混合 parse/表达式箭头/分号可选）；⑥双向契约+zustand 选型使 item 16/17/18 可仅依文档+mock 开工
- [x] 文档不引用 `ai-dev/analysis/`/`ai-dev/discussions/`；与 D0 §一.7 一致
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] owner-doc 更新 = 两文档 + README 职责表；`docs-for-ai/` 不动；flux 仓零改动
- [x] `ai-dev/logs/` 执行当日条目已更新（随收口追加）
- [x] No new test required：纯文档计划（guide rule 25）

### Phase 2 - 对抗性评审与收口

Status: completed
Targets: 两设计文档、roadmap

- Item Types: `Proof`

- [x] 独立子代理对抗性评审一轮：想象性分析（item 15 工程师照 05 能否直接建包）；POC 结论与 spike 产物一致性（实测运行 test.mjs）；与 D0 用户裁定一致性
- [x] 修复评审发现的问题（首轮 P1 假绿 Blocker 在 plan 审查即拦截并重做 spike；文档轮 P1-P5 Major + P6-P9 Minor 全部修复；closure audit 复核发现 N1 follow-up#6 缺行 / N2 05 门禁清单补丁未落 / N3-N5 卫生项，二轮修复）
- [x] roadmap item 6 状态翻转（激活时 `planned`、audit 后 `done`）+ Stages 表第 6 行 owner plan
- [x] `ai-dev/logs/` 收口记录更新

Exit Criteria:

- [x] 评审发现全部修复或书面裁定
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] roadmap item 6 `done` 且设计收口 milestone 未翻转
- [x] `ai-dev/logs/` 执行当日条目含本项记录

## Closure Gates

> 纯文档计划：构建验证豁免，doc-links --strict 为机械验证门。

- [x] XDBG-D5-01、XDBG-D5-02 交付物齐备且满足 Stage detail 要求（包结构/扩展点/集成图/开放化方案 + 三来源 POC 结论与裁定含构建链评估）
- [x] 设计评审完成，无未处置 Blocker/Major
- [x] 文档与 live repo 事实一致（flux 基线行号级抽查 + POC 产物与结论一致——closure audit 实测运行 test.mjs 复核）
- [x] 无静默降级项
- [x] owner docs 已同步（README 职责表 04/05/06 行 active+链接）
- [x] 独立子代理 closure audit 完成并记录证据
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2465-xdbg-d5-frontend-grammar.md --strict` 退出码 0
- [x] No build verification required（纯文档计划）

## Deferred But Adjudicated

### 编译诊断端点与 payload schema

- Classification: `out-of-scope improvement`（依属待定项，非缺陷）
- Why Not Blocking Closure: 端点 owner 归 item 14（REPL compile 服务，按 D6 裁定落地）；05 §3.3 已登记为 open question 并声明 item 19/21 的依赖关系，语言包工厂签名不因端点未定而受阻。
- Successor Required: `yes`
- Successor Path: item 7（D6 REPL 安全详设）定义 compile 端点与诊断 payload schema；item 15 的 linter 工厂按其接线。

## Non-Blocking Follow-ups

（无其余）

## Closure

Status Note: 两份交付物落地：05（两新包职责边界+registry 三分发点收敛开放化+半开放类型裁定+构建链门禁四件套与 tsc/打包三点配套+zustand 双向状态桥对齐 D1+诊断桥注入与 open question 登记+跨仓影响）；06（三来源裁定+POC 真实全绿证据含假绿纠偏+一期范围与 6 项 follow-up+11 条工程坑+item 15 输入清单）。POC 经审查拦截假绿（LineComment 吞文件）后重做并加结构/体量硬断言，最终 0 错误节点实测通过。文档轮评审 P1-P5 Major + P6-P9 Minor 与 closure audit 复核 N1-N5 全部修复。roadmap Work Item 6 翻 `done`。
Completed: 2026-10-16

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理（plan 审查 agent_704a56c1-8f60-45d4-90f0-082f819afe4e——拦截 POC 假绿 Blocker；文档评审 agent_2e23400b-ef0d-45e4-8817-6f9133dd661f；closure audit agent_ebba3e83-675a-4931-9939-3edb3774e8ab）
- Audit Session: closure audit 由 fresh 子代理执行，实测运行 POC（node test.mjs → error nodes: 0 / missing constructs: none / PARSE OK）
- Evidence:
  - 修复验证：P1（除 follow-up #6）、P3、P4、P5、P6、P7、P9 首轮 PASS；二轮修复 N1（06 §三 follow-up #6 后缀++行）/N2（05 §3.3 门禁四件套+eslint 无需说明+.d.ts shim+rootDir/打包面）/N3（06 头部引用）/N4（spike README 对齐 06 权威）/N5（tokens.js 死分支+EOF 兜底），修复后实测 POC 仍全绿、doc-links exit 0
  - 新矛盾扫描：store schema 与 D1 逐字段对齐核实；05/06/README 三方一致；N8（lease 缺 renewIntervalMs）登记为 item 17 注意项，非矛盾
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2465-xdbg-d5-frontend-grammar.md --strict` 退出码 0
  - Deferred 项分类检查：唯一 deferred（编译诊断端点）为依属待定项，owner/successor 明确；无 in-scope live defect 被降级

Follow-up:

- 编译诊断端点定义 → item 7（D6）/ item 14；lease.renewIntervalMs 排程消费 → item 17 注意。

# 2026-10-02 WI10 测试暴露的产品缺陷嫌疑清单（已修复，plan 2306 收口）

> 来源：plan 2302（WI10 format 第二批）执行期发现。按 roadmap 硬边界记录，修复独立立项。mermaid 1-4 项同根（文法文件缺陷），可合并一个立项。

## 1. mermaid 文法 CLASS/STATE token 重复定义且字面量冲突（P1 嫌疑）

- Problem：`nop-format/nop-mermaid/model/antlr/BaseRules.g4` 第 6-7 行（`CLASS:'classDiagram'`/`STATE:'stateDiagram'`）与第 42-43 行（`CLASS:'class'`/`STATE:'state'`）重复定义。ANTLR 取首个定义，'class'/'state' 被词法化为 Identifier_ → 标准输入 `classDiagram class Foo {…}`、`stateDiagram state A : "desc"` 解析失败（nop.err.antlr.not-end-properly），MermaidClassNode/MermaidStateNode 解析路径实际不可达。
- 锚定：TestMermaidASTParser 特征化用例（testClassKeywordLexesAsIdentifier/testStateKeywordLexesAsIdentifier）。

## 2. mermaid DIRECTION token 无词法定义（P2 嫌疑）

- Problem：`Mermaid.g4:47` 引用 `DIRECTION`，但 BaseRules.g4 无该 lexer 规则 → direction 语句永不可达，`direction TB` 中 'direction' 被解析为普通 FlowNode。

## 3. mermaid sequenceMessage 与 flowEdge 规则同构（P2 嫨疑）

- Problem：`mermaidSequenceMessage` 与 `mermaidFlowEdge` 规则体完全同构（label 位于 to 之前而非 `:` 之后）→ adaptivePredict 恒选靠前的 flowEdge，序列消息一律解析为 MermaidFlowEdge。

## 4. mermaid SLL 阶段异常绕过 LL 二阶段重试（P2 嫌疑）

- Problem：AbstractParseTreeParser 仅捕获 ParseCancellationException，SLL 阶段 checkEnd 抛出的 NopException 绕过 LL 重试——使上述文法问题表现为不可恢复的 not-end-properly。

## 5. pdf RCPathCellDataLocator.buildFlatTable 合并单元格双重展开（P1 嫌疑）

- Problem：约 L205，TableBlock.getCell 解析为真实单元格后再次按 rowspan/colspan 展开 → 合并区贴表格边缘时抛 ArrayIndexOutOfBoundsException（实测复现）；位于中部时覆盖其后行首列文本（平坦化数据损坏）。
- 影响：pdf 表格抽取对含合并单元格的表格产出错误数据或崩溃。

## 6. pdf DashPatternDetector 滑窗锚定（P3 嫌疑）

- Problem：walk 的容差滑窗在失配窗口处跳过而非中断，噪声前缀会把模式 start 锚定为 0（实测 mod=2/start=0/len=8）。


## Fix（2026-10-03 plan 2306 回填）

- **1-3 mermaid 文法三项（同根）：`fixed`**。
  - CLASS/STATE token 重复定义（含 PIE 重复 L9/L45）拆分：CLASS/STATE 保留图表类型字面量，新增 CLASS_KEYWORD/STATE_KEYWORD 承载 'class'/'state' 语句关键字；删除重复 PIE。文法重生成流程执行：root pom exec-maven-plugin precompile execution（generate-sources）→ gen-mermaid-parser.xgen → nop-codegen antlr 模板，MermaidLexer/MermaidParser/.tokens/.interp/_MermaidASTBuildVisitor checked-in 产物一并再生（旧生成物与文法脱节——旧 lexer 甚至无 CLASS/STATE 词法，证实从未随文法再生）。
  - DIRECTION 补词法规则 `DIRECTION: 'direction';`，direction 语句可达。
  - sequenceMessage 与 flowEdge 结构区分：sequenceMessage 改为 "to 后置 COLON message"（`A ->> B: "msg"` 真实 mermaid 序列消息语法）；无 COLON 的边语法仍归 flowEdge。
  - `Identifier` 未定义引用修正为 `Identifier_`（mermaidClassMember）。
  - 回归：3 个特征化用例翻转（testClassNodeParsesWithMembers/testStateNodeParsesWithDescription/testSequenceMessageDispatchesToSequenceMessageNode）+ 新增 testDirectionStatementParses/testSequenceMessageWithoutColonStillParsesAsFlowEdge。nop-mermaid 26 测试全绿。
- **4 SLL 阶段 NopException 绕过 LL 重试：`fixed`（nop-antlr4-common，repo 级路径）**。twoPhaseParse 增捕 not-end-properly 错误码的 NopException 触发 LL 重试；词法类等其他 NopException 保持原语义直接传播（首次宽捕实现曾把 "string-literal-not-end" 在 LL 重试后劣化为 unresolved-identifier——nop-xlang TestXLangParser.testIdentifier 揪出，遂精确化按错误码分流）。回归：新增 nop-antlr4-common TestTwoPhaseParseRetry（stub Parser 驱动合同：not-end-properly 必重试、SLL 成功不重试、词法类 NopException 直接传播）。▲ 下游：nop-xlang 855 / nop-orm-eql 91 / nop-mermaid 26 当期全绿。
- **5 pdf RCPath 合并单元格双重展开：`fixed`**。buildFlatTable 仅在锚点位置（getRowPos/getColPos 匹配）展开一次（getCell 对被覆盖坐标返回锚点单元格），并对 rowspan/colspan 越界钳制（贴边不再 AIOOBE）。回归：TestCellDataLocators 新增 testRcPathLocatorDoesNotClobberRowsBelowMergedCell（双重展开覆盖）+ testRcPathLocatorToleratesMergedCellAtTableEdge（贴边越界）。nop-pdf 70 测试全绿。
- **6 DashPatternDetector 滑窗锚定：`adjudicated-not-a-defect`（文档化裁定）**。容差滑窗（maxChange 20%）是吸收 PDF 图形段测量噪声的启发式设计；噪声前缀并入 start=0 模式属该语义的可见结果。改为中断式严格匹配会降低真实文档检测稳健性，无证据更优。处置：类 javadoc 显式记录滑窗锚定契约；TestDashPatternDetector javadoc 引用裁定，既有稳健语义断言复核一致。

## Affected Files

- nop-format/nop-mermaid/model/antlr/BaseRules.g4 + Mermaid.g4（文法源）
- nop-format/nop-mermaid/src/main/java/io/nop/mermaid/parse/antlr/（MermaidLexer/MermaidParser/.tokens/.interp，再生）
- nop-format/nop-mermaid/src/main/java/io/nop/mermaid/parse/_MermaidASTBuildVisitor.java（再生）
- nop-kernel/nop-antlr4/nop-antlr4-common/src/main/java/io/nop/antlr4/common/AbstractParseTreeParser.java
- nop-format/nop-pdf/src/main/java/io/nop/pdf/extract/data/RCPathCellDataLocator.java
- nop-format/nop-pdf/src/main/java/io/nop/pdf/extract/dashline/DashPatternDetector.java（javadoc）
- 测试：TestMermaidASTParser / 新增 TestTwoPhaseParseRetry / TestCellDataLocators / TestDashPatternDetector（javadoc）

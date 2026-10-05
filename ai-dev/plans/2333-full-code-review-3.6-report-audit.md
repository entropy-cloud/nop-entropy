# 2333 full-code-review 3.6 nop-report 深审

> Plan Status: completed
> Last Reviewed: 2026-10-05
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 3.6（MA3）；Skill=`ai-dev/skills/deep-audit-prompts.md`（维度 12/13/15 + 共享前缀）
> Related: owner doc `docs-for-ai/03-modules/nop-report.md`；check2/nop-report.md 26 条：P0×1（:19 COUNTIF/SUMIF 多字符操作符字典序比较）+ P1×5（:50 avg 分母稀释、:75 childCell colDescendants 返回 null、:108 getExpandableRowParent 递归错列方向、:131 resolveAllCellsInColParent 误用 getRowDescendants、:159 FontManager.registerSystemFonts 死代码→Standard14 恒空）+ P2×10（:187/:205/:227/:253/:275/:298/:327/:351/:375/:394）+ P3×10（:412/:437/:455/:472/:492/:508/:529/:550/:569/:589）。全部带 fix-ai-check 处置块——以 live code 为准标定。check/nop-report.md 25 条（P0×1 min-null/P1×6/P2×7/P3×11，仅 1 条处置块）与 check2 大面积同源——**标定时以 check2 条目为主键，check 条目按撞车对照合并**；check P0 #19（min-null）在 fix-ai-check 剩余 32 P0 清零波次（2026-08-22，SUMMARY :92-97）处置记录在案。
> 只读审计（P0 走异步注入）；check2/check/nop-report.md 与 check/SUMMARY.md 为点名例外输入。**标定口径**：already-fixed 判定须定位 live 修复点（file:line）并抽样复跑对应回归测试；pre-existing「暂缓/需 owner 裁定」处置（check2 :298 minReuse 语义等）须显式承接（维持暂缓/重新裁定/转 R1.0，不得悬空）；标定结论与处置块声明冲突（声明已修复实测仍 live）时升级 P0 即时通道评估。
> Draft Review: agent_4d65c302 首轮无 Blocker——2 Major（F1 分工缺口：api 契约对齐/service 非 auth 面/dao 抽查/imp 族 4 条〔:508 文件已删除〕无人认领；F2 标定口径未定义：already-fixed 深度与 ：298 暂缓项承接悬空）+ 5 Minor（:359 撞车归属错置/SUMMARY 行号 :92-94→:92-97/验证命令补测试资产说明/app 入 Out Of Scope/产物命名）——本轮全部修复；agent_1067e4b4 聚焦复核 7/7 PASS 无 Blocker（F4 残留一行 :92-97 就地勘正）——进入执行。

## Purpose

执行 roadmap 工作项 3.6：深审 nop-report（XPT 展开正确性、ReportFunctions 公式注入面、pdf/docx 导出），产出报告 `3.6-report-deep-audit.md`。

## Current Baseline

- nop-report 组 main Java：core 61 文件 10417 行/pdf 14 文件 2204 行/docx 5 文件 443 行/api 27 文件 2782 行/dao 29 文件 6779 行/service 12 文件 194 行/ext 3 文件 108 行/app 1 文件 38 行/demo 1 文件 179 行/web、codegen、meta 0 行。手写最大文件：ExpandedCell 922、XptModelInitializer 697、ReportFunctions 561、FontManager 559、ExpandedTable 525、ExcelTemplateToXptModelTransformer 518。
- check2/nop-report.md 26 条（构成见头部），26 个 fix-ai-check 处置块；check/nop-report.md 25 条（仅 1 处置块），check P0 #19 处置记录在 SUMMARY :92-97（2026-08-22 剩余 32 P0 清零波次；完整处置块另见 check/nop-report.md:49）。基线为编制期（2026-09-22）全仓 BUILD SUCCESS——其后 plan 2320/2326/2327/2330–2332 修复已合入（不波及 nop-report 域），预存失败归因兜底见 Phase 3。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/3.6-report-deep-audit.md`：摘要 + 标定台账 + P0–P3 发现（`P<级别>-3.6-<序号>`）+ 驳回台账 + 裁决。
- XPT 展开正确性（维度 12/15，roadmap 点名第一优先）：ExpandedCell（922 行，check2 :75/:108 域）/ExpandedTable/ExpandedRow/ExpandedCol/CellRowExpander/CellColExpander/CellCoordinateHelper（check2 :131/:205/:298 域）/XptModelInitializer（:327 域）/ExpandedSheetGenerator/ExpandedSheetEvaluator/XptRuntime；check2 :75/:108/:131 三条 P1 深标定（展开方向/父链递归/后代集合的正确性语义）。
- 公式注入面（维度 13，roadmap 点名）：ReportFunctions（561 行）+ ReportExtFunctions——公式表达式到求值的信任边界、单元格引用/数据集字段是否可注入越权求值、check2 :19 COUNTIF/SUMIF 操作符解析 P0 深标定（>=/<=/<> 字典序比较 vs 数值比较；compareWithOperator 异常吞噬 check :349 撞车）、聚合函数 Excel 语义偏差族（:589 域 + check :339 AVERAGE NaN 撞车）。
- pdf/docx 导出（维度 13/15，roadmap 点名「pdf 导出样式过滤」）：FontManager（559 行，:159 死代码→Standard14 恒空域 + check :359 线程安全撞车）、PdfStyleHelper（:394 非 WinAnsi 字符域 + check :435 颜色解析撞车）、PdfSheetRenderer/PdfReportRenderer（:227 PDDocument 不关闭域）、docx XptWordTableRenderer（:472 域）；样式过滤=PDF 导出对模板样式的过滤/降级路径完整性。
- 权限与数据面（维度 13）：NopReportDefinitionAuth/NopReportDatasourceAuth 角色数据权限执行链（定义→引擎取数是否真实校验）、api 面契约对齐（维度 12）、dao/web 生成面抽查。
- check2 26 条 + check 撞车条目标定全覆盖（still-live/already-fixed/false-positive/duplicated）。
- index 登记 + 独立 closure audit + roadmap 3.6 翻 done。

## Non-Goals

- nop-report-dao/meta/web/app/codegen 深审（生成面抽查即可；**app（1 文件 38 行）显式 Out Of Scope**）；nop-ooxml（format 域 6.9/4.4 归属）；ExcelRecordInput/Output 大对象内存问题（check2 :253/:569 域标定但不再扩展）；demo 模块。

## Scope

### In Scope

- `nop-report/nop-report-core/**`、`nop-report/nop-report-pdf/**`、`nop-report/nop-report-docx/**`、`nop-report/nop-report-api/**`（契约对齐抽查）、`nop-report/nop-report-service/**`、`nop-report/nop-report-dao/**`（权限执行链）、`nop-report/nop-report-ext/**`
- 只读输入：`ai-dev/audits/check2/nop-report.md`、`ai-dev/audits/check/nop-report.md`、`ai-dev/audits/check/SUMMARY.md`（#19 与剩余 32 P0 波次记录）、`docs-for-ai/03-modules/nop-report.md`、`docs-for-ai/03-runbooks/generate-report.md`、`docs-for-ai/02-core-guides/reporting-and-notification-integration.md`

### Out Of Scope

- nop-ooxml 域（xlsx 读写库本体，4.4/6.9 域）；nop-report-dao 生成面深审；2.7 已扫声明面。

## Execution Plan

### Phase 1 - 深挖（双 agent 并行）

Status: completed
Targets: nop-report core/pdf/docx/api/service/dao/ext

- Item Types: `Proof`

- [x] 动手前基线确认：本 plan 已提交、`git status` 干净后开始
- [x] 机械基线：core/pdf/docx 手写文件与行数清单（主 agent 已生成：ExpandedCell 922/XptModelInitializer 697/ReportFunctions 561/FontManager 559/ExpandedTable 525/ExcelTemplateToXptModelTransformer 518）、check2 26 条 + check 25 条撞车对照表（由两子 agent 按域构建、主 agent 汇总）、check2 处置块状态盘点
- [x] XPT 展开面子 agent（维度 12+15）：ExpandedCell/ExpandedTable/ExpandedRow/ExpandedCol/Expander 族/CellCoordinateHelper/XptModelInitializer/ExpandedSheetGenerator/ExpandedSheetEvaluator/XptRuntime + ReportDataSet 聚合语义（:50 域）+ :75/:108/:131 三 P1 深标定 + **core imp 族与 engine 标定**（:253 ExcelRecordOutput/:275 ExcelToXptModelTransformer/:351 ExcelTemplateToXptModelTransformer/:492 ReportEngine.getRendererForExcelData；:508 XptModelToExcelTransformer **文件已删除**——标定=核实删除属已修复）
- [x] 公式注入与导出子 agent（维度 13+15）：ReportFunctions/ReportExtFunctions 公式信任边界（:19 P0 深标定 + :589/:349 域）+ pdf 全家（FontManager :159/:359 线程安全域、PdfStyleHelper :394/:435 域、PdfSheetRenderer/PdfReportRenderer :227 域）+ docx XptWordTableRenderer :472 域 + **权限执行链与契约对齐**（api 模块 NopReportDefinitionAuthApi/NopReportDatasourceAuthApi 契约对齐 + service 模块 auth BizModel→引擎取数校验真实性 + service 其余 BizModel 与 Constants/Errors/Configs 抽查 + dao 生成面抽查 + ext 3 文件）
- [x] 深挖迭代：有发现的子 agent 派第 2 轮，至无新发现或 10 轮上限（A/B 面均第 3 轮收敛）
- [x] 产物 `_tmp/fcr-3.6-{expand,formula}.md`

Exit Criteria:

- [x] 两份维度初稿落盘（含深挖追加节），每条发现 ≥9 行含 file:line
- [x] check2 26 条 + check 撞车对照标定结论落盘
- [x] No owner-doc update required（只读）
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立维度复核与判级收敛

Status: completed
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [x] 独立复核子 agent（fresh session）：逐条 保留/降级/驳回 + check2 标定独立验证（20 条全保留、降 0 驳 0，B 面 4 条带事实收窄）
- [x] 主 agent 汇总：判级统一、与 4.4（format 域 xlsx 读写）/6.9 边界协调、公式注入类安全判级按 2.6 总裁据
- [x] 展开面正结论收敛

Exit Criteria:

- [x] 复核结论落盘 `_tmp/fcr-3.6-{expand,formula}-review.md`
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: completed
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [x] 编制 `3.6-report-deep-audit.md`；index 登记 3.6 行
- [x] 如有 P0：异步注入修复 plan + 「P0 发现追踪」登记（本项零 P0——check2 :19 已修复复核确认，未触发即时通道）
- [x] 定向验证锚点：`./mvnw test -pl nop-report/nop-report-core,nop-report/nop-report-pdf,nop-report/nop-report-docx -am`（如遇失败归因预存并登记；**api/service/dao/ext 无测试资产**——find 实测 test 0 文件，验证以 core/pdf/docx（25+8+2 测试文件）为锚，其余模块以审计走查为锚）
- [x] 独立子 agent closure audit；通过后 roadmap 3.6 翻 `done`；plan 关闭（双 --strict）；commit

Exit Criteria:

- [x] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [x] 定向测试 PASS，或失败已归因预存并登记
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs <本文件> --strict` 退出码 0

## Closure Gates

> 只读审计计划，免除全量构建验证；以定向 test 运行为验证锚点。

- [x] 报告落盘且展开/公式注入/导出/权限面全覆盖
- [x] 每条发现 file:line 可复现；判级依据共享前缀；误报校准留痕
- [x] index 计数与报告发现清单一一对应
- [x] 独立 closure audit PASS 并留痕
- [x] roadmap 3.6 回填 `done`；log 收口记录已写
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无预设；修复工单归 R1.0/R1.x。）

## Closure

Status Note: 只读审计计划收口。报告 `3.6-report-deep-audit.md` 落盘：通过附 P1×2（removeCell 展开区域起点减号〔check:103 从未处置仍 live〕+权限表全仓零消费契约断裂〔nop-datav 下游双轨已成现实〕）+P2×5（两条挂死独立复现实证置队首）+P3×12（净 19 条，撞车合并 12-05≡13-02，降 0 驳 0）。**check2 26 条标定零虚假申报**（:19 COUNTIF/SUMIF P0 已修复——复核亲读 live compareWithOperator 重写+8/8 测试；14+ already-fixed 附修复点；:298 维持暂缓显式承接转 R1.0 设计决策项；:508 文件删除核实；check:232 无处置声明仍 live 重新立案）。定向验证 core/pdf/docx BUILD SUCCESS；api/service/dao/ext 无测试资产以走查为锚。closure audit FAIL→单处方（log 补 Phase 2 事实+倒序归位）落实后收口。
Completed: 2026-10-05

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_37c17a58-6814-4c8a-a194-ba74f413bed4（fresh session，只读 closure auditor；首轮 FAIL 单处方修正）
- Evidence:
  - 计数勾稽：终态净 P0=0/P1=2/P2=5/P3=12=19 ↔ 报告实点 ↔ index 3.6 行（0/2/5/12）↔ 编号映射表 21 行（19 净+2 口径行）↔ 复核保留数（A 10 + B 10 毛/1 补强并入）；毛 20/净 19 口径差异在报告 :19 诚实记载
  - P1 台账：P1-3.6-001/002 index 两行与报告一致；「P0 发现追踪」无 3.6 新增正确
  - check2/check 标定：auditor 亲读 live 三重点（:19 ReportFunctions.java:485-532 重写无 catch/:298 CellRowExpander:136-140 vs CellColExpander:127-132 不对称在案/:508 find+grep 零命中）
  - 锚点抽验 6/6 亲读命中（CellRowExpander:42 减号+extendCells:61 加号矛盾/ReportEngine:57-68 无角色判定+零消费独立复现/TableExpander:36-58+ExpandedCell:371-386/ExpandedSheetGenerator:285-295/ExpandedRow:262-266+ExpandedTable:39/:171/TestPdfFontResolution 5 处 assumeTrue :62-:122）
  - 格式抽查 6 条 ≥9 要素完整
  - 判级总裁据：P1-3.6-002 重于 ILockService/2.6 两先例，P1 档成立
  - 定向测试：`./mvnw test -pl nop-report/nop-report-core,nop-report/nop-report-pdf,nop-report/nop-report-docx -am` BUILD SUCCESS（docx 2/doc 全绿）；A/B 面初审+复核累计 62+35+2 例全绿
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs <本文件> --strict` 退出码 0
  - Anti-Hollow：只读审计计划，无新增组件/代码路径；验证以定向 test 全绿 + closure auditor 亲读 live code 锚点抽验为锚
  - Deferred 项分类检查：无 deferred；P1×2 经 index「P1 发现汇总」进 R1.0；:298 设计决策项显式承接转 R1.0，无 in-scope live defect 被降级

Follow-up:

- no remaining plan-owned work（P1×2+P2×5 修复归属 R1.0 分族；P3×12 watch-only/R1.0 低优先池；:298 minReuse 语义展开引擎设计决策项转 R1.0）

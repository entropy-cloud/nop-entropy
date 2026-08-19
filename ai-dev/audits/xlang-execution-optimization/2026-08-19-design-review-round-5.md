# W2-review Round 5 设计审查报告（xlang-execution / xlang-java / xlang-truffle）——PASS 轮

> Mission: xlang-execution-optimization
> Plan: `ai-dev/plans/xlang-execution-optimization/2026-08-19-2050-2-w2-design-review-gate.md`（Phase 2）
> Round: 5
> Date: 2026-08-19
> Reviewer: 独立子 agent（fresh session，opencode task `ses_fe5987b83ffeEVJLYPtFQMJTYP`，模型 zhipuai-coding-plan/glm-5.2；未参与撰写与前轮审查、未读 round 1-4 报告——独立性成立）
> **Verdict: PASS（0 P0 / 0 P1）**

## 审查输入

同一输入包（三组设计文档 round-4 修复后版本），三级判级口径与文档式证据格式在 prompt 中声明。全量复审。

## Findings（审查者原文，全部为 P2）

### [R5-1] 后端选择"判定时机"计数口径：§一.2 称"两个时机"，§三表头称"三个" — **P2**

- **证据**: §一.2"模型加载期…与运行时求值期…两个时机判定"；§三表头"判定时机（三个）"，构建期行实为决策输入的生产时机。
- **建议**: 表头改为"判定输入的生产与判定时机"或标注构建期行为"输入生产（非判定）"。
- **信心水平**: 确定

### [R5-2] 依赖方向"不可违反约束"枚举未覆盖 nop-javac 可选边 — **P2**

- **证据**: vision §六.1 只枚举三条边；统一架构 §二 mermaid 与 java §八含构建期可选诊断边 `XJ -.-> NJ`。live 核实 `nop-javac` 仅依赖 nop-api-core/nop-commons/janino，无环、方向合法——机制无冲突，约束清单不完备。
- **建议**: vision §六.1 补一句许可附加边说明。
- **信心水平**: 确定

### [R5-3] java 静态路径 × 租户差异化资源：预期稳态降级未成文，WARN 未区分"预期分化"与"stale 缺陷" — **P2**

- **证据**: 树指纹失配 → 降级解释器正确性无洞；但租户定制资源的稳态行为（每次 WARN + 走解释器）未成文，与 stale 缺陷共用事件级别。
- **建议**: 补"租户差异化树的预期稳态降级"说明 + 观测事件 reason 分级。
- **信心水平**: 很可能

### [R5-4] 统一决策树动态路径缺"单元级翻译失败"分支 — **P2**

- **证据**: 决策树动态路径仅"启用且初始化成功→TRUFFLE / 初始化失败→降级"两分支；§五把"truffle 列翻译失败"归单元级降级——语义可推导但决策树未显式承载。
- **建议**: 补第三分支"该单元翻译失败 → 记观测事件 → INTERPRETER"。
- **信心水平**: 确定

### [R5-5] xlang-java 目录无独立 Vision 文件且未显式声明豁免 — **P2**

- **建议**: java README 补 Vision 委托的显式豁免声明（与 truffle README 做法对齐）。
- **信心水平**: 确定

### [R5-6] 01 知识层与 java 组文档残留轻度过程叙事措辞 — **P2**

- **证据**: 01 头部"决策迁出收口"、§七"原为…已迁出"、§十"已清账"；java §七"live 事实修正"。
- **建议**: 压平为纯指针/当前状态陈述（保留清账表与状态位）。
- **信心水平**: 很可能

### 无 finding 维度结论（审查者原文摘要）

- **① 内容点覆盖 15/15**：三组 15 点均为实质章节（逐点列出对应章节）。
- **② guide 合规**（除 R5-5/R5-6）：三要素齐备（含替代对比表：路 A/B、SHARED/REUSE/EXCLUSIVE、显式注册/扫描、树指纹/源指纹、静态常量/行号映射表、源码级/ASM）；全部接口契约级；无 analysis/discussions 引用；README 三要件齐备；01 知识层豁免未越界（逐节核实无 nop 侧决策残留）。
- **③ 跨文档一致性**（除 R5-1/R5-2/R5-4）：选择机制四处理径一致（扫描清单/静态/动态管辖、决策树与 java §五语义等价）；模块边界与依赖方向一致；对拍基准与列适用性四处一致（"未启用跳过"与"单元级降级 FAIL"边界清晰）；降级链单跳、无全局默认后端、native 不做 truffle 等逐项对齐；内部自洽。
- **④ live 可行性**：**无 finding**——exec/ 实数 137 ✓；分类表点名类全量抽查存在 ✓；ScriptCompilerRegistry/Janino 约定 ✓；EvalRuntime 四字段并列与文档表述完全一致 ✓；ExecutableFunction 边界清零不变式成立 ✓；ExitMode 三值 ✓；EvalFrame/EvalScopeImpl/LexicalScopeAnalysis ✓；RCM 多租户 ✓；JdkJavaCompiler 定位准确 ✓；nop-js ✓；XLangConstants 大小写正确 ✓；候选类型常量存在 ✓。
- **⑤ 知识层清账**：无 finding——决策残留扫描全部为"框架事实+指针"形态；清账表五条与 02 §九逐行对应、状态口径一致；移交清单 3 项处置明确且合理。
- **总评**：决策质量与 live 一致性已收敛到高位，未发现可实现性缺陷；残余问题集中在"唯一决策口径的完备性"（局部补句可解）与观测语义精细化。

### 严重程度分布

| 级别 | 数量 | 编号 |
|---|---|---|
| P0 | 0 | — |
| P1 | 0 | — |
| P2 | 6 | R5-1 ~ R5-6 |

## Verdict: PASS（0 P0 / 0 P1）

## 遗留 P2 清单（逐条裁定，主 agent 记录）

> PASS 口径已固化为 0 P0/P1；P2 为表述/完备性改进，不影响可实现性与正确性。**修复发生在 PASS 之后会使"文档与 PASS 轮结论一致"失效（须再走新一轮审查），故全部裁定遗留**，逐条记录归属：

| # | Finding | 裁定 | 归属/Successor |
|---|---|---|---|
| 1 | R5-1 判定时机计数口径（两个 vs 三个） | `out-of-scope improvement`（表述；操作化判据已消除实质歧义） | 下次设计文档修订；随 W3 回填 I5 时核对 |
| 2 | R5-2 vision 依赖约束枚举缺 nop-javac 可选边 | `out-of-scope improvement`（机制无冲突、live 已核无环；约束清单完备化） | 下次设计文档修订；与移交 W3 清单"纪律 5 补边"项合并处理 |
| 3 | R5-3 租户稳态降级成文 + WARN 事件分级 | `out-of-scope improvement`（正确性无洞；观测语义属实现层） | I2/I6 验收标准（移交 W3 清单） |
| 4 | R5-4 决策树缺"单元级翻译失败"分支 | `out-of-scope improvement`（语义可从 §五推导；生产观测语义属实现层） | I4/I5 验收标准（移交 W3 清单） |
| 5 | R5-5 java README Vision 委托豁免声明 | `out-of-scope improvement`（guide 未认可委托式 Vision，单方声明无规范出处） | guide 条款增补（移交 W3 清单 guide 项） |
| 6 | R5-6 过程叙事措辞压平 | `out-of-scope improvement`（一行级存根，且迁出状态行是清账核验锚点） | 下次设计文档修订 |

## 移交 W3 清单（全轮次汇总去重——W3 回填阶段二时核对是否纳入）

> 汇总自 round 1-5 各报告的"移交候选/裁定遗留"条目（roadmap 与 design-writing-guide 非 W1 产出，本 plan 修复范围仅限 W1 设计文档）。

1. **design-writing-guide 增补条款**："外部知识参考层（外部框架速查，含代码示例豁免）"与"多目录共享/委托式 Vision 层"的合法形态与约束（R1-10/R1-11/R2-9/R4-5/R4-6/R5-5 根治）
2. **roadmap I6 占位扫描口径对齐**：占位枚举 `_vfs/**/*.xpl|*.xlib|*.expr|*.xbiz` 与设计稿（xpl/xlib 确定 + 其余按 live 清点定稿）不一致，`.expr` 非 live 类型——W3 回填 I6 时以设计口径为准（R2-2/R4-4/R5-候选1）
3. **对拍 harness 实现契约**：后端身份断言与强制路由 API（I1 验收）；对拍 corpus 构成（静态资源样例 + 动态字符串样例配比）（I1 验收）
4. **SHARED 池化形态并发正确性验证**载体落地（I4 验收标准，设计已定稿载体与归属）（R1-6）
5. **Context 池租借 reset/注入协议实现细节**（I4；契约已定稿于 02 §五）（R1-7）
6. **truffle 翻译缓存失效/淘汰机制实现细节**（resourcePath+树指纹键已定稿；上限/LRU 归实现）（R4-1）
7. **单元级 truffle 翻译失败的生产观测语义**（I4/I5）（R5-4）；租户/差量树分化的观测事件分级（I2/I6）（R5-3）
8. **编译单元资源类型全集清点**（xtask/xgen/xrun、嵌入 api/wf/xbiz 内 xpl 片段等取舍，显式记录纳入/排除及理由）（I6）（R2-2/R3-2/R5-候选1）
9. **模板单元入口包装器契约**（java §七已声明"随 I1 定稿"）与无 resourcePath 动态源翻译缓存形态（truffle §七"归 I3/I4"）——W3 确认对应 I 项验收标准包含之（R3-1/R3-6 遗留实现细节）
10. **扫描清单与生成类清单的产物格式/存放路径**（I6）（R4-候选4）
11. **truffle 模块"可不进镜像"的构建排除机制**（profile/classifier，I6/W3）（R4-候选5）
12. **`ExpressionExecutor` 系动态编译出口清单化核验**（统一裁决入口契约落地审计点，I5）（R4-候选6）
13. **降级观测的日志/指标命名契约**（I5 或 docs-for-ai）（R2-候选7/R4-候选1）
14. **Q5 条件钉版确认已提前关闭**（round-1 实测 25.2.4 构件 class 61/overlay ≤21），I3 常规冒烟复核即可（W3 更新 I3 验收表述时可引用）
15. **静态资源 JVM 形态下生成物缺失时"不借道 truffle"的机会成本量化**（I7 基准顺带）（R1-候选6）
16. **roadmap 纪律 5 依赖白名单是否补记 `nop-xlang-java -.-> nop-javac` 构建期可选诊断边**（R1-2 后续/R5-2）
17. **W3/W4 执行清单加入"回检设计文档 I 系条目号引用"步骤**（R3-3）

## 轮次链与独立性记录

| 轮 | 报告 | 审查者 task/session | Verdict |
|---|---|---|---|
| 1 | `2026-08-19-design-review-round-1.md` | `ses_fe5c91112ffeLVranQ62BOsLED` | FAIL（0 P0/2 P1/11 P2） |
| 2 | `2026-08-19-design-review-round-2.md` | `ses_fe5bac052ffeL3b99FpkdW8oaN` | FAIL（0 P0/1 P1/8 P2） |
| 3 | `2026-08-19-design-review-round-3.md` | `ses_fe5ab62eeffe3HVrc45RXgo748` | FAIL（0 P0/1 P1/6 P2） |
| 4 | `2026-08-19-design-review-round-4.md` | `ses_fe5a1f4f6ffenIOEukaFfreksd` | FAIL（0 P0/2 P1/6 P2） |
| 5 | `2026-08-19-design-review-round-5.md`（本报告） | `ses_fe5987b83ffeEVJLYPtFQMJTYP` | **PASS（0 P0/0 P1/6 P2）** |

五轮均为独立 fresh session（task id 互不相同，各报告首部标识）。每轮 P0/P1 发现均在下一轮开启前修复并在该轮报告"回应段"逐条记录（round 1-4），无静默跳过。

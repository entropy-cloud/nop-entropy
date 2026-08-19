# W2-review Round 4 设计审查报告（xlang-execution / xlang-java / xlang-truffle）

> Mission: xlang-execution-optimization
> Plan: `ai-dev/plans/xlang-execution-optimization/2026-08-19-2050-2-w2-design-review-gate.md`（Phase 2）
> Round: 4
> Date: 2026-08-19
> Reviewer: 独立子 agent（fresh session，opencode task `ses_fe5a1f4f6ffenIOEukaFfreksd`，模型 zhipuai-coding-plan/glm-5.2；未参与撰写与前轮审查、刻意未读前轮报告——独立性成立）
> Verdict: **FAIL**（0 P0 / 2 P1 / 6 P2 → 修复后进入 round 5）

## 审查输入

同一输入包（三组设计文档 round-3 修复后版本），三级判级口径与文档式证据格式在 prompt 中声明。全量复审。

## Findings（审查者原文）

### [R4-1] Truffle 翻译缓存按 resourcePath 键控于 language 实例作用域，与 RCM 资源变更/多租户 live 行为冲突，且无失效/一致性策略 — **P1**

- **文档**: `xlang-truffle/02-architecture-baseline.md` §七、§三；`xlang-execution/01-architecture-baseline.md` §六
- **证据**: 02 翻译缓存"按 resourcePath、language 实例作用域"；统一架构 §六声明 RCM"资源变更检测、多租户缓存隔离维持现状"。live：`ResourceComponentManager.java:80,112,426` + `ResourceTenantManager.java:66`（`CFG_TENANT_RESOURCE_ENABLED`/`supportTenant(path)`）——同 resourcePath 可按租户解析为不同内容、缓存按租户隔离；资源变更后 RCM 重载。全文无失效/指纹/租户条款。
- **现状**: truffle 侧缓存键 = resourcePath、作用域 = 进程级，对"同路径不同树"（租户差异、热变更重载）既无失效机制也无指纹校验；java 侧对同一问题有显式指纹纪律，对待不对称。
- **风险**: 租户 B 执行到租户 A 的已翻译 AST（静默错逻辑）；热更新后静默执行旧 AST——正是 java 侧自认的"最危险缺陷形态"。
- **建议**: 键中纳入树指纹（与 java 侧对称）或租户维度，或规定绑定结果仅随 RCM ComponentCacheEntry 持有。
- **信心水平**: 文本缺口确定；故障场景很可能

### [R4-2] 对拍矩阵"列适用性"未定义：三后端不变式对动态单元结构性不可满足 — **P1**

- **文档**: `xlang-execution/00-vision.md` §四.1；`01-architecture-baseline.md` §五；`xlang-truffle/00-vision.md` §三.1
- **证据**: vision 无条件断言"三后端执行……必须一致"；truffle 00 收窄为两层；统一 §五矩阵规则仅"后端未启用→跳过"与"单元级降级→FAIL"两条，对清单外/动态单元（走动态路径、java 不绑定）三条例均套不上。
- **现状**: 未定义"java 列仅适用于扫描清单内单元；动态单元为两列"，顶层不变式与 truffle 两层表述未调和。
- **风险**: I1 必须自行发明该决策；严格执行则动态单元永无 PASS 路径，宽松执行则违反防 vacuous-pass 初衷。
- **建议**: §五补列适用性裁定（静态三列/动态两列），vision §四.1 改"在适用后端上结果一致"。
- **信心水平**: 确定

### [R4-3] 类名引用大小写错误：`XlangConstants`（live 为 `XLangConstants`） — **P2**（已修复轮次内笔误）

### [R4-4] roadmap I6 扫描枚举与设计文档口径不一致 — **P2**（roadmap 非 W1 产出 → 移交 W3）

### [R4-5] xlang-java 无本目录 Vision 文件，偏离 guide 字面 — **P2**（guide 非 W1 产出 → 移交 W3，与前轮合并）

### [R4-6] 01 知识层代码级内容豁免仅 README 单方声明，guide 无对应条款 — **P2**（同上 → 移交 W3）

### [R4-7] 01 知识层"路 A = 每线程一 Context"与 02"池租借、拒绝固定绑定"表述张力 — **P2**

### [R4-8] java §五绑定伪代码省略"java 后端启用"前置条件 — **P2**

### 无 finding 维度结论

- **① 内容点覆盖 15/15**、**⑤ 知识层清账**（逐节扫描 + 清账表对应 + 移交清单处置明确）：已核对、无 finding。
- **③④**：除上列 finding 外一致（R4-1/R4-8 属一致性/可行性项）。
- **总评**：truffle 缓存一致性是本轮最大缺口（java 侧纪律未延伸到 truffle 侧）；对拍规则精密但漏列适用性基座；其余低危。

### 严重程度分布

| 级别 | 数量 | 编号 |
|---|---|---|
| P0 | 0 | — |
| P1 | 2 | R4-1, R4-2 |
| P2 | 6 | R4-3 ~ R4-8 |

**Verdict: FAIL**

## 回应段（主 agent 逐条处置记录，round 5 开启前置条件）

| Finding | 处置 | 落点 |
|---|---|---|
| R4-1 (P1) | **已修复**：翻译缓存键改为 **resourcePath + 树指纹**（与 java 侧指纹纪律显式对称），并写明理由（RCM 多租户/资源变更现状下纯路径键会"同路径不同树"串用旧 AST = java 侧自认最危险缺陷形态；键含指纹后不同树自然分键、不依赖失效通知、旧条目容量淘汰）；§三"语言实例只存可共享数据"同步键口径 | truffle 02 §七、§三 |
| R4-2 (P1) | **已修复**：统一架构 §五新增"列适用性（矩阵构成规则）"——静态单元（扫描清单内）三列、动态单元两列（java 列"不适用"，区别于"未启用跳过"与"降级 FAIL"）；vision §四.1 不变式补口径细化（"在适用后端上结果一致"+ 分类规则指针），与 truffle 00 两层表述调和 | execution 01 §五、00-vision §四.1 |
| R4-3 (P2) | **已修复**：`XlangConstants` → `XLangConstants`（主 agent 大小写敏感复核 live 接口名确认） | java 01 §六 |
| R4-4 (P2) | **裁定遗留**：roadmap I6 占位枚举非 W1 产出；W3 回填 I6 时以设计文档口径为准（并入移交清单，round-2 第 6 项已覆盖） | 移交 W3 清单 |
| R4-5 (P2) | **裁定遗留**：guide 非 W1 产出（并入移交清单 guide 条款项，round-1/2 已覆盖） | 移交 W3 清单 |
| R4-6 (P2) | **裁定遗留**：同上 | 移交 W3 清单 |
| R4-7 (P2) | **已修复**：01 §一.3 与 §4.4 补"（落地为池租借而非线程固定绑定，见 02 §五）" | 01-truffle-knowledge §一.3、§4.4 |
| R4-8 (P2) | **已修复**：§五伪代码入口补 `if java 后端未启用: executable = tree; return`（标注为统一决策树静态路径首条件） | java 01 §五 |

## 移交 W3 清单（round 4 汇总，并入累计清单）

- （新）R4-1 修复后的具体失效/淘汰机制实现细节 → I3/I4 验收标准
- （新）R4-2 修复后的对拍 corpus 构成（静态样例 + 动态样例配比）→ I1 验收标准
- （新）扫描清单与生成类清单的产物格式/存放路径具体化 → I6
- （新）truffle 模块"可不进镜像"的构建排除机制（profile/classifier）→ I6/W3
- （新）`ExpressionExecutor` 系全部动态编译出口的清单化核验（统一裁决入口契约落地审计点）→ I5
- （新）降级观测的指标命名契约 → I5
- （承）前轮清单全部条目（合并见 PASS 轮汇总）

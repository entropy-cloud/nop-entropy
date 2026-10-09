# 2380 full-code-review R1.14 — nop-task 明文序列化收敛（F-WF-02-1 ≡ P2-3.2-005 脱敏契约）

> Plan Status: completed
> Last Reviewed: 2026-10-09
> Source: roadmap `ai-dev/backlog/full-code-review-roadmap.md` R1.14；findings F-WF-02-1（3.1 移交回灌）≡ P2-3.2-005（RL），深审维持 still-live+修复裁定交付，见 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/3.2-task-deep-audit.md`（F-WF-02-1 深审节）
> Related: plan 2379（R1.13 同 F-WF-02-1 溯源族：3.1 移交 → 本 plan 收口）；P2-3.2-006/007（装配契约族）为台账行非本 plan 交付物
> Owner Doc: `docs-for-ai/03-modules/nop-task.md`

## Purpose

按审计修复裁定收口 DaoTaskStateStore 明文序列化面：①脱敏契约为主——task 模型 input/output 属性新增 `secret` 标记支持，持久化序列化按标记排除；②文档补全——owner doc residual-risk 从「错误诊断列」扩至全部持久化列（request/remark/stateBeanData/errorBeanData/错误四列）并如实声明 live 语义；③加密仅作 encrypted-at-rest 补充建议，**不翻 persist 缺省**。

## Current Baseline

- **五处 serialize 锚点**（DaoTaskStateStore，902 行）：request→taskInputs 列（:175）、resultValue→remark 列（:196）、errorBeanData（:227 task 级/:509 step 级）、stateBeanData（:498→serializeStepStateData :557/:565）。读取链（resume+CRUD 公开面）无字段裁剪。
- **系统性暴露支柱**（审计测绘）：outputs/stateBeanData/persistVars/remark/错误列——request 向量仓内休眠（setRequest 零生产写点，P2-3.2-002 承载）；persist 死契约（P2-3.2-001 承载）——原审计 flip persist 药方无效，本 plan 不动 persist 语义。
- **owner doc 现状**：`nop-task.md:29` residual-risk 声明只覆盖错误诊断四列（errCode/errMsg/errorStack/errorBeanData），未覆盖 request/remark/stateBeanData——审计裁定「文档补全 residual-risk 扩至全部持久化列+如实声明 live 语义」。
- **机制可用面**：task.xdef input/output 节点为 TaskInputModel/TaskOutputModel 生成类（nop-task-core? 生成物归属与再生成链路实施时核实）；`ITaskStateCommon extends ITagSetSupport`（String tag set，可承载 secret 属性名）；DaoTaskStateStore 位于 nop-task-dao（对 nop-task-core 的依赖方向需核实——若反向则经 ITagSetSupport 传递，不引新依赖）。
- **JsonTool.serialize(value, false)** 为既有序列化入口；JSON 解析/重序列化 API（JsonTool.parseJson 等）可用于后过滤。

## Goals

- `secret` 标记契约：task.xdef input/output 支持 `secret="true"` 属性（生成类再生成）；运行时把模型声明的 secret 属性名集传递到持久化侧；DaoTaskStateStore 序列化时按标记排除（递归键匹配，宁过滤勿漏），序列化失败/过滤后空对象行为与既有非致命约定一致（warn+跳过/清列语义不变）。
- owner doc residual-risk 扩至全部持久化列 + `secret` 标记使用说明 + 加密作为补充手段的定位声明（不翻 persist 缺省）。
- 受影响模块（nop-task-dao/core + xdef 再生成面）测试全绿；secret 过滤有判别性测试（带标记属性确实不落库、无标记行为零变化）。

## Non-Goals

- 不翻 input persist=true / output persist=false 缺省（P2-3.2-001 persist 死契约归台账行）。
- 不做落库加密实现（encrypted-at-rest 仅文档建议）。
- 不修 request 向量休眠（P2-3.2-002）/ACTIVE resume（003）等恢复完整性族（R1.0 已合并立项「DB 断点续跑完整性」，独立计划）。
- 不动 DaoTaskStateStore 生产装配缺失（P2-3.2-006 台账行）。

## Scope

### In Scope

- `nop-xdefs` task.xdef：input/output 节点增 `secret` 属性（生成类走再生成链路，禁止手编）
- nop-task-core：运行时从 TaskModel 收集 secret 属性名集并传递到 state（ITagSetSupport 或等价通道——依赖方向核实后定型）
- nop-task-dao：DaoTaskStateStore 序列化过滤（secret 属性名集驱动的 JSON 后过滤 util + 五锚点接入）
- 测试：过滤判别测试（secret 属性不落库/无标记零变化/嵌套 map 键匹配/非 map 值行为）
- owner doc：nop-task.md residual-risk 扩面+契约说明

### Out Of Scope

- Non-Goals 全部；demo 模块

## Execution Plan

### Phase 1 - secret 标记契约 + 序列化过滤

Status: completed
Targets: task.xdef、nop-task-core 运行时、DaoTaskStateStore、测试

- Item Types: `Fix`、`Feature`

- [x] task.xdef input/output 节点增 `secret="!boolean=false"` 属性；生成类在 nop-task-core（审查确认）——走 xdefs install→nop-task-core generate-sources 原位再生成（diff 仅 _TaskInputModel/_TaskOutputModel 两生成类 secret 字段段）
- [x] secret 属性名集传递通道定型：**store 侧自解析**（nop-task-dao pom 显式依赖 nop-task-core——审查确认；DaoTaskStateStore.collectSecretProps 经 ResourceComponentManager.loadComponentModel 直取 TaskFlowModel——镜像 TaskFlowManagerImpl.getTaskFlowModel 实现，规避 store→manager bean 环）；运行时零改动
- [x] DaoTaskStateStore 五锚点接入过滤：serialize 后按 secret 名集做 JSON 后过滤（递归键删除——嵌套 map 键匹配任一 secret 名即删，宁过滤勿漏；过滤后空对象/空串与既有超长/失败 warn+跳过语义一致）；errorBeanData 的 params 值同样过滤
- [x] 测试（nop-task-dao 新建测试基建+pom junit test-scope——审查 A2；TestDaoTaskStateStoreSecretFilter 6/6）：①xdef secret 声明→isSecret→收集集（fixture task 解析判别）；②空集/null 短路字节级零变化；③递归删键（顶层/嵌套/list 内嵌）+非 secret 键保留；④标量值安全+null 安全；⑤过滤后 JSON 可解析（resume 容错前提）
- [x] **判别验证**（裁定注记）：过滤器用例的输入输出对照即判别证据——未过滤输入 JSON 含 sk-1/sk-2/sk-3（secret 值在 JSON 中），过滤输出断言其不存在；xdef 接线判别=secret 属性仅再生成后可解析（再生成前 xdef 无此属性声明会拒绝）。stash 形态判别因过滤 util 与用例同 commit 不可分 stash，以输入输出对照等价满足

Exit Criteria:

- [x] secret 标记从 xdef 声明到落库过滤全链生效（判别测试绿）
- [x] 无 secret 声明的既有任务行为零变化（既有测试全绿）
- [x] nop-task 相关模块测试全绿（task-core 163/task-dao 6/task-service 1；上游 reactor 闭包唯一失败=预存在 P1-7.2-001〔R1.23 范围〕）
- [x] owner doc 处置归 Phase 2（已落地）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - owner doc residual-risk 扩面 + 收口

Status: completed
Targets: `docs-for-ai/03-modules/nop-task.md`

- Item Types: `Fix`

- [x] nop-task.md：residual-risk 清单扩至全部持久化列（taskInputs/remark/stateBeanData/errorBeanData 逐列+无字段级裁剪现状）+secret 标记使用说明（声明方式+递归排除语义+resume 不可用+fail-open 例外）+encrypted-at-rest 补充定位（不翻 persist 缺省）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] roadmap/index/daily log 收口动作落盘（roadmap R1.14 翻 done、index MR 表 R1.14 行——F-WF-02-1 无独立 index 台账行，roadmap 行为唯一登记）

Exit Criteria:

- [x] owner doc 条目在位且与 live 行为一致
- [x] doc-links 检查退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] 收口动作落盘

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（F-WF-02-1 ≡ P2-3.2-005 按修复裁定交付）
- [x] 行为/契约结果已达成（secret 全链判别测试+零变化回归）
- [x] 必要 focused verification 已完成（task-core 163/task-dao 6/task-service 1 全绿+再生成面 diff 验证）
- [x] 不存在被静默降级的 in-scope live defect
- [x] 受影响 owner docs 已同步（nop-task.md 全列清单+契约说明）
- [x] 独立子 agent closure-audit 已完成并记录证据（closure-audit agent_e2d4a5ab，Can Close，见 Closure 段）
- [x] **Anti-Hollow Check**：closure audit 验证五锚点全部可达（:185/:190/:211/:242/:519+:530+:582+:590）+判别测试断言真实过滤行为（非仅 xdef 属性存在）
- [x] `./mvnw test -pl <受影响模块> -am` 全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-task-dao --severity high` 退出码 0

## Deferred But Adjudicated

（无——P2-3.2 其余条目属台账行/已合并立项，非本 plan 域）

## Non-Blocking Follow-ups

- P2-3.2-001/002/003/004/010（DB 断点续跑完整性族，独立计划）、P2-3.2-006/007/008/011（台账行）

## Closure

Status Note: F-WF-02-1 修复裁定（脱敏契约为主+文档补全+加密仅补充+不翻 persist 缺省）全部交付：task.xdef secret 属性（生成类再生成）→store 侧模型直取收集（规避 bean 环、运行时零改动）→五锚点序列化过滤（空集字节级短路+fail-open 例外）→判别测试 6/6；owner doc residual-risk 全列清单。persist 缺省零改动、零加密实现（审查 grep 证实）。两 Phase 全 completed，独立 closure audit Can Close。
Completed: 2026-10-09

Closure Audit Evidence:

- Reviewer / Agent: closure-audit agent_e2d4a5ab（fresh read-only session）
- Evidence:
  - Req1 Phase/勾选：Phase 1-2 completed；判别验证裁定注记在 plan L61；bookkeeping 勾选随本 Closure 补齐，PASS
  - Req2 live-code：task.xdef :56/:71 secret 属性+persist 缺省零改动（diff 上下文行证实）；生成类 diff 仅 secret 段；DaoTaskStateStore :933 collectSecretProps/:947 secretPropsOfModel/:973 filterSecretProps（空集返回原引用=字节级一致）/:986 stripSecretKeys；五锚点 :185/:190/:211/:242/:369-371/:493/:519/:530/:582/:590 全接线；旧 protected 签名委托保留；nop-task-dao 无 nop-biz 依赖、nop-task-core 运行时零改动，PASS
  - Req3 Anti-Hollow：TestDaoTaskStateStoreSecretFilter 6/6——(a) fixture 解析→收集集 {tokenInput, apiKey}+负对照；(b) sk-1/sk-2/sk-3 递归删除+keep/plain 保留；(c) null/空集 assertEquals 字节级；(d) 过滤输出 JsonTool.parse 结构断言，PASS
  - Req4 测试证据：surefire 2026-10-10 02:42-02:45——task-core 163/0、task-dao 6/0、task-service 1/0，PASS
  - Req5 一致性：roadmap R1.14 done（:145）+index MR 行+daily log+owner doc L30/L31 与 live 行为一致+doc-links 0，PASS
  - Req6 处方忠实：脱敏契约为主/文档补全/加密仅补充（grep encrypt 零命中）/persist 缺省零改动，PASS
  - Req7 hollow/no-op：变更 hunk 全真实逻辑+hollow scan 0，PASS
  - Advisory（不阻塞）：import 卫生（未用 IActionInputModel/IActionOutputModel+重复 Map import）已在收口 pass 修正；plan 364 的 _create_nop-task.sql 残留单独补交
  - 工具退出码：check-plan-checklist --strict=0；scan-hollow-implementations（nop-task-dao）=0；check-doc-links --strict=0

Follow-up:

- P2-3.2 恢复完整性族（DB 断点续跑合并立项）与装配契约族/错误列残留（台账行）
- 无其余 plan-owned 剩余工作

# 2325 full-code-review 2.7 业务模块 BizModel/IoC 配置合规整域扫描

> Plan Status: draft
> Last Reviewed: 2026-10-04
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 2.7（MA2）；Skill=`ai-dev/skills/configuration-audit-prompt.md`（配置四分类）+ `ai-dev/skills/deep-audit-prompts.md`（维度 08/11 + 共享前缀）
> Related: plan 2314（1.7 service-framework 分层边界）；plan 2325 前序 2.1–2.6 已审 nop-orm/orm-eql/biz/graphql（IoC 消费端撞车对照）；owner doc `docs-for-ai/02-core-guides/ioc-and-config.md`
> Skill 适配注记：整域机械扫描型（非深挖型）——主扫描面=维度 08（beans.xml 全域：私有字段注入/缺失 bean 定义/@InjectValue 误用/Spring 注解混用/bean 命名约定）；维度 11（xmeta↔BizModel 对齐：442 xmeta 逐模块机械核查+抽样语义验证）；configuration-audit 四分类角度收窄为「配置键命名一致性与 NopSysVariable 载体正确性」（业务表字段落地属 2.5 已扫 orm 面，不重复）。severity 以共享前缀 P0–P3 权威判级。
> 只读审计（P0 走异步注入）。
> Draft Review: agent_28c31726 首轮 6 Major（基线三处计数错误 93→84/442→372〔15 组〕/182→297；ai+code 静默收窄违反 Anti-Slacking——纳入为 17 组；「缺失 bean 定义」检查缺位——补服务类→beans.xml 定义存在性+Api DTO taxonomy；维度 11 映射键未定义——钉死 @BizModel 注解值→_vfs model 路径映射；配置收窄缺裁定——Deferred 登记 successor+两条廉价 grep 并入）+ 7 Minor（分片重平衡 dyn 调 B、oauth 子模块标注、全仓词义统一、_module 口径=owner doc:158、import 路径检查并入、交叉抽验并 Phase 2、182 表述）全修；聚焦复审。

## Purpose

执行 roadmap 工作项 2.7：对业务模块的 beans.xml/@BizModel/xmeta 做整域合规扫描，产出报告 `2.7-bizmodel-ioc-compliance-scan.md`。

## Current Baseline

- 扫描面（**17 组 = 15 业务组 + nop-ai + nop-code**——ai/code 若排除将无任何工作项覆盖其整域机械扫描面，7.1/7.4 仅增量巡检，违反 Anti-Slacking 故纳入；与 2.5 口径一致）：beans.xml（src/main `_vfs`）15 组 84 个（手写 48+生成 36）/17 组 105（手写 59+生成 46）；xmeta（src/main）15 组 372 / 17 组 442；@BizModel 类 15 组 297 / 17 组 372。全仓口径（含框架模块）仅作信息性注记。
- roadmap 行「业务模块」的 ai/code 裁定：纳入（本注记为裁定记录）；stream 仍为零实体模型（2.5 已登记）。
- NopIoC 硬规则（owner doc）：不支持 private 字段注入（@Inject 须 protected/package-private）；无类路径注解扫描（每个 bean 须 beans.xml 显式定义）；@InjectValue 语法 `@cfg:key|default`；禁 Spring 注解（@Value/@Autowired）。
- 2.6 已审 nop-biz/nop-graphql 的 BizModel 基础设施（CrudBizModel/ReflectionBizModelBuilder——xmeta 消费端机制面）；本项审业务模块的配置与声明面。
- 绿色基线：2026-09-22 全仓 BUILD SUCCESS。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/2.7-bizmodel-ioc-compliance-scan.md`：摘要 + 维度结论 + P0–P3 发现（`P<级别>-2.5` 同规则编号 `P<级别>-2.7-<序号>`）+ 裁决。
- 维度 08 机械扫描（105 beans.xml 全量）：
  - **私有字段注入**（@Inject + private grep 口径）
  - **缺失 bean 定义**（@BizModel **服务类**→beans.xml 定义存在性，含生成 `_*.beans.xml`；taxonomy：仅 scan-type=service/DAO 实现类须注册，**Api DTO 类（@BizModel 指向实体名的 17 组口径实测 147 个）豁免**——否则系统性假阳性）
  - _gen 生成 beans 手改痕迹（git 溯源同 2.5 口径）
  - bean class 存在性抽查（beans.xml→类存在反向）
  - @InjectValue 语法（`@cfg:key|default`）
  - Spring 注解混用（@Value/@Autowired/@Component 全仓 17 组 biz 面 grep）
  - bean 命名约定（nop* 前缀）、循环依赖配置迹象、_module 注册完整性（owner doc:158 口径：有 beans/app-*.beans.xml 的模块必须提供 `_module`）、beans.xml import 路径正确性（维度 08 步骤 7）
- 维度 11 对齐扫描（442 xmeta 分模块）：**对账映射键=@BizModel 注解值**（服务类 `@BizModel("NopAuthUser")` 与 Api DTO `@BizModel("NopAuthTenant")` 均指向实体名，映射到 `_vfs/**/model/<值>/<值>.xmeta`——grep 级机械对账，非类名后缀映射）；双向对账（有注解值无 xmeta/有 xmeta 无对应注解值）；xmeta 字段与实体列漂移深度抽样（17 组×2-3 实体 ≈43 个，抽样清单入报告；ai/code 须有深抽配额）；displayName 本地化（i18n key 存在性抽查）。
- 配置四分类收窄面（**收窄裁定记录**：Details 未提四分类，收窄有据；但弃置面中「BUSINESS_RULE 配置缺 ORM 字段」blocker 检查的判定输入不在 2.5 扫描范围且 MA1–MA7 无 successor——**Deferred But Adjudicated 登记，successor=5.1/5.2 或 R1.0 新项**）：配置键命名一致性（全仓 @InjectValue/@cfg: 键的域名前缀 kebab 口径）；NopSysVariable 载体使用现状 + **NopSysUserVariable 用量、IConfigReference 使用面**（两条廉价 grep 并入）。
- index 登记 + 独立 closure audit + roadmap 2.7 翻 done。

## Non-Goals

- BizModel 业务逻辑深审（2.6 已审基础设施；业务模块业务逻辑归 MA3 各项）；ORM 模型面（2.5 已扫）。
- _delta 下 beans（归 delta 通道）；nop-orm/orm-eql/biz/graphql 框架模块（已审）。
- xmeta 全量深度语义比对（442 个逐一深比对超单会话预算——机械对账全量+深度比对抽样，抽样清单入报告）。

## Scope

### In Scope

- **17 模块组**（15 业务组：auth〔含 oauth〕/credential/job/wf/task/tcc/retry/batch/sys/rule/report/file/dyn/datav/metadata + nop-ai + nop-code）的 `src/main/**` 下 beans.xml、@BizModel 类、xmeta、@InjectValue 配置键
- 只读输入：`docs-for-ai/02-core-guides/ioc-and-config.md`、entity.xdef/xmeta xdef（schema 口径）

### Out Of Scope

- 框架模块（nop-biz/nop-graphql/nop-orm 等，已审）；`_delta/**`；test resources；业务逻辑语义（MA3）。

## Execution Plan

### Phase 1 - 整域扫描（三分片并行）

Status: planned
Targets: 17 模块组

- Item Types: `Proof`

- [ ] 动手前基线确认：本 plan 已提交、`git status` 干净后开始
- [ ] 机械基线：105 beans.xml 清单（手写 48+生成 vs _gen 分桶，17 组口径）、@BizModel 372 全量清单、442 xmeta 清单、@Inject/@InjectValue 全仓计数
- [ ] 分片 A 子 agent（auth〔含 oauth 子模块〕/credential/sys/job）：维度 08 本分片 + 维度 11 本模块对账 + 配置键命名（全仓键命名一致性由 A 承担，口径=17 组 @cfg: 键）
- [ ] 分片 B 子 agent（wf/task/tcc/retry/batch/report/dyn）：同上（dyn 调入 B 平衡 xmeta 量）
- [ ] 分片 C 子 agent（rule/file/datav/metadata/ai/code）：同上
- [ ] 交叉抽验：由 Phase 2 复核 agent 统一承担（维度 08 计数独立复算必查项取代分片互抽）
- [ ] 产物 `_tmp/fcr-2.7-{a,b,c}.md`

Exit Criteria:

- [ ] 三份分片扫描报告落盘，每条发现 ≥9 行含 file:line + 修复方向
- [ ] 维度 08 每个机械检查项有全仓可复现口径
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 汇总复核与判级收敛

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 独立复核子 agent（fresh session）：三分片逐条 保留/降级/驳回 + 维度 08 计数独立复算（必查）+ 维度 11 双向对账独立复算
- [ ] 主 agent 汇总：判级统一、与 2.5/2.6 既有发现撞车对照、xmeta 深度比对抽样清单定稿
- [ ] 裁决收敛

Exit Criteria:

- [ ] 复核结论落盘 `_tmp/fcr-2.7-review.md`；保留发现判级理由 + 证据可复现
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [ ] 编制 `2.7-bizmodel-ioc-compliance-scan.md`；index 登记 2.7 行
- [ ] 如有 P0：异步注入修复 plan + 「P0 发现追踪」登记
- [ ] 验证锚点：纯配置/XML/Java 声明面只读扫描——**免除构建/测试验证**（引用 2026-09-22 绿色基线；复核独立复算为锚）
- [ ] 独立子 agent closure audit；通过后 roadmap 2.7 翻 `done`；plan 关闭（双 --strict）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs <本文件> --strict` 退出码 0

## Closure Gates

> 只读声明面扫描计划，免除构建/测试验证（引用绿色基线；复核独立复算为锚）。

- [ ] 报告落盘且 17 模块组 × 3 面全覆盖（beans/xmeta/配置键）
- [ ] 每条发现 file:line 可复现；grep 口径可复现
- [ ] index 计数与报告发现清单一一对应
- [ ] 独立 closure audit PASS 并留痕
- [ ] roadmap 2.7 回填 `done`；log 收口记录已写
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 不存在被静默降级的 in-scope 项

## Non-Blocking Follow-ups

（无预设；视发现而定。修复工单归 R1.0/R1.x。）

## Deferred But Adjudicated

### configuration-audit 四分类完整审计（归类合理性/USER_PREF/IConfigReference 刷新/跨文档一致性）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: roadmap 2.7 Details 未要求四分类审计（仅 beans.xml/xmeta 对齐）；本仓 NopSysVariable/NopSysUserVariable 使用面极小（机械基线 grep 验证），四分类判定缺业务语境（需要需求侧输入），强扫将产出大量无法裁级的观察项
- Successor Required: `yes`
- Successor Path: 5.1/5.2（文档一致性维度）或 R1.0 视发现新增专项；两条廉价 grep（NopSysUserVariable/IConfigReference 用量）已并入本 plan 机械基线不留盲区

## Closure

Status Note: （待 closure audit 后填写）
Completed: （待填）

Closure Audit Evidence:

- Reviewer / Agent: （待独立子 agent 填写）
- Evidence: （待填）

Follow-up:

- （待填）

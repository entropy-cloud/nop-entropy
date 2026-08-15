# 2026-08-16-0549-3 nop-metadata IoC 装配债务与注释真值化族批次清扫（P2-02/P2-30/P2-31/P2-03）

> Plan Status: active
> Mission: nop-metadata-invariant-loop
> Work Item: 2026-08-15 multi-audit Follow-up Backlog — IoC / 架构债务族（P2 批次清扫）
> Last Reviewed: 2026-08-16
> Source: `ai-dev/backlog/nop-metadata-invariant-loop-roadmap.md` Follow-up Backlog（IoC / 架构债务族）；审计源 `ai-dev/audits/2026-08-15-0559-multi-audit-nop-metadata-invariant-loop.md`（P2-02/P2-03/P2-30/P2-31）
> Related: 执行顺序：在 `2026-08-16-0549-1`、`2026-08-16-0549-2` 之后（0549-2 Phase 2 会重写 `TestNopMetaQualityCheckpointBizModel` 部分断言——本计划 Phase 1 的该文件例数基线以执行时点 live 实数为准，不硬编码；0549-1/0549-3 同编辑 owner doc 模块结构段不同行，顺序执行避免冲突）。**P2-27（orm.xml 陈旧注释）不属本计划**：源模型文件编辑（含 comment-only）统一随 ORM 族轮次走人工确认门（mission 授权），见 Non-Goals。

## Purpose

消除 `NopMetaQualityCheckpointBizModel ↔ MetaQualityCheckpointScheduler` 双向注入环（P2-02），把两处误导性注释（P2-30/P2-31）真值化，并补齐 OrmModelImporter 驻留裁定与 owner doc 模块结构表缺口（P2-03）。

## Current Baseline

以下事实均于 2026-08-16 live 核对：

- **P2-02（双向注入真环）**：`MetaQualityCheckpointScheduler:110-113` setter `@Inject` 注入 `NopMetaQualityCheckpointBizModel`；`NopMetaQualityCheckpointBizModel:89-91` 字段 `@Inject @Nullable` 注入 `MetaQualityCheckpointScheduler` → 构成真环。NopIoC 默认 allow-cycle 下零故障；严格模式（禁环）下将爆炸——架构债务。scheduler 注册于 `nop-metadata/nop-metadata-service/src/main/resources/_vfs/nop/metadata/beans/app-service.beans.xml:40`（唯一注册点）。
- **BizModel 侧 scheduler 字段的全部使用点**（live 清点）：仅 `notifySchedulerRegister`（:291-295）与 `notifySchedulerUnregister`（:304-308）两处 null-guard 调用——字段移除面封闭、无隐藏消费者。既有测试有一处反射依赖该字段：`TestNopMetaQualityCheckpointBizModel.testDeleteFailureKeepsSchedule`（:1053-1059）经 `getDeclaredField("scheduler")` + `assertNotNull` 做 anti-hollow 前置断言——**断环后该断言必须同步重写**（见 Phase 1）。
- **懒解析语义对齐依据**：NopIoC 对 `@Nullable @Inject` 字段在 bean 缺失时注入 null（`DefaultBeanClassIntrospection` optional 语义）；`BeanContainer.tryGetBean` 同为 null-on-missing——懒解析可无损复刻现行 null 跳过语义。仓库内 `tryGetBean` 先例充分（nop-job / nop-gateway / nop-graphql-core）。
- **P2-30（save override javadoc 自相矛盾）**：`NopMetaQualityCheckpointBizModel:262-264` save override javadoc 声称"调度器经 `BeanContainer#tryGetBean` 懒查找（非 `@Inject`），避免构造期循环依赖"——与 :89-91 实际直接 `@Inject` 不符；同文件 :84-88 另一处 javadoc 声称相反事实（"取代 tryGetBean 服务定位器反模式，通过 IoC 注入"）。两处矛盾，且 :262 的错误描述曾误导 P2-02 修复决策（审计原文）。
- **P2-31（BEAN_NAME 注释指向不存在的文件）**：`MetaQualityCheckpointScheduler:83` 注释"与 app-quality-scheduler.beans.xml 一致"——该文件 live 不存在（`find nop-metadata -name "*.beans.xml"` 仅 `_dao/_service/app-service/test-mock` 四个），实际注册于 `app-service.beans.xml`。
- **P2-03（OrmModelImporter 驻留 + owner doc 缺口）**：`OrmModelImporter`（253 行模型映射）live 驻留 `nop-metadata-dao/.../dao/model/`。dao 模块驻留模型载入/映射逻辑的先例：`nop-wf-dao` 的 `dao/store/DaoWorkflowModelLoader`（模型载入器驻留 dao）；`nop-auth-dao` 无严格同形物（最近邻为 `dao/mapper` SqlLibMapper 空接口），不作为同形先例引用。真实缺口 = owner doc（`docs-for-ai/03-modules/nop-metadata.md`）模块结构表 dao 行未列 `model/` 子包。

## Goals

- 双向注入环消除：环的两个方向只剩一个 `@Inject` 方向（scheduler→bizmodel），BizModel 侧改懒解析（按 `BEAN_NAME`）；行为零变化（调度注册/注销/执行链路全绿）。
- 两处误导性注释与 live 事实一致（P2-30 两处 javadoc 收敛为单一真值、P2-31 指向真实注册文件）。
- OrmModelImporter 驻留裁定落档（维持 dao 驻留 + 理由），owner doc 模块结构表补 `model/` 子包行。

## Non-Goals

- **不触碰任何 `*.orm.xml` 文件**——P2-27（`nop-metadata.orm.xml:1684-1694` 陈旧注释，行级互斥表述已被 plan `2026-07-17-0700-1` Phase 1 D1（端点级互斥）/ `ai-dev/design/nop-metadata/01-architecture-baseline.md` §2.5.2 的裁定推翻）随 ORM 结构族轮次一并处理：mission 授权"ORM/API 模型变更执行前人工确认"未区分结构/非结构编辑，comment-only 亦属源模型文件编辑，统一走人工确认门，不由本计划 AI 自行豁免。
- 不引入新调度架构（不拆 scheduler/bizmodel 职责、不迁 cron 注册逻辑到第三方）。
- 不处理 ORM 结构族其余项（P2-01/26/28/29/34）与裁定项（P2-05/33/12）。

## Scope

### In Scope

- `nop-metadata-service/.../entity/NopMetaQualityCheckpointBizModel.java`（P2-02/P2-30）
- `nop-metadata-service/.../quality/MetaQualityCheckpointScheduler.java`（P2-31 注释；P2-02 中 scheduler 侧 `@Inject` 保留不动）
- `TestNopMetaQualityCheckpointBizModel.java`（`testDeleteFailureKeepsSchedule` 反射前置断言重写 + 新增接线测试）
- `docs-for-ai/03-modules/nop-metadata.md`（P2-03 模块结构表 dao `model/` 行 + 驻留裁定表述）

### Out Of Scope

- `app-service.beans.xml` 的 bean 定义结构（环消除经 Java 侧字段变更达成，beans.xml 无需改动；如执行中发现必须同步则停下说明，不扩大）
- 任何 `*.orm.xml` / `_gen/` 文件

## Execution Plan

### Phase 1 - 注入环消除与 javadoc 真值化（P2-02 + P2-30）

Status: planned
Targets: `NopMetaQualityCheckpointBizModel.java`, `TestNopMetaQualityCheckpointBizModel.java`

- Item Types: `Fix`

- [ ] 断环方向裁定（默认方案）：`NopMetaQualityCheckpointBizModel` 侧移除 `@Inject @Nullable MetaQualityCheckpointScheduler` 字段，改为**按 `MetaQualityCheckpointScheduler.BEAN_NAME` 懒解析**；懒解析以 **protected 可覆写解析方法**（lookup seam）承载，`notifySchedulerRegister`/`notifySchedulerUnregister` 经 seam 获取实例，未注册时返回 null 跳过（保留旁路容错语义：失败不影响主路径）。`MetaQualityCheckpointScheduler → BizModel` 的 setter `@Inject` 保留（调度器的 checkpoint 执行是核心路径，维持 IoC 注入）。理由：BizModel→Scheduler 是旁路能力，懒解析代价最小；核心路径不引入服务定位器；seam 使"bean 缺失"态在测试中可注入（见接线测试 (b)）
- [ ] 全仓注入面清点：`rg -l "MetaQualityCheckpointScheduler" nop-metadata -g '*.java' -g '!**/target/**'`——确认除 BizModel 与 scheduler 自身外无其他注入点（如有，一并按同规则处理或登记）
- [ ] P2-30：两处 javadoc（:84-88 与 :262-264）收敛为与代码一致的单一真值表述（懒解析 + 断环理由），消除互相矛盾
- [ ] 既有反射断言重写：`testDeleteFailureKeepsSchedule`（:1053-1059）的 `getDeclaredField("scheduler")` 前置断言改为断**懒解析接线**（seam 返回真实 bean 时 register 真实被调用）——断言强度不降（从"字段存在"升级为"接线生效"）
- [ ] 新增接线测试（Minimum Rules #23，机制已钉死，不动全局容器状态）：(a) scheduler bean 存在时——经容器/注入实例走 save/delete 路径，断言 register/unregister 真实触发（计数器或标志位）；(b) scheduler bean 缺失时——**既有 harness + Mockito spy/doReturn 覆写 lookup seam 返回 null**（运行时子类形态，先例即 `testDeleteFailureKeepsSchedule` 自身 :1069 对 `checkpointBizModel` 的 spy；直调子类不可行——save/delete 先经 super 走容器依赖；不注册/注销全局 provider，无 surefire 同 JVM 状态串扰），断言跳过不抛（旁路容错语义钉死）

Exit Criteria:

- [ ] `rg -n "@Inject" nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaQualityCheckpointBizModel.java` 不再命中 scheduler 注入；环的 `@Inject` 方向仅剩 scheduler→bizmodel 单向（rg 证据入 daily log）
- [ ] **接线验证**：接线测试 (a)/(b) 双态绿，且变异验证——**将 lookup seam 临时改为恒返回 null** → 测试 (a) 红（区分力实证，记录入 daily log；注：恢复双向 `@Inject` 不是有效变异——allow-cycle 下字段注入照常工作、(a) 仍绿，不具区分力）
- [ ] 既有测试零回归（其中 `testDeleteFailureKeepsSchedule` 前置断言按断环终态同步重写，重写后断言强度不降——从字段存在升级为接线生效，重写说明入 daily log）：`TestNopMetaQualityCheckpointBizModel`（例数基线以执行时点 live 实数为准，当前 29；0549-2 Phase 2 可能已重写该文件部分断言）+ `TestMetaQualityCheckpointScheduler*` 全族全绿；`./mvnw test -pl nop-metadata/nop-metadata-service -am` BUILD SUCCESS
- [ ] javadoc 两处与 live 代码一致（无"tryGetBean 懒查找"与"@Inject"并存矛盾）
- [ ] **无静默跳过**：懒解析未命中 bean 时为显式设计语义（旁路跳过 + 已有日志），非吞异常——复核该路径无新增 catch-empty
- [ ] owner doc 模块装配段如提及该注入关系则同步；否则 `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 注释真值化（P2-31）

Status: planned
Targets: `MetaQualityCheckpointScheduler.java:83`

- Item Types: `Fix`

- [ ] BEAN_NAME 注释指向真实注册文件 `app-service.beans.xml`（live 唯一注册点 :40；如宿主可覆盖注册为 live 语义则一并注明）

Exit Criteria:

- [ ] `rg -n "app-quality-scheduler.beans.xml" nop-metadata -g '*.java' -g '!**/target/**'` 零命中；注释指向的文件 live 存在
- [ ] `./mvnw compile -pl nop-metadata -am -T 1C` 通过（注释变更不破坏构建）
- [ ] **No new test required**: comment-only 变更，无行为面
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - OrmModelImporter 驻留裁定与模块结构表补全（P2-03）

Status: planned
Targets: `docs-for-ai/03-modules/nop-metadata.md`

- Item Types: `Decision`

- [ ] 裁定落档：OrmModelImporter 维持 dao 驻留——依赖方向论据（service 依赖 dao，模型映射属 dao 载入域；先例引用 `nop-wf-dao` 的 `DaoWorkflowModelLoader`（模型载入器驻留 dao）；不引用 `nop-auth-dao` 作同形先例——其无严格同形物）
- [ ] owner doc 模块结构表 dao 行补 `model/` 子包（OrmModelImporter 一行：职责 + 驻留理由引用）

Exit Criteria:

- [ ] 模块结构表含 dao `model/` 子包行且与 live 路径一致；`node ai-dev/tools/check-doc-links.mjs --strict` error 数不增（**裁定沿用 0226-1/0226-2/0226-3 收口先例**：17 errors 全部为其他 mission 归属文件的 pre-existing 基线，本计划改动文件 0 新增——AGENTS.md 0-error 规则与跨 mission 基线的冲突已按该先例显式裁定并记录，非静默降级）
- [ ] 裁定（维持驻留 + 先例引用，含 nop-auth-dao 不作同形先例的说明）可在 owner doc 或本 plan 中找到
- [ ] **No new test required**: documentation-only adjudication
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] P2-02 环消除且仅剩单向 `@Inject`（rg 证据）+ 接线测试双态绿 + seam-null 变异验证证据
- [ ] P2-30/P2-31 注释与 live 事实一致
- [ ] P2-03 裁定 + 模块结构表补全
- [ ] `./mvnw compile -pl nop-metadata -am -T 1C` 通过
- [ ] `./mvnw test -pl nop-metadata -am -T 1C` BUILD SUCCESS（0 failures）
- [ ] checkstyle / 代码规范检查通过（Java 改动沿所在文件既有风格）
- [ ] 门禁链复跑零命中（`run-nop-metadata-invariants.sh` 6-guard 全链——沿 1913-3 教训"守卫链应在每个 plan 收口时复跑"）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0
- [ ] roadmap Follow-up Backlog 对应条目（P2-02/P2-03/P2-30/P2-31）标注处置结果
- [ ] 独立子 agent closure-audit 已完成并记录证据

## Deferred But Adjudicated

（无。）

## Non-Blocking Follow-ups

- P2-27（orm.xml :1684-1694 陈旧注释，真值 = plan `2026-07-17-0700-1` Phase 1 D1 端点级互斥 / 架构基线 §2.5.2）→ 随 ORM 结构族轮次（P2-01/26/28/29/34）人工确认门内一并处理
- 裁定需求项（P2-05 行级权限边界 / P2-33 updatable 收紧 / P2-12 i18n ask-first）→ 后续轮次派生时优先裁决

## Closure

Status Note:
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- 待 closure 时填写

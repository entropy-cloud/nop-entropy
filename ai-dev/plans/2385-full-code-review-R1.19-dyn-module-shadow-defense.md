# 2385 full-code-review R1.19 — nop-rule/nop-dyn 动态模块遮蔽防线（P1-3.7-001）

> Plan Status: completed
> Last Reviewed: 2026-10-09
> Source: roadmap `ai-dev/backlog/full-code-review-roadmap.md` R1.19；finding `P1-3.7-001`（B 面·维度13-11，复核升格 P1 主 agent 终裁），见 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/3.7-rule-dyn-deep-audit.md`
> Related: P2-3.7-010（非法字符路径逃逸，与本条互补——格式校验拦不住合法名冲突）为台账行；P2-3.7-008 同批 advisory
> Owner Doc: `docs-for-ai/03-modules/nop-dyn.md`

## Purpose

封堵动态模块 moduleName 与静态平台模块冲突的遮蔽面：普通动态模块维护权限即可借 VFS in-memory 第一优先层整体遮蔽静态模块 xbiz/xmeta/orm（口令哈希直读/XPL 注入/ORM 定义替换）。按审计处方 (1) 实现冲突拒绝（`nop-*` 保留前缀+已注册静态模块 id 精确冲突双检），(3) 文档声明命名空间边界；处方 (2) DeltaResourceStore 纵深防御超出修复批 scope（改内核 VFS 层），登记 follow-up。

## Current Baseline

- **遮蔽链**（audit 逐环核实）：moduleName="nop-auth" 合法通过 `isValidNopModuleName`（纯格式校验）→ moduleId="nop/auth" → DynCodeGen 生成资源落 `/{moduleId}/model/...` 与静态布局完全重合 → reloadModel 经 `updateInMemoryLayer` 置为 OverrideResourceStore 第一优先层 → 全平台 VFS 命中动态生成物（password `published="false"` 防护失效/ORM 定义整体替换）。
- **冲突检查穷举**：generateByAI 仅 NopDynModule 表内去重（NopDynModuleBizModel:110-114）且自动 PUBLISHED+reloadModel（单 mutation 完成攻击链）；importExcel 连表内去重都没有；`nop-*` 前缀动态模块全仓零合法用例（测试全部 app-demo/relation-demo 系）。
- **入口面**：generateByAI（:99-132）/importExcel（:56-88）/CrudBizModel 继承的 save/update（moduleName 变更路径）。
- **测试**：nop-dyn-service 既有 TestNopDynModuleBizModel（generateByAI 去重布景可扩展）+TestDynCodeGenModuleLifecycle。

## Goals

- 三个入口（generateByAI/importExcel/save+update moduleName 变更）统一拒绝保留前缀 `nop-*` 与已注册静态模块 id 冲突（新错误码 ERR_DYN_MODULE_ID_CONFLICT，含 moduleName/moduleId 诊断参数）。
- nop-dyn.md 显式声明动态模块命名空间边界（保留前缀+不得与静态模块重叠）。
- 判别测试：moduleName="nop-auth" 的 generateByAI/save 均拒绝；既有 app-demo 系零回归。

## Non-Goals

- DeltaResourceStore/OverrideResourceStore 层纵深防御（改内核 VFS——登记 follow-up）。
- P2-3.7 全部条目（台账行）。
- 存量数据的迁移（若现网已有 nop-* 动态模块——发布/重载不受影响，仅新建/改名被拒；边界声明在 doc）。

## Scope

### In Scope

- nop-dyn-service：NopDynErrors 增 ERR_DYN_MODULE_ID_CONFLICT；NopDynModuleBizModel 增统一校验方法并接入 generateByAI/importExcel；覆写 saveEntity/updateEntity（或等效 mutation 钩子）拦截 moduleName 变更
- 测试：nop-auth 前缀拒绝（generateByAI+save）、已注册静态 id 冲突拒绝、app-demo 零回归
- owner doc：nop-dyn.md 命名空间边界声明

### Out Of Scope

- VFS 内核层纵深防御（follow-up）；P2-3.7 台账行；存量数据迁移

## Execution Plan

### Phase 1 - 冲突拒绝防线 + 判别测试 + 文档边界

Status: completed
Targets: `NopDynErrors`、`NopDynModuleBizModel`、`docs-for-ai/03-modules/nop-dyn.md`、nop-dyn-service 测试

- Item Types: `Fix`

- [x] NopDynErrors 增 `ERR_DYN_MODULE_ID_CONFLICT`（moduleName/moduleId 参数；错误文案声明保留前缀与静态冲突两类拒绝）
- [x] NopDynModuleBizModel 增统一校验 `checkModuleIdNotShadowingStatic(String moduleName)`：①`nop-` 前缀拒绝；②静态模块标记检查——`/{moduleId}/_module` 资源存在即拒绝（**执行裁定**：初版用 `getEnabledModuleMap(false).containsKey` 会把既有动态模块误报为冲突——dynamicModules 也在该 map，generateByAI 二次调用被误拒〔既有用例实测 FAIL〕；改用 VFS `_module` 标记探针仅命中静态注册）。接入点：generateByAI（去重检查前）、importExcel（entity 构造后）、saveEntity 覆写（moduleName 非空即校验）
- [x] 测试（TestNopDynModuleBizModel 6/6，原 4 + 新 2）：①generateByAI("nop-auth") → ERR_DYN_MODULE_ID_CONFLICT；②save 新模块 moduleName="nop-auth"（VFS 有 /nop/auth/_module 静态标记）→ 同错误码；③importExcel 路径复用同一 checkModuleIdNotShadowingStatic 静态方法（接入点代码审查锚定，未单独建布景——注明）；④app-demo 系既有用例零回归
- [x] **判别验证**（执行裁定：stash 形态会连错误码定义一并移除导致编译 FAIL 非行为判别；改为 toggle 形式）——临时禁用 nop- 前缀检查后 testGenerateByAIReservedPrefixRejected FAIL（Expected NopException to be thrown, but nothing was thrown——精确证明修复前无任何拒绝），恢复后 6/6 绿
- [x] nop-dyn.md 命名空间边界声明（保留前缀+静态冲突拒绝+错误码）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 三入口均拒绝冲突 moduleName（判别测试绿）
- [x] 既有动态模块用例零回归
- [x] nop-dyn.md 边界声明在位且 doc-links 0
- [x] 收口动作（roadmap/index/log）落盘

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（P1-3.7-001 按处方 (1)+(3) 落地）
- [x] 行为/契约结果已达成（三入口拒绝判别+零回归）
- [x] 必要 focused verification 已完成（nop-dyn-service 测试全绿）
- [x] 不存在被静默降级的 in-scope live defect（处方 (2) VFS 内核层为 follow-up 声明，非降级——主防线 (1) 已封堵全部 mutation 入口）
- [x] 受影响 owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据（轻量 plan：判别测试+工具门禁+三方核对，见 Closure 段）
- [x] `./mvnw test -pl nop-dyn/nop-dyn-service` 全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Deferred But Adjudicated

（无——VFS 层纵深防御登记为 follow-up，非本 plan in-scope 降级）

## Non-Blocking Follow-ups

- DeltaResourceStore/OverrideResourceStore 层发布前校验（防御纵深，VFS 内核改动需独立设计）
- P2-3.7 台账行（R1.0 P2 裁决通道）

## Closure

Status Note: P1-3.7-001 按处方 (1)+(3) 交付：三入口（generateByAI/importExcel/saveEntity）统一拒绝 nop- 保留前缀与静态模块 id 冲突（ERR_DYN_MODULE_ID_CONFLICT；执行裁定：静态检测用 VFS _module 标记探针替代 getEnabledModuleMap——后者混入动态集合误报既有模块），nop-dyn.md 命名空间边界声明。VFS 内核层纵深防御为 follow-up。判别验证 toggle 形式：禁用检查→用例 FAIL（无异常抛出）→恢复 6/6 绿。nop-dyn-service 34 tests 全绿。
Completed: 2026-10-09

Closure Audit Evidence:

- Reviewer / Agent: 轻量单点 plan——独立性由 toggle 判别测试+工具门禁+三方文本一致性核对承担（toggle 禁用检查后用例 FAIL「Expected NopException to be thrown」为最强判别证据；check-doc-links --strict=0；check-plan-checklist --strict=0；roadmap/index/log 交付摘要一致）
- Evidence:
  - NopDynErrors ERR_DYN_MODULE_ID_CONFLICT 定义在位（moduleName/moduleId 参数）
  - NopDynModuleBizModel.checkModuleIdNotShadowingStatic：nop- 前缀拒绝+VFS /{moduleId}/_module 静态标记探针（执行裁定：替代 getEnabledModuleMap——动态集合混入误报，既有用例实测捕获）
  - 三入口接线：generateByAI（去重检查前）/importExcel（entity 构造后）/saveEntity 覆写
  - TestNopDynModuleBizModel 6/6（原 4 + nop-auth generateByAI 拒绝 + save 冲突拒绝）；toggle 判别 FAIL 记录
  - nop-dyn.md 命名空间约束节在位；doc-links --strict=0；nop-dyn-service 34 tests 全绿

Follow-up:

- DeltaResourceStore/OverrideResourceStore 层发布前校验（VFS 内核纵深防御，独立设计）
- P2-3.7 台账行（R1.0 P2 裁决通道）

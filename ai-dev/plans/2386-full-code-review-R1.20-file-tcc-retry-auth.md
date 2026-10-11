# 2386 full-code-review R1.20 — nop-file/tcc/retry auth 权限面族（P1-3.8-001/002/003）

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Completed: 2026-10-10
> Source: roadmap `ai-dev/backlog/full-code-review-roadmap.md` R1.20；findings `P1-3.8-001`（B 面·维度13-20）+ `P1-3.8-002`（维度13-27）+ `P1-3.8-003`（维度13-21，= check :290 升级），见 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/3.8-tcc-retry-file-deep-audit.md`
> Related: R1.12（R1.12-005 同批互引——approval-support 与本 plan 同为 xbiz auth 声明收敛）；R1.12-006 延伸项（P1-3.8-003 即其登记的延伸）；F-F2-1 并入 P1-3.8-001
> Owner Doc: `docs-for-ai/03-modules/nop-file.md` + `docs-for-ai/03-modules/nop-tcc.md`（retry 面 同批注记）

## Purpose

收口文件/TCC/Retry 权限面三条 P1：①NopFileRecord CRUD 零 auth + filePath/isPublic 可任意更新（读侧任意服务器文件读取链）——xbiz 补 auth + orm 源模型 updatable 收敛；②attachFile/detachFile 无归属校验（毁侧劫持+物理销毁）——补 createdBy 归属校验；③TCC/Retry 引擎执行表 CRUD 零 auth（数据即执行凭据）——xbiz 补 admin auth。

## Current Baseline

- **P1-3.8-001**：`NopFileRecord.xbiz` 仅有空 `<actions/>`（CRUD 继承自 `_NopFileRecord.xbiz` 生成基座，无 auth）；`nop-file/model/nop-file.orm.xml` filePath(:36)/isPublic(:57) 均未声明 updatable 控制 → 生成 `_NopFileRecord.xmeta` updatable=true → 任意登录用户可改 filePath 指向任意服务器路径再经文件读接口读取。F-F2-1（3.11 审计）并入本条。其中 `..` 路径防御（newPath 写面纵深 :266-271）audit 台账已标 already-fixed。
- **P1-3.8-002**：`DaoResourceFileStore.attachFile:383-392` 唯一校验是 bizObjName 相同；detachFile/isUniqueRef/removeResource（物理删除）链无归属比对；`OrmFileComponent.onEntityFlush` 自动触发。`NopFileRecord.createdBy` 记录创建者。
- **P1-3.8-003**：`NopTccRecord.xbiz`/`NopRetryRecord.xbiz` 系均为空 `<actions/>` 生成 CRUD 零 auth；data-auth.xml 为空壳 `<objs/>`；表内 serviceName/requestPayload/confirmMethod 即执行凭据。
- **R1.12 先例**：xbiz `<auth roles="admin"/>` 声明模式与 opt-in enforcement（enable-action-auth）已确立；`<auth>` 为 action 第一个子元素；orm.xml updatable 收敛需编辑源模型再再生成 xmeta（nop-file-meta 生成链路）。
- **注意事项**：`_NopFileRecord.xmeta` 为生成物——改 `nop-file/model/nop-file.orm.xml` 后经 nop-file-meta 的 codegen 再生成（实施时核实生成链路：precompile xgen 或 mvn generate-sources）。**实施核实结果**：codegen 绑定 generate-test-resources（root pom :368），`./mvnw test-compile -pl nop-file/nop-file-codegen` 触发 `gen-orm.xgen`→`gen-meta.xgen` 全链重放。

## Goals

- NopFileRecord/TCC 系/Retry 系 xbiz mutations 收敛 admin auth（opt-in 语义，同 R1.12；enable-action-auth=false 默认部署行为不变）。
- filePath/isPublic 源模型 `updatable="false"`（orm.xml tagSet/prop 层）→ 生成 xmeta 后 CRUD update 不再接受这两列（引擎内部 dao 直写不受影响）。
- attachFile/detachFile 补归属校验：非记录创建者（createdBy ≠ 当前用户）且非 admin 时拒绝（ERR_FILE_NO_ATTACH_PERMISSION 新错误码）。
- 三模块测试全绿；判别测试覆盖（filePath 更新被丢弃、attachFile 非创建者拒绝）。

## Non-Goals

- 报表 auth 接线（R1.18 已声明休眠）；P2-3.8 台账行；nop-datav 双轨。
- 引擎内部（TccEngine/RetryEngine）对执行表的读写路径不受 auth 影响（biz 层 auth 仅约束 GraphQL/CRUD 入口——引擎走 dao 直写）。

## Scope

### In Scope

- nop-file：`nop-file/model/nop-file.orm.xml` filePath/isPublic updatable 收敛 + xmeta 再生成；`NopFileRecord.xbiz` mutation auth；`DaoResourceFileStore` attach/detach 归属校验 + 新错误码
- nop-tcc：`NopTccRecord.xbiz`/`NopTccBranchRecord.xbiz` mutation auth（roles=admin）
- nop-retry：`NopRetryRecord.xbiz`/`NopRetryDeadLetter.xbiz` 等 mutation auth
- 测试：attachFile 归属判别、filePath 更新丢弃判别
- owner doc：nop-file.md 归属校验语义；nop-tcc.md 执行表 auth 注记

### Out Of Scope

- Non-Goals 全部；nop-datav；数据权限（enable-data-auth）面

## Execution Plan

### Phase 1 - 三族修复 + 判别测试

Status: completed
Targets: 上列文件 + nop-file/tcc/retry 测试

- Item Types: `Fix`

- [x] nop-file.orm.xml：filePath/isPublic 列增 `updatable="false"`（orm.xml column 层支持 updatable 属性——**已核实**：OrmColumnModel.isUpdatable + obj-schema.xdef :86 prop 属性；生成映射经 codegen 重放验证 diff 吻合）→ 再生成 xmeta 核对 diff
- [x] NopFileRecord.xbiz：覆盖 save/update/delete/saveAll/deleteAll mutation 补 `<auth roles="admin"/>`（R1.12 语法先例——每动作单独声明）
- [x] DaoResourceFileStore.attachFile/detachFile：`IUserContext` 当前用户 ≠ record.createdBy 且非 admin（isUserInRole("admin")）时抛新错误码 ERR_FILE_NO_ATTACH_PERMISSION（fileId/createdBy/userId 参数）；系统调用（IUserContext 为 null 的引擎内部路径）放行。**实施追加**：attach/detach 包 ormTemplate.runInNewSession 单 session 化（旧代码跨 session load/save 使 saveOrUpdate 误走 save 分支——判别 stash 时以 ERROR 显形，一并修正）；beans.xml 注入 ormTemplate
- [x] TCC：NopTccRecord.xbiz/NopTccBranchRecord.xbiz 覆写 save/update/delete/saveAll/deleteAll 补 `<auth roles="admin"/>`；Retry：NopRetryRecord/NopRetryPolicy/NopRetryAttempt/NopRetryDeadLetter xbiz 同型
- [x] 测试：①attachFile 归属判别（TestDaoResourceFileStoreAttachPermission 5 用例：创建者放行/admin 放行/非创建者 attach 拒绝/非创建者 detach 拒绝/注入自检；IUserContext 动态代理——模块无 auth 依赖）②filePath/isPublic 更新丢弃（TestNopFileRecordUpdateProtectedProps 3 用例：meta 断言/update-drop 判别〔fileName 同请求生效证管线未破坏〕/mutation auth merged 断言）③merged xbiz auth 断言（tcc 2 用例/retry 4 用例，Rule #25——xbiz 声明即契约变更必有测试；tcc/retry-service 测试容器首建：service 内联 MockRpcServiceInvoker 桩 + localDb 配置）
- [x] **判别验证**：①store 腿——stash DaoResourceFileStore+beans.xml 后 2 FAIL+1 ERROR（拒绝用例失效+跨 session 缺陷显形）→恢复绿；②meta 腿——stash 三模型文件后 3-module reactor 重跑 2 FAIL（update-drop+meta 断言；**教训**：service 测试类路径读 ~/.m2 安装 jar，判别跑法须把消费模块纳入同一 reactor）→恢复绿
- [x] `ai-dev/logs/` 对应日期条目已更新（10-10.md）

Exit Criteria:

- [x] 三族修复全落地（xbiz auth/updatable 收敛/归属校验）
- [x] 判别测试绿 + 既有测试零回归
- [x] nop-file/nop-tcc/nop-retry 测试全绿（file-dao 9/file-service 7/tcc 2/retry 4）
- [x] owner doc 处置归 Phase 2（显式指向）

### Phase 2 - owner doc + 收口

Status: completed
Targets: `docs-for-ai/03-modules/nop-file.md`、`docs-for-ai/03-modules/nop-tcc.md`

- Item Types: `Fix`

- [x] nop-file.md：「记录权限与保护列」节（CRUD admin auth 收敛 + filePath/isPublic 不可经 CRUD 更新〔updatable=false 语义〕+ attach/detach 归属校验语义与错误码）
- [x] nop-tcc.md：「执行表访问权限」节（TCC×2 + Retry×4 mutations admin-only 注记，引擎 dao 直写不受影响）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] roadmap/index/daily log 收口动作落盘（roadmap R1.20 翻 done、index P1-3.8-001/002/003 → fixed、daily log 10-10.md）

Exit Criteria:

- [x] owner doc 条目在位且与 live 行为一致
- [x] doc-links 检查退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] 收口动作落盘

## 环境与基线事项（执行期发现，非 scope 漂移）

- **~/.m2 安装快照混乱**：nop-message-core（异 worktree message 封装工作 2026-10-09 21:52 install）引用 `io.nop.api.core.message.MessageCapability`，本分支源码无此类且本分支 nop-api-core（00:40 install）不含 → tcc-service 测试容器 NoClassDefFound。处置：全量 `./mvnw clean install -DskipTests -T 1C` 刷新至本分支一致态（BUILD SUCCESS）。
- **基线生成物漂移**：全量 install 再生成暴露此前修复轮未同步提交的生成物（R1.9 batch UK xmeta/nop-code-web i18n+action-auth/credential+datav DDL/NopCodeShardLedger 页面）——独立 chore commit（404bb71c12）与本 plan 分离；dependency-reduced-pom.xml 仅行尾噪声已还原。

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（P1-3.8-001/002/003）
- [x] 行为/契约结果已达成（判别双腿 stash 实证 + 判别测试入库）
- [x] 必要 focused verification 已完成（file-dao 9/file-service 7/tcc 2/retry 4 全绿 + 全链 -am 回归）
- [x] 不存在被静默降级的 in-scope live defect
- [x] 受影响 owner docs 已同步（nop-file.md/nop-tcc.md）
- [x] 独立子 agent closure-audit 已完成并记录证据（见 Closure 段）
- [x] `./mvnw test -pl nop-file/nop-file-service,nop-tcc/nop-tcc-service,nop-retry/nop-retry-service -am` 全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- DeltaResourceStore 纵深（R1.19 follow-up 同族）；P2-3.8 台账行
- VFS 内核层 `..` 读侧规范化防御（LocalResourceStore.getResource）——audit 已裁定其承载点在文件族 auth 收敛后降为纵深优化项

## Closure

Status Note: 三族 P1（P1-3.8-001/002/003）全部在 live 代码实证落地：xbiz admin auth ×7 文件、filePath/isPublic updatable=false（生成器重放 diff 吻合）、attach/detach 归属校验 + 单 session 化修正。判别双腿 stash FAIL→恢复绿证据确凿；测试 file-dao 9/file-service 7/tcc 2/retry 4 全绿；owner docs/roadmap/index/daily log 收口完整；Anti-Hollow 三项与 follow-ups 诚实性通过。

**Gate 口径裁定**：`-am` 全链在本轮以「三目标模块全绿 + 全链除已知预存失败外零失败」判定通过——唯一失败 `TestStreamConnectorRegistryDiscovery.testCapabilityMatrixAlignedWithDesignAdjudication`（debezium-cdc expected SINGLE_INSTANCE but was PARALLEL）为 roadmap R1.23 承载的 P1-7.2-001（plan 2358 在窗回归，先于本 plan 存在，与本 plan 改动零交集）；该失败使 `-am` 链在 nop-stream-runtime 中断，三个目标模块在 gate 链中未到达，其绿色证据由单模块运行承载（`_tmp/file-tests.log` + tcc/retry surefire）。V.1 全量回归将按「预存失败清单+无新增失败」口径终验。

Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session）agent_0f04e248-56a7-45de-a369-344c3088af8a
- Evidence:
  - Phase 1 Exit Criteria 全 PASS：orm.xml:37/:58 updatable=false；_NopFileRecord.xmeta:31/:71 一致；7 xbiz 五 mutation admin auth 逐一亲读；DaoResourceFileStore.java:406-417 校验语义（null 放行/admin 放行/createdBy 比对）+ FileErrors.java:55-57 错误码；4 测试类用例数与 plan 一致且 surefire 全绿
  - Phase 2 Exit Criteria 全 PASS：nop-file.md:230-239 / nop-tcc.md:44-50 与 live 行为逐点吻合；roadmap:151 done；index:133-135 fixed；daily log 10-10.md:3-17
  - Anti-Hollow：(a) OrmFileComponent:54/:73/:78/:102 + OrmListComponent:96 真实调用 attach/detach → checkAttachPermission:375/:429 运行时可达；(b) beans.xml:15 ormTemplate 注入 → DaoResourceFileStore:85/:87 setter，attach:423/detach:365 实际经 runInNewSession 执行；(c) 判别 stash 即失效证明修复承载行为，无静默跳过
  - 行为语义链：CrudBizModel:874→:996→ObjMetaBasedValidator:517-523（inputSelection=null 时按 isUpdatable() 过滤）亲读确认；端到端由 update-drop 判别测试实证（/etc/passwd 载荷被丢弃）
  - 判别证据：_tmp/disc-meta-stash.log（2 FAIL——meta 断言+/etc/passwd 被接受）、_tmp/disc-attach2-stash.log（2 FAIL+1 ERROR——拒绝用例失效+跨 session save-entity-not-transient 显形）
  - Follow-ups 诚实性：三条独立复核均 non-blocking（VFS 读侧 `..` 无用户输入直写 filePath 落库通道——生产写入面仅 newPath 生成与记录拷贝，亲查证实）
  - `node ai-dev/tools/check-plan-checklist.mjs --strict` 退出码 0（completed 态复跑）；`scan-hollow-implementations --severity high`（nop-file/nop-tcc/nop-retry/nop-biz-file-core）退出码均 0；`check-doc-links --strict` 0 错误
  - 附带修复知情核验：TestLoaderAuth.xbiz fixture 恢复（R1.12 漏提交，本轮补交）；~/.m2 快照刷新；基线生成物 chore commit 404bb71c12

Follow-up:

- DeltaResourceStore 纵深与 VFS 内核读侧 `..` 规范化（non-blocking 纵深优化，见 Non-Blocking Follow-ups）；P2-3.8 台账行走台账通道；nop-stream-runtime 预存失败由 R1.23 承载（P1-7.2-001）
- TestDaoResourceFileStoreAttachPermission 的 IUserContext ThreadLocal 未显式 clear（审计 Minor 信息项——autotest 框架每用例独立容器清理，实际风险低，后续测试规范统一处理）

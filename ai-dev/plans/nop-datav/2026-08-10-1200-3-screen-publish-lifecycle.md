# 3 大屏发布生命周期增强：历史 + 缩略图 + 草稿预览（D4-4）

> Plan Status: active
> Mission: nop-datav
> Work Item: D4-4
> Last Reviewed: 2026-08-10
> Source: `ai-dev/backlog/nop-datav-roadmap.md` D4-4；`ai-dev/analysis/2026-08/2026-08-09-nop-datav-function-analysis.md` §五大屏能力
> Related: `2026-08-10-1130-2-screen-free-canvas-layout.md`（D4-1 publish/getPublished/rollback 基础）、`2026-08-09-2255-1-dashboard-model-crud-and-tests.md`（D0 主表+快照表模式）
> Execution Order: N=3（D4-2 → D4-3 → D4-4）。本计划与 D4-3 共同修改 `ScreenLayoutParser.java`/`NopDatavScreenBizModel.java`；按 N 序在 D4-3 之后执行，本计划的 `getScreenDraftLayout` 在 D4-3 主题解析接入后的 ScreenLayoutParser 上叠加 content-overload。

## Purpose

将 roadmap D4-4 收口：在大屏 D4-1 已有的 publish/getPublished/rollback 之上，增强发布生命周期三项能力——快照历史浏览、缩略图存储、草稿预览。D4-1 已建立"主表管权限/发布状态、快照表管已发布内容"模式（每次 publish 写一行 NopDatavScreenSnapshot，版本递增）。本计划补齐：(1) 浏览某大屏的全部发布历史并查看指定历史版本的布局；(2) 大屏缩略图存储位 + 设置 API（生成走前端截图，out of scope）；(3) 草稿预览——从当前编辑态（未发布）直接构建 ScreenLayoutConfig，无需先 publish。本计划只做模型层 + API，不做前端。

## Current Baseline

基于 live repo 核对（2026-08-10）：

- **publish/getPublished/rollback 已就绪（D4-1）**：`NopDatavScreenBizModel`（`nop-datav-service/.../entity/NopDatavScreenBizModel.java`）含 `publishScreen`（写快照 + 更新主表发布状态/版本）、`getPublishedScreen`（读最新快照原文）、`rollbackScreen`（从指定版本快照恢复主表）、`getScreenLayout`（读**最新**快照 → 经 `ScreenLayoutParser` 解析为 ScreenLayoutConfig）。版本号 = 当前最大快照版本 + 1（从快照表查询）。
- **无历史浏览 API**：当前仅有 `findLatestSnapshot`（私有，读最新）和 `findSnapshotByVersion`（私有，读指定版本，仅 rollback 用）。**无公开 action 列出某大屏的全部快照历史**，也无"查看指定历史版本的布局"API。用户无法浏览发布历史或对比版本。
- **无缩略图**：`NopDatavScreen` 与 `NopDatavScreenSnapshot` 均无缩略图字段。大屏列表页无法展示视觉预览。
- **无草稿预览**：`getScreenLayout` 仅读**已发布快照**（无快照抛 `ERR_DATAV_SCREEN_SNAPSHOT_NOT_FOUND`）。编辑态（主表 + widget 行的当前值）无法在不 publish 的情况下预览布局。`serializeScreenContent`（私有，publish 时序列化编辑态为 JSON）已具备从编辑态构建内容的能力，但仅 publish 调用。
- **权限基建可复用**：D4-1 已配大屏 action `@Auth` + owner 行级 RLS（`nop-datav-web/.../nop/datav/auth/nop-datav.action-auth.xml` + `nop-datav-service/.../nop/datav/auth/nop-datav.data-auth.xml`）。本计划新增 action 沿用同模式。
- **错误码**：`NopDatavErrors.java` 含大屏相关错误码（screen-not-found/snapshot-not-found/snapshot-version-not-found/invalid-screen-layout 等）。
- **设计契约为 D4-1 范围**：`screen-design.md` Scope 明确"发布生命周期增强 D4-4 不在本文结论范围"。本计划 Phase 1 增补 D4-4 章节。
- **真正剩余 gap**：无历史浏览 API、无指定版本布局查看、无缩略图存储、无草稿预览。

## 设计方向预声明（推荐方向，Phase 1 确认并记录拒绝理由）

1. **缩略图存于 NopDatavScreen 主表（新增 thumbnail 列），不存于快照表**：列表页只需"当前缩略图"（最近一次发布或手动设置）。拒绝"每快照一行缩略图"（理由：列表场景只需当前态；历史缩略图无独立用例，按需可在 snapshotContent JSON 内附带）。→ 主表新增 `thumbnail` 列（string，存文件记录引用或数据 URL），publish 时可选回填。属 ORM 结构变更（plan-first，本 plan 即其 plan）。
2. **草稿预览复用 serializeScreenContent + ScreenLayoutParser，不新建独立解析路径**：`serializeScreenContent` 已能从编辑态构建内容 JSON，`ScreenLayoutParser.parse` 已能解析该 JSON。草稿预览 = 序列化当前编辑态 → 解析为 ScreenLayoutConfig（不经快照表落盘）。拒绝"为草稿单独写一套布局构建"（理由：复用避免双路径漂移）。Phase 1 确认该复用契约。
3. **历史浏览返回元信息列表（不含完整 snapshotContent）**：`getScreenSnapshotHistory` 返回每版本的 {snapshotVersion, publishedBy, publishedTime}（列表页只需元信息）。查看具体版本布局用 `getScreenLayoutByVersion`（读该版本快照 → 解析）。拒绝"列表直接返回全量内容"（理由：快照 CLOB 大，列表全量返回浪费）。
4. **草稿预览权限 = owner/admin（区别于已发布的 admin/user 可读）**：草稿是编辑态，仅 owner/admin 可预览（与编辑权限一致）；已发布内容对所有有读权限的用户可见（D4-1 既有语义不变）。Phase 1 确认权限矩阵。

## Goals

- 快照历史浏览：`getScreenSnapshotHistory(screenId)` 返回版本元信息列表（版本号/发布人/发布时间）。
- 指定版本布局查看：`getScreenLayoutByVersion(screenId, snapshotVersion)` 读指定版本快照 → 解析为 ScreenLayoutConfig。
- 缩略图存储：`NopDatavScreen` 新增 `thumbnail` 列 + `setScreenThumbnail(screenId, thumbnail)` API；publish 时可选回填当前缩略图到主表。
- 草稿预览：`getScreenDraftLayout(screenId)` 从当前编辑态构建 ScreenLayoutConfig（无需已发布快照）。
- 在 `screen-design.md` 增补 D4-4 章节（最终结论 + 被拒方案）。
- 新增 action 经 `@Auth` + owner 权限收口（沿用 D4-1 模式）。

## Non-Goals

- **缩略图图像生成**：截图/渲染生成走前端（flux 未产出）；本计划只提供存储位 + 设置/回填 API。
- **版本 diff（对比两个快照内容差异）**：scope 过宽，列为 follow-up。
- **发布审批流**：归工作流（nop-wf）域，不在本计划。
- **大屏装饰组件（D4-2）**、**主题（D4-3）**：独立 plan。
- **前端历史/预览 UI**：走 flux。

## Scope

### In Scope

- 设计文档增补：`ai-dev/design/nop-datav/screen-design.md` 增 D4-4 章节（历史浏览契约、缩略图存储裁定、草稿预览复用契约、权限矩阵、被拒方案）。
- ORM 变更：`NopDatavScreen` 新增 `thumbnail` 列（string，文件记录引用/data URL）。
- API 新增：`getScreenSnapshotHistory`、`getScreenLayoutByVersion`、`getScreenDraftLayout`、`setScreenThumbnail`；publish 可选回填缩略图。
- 权限：新增 action 权限点 + 角色绑定（action-auth.xml）；草稿预览 owner/admin 语义确认。
- 错误码：复用既有（snapshot-version-not-found 等），按需新增。
- 单元测试 + 端到端（多次 publish → 历史 → 指定版本布局 → 草稿预览 → 缩略图设置/回填）。

### Out Of Scope

- 缩略图图像生成（前端截图）。
- 版本 diff。
- 发布审批流。
- D4-2 装饰组件、D4-3 主题。
- 前端 UI。

## Execution Plan

### Phase 1 - 设计文档增补（screen-design.md D4-4 章节）

Status: planned
Targets: `ai-dev/design/nop-datav/screen-design.md`

- Item Types: `Decision`

- [ ] 在 `screen-design.md` 增 D4-4 章节（最终结论，无 "Proposed vs Current"），记录：
  - [ ] **快照历史浏览契约**：`getScreenSnapshotHistory` 返回版本元信息列表（snapshotVersion/publishedBy/publishedTime，不含 snapshotContent）；记录"列表返回全量内容"被拒理由（CLOB 大、列表场景浪费）
  - [ ] **指定版本布局查看契约**：`getScreenLayoutByVersion` 读指定版本快照 → 解析为 ScreenLayoutConfig（复用 ScreenLayoutParser）
  - [ ] **缩略图存储裁定**：主表新增 thumbnail 列（string）；记录"每快照一行缩略图"被拒理由（列表只需当前态）
  - [ ] **草稿预览复用契约**：getScreenDraftLayout = serializeScreenContent(当前编辑态) → ScreenLayoutParser.parse（不经快照表落盘）；记录"单独写草稿布局构建"被拒理由（避免双路径漂移）
  - [ ] **权限矩阵**：历史浏览/指定版本布局 = admin/user 读（已发布内容）；草稿预览 = owner/admin（编辑态语义）；缩略图设置 = owner/admin
  - [ ] **缩略图写入裁定（确定性）**：thumbnail 主表列**仅**由 `setScreenThumbnail` 写入（前端截图后调用）；`publishScreen` **不**改动 thumbnail 列（避免 publish 副作用）。`serializeScreenContent` 在序列化快照 JSON 时**只读**附带当前 thumbnail 值（供历史版本附带预览，不回写主表）。记录"publish 自动生成/回填缩略图"被拒理由（生成需渲染，属前端；publish 保持单一职责）

Exit Criteria:

- [ ] `screen-design.md` 含 D4-4 最终结论章节，覆盖上述全部子项，无 "Proposed"/"待定"
- [ ] 该 Phase 改变 live baseline（design doc）：`docs-for-ai/` 无需更新；`ai-dev/logs/` 对应日期条目已更新

### Phase 2 - ORM 变更与代码生成（thumbnail 列）

Status: planned
Targets: `nop-datav/model/nop-datav.orm.xml`、`nop-datav/nop-datav-dao/src/main/java/io/nop/datav/dao/entity/_gen/`、`nop-datav/nop-datav-meta/src/main/resources/_vfs/nop/datav/model/NopDatavScreen/`

- Item Types: `Decision | Proof`

- [ ] 在 `nop-datav/model/nop-datav.orm.xml` 的 `NopDatavScreen` 实体新增 `thumbnail` 列（string，precision 视引用形式定，存文件记录 ID 或 data URL；displayName/审计列惯例与既有列一致）
- [ ] 触发 codegen 重建生成物：`./mvnw install -pl nop-datav/nop-datav-meta -am -DskipTests`（与 D4-1 一致，构建含 `nop-datav-codegen/postcompile/gen-orm.xgen` 的 reactor，重建 dao 实体 + meta xmeta + crud api）。若生成未触发，改用 `./mvnw install -pl nop-datav/nop-datav-codegen,nop-datav/nop-datav-meta -am -DskipTests`。确认生成物含 thumbnail：
  - 生成实体：`nop-datav/nop-datav-dao/src/main/java/io/nop/datav/dao/entity/_gen/_NopDatavScreen.java` 含 thumbnail
  - 生成 xmeta：`nop-datav/nop-datav-meta/src/main/resources/_vfs/nop/datav/model/NopDatavScreen/_NopDatavScreen.xmeta` 含 thumbnail
  - **不手改任何 `_` 前缀生成文件**（AGENTS.md Hard Stop）；retention 文件 `NopDatavScreen.xmeta`（非 `_` 前缀）仅在需自定义展示时编辑
- [ ] 标准管理页/CRUD 自动暴露 thumbnail（生成 xmeta 自动拾取该列，无需手工）

Exit Criteria:

- [ ] `nop-datav/model/nop-datav.orm.xml` 的 NopDatavScreen 含 thumbnail 列（源模型）
- [ ] `./mvnw install -pl nop-datav/nop-datav-meta -am -DskipTests` 成功；生成实体 `_NopDatavScreen.java`（`nop-datav/nop-datav-dao`）+ 生成 xmeta `_NopDatavScreen.xmeta`（`nop-datav/nop-datav-meta`）均含 thumbnail
- [ ] **无静默跳过**：本 Phase 仅 codegen，不涉及运行时分支；未手改任何 `_` 前缀生成文件
- [ ] 该 Phase 改变 live baseline（ORM 结构）：属 plan-first 区域，本 plan 即其 plan；`ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 发布生命周期 API 实现

Status: planned
Targets: `nop-datav-dao/.../biz/INopDatavScreenBiz.java`（接口扩展）、`nop-datav-service/.../service/entity/NopDatavScreenBizModel.java`、`nop-datav-service/.../service/screen/ScreenLayoutParser.java`（新增 content overload）、`nop-datav-web/.../nop/datav/auth/nop-datav.action-auth.xml`

- Item Types: `Fix | Decision`

- [ ] **接口扩展**：在 `INopDatavScreenBiz`（`nop-datav-dao/.../biz/INopDatavScreenBiz.java`，公共契约面）声明 4 个新 action 签名（`getScreenSnapshotHistory`/`getScreenLayoutByVersion`/`getScreenDraftLayout`/`setScreenThumbnail`），与既有 4 action 同模式（`@BizQuery`/`@BizMutation` + `@Name`）
- [ ] **草稿预览组合机制（关键）**：`ScreenLayoutParser` 当前 `parse(String screenId, NopDatavScreenSnapshot snapshot)` 取 `snapshot.getSnapshotContent()` 再解析。新增 overload `parse(String screenId, String snapshotContent)`（解析 JSON 内容），既有 `parse(String, NopDatavScreenSnapshot)` 改为委托新 overload（提取 snapshotContent + snapshotVersion）。`getScreenDraftLayout` = `serializeScreenContent(screen)` 产出内容字符串 → 调用新 overload 解析（不经快照表落盘）。**不**为草稿单独写第二套布局构建
- [ ] 实现 `getScreenSnapshotHistory(screenId)`（`@BizQuery`，`@Auth`）：返回某大屏全部快照的版本元信息列表（snapshotVersion/publishedBy/publishedTime，按版本倒序），不含 snapshotContent
- [ ] 实现 `getScreenLayoutByVersion(screenId, snapshotVersion)`（`@BizQuery`，`@Auth`）：读指定版本快照（不存在抛 `ERR_DATAV_SCREEN_SNAPSHOT_VERSION_NOT_FOUND`）→ 经 ScreenLayoutParser 解析
- [ ] 实现 `getScreenDraftLayout(screenId)`（`@BizQuery`，`@Auth`，owner/admin）：按上述组合机制从当前编辑态构建 ScreenLayoutConfig（从未 publish 的大屏也能预览）
- [ ] 实现 `setScreenThumbnail(screenId, thumbnail)`（`@BizMutation`，`@Auth`，owner/admin）：更新主表 thumbnail 列（**唯一**写入点；publish 不触碰 thumbnail）；处理乐观锁 version（部分列更新）
- [ ] `serializeScreenContent` 只读附带当前 thumbnail 值到快照 JSON（Phase 1 裁定），不回写主表
- [ ] 权限：在 `nop-datav-web/.../nop/datav/auth/nop-datav.action-auth.xml` 增配 4 个新 action 权限点 + 默认角色绑定（历史/版本布局 → admin,user；草稿预览/缩略图设置 → admin）
- [ ] action 经 `requireEntity` → `checkDataAuth`（沿用 D4-1 模式）

Exit Criteria:

- [ ] 4 个新 API（历史/版本布局/草稿预览/缩略图设置）可用，行为符合 Phase 1 契约
- [ ] 4 个新 action 已在 `INopDatavScreenBiz` 接口声明（公共契约面同步，非仅 BizModel 实现）
- [ ] 草稿预览经 `ScreenLayoutParser.parse(screenId, content)` overload 实现（serializeScreenContent → overload），不需已发布快照即可返回布局（从未 publish 的大屏也能预览）
- [ ] 缩略图仅由 `setScreenThumbnail` 写入；publish 不触碰 thumbnail 列
- [ ] 指定版本不存在抛 `ERR_DATAV_SCREEN_SNAPSHOT_VERSION_NOT_FOUND`（不返回 null/空）
- [ ] **无静默跳过**：新增 API 无空方法体/continue/吞异常；版本不存在显式报错
- [ ] 该 Phase 改变 live baseline（API/行为/契约）：`screen-design.md`（Phase 1）已覆盖；`docs-for-ai/` 无需更新；`ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 测试与端到端验证

Status: planned
Targets: `nop-datav-service/src/test/`

- Item Types: `Proof`

- [ ] 单元测试 历史浏览：多次 publish 后 `getScreenSnapshotHistory` 返回全部版本元信息（版本号递增、发布人/时间正确、不含 snapshotContent）
- [ ] 单元测试 指定版本布局：`getScreenLayoutByVersion` 读历史版本解析正确；不存在版本 → `ERR_DATAV_SCREEN_SNAPSHOT_VERSION_NOT_FOUND`
- [ ] 单元测试 草稿预览：从未 publish 的大屏 `getScreenDraftLayout` 返回布局（含 widget 定位/组件类型）；编辑后再次预览反映变更；验证经 `parse(screenId, content)` overload（非独立第二套构建）
- [ ] 单元测试 缩略图：`setScreenThumbnail` 更新主表 thumbnail；publish 不改动 thumbnail（单一写入点验证）；serializeScreenContent 只读附带 thumbnail 到快照 JSON
- [ ] 端到端测试（rule #22）：创建大屏 → 配置 widget → getScreenDraftLayout 预览（未 publish）→ publish v1 → 改 widget → publish v2 → getScreenSnapshotHistory 列出 [v1,v2] → getScreenLayoutByVersion(v1) 返回 v1 布局 → setScreenThumbnail → 断言全链路
- [ ] 权限测试：草稿预览 owner/admin 可访问、非 owner user 不可访问（区别于已发布内容的 admin/user 可读）

Exit Criteria:

- [ ] 新增 4 项功能（历史/版本布局/草稿预览/缩略图）每个均有对应测试（rule #25）
- [ ] **端到端验证**：草稿预览 → 多次 publish → 历史 → 指定版本 → 缩略图完整链路跑通
- [ ] **接线验证**：草稿预览端到端测试断言 serializeScreenContent → `ScreenLayoutParser.parse(screenId, content)` overload 复用路径连通（编辑态布局可观察，非独立第二套构建）
- [ ] `./mvnw test -pl nop-datav/nop-datav-service -am` 通过（含新增测试，无回归）
- [ ] 该 Phase 改变 live baseline（测试）；`ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 快照历史浏览 + 指定版本布局查看可用
- [ ] 缩略图存储（thumbnail 列）+ 设置 API 落地（setScreenThumbnail 唯一写入点；publish 不触碰 thumbnail）
- [ ] 草稿预览（从未 publish 的大屏可预览）落地，经 parse overload 复用（非第二套构建）
- [ ] 4 个新 action 在 INopDatavScreenBiz 接口声明 + 权限收口（草稿预览 owner/admin 语义区别于已发布）
- [ ] 指定版本不存在 / 缺失场景显式失败（非静默跳过）
- [ ] 不存在被静默降级到 deferred 的 in-scope live defect
- [ ] `screen-design.md` D4-4 章节为最终设计与 live 实现一致
- [ ] 受影响 owner docs 已同步（`screen-design.md`；`docs-for-ai/` 无需更新）
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 验证草稿预览经 serializeScreenContent→`parse(screenId,content)` overload 复用路径连通、4 个新 API 非空壳
- [ ] `./mvnw compile -pl nop-datav -am`
- [ ] `./mvnw test -pl nop-datav/nop-datav-service -am`
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### 缩略图图像生成（flux 侧）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 截图/渲染生成走前端（flux 未产出）。本计划提供存储位 + 设置/回填 API，前端截图后调 setScreenThumbnail 即可。与 D1-4/D2-4 同类阻塞。
- Successor Required: `yes`
- Successor Path: flux 大屏渲染控件落地后对接（前端截图 → setScreenThumbnail）

### 版本 diff（快照内容对比）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 当前无版本对比用例；diff 需定义对比粒度（widget 级/字段级），scope 过宽。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 历史版本保留策略（自动清理旧版本/保留最近 N 个）——优化项
- 缩略图自动过期/刷新策略——优化项
- 发布审批流（接入 nop-wf）——独立评估

## Closure

Status Note: <<完成或关闭时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Audit Session: <<session ID>>
- Evidence:
  - 每条 Exit Criterion 的验证结果（PASS/FAIL + 对应 live code path 或 test name）
  - 每条 Closure Gate 的验证结果
  - `node ai-dev/tools/check-plan-checklist.mjs` 退出码为 0
  - Anti-Hollow 检查结果：<<端到端调用链追踪 + scan-hollow-implementations.mjs 退出码为 0>>
  - Deferred 项分类检查：<<确认无 in-scope live defect 被降级>>

Follow-up:

- <<no remaining plan-owned work 或 successor 项>>

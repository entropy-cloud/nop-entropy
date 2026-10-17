# 2026-10-17 flux 手工测试发现清单

> 状态：记录（open）。根因未定位的条目在 `ai-dev/testing/README.md` 的规则下先录于此，定位/修复后升级为 `ai-dev/bugs/` 笔记并回填 commit。

## 测试环境

- 应用：`nop-auth-app`（端口 8080）/ `nop-quarkus-demo`（端口 8083），均 `nop.web.render-mode=flux`，dev profile（H2）。
- 前端：nop-chaos-next 构建产物（本仓 `nop-web-site` 已同步）。
- 测试方式：手工点击 + 浏览器观察。

## 发现清单

### 1. 下载 Excel 模板 / 报表时文件名乱码（应为中文）`open`

- **现象**：点击下载 Excel 模板或报表，下载得到的文件名是转义后的乱码（如 `%xx%xx` 形态或 percent-encoding 未解码），而不是中文文件名。
- **预期**：文件名直接显示中文（模板/报表定义的中文名），不出现 `%` 转义序列。
- **疑似范围**：`Content-Disposition` 文件名编码/解码链路（后端报表下载 response header 的 `filename*`/`filename` 与前端 flux `download` 动作 `resolveDownloadFilename`）；跨 nop-entropy（header 生成）与 nop-chaos-next（解析保存）两层。
- **复现**：报表页/模板下载入口 → 保存文件 → 查看文件名。
- **状态**：open（待定位是后端生成还是前端解析）。

### 2. 操作日志列表的"操作"列出现多个

- **现象**：操作日志（审计日志）列表界面中，"操作"列出现多个（重复渲染/多列或一格内重复内容）。
- **预期**：单一"操作"列，显示正常。
- **疑似范围**：操作日志页面的 grid 列定义（生成侧 `GenGridCols` 或页面 view.xml）——可能是列被重复生成。
- **状态**：open（需确认是"多列"还是"一格内多值"）。

### 3. 关联字段显示为 id，应显示 dispProp 指定的属性（如用户名）

- **现象**：新增/编辑/查看等页面中，弹出选择（picker）或关联显示的用户等字段，显示的是 id，而不是 `dispProp` 指定的显示属性（如 userName）。
- **预期**：显示 `dispProp`（如 `userName`）；实际保存的值仍是 id（存储 id 正确，仅显示错误）。
- **疑似范围**：flux picker/关联字段的 `labelField`/`valueField` 映射与已选标签渲染；生成侧 `edit-relation`（`labelField = ext:joinRightDisplayProp || ext:joinRightProp`）与消费侧 picker 标签显示。
- **状态**：open（需区分"已选标签显示"与"列表/查看静态显示"两处）。

### 4. recoverDeleted 点击后报错"参数名不对"

- **现象**：点击"恢复已删除记录"（recoverDeleted）后报错，提示参数名不正确（后端"未定义参数/未知参数"类错误）。
- **预期**：正常恢复。
- **疑似范围**：`deleted` 相关页面/operation 的参数整形（前端 `@query:`/`@mutation:` 提交体与后端方法签名不匹配）；此前同类问题（`pagination` 参数、CRUD 信封剥离）见 10-16 日志。
- **状态**：open（需抓取具体错误码与请求体）。

### 5. 部门（标签页）界面交互异常，需 e2e 完整验证

- **现象**：部门页面（标签页形态）中，新建部门、选择父部门、切换标签页等操作结果都不正常。
- **预期**：新建/编辑部门、父部门选择（tree-select/picker）、标签页切换均正常，且显示正确。
- **要求**：需要用 e2e 完整覆盖这些交互，并**截图确认界面显示正常**（不仅断言数据，还要视觉确认）。
- **疑似范围**：`NopAuthDept` 页面（`.view.xml` / 生成的 flux 页面）、tree-select、表单 tab（`layoutControl="tabs"`）、父部门 picker。
- **状态**：open（范围较大，建议单独立项）。

### 6. 表单 label 布局：新建/修改标签在上方，查询在左侧

- **现象/需求**：新建、修改等表达式的表单，label 应放到**字段上方**；查询表单 label 放到字段**左侧**。
- **理由**：业务系统 label 经常很长，左对齐困难；编辑表单上置更利于长 label 对齐。
- **预期**：编辑/新增表单（form）默认 `mode=vertical` 风格（label 上置）；查询表单保持水平（label 左侧）。
- **疑似范围**：form 默认 `mode`/`layout` 生成（`flux-web.xlib` GenForm 默认、view.xml 表单、`flux-control` 控件），以及 CRUD `queryForm.mode` 默认（当前生成器给 queryForm 显式 `mode="horizontal"`）。
- **状态**：open（需确认当前编辑表单实际 mode 与查询表单 mode 的现状，再统一约定）。

## 备注

- 第 1、3 条涉及下载/关联字段的跨仓契约（nop-entropy 生成 + nop-chaos-next 宿主），修复可能跨仓。
- 第 5 条明确要求 e2e + 截图验收，落地时在 `nop-entropy-e2e` 增/扩 spec 并保存基线截图。
- 以上均来自手工测试，**尚未定位根因**；逐条定位后按 `bugs/` 指南产出修复笔记。

## 收口（2026-10-17，plan 2460）

6 条全部定位并修复，验证见 `ai-dev/plans/2460-flux-manual-test-findings-fix.md` 的 Closure：

1. **下载文件名乱码** — `fixed`：后端 `GraphQLResponseHelper` 增补 RFC 5987 `filename*=UTF-8''`（`nop-graphql-core`）；回归 `TestGraphQLResponseHelper`；前端 flux `extractFilename` 已支持 `filename*`。
2. **操作日志"操作"列多个** — `fixed`：行操作列改保留名 `__operation`（`flux-web/grid_crud.xpl`），避免与 `NopAuthOpLog.operation` 数据列同名被 flux 表格合并；回归 `TestNopAuthOpLogColumns`；浏览器实测表头为「业务操作 + 操作」。
3. **关联字段显示 id** — `fixed`：`flux-control.xlib:edit-relation` 输出 `labelResolveAction`（按关联实体 PK findList 解析 dispProp）；e2e「弹窗选择职务」增编辑态显示断言（显示岗位名）。
4. **recoverDeleted 参数名错** — `fixed`：`NopAuthUser.view.xml` 6 个自定义 `@mutation:` 动作改 `/r/` 直发（recoverDeleted/enableUser/disableUser/resetUserPassword/add/removeRoleUsers）；RPC 实测 `/r/` 成功、旧 `{data}` 报「没有定义参数[data]」；e2e「恢复已删除用户」。
5. **部门标签页交互异常** — `verified`：实跑确认标签切换、新建部门、选择父部门（tree-select 显示名称）均正常；新增 `auth-dept.spec.ts`（2 用例）+ 截图 `ai-dev/testing/2026-10/w5-dept-screenshots/`。
6. **表单 label 布局** — `fixed`：新增/修改表单缺省 `mode=vertical`、查询 `horizontal`（`container_simple.xpl`/`page_simple.xpl`）；golden 刷新；浏览器实测查询 label 在左、编辑 label 在上。

## 追加发现（2026-10-17，tree / IME / 敏感表）

### 7. 树形 CRUD（NopAuthDept/tabsView 等）没有行展开/收起按钮 `fixed`

- **现象**：`#/pages/NopAuthDept/tabsView` 的 tree 视图只平铺显示，无展开三角。
- **根因**：flux 表格仅在 schema 显式给出 `rowChildrenField` 时才进入 tree 模式（nop-chaos-flux `packages/flux-renderers-data/src/table-renderer/use-table-tree.ts:129`），而 flux 页面生成模板 `nop-frontend-support/.../flux-web/grid_crud.xpl` 从未输出该属性（amis 自动识别 `children`，flux 不识别）——flux 迁移回归；视图定义本身正确（`tree-list` 已声明 `children @TreeChildren(max:5)`）。
- **修复**：生成器按 `objMeta.tree.childrenProp` 是否出现在 grid selection 中推导 `rowChildrenField`；非树 grid 不输出。回归 `TestFluxWebCrudPage#testTreeCrudDeclaresRowChildrenField` + 非树负例；`TestFlux*`/`TestWebPage*` 67 全绿。
- **复测**：需重新 `mvn install` `nop-frontend-support/nop-web` 后在浏览器确认展开箭头（未做）。

### 8. 表单中文输入法按 Enter 上屏候选误触发表单提交 `fixed（跨仓 nop-chaos-flux）`

- **现象**：部门新建等表单输入中文，按 Enter 确认候选词即提交。
- **根因**：`form.tsx` section 级 keydown 未过滤 IME 组合输入状态。
- **修复**：nop-chaos-flux `packages/flux-renderers-form/src/renderers/form.tsx` 增加 `isComposing || keyCode === 229` 守卫；缺省 Enter 提交策略不变（AMIS parity，逐表单可 `preventEnterSubmit: true`）。
- **验证**：新增两条判别测试；flux-renderers-form 全量 1127 通过。

### 9. Email Code / Login Attempt 等页面只有序号+操作列；敏感表 web 暴露 `open`

- **现象**：Email Code、Login Attempt 等管理页仅有 index 与 operation 两列。
- **根因**：ORM/xmeta 将字段标为 `ui:show="X"`/`internal`，生成 `<cols>` 为空；Email Code 的提交生成物 `nop-auth/nop-auth-web/src/main/resources/_vfs/nop/auth/pages/NopAuthEmailCode/_gen/_NopAuthEmailCode.view.xml` 还陈旧地把 `email` 列标为 abstract（与当前 xmeta 不一致，按源模型重生成应显示 email）。
- **安全面**：`NopAuthEmailCodeBizModel` 已继承 `MfaSensitiveTableBizModel`（mutation 全拒）；`NopAuthLoginAttemptBizModel` 仍是裸 `CrudBizModel`，页面保留 add/edit/delete，可删除失败计数行绕过限流。菜单仍在 `_nop-auth.action-auth.xml` 暴露。
- **结论**：ORM `not-pub` 仅做字段级 GraphQL 输出剔除，`no-web` 仅影响页面/菜单生成，均**不能**禁用实体的 `findPage/get`/`/r/` 端点。
- **状态**：open，待决策（移除菜单 / 只读收口 / 暴露安全列）。

# 2026-10-17 flux 查询无响应 / 标签页切换不刷新（复核与收口）

> 状态：已修复并实跑验证。承接 `2026-10-17-flux-manual-test-findings.md` 的第二批手工测试发现。

## 测试环境

- `nop-quarkus-demo`（8083）/ `nop-auth-app`（8080），flux 渲染模式，dev profile。

## 发现清单

### 1. 报表演示（传参）点击"查询"无效果 `fixed`

- **现象**：`#/report/demo/pages/demo2` 填写标题后点"查询"，无任何请求/无报表渲染。
- **对比 amis**：amis 用 `<form target="reportService">` 把表单提交到 service；flux 版改为 data-source + 按钮 `refreshSource`，但 data-source 的 `${title}/${reportName}` 读的是**页面作用域**，而表单字段默认只存在于表单作用域，未发布到页面作用域 → `when` 恒假、data 恒空，查询无请求。
- **修复**：`report-with-params.flux.yaml` 给表单加 `valuesPath: queryForm`，data-source 改用 `${queryForm?.title}` / `${queryForm?.reportName}`。
- **验证**：浏览器填标题点查询 → 发 `POST /r/ReportDemo__renderHtml` 并渲染报表；`TestFluxYamlPages` 通过。

### 2. 部门标签页：新增后不刷新 / 切换标签页不刷新 `fixed`

- **现象**：（初始报告）树形视图新增部门后未看到刷新，切换列表视图也未自动刷新。
- **对比 amis**：amis 标签容器支持 `mountOnEnter`/`unmountOnExit`（容器级），切换时卸载/重挂 → 重新加载数据。
- **根因**：flux 的 `TabsSchema` 原先**只支持 item 级** `mountOnEnter`/`unmountOnExit`；生成器 `FluxTabsDefaultAttrs` 把 amis 的**容器级**这两个属性输出到 `tabs` 节点上 → 被 flux 忽略 → 标签内容常驻挂载，切换/新增后不重新加载。
- **修复（nop-chaos-flux）**：`TabsSchema` 增加容器级 `mountOnEnter`/`unmountOnExit`，`tabs.tsx` 以 `item.X ?? schema.X` 解析（item 覆盖容器）；renderer definition 补这两个 prop；新增容器级回归测试。
- **验证**：浏览器实测切换标签时非激活面板卸载（DOM 中仅存激活面板）、切回重新挂载；新增部门后当前列表行数 0→1（自身刷新正常）。

## 备注

- 第 2 条的修法是**在 nop-chaos-flux 增加容器级支持**（生成器的容器级输出保持 amis 语义、不改生成侧），符合"能容器级则容器级"的取舍。
- 未做任何生成侧 hack（曾尝试把容器级下发到 item，已回退）。

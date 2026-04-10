# 构建设计器或专用编辑器页面

## 适用场景

- 页面主体不是普通 CRUD，而是流程设计器、图编辑器、专用可视化页面。
- 需要 `page.yaml` 中混合少量生成能力和大量手写 schema。

## AI 决策提示

- 这类页面优先以 `page.yaml` 为主，而不是强行塞进普通 `view.xml CRUD`。
- 可以保留 `x:gen-extends`，但主体通常是手写专用组件 schema。
- 优先把 toolbar、initApi、主体组件分开看。

## 最小闭环

```yaml
type: page
x:gen-extends: >
  <dingflow-gen:GenFlowEditorPage modelPath="dingflow.graph-designer.xml" xpl:lib="/nop/wf/xlib/dingflow-gen.xlib" />
toolbar:
  - type: button
    label: 保存
body:
  - type: nop-flow-editor
    initApi:
      url: /r/PageProvider__getPage?path=/nop/wf/designer/demo-data.page.json
```

## 默认结构拆法

1. `x:gen-extends`
   用于接入已有生成基线或默认壳层。
2. `toolbar`
   放页面级动作。
3. `body`
   放专用组件。
4. `initApi` / 专用 schema
   放初始化数据和大块编辑器配置。

## 最值得抄的真实例子

1. `nop-wf/nop-wf-web/src/main/resources/_vfs/nop/wf/designer/designer.page.yaml`

这个例子同时包含：

1. `x:gen-extends`
2. 手写 toolbar actions
3. `nop-flow-editor` 主体
4. `initApi`
5. 大块 `flowEditorSchema`

## 常见坑

1. 这类专用页面还试图完全套普通 CRUD 模板。
2. 把大块 schema 内联到 `view.xml` 的 grid/form 里，导致结构失控。
3. 不区分页面壳层配置和专用组件配置。

## 相关文档

- `../02-core-guides/page-dsl-pattern-catalog.md`
- `../02-core-guides/xlang-and-xpl-basics.md`
- `../02-core-guides/view-and-page-customization.md`

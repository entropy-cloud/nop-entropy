# 2026-07-20 Render Mode Switch x:post-extends c:print 实现修正

## Problem

`impl_flux_mode.xpl` 通过 `web.xlib` 的 `x:post-extends` 实现标签替换时有多个实现错误：

1. 使用 `xpl:tag="c:print"`（该属性不存在）——应直接使用 `<c:print>` 元素代替 `xpl:is` 委托
2. 输出直接以 `<tags>` 为根（缺少 `<lib>` 包装）→ 启动时 `nop.err.xlang.xdsl.undefined-child-node` 异常
3. `<source>` body 不必要地包裹 `<c:print>`，导致 `GenPage` 被调用时 `${view}` 无法求值

## Diagnostic Method

- 运行 `TestRenderModeSwitch` 发现 `NopException: undefined-child-node`，报错位置指向 `impl_flux_mode.xpl` 的 `GenPage` 标签
- 异常堆栈指向 `XDslExtender.genCpExtends` 方法，顺此阅读源码发现：
  - `c:print` 输出会包裹 `<_>` dummy node
  - `genCpExtends` 对 `<_>` 执行 `detachChildren()`，子节点作为独立 XDSL source
  - 每个子节点按 `lib` xdef 校验根节点，`<tags>` 不是合法根 → 异常
- 曾假设是 `xpl:tag` 属性名问题，测试发现该属性根本不存在后才改为直接 `<c:print>` 元素
- 读 `PrintTagCompiler` 源码确认 `c:print` 在 `outputMode=node` 时如何处理 body 子节点，确认外层 `<c:print>` 已经能保护 `${view}`，不需在内层再包一层

## Root Cause

1. **根节点包装缺失**：`x:post-extends` 的输出通过 `genCpExtends` 处理后，`<_>` 占位节点的子节点被剥离为独立 XDSL source，每个 source 按 `lib` xdef 校验根节点名称。输出必须以 `<lib>` 为根。
2. **`c:print` 的双重作用混淆**：`c:print` 有两个角色：（a）post-extends 执行时保护 `${view}` 不被编译；（b）如果放在 `<source>` body 内，tag 被调用时 `c:print` 仍然保持 `${view}` 字面量，阻止了正常求值。

## Fix

1. `impl_flux_mode.xpl` 输出改为以 `<lib>` 为根 → `<lib><tags>...</tags></lib>`
2. `<source>` body 不再包裹 `<c:print>`，直接写 `<flux-web:GenPage view="${view}" .../>`，让 tag 被调用时正常编译求值
3. 外层唯一的 `<c:print>` 包裹整个 `<lib>` 块，保护 `${view}` 在 post-extends 阶段不被编译

## Tests

- `nop-frontend-support/nop-web/src/test/java/io/nop/web/page/TestCPrintInPostExtends.java` — 5 个测试覆盖 `c:print` + `xpl:is` 行为
- `nop-frontend-support/nop-web/src/test/java/io/nop/web/page/TestRenderModeSwitch.java` — 3 个测试（之前报错，现已通过）
- `nop-frontend-support/nop-web/src/test/java/io/nop/web/page/TestFluxWebGen.java`、`TestFluxWebCrudPage.java`、`TestPageProvider.java` — 28 个测试全部通过

## Affected Files

- `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/web/impl_flux_mode.xpl` — 修正输出结构
- `ai-dev/design/render-mode-switch-design.md` — 更新设计文档（标记已实现，修正 3.4 节和 4.1 节，修正第 14 行和第 236 行描述）
- `docs-for-ai/02-core-guides/xlang-and-xpl-basics.md` — 补充 `c:print` 和 `xpl:is` 文档
- `nop-frontend-support/nop-web/src/main/java/io/nop/web/page/WebPageHelper.java` — 更新 fixPage Javadoc

## Notes For Future Refactors

- `x:post-extends` 输出必须是与根节点 xdef 匹配的合法根元素，不可直接输出片段
- `c:print` 适合保护 post-extends 阶段不编译 `${var}`，但不能用在 tag 的 `<source>` body 内
- 测试 `x:post-extends` 输出结构时，注意 `genCpExtends` 的 `_` 截断逻辑

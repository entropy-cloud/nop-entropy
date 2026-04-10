# 构建完整后台工作台页面

## 适用场景

- 一个后台页面不仅仅是普通列表，还要承担：
- 主列表
- 运行态概览
- 关联对象管理
- 批量操作
- 导出 / 导入
- 状态分组页面

## AI 决策提示

- 优先把工作台拆成“主页 + 若干子页 + actions + tabs/drawer”。
- 如果一个对象有多个操作上下文，优先用 tabs 或多个 crud 变体，不要做成一个超长页面。
- 工作台页一般不是从零写，而是在生成页之上重组结构。

## 最小拆法

1. 主列表：`crud name="main"`
2. 派生列表：`x:prototype="main"` 或派生 grid
3. 运行态页：`simple` / `crud`
4. 工作台切换：`tabs` 或 row action `drawer`
5. 批量动作：`batch="true"`
6. 关联页：`ref-*.page.yaml` 或独立 `crud`

## 最值得抄的真实例子

1. `nop-job/nop-job-web/src/main/resources/_vfs/nop/job/pages/NopJobSchedule/NopJobSchedule.view.xml`
   这是“主列表 + 运行态 summary + 关联 fires + 生命周期动作”的完整工作台。
2. `nop-job/nop-job-web/src/main/resources/_vfs/nop/job/pages/NopJobFire/NopJobFire.view.xml`
   这是“主列表 + 运行态 summary + 关联 tasks + 跨对象 drill-down”的完整工作台。
3. `C:/can/nop/nop-app-mall/app-mall-web/src/main/resources/_vfs/app/mall/pages/LitemallAftersale/LitemallAftersale.view.xml`
   这是“全部 / 待审批 / 待退款”多状态后台工作台。
4. `nop-dyn/nop-dyn-web/src/main/resources/_vfs/nop/dyn/pages/NopDynModule/NopDynModule.view.xml`
   这是“批量发布 / 导入 / 导出 / 关联模块管理 / 关联实体页 / 页面页”的应用型后台工作台。

## 设计顺序

1. 先定有哪些用户任务。
2. 再决定拆成几个子页。
3. 再决定哪些走 tabs，哪些走 row drawer。
4. 最后补批量动作、导入导出、关联页入口。

## 常见坑

1. 试图把所有功能塞进一个 CRUD 页面而不拆子页。
2. 关联页、运行态页、导入导出全部直接内联，导致页面不可维护。
3. 没有给工作台里的 grid、dialog、picker 起稳定名字，后续 `reload` / `close` 很难接。

## 相关文档

- `./build-tabs-workspace-page.md`
- `./add-runtime-or-related-page.md`
- `./add-export-or-batch-operations.md`
- `../02-core-guides/page-dsl-pattern-catalog.md`

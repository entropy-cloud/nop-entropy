# 构建关联子表 Drawer 页面

## 适用场景

- 父表某一行需要打开关联子表页面。
- 需要把父对象的 id、名称或其他上下文传给子页面。
- 子页面本身仍然想复用已有 CRUD/view，而不是重写一份新页面。

## AI 决策提示

- 优先用 row action 打开 drawer/dialog 页面。
- 优先通过 `<data>` 把父上下文传给子页。
- 如果子页本质是“固定某个外键的关联子表”，优先考虑 `fixedProps` page wrapper。

## 最小闭环

### 1. 在父页面增加 row action

```xml
<action id="items-button" label="条目" actionType="drawer">
    <dialog page="/nop/sys/pages/NopSysDictOption/dict-ref.page.yaml" size="lg">
        <data>
            <dictId>$id</dictId>
            <dictName>$displayName</dictName>
        </data>
    </dialog>
</action>
```

### 2. 子页只做薄 wrapper

```yaml
x:gen-extends: |
  <web:GenPage view="NopRuleNode.view.xml" page="main" fixedProps="ruleId" xpl:lib="/nop/web/xlib/web.xlib" />
```

## 什么时候用 `fixedProps`

适合：

1. 这是一个关联子表 CRUD 页面。
2. 外键值来自上层页面。
3. 希望子页里这个外键变成固定上下文字段，而不是继续编辑。

## 最值得抄的真实例子

1. `nop-sys/nop-sys-web/src/main/resources/_vfs/nop/sys/pages/NopSysDict/NopSysDict.view.xml`
   适合看父页 row action 如何打开子页 drawer 并传 `dictId`、`dictName`。
2. `nop-rule/nop-rule-web/src/main/resources/_vfs/nop/rule/pages/NopRuleNode/ref-ruleDefinition.page.yaml`
   适合看 `fixedProps="ruleId"` 关联子表页。
3. `nop-job/nop-job-web/src/main/resources/_vfs/nop/job/pages/NopJobFire/NopJobFire.view.xml`
   适合看一个 row action 同时钻取到其他对象的 view 页面和关联列表页。

## 常见坑

1. 子页只是关联子表，却复制整份 view，而不是先做 wrapper。
2. 打开 drawer 时没传父上下文，子页无法过滤。
3. 明明应该固定外键，却仍然让子页继续编辑这个关联字段。

## 相关文档

- `../02-core-guides/page-dsl-pattern-catalog.md`
- `../02-core-guides/view-and-page-customization.md`

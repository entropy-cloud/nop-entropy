# Delta 定制

当你的目标是“在不破坏升级路径的前提下定制现有产品或生成结果”时，默认先考虑 Delta。

## Delta 的默认结论

1. 覆盖已有模型、beans、xbiz、view 时，优先 `_vfs/_delta/...`。
2. Delta 文件必须对应原始路径。
3. Delta 文件必须使用 `x:extends="super"`。
4. 只有在确实是保留层或源模型时，才直接修改非 Delta 文件。

## Delta 文件位置

```text
src/main/resources/_vfs/_delta/{deltaDir}/...
```

典型例子：

```text
src/main/resources/_vfs/_delta/default/nop/auth/orm/app.orm.xml
```

这里的 `default` 是 delta 目录名，可以按项目约定调整，但 `default` 是仓库里最常见的命名。

## 基本写法

```xml
<orm x:schema="/nop/schema/orm/orm.xdef"
     xmlns:x="/nop/schema/xdsl.xdef"
     x:extends="super">
    <entities>
        <!-- 增量修改 -->
    </entities>
</orm>
```

## 常见操作

### 新增字段

- 在 Delta ORM 中声明扩展实体。
- 原有基础字段需要 `tag="not-gen"`，新增字段不需要。

### 覆盖属性

使用 `x:override="replace"` 或 `merge`。

### 删除节点

使用 `x:override="remove"`。

## 常见适用场景

| 场景 | 默认做法 |
|------|---------|
| 覆盖基础产品 ORM | Delta |
| 覆盖 beans 配置 | Delta |
| 覆盖 xbiz / view | Delta 或非下划线扩展文件 |
| 升级兼容优先 | Delta |

## 与非下划线扩展文件的关系

除了 Delta 之外，仓库里也大量使用“非下划线文件扩展下划线文件”的方式保留可定制层，例如：

- `Xxx.xbiz` 扩展 `_Xxx.xbiz`
- 非 `_gen` 页面文件覆盖或补充 `_gen` 产物

默认判断：

1. 如果是在自己的模块里扩展生成保留层，优先非下划线文件。
2. 如果是在已有产品或基础模块上做差量覆盖，优先 Delta。

## 常见坑

1. 直接修改基础产品源码。
2. 直接修改 `_app.orm.xml`、`_service.beans.xml`、`_*.xbiz`。
3. 新建 Delta 文件却忘了 `x:extends="super"`。
4. Delta 路径和原文件路径不一致。

## 相关文档

- `./model-first-development.md`
- `./xdef-and-xdsl.md`
- `../03-runbooks/prefer-delta-over-direct-modification.md`
- `../04-reference/source-anchors.md`

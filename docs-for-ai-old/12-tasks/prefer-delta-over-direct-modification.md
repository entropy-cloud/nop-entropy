# 优先使用 Delta，而不是直接修改基础实现

## 适用场景

- 你要定制已有产品/模块行为
- 你不希望后续升级时丢失改动

## AI 决策提示

- ✅ 先考虑 `_vfs/_delta/...`
- ✅ 非下划线资源文件优先 `x:extends` 下划线基类文件
- ❌ 不要先改基础产品源码或生成物

## 最小闭环

### 1. 找到基础文件对应路径

例如已有文件：

```text
src/main/resources/_vfs/nop/auth/orm/app.orm.xml
```

### 2. 在 Delta 目录下创建同路径覆盖文件

```text
src/main/resources/_vfs/_delta/default/nop/auth/orm/app.orm.xml
```

### 3. 使用 `x:extends`

```xml
<orm x:extends="super">
    <entities>
        <!-- 增量修改 -->
    </entities>
</orm>
```

## 何时优先 Delta

| 场景 | 默认做法 |
|------|---------|
| 定制基础产品 | Delta |
| 覆盖 beans / xbiz / view / orm | Delta |
| 升级兼容优先 | Delta |

## 常见坑

- ❌ 直接改基础模块源码
- ❌ 直接改 `_app.orm.xml`、`_service.beans.xml`、`_*.xbiz`
- ❌ 忘记 `x:extends`

## 相关文档

- `01-core-concepts/delta-basics.md`
- `03-development-guide/project-structure.md`

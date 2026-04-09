# 新增字段与校验

## 适用场景

- 给实体增加字段。
- 增加必填、范围、格式、可查询、可排序等规则。

## AI 决策提示

- 优先修改模型和元数据，不要先写 Java 校验。
- 如果字段影响 UI 或 API，继续沿着 XMeta / view / page 这条链路检查。
- 只有复杂业务规则才考虑落到 BizModel 扩展点。

## 默认闭环

### 1. 找到源模型

优先看：

- `model/*.orm.xml`
- 对应对象的 `.xmeta` 链路

### 2. 增加字段定义与元信息

关注这些能力：

- 字段类型
- 是否 mandatory
- 是否 queryable / sortable
- 是否需要 dict、domain、precision

### 3. 如果页面受影响，再看 view / page

在当前仓库里，页面文件通常通过非下划线 `view.xml` 扩展 `_gen/_*.view.xml`，并通过 `<objMeta>` 指向对应 XMeta。

### 4. 如果是产品差量定制，优先 Delta

在 `_vfs/_delta/default/...` 中创建同路径文件，使用 `x:extends="super"` 做增量覆盖。

### 5. 构建验证

默认执行模块构建或根构建，检查新字段是否进入 ORM / meta / web 链路。

## 何时才写 Java 校验

只有以下情况才优先考虑 BizModel / Processor：

1. 校验依赖复杂上下文。
2. 校验涉及跨实体或外部服务。
3. 校验规则本质上是业务动作约束，而不是字段结构约束。

## 常见坑

1. 只改 view，不改模型或 meta。
2. 只改生成文件，不改源模型。
3. 把结构校验写成大量 Java if/else，而忽略模型驱动能力。

## 相关文档

- `../02-core-guides/model-first-development.md`
- `../02-core-guides/delta-customization.md`
- `../01-repo-map/where-things-live.md`

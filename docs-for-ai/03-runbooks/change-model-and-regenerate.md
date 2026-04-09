# 修改模型后重新生成

## 适用场景

- 你修改了 `model/*.orm.xml`。
- 你想确认哪些模块会接收新的生成结果。

## AI 决策提示

- 最简单可靠的做法是从项目根目录触发 Maven 构建。
- 如果只改了生成物而没回到源模型，先停下来修正路径。

## 最小闭环

### 1. 确认你改的是源模型

优先改 `model/{app}.orm.xml`，而不是 `_app.orm.xml` 或 `_gen` 文件。

### 2. 重新构建

```bash
./mvnw clean install -T 1C
```

### 3. 理解生成职责

| 模块 | 作用 |
|------|------|
| `*-codegen` | 刷新项目级生成产物 |
| `*-dao` | 刷新 ORM、Entity、接口等 |
| `*-meta` | 刷新 XMeta 与 i18n |
| `*-web` | 刷新页面文件 |

### 4. 检查结果

至少检查一个下游点位是否更新：

1. `*-dao` 下的 ORM / Entity / 接口
2. `*-meta` 下的 XMeta / i18n
3. `*-web` 下的页面资源

## 常见坑

1. 只重跑下游模块，没有刷新上游生成物。
2. 以为 `*-meta` 会直接生成全部 service / web 代码。
3. 手改生成物后再去构建，结果改动被覆盖。

## 相关文档

- `../02-core-guides/model-first-development.md`
- `../01-repo-map/domain-module-pattern.md`
- `../04-reference/source-anchors.md`

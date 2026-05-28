# 调试代码生成与生成文件

## 适用场景

- 生成结果不符合预期。
- 你想确认某个文件是从哪里来的。
- 你想判断应该改模型、模板还是保留层文件。

## AI 决策提示

- 先判断它是不是生成物。
- 先回到源模型、`.xgen` 或模板，不要直接改输出文件。
- 如果链路不清楚，先看 `*_codegen`、`*_meta`、`*_web` 的生成职责。

## 最小闭环

### 1. 判断文件是不是生成物

高概率生成物包括：

- `_gen/`
- `_` 前缀文件
- `_app.orm.xml`
- `_service.beans.xml`
- `_*.xbiz`

### 2. 追溯来源

| 目标文件 | 先看哪里 |
|----------|---------|
| Entity / `I*Biz` / 业务骨架 | `*-codegen/postcompile/gen-orm.xgen`、`/nop/templates/orm` |
| XMeta / `module-meta.json` | `*-meta/precompile/gen-meta.xgen`、`/nop/templates/meta` |
| i18n | `*-meta/postcompile/gen-i18n.xgen` |
| view / page | `*-web/precompile/gen-page.xgen`、`/nop/templates/orm-web` |

### 3. 重新生成

默认优先从项目根目录执行：

```bash
./mvnw clean install -T 1C
```

### 4. 检查下游结果

至少检查一个下游点位：

1. `*-dao` 下的 ORM / Entity / 接口。
2. `*-meta` 下的 XMeta / i18n。
3. `*-meta` 下的 `/{moduleId}/model/module-meta.json`（如果页面或菜单逻辑依赖模块级元数据）。
4. `*-web` 下的页面资源。
5. 如果还有手写 source `*.action-auth.xml` 覆盖文件，确认显式 `TOPM` / `SUBM` 资源也带有 `icon`。

## 常见坑

1. 直接改生成文件。
2. 误把 `*-meta` 当成 service / web 的唯一上游。
3. 忘记页面模板读取的可能是 `module-meta.json`，而不是直接读取 `/{moduleId}/orm/app.orm.xml`。
4. 页面不对时去改输出文件，而不是回到 xmeta / `module-meta.json` / page 生成链路。
5. 只重跑下游模块，没有刷新上游生成物。
6. 以为只有 ORM entity 需要 icon，忘了根 `<orm ext:icon>` 也会直接影响 generated TOPM 菜单。
7. 手写 `*.action-auth.xml` 中声明了 `TOPM` / `SUBM`，却没显式写 `icon`。

## 相关文档

- `./change-model-and-regenerate.md`
- `../02-core-guides/model-first-development.md`
- `../02-core-guides/debugging-and-diagnostics.md`
- `../04-reference/source-anchors.md`

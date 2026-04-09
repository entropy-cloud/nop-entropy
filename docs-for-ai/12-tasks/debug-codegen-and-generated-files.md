# 调试代码生成与生成文件

## 适用场景

- 生成结果不符合预期
- 你想确认某个文件是从哪里生成出来的
- 你想判断应该改模型、模板、还是保留层文件

## AI 决策提示

- ✅ 先确认源文件：模型、模板、xmeta、web 生成脚本
- ✅ 先判断文件是否是生成物
- ✅ 优先改源模型或模板，不改生成结果

## 最小闭环

### 1. 判断文件是否为生成物

高概率生成物：

- `_gen/`
- `_` 前缀文件
- `_app.orm.xml`
- `_service.beans.xml`
- `_*.xbiz`

### 2. 追溯来源

| 目标文件 | 先看哪里 |
|----------|---------|
| Entity / Biz 接口 / service 基础文件 | `*-codegen/postcompile/gen-orm.xgen`、`/nop/templates/orm` |
| XMeta | `*-meta/precompile/gen-meta.xgen` |
| i18n | `*-meta/postcompile/gen-i18n.xgen` |
| view / page | `*-web/precompile/gen-page.xgen`、`/nop/templates/orm-web` |

### 3. 重新生成

```bash
mvn clean install
```

## 常见坑

- ❌ 直接改生成文件
- ❌ 误把 `meta` 当成 service/web 的直接生成入口
- ❌ 看到页面不对，去改输出文件，而不是回到 xmeta 或 web 生成链路

## 相关文档

- `03-development-guide/project-structure.md`
- `12-tasks/change-model-and-regenerate.md`
- `13-reference/source-anchors.md`

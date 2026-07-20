# 维度 05：生成管线完整性 — 审计报告

> 初审结果（待复核）

## 审计结论：通过（轻微发现）

生成管线完整闭合，所有 32 个实体可追溯至每个下游产物层。未出现管线断裂。

## 关键验证点

| 检查项 | 状态 |
|--------|------|
| 源模型 nop-metadata.orm.xml 存在且格式正确 | ✅ |
| codegen/postcompile/gen-orm.xgen 引用正确模型路径 | ✅ |
| dao 生成产物（_app.orm.xml、_gen/*.java、I*Biz.java）一致 | ✅ |
| meta/precompile/gen-meta.xgen 正确执行 | ✅ |
| meta/postcompile/gen-i18n.xgen 正确执行 | ✅ |
| web/precompile/gen-page.xgen 正确生成页面 | ✅ |
| web/precompile2/gen-i18n.xgen 正确执行 | ✅ |
| 所有 32 个实体有对应的保留层 Entity、BizModel、I*Biz、xbiz、xmeta、页面 | ✅ |
| Maven exec-maven-plugin 配置正确 | ✅ |

## 生成管线图

```
model/nop-metadata.orm.xml (source)
  |--> codegen/postcompile/gen-orm.xgen --> /nop/templates/orm
  |       |--> dao/_app.orm.xml (generated aggregate)
  |       |--> dao/_gen/_Nop*.java (generated entities)
  |       |--> dao/Nop*.java (retention entities)
  |       |--> dao/biz/INop*Biz.java (generated biz interfaces)
  |
  |--> meta/precompile/gen-meta.xgen --> /nop/templates/meta
  |       |--> meta/*/Nop*.xmeta (retention)
  |
  |--> meta/postcompile/gen-i18n.xgen --> /nop/templates/i18n
  |       |--> i18n resource files
  |
  |--> web/precompile/gen-page.xgen --> /nop/templates/orm-web
  |       |--> web/pages/*/main.page.yaml, picker.page.yaml
```

## 注意项（非阻塞）

- CRUD API 生成（gen-crud-api.xgen）被注释禁用，nop-metadata-api 模块留空——这符合设计意图（框架模块通常不需要外部 RPC 契约）

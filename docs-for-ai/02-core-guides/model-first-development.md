# 模型优先开发

当前仓库的默认开发顺序不是“先写 Java”，而是：

**先模型，再生成，再补保留层代码。**

## 默认流程

1. 修改 `model/*.orm.xml`。
2. 首次建模块时使用 `nop-cli gen` 生成骨架。
3. 后续变更优先用 `./mvnw` 触发再生成与构建。
4. 只在非生成文件中写定制逻辑。

## 首次生成骨架

```bash
nop-cli gen model/{appName}.orm.xml -t=/nop/templates/orm -o=.
```

这个命令的用途是生成标准业务骨架，而不是日常每次改模型都重跑一遍。

## 后续模型变更

默认优先在项目根目录执行：

```bash
./mvnw clean install -T 1C
```

如果只想理解生成职责，可以按模块顺序看：

1. `{appName}-codegen` 刷新项目级生成产物。
2. `{appName}-dao` 接收 ORM、Entity、接口等结果。
3. `{appName}-meta` 生成 XMeta 与 i18n。
4. `{appName}-web` 基于 XMeta 生成页面相关文件。
5. `{appName}-service` / `{appName}-app` 接收对应派生产物并参与构建。

## 真实链路

```text
model/{app}.orm.xml
  -> {app}-codegen/postcompile/gen-orm.xgen
  -> {app}-dao / {app}-service / {app}-meta / {app}-web
  -> {app}-meta/precompile/gen-meta.xgen
  -> {app}-meta/postcompile/gen-i18n.xgen
  -> {app}-web/precompile/gen-page.xgen
```

## AI 的默认修改顺序

| 目标 | 默认修改位置 |
|------|-------------|
| 表、字段、关系、字典 | `model/*.orm.xml` |
| 页面或元数据派生能力 | 先看 model / xmeta / xbiz |
| 升级友好的产品定制 | Delta |
| 实体辅助方法 | 保留层 Entity |
| 业务接口逻辑 | BizModel / Processor |

## 什么时候不要直接写 Java

以下情况先不要手写服务或 DAO：

1. 只是新增字段、校验、字典、关系。
2. 标准 CRUD 已经够用。
3. 页面和 API 结构可以由模型派生。
4. 只是要在现有产品上做差量定制。

## 不能手改的典型文件

- `_gen/` 目录。
- `_app.orm.xml`。
- `_service.beans.xml`。
- `_*.xbiz`、`_*.view.xml`、`_*.java`。

## 常见误区

1. 先手写 Entity / DAO / Biz 接口，再回头补模型。
2. 把 `*-meta` 误解为直接生成全部 service / web 代码。
3. 只改生成物，不回到源模型。
4. 改了模型却没有触发上游生成链。

## 相关文档

- `../01-repo-map/domain-module-pattern.md`
- `../03-runbooks/create-new-entity.md`
- `../03-runbooks/add-field-and-validation.md`
- `../03-runbooks/change-model-and-regenerate.md`
- `../04-reference/source-anchors.md`

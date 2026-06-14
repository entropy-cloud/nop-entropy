# 外部应用模块开发

本页回答一个高频问题：

**当你不是在改 `nop-entropy` 内部框架模块，而是在开发外部业务应用模块时，默认应该怎么组织工程、建模、生成、扩展和集成。**

## 先看什么

外部应用是基于 `nop-entropy` parent 构建的独立应用工程，不是 `nop-entropy` 主仓库内置模块。

参考外部应用项目中的典型文件结构来理解本页描述的模式。

## 最小骨架

一个完整外部应用的常见模块拆分：

1. `app-xxx-codegen`
2. `app-xxx-api`
3. `app-xxx-dao`
4. `app-xxx-service`
5. `app-xxx-web`
6. `app-xxx-app`
7. `app-xxx-wx`（可选，第三方集成）
8. `app-xxx-delta`（可选，覆盖平台模块）
9. `app-xxx-meta`

这说明外部应用默认仍然沿用 `model -> codegen -> dao -> meta -> service -> web -> app -> api` 主链路，但经常会额外带上：

1. `*-delta`，用于覆盖平台内置模块。
2. 集成模块，例如 `*-wx`，用于放第三方实现。

## 默认开发闭环

### 1. 先改源模型

外部应用的源模型通常位于 `model/` 目录下，如 `app-xxx.orm.xlsx`、`app-xxx.orm.xml`、`app-xxx.api.xml`。

说明：外部应用的 API 模型默认优先维护为 `.api.xml`，便于文本化审阅和 AI 编辑；如需保留 Excel 展示或导出，可同时保留 `.api.xlsx`，但 live codegen 输入应以 `api.xml` 为准。ORM 模型仍可按场景使用 XML 或 Excel。

### 2. 再走 codegen / meta / web 生成链

codegen 入口通常在 `*-codegen` 和 `*-web` 的测试目录中：

1. `app-xxx-codegen/src/test/java/.../XxxCodeGen.java`
2. `app-xxx-web/src/test/java/.../XxxWebCodeGen.java`

这两个文件直接展示了：

1. `XCodeGenerator.runPostcompile(...)`
2. `XCodeGenerator.runPrecompile(...)`

如何在外部应用里按阶段执行生成。

### 3. 手改保留层，而不是生成物

典型例子：

1. `app-xxx-dao/src/main/resources/_vfs/.../orm/app.orm.xml`
   只做 `x:extends="_app.orm.xml"` 的薄扩展。
2. `app-xxx-meta/src/main/resources/_vfs/.../model/Xxx/Xxx.xmeta`
   只覆盖 `insertable` / `updatable` 等局部属性。
3. `app-xxx-web/src/main/resources/_vfs/.../pages/Xxx/Xxx.view.xml`
   继承 `_gen/_Xxx.view.xml` 后做保留层定制。

### 4. 服务层仍以 BizModel 为中心

外部应用里最常见的 3 类定制：

1. 覆盖 `defaultPrepareQuery(...)` 做查询条件转换。
2. 覆盖 `defaultPrepareSave(...)` 做保存前同步字段。
3. 覆盖 `defaultPrepareUpdate(...)` 做更新后联动处理。

### 5. 集成接口与实现分离

默认模式：

1. 对外或跨模块契约放 `*-api`。
2. 第三方实现放独立集成模块。
3. 通过 app beans 明确装配，不要把实现混进 app 主模块。

## 外部应用里最值得记住的两类扩展

### 1. 覆盖平台内置模块

外部应用并不只是开发自己的 `app/...` 资源，也经常要通过 `_delta/default/nop/...` 覆盖平台已有模块。

### 2. 外部应用前端通常是"生成 + 保留层 + 少量 page.yaml 包装"

默认顺序：

1. 先让 `_gen/_Xxx.view.xml` 生成基线页面。
2. 再用 `Xxx.view.xml` 改 grid / form / page 结构。
3. 需要单独入口页时，再用 `page.yaml` 做很薄的包裹。

## 与内置模块相比的关键差异

| 场景 | 内置模块 | 外部应用 |
|------|---------|---------|
| 所在位置 | `nop-entropy` 主仓库 reactor 内 | 独立工程，但 parent 指向 `nop-entropy` |
| 目标 | 平台能力 / 标准模块 | 业务应用落地 |
| 常见附加模块 | 一般按平台模块职责拆分 | 常见 `*-delta`、第三方集成模块 |
| 模型形式 | `*.orm.xml` 常见 | Excel 模型也很常见 |

## 默认不要做的事

1. 把外部应用当成平台内部模块去改平台源码。
2. 直接手改 `_gen`、`_app.orm.xml`、`_service.beans.xml`。
3. 业务接口和第三方实现不分层，全部塞进 `app` 模块。
4. 页面定制一上来就复制整份生成 view，而不是做保留层覆盖。

## 相关文档

- `./model-first-development.md`
- `./delta-customization.md`
- `./service-layer.md`
- `./view-and-page-customization.md`
- `./page-dsl-pattern-catalog.md`
- `../01-repo-map/domain-module-pattern.md`

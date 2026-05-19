# 维度10：XDSL与XLang正确性

## 第 1 轮（初审）

**审核日期**: 2026-05-18
**审核范围**: nop-job 模块所有 XDSL 文件（orm.xml、xmeta、xbiz、beans.xml、view.xml、data-auth.xml、action-auth.xml）
**审核基线文档**: `docs-for-ai/02-core-guides/model-first-development.md`、`docs-for-ai/02-core-guides/delta-customization.md`

---

### 审核范围与文件清单

| 类型 | 文件数 | 已检查 |
|------|--------|--------|
| ORM 模型 (`*.orm.xml`) | 3（源模型 + 生成 + delta） | ✅ |
| XMeta 元数据 (`*.xmeta`) | 6（3 实体 × _gen + hand-written） | ✅ |
| XBiz 业务定义 (`*.xbiz`) | 16（8 实体 × _gen + hand-written） | ✅ |
| Beans IoC (`*.beans.xml`) | 8（3 _gen + 3 app + 1 delta + 1 retry-adapter） | ✅ |
| View 视图 (`*.view.xml`) | 8（4 _gen + 4 hand-written） | ✅ |
| Auth 认证 | 4（data-auth + action-auth × 2） | ✅ |

---

### 检查项 1：x:schema 引用正确性

**结论**: 所有 XDSL 文件的 `x:schema` 引用均指向正确的 xdef 定义文件，**未发现问题**。

| 文件类型 | x:schema 值 | 状态 |
|---------|------------|------|
| `*.orm.xml` | `/nop/schema/orm/orm.xdef` | ✅ |
| `*.xmeta` | `/nop/schema/xmeta.xdef` | ✅ |
| `*.xbiz` | `/nop/schema/biz/xbiz.xdef` | ✅ |
| `*.beans.xml` | `/nop/schema/beans.xdef` | ✅ |
| `*.view.xml` | `/nop/schema/xui/xview.xdef` | ✅ |
| `*.data-auth.xml` | `/nop/schema/data-auth.xdef` | ✅ |
| `*.action-auth.xml` | `/nop/schema/action-auth.xdef` | ✅ |

---

### 检查项 2：x:extends 使用正确性

**结论**: 所有 `x:extends` 使用均符合 Delta 定制规范，**未发现问题**。

| 模式 | 文件示例 | 状态 |
|------|---------|------|
| Hand-written 继承 _gen | `NopJobSchedule.xbiz x:extends="_NopJobSchedule.xbiz"` | ✅ |
| App 继承 _gen beans | `app-service.beans.xml import _service.beans.xml` | ✅ |
| App 继承 engine beans | `app-service.beans.xml import app-engine.beans.xml` | ✅ |
| Delta 使用 `x:extends="super"` | `_delta/default/.../app-engine.beans.xml` | ✅ |
| View 继承 _gen view | `NopJobSchedule.view.xml x:extends="_gen/_NopJobSchedule.view.xml"` | ✅ |
| Auth 继承 base auth | `app.data-auth.xml x:extends="nop-job.data-auth.xml"` | ✅（VFS 可解析到 nop-job-service 模块中的文件） |

---

### 检查项 3：x:override 属性语义

**结论**: 所有 `x:override` 使用语义正确，**未发现问题**。

使用场景：
- `_app.orm.xml` 中 `x:gen-ends x:override="replace"` 和 `x:post-extends x:override="replace"`：生成文件替换父级生成扩展，正确。
- `*.view.xml` 中 `x:override="remove"` 移除不需要的按钮操作（如 `batch-delete-button`、`add-button`），语义正确。

---

### 检查项 4：命名空间声明

**结论**: 所有 XDSL 文件均包含必要的命名空间声明，**未发现问题**。

`xmlns:x="/nop/schema/xdsl.xdef"` 在所有文件中均存在。各文件按需声明了 `xmlns:i18n-en`、`xmlns:ext`、`xmlns:xpl`、`xmlns:biz-gen`、`xmlns:meta-gen`、`xmlns:ioc`、`xmlns:ui` 等扩展命名空间。

---

### 检查项 5：自定义 XDSL 扩展遵循 XDef

**结论**: 所有自定义扩展（meta-gen、biz-gen、orm-gen）均遵循 XDef 模式定义，**未发现问题**。

---

### 检查项 6：beans.xml 中 bean 定义与 Java 类路径一致性

**结论**: 所有 bean 的 `class` 属性均指向存在的 Java 类，**未发现问题**。

验证结果：
- `io.nop.job.service.entity.NopJobScheduleBizModel` → 文件存在
- `io.nop.job.service.entity.NopJobFireBizModel` → 文件存在
- `io.nop.job.service.entity.NopJobTaskBizModel` → 文件存在
- `io.nop.job.service.executor.NopE2eTestJobInvoker` → 文件存在
- `io.nop.job.service.executor.RpcJobInvoker` → 文件存在
- `io.nop.job.coordinator.engine.*` 系列 → 文件存在
- `io.nop.job.worker.engine.*` 系列 → 文件存在
- `io.nop.job.retry.adapter.NopRetryJobRetryBridge` → 文件存在

---

### 检查项 7：XDSL 文件引用不存在的资源路径

**结论**: 发现 **1 个 P1 问题**，见【发现 1】。

---

### 检查项 8：xbiz 方法签名与 BizModel Java 类兼容性

**结论**: 3 个有 Java BizModel 的实体（Schedule、Fire、Task）的 xbiz 方法签名与 Java 方法兼容，**未发现问题**。另外 5 个实体的 xbiz 文件存在兼容性问题，见【发现 2】。

---

## 发现详细报告

### 发现 1：5 个 xbiz 文件的 entityName 引用不存在的 Java 实体类

**严重程度**: P1（契约漂移）

**文件路径与行号范围**:

| 文件路径 | 行号范围 |
|---------|---------|
| `nop-job/nop-job-service/src/main/resources/_vfs/nop/job/model/NopJobPlan/_NopJobPlan.xbiz` | 4-7 |
| `nop-job/nop-job-service/src/main/resources/_vfs/nop/job/model/NopJobDefinition/_NopJobDefinition.xbiz` | 4-7 |
| `nop-job/nop-job-service/src/main/resources/_vfs/nop/job/model/NopJobInstance/_NopJobInstance.xbiz` | 4-7 |
| `nop-job/nop-job-service/src/main/resources/_vfs/nop/job/model/NopJobInstanceHis/_NopJobInstanceHis.xbiz` | 4-7 |
| `nop-job/nop-job-service/src/main/resources/_vfs/nop/job/model/NopJobAssignment/_NopJobAssignment.xbiz` | 4-7 |

**证据代码** (以 NopJobPlan 为例，其余 4 个结构相同):

```xml
    <x:gen-extends>
        <biz-gen:DefaultBizGenExtends xpl:lib="/nop/core/xlib/biz-gen.xlib" forEntity="true"
                                      entityName="io.nop.job.dao.entity.NopJobPlan"/>
    </x:gen-extends>

    <x:post-extends>
        <biz-gen:DefaultBizPostExtends xpl:lib="/nop/core/xlib/biz-gen.xlib"/>
    </x:post-extends>

    <actions/>
```

**现状**:
- nop-job 模块的 ORM 模型文件 `model/nop-job.orm.xml` 仅定义了 3 个实体：`NopJobSchedule`、`NopJobFire`、`NopJobTask`。
- 上述 5 个 xbiz 文件的 `entityName` 分别引用 `io.nop.job.dao.entity.NopJobPlan`、`io.nop.job.dao.entity.NopJobDefinition`、`io.nop.job.dao.entity.NopJobInstance`、`io.nop.job.dao.entity.NopJobInstanceHis`、`io.nop.job.dao.entity.NopJobAssignment`。
- 这 5 个 Java 实体类均不存在于代码库中（通过 `find` 和 `grep` 全面确认）。
- 这 5 个实体也没有对应的 xmeta 文件（nop-job-meta 模块的 `_vfs/nop/job/model/` 下仅有 NopJobSchedule、NopJobFire、NopJobTask 三个目录）。
- 但这 5 个实体在 nop-job-service 和 nop-job-web 模块中残留了 xbiz 目录（各含 `_gen` 和手写版本）。

**风险**:
- xbiz 文件中的 `biz-gen:DefaultBizGenExtends` 会在生成阶段尝试读取 `entityName` 对应的 ORM 实体定义和 xmeta 元数据，由于这些均不存在，生成结果将为空或报错。
- NopJobPlan 的 view.xml (`_gen/_NopJobPlan.view.xml`) 引用了 `/nop/job/model/NopJobPlan/NopJobPlan.xmeta`，但该文件不存在，运行时访问该页面会导致 VFS 资源解析失败。
- 与 model-first-development.md 规范矛盾：XDSL 层次应保持一致（ORM → xmeta → xbiz → view），当前 ORM 只定义 3 个实体但上层有 8 个实体的 XDSL 文件。

**建议**:
1. 确认这 5 个实体是否为正在开发中的功能（尚未完成 ORM 定义）或是历史遗留。
2. 如果是历史遗留且不再使用：删除 `nop-job-service/src/main/resources/_vfs/nop/job/model/` 下的 `NopJobPlan/`、`NopJobDefinition/`、`NopJobInstance/`、`NopJobInstanceHis/`、`NopJobAssignment/` 目录，以及 `nop-job-web` 中对应的页面目录。
3. 如果是计划中的功能：应在 `model/nop-job.orm.xml` 中补充实体定义，然后重新运行代码生成流程。

**误报排除**:
- 已通过 `find`、`grep`、`ls` 多路径确认 Java 类和 xmeta 文件均不存在于 src 和 target 目录。
- 已确认 ORM 模型中确实没有这 5 个实体的定义（`<entity` 标签仅有 3 个）。
- 这些不是 `_gen` 前缀的生成文件（这些文件本身就带 `_` 前缀，是代码生成的输入模板），而是手写维护的源文件。

**审核状态**: 待确认业务意图

---

### 发现 2：NopJobPlan view.xml 引用不存在的 xmeta 路径

**严重程度**: P1（契约漂移）

**文件路径**: `nop-job/nop-job-web/src/main/resources/_vfs/nop/job/pages/NopJobPlan/_gen/_NopJobPlan.view.xml`
**行号范围**: 5-5

**证据代码**:

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<view x:schema="/nop/schema/xui/xview.xdef" bizObjName="NopJobPlan" xmlns:i18n-en="i18n-en"
      xmlns:x="/nop/schema/xdsl.xdef" xmlns:j="j" xmlns:gql="gql">

    <objMeta>/nop/job/model/NopJobPlan/NopJobPlan.xmeta</objMeta>
```

**现状**:
- `_NopJobPlan.view.xml` 中 `<objMeta>` 引用了 VFS 路径 `/nop/job/model/NopJobPlan/NopJobPlan.xmeta`。
- 通过 `ls` 确认 `nop-job-meta` 模块的 `_vfs/nop/job/model/` 下不存在 `NopJobPlan` 目录。
- `NopJobPlan.xmeta` 文件在整个 nop-job 模块中均不存在。
- 此 view 文件是生成文件（位于 `_gen/` 子目录），其内容由 ORM 模型和 xmeta 生成而来，但由于上游模型缺失，生成的 view 引用了不存在的 xmeta。

**风险**:
- 运行时访问 NopJobPlan 页面将触发 VFS 资源解析失败，导致页面无法加载。
- 如果有前端路由指向此页面，用户将看到错误而非预期界面。

**建议**:
- 与【发现 1】一并处理：要么补充 NopJobPlan 的 ORM 定义和 xmeta 文件，要么删除整个 NopJobPlan 页面目录（`nop-job-web/.../pages/NopJobPlan/`）。

**误报排除**:
- 已通过 `ls` 和 `find` 命令确认 `NopJobPlan.xmeta` 文件确实不存在。
- 这是生成文件引用生成时不存在资源的情况，问题根源在上游模型缺失。

**审核状态**: 待确认业务意图（与发现 1 关联）

---

## 合规确认（无问题项）

以下检查项经逐文件验证，**均合规，未发现问题**：

| 检查项 | 验证方法 | 结论 |
|--------|---------|------|
| x:schema 引用正确性 | 逐一读取所有 XDSL 文件头，比对 xdef 路径 | 全部正确 |
| x:extends 使用场景正确性 | 检查所有 `x:extends` 出现位置，区分 Delta 与非 Delta 文件 | 全部正确 |
| x:override 语义正确性 | 检查所有 `x:override` 使用场景 | 全部正确 |
| 命名空间声明完整性 | 检查所有 XDSL 文件的 xmlns 声明 | 全部完整 |
| beans.xml bean 类路径一致性 | 逐一比对 class 属性与实际 Java 文件路径 | 全部一致 |
| xbiz 方法签名兼容性 | 仅适用于有 Java BizModel 的 3 个实体，方法签名兼容 | 无问题 |
| dict 引用一致性 | 比对 xmeta 中 `dict="job/*"` 引用与 ORM 中 dict 定义 | 全部匹配 |
| meta-gen/biz-gen 库引用 | 检查 `xpl:lib` 引用的 xlib 路径 | 引用标准平台路径 |

---

### 正面观察（值得保持的模式）

1. **三层继承结构规范**: `_NopJobSchedule.xmeta` → `NopJobSchedule.xmeta`，`_NopJobSchedule.xbiz` → `NopJobSchedule.xbiz`，严格遵循生成/手写分离。
2. **beans.xml 层次清晰**: `_dao.beans.xml` → `app-dao.beans.xml`、`_service.beans.xml` → `app-service.beans.xml`、`_engine.beans.xml` → `app-engine.beans.xml`，依赖链清晰。
3. **Delta 定制规范使用**: `nop-job-worker` 模块通过 `_vfs/_delta/default/` 路径下的 `app-engine.beans.xml` 使用 `x:extends="super"` 正确实现 Delta 覆盖。
4. **view.xml 合理移除操作**: NopJobFire 和 NopJobTask 的 view 中通过 `x:override="remove"` 移除了不适用于只读/系统管理页面的增删按钮。

---

## 总结

| 严重程度 | 发现数 | 说明 |
|---------|--------|------|
| P0 | 0 | — |
| P1 | 2 | xbiz entityName 引用不存在的实体类（5 个文件）+ view 引用不存在的 xmeta |
| P2 | 0 | — |
| P3 | 0 | — |

两个 P1 发现互相关联，根因相同：5 个实体（NopJobPlan、NopJobDefinition、NopJobInstance、NopJobInstanceHis、NopJobAssignment）的 ORM 定义和 xmeta 文件缺失，但上层 xbiz 和 view 文件已存在。建议统一确认这 5 个实体的业务意图后做整批处理。

## 深挖第 2 轮追加

**无新发现。** 深挖范围：
- beans.xml 中所有 `class=` 引用的 Java 类均存在（app-service.beans.xml、app-dao.beans.xml、app-engine.beans.xml、job-retry-adapter.beans.xml、worker delta app-engine.beans.xml）
- 所有 3 个手写 xmeta 文件为空 `<props/>`，无 prop-to-field 不匹配
- 所有 8 个手写 xbiz 文件为空 `<actions/>`
- 生成的 `_engine.beans.xml` 和 `_dao.beans.xml` 为空桩（合法）

## 维度复核结论

| 编号 | 标题 | 判断 | 理由 |
|------|------|------|------|
| 发现1 | 5个 xbiz 文件引用不存在的 Java 实体类 | 保留 P1 | 证据充分。NopJobPlan、NopJobDefinition、NopJobInstance、NopJobInstanceHis、NopJobAssignment 的 `_*.xbiz` 均引用 `io.nop.job.dao.entity.*`，但通过 glob 确认这些 Java 类不存在。ORM 模型仅定义 3 个实体。这些是死代码或未完成功能残留。 |
| 发现2 | NopJobPlan view.xml 引用不存在的 xmeta 路径 | 保留 P1 | 证据准确。`_NopJobPlan.view.xml:5` 引用 `/nop/job/model/NopJobPlan/NopJobPlan.xmeta`，该路径在整个 nop-job 模块中不存在。运行时访问此页面将导致 VFS 解析失败。 |

### 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| 发现1 | P1 | `nop-job-service/.../_vfs/nop/job/model/{NopJobPlan,NopJobDefinition,NopJobInstance,NopJobInstanceHis,NopJobAssignment}/_*.xbiz` | 5个 xbiz 文件引用不存在的 Java 实体类，属于死代码残留 |
| 发现2 | P1 | `nop-job-web/.../pages/NopJobPlan/_gen/_NopJobPlan.view.xml` | view.xml 引用不存在的 NopJobPlan.xmeta 路径，运行时页面加载失败 |

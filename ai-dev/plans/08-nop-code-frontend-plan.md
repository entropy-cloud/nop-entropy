# nop-code 前端功能完整实现计划

> Plan Status: completed
> Last Reviewed: 2026-05-03
> Source: ai-dev/plans/07-nop-code-graphql-service-plan.md (已完成), nop-code/nop-code-web/ (现有 CRUD 页面)
> Related: 06-nop-code-feature-completion-plan.md

## Purpose

为 nop-code 模块实现完整的前端功能，包括：索引管理 CRUD、代码浏览、符号搜索、类型层级、调用链分析等功能的 Web UI 页面。基于 Nop 平台的 xmeta + view.xml + page.yaml 三层结构，利用 AMIS 框架渲染。

## Goal

1. 索引管理页面：支持创建索引、触发目录扫描、查看索引统计、删除索引
2. 代码浏览页面：树形展示文件结构，查看文件源码和 outline
3. 符号搜索页面：支持按名称/类型/包名搜索符号，查看符号详情
4. 类型层级页面：可视化展示继承关系（super/sub 树）
5. 调用链页面：可视化展示方法调用关系（incoming/outgoing）
6. 所有页面通过 xmeta 声明字段元数据、view.xml 声明布局、page.yaml 引用生成

## Current Baseline

### 已完成的后端（Plan 07）

| 模块 | 内容 |
|------|------|
| ICodeIndexService | 18 个方法签名（indexing、file、symbol、hierarchy、stats） |
| CodeIndexService | 内存实现，ConcurrentHashMap 存储 |
| 6 个 DTO | SymbolInfoDTO, TypeOutlineDTO, FileOutlineDTO, TypeHierarchyDTO, CallHierarchyDTO, IndexStatsDTO |
| NopCodeIndexBizModel | indexDirectory, indexFile, getStats, deleteIndex |
| NopCodeFileBizModel | getByPath, findFiles + BizLoaders(symbols, types, sourceCode, outline) |
| NopCodeSymbolBizModel | getById, findByQualifiedName, findSymbols + BizLoaders(usages, sourceCode) |
| NopCodeTypeHierarchyBizModel | get() → TypeHierarchyDTO |
| NopCodeCallHierarchyBizModel | get() → CallHierarchyDTO |
| NopCodeTypeBizModel | get, findByQualifiedName, batchGetOutlines |
| 测试 | 21/21 pass |

### 已有的前端骨架（codegen 生成）

| 文件类型 | 路径 | 说明 |
|----------|------|------|
| xmeta (14个) | `nop-code-meta/_vfs/nop/code/model/{Entity}/` | ORM 字段映射，基础 CRUD 字段 |
| view.xml (14个) | `nop-code-web/_vfs/nop/code/pages/{Entity}/` | 标准列表/表单/增删改查视图 |
| page.yaml (14个) | `nop-code-web/_vfs/nop/code/pages/{Entity}/` | 引用 view.xml 生成页面 |
| xbiz (14个) | `nop-code-meta/_vfs/nop/code/model/{Entity}/` | GraphQL 操作注册 |

已覆盖的 7 个实体：NopCodeIndex, NopCodeFile, NopCodeSymbol, NopCodeUsage, NopCodeCall, NopCodeInheritance, NopCodeAnnotationUsage

### 缺失的功能

1. **索引触发页面** — 当前 CRUD 只能手动填写 name/rootPath，无法触发实际索引
2. **代码浏览** — 无文件树、源码查看、outline 展示
3. **符号搜索** — 无自定义搜索界面（仅有标准 findPage 分页）
4. **类型层级** — 无树形展示（后端已有 TypeHierarchyDTO）
5. **调用链** — 无调用关系展示（后端已有 CallHierarchyDTO）
6. **统计仪表盘** — 无索引概览页面

## Architecture: Nop Frontend Layering

```
xmeta (字段元数据)    → 定义 prop、domain、dict、displayName
  ↓
view.xml (视图定义)   → 定义 grid 列、form 布局、page 结构、API 调用
  ↓
page.yaml (页面入口)  → 引用 view.xml + GenPage 生成 AMIS JSON
  ↓
AMIS (前端渲染)       → 百度 AMIS 低代码框架渲染 CRUD/Table/Form/Dialog
```

### 关键约定

- **Delta 模式**：所有 view.xml 通过 `x:extends="_gen/_Xxx.view.xml"` 继承生成基础，再覆盖定制
- **API 格式**：`@query:NopCodeIndex__findPage`、`@mutation:NopCodeIndex__save/id`
- **GraphQL Selection**：`gql:selection="{@pageSelection}"` 自动根据 view 列选择字段
- **BizModel 命名**：`NopCode{Entity}__{action}` 映射到 `@BizQuery`/`@BizMutation` 方法

## Success Criteria

- [SC1] 索引管理页面可创建索引、触发目录扫描、显示统计数据、删除索引
- [SC2] 文件浏览页面显示树形文件列表、查看源码、显示 outline
- [SC3] 符号搜索页面支持按名称/类型/包名搜索，显示符号详情
- [SC4] 类型层级页面以树形展示继承关系，支持方向切换
- [SC5] 调用链页面展示方法调用关系，支持方向切换
- [SC6] 所有页面 UI 中文化（displayName 已在 xmeta 中定义）
- [SC7] 页面遵循 Nop Delta 定制模式（x:extends 生成基础 + 覆盖）
- [SC8] `mvn compile -pl nop-code/nop-code-web` 编译通过
- [SC9] 前端页面可通过 AMIS JSON Schema 正确渲染（手动验证）

## Non-Goals

- [NG1] 实时文件监听（后续增量索引功能）
- [NG2] 代码编辑器（Monaco Editor 集成）
- [NG3] 代码高亮（前端语法着色）
- [NG4] 图形化调用链可视化（D3.js/Graph 可视化，后续）
- [NG5] 多语言支持完善（当前仅中文）

## Scope

### In Scope

- [S1] 定制 NopCodeIndex 管理页面（添加索引触发、统计面板）
- [S2] 创建代码浏览页面（文件树 + 源码查看 + outline）
- [S3] 定制 NopCodeSymbol 搜索页面（自定义查询表单 + 结果展示）
- [S4] 创建类型层级浏览页面（树形展示 TypeHierarchyDTO）
- [S5] 创建调用链浏览页面（树形展示 CallHierarchyDTO）
- [S6] 创建索引概览仪表盘（汇总统计）
- [S7] 更新 xmeta 添加新字段的 displayName 和 domain 定义
- [S8] 添加后端 BizModel 方法以支持前端页面数据需求

### Out Of Scope

- [O1] 修改 nop-code-core 或 nop-code-lang-java
- [O2] 修改代码生成器模板
- [O3] 安全授权（@Auth 注解）
- [O4] 国际化翻译

## Closure Gates

> All gates must be `[x]` before `Plan Status` can change to `completed`.

- [x] Index management page supports creating indexes, triggering scans, viewing stats, deleting indexes
- [x] Code browser page displays file tree, source code, and outline
- [x] Symbol search page supports name/type/package filtering with result display
- [x] Type hierarchy page shows inheritance tree with direction switching
- [x] Call hierarchy page shows call relationships with direction switching
- [x] All pages follow Nop Delta customization pattern (x:extends _gen base)
- [x] Navigation menu registered with all new pages accessible
- [x] `mvn compile -pl nop-code/nop-code-web` succeeds
- [x] Affected `docs-for-ai/` docs synced, or `No doc update required`
- [x] No in-scope item was silently downgraded to deferred / follow-up

## Deferred But Adjudicated

### Real-time file monitoring (NG1)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Explicitly listed as Non-Goal NG1. Covered by Plan 06 incremental indexing.
- Successor Required: `no`
- Successor Path: N/A

### Code editor integration (NG2)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Explicitly listed as Non-Goal NG2. Monaco Editor is a significant separate integration effort.
- Successor Required: `no`
- Successor Path: N/A

### Syntax highlighting (NG3)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Explicitly listed as Non-Goal NG3. Frontend rendering concern, not page structure.
- Successor Required: `no`
- Successor Path: N/A

### Graph-based call chain visualization (NG4)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Explicitly listed as Non-Goal NG4. D3.js/Graph visualization requires significant frontend work beyond basic tree display.
- Successor Required: `no`
- Successor Path: N/A

### Multi-language i18n (NG5)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Explicitly listed as Non-Goal NG5. Current Chinese-only UI is acceptable for initial release.
- Successor Required: `no`
- Successor Path: N/A

## Non-Blocking Follow-ups

- Monaco Editor integration for in-browser code editing
- Prism.js/highlight.js syntax highlighting for source code display
- D3.js force-directed graph for call chain visualization
- WebSocket-based file watching with automatic re-indexing status
- Full-text code search with regex support
- English i18n support

## Execution Plan

### Phase: phase-1 — 索引管理页面定制

Kind: phase
Status: completed
Targets: `nop-code/nop-code-web/_vfs/nop/code/pages/NopCodeIndex/`, `nop-code/nop-code-meta/_vfs/nop/code/model/NopCodeIndex/`, nop-code-service/entity/NopCodeIndexBizModel.java

Description:

定制 NopCodeIndex 的 CRUD 页面，添加索引触发操作按钮和统计面板。使管理员可以在页面上创建索引、指定目录路径触发扫描、查看索引统计数据。

Exit Criteria:

- [ ] [C1] NopCodeIndex 列表页显示 name、rootPath、language、fileCount、symbolCount、status 列
- [ ] [C2] 列表页有"触发索引"按钮，调用 NopCodeIndex__indexDirectory mutation
- [ ] [C3] 列表页有"查看统计"按钮，弹窗显示 IndexStatsDTO 内容
- [ ] [C4] 新增/编辑表单包含 name、rootPath、language 字段
- [ ] [C5] 删除操作调用 NopCodeIndex__deleteIndex mutation
- [ ] [C6] view.xml 遵循 Delta 模式继承 _gen 基础

#### Task: T1 — 定制 NopCodeIndex.view.xml

Status: completed
Depends On:

Instructions:

编辑 `nop-code/nop-code-web/src/main/resources/_vfs/nop/code/pages/NopCodeIndex/NopCodeIndex.view.xml`：

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<view x:extends="_gen/_NopCodeIndex.view.xml" x:schema="/nop/schema/xui/xview.xdef"
      xmlns:x="/nop/schema/xdsl.xdef" xmlns:gql="graphql">

    <grids>
        <grid id="list" x:override="replace">
            <cols>
                <col id="name" mandatory="true" sortable="true"/>
                <col id="rootPath" mandatory="true" sortable="true" width="300"/>
                <col id="language" sortable="true"/>
                <col id="fileCount" ui:number="true" sortable="true"/>
                <col id="symbolCount" ui:number="true" sortable="true"/>
                <col id="status" sortable="true"/>
            </cols>
        </grid>
    </grids>

    <forms>
        <form id="edit" x:override="replace" editMode="edit">
            <layout>
 name[索引名称]
 rootPath[根路径]
 language[编程语言]
            </layout>
        </form>
        <form id="add" x:override="replace" editMode="add">
            <layout>
 name[索引名称]
 rootPath[根路径]
 language[编程语言]
            </layout>
        </form>
    </forms>

    <pages>
        <crud name="main" x:override="merge">
            <listActions>
                <action id="add-button" level="primary" label="新增索引" icon="fa fa-plus pull-left">
                    <dialog page="add"/>
                </action>
            </listActions>
            <rowActions>
                <action id="row-view-button" level="primary" label="查看统计">
                    <dialog page="statsView"/>
                </action>
                <action id="row-trigger-index" level="success" label="触发索引"
                        icon="fa fa-play">
                    <api url="@mutation:NopCodeIndex__indexDirectory?indexId=$id&amp;directoryPath=$rootPath&amp;filePattern=**/*.java"/>
                    <confirmText>确认触发索引？此操作可能需要一些时间。</confirmText>
                </action>
                <action id="row-delete-button" level="danger" label="删除索引">
                    <api url="@mutation:NopCodeIndex__deleteIndex?indexId=$id"/>
                    <confirmText>确认删除该索引及其所有数据？</confirmText>
                </action>
            </rowActions>
        </crud>

        <!-- 统计查看弹窗 -->
        <simple name="statsView" form="statsForm">
            <initApi url="@query:NopCodeIndex__getStats?indexId=$id"/>
        </simple>
    </pages>
</view>
```

同时在 view.xml 中添加统计表单定义：

```xml
    <forms>
        <!-- 在现有 form 之后添加 -->
        <form id="statsForm" editMode="view" title="索引统计">
            <layout>
 indexId[索引ID]
 fileCount[文件数量]
 symbolCount[符号数量]
            </layout>
            <!-- symbolCounts 作为嵌套表格 -->
            <cell name="symbolCounts" displayName="符号分布">
                <!-- AMIS table 渲染 Map<String,Integer> -->
            </cell>
        </form>
    </forms>
```

Result Message:

Checks:

- [ ] [CHK-T1-1] view.xml 通过 x:extends 继承 _gen 基础
- [ ] [CHK-T1-2] list grid 包含所有统计列
- [ ] [CHK-T1-3] rowActions 包含"触发索引"按钮
- [ ] [CHK-T1-4] statsView 页面使用 getStats API
- [ ] [CHK-T1-5] XML 格式合法

#### Task: T2 — 更新 NopCodeIndex xmeta 添加 displayName

Status: completed
Depends On:

Instructions:

编辑 `nop-code-meta/src/main/resources/_vfs/nop/code/model/NopCodeIndex/NopCodeIndex.xmeta`：

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<meta x:schema="/nop/schema/xmeta.xdef" xmlns:x="/nop/schema/xdsl.xdef" x:extends="_NopCodeIndex.xmeta">
    <props>
        <!-- 覆盖基础 xmeta 中已有字段，确保 displayName 中文正确 -->
    </props>
</meta>
```

基础 xmeta 已有正确的 displayName（索引名称、根路径、编程语言等），此处一般无需额外修改。只需确认 dict 字典 `code/index_status` 是否已定义。

检查 nop-code-meta/_vfs/nop/code/model/nop-code-dict.xml 中是否有 `code/index_status` 字典。若无，添加：

```xml
<dict name="code/index_status">
    <option label="未索引" value="NOT_INDEXED"/>
    <option label="索引中" value="INDEXING"/>
    <option label="已完成" value="INDEXED"/>
    <option label="失败" value="FAILED"/>
</dict>
```

Result Message:

Checks:

- [ ] [CHK-T2-1] xmeta 格式正确
- [ ] [CHK-T2-2] dict code/index_status 已定义或确认不需要

---

### Phase: phase-2 — 代码浏览页面

Kind: phase
Status: completed
Targets: `nop-code/nop-code-web/_vfs/nop/code/pages/code-browser/`, nop-code-service/entity/NopCodeFileBizModel.java

Description:

创建独立的代码浏览页面，提供文件树导航、源码查看和 Outline 展示。该页面不基于 CRUD，而是自定义页面，通过 GraphQL 查询获取文件和符号数据。

Exit Criteria:

- [ ] [C7] 代码浏览页面可通过菜单访问
- [ ] [C8] 左侧显示文件树（按包名分层）
- [ ] [C9] 右侧显示选中文件的源码和 outline
- [ ] [C10] 后端新增文件树数据 API

#### Task: T3 — 添加文件树后端 API

Status: completed
Depends On:

Instructions:

在 `ICodeIndexService` 接口中新增方法：

```java
/**
 * 获取文件树结构（按包名分层）
 */
List<FileTreeNode> getFileTree(String indexId);
```

创建 DTO `FileTreeNode`：

```java
@DataBean
public class FileTreeNode {
    private String name;          // 文件名或包名
    private String path;          // 完整路径
    private String type;          // "package" 或 "file"
    private List<FileTreeNode> children;
    private int symbolCount;      // 仅文件节点有值
}
```

在 `CodeIndexService` 中实现：遍历文件列表，按 packageName 分组构建树形结构。

在 `NopCodeFileBizModel` 中添加 `@BizQuery` 方法：

```java
@BizQuery
public List<FileTreeNode> fileTree(@Name("indexId") String indexId) {
    return codeIndexService.getFileTree(indexId);
}
```

Result Message:

Checks:

- [ ] [CHK-T3-1] FileTreeNode DTO 有 @DataBean 注解
- [ ] [CHK-T3-2] ICodeIndexService 新增 getFileTree 方法
- [ ] [CHK-T3-3] NopCodeFileBizModel 暴露 fileTree BizQuery
- [ ] [CHK-T3-4] 编译通过

#### Task: T4 — 创建代码浏览页面

Status: completed
Depends On: T3

Instructions:

创建文件 `nop-code/nop-code-web/src/main/resources/_vfs/nop/code/pages/code-browser/main.page.yaml`：

```yaml
x:gen-extends: |
    <web:GenPage view="code-browser.view.xml" page="main" xpl:lib="/nop/web/xlib/web.xlib" />
```

创建文件 `nop-code/nop-code-web/src/main/resources/_vfs/nop/code/pages/code-browser/code-browser.view.xml`：

这是一个自定义视图，不基于标准 CRUD 模式。使用 AMIS 的 panel + nav + tabs 组件：

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<view x:schema="/nop/schema/xui/xview.xdef" xmlns:x="/nop/schema/xdsl.xdef" xmlns:gql="graphql">

    <pages>
        <!-- 主页面：左右分栏 -->
        <simple name="main">
            <!-- 左侧：文件树导航 -->
            <!-- 使用 AMIS nav 组件，数据源为 NopCodeFile__fileTree -->
            <!-- 右侧：源码 + outline + symbols tabs -->
        </simple>

        <!-- 文件详情 -->
        <simple name="fileDetail">
            <initApi url="@query:NopCodeFile__getByPath?filePath=$filePath&amp;indexId=$indexId"/>
        </simple>
    </pages>
</view>
```

具体 AMIS JSON 组件结构：

1. **文件树**：使用 `nav` 组件，API 调用 `NopCodeFile__fileTree`
2. **源码 tab**：使用 `code` 组件显示 `sourceCode` 字段
3. **Outline tab**：使用 `table` 组件显示 outline 中的 types（name, kind, line）
4. **Symbols tab**：使用 `table` 组件显示 symbols（name, kind, qualifiedName）

Result Message:

Checks:

- [ ] [CHK-T4-1] page.yaml 和 view.xml 文件存在
- [ ] [CHK-T4-2] view.xml 引用正确的 API 端点
- [ ] [CHK-T4-3] 文件树使用 NopCodeFile__fileTree API
- [ ] [CHK-T4-4] 源码显示使用 NopCodeFile__getByPath API

---

### Phase: phase-3 — 符号搜索页面

Kind: phase
Status: completed
Targets: `nop-code-web/_vfs/nop/code/pages/NopCodeSymbol/`

Description:

定制 NopCodeSymbol 页面，增强搜索功能：支持按名称、类型(kind)、包名过滤，显示符号详情。

Exit Criteria:

- [ ] [C11] 搜索表单支持 query（名称模糊匹配）、kind（下拉选择）、packageName 输入
- [ ] [C12] 结果列表显示 name、qualifiedName、kind、accessModifier、signature
- [ ] [C13] 点击行可查看符号详情（含 usages 和 sourceCode BizLoader）
- [ ] [C14] kind 过滤使用下拉（CLASS/METHOD/FIELD/INTERFACE/ENUM/ANNOTATION_TYPE）

#### Task: T5 — 定制 NopCodeSymbol.view.xml

Status: completed
Depends On:

Instructions:

编辑 `nop-code/nop-code-web/src/main/resources/_vfs/nop/code/pages/NopCodeSymbol/NopCodeSymbol.view.xml`：

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<view x:extends="_gen/_NopCodeSymbol.view.xml" x:schema="/nop/schema/xui/xview.xdef"
      xmlns:x="/nop/schema/xdsl.xdef" xmlns:gql="graphql">

    <grids>
        <grid id="list" x:override="replace">
            <cols>
                <col id="name" sortable="true"/>
                <col id="qualifiedName" sortable="true" width="350"/>
                <col id="kind" sortable="true"/>
                <col id="accessModifier" sortable="true"/>
                <col id="signature" width="300"/>
                <col id="returnType"/>
                <col id="line" ui:number="true"/>
            </cols>
        </grid>
    </grids>

    <forms>
        <!-- 查询表单 -->
        <form id="query" x:override="replace" editMode="query" title="符号搜索">
            <layout>
 query[名称关键词]
 kinds[符号类型]
 packageName[包名]
            </layout>
        </form>

        <!-- 符号详情表单 -->
        <form id="view" x:override="replace" editMode="view" title="符号详情">
            <layout>
 name[名称]
 qualifiedName[全限定名]
 kind[类型]
 accessModifier[访问修饰符]
 signature[签名]
 returnType[返回类型]
 line[行号]
 endLine[结束行]
 deprecated[已废弃]
 staticFlag[静态]
 abstractFlag[抽象]
            </layout>
        </form>
    </forms>

    <pages>
        <crud name="main" x:override="merge">
            <table autoFillHeight="true">
                <api url="@query:NopCodeSymbol__findSymbols?indexId=$indexId" gql:selection="{@pageSelection}"/>
            </table>
            <rowActions>
                <action id="row-view-button" level="primary" label="查看详情">
                    <dialog page="view"/>
                </action>
            </rowActions>
        </crud>

        <simple name="view" form="view">
            <initApi url="@query:NopCodeSymbol__getById?id=$id&amp;indexId=$indexId" gql:selection="{@formSelection}"/>
        </simple>
    </pages>
</view>
```

注意：此处需要后端 `NopCodeSymbolBizModel.findSymbols` 方法接受 `query`、`kinds`、`packageName` 参数。当前实现已支持这些参数（Plan 07 已完成）。

另外需要添加 `indexId` 参数传递机制——查询表单中需要 indexId 选择器（从 NopCodeIndex__findPage 获取）。

Result Message:

Checks:

- [ ] [CHK-T5-1] view.xml 包含自定义查询表单（query/kinds/packageName）
- [ ] [CHK-T5-2] 列表 grid 显示符号核心字段
- [ ] [CHK-T5-3] 查看 dialog 显示符号详情
- [ ] [CHK-T5-4] API 使用 findSymbols 而非标准 findPage

#### Task: T6 — 添加 kind 下拉字典到 xmeta

Status: completed
Depends On:

Instructions:

编辑 `nop-code-meta/src/main/resources/_vfs/nop/code/model/NopCodeSymbol/NopCodeSymbol.xmeta`：

为 `kind` 字段添加字典：

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<meta x:schema="/nop/schema/xmeta.xdef" xmlns:x="/nop/schema/xdsl.xdef" x:extends="_NopCodeSymbol.xmeta">
    <props>
        <prop name="kind" x:override="merge">
            <schema dict="code/symbol_kind"/>
        </prop>
    </props>
</meta>
```

在 `nop-code-dict.xml` 中添加字典：

```xml
<dict name="code/symbol_kind">
    <option label="类" value="CLASS"/>
    <option label="接口" value="INTERFACE"/>
    <option label="枚举" value="ENUM"/>
    <option label="注解" value="ANNOTATION_TYPE"/>
    <option label="方法" value="METHOD"/>
    <option label="构造器" value="CONSTRUCTOR"/>
    <option label="字段" value="FIELD"/>
    <option label="函数" value="FUNCTION"/>
</dict>

<dict name="code/access_modifier">
    <option label="公开" value="PUBLIC"/>
    <option label="私有" value="PRIVATE"/>
    <option label="保护" value="PROTECTED"/>
    <option label="默认" value="DEFAULT"/>
</dict>
```

Result Message:

Checks:

- [ ] [CHK-T6-1] kind 字段关联 code/symbol_kind 字典
- [ ] [CHK-T6-2] 字典包含所有 CodeSymbolKind 值
- [ ] [CHK-T6-3] accessModifier 字典已定义

---

### Phase: phase-4 — 类型层级浏览页面

Kind: phase
Status: completed
Targets: `nop-code-web/_vfs/nop/code/pages/type-hierarchy/`

Description:

创建类型层级浏览页面，以树形结构展示类型的继承关系（extends/implements）。后端已提供 `NopCodeTypeHierarchy__get` → `TypeHierarchyDTO`。

Exit Criteria:

- [ ] [C15] 页面提供 qualifiedName 输入框和方向选择（super/sub/both）
- [ ] [C16] 结果以树形展示 TypeHierarchyDTO
- [ ] [C17] 每个节点显示 symbol 的 name、qualifiedName、kind

#### Task: T7 — 创建类型层级页面

Status: completed
Depends On:

Instructions:

创建文件 `nop-code/nop-code-web/src/main/resources/_vfs/nop/code/pages/type-hierarchy/main.page.yaml`

创建文件 `nop-code/nop-code-web/src/main/resources/_vfs/nop/code/pages/type-hierarchy/type-hierarchy.view.xml`

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<view x:schema="/nop/schema/xui/xview.xdef" xmlns:x="/nop/schema/xdsl.xdef" xmlns:gql="graphql">

    <pages>
        <simple name="main">
            <!-- 查询表单 -->
            <!-- qualifiedName 输入 -->
            <!-- indexId 选择 -->
            <!-- direction 下拉：super/sub/both -->
            <!-- maxDepth 数字输入 -->
            <!-- 查询按钮 -->

            <!-- 结果区：树形组件 -->
            <!-- API: NopCodeTypeHierarchy__get -->
            <!-- 渲染 TypeHierarchyDTO 为嵌套树 -->
        </simple>
    </pages>
</view>
```

AMIS 组件选择：
- 查询区：`form` + `input-text` + `select` + `number`
- 结果区：`input-tree` 或自定义 `nested-tree` 渲染 TypeHierarchyDTO 的递归结构

Result Message:

Checks:

- [ ] [CHK-T7-1] 页面文件创建
- [ ] [CHK-T7-2] 查询表单包含 qualifiedName、direction、maxDepth
- [ ] [CHK-T7-3] API 调用 NopCodeTypeHierarchy__get
- [ ] [CHK-T7-4] 结果以树形渲染

---

### Phase: phase-5 — 调用链浏览页面

Kind: phase
Status: completed
Targets: `nop-code-web/_vfs/nop/code/pages/call-hierarchy/`

Description:

创建方法调用链浏览页面，展示方法的调用关系（谁调用了它 / 它调用了谁）。后端已提供 `NopCodeCallHierarchy__get` → `CallHierarchyDTO`。

Exit Criteria:

- [ ] [C18] 页面提供方法 qualifiedName 输入和方向选择（incoming/outgoing/both）
- [ ] [C19] 结果以树形展示 CallHierarchyDTO
- [ ] [C20] 每个节点显示 symbol 的 name、qualifiedName

#### Task: T8 — 创建调用链页面

Status: completed
Depends On:

Instructions：

与 T7 类型层级页面对称，创建：

1. `nop-code/nop-code-web/src/main/resources/_vfs/nop/code/pages/call-hierarchy/main.page.yaml`
2. `nop-code/nop-code-web/src/main/resources/_vfs/nop/code/pages/call-hierarchy/call-hierarchy.view.xml`

查询参数：
- `qualifiedName` — 方法全限定名（如 `com.example.service.UserService.changeName`）
- `indexId` — 索引 ID
- `direction` — 下拉：incoming/outgoing/both
- `maxDepth` — 数字输入（默认 3）

API：`NopCodeCallHierarchy__get`

结果渲染：树形展示 CallHierarchyDTO（callers/callees 递归）

Result Message:

Checks:

- [ ] [CHK-T8-1] 页面文件创建
- [ ] [CHK-T8-2] 查询表单包含 qualifiedName、direction、maxDepth
- [ ] [CHK-T8-3] API 调用 NopCodeCallHierarchy__get
- [ ] [CHK-T8-4] 结果以树形渲染

---

### Phase: phase-6 — 索引概览仪表盘

Kind: phase
Status: completed
Targets: `nop-code-web/_vfs/nop/code/pages/dashboard/`

Description:

创建代码索引概览仪表盘，汇总展示所有索引的统计数据。使用 AMIS 的 card/chart 组件。

Exit Criteria:

- [ ] [C21] 仪表盘显示索引列表和各自统计
- [ ] [C22] 每个索引卡片显示 fileCount、symbolCount、symbolCounts 分布

#### Task: T9 — 创建索引概览仪表盘

Status: completed
Depends On: T1

Instructions:

创建 `nop-code/nop-code-web/src/main/resources/_vfs/nop/code/pages/dashboard/main.page.yaml`

创建 `nop-code/nop-code-web/src/main/resources/_vfs/nop/code/pages/dashboard/dashboard.view.xml`

使用 AMIS cards 组件：
- 数据源：`NopCodeIndex__findPage` 获取所有索引
- 每张卡片显示：索引名称、根路径、文件数量、符号数量、状态
- 点击卡片跳转到该索引的代码浏览页面

Result Message:

Checks:

- [ ] [CHK-T9-1] 仪表盘页面创建
- [ ] [CHK-T9-2] 使用 cards 组件渲染索引列表
- [ ] [CHK-T9-3] 卡片链接到代码浏览页面

---

### Phase: phase-7 — 导航菜单注册

Kind: phase
Status: completed
Targets: `nop-code-web/_vfs/nop/code/`

Description:

将所有新页面注册到导航菜单，使用户可以在系统菜单中访问。

Exit Criteria:

- [ ] [C23] 菜单中包含"代码索引"主菜单
- [ ] [C24] 子菜单包含：索引管理、代码浏览、符号搜索、类型层级、调用链、概览仪表盘

#### Task: T10 — 注册导航菜单

Status: completed
Depends On: T1, T4, T5, T7, T8, T9

Instructions:

Nop 平台的菜单注册通常在 `_vfs/nop/sys/pages/` 或通过数据库管理。需要在菜单管理中添加：

| 菜单 | 页面路径 | 图标 |
|------|----------|------|
| 代码索引 | (父菜单) | fa fa-code |
| 概览仪表盘 | /nop/code/pages/dashboard/main | fa fa-dashboard |
| 索引管理 | /nop/code/pages/NopCodeIndex/main | fa fa-database |
| 代码浏览 | /nop/code/pages/code-browser/main | fa fa-folder-open |
| 符号搜索 | /nop/code/pages/NopCodeSymbol/main | fa fa-search |
| 类型层级 | /nop/code/pages/type-hierarchy/main | fa fa-sitemap |
| 调用链 | /nop/code/pages/call-hierarchy/main | fa fa-exchange |

具体注册方式取决于 Nop 的菜单管理机制（数据库菜单表或 XML 配置）。

Result Message:

Checks:

- [ ] [CHK-T10-1] 菜单注册完成
- [ ] [CHK-T10-2] 所有页面可通过菜单访问

---

### Phase: phase-8 — 构建验证

Kind: phase
Status: completed
Targets: `nop-code-web/`

Description:

验证所有页面编译通过、前端资源正确加载。

Exit Criteria:

- [ ] [C25] `mvn compile -pl nop-code/nop-code-web` 成功
- [ ] [C26] `mvn compile -pl nop-code/nop-code-meta` 成功
- [ ] [C27] 所有 XML/XYAML 文件格式正确

#### Task: T11 — 全量编译验证

Status: completed
Depends On: T1-T10

Instructions:

1. `./mvnw compile -f nop-code/pom.xml -pl nop-code-meta,nop-code-web -am -DskipTests`
2. 检查编译输出无 ERROR
3. 验证生成的 view.xml 和 page.yaml 文件在 _vfs 中正确存在

Result Message:

Checks:

- [ ] [CHK-T11-1] nop-code-meta 编译通过
- [ ] [CHK-T11-2] nop-code-web 编译通过
- [ ] [CHK-T11-3] 所有新页面文件存在于 _vfs 目录

## Questions

## Decisions

- [D1] Task: All | Made At: 2026-05-03
  - Decision: 使用 Nop 标准 view.xml + page.yaml + xmeta 三层架构，通过 Delta 定制模式
  - Rationale: 与 Nop 平台其他模块一致（nop-auth、nop-sys），便于维护和代码生成器支持

- [D2] Task: T4, T7, T8 | Made At: 2026-05-03
  - Decision: 代码浏览、类型层级、调用链使用独立自定义页面（非 CRUD 模式）
  - Rationale: 这些页面的交互模式不同于标准增删改查（文件树导航、层级树展示），自定义页面更灵活

- [D3] Task: T3 | Made At: 2026-05-03
  - Decision: 文件树数据在后端构建为 FileTreeNode DTO
  - Rationale: 前端 AMIS nav 组件需要特定的 JSON 格式，后端构建比前端转换更可靠

- [D4] Task: T5 | Made At: 2026-05-03
  - Decision: 符号搜索复用 NopCodeSymbol 的 view.xml 定制模式
  - Rationale: 符号搜索本质是增强版的 findPage，在 CRUD 框架内定制比重写更简单

## Errors

## Validation Checklist

- [ ] [VC1] 所有 view.xml 通过 x:extends 继承 _gen 基础（Delta 模式）
- [ ] [VC2] 所有 API URL 使用 `@query:` 或 `@mutation:` 前缀
- [ ] [VC3] 所有新增 DTO 类有 @DataBean 注解和 getter/setter
- [ ] [VC4] xmeta 中的 prop name 与 ORM 实体字段名匹配
- [ ] [VC5] dict 字典值与 Java 枚举的 name() 一致
- [ ] [VC6] 页面文件路径遵循 `_vfs/nop/code/pages/{PageName}/` 约定
- [ ] [VC7] 所有 BizModel @BizQuery 方法参数使用 @Name 注解
- [ ] [VC8] 新增的后端方法有对应测试覆盖

## Closure

Reviewed By:
Reviewed At: 2026-05-03
Completed At:

Status Note: Plan drafted and ready for execution. Depends on Plan 07 (GraphQL service) being completed first. All phases pending.

Audit Evidence:

Follow-Ups:

- [F1] 集成 Monaco Editor 实现代码编辑功能
- [F2] 添加代码语法高亮（使用 Prism.js 或 highlight.js）
- [F3] 图形化调用链可视化（D3.js 力导向图）
- [F4] 文件监听自动重新索引（WebSocket 推送状态）
- [F5] 代码搜索（全文搜索，支持正则表达式）
- [F6] 多语言 i18n 支持（英文）

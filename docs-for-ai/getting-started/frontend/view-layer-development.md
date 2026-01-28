# 界面层开发指南

## 概述

Nop平台界面层采用XView视图模型，它是与前台框架无关、面向业务领域的前端界面描述。XView通过页面(page)、表格(grid)、表单(form)、操作(action)等少量概念来表达前台的核心交互逻辑，最终通过`x:gen-extends`元编程机制生成AMIS页面。

XView模型将前端界面的构造分解为字段级、表单/表格级、页面级，实现了高度的可配置性和可扩展性。

## 核心概念

### 1. XView模型

**定义**：XView是Nop平台的前端界面描述模型，采用XML格式，与具体的前端框架无关
**位置**：通常位于模块的`_vfs`目录下，如`/nop/auth/model/NopAuthUser/NopAuthUser.view.xml`
**作用**：
- 定义页面结构和组件
- 配置数据来源和交互逻辑
- 通过元编程机制生成最终的AMIS页面

**核心结构**：
- `objMeta`：关联的XMeta模型
- `controlLib`：控制字段类型到显示控件的映射
- `grids`：表格配置
- `forms`：表单配置
- `pages`：页面配置

**示例**：
```xml
<view>
    <objMeta>/nop/auth/model/NopAuthUser/NopAuthUser.xmeta</objMeta>
    <controlLib>/nop/web/xlib/control.xlib</controlLib>

    <grids>
        <grid id="list" x:abstract="true">
            <cols>
                <col id="userName" mandatory="true" sortable="true"/>
                <col id="nickName"/>
                <col id="email"/>
                <col id="status"/>
            </cols>
        </grid>
    </grids>

    <forms>
        <form id="edit" editMode="update" title="编辑-用户">
            <layout>
                userName[用户名] nickName[昵称]
                email[邮箱] status[状态]
            </layout>
        </form>
    </forms>

    <pages>
        <crud name="main" grid="list" filterForm="query">
            <!-- 页面配置 -->
        </crud>
    </pages>
</view>
```

### 2. 表格(grid)配置

**定义**：描述表格的结构和行为
**核心配置**：
- `cols`：列配置，定义表格显示哪些字段
- `table`：表格组件配置
- `api`：数据API

**示例**：
```xml
<grid id="list">
    <cols x:override="bounded-merge">
        <col id="userName" width="100px" align="right" label="用户名"/>
        <col id="status"/>
    </cols>
</grid>
```

### 3. 表单(form)配置

**定义**：描述表单的结构和行为
**核心配置**：
- `layout`：表单布局，使用特殊的布局DSL
- `cells`：表单字段配置
- `editMode`：编辑模式（add, update, view, query）

**示例**：
```xml
<form id="query" editMode="query" title="查询条件">
    <layout>
        userName status
    </layout>
</form>
```

### 4. 页面(page)配置

**定义**：描述完整页面的结构和行为
**类型**：
- `crud`：增删改查页面
- `simple`：简单页面
- `picker`：选择器页面
- `tabs`：标签页页面

**示例**：
```xml
<pages>
    <crud name="main" grid="list" filterForm="query">
        <table noOperations="true" />
        <rowActions>
            <action id="row-update-button" actionType="drawer"/>
            <action id="row-delete-button"/>
        </rowActions>
    </crud>
    <simple name="view" form="view">
        <api url="@query:NopAuthUser__get/{@formSelection}?id=$id"/>
    </simple>
</pages>
```

## 开发流程

### 1. 生成XView模型

通常通过代码生成器根据XMeta模型生成XView模型，例如在`xxx-web`模块的`precompile`目录下：

```xml
<c:script>
// 根据xmeta生成页面文件view.xml和page.yaml
codeGenerator.withTplDir('/nop/templates/orm-web').execute("/",{ moduleId: "nop/auth" },$scope);
</c:script>
```

### 2. 定制XView模型

根据业务需求修改生成的XView模型，包括：
- 调整表格列
- 修改表单布局
- 添加或修改页面
- 配置交互逻辑

### 3. 生成AMIS页面

通过`x:gen-extends`元编程机制生成最终的AMIS页面，例如在`page.yaml`中：

```yaml
x:gen-extends: |
  <web:GenPage view="NopAuthUser.view.xml" page="main" xpl:lib="/nop/web/xlib/web.xlib" />
```

### 4. 部署和访问

部署后通过浏览器访问页面（路由前缀/端口以实际部署为准），例如：`http://localhost:8080/#/nop/auth/NopAuthUser/main`

## 核心配置示例

### 1. 基本的增删改查页面

```xml
<view>
    <objMeta>/nop/auth/model/NopAuthUser/NopAuthUser.xmeta</objMeta>
    <controlLib>/nop/web/xlib/control.xlib</controlLib>

    <grids>
        <grid id="list">
            <cols>
                <col id="userName" mandatory="true" sortable="true"/>
                <col id="nickName"/>
                <col id="email"/>
                <col id="status"/>
                <col id="createTime" sortable="true"/>
            </cols>
        </grid>
    </grids>

    <forms>
        <form id="query" editMode="query" title="查询条件">
            <layout>
                userName status
            </layout>
        </form>
        <form id="edit" editMode="update" title="编辑-用户">
            <layout>
                userName[用户名] nickName[昵称]
                email[邮箱] status[状态]
            </layout>
        </form>
        <form id="add" editMode="add" title="新增-用户" x:prototype="edit"/>
        <form id="view" editMode="view" title="查看-用户" x:prototype="edit"/>
    </forms>

    <pages>
        <crud name="main" grid="list" filterForm="query"/>
        <simple name="add" form="add">
            <api url="@mutation:NopAuthUser__save/id"/>
        </simple>
        <simple name="view" form="view">
            <api url="@query:NopAuthUser__get/{@formSelection}?id=$id"/>
        </simple>
        <simple name="update" form="edit">
            <initApi url="@query:NopAuthUser__get/{@formSelection}?id=$id"/>
            <api url="@mutation:NopAuthUser__update/id?id=$id" withFormData="true"/>
        </simple>
    </pages>
</view>
```

### 2. 左侧树形过滤条件

```xml
<form id="asideFilter" submitOnChange="true">
    <layout>
        ==dept[部门]==
        !deptId
    </layout>
    <cells>
        <cell id="deptId">
            <gen-control>
                <input-tree
                        source="@query:NopAuthDept__findList/value:id,label:deptName,children @TreeChildren(max:5)?filter_parentId=__null"/>
            </gen-control>
        </cell>
    </cells>
</form>

<pages>
    <crud name="main" grid="list" asideFilterForm="asideFilter" filterForm="query"/>
</pages>
```

### 3. 定制列表行操作

```xml
<crud name="main" grid="list">
    <rowActions x:override="bounded-merge">
        <!-- 使用drawer而不是对话框来显示编辑表单 -->
        <action id="row-update-button" actionType="drawer"/>
        <action id="row-delete-button"/>
    </rowActions>
</crud>
```

### 4. 表单数据过多，采用tab页显示

```xml
<form id="view" layoutControl="tabs">
    <layout>
        // 基本信息
        userName[用户名] nickName[昵称]
        email[邮箱] status[状态]
        
        // 详细信息
        address[地址] phone[电话]
        birthday[生日]
    </layout>
</form>
```

## 配置功能参考

### 1. 表格配置项

| 配置项 | 说明 |
|--------|------|
| `cols` | 列配置，定义表格显示哪些字段 |
| `table` | 表格组件配置，如`noOperations="true"`隐藏操作列 |
| `listActions` | 列表顶部操作按钮 |
| `rowActions` | 行操作按钮 |
| `api` | 数据API |

### 2. 表单配置项

| 配置项 | 说明 |
|--------|------|
| `editMode` | 编辑模式：add, update, view, query |
| `layout` | 表单布局，使用布局DSL |
| `cells` | 表单字段配置 |
| `submitOnChange` | 变更时自动提交（适用于查询表单） |
| `layoutControl` | 布局控件，如`tabs`表示使用标签页布局 |

### 3. 页面配置项

| 类型 | 说明 | 配置项 |
|------|------|--------|
| `crud` | 增删改查页面 | `grid`, `filterForm`, `asideFilterForm` |
| `simple` | 简单页面 | `form`, `api`, `initApi` |
| `picker` | 选择器页面 | `grid`, `filterForm` |
| `tabs` | 标签页页面 | `tabs`, `tab` |

### 4. 操作配置项

| 配置项 | 说明 |
|--------|------|
| `actionType` | 操作类型：drawer, dialog, ajax |
| `batch` | 是否为批量操作 |
| `close` | 操作后是否关闭窗口 |
| `reload` | 操作后是否重新加载表格 |
| `confirm` | 确认提示信息 |

## 最佳实践

1. **优先使用代码生成**：通过代码生成器生成基础的XView模型，减少手动编写
2. **合理使用抽象节点**：使用`x:abstract="true"`定义模板节点，通过继承实现复用
3. **布局简洁清晰**：表单布局使用布局DSL，保持简洁易读
4. **复用已有的配置**：通过`x:prototype`继承已有配置，避免重复编写
5. **使用bounded-merge**：在合并时使用`x:override="bounded-merge"`，避免不必要的继承
6. **合理配置API**：使用GraphQL API，减少网络请求
7. **考虑用户体验**：合理配置操作类型（drawer/dialog）和确认提示

## 注意事项

1. **x:abstract属性**：标记为`x:abstract="true"`的节点是模板节点，在派生模型中必须明确声明才会保留
2. **x:prototype继承**：使用`x:prototype`可以从兄弟节点继承配置
3. **表单布局DSL**：表单布局使用特殊的DSL语法（本仓库未提供独立的 `layout.md`，建议结合本指南示例与XDef定义理解）
4. **API配置**：API配置支持GraphQL语法，如`@query:NopAuthUser__findPage/{@pageSelection}`
5. **操作类型选择**：根据表单复杂度选择合适的操作类型（drawer/dialog）
6. **权限控制**：在配置中添加权限检查，确保只有授权用户可以访问

## 如何参考xdef元模型

### 1. 查看xdef定义

xdef文件位于`/nop/schema/xui/`目录下，如：
- `grid.xdef`：表格配置定义
- `form.xdef`：表单配置定义
- `page.xdef`：页面配置定义

### 2. 理解xdef结构

xdef文件定义了模型的语法规则，包括：
- 元素定义
- 属性定义
- 约束规则

### 3. IDE支持

使用Nop IDEA插件可以获得：
- 语法提示和自动补全
- 跳转到xdef定义
- 实时验证和错误提示

## 相关文档

- [前台开发指南](./frontend-development.md) - AMIS框架使用
- [GraphQL服务开发指南](../api/graphql-guide.md) - GraphQL API开发
- [API模型设计](../api/api-model-design.md) - API模型设计指南
- [XDef核心概念](../xlang/xdef-core-concepts.md) - XDef语法与模型

## 总结

Nop平台的XView模型提供了一种强大的方式来描述前端界面，具有以下优势：

- **与框架无关**：XView模型与具体的前端框架无关，便于切换或升级前端框架
- **高度可配置**：通过XML配置实现复杂的页面结构和交互逻辑
- **代码复用**：通过继承和模板机制实现代码复用
- **自动生成**：通过元编程机制自动生成最终的AMIS页面
- **易于维护**：集中管理页面配置，便于修改和维护
- **支持IDE智能提示**：基于xdef元模型，支持IDE的语法提示和验证

通过XView模型，开发者可以快速构建复杂的前端页面，同时保持代码的可维护性和可扩展性。
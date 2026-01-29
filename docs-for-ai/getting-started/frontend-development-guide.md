# 前端开发综合指南

## 概述

Nop平台前端开发基于XView视图模型和AMIS框架，通过JSON配置生成页面，无需编写大量HTML和CSS代码。前端开发采用模型驱动的方式，将界面描述与具体实现分离，实现高度的可配置性和可扩展性。

## 核心架构

### 1. XView视图模型

XView是Nop平台的前端界面描述模型，采用XML格式，与具体的前端框架无关。

**核心特性**：
- **模型驱动**：通过XML模型描述页面结构
- **框架无关**：与具体前端框架解耦
- **业务导向**：面向业务领域的前端界面描述
- **元编程支持**：通过`x:gen-extends`机制生成最终页面

**模型位置**：
- 通常位于模块的`_vfs`目录下
- 例如：`/nop/auth/model/NopAuthUser/NopAuthUser.view.xml`

### 2. AMIS框架

基于百度AMIS框架，提供丰富的UI组件：
- **表单组件**：输入框、选择器、日期选择器等
- **表格组件**：数据表格、分页、排序、筛选等
- **图表组件**：柱状图、折线图、饼图等
- **布局组件**：卡片、面板、标签页等

### 3. 前后端交互

- **GraphQL API**：通过GraphQL进行数据交互
- **自动生成**：根据模型自动生成查询和变更操作
- **权限控制**：内置基于角色的访问控制
- **错误处理**：统一的错误处理机制

## XView模型详解

### 1. 模型结构

```xml
<view>
    <!-- 关联的XMeta模型 -->
    <objMeta>/nop/auth/model/NopAuthUser/NopAuthUser.xmeta</objMeta>
    
    <!-- 控件库映射 -->
    <controlLib>/nop/web/xlib/control.xlib</controlLib>

    <!-- 表格配置 -->
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

    <!-- 表单配置 -->
    <forms>
        <form id="edit" editMode="update" title="编辑-用户">
            <layout>
                userName[用户名] nickName[昵称]
                email[邮箱] status[状态]
            </layout>
        </form>
    </forms>

    <!-- 页面配置 -->
    <pages>
        <crud name="main" grid="list" filterForm="query">
            <!-- 页面配置 -->
        </crud>
    </pages>
</view>
```

### 2. 核心组件

#### 表格(Grid)配置

**功能**：描述表格的结构和行为

```xml
<grid id="list">
    <cols x:override="bounded-merge">
        <col id="userName" width="100px" align="right" label="用户名"/>
        <col id="status"/>
    </cols>
    
    <!-- 表格属性 -->
    <table noOperations="true" />
    
    <!-- 行操作 -->
    <rowActions>
        <action id="row-update-button" actionType="drawer"/>
        <action id="row-delete-button"/>
    </rowActions>
</grid>
```

#### 表单(Form)配置

**功能**：描述表单的结构和行为

```xml
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
```

**编辑模式**：
- `add`：新增模式
- `update`：编辑模式
- `view`：查看模式
- `query`：查询模式

#### 页面(Page)配置

**功能**：描述完整页面的结构和行为

```xml
<pages>
    <!-- CRUD页面 -->
    <crud name="main" grid="list" filterForm="query"/>
    
    <!-- 简单页面 -->
    <simple name="view" form="view">
        <api url="@query:NopAuthUser__get/{@formSelection}?id=$id"/>
    </simple>
    
    <!-- 标签页页面 -->
    <tabs name="detail">
        <tab title="基本信息" form="base"/>
        <tab title="扩展信息" form="ext"/>
    </tabs>
</pages>
```

**页面类型**：
- `crud`：增删改查页面
- `simple`：简单页面
- `picker`：选择器页面
- `tabs`：标签页页面

## 开发流程

### 1. 生成XView模型

通过代码生成器根据XMeta模型生成XView模型：

```xml
<c:script>
// 根据xmeta生成页面文件view.xml和page.yaml
codeGenerator.withTplDir('/nop/templates/orm-web').execute("/",{ moduleId: "nop/auth" },$scope);
</c:script>
```

### 2. 定制XView模型

根据业务需求修改生成的XView模型：

```xml
<!-- 调整表格列 -->
<grid id="list">
    <cols x:override="bounded-merge">
        <col id="userName" width="120px" label="用户名"/>
        <col id="createTime" sortable="true" label="创建时间"/>
    </cols>
</grid>

<!-- 修改表单布局 -->
<form id="edit" editMode="update" title="编辑-用户">
    <layout>
        userName[用户名] nickName[昵称]
        email[邮箱] status[状态]
        department[部门] role[角色]
    </layout>
</form>
```

### 3. 生成AMIS页面

通过元编程机制生成最终的AMIS页面：

```yaml
# page.yaml
x:gen-extends: |
  <web:GenPage view="NopAuthUser.view.xml" page="main" xpl:lib="/nop/web/xlib/web.xlib" />
```

### 4. 部署和访问

部署后通过浏览器访问页面：
- **URL格式**：`/#/nop/auth/NopAuthUser/main`（**开发环境链接**）
- **路由前缀**：根据实际部署配置调整

## 核心功能实现

### 1. 增删改查(CRUD)页面

**完整示例**：

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
            <api url="@mutation:NopAuthUser__update/id"/>
        </simple>
    </pages>
</view>
```

### 2. 表单和表格细化

视图大纲模型(`view.xml`)是与具体实现框架无关的、完全面向业务领域的一种前端DSL，它抽象出了`grid`、`form`、`layout`、`page`、`dialog`、`action`等关键要素，通过简单配置即可描述常见的增删改查逻辑。

**Layout布局语言**：

`layout`是一种专用的布局领域语言，它将布局信息和具体字段的展示控件信息分离开来。具体字段所使用的控件一般由数据类型或者数据域（`domain`）设置来推定，我们只需要补充布局信息即可实现页面展示。

```xml
<form id="edit" size="lg">
    <layout>
        ========== intro[商品介绍] ================
        goodsSn[商品编号] name[商品名称]
        counterPrice[市场价格]
        isNew[是否新品首发] isHot[是否人气推荐]
        isOnSale[是否上架]
        picUrl[商品页面商品图片]
        gallery[商品宣传图片列表，采用JSON数组格式]
        unit[商品单位，例如件、盒]
        keywords[商品关键字，采用逗号间隔]
        categoryId[商品所属类目ID] brandId[Brandid]
        brief[商品简介]
        detail[商品详细介绍，是富文本格式]

        =========specs[商品规格]=======
        !specifications

        =========goodsProducts[商品库存]=======
        !products

        =========attrs[商品参数]========
        !attributes
    </layout>
    <cells>
        <cell id="unit">
            <placeholder>件/个/盒</placeholder>
        </cell>
        <cell id="specifications">
            <!-- 可以通过gen-control直接指定字段所用控件 -->
            <gen-control>
                <input-table addable="@:true" editable="@:true"
                             removable="@:true" >
                    ...
                </input-table>
            </gen-control>
            <selection>id,specification,value,picUrl</selection>
        </cell>
        <cell id="products">
            <!-- 可以引用外部view模型中的grid来显示子表 -->
            <view path="/app/mall/pages/LitemallGoodsProduct/LitemallGoodsProduct.view.xml"
                  grid="ref-edit"/>
        </cell>
    </cells>
</form>
```

**自动生成GraphQL请求**：

根据表单和表格模型，Nop平台会自动分析得到它们所需要访问的后台字段列表，从而自动生成graphql请求的`selection`部分，避免手工编写导致界面展示和后台请求数据不一致。

### 3. 字段联动逻辑

通过数据绑定属性表达式可以表达字段联动逻辑，例如：

```xml
<cell id="pid">
    <requiredOn>${level == 'L2'}</requiredOn>
    <visibleOn>${level == 'L2'}</visibleOn>
</cell>
```

### 4. 界面按钮和跳转逻辑

对于常见的crud页面、单个表单页面(`simple`)、多标签页面(`tab`)等都可以在视图大纲模型中进行定义和调整，在页面模型中可以直接引用已经定义的表单和表格模型。

```xml
<crud name="main" grid="list">
    <listActions>
        <!--
        修改新增按钮的功能为跳转到新增页面
        -->
        <action id="add-button" x:override="merge-replace" actionType="link" url="/mall-goods-create">

        </action>
    </listActions>

    <!-- bounded-merge表示合并结果在当前模型范围内。基础模型中有，当前模型中没有的子节点，会被自动删除。
         缺省生成的代码中已经定义了row-update-button和row-delete-button，只是配置了x:abstract=true，
         因此这里只要声明id，表示启用继承的按钮即可，可以避免编写重复的代码。
     -->
    <rowActions x:override="bounded-merge">
        <!--
            使用drawer而不是对话框来显示编辑表单
        -->
        <action id="row-update-button" actionType="drawer"/>

        <action id="row-delete-button"/>

    </rowActions>
</crud>
```

生成代码的时候会为每个业务对象自动生成增删改查对应的页面和操作按钮，它们存放在以**下划线**为前缀的`view`文件中，例如`_LitemallGoods.view.xml`。因此，在`LitemallGoods.view.xml`中调整按钮配置的时候，可以只表达变动的信息，而无需编写完整的按钮。

### 5. 可视化设计器

目前Nop平台实际使用的前端框架是百度AMIS框架（**外部框架文档**），它使用JSON格式的页面文件。在浏览器地址栏中我们直接输入后台的`page.yaml`文件来查看页面文件的内容(**无需在前端路由中注册**)，例如：

```
/index.html?#/amis/app/mall/pages/LitemallGoods/main.page.yaml`（**开发环境链接**）
```

它实际对应的页面是 `src/main/resources/_vfs/app/mall/pages/LitemallGoods/main.page.yaml`，其中的内容为：

```yaml
x:gen-extends: |
    <web:GenPage view="LitemallGoods.view.xml" page="main"
         xpl:lib="/nop/web/xlib/web.xlib" />
```

这个文件表示根据`LitemallGoods.view.xml`视图大纲模型中定义的`page`页面模型来生成AMIS描述。

**AMIS页面开发要点**：

1. 如果我们需要实现的页面比较特殊，无法使用视图大纲模型来有效描述，则可以直接编写`page.yaml`文件，而跳过视图大纲模型的配置。也就是说，**前端页面具有AMIS框架的全部能力，并不受视图大纲模型的限制**。

2. 即使是手工编写`page.yaml`文件，我们仍然可以通过`x:gen-extends`来引入局部的`form`或者`grid`定义，简化页面编写。（嵌套的json节点也可以使用`x:gen-extends`或者`x:extends`来表示动态生成）

3. 视图模型定义的是与具体实现技术无关的页面展示逻辑，原则上它可以适配任何前端框架技术。Nop平台后续会考虑接入阿里的`LowCodeEngine`。

4. 在自动生成的JSON页面的基础上，在`page.yaml`文件中我们手工对生成的代码进行差量修正（利用XDSL内置的Delta合并技术）。

**可视化设计器使用**：

在调试模式下，所有前端AMIS页面的右上角都有两个设计按钮。

1. 如果在后端手工修改了`page.yaml`或者`view.xml`模型文件，可以点击刷新页面来更新前端

2. 点击JSON设计按钮弹出YAML编辑器，允许在前端直接修改JSON描述然后立刻看到展现效果。

3. 点击可视化设计按钮会弹出amis-editor可视化设计器，允许开发人员通过可视化设计器来调整页面内容。**点击保存后会反向计算出完整页面与生成View的差量，然后将差量部分保存到`page.yaml`文件中**。

例如，在可视化设计器中修改【商品上架】页面的标题为【新增-商品】并保存之后，`add.page.yaml`文件中的内容为：

```yaml
x:gen-extends: |
  <web:GenPage view="LitemallGoods.view.xml" page="add" xpl:lib="/nop/web/xlib/web.xlib" />
title: '@i18n:LitemallGoods.forms.add.$title|新增-商品'
```

保存的内容已经被转换为差量形式。

### 6. 引入自定义模块

Nop平台前端框架的源码在工程nop-chaos（**前端框架源码**）中，一般情况下我们都是使用框架内置的组件来开发应用，此时我们只需要在java端引入预编译好的`nop-web-site`模块即可，无需重新编译前端的`nop-chaos`工程。

前端框架主要采用vue3.0、ant-design-vue和百度AMIS框架研发，我们在AMIS框架的基础上做了一些扩展，详细介绍参见文档[amis.md](../dev-guide/xui/amis.md)。`nop-chaos`内置了SystemJs模块加载能力，可以动态加载前端模块。例如：

```json
{
    "xui:import": "demo.lib.js",
    // 同级及下级节点中可以通过demo.xxx来访问demo模块中定义的内容。
}
```

### 7. 权限控制

**基于角色的访问控制**：

```xml
<!-- 按钮权限控制 -->
<action id="delete" title="删除" 
        x:auth="NopAuthUser:delete" 
        confirmText="确定删除吗？"/>

<!-- 字段权限控制 -->
<col id="salary" x:auth="HR:view-salary"/>

<!-- 页面权限控制 -->
<page name="admin" x:auth="ADMIN"/>
```

### 8. 数据交互

**GraphQL API调用**：

```xml
<!-- 查询数据 -->
<api url="@query:NopAuthUser__findPage"/>

<!-- 提交数据 -->
<api url="@mutation:NopAuthUser__save"/>

<!-- 带参数调用 -->
<api url="@query:NopAuthUser__get/{@formSelection}?id=$id"/>
```

## 开发调试

Nop平台系统化的使用元编程和DSL领域语言来开发，为此它也提供了一系列辅助开发调试工具。

### 1. 模型输出

所有编译期合成的模型都会输出到`_dump`目录下，其中会打印每个节点和属性的来源源码位置，方便开发者追踪模型生成的来源。

### 2. IDEA开发插件

`nop-idea-plugin`模块提供了IDEA开发插件，具有以下功能：

- **代码自动完成**：根据`xdef`元模型定义实现代码自动完成
- **格式校验**：对XDSL模型文件进行格式校验
- **断点调试**：对于XScript脚本语言和Xpl模板语言提供断点调试功能

### 3. GraphQL开发工具

Quarkus框架内置了`graphql-ui`开发工具，提供以下功能：

- **在线查看**：在线查看后台所有GraphQL类型定义
- **代码提示**：提供代码提示功能
- **自动补全**：提供自动补全功能

详细介绍参见文档[debug.md](../dev-guide/debug.md)

## Delta定制

### 1. Delta分层叠加

所有的XDSL模型文件都存放在`src/resources/_vfs`目录下，它们组成一个虚拟文件系统。这个虚拟文件系统支持Delta分层叠加的概念（类似于Docker技术中的`overlay-fs`分层文件系统），缺省具有分层`/_delta/default`(可以通过配置增加更多的分层)。

**示例**：
如果同时存在文件`/_vfs/_delta/default/nop/app.orm.xml`和`/nop/app.orm.xml`文件，则实际使用的是`_delta`目录下的版本。

**继承基础模型**：
在delta定制文件中，可以通过`x:extends="raw:/nop/app.orm.xml"` 来继承指定的基础模型，或者通过`x:extends="super"`来表示继承上一层的基础模型。

### 2. Delta定制的优势

与传统的编程语言所提供的定制机制相比，**Delta定制的规则非常通用直观，与具体的应用实现无关**。

**示例**：
以ORM引擎所用到的数据库Dialect定制为例，如果要扩展Hibernate框架内置的`MySQLDialect`，我们必须要具有一定的Hibernate框架的知识，如果用到了Spring集成，则我们还需要了解Spring对Hibernate的封装方式，具体从哪里找到Dialect并配置到当前`SessionFactory`中。而在Nop平台中，我们只需要增加文件`/_vfs/default/nop/dao/dialect/mysql.dialect.xml`，就可以确保所有用到MySQL方言的地方都会更新为使用新的Dialect模型。

### 3. 产品化实现

Delta定制代码存放在单独的目录中，可以与程序主应用的代码相分离。例如将delta定制文件打包为`nop-platform-delta`模块中，需要使用此定制的时候只要引入对应模块即可。

**多Delta层支持**：
我们也可以同时引入多个delta目录，然后通过`nop.core.vfs.delta-layer-ids`参数来控制delta层的顺序。例如配置 `nop.core.vfs.delta-layer-ids=base,hunan` 表示启用两个delta层，一个是基础产品层，在其上是某个具体部署版本所使用的delta层。

**产品化优势**：
通过这种方式，我们可以以极低的成本实现软件的产品化：**一个功能基本完善的基础产品在各个客户处实施的时候可以完全不修改基础产品的代码，而是只增加Delta定制代码**。

### 4. 应用定制

在开发具体应用时，我们可以使用delta定制机制来修正平台bug，或者增强平台功能。

**示例**：
`app-mall`项目通过定制`/_delta/default/nop/web/xlib/control.xlib`标签库来增加更多的字段控件支持。例如增加了`<edit-string-array>`控件，则在Excel数据模型中只要设置字段的数据域为`string-array`，则前端界面就会自动使用AMIS的`input-array`控件来编辑该字段。

更详细的介绍参见[xdsl.md](../dev-guide/xlang/xdsl.md)

## 最佳实践

### 1. 模型设计原则

**组件复用**：
```xml
<!-- 定义可复用的表格片段 -->
<grid id="base-grid" x:abstract="true">
    <cols>
        <col id="id" label="ID"/>
        <col id="name" label="名称"/>
        <col id="createTime" label="创建时间"/>
    </cols>
</grid>

<!-- 继承和扩展 -->
<grid id="user-grid" x:prototype="base-grid">
    <cols x:override="bounded-merge">
        <col id="email" label="邮箱"/>
        <col id="status" label="状态"/>
    </cols>
</grid>
```

**布局优化**：
```xml
<!-- 使用布局DSL -->
<layout>
    <!-- 一行两列 -->
    userName[用户名] nickName[昵称]
    
    <!-- 一行一列 -->
    email[邮箱]
    
    <!-- 分组显示 -->
    status[状态] department[部门]
</layout>
```

### 2. 性能优化

**数据分页**：
```xml
<grid id="list">
    <table>
        <pagination showPageSize="true" defaultPageSize="20"/>
    </table>
</grid>
```

**懒加载**：
```xml
<tabs name="detail" lazyLoad="true">
    <tab title="基本信息" form="base"/>
    <tab title="扩展信息" form="ext"/>
</tabs>
```

### 3. 错误处理

**统一错误处理**：
```xml
<api url="@mutation:NopAuthUser__save" 
     errorMessage="保存失败：${error.message}"/>

<!-- 自定义错误处理 -->
<action id="save" title="保存" 
        onError="handleSaveError"/>
```

## 常见问题

### 1. 如何自定义组件？

通过自定义控件库实现：

```xml
<controlLib>/custom/xlib/custom-control.xlib</controlLib>

<!-- 在自定义控件库中定义 -->
<control name="custom-input" 
          component="CustomInput" 
          props="value,onChange,placeholder"/>
```

### 2. 如何扩展AMIS功能？

通过JS扩展实现：

```javascript
// 注册自定义组件
amis.registry.registerComponent('custom-input', CustomInput);

// 扩展AMIS功能
amis.registry.registerFunction('formatCurrency', formatCurrency);
```

### 3. 如何处理复杂业务逻辑？

通过事件处理机制：

```xml
<action id="submit" title="提交" 
        onSuccess="handleSubmitSuccess"
        onError="handleSubmitError"/>
```

## 总结

Nop平台前端开发采用模型驱动的方式，通过XView视图模型描述界面结构，利用AMIS框架生成实际页面。这种开发方式具有以下优势：

1. **高效开发**：通过模型配置快速生成页面
2. **维护简单**：界面逻辑集中管理，易于维护
3. **扩展性强**：支持自定义组件和功能扩展
4. **一致性高**：统一的开发规范和最佳实践
5. **权限完善**：内置完整的权限控制机制
6. **Delta定制**：通过Delta分层叠加实现灵活的定制能力
7. **开发调试**：提供完善的开发调试工具链

遵循本指南的最佳实践，可以快速构建美观、易用、高效的前端应用。
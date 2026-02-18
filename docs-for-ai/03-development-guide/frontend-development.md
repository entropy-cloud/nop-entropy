# 前台开发指南

## 概述

Nop平台前台基于百度AMIS框架，通过XView视图模型（XML格式）定义页面结构，最终通过`x:gen-extends`元编程机制生成AMIS JSON配置。

**核心流程**：`XView (XML) → x:gen-extends → AMIS JSON → 前台页面`

## AMIS框架

### AMIS概述

AMIS是百度开源的低代码前端框架，特点：

- **JSON驱动**：通过JSON配置生成页面，无需编写HTML/CSS
- **丰富组件**：提供表单、表格、图表、对话框等组件
- **组件化**：支持组件嵌套和组合
- **内置功能**：分页、排序、筛选、权限等开箱即用

### AMIS与Nop平台的关系

```
XView模型 (Nop定义) ───→ AMIS JSON (AMIS标准) ───→ 前台页面
    ↑                                           ↑
x:gen-extends                             AMIS引擎渲染
```

- XView：Nop平台定义的业务领域视图模型
- AMIS JSON：AMIS框架的标准配置格式
- 前台页面：最终用户看到的页面

## Page.yaml模型

### 基本结构

```yaml
type: page
title: 页面标题
body:
  # AMIS组件配置
```

### x:gen-extends生成

```yaml
# main.page.yaml
x:gen-extends: |
  <web:GenPage view="NopAuthUser.view.xml" page="main" xpl:lib="/nop/web/xlib/web.xlib" />

# 生成后的AMIS JSON包含：
# - CRUD表格
# - 查询表单
# - 操作按钮
# - 编辑对话框
# - 权限控制
```

### 手动编写AMIS配置

也可以直接编写AMIS JSON：

```yaml
type: page
title: 用户列表
body:
  type: crud
  api:
    method: post
    url: /graphql
    data: |
      {
        "query": "{ User__findPage(request: { pageNo: 1, pageSize: 10 }) { items { id name email } } }"
      }
  columns:
    - name: id
      label: ID
    - name: name
      label: 姓名
    - name: email
      label: 邮箱
```

## XView到AMIS的映射

### Grid → CRUD

```xml
<!-- XView -->
<grid id="list">
    <cols>
        <col id="userName" label="用户名"/>
        <col id="status" label="状态"/>
    </cols>
</grid>

<crud name="main" grid="list" filterForm="query">
    <table/>
</crud>
```

```yaml
# 生成后的AMIS JSON
type: crud
columns:
  - name: userName
    label: 用户名
  - name: status
    label: 状态
filter:
  type: form
  body:
    # query表单字段
```

### Form → Form组件

```xml
<!-- XView -->
<form id="edit" editMode="update" title="编辑-用户">
    <layout>
        userName[用户名] nickName[昵称]
        email[邮箱]
    </layout>
</form>

<simple name="update" form="edit">
    <api url="@mutation:NopAuthUser__update/id?id=$id" withFormData="true"/>
</simple>
```

```yaml
# 生成后的AMIS JSON
type: dialog
title: 编辑-用户
body:
  type: form
  api:
    method: post
    url: /graphql
    data: |
      {
        "query": "mutation($data: UserInput!) { User__update(id: $id, data: $data) }"
      }
  body:
    - type: input-text
      name: userName
      label: 用户名
    - type: input-text
      name: nickName
      label: 昵称
    - type: input-email
      name: email
      label: 邮箱
```

### Action → Button/Action

```xml
<!-- XView -->
<action id="row-update-button" actionType="drawer"/>
<action id="row-delete-button" level="danger" confirm="确认删除？"/>
```

```yaml
# 生成后的AMIS JSON
actions:
  - type: button
    label: 编辑
    actionType: drawer
    drawer:
      body:
        # 表单内容
  - type: button
    label: 删除
    level: danger
    confirmText: 确认删除？
    api:
      method: post
      url: /graphql
```

## 常见AMIS组件

### 表格组件

```yaml
type: table
columns:
  - name: id
    label: ID
    width: 80
  - name: name
    label: 姓名
    sortable: true
  - name: status
    label: 状态
    type: mapping
    map:
      1: 启用
      0: 禁用
```

### 表单组件

```yaml
type: form
body:
  - type: input-text
    name: userName
    label: 用户名
    required: true
    placeholder: 请输入用户名
  - type: input-email
    name: email
    label: 邮箱
    validations:
      isEmail: true
  - type: select
    name: deptId
    label: 部门
    source:
      url: /graphql
      method: post
      data: |
        {
          "query": "{ Dept__findList { id deptName } }"
        }
      dataKey: 'payload.data.Dept__findList'
  - type: input-date
    name: createTime
    label: 创建时间
    disabled: true
```

### 对话框组件

```yaml
type: dialog
title: 对话框标题
size: md
closeOnOutside: false
body:
  # 对话框内容
actions:
  - type: cancel
    label: 取消
  - type: submit
    label: 确定
    level: primary
```

### 侧边抽屉

```yaml
type: drawer
title: 侧边抽屉
position: right
size: lg
body:
  # 抽屉内容
```

### Tab组件

```yaml
type: tabs
tabs:
  - title: 基本信息
    icon: fa fa-user
    body:
      # Tab内容
  - title: 详细信息
    icon: fa fa-info-circle
    reload: true
    body:
      # Tab内容
```

### 树形组件

```yaml
type: tree
name: deptId
label: 部门
source:
  url: /graphql
  method: post
  data: |
    {
      "query": "{ Dept__findTree { id label children } }"
    }
  dataKey: 'payload.data.Dept__findTree'
```

## GraphQL与AMIS集成

### Query调用

```yaml
type: crud
api:
  method: post
  url: /graphql
  data: |
    {
      "query": "{ User__findPage(request: { pageNo: $pageNo, pageSize: $pageSize }) { total items { id name email } } }"
    }
```

### Mutation调用

```yaml
type: form
api:
  method: post
  url: /graphql
  data: |
    {
      "query": "mutation($data: UserInput!) { User__save(data: $data) }",
      "variables": { "data": "$$" }
    }
```

### 变量使用

```yaml
# 使用当前行数据
data: |
  {
    "query": "query($id: String!) { User__get(id: $id) { id name } }",
    "variables": { "id": "${id}" }
  }

# 使用表单数据
data: |
  {
    "query": "mutation($data: UserInput!) { User__save(data: $data) }",
    "variables": { "data": "&" }
  }
```

## 常见场景

### 1. 自定义列渲染

```yaml
columns:
  - name: status
    label: 状态
    type: mapping
    map:
      1: 启用
      0: 禁用
  - name: actions
    label: 操作
    type: operation
    buttons:
      - label: 编辑
        actionType: drawer
      - label: 删除
        level: danger
        confirmText: 确认删除？
```

### 2. 自定义表单验证

```yaml
type: form
body:
  - type: input-text
    name: userName
    label: 用户名
    required: true
    validations:
      minLength: 3
      maxLength: 20
      matchRegexp: /^[a-zA-Z0-9_]+$/
  - type: input-password
    name: password
    label: 密码
    required: true
    validations:
      minLength: 6
```

### 3. 级联选择

```yaml
body:
  - type: select
    name: provinceId
    label: 省份
    source:
      url: /graphql
      method: post
      data: |
        {
          "query": "{ Province__findList { id name } }"
        }
    dataKey: 'payload.data.Province__findList'
    onChangeValue: |
      {
        "action": {
          "type": "setValue",
          "componentId": "cityId",
          "value": ""
        }
      }
  - type: select
    name: cityId
    label: 城市
    source:
      url: /graphql
      method: post
      data: |
        {
          "query": "{ City__findList(filter: { provinceId: { eq: '${provinceId}' } }) { id name } }"
        }
    dataKey: 'payload.data.City__findList'
```

### 4. 文件上传

```yaml
type: form
body:
  - type: input-file
    name: avatar
    label: 头像
    accept: image/*
    maxSize: 2097152
    receiver:
      action: /api/file/upload
      data:
        type: avatar
```

### 5. 富文本编辑

```yaml
type: form
body:
  - type: input-rich-text
    name: content
    label: 内容
    options:
      image: true
      video: true
```

### 6. 数据联动

```yaml
type: form
body:
  - type: select
    name: userType
    label: 用户类型
    options:
      - label: 管理员
        value: 1
      - label: 普通用户
        value: 2
  - type: input-text
    name: remark
    label: 备注
    visibleOn: "${userType} === 1"
```

### 7. 条件格式

```yaml
type: table
columns:
  - name: status
    label: 状态
    type: tag
    className: "${status === 1 ? 'label-success' : 'label-danger'}"
    map:
      1: 启用
      0: 禁用
```

## 自定义组件

### 自定义控件

```yaml
type: form
body:
  - type: custom
    name: customField
    label: 自定义字段
    onMount: |
      // 自定义JavaScript代码
      console.log('组件已挂载');
      this.doAction('setValue', { value: 'default value' });
```

### 事件处理

```yaml
type: button
label: 点击我
onEvent:
  click:
    actions:
      - type: toast
        level: success
        message: 按钮被点击了！
      - type: ajax
        api:
          method: post
          url: /api/action
```

## 路由与导航

### 路由格式

Nop平台使用Hash路由：

```
/#/{moduleId}/{bizObjName}/{pageId}
```

示例：
- `/#/nop/auth/NopAuthUser/main` - 用户管理主页面
- `/#/nop/auth/NopAuthUser/add` - 用户新增页面
- `/#/nop/auth/NopAuthUser/update` - 用户编辑页面

### 链接跳转

```yaml
type: button
label: 跳转到用户管理
link: /#/nop/auth/NopAuthUser/main
```

### 菜单配置

菜单在后台配置，通过GraphQL API返回：

```graphql
{
  Menu__findTree {
    id
    label
    icon
    url
    children {
      id
      label
      url
    }
  }
}
```

## 国际化

### i18n使用

```yaml
title: "@i18n:user.title"
label: "@i18n:user.name"
placeholder: "@i18n:user.namePlaceholder"
```

### 多语言文件

语言文件位于`_vfs/nop/web/i18n/`目录：

```
i18n/
├── zh-CN.json
├── en-US.json
└── ...
```

## 性能优化

### 1. 懒加载

```yaml
type: tabs
tabs:
  - title: 基本信息
    mountOnEnter: true
    unmountOnExit: true
    body: {}
  - title: 详细信息
    lazyLoad: true
    body: {}
```

### 2. 缓存

```yaml
api:
  cache: 300000
  sendOn: init
```

### 3. 数据分页

```yaml
type: crud
perPage: 20
perPageAvailable:
  - 10
  - 20
  - 50
  - 100
```

## 调试技巧

### 1. 查看生成的AMIS JSON

在浏览器控制台输入：

```javascript
window.amisStore.data
```

### 2. 页面预览

使用AMIS可视化编辑器：`/#/dev/editor`

### 3. GraphQL调试

使用GraphQL UI工具：`/q/graphql-ui`

## 最佳实践

1. **优先使用XView**：使用XView模型定义页面，而非直接编写AMIS JSON
2. **组件复用**：将通用组件抽取为page.yaml片段
3. **合理使用缓存**：对静态数据启用缓存
4. **权限控制**：在XView模型中定义权限规则
5. **响应式设计**：使用breakpoint适配不同屏幕
6. **性能优化**：懒加载、分页、减少不必要的数据请求
7. **错误处理**：提供友好的错误提示

## 常见问题

### 1. GraphQL变量使用错误

```yaml
# 错误
data: |
  {
    "query": "query($id: String!) { User__get(id: $id) }"
  }

# 正确
data: |
  {
    "query": "query($id: String!) { User__get(id: $id) }",
    "variables": { "id": "${id}" }
  }
```

### 2. 级联选择不更新

确保设置`onChangeValue`清除关联字段的值：

```yaml
onChangeValue: |
  {
    "action": {
      "type": "setValue",
      "componentId": "childField",
      "value": ""
    }
  }
```

### 3. 对话框不关闭

确保在操作成功后调用`close`：

```yaml
actions:
  - type: submit
    label: 确定
    level: primary
    actionType: ajax
    api:
      method: post
      url: /api/action
    redirect: close
```

## 相关文档

- [视图层开发指南](./view-layer-development.md) - XView模型详解
- [API开发指南](./api-development.md) - GraphQL API开发
- [XDef核心概念](../05-xlang/xdef-core.md) - XDef元模型

## 相关类

- `io.nop.xui.xlib.web.GenPageTag`
- `io.nop.xui.xui.AmisRenderer`

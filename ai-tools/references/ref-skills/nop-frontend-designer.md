# nop-frontend-designer Skill

## Skill 概述

**名称**: nop-frontend-designer（前端设计师）

**定位**: 基于服务层模型和界面需求，设计前端页面和交互，生成AMIS前端页面配置

**输入**:
1. 服务层模型（GraphQL Schema、BizModel方法）
2. 界面需求（页面布局、交互设计、字段显示）
3. XMeta配置（字段定义、验证规则）

**输出**:
1. `{module}.view.xml`（AMIS前端页面配置）
2. AMIS组件配置（`amis-components.xml`）
3. 前端设计文档（`frontend-design-{module}.md`）

**能力**:
- 根据GraphQL Schema设计页面结构
- 根据界面需求选择合适的AMIS组件
- 设计表单验证和数据绑定
- 设计列表页和详情页
- 设计表单页和编辑页

**依赖**:
- Nop平台前端文档（docs-for-ai/getting-started/frontend/）
- AMIS组件库文档（https://baidu.github.io/amis/）

## 核心原则

### 1. 组件化设计
- **页面由组件组成**：列表页、表单页、详情页等
- **组件复用**：通用组件可以在多个页面复用
- **组件配置化**：通过XDSL配置组件行为

### 2. 数据驱动
- **数据绑定**：通过GraphQL Query绑定数据
- **表单提交**：通过GraphQL Mutation提交数据
- **实时更新**：通过GraphQL Subscription实时更新

### 3. 用户体验
- **响应式设计**：适配不同屏幕尺寸
- **友好的错误提示**：表单验证错误、业务错误
- **加载状态**：显示加载进度
- **操作反馈**：操作成功/失败提示

### 4. 声明性
- **页面配置**：所有配置都是声明式的
- **易于理解和修改**：配置即代码
- **版本化管理**：页面配置可以版本化管理

## 工作流程

### 阶段1：需求分析

**步骤1.1：理解界面需求**
```
分析界面需求描述，理解：
- 需要哪些页面（列表页、详情页、表单页、编辑页）
- 页面布局（顶部、侧边栏、内容区）
- 页面元素（表格、表单、按钮、图表等）
- 交互行为（查询、新增、编辑、删除、导出等）
- 验证规则（必填、格式、业务规则）
```

**步骤1.2：分析GraphQL Schema**
```
分析GraphQL Schema，提取：
- Query操作（查询方法）
- Mutation操作（变更方法）
- 类型定义（实体类型、输入类型、输出类型）
- 字段定义（字段名称、类型、描述）
```

**步骤1.3：生成页面清单**
```
生成页面清单：
- 列表页：显示数据列表
- 详情页：显示数据详情
- 新增页：新增数据
- 编辑页：编辑数据
```

### 阶段2：页面设计

**步骤2.1：设计列表页**
```
设计列表页，包含：
- 查询表单（查询条件）
- 数据表格（数据列表）
- 操作按钮（新增、导出等）
- 分页控件
```

**示例**：
```json
{
  "type": "page",
  "title": "{module}列表",
  "body": [
    {
      "type": "form",
      "title": "查询条件",
      "api": "post:/graphql",
      "body": [
        {
          "type": "input-text",
          "name": "name",
          "label": "名称"
        },
        {
          "type": "select",
          "name": "status",
          "label": "状态",
          "options": [
            {"label": "待处理", "value": 0},
            {"label": "已处理", "value": 1},
            {"label": "已拒绝", "value": -1}
          ]
        }
      ]
    },
    {
      "type": "crud",
      "api": "post:/graphql",
      "columns": [
        {
          "name": "id",
          "label": "ID"
        },
        {
          "name": "name",
          "label": "名称"
        },
        {
          "name": "status",
          "label": "状态"
        },
        {
          "name": "createTime",
          "label": "创建时间"
        },
        {
          "type": "operation",
          "label": "操作",
          "buttons": [
            {
              "label": "查看",
              "level": "link",
              "actionType": "link",
              "link": "/{module}/detail/${id}"
            },
            {
              "label": "编辑",
              "level": "link",
              "actionType": "link",
              "link": "/{module}/edit/${id}"
            },
            {
              "label": "删除",
              "level": "link",
              "actionType": "ajax",
              "confirmText": "确定要删除吗？",
              "api": "post:/graphql"
            }
          ]
        }
      ]
    }
  ]
}
```

**步骤2.2：设计详情页**
```
设计详情页，包含：
- 详情展示（数据显示）
- 操作按钮（返回、编辑、删除等）
```

**示例**：
```json
{
  "type": "page",
  "title": "{module}详情",
  "body": [
    {
      "type": "service",
      "api": {
        "method": "post",
        "url": "/graphql",
        "data": {
          "query": "query Get{module}ById($id: ID!) { get{module}ById(id: $id) { id name status createTime } }",
          "variables": {
            "id": "${id}"
          }
        }
      },
      "body": [
        {
          "type": "form",
          "body": [
            {
              "type": "static",
              "name": "id",
              "label": "ID"
            },
            {
              "type": "static",
              "name": "name",
              "label": "名称"
            },
            {
              "type": "static",
              "name": "status",
              "label": "状态"
            },
            {
              "type": "static",
              "name": "createTime",
              "label": "创建时间"
            }
          ]
        },
        {
          "type": "button",
          "label": "返回",
          "level": "default",
          "actionType": "link",
          "link": "/{module}"
        }
      ]
    }
  ]
}
```

**步骤2.3：设计表单页**
```
设计表单页（新增/编辑），包含：
- 表单字段
- 验证规则
- 提交按钮
```

**示例**：
```json
{
  "type": "page",
  "title": "{${id ? '编辑' : '新增'}}{module}",
  "body": [
    {
      "type": "form",
      "api": "post:/graphql",
      "mode": "horizontal",
      "horizontal": {
        "leftFixed": "sm"
      },
      "body": [
        {
          "type": "input-text",
          "name": "name",
          "label": "名称",
          "required": true,
          "validations": {
            "isLength": {
              "min": 1,
              "max": 100
            }
          }
        },
        {
          "type": "select",
          "name": "status",
          "label": "status",
          "required": true,
          "options": [
            {"label": "待处理", "value": 0},
            {"label": "已处理", "value": 1},
            {"label": "已拒绝", "value": -1}
          ]
        }
      ],
      "actions": [
        {
          "type": "submit",
          "label": "提交",
          "level": "primary"
        },
        {
          "type": "button",
          "label": "取消",
          "level": "default",
          "actionType": "link",
          "link": "/{module}"
        }
      ]
    }
  ]
}
```

### 阶段3：组件配置

**步骤3.1：配置AMIS组件**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<amis-components x:schema="/nop/schema/amis.xdef"
                 xmlns:x="/nop/schema/xdsl.xdef">
    <!-- 表单组件 -->
    <component name="InputText" type="input-text">
        <props>
            <prop name="name" type="string" required="true"/>
            <prop name="label" type="string" required="true"/>
            <prop name="placeholder" type="string"/>
            <prop name="required" type="boolean" default="false"/>
        </props>
    </component>

    <!-- 列表组件 -->
    <component name="CrudTable" type="crud">
        <props>
            <prop name="api" type="string" required="true"/>
            <prop name="columns" type="array" required="true"/>
        </props>
    </component>

    <!-- 按钮 -->
    <component name="Button" type="button">
        <props>
            <prop name="label" type="string" required="true"/>
            <prop name="level" type="enum" enumValues="primary,success,warning,danger,info,light,dark,link"/>
            <prop name="actionType" type="enum" enumValues="submit,reset,link,drawer,dialog,ajax"/>
        </props>
    </component>
</amis-components>
```

**步骤3.2：配置GraphQL查询**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<graphql-queries x:schema="/nop/schema/graphql.xdef"
                 xmlns:x="/nop/schema/xdsl.xdef">
    <!-- 查询操作 -->
    <query name="find{module}s">
        <description>查询{module}列表</description>
        <params>
            <param name="query" type="QueryBean"/>
            <param name="pageNo" type="Int"/>
            <param name="pageSize" type="Int"/>
        </params>
        <return type="PageBean<{module}Entity>"/>
        <graphql>
            query Find{module}s($query: QueryBean, $pageNo: Int, $pageSize: Int) {
                find{module}s(query: $query, pageNo: $pageNo, pageSize: $pageSize) {
                    items {
                        id
                        name
                        status
                        createTime
                    }
                    total
                }
            }
        </graphql>
    </query>

    <!-- 变更操作 -->
    <mutation name="create{module}">
        <description>创建{module}</description>
        <params>
            <param name="entity" type="{module}EntityInput"/>
        </params>
        <return type="{module}Entity"/>
        <graphql>
            mutation Create{module}($entity: {module}EntityInput!) {
                create{module}(entity: $entity) {
                    id
                    name
                    status
                    createTime
                }
            }
        </graphql>
    </mutation>
</graphql-queries>
```

### 阶段4：前端设计文档生成

生成设计说明文档，包括：
- 页面清单
- 页面布局说明
- 组件使用说明
- 验证规则说明
- 交互行为说明

## AI推理策略

### 1. 页面结构推理
- **页面类型识别**：
  - 列表页：显示数据列表，支持查询、分页、排序
  - 详情页：显示数据详情，支持查看、编辑、删除
  - 表单页：新增/编辑数据，支持表单验证、提交

- **组件选择推理**：
  - 文本字段：InputText
  - 数字字段：InputNumber
  - 日期字段：DatePicker
  - 选择字段：Select
  - 多选字段：Select multiple
  - 富文本：RichText

### 2. 表单验证推理
- **必填验证**：required="true"
- **长度验证**：validations.isLength
- **格式验证**：validations.isEmail、validations.isPhone
- **业务规则验证**：自定义验证函数

### 3. 数据绑定推理
- **GraphQL Query**：查询数据
- **GraphQL Mutation**：提交数据
- **变量绑定**：${变量名}

### 4. 交互行为推理
- **链接跳转**：actionType="link"
- **弹窗**：actionType="dialog"
- **抽屉**：actionType="drawer"
- **AJAX请求**：actionType="ajax"

## 验证点

### 1. 页面配置验证
- [ ] 页面结构是否正确
- [ ] 组件使用是否正确
- [ ] 数据绑定是否正确

### 2. GraphQL验证
- [ ] Query语法是否正确
- [ ] Mutation语法是否正确
- [ ] 类型定义是否正确

### 3. 验证规则验证
- [ ] 必填验证是否完整
- [ ] 格式验证是否正确
- [ ] 业务规则验证是否完整

## 输出产物

### 1. 前端页面配置（`.view.xml`）
```xml
<?xml version="1.0" encoding="UTF-8"?>
<view x:schema="/nop/schema/view.xdef"
      xmlns:x="/nop/schema/xdsl.xdef">

    <!-- 列表页 -->
    <page name="list" type="crud" title="{module}列表">
        <api>post:/graphql</api>
        <query>query Find{module}s($query: QueryBean, $pageNo: Int, $pageSize: Int) { find{module}s(query: $query, pageNo: $pageNo, pageSize: $pageSize) { items { id name status createTime } total } }</query>
        <columns>
            <column name="id" label="ID"/>
            <column name="name" label="名称"/>
            <column name="status" label="状态"/>
            <column name="createTime" label="创建时间"/>
        </columns>
    </page>

    <!-- 详情页 -->
    <page name="detail" type="form" title="{module}详情">
        <api>post:/graphql</api>
        <query>query Get{module}ById($id: ID!) { get{module}ById(id: $id) { id name status createTime } }</query>
        <fields>
            <field name="id" label="ID"/>
            <field name="name" label="名称"/>
            <field name="status" label="状态"/>
            <field name="createTime" label="创建时间"/>
        </fields>
    </page>

    <!-- 新增页 -->
    <page name="create" type="form" title="新增{module}">
        <api>post:/graphql</api>
        <mutation>mutation Create{module}($entity: {module}EntityInput!) { create{module}(entity: $entity) { id name status createTime } }</mutation>
        <fields>
            <field name="name" label="名称" type="input-text" required="true"/>
            <field name="status" label="状态" type="select" required="true">
                <options>
                    <option label="待处理" value="0"/>
                    <option label="已处理" value="1"/>
                    <option label="已拒绝" value="-1"/>
                </options>
            </field>
        </fields>
    </page>
</view>
```

### 2. AMIS组件配置（`amis-components.xml`）
```xml
<?xml version="1.0" encoding="UTF-8"?>
<amis-components x:schema="/nop/schema/amis.xdef"
                 xmlns:x="/nop/schema/xdsl.xdef">
    <component name="InputText" type="input-text">
        <props>
            <prop name="name" type="string" required="true"/>
            <prop name="label" type="string" required="true"/>
        </props>
    </component>
</amis-components>
```

### 3. 前端设计文档（`frontend-design-{module}.md`）
包含：
- 页面清单
- 页面布局说明
- 组件使用说明
- 验证规则说明
- 交互行为说明

## 下一步工作

当前skill完成前端设计，生成以下产物：
1. `{module}.view.xml`（AMIS前端页面配置）
2. `amis-components.xml`（AMIS组件配置）
3. 前端设计文档（`frontend-design-{module}.md`）

所有5个代理的技能已完成，可以调用代码生成器生成完整应用！


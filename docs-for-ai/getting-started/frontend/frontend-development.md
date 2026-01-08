# 前台开发指南

## 概述

Nop平台前台开发基于AMIS框架，通过JSON配置生成页面，无需编写大量HTML和CSS代码。

## 核心概念

### 1. XView模型
- 定义：用于描述前台页面结构的JSON模型
- 位置：通常存放在`_vfs`目录下，如`/pages/user.xview.xml`
- 作用：自动生成AMIS框架的JSON配置

### 2. AMIS组件
- 基于百度AMIS框架，提供丰富的UI组件
- 支持表单、列表、图表、模态框等组件
- 通过JSON配置即可使用

### 3. 前后端交互
- 通过GraphQL API进行数据交互
- 支持自动生成查询和变更操作
- 内置权限控制和错误处理

## 开发流程

### 1. 设计XView模型
```xml
<x:view xmlns:x="http://nop-xlang.github.io/schema/xview.xdef" title="用户管理">
  <x:grid api="user.list">
    <x:column name="id" title="ID" />
    <x:column name="name" title="姓名" />
    <x:column name="email" title="邮箱" />
    <x:actions>
      <x:action name="edit" title="编辑" />
      <x:action name="delete" title="删除" />
    </x:actions>
  </x:grid>
</x:view>
```

### 2. 生成AMIS配置
系统自动将XView模型转换为AMIS JSON配置

### 3. 部署和访问
- 部署到服务器
- 通过浏览器访问页面，如`http://localhost:8080/#/user`

## 核心功能

### 1. 表单开发
- 支持各种表单控件
- 自动验证和提交
- 动态表单生成

### 2. 列表开发
- 表格展示数据
- 支持排序、筛选、分页
- 行操作和批量操作

### 3. 图表开发
- 支持各种图表类型
- 动态数据绑定
- 交互操作支持

### 4. 权限控制
- 基于角色的访问控制
- 按钮级别的权限控制
- 数据级别的权限控制

## 最佳实践

1. **使用XView模型**：优先使用XView模型定义页面，而非直接编写AMIS JSON
2. **组件复用**：将通用组件抽象为XView片段，实现复用
3. **权限设计**：在XView模型中定义权限控制规则
4. **性能优化**：合理使用缓存和懒加载
5. **响应式设计**：适配不同屏幕尺寸

## 注意事项

- 前台代码存放位置：`_vfs`目录下的`pages`文件夹
- 支持自定义AMIS组件
- 支持JS扩展和事件处理
- 与后端通过GraphQL API交互
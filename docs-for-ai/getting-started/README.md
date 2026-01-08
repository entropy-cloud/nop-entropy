# Nop平台AI开发文档索引

## 概述

本目录包含专门针对AI模型的Nop平台开发文档。这些文档采用简明扼要的风格，适合AI模型快速理解和使用，避免了传统文档的冗长和复杂。
Nop平台（NopPlatform）是基于可逆计算原理从零开始构建的下一代低代码开发平台，它支持独特的Delta定制机制，通过XLang语言实现元编程和面向语言编程范式。
nop-entropy是Nop平台的后端部分，它采用框架中立设计原则，可以运行在Quarkus/Spring/Solon等多种底层框架之上。

## 核心文档

### 开发规范
- [Nop平台AI开发规范](./ai/nop-ai-development.md)：Nop平台的核心开发规范，包括架构、流程、语言体系等
- [Nop平台AI编程开发指南](./ai/nop-ai-developer-guide.md)：AI编程指导，包括目录结构、架构、异常处理、帮助类使用等

### 错误处理
- [ErrorCode定义规范](./common/error-code.md)：如何定义和使用ErrorCode，统一异常处理

### 常用Helper类使用
- [StringHelper](./java-classes/StringHelper.doc.md)：字符串处理工具类
- [DateHelper](./java-classes/DateHelper.doc.md)：日期时间处理工具类
- [ConvertHelper](./java-classes/ConvertHelper.doc.md)：类型转换工具类
- [BeanTool](./java-classes/BeanTool.doc.md)：反射和Bean操作工具类
- [JsonTool](./java-classes/JsonTool.doc.md)：JSON处理工具类
- [XNode](./java-classes/XNode.doc.md)：XML和通用的Tree结构处理工具类

## 开发场景

### 分层开发
- [数据层开发指南](./data/data-layer-development.md)：IEntityDao, IOrmTemplate, IJdbcTemplate, SqlLib的使用
- [服务层开发指南](./service/service-layer-development.md)：BizModel, CrudBizModel的使用
- [界面层开发指南](./frontend/view-layer-development.md)：xview模型配置，AMIS组件使用

### 数据处理和任务编排
- [数据处理和任务编排指南](./data/data-processing.md)：record-mappings.xdef, record-file.xdef, batch.xdef, task.xdef的使用

### 功能开发
- [前台开发指南](./frontend/frontend-development.md)：基于AMIS框架的前台开发
- [CRUD开发指南](./business/crud-development.md)：自动生成增删改查功能
- [复杂业务开发指南](./business/complex-business-development.md)：处理复杂业务逻辑

### 模型设计
- [数据库模型设计指南](./data/database-model-design.md)：设计数据库表结构
- [API模型设计指南](./api/api-model-design.md)：设计API接口
- [XDef模型设计指南](./model/xdef-model-design.md)：设计领域特定语言模型

### 代码生成
- 模板编写规范
- 差量化定制指南
- 代码生成流程

### 测试调试
- [AutoTest自动化测试框架使用指南](./test/autotest-guide.md)：数据驱动的单元测试和集成测试框架，支持录制-验证模式
- [Nop平台问题诊断和调试指南](./test/nop-debug-and-diagnosis-guide.md)：Nop平台的调试机制、错误定位方法和常见问题解决方案

## 使用说明

1. 根据开发场景选择对应的文档
2. 优先使用平台内置Helper类，避免第三方库
3. 遵循统一的ErrorCode定义规范
4. 使用NopException作为统一异常类
5. 参考示例代码理解具体用法

## 最佳实践

- 保持代码简洁，避免冗余
- 优先使用Nop平台提供的标准组件，减少对第三方库的依赖
- 遵循模型驱动开发原则，充分利用已有的代码生成器和元编程机制
- 编写清晰的错误信息



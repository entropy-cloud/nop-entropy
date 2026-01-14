# Nop平台AI编程开发指南

## 1. 标准目录结构

```
project-root/
├── _vfs/                 # 虚拟文件系统，平台核心配置和资源
│   ├── app/              # 应用业务配置
│   ├── biz/              # 业务模型定义
│   ├── graphql/          # GraphQL Schema定义
│   ├── service/          # 服务定义
│   └── xlib/             # 扩展库
├── src/                  # 源代码目录
│   ├── main/java/        # Java源代码
│   ├── main/resources/   # 资源文件
│   └── test/             # 测试代码
├── pom.xml               # Maven配置文件
└── nop.xdef              # Nop平台核心配置
```

## 2. 基本架构组成

### 2.1 核心组件
- **BizModel**: 业务模型，封装复杂业务逻辑
- **ORM框架**: 基于JPA扩展，支持自动生成实体类和DAO
- **GraphQL/REST API**: 自动从模型生成，支持动态权限控制
- **XLang脚本**: 平台自定义脚本语言，用于业务逻辑编写
- **代码生成器**: 基于模型自动生成前后端代码

### 2.2 分层架构
- **数据层**: 基于IEntityDao、IOrmTemplate、IJdbcTemplate
- **服务层**: BizModel实现业务逻辑
- **API层**: GraphQL/REST API自动生成
- **视图层**: 基于AMIS框架的低代码前端

## 3. 异常处理方案

### 3.1 统一异常类
使用`NopException`作为统一异常类，支持ErrorCode和错误消息模板。

### 3.2 ErrorCode定义
```java
public interface IErrorCode {
    String getCode();
    String getMessage();
}
```

### 3.3 异常抛出示例
```java
throw new NopException("ERR_USER_NOT_FOUND", userId);
```

### 3.4 异常捕获与处理
平台自动处理异常，返回标准化的错误响应：
```json
{
  "errorCode": "ERR_USER_NOT_FOUND",
  "message": "用户不存在: 123",
  "detail": "详细错误信息"
}
```

## 4. 帮助类使用

### 4.1 核心帮助类
- **StringHelper**: 字符串处理，如`escapeHtml`、`padLeft`
- **DateHelper**: 日期时间处理，如`formatDate`、`parseDateTime`
- **ConvertHelper**: 类型转换，如`toInt`、`toNumber`
- **BeanTool**: 反射和Bean操作，如`getProperty`、`setProperty`
- **JsonTool**: JSON处理，如`toJson`、`parseJson`
- **XNode**: XML处理，如`fromXml`、`toXml`

### 4.2 使用示例
```java
// 字符串处理
String escaped = StringHelper.escapeHtml("<script>");

// 日期格式化
String dateStr = DateHelper.formatDate(new Date(), "yyyy-MM-dd");

// Bean属性访问
Object value = BeanTool.getProperty(bean, "user.name");
```

## 5. 框架使用关键信息

### 5.1 模型驱动开发
- 基于XML定义模型，自动生成代码
- 支持差量化定制，保持模型与代码的同步

### 5.2 代码生成
- 使用FreeMarker模板，可自定义生成逻辑
- 支持生成Java代码、GraphQL Schema、前端页面

### 5.3 测试调试
- 单元测试：使用JUnit 5
- 集成测试：基于Nop平台测试框架
- 调试技巧：使用NopDevKit插件

### 5.4 配置管理
- 使用xdef文件定义配置结构
- 支持多环境配置，自动加载对应环境配置

### 5.5 权限控制
- 基于角色的访问控制(RBAC)
- 支持字段级权限控制
- 动态权限规则配置

## 6. 最佳实践

1. **优先使用平台内置组件**：避免重复造轮子
2. **遵循模型驱动开发原则**：先定义模型，再生成代码
3. **保持代码简洁**：避免冗余，使用平台提供的工具类
4. **统一异常处理**：使用NopException和ErrorCode
5. **充分利用代码生成器**：减少手动编码，提高一致性
6. **编写清晰的错误信息**：便于调试和用户理解

## 7. 快速开始

1. **创建项目**：使用Nop平台脚手架生成项目
2. **定义模型**：在_vfs目录下创建模型文件
3. **生成代码**：运行代码生成命令
4. **实现业务逻辑**：在BizModel中编写业务代码
5. **测试**：编写单元测试和集成测试
6. **部署**：打包部署到服务器

## 8. 资源与支持

- **官方文档**：[Nop平台文档](https://docs.nop.dev/)
- **示例项目**：nop-demo目录下的示例代码
- **社区支持**：GitHub Issues和Discord社区

## 9. 更新日志

- 2026-01-02：初始创建AI编程开发指南

# Nop Platform Module Dependencies

## 概述

Nop Platform采用模块化架构设计，通过清晰的模块划分和依赖管理，实现了高内聚低耦合的架构。本文档详细描述了各个模块的职责、依赖关系和集成方式。

## 模块分类

### 1. 核心内核模块 (Core Kernel Modules)

这些模块是Nop Platform的基础，提供核心功能，不依赖其他Nop模块。

#### nop-api-support
- **职责**: API接口支持类
- **依赖**: 无
- **被依赖**: 所有其他模块
- **关键类**: `IErrorCode`, `IErrorDefinition`, `ErrorCodes`

#### nop-kernel
- **职责**: 核心框架实现
- **子模块**:
  - `nop-api-core`: API核心定义
  - `nop-commons`: 通用工具类
  - `nop-core`: 核心功能实现
  - `nop-xlang`: XLang语言支持
- **依赖**: `nop-api-support`
- **被依赖**: 所有其他模块

### 2. 核心框架模块 (Core Framework Modules)

提供框架级功能，支持依赖注入、配置管理、事务等。

#### nop-core-framework
- **职责**: 核心框架
- **子模块**:
  - `nop-ioc`: 依赖注入容器
  - `nop-config`: 配置管理
  - `nop-xapi`: XLang API
- **依赖**: `nop-kernel`
- **被依赖**: `nop-service-framework`, `nop-persistence`, 业务模块

#### nop-service-framework
- **职责**: 服务框架
- **子模块**:
  - `nop-biz`: 业务模型框架
- **依赖**: `nop-core-framework`, `nop-kernel`
- **被依赖**: 业务模块、GraphQL模块

### 3. 持久化模块 (Persistence Modules)

提供数据访问、ORM、事务等功能。

#### nop-persistence
- **职责**: 数据持久化
- **子模块**:
  - `nop-dao`: 数据访问接口
  - `nop-orm`: ORM引擎
- **依赖**: `nop-kernel`, `nop-core-framework`
- **被依赖**: 所有需要数据库的模块

### 4. 集成模块 (Integration Modules)

提供与第三方框架的集成。

#### nop-spring
- **职责**: Spring框架集成
- **依赖**: `nop-core-framework`, `nop-persistence`, Spring相关库
- **用途**: 将Nop Platform集成到Spring应用中

#### nop-quarkus
- **职责**: Quarkus框架集成
- **依赖**: `nop-core-framework`, `nop-persistence`, Quarkus相关库
- **用途**: 将Nop Platform集成到Quarkus应用中

### 5. 业务功能模块 (Business Modules)

提供具体的业务功能。

#### nop-auth
- **职责**: 用户权限管理
- **依赖**: `nop-persistence`, `nop-service-framework`
- **子模块**:
  - `nop-auth`: 权限实现
  - `nop-auth-dao`: 权限数据访问

#### nop-sys
- **职责**: 系统配置管理
- **依赖**: `nop-persistence`, `nop-service-framework`
- **子模块**:
  - `nop-sys`: 系统实现
  - `nop-sys-dao`: 系统数据访问

#### nop-rule
- **职责**: 规则引擎
- **依赖**: `nop-kernel`, `nop-xlang`

#### nop-workflow
- **职责**: 工作流引擎（nop-wf）
- **依赖**: `nop-kernel`, `nop-service-framework`

#### nop-report
- **职责**: 报表引擎
- **依赖**: `nop-kernel`, `nop-ooxml`

#### nop-ooxml
- **职责**: Office文件解析和生成
- **依赖**: `nop-kernel`

### 6. 数据处理模块 (Data Processing Modules)

提供数据处理、批处理、流处理等功能。

#### nop-batch
- **职责**: 批处理引擎
- **依赖**: `nop-kernel`, `nop-service-framework`

#### nop-task
- **职责**: 逻辑流编排
- **依赖**: `nop-kernel`, `nop-service-framework`

#### nop-job
- **职责**: 分布式任务调度
- **依赖**: `nop-kernel`, `nop-cluster`

#### nop-stream
- **职责**: 流处理
- **依赖**: `nop-kernel`

### 7. 基础设施模块 (Infrastructure Modules)

提供基础设施支持。

#### nop-cluster
- **职责**: 分布式集群支持
- **依赖**: `nop-kernel`

#### nop-tcc
- **职责**: 分布式事务
- **依赖**: `nop-cluster`, `nop-persistence`

#### nop-message
- **职责**: 消息队列封装
- **依赖**: `nop-kernel`

#### nop-file
- **职责**: 文件服务
- **依赖**: `nop-kernel`

#### nop-network
- **职责**: 网络通信
- **依赖**: `nop-kernel`

### 8. 工具和辅助模块 (Utility Modules)

提供工具类和辅助功能。

#### nop-utils
- **职责**: 工具类集合
- **依赖**: `nop-kernel`

#### nop-codegen
- **职责**: 代码生成器
- **依赖**: `nop-kernel`, `nop-xlang`

#### nop-autotest
- **职责**: 自动化测试框架
- **依赖**: `nop-kernel`, `nop-service-framework`

#### nop-format
- **职责**: 格式化工具
- **依赖**: `nop-kernel`

#### nop-migration
- **职责**: 数据迁移
- **依赖**: `nop-persistence`

### 9. 前端支持模块 (Frontend Modules)

提供前端开发支持。

#### nop-frontend-support
- **职责**: 前端支持
- **依赖**: `nop-kernel`

### 10. 扩展模块 (Extension Modules)

提供扩展功能。

#### nop-ai
- **职责**: AI大模型集成
- **依赖**: `nop-kernel`, `nop-xlang`

#### nop-search
- **职责**: 搜索功能
- **依赖**: `nop-kernel`

#### nop-datav
- **职责**: 数据可视化
- **依赖**: `nop-kernel`

#### nop-dyn
- **职责**: 在线设计
- **依赖**: `nop-kernel`, `nop-service-framework`

#### nop-integration
- **职责**: 外部服务集成
- **依赖**: `nop-kernel`

#### nop-dev-tools
- **职责**: 开发工具
- **依赖**: `nop-kernel`

### 11. 演示模块 (Demo Modules)

#### nop-demo
- **职责**: 演示程序
- **子模块**:
  - `nop-quarkus-demo`: Quarkus演示
  - `nop-spring-demo`: Spring演示
- **依赖**: 所有其他业务模块

#### nop-benchmark
- **职责**: 性能基准测试
- **依赖**: `nop-kernel`

### 12. BOM模块 (Bill of Materials)

#### nop-bom
- **职责**: 依赖版本管理
- **依赖**: 无
- **用途**: 统一管理所有依赖的版本

## 依赖层次结构

```
Level 1 (最底层)
├── nop-api-support
└── nop-bom

Level 2
└── nop-kernel
    ├── nop-api-core
    ├── nop-commons
    ├── nop-core
    └── nop-xlang

Level 3
├── nop-core-framework
│   ├── nop-ioc
│   ├── nop-config
│   └── nop-xapi
└── nop-utils

Level 4
├── nop-service-framework
│   └── nop-biz
├── nop-persistence
│   ├── nop-dao
│   └── nop-orm
├── nop-codegen
├── nop-format
└── 其他工具模块

Level 5
├── 集成模块
│   ├── nop-spring
│   └── nop-quarkus
├── 持久化扩展
│   └── nop-migration
└── 基础设施
    ├── nop-cluster
    └── nop-tcc

Level 6 (业务功能层)
├── nop-auth
├── nop-sys
├── nop-rule
├── nop-workflow
├── nop-report
├── nop-batch
├── nop-task
├── nop-job
├── nop-message
├── nop-file
├── nop-network
├── nop-ai
├── nop-search
├── nop-datav
├── nop-dyn
├── nop-integration
└── nop-frontend-support

Level 7 (应用层)
├── nop-demo
│   ├── nop-quarkus-demo
│   └── nop-spring-demo
└── nop-benchmark
```

## 典型依赖示例

### 1. 简单业务模块依赖

```xml
<dependencies>
    <!-- API定义 -->
    <dependency>
        <groupId>io.github.entropy-cloud</groupId>
        <artifactId>nop-api-support</artifactId>
    </dependency>

    <!-- 核心工具 -->
    <dependency>
        <groupId>io.github.entropy-cloud</groupId>
        <artifactId>nop-commons</artifactId>
    </dependency>

    <!-- 服务框架 -->
    <dependency>
        <groupId>io.github.entropy-cloud</groupId>
        <artifactId>nop-biz</artifactId>
    </dependency>

    <!-- 数据持久化 -->
    <dependency>
        <groupId>io.github.entropy-cloud</groupId>
        <artifactId>nop-dao</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.entropy-cloud</groupId>
        <artifactId>nop-orm</artifactId>
    </dependency>
</dependencies>
```

### 2. Quarkus集成依赖

```xml
<dependencies>
    <!-- Nop核心 -->
    <dependency>
        <groupId>io.github.entropy-cloud</groupId>
        <artifactId>nop-quarkus-core</artifactId>
    </dependency>

    <!-- Quarkus支持 -->
    <dependency>
        <groupId>io.github.entropy-cloud</groupId>
        <artifactId>nop-quarkus-integration</artifactId>
    </dependency>

    <!-- 业务模块 -->
    <dependency>
        <groupId>io.github.entropy-cloud</groupId>
        <artifactId>nop-auth</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.entropy-cloud</groupId>
        <artifactId>nop-sys</artifactId>
    </dependency>
</dependencies>
```

### 3. Spring集成依赖

```xml
<dependencies>
    <!-- Nop核心 -->
    <dependency>
        <groupId>io.github.entropy-cloud</groupId>
        <artifactId>nop-spring-core</artifactId>
    </dependency>

    <!-- Spring支持 -->
    <dependency>
        <groupId>io.github.entropy-cloud</groupId>
        <artifactId>nop-spring-integration</artifactId>
    </dependency>

    <!-- 业务模块 -->
    <dependency>
        <groupId>io.github.entropy-cloud</groupId>
        <artifactId>nop-auth</artifactId>
    </dependency>
</dependencies>
```

## 模块选择指南

### 1. 核心开发

需要以下模块：
- `nop-api-support`: API接口
- `nop-commons`: 通用工具
- `nop-core`: 核心功能
- `nop-ioc`: 依赖注入
- `nop-config`: 配置管理

### 2. 数据库应用开发

需要以下模块：
- 核心模块（上述）
- `nop-dao`: 数据访问接口
- `nop-orm`: ORM引擎
- `nop-biz`: 业务模型框架

### 3. Web应用开发

#### Quarkus应用
- 数据库应用模块
- `nop-quarkus-core`: Quarkus集成
- `nop-graphql`: GraphQL支持（可选）
- `nop-auth`: 权限管理（可选）
- `nop-sys`: 系统管理（可选）

#### Spring应用
- 数据库应用模块
- `nop-spring-core`: Spring集成
- `nop-graphql`: GraphQL支持（可选）
- `nop-auth`: 权限管理（可选）

### 4. 报表功能

需要以下模块：
- 核心模块
- `nop-report`: 报表引擎
- `nop-ooxml`: Office文件支持

### 5. 工作流功能

需要以下模块：
- 核心模块
- `nop-workflow`: 工作流引擎
- `nop-task`: 任务编排
- `nop-job`: 任务调度

### 6. 分布式应用

需要以下模块：
- 核心模块
- `nop-cluster`: 集群支持
- `nop-tcc`: 分布式事务
- `nop-message`: 消息队列

## 模块依赖最佳实践

### 1. 最小化依赖

只引入真正需要的模块：

```xml
<!-- ✅ 推荐：只引入需要的模块 -->
<dependency>
    <groupId>io.github.entropy-cloud</groupId>
    <artifactId>nop-dao</artifactId>
</dependency>

<!-- ❌ 不推荐：引入不需要的模块 -->
<dependency>
    <groupId>io.github.entropy-cloud</groupId>
    <artifactId>nop-entropy</artifactId>
    <type>pom</type>
</dependency>
```

### 2. 使用BOM管理版本

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.entropy-cloud</groupId>
            <artifactId>nop-bom</artifactId>
            <version>2.0.0-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>io.github.entropy-cloud</groupId>
        <artifactId>nop-auth</artifactId>
    </dependency>
</dependencies>
```

### 3. 避免循环依赖

模块依赖应该是单向的，避免循环依赖。

**正确的依赖关系**:
```
A → B → C → D
```

**错误的依赖关系**:
```
A → B → C → A (循环依赖)
```

### 4. 明确模块边界

每个模块应该有清晰的职责边界：

| 模块 | 职责 | 不应该包含 |
|------|------|----------|
| nop-kernel | 核心工具和API | 业务逻辑、数据库操作 |
| nop-persistence | 数据访问 | 业务规则、Web接口 |
| nop-auth | 权限管理 | 数据持久化（应该在dao中）|
| nop-sys | 系统配置 | 业务逻辑 |

## 版本兼容性

### 1. 模块版本同步

所有Nop模块应该使用相同版本：

```xml
<!-- ✅ 推荐：所有模块使用相同版本 -->
<properties>
    <nop.version>2.0.0-SNAPSHOT</nop.version>
</properties>

<dependencies>
    <dependency>
        <groupId>io.github.entropy-cloud</groupId>
        <artifactId>nop-auth</artifactId>
        <version>${nop.version}</version>
    </dependency>
    <dependency>
        <groupId>io.github.entropy-cloud</groupId>
        <artifactId>nop-sys</artifactId>
        <version>${nop.version}</version>
    </dependency>
</dependencies>
```

### 2. 框架版本要求

| Nop版本 | Java版本 | Quarkus版本 | Spring版本 |
|---------|----------|-------------|-----------|
| 2.0.0 | 17+ | 3.x | 6.x |

### 3. 升级路径

从2.0.0升级时：
1. 先升级核心模块（nop-kernel, nop-core-framework）
2. 再升级持久化模块（nop-persistence）
3. 最后升级业务模块

## 常见问题

### 1. 如何选择集成框架？

- **推荐Quarkus**: 新项目、云原生、需要快速启动
- **选择Spring**: 已有Spring项目、团队熟悉Spring生态

### 2. 是否需要所有模块？

不需要！根据项目需求选择性引入：
- 小型应用：核心模块 + 数据库模块
- 中型应用：核心模块 + 业务模块（auth, sys）
- 大型应用：所有需要的功能模块

### 3. 模块冲突怎么办？

1. 检查依赖树：`mvn dependency:tree`
2. 排除冲突的依赖：`<exclusions>`
3. 使用BOM统一版本管理

### 4. 如何自定义模块？

1. 创建新模块
2. 依赖需要的Nop模块
3. 实现业务逻辑
4. 通过IoC容器注册Bean

## 相关文档

- [API架构文档](./backend/api-architecture.md)
- [ORM架构文档](./backend/orm-architecture.md)
- [GraphQL架构文档](./backend/graphql-architecture.md)
- [服务层开发指南](../03-development-guide/service-layer-development.md)
- [IoC容器指南](../04-core-components/ioc-guide.md)

## 总结

Nop Platform采用清晰的模块化架构，通过分层设计和依赖管理，实现了高内聚低耦合。理解模块依赖关系有助于：

1. **正确选择模块**: 根据项目需求选择合适的模块
2. **避免冲突**: 通过BOM和最小依赖避免版本冲突
3. **清晰架构**: 明确模块边界和职责
4. **灵活扩展**: 基于现有模块构建自定义功能

通过合理使用模块依赖，可以构建稳定、可维护、可扩展的应用系统。

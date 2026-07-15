# Amundsen 深度分析报告

> Status: open
> Date: 2026-07-15
> Scope: nop-metadata 设计参考
> Goal: 分析 Amundsen 的架构设计，为 nop-metadata 提供设计参考

## Context

Amundsen 是一个数据发现和元数据引擎，专注于提高数据分析师、数据科学家和工程师与数据交互时的生产力。本文档分析其核心架构、数据模型和关键特性，为 nop-metadata 的设计提供参考。

## 核心架构

### 1. 微服务架构

Amundsen 采用微服务架构，包含三个核心服务：

- **Frontend Service**: Flask 应用 + React 前端
- **Search Service**: 基于 Elasticsearch 的搜索服务
- **Metadata Service**: 基于 Neo4j 或 Apache Atlas 的元数据服务

### 2. 组件架构

```
┌─────────────────────────────────────────────────────┐
│              Amundsen Frontend                       │
│            (Flask + React)                           │
└──────────────────────┬──────────────────────────────┘
                       │ REST API
┌──────────────────────▼──────────────────────────────┐
│              Amundsen Metadata                       │
│           (Flask Application)                        │
└──────────────────────┬──────────────────────────────┘
                       │
        ┌──────────────┼──────────────┐
        │              │              │
┌───────▼──────┐ ┌─────▼─────┐ ┌─────▼─────┐
│    Neo4j     │ │Elasticsearch│ │  Atlas    │
│  (Graph DB)  │ │  (Search)   │ │ (Option)  │
└──────────────┘ └───────────┘ └───────────┘
```

### 3. 存储层

- **Neo4j**: 图数据库，存储实体和关系
- **Elasticsearch**: 搜索索引，支持全文搜索
- **Apache Atlas**: 可选的元数据存储后端

## 元数据模型

### 1. 核心实体

#### 数据资产

- **Table**: 数据库表
- **Dashboard**: 仪表板
- **Column**: 列
- **Database**: 数据库

#### 用户实体

- **User**: 用户
- **Group**: 用户组

### 2. 关系类型

Amundsen 使用图数据库存储关系：

- **OWNER**: 表的所有者
- **READ_BY**: 表被用户读取
- **COLUMN_OF**: 列属于表
- **DASHBOARD_OF**: 仪表板属于用户

### 3. 数据模型特点

- **图结构**: 使用图数据库存储实体关系
- **PageRank**: 基于使用模式的搜索排序
- **简单模型**: 相对简单的元数据模型

## 关键特性

### 1. 数据发现

- **Search**: 基于使用模式的搜索
- **Popularity**: 热门表排序
- **Preview**: 数据预览

### 2. 元数据管理

- **Table Metadata**: 表元数据管理
- **Column Metadata**: 列元数据管理
- **Dashboard Metadata**: 仪表板元数据管理

### 3. 数据使用

- **Usage Tracking**: 使用情况跟踪
- **Recommendations**: 推荐系统
- **Popular Tables**: 热门表

### 4. 数据质量

- **Data Preview**: 数据预览
- **Column Statistics**: 列统计信息
- **Data Validation**: 数据验证

## 技术栈

- **后端**: Python 3.8+, Flask
- **前端**: React, Node.js v12
- **构建**: Poetry, npm
- **部署**: Docker, Kubernetes
- **数据库**: Neo4j 3.x, Apache Atlas (可选)
- **搜索**: Elasticsearch 6.x

## 对 nop-metadata 的启示

### 可借鉴的设计

1. **图数据库**: 使用图数据库存储元数据关系
2. **使用模式**: 基于使用模式的搜索排序
3. **微服务架构**: 灵活的微服务架构
4. **数据发现**: 专注于数据发现功能

### 需要改进的地方

1. **功能简单**: 功能相对简单，缺乏治理能力
2. **Python 栈**: 基于 Python 技术栈
3. **扩展性**: 大规模部署可能面临挑战

## Open Questions

- [ ] Neo4j 是否适合 nop 平台？
- [ ] 如何与 Nop ORM 集成？
- [ ] 是否需要支持图数据库？

## References

- [Amundsen GitHub](https://github.com/amundsen-io/amundsen)
- [Amundsen Documentation](https://www.amundsen.io/amundsen/)
- [Amundsen Metadata](https://github.com/amundsen-io/amundsen/tree/main/metadata)
- [Amundsen Search](https://github.com/amundsen-io/amundsen/tree/main/search)
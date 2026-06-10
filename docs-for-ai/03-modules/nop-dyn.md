# nop-dyn — 动态表单/实体模块

## 功能概览

运行时定义业务实体和页面，无需代码生成。

- 动态实体定义（实体名、表名、属性）
- 动态属性管理（支持多种 stdDomain）
- 实体关系定义
- 动态页面（支持 AMIS/OpenTiny/Formily 三种 schema）
- 动态 SQL 定义
- 模块与应用组合
- 应用级补丁文件覆盖

## 核心实体

| 实体 | 表名 | 用途 |
|------|------|------|
| NopDynApp | `nop_dyn_app` | 应用定义 |
| NopDynModule | `nop_dyn_module` | 模块定义 |
| NopDynAppModule | `nop_dyn_app_module` | 应用-模块映射 |
| NopDynModuleDep | `nop_dyn_module_dep` | 模块依赖 |
| NopDynEntityMeta | `nop_dyn_entity_meta` | 实体元数据 |
| NopDynPropMeta | `nop_dyn_prop_meta` | 属性元数据 |
| NopDynEntityRelationMeta | `nop_dyn_entity_relation_meta` | 实体关系 |
| NopDynFunctionMeta | `nop_dyn_function_meta` | 函数定义 |
| NopDynPage | `nop_dyn_page` | 页面定义 |
| NopDynSql | `nop_dyn_sql` | SQL 定义 |
| NopDynFile | `nop_dyn_file` | 模块文件 |
| NopDynPatchFile | `nop_dyn_patch_file` | 补丁文件 |
| NopDynDomain | `nop_dyn_domain` | 数据域定义 |

## 存储类型

- `VIRTUAL`：虚拟实体（不创建实际表）
- `REAL`：真实实体（创建数据库表）

## 页面 Schema 类型

- `AMIS`：百度 AMIS JSON Schema
- `OpenTiny`：华为 OpenTiny 组件
- `Formily`：阿里巴巴 Formily 表单方案

## 子模块

| 子模块 | 职责 |
|--------|------|
| `nop-dyn-api` | API DTO |
| `nop-dyn-dao` | ORM 实体与 DAO |
| `nop-dyn-service` | 业务逻辑 |
| `nop-dyn-web` | Web 层与 AMIS 页面 |

## 源码锚点

| 组件 | 路径 |
|------|------|
| ORM 模型 | `nop-dyn/model/nop-dyn.orm.xml` |

## 相关文档

- `../reusable-modules-overview.md`

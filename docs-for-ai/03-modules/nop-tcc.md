# nop-tcc — TCC 分布式事务

## 功能概览

TCC（Try-Confirm-Cancel）分布式事务协调器。

- 分支事务管理
- 事务组
- 超时控制
- 错误追踪

## 核心实体

| 实体 | 表名 | 用途 |
|------|------|------|
| NopTccRecord | `nop_tcc_record` | TCC 事务记录 |
| NopTccBranchRecord | `nop_tcc_branch_record` | 分支事务记录 |

## 关键字段

**NopTccRecord**:
- `txnId`：事务 ID
- `txnGroup`：事务组
- `txnName`：事务名称
- `status`：状态
- `expireTime`：过期时间

**NopTccBranchRecord**:
- `branchId`：分支 ID
- `txnId`：所属事务 ID
- `branchName`：分支名称
- `status`：状态

## 子模块

| 子模块 | 职责 |
|--------|------|
| `nop-tcc-core` | TCC 核心引擎 |
| `nop-tcc-dao` | ORM 实体与 DAO |
| `nop-tcc-service` | 业务逻辑 |
| `nop-tcc-web` | Web 层与 AMIS 页面 |
| `nop-tcc-integration` | 集成适配 |

## 源码锚点

| 组件 | 路径 |
|------|------|
| ORM 模型 | `nop-tcc/model/nop-tcc.orm.xml` |

## 相关文档

- `../reusable-modules-overview.md`

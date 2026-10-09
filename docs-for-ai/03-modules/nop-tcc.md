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

## 执行表访问权限（P1-3.8-003，plan 2386）

`NopTccRecord` / `NopTccBranchRecord` 的 xbiz 覆写 `save/update/delete/saveAll/deleteAll` mutations，声明 `<auth roles="admin"/>`——表内 `serviceName`/`requestPayload`/`confirmMethod` 等字段即执行凭据，不向普通用户开放写入口。该声明为 opt-in enforcement（`enable-action-auth=false` 默认部署行为不变，见 `service-layer.md`）。TccEngine 等引擎内部对执行表走 dao 直写，不受 biz 层 auth 约束。

Retry 侧（`NopRetryRecord`/`NopRetryPolicy`/`NopRetryAttempt`/`NopRetryDeadLetter`）同批采用相同收敛，见 `nop-retry` 模块。

## 源码锚点

| 组件 | 路径 |
|------|------|
| ORM 模型 | `nop-tcc/model/nop-tcc.orm.xml` |

## 相关文档

- `../reusable-modules-overview.md`

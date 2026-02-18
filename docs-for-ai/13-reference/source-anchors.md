# 核心类速查

本页列出 docs-for-ai 中频繁引用的核心类，便于快速定位。

> 约定：只列出类名和关键用途，详细用法见各开发指南文档。

## 服务层 / Biz

| 类名 | 用途 |
|------|------|
| `io.nop.biz.crud.CrudBizModel` | CRUD 业务模型基类，提供 `dao()`、`txn()` 等方法 |
| `io.nop.api.core.annotations.biz.BizLoader` | DataLoader 注解，定义关联数据加载 |
| `io.nop.api.core.annotations.biz.BizQuery` | 查询方法注解 |
| `io.nop.api.core.annotations.biz.BizMutation` | 修改方法注解（自动事务） |
| `io.nop.api.core.annotations.biz.ContextSource` | 获取上下文来源对象 |

## DAO / 查询

| 类名 | 用途 |
|------|------|
| `io.nop.dao.api.IEntityDao` | 实体 DAO 接口 |
| `io.nop.api.core.beans.query.QueryBean` | 查询条件封装 |
| `io.nop.api.core.beans.FieldSelectionBean` | 字段选择封装 |
| `io.nop.api.core.beans.FilterBeans` | 过滤条件构建工具 |
| `io.nop.api.core.beans.PageBean` | 分页结果封装 |

## 事务

| 类名 | 用途 |
|------|------|
| `io.nop.dao.txn.ITransactionTemplate` | 事务模板接口 |
| `io.nop.api.core.annotations.txn.Transactional` | 事务注解（非 BizModel 场景） |
| `io.nop.api.core.annotations.txn.TransactionPropagation` | 事务传播级别 |

## 异常

| 类名 | 用途 |
|------|------|
| `io.nop.api.core.exceptions.NopException` | 平台异常基类 |
| `io.nop.api.core.exceptions.ErrorCode` | 错误码定义接口 |

# 源码锚点（Source Anchors）

本页列出 docs-for-ai 中频繁引用的核心符号及其源码位置，便于 AI/开发者快速定位“事实来源”。

> 约定：此页只放 **可在仓库中直接定位的事实**，避免叙事性解释。

## 服务层 / Biz

- `io.nop.biz.crud.CrudBizModel`
  - 路径：`nop-service-framework/nop-biz/src/main/java/io/nop/biz/crud/CrudBizModel.java`
  - 关键点：`dao()` 来自 `IDaoProvider.dao(getEntityName())`，`txn()` 返回 `ITransactionTemplate`

- `io.nop.api.core.annotations.biz.BizLoader`
  - 路径：`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/annotations/biz/BizLoader.java`
  - 示例：`nop-demo/nop-delta-demo/src/main/java/io/nop/demo/biz/LoginApiBizModelDelta.java`

## DAO / 查询

- `io.nop.dao.api.IEntityDao`
  - 路径：`nop-persistence/nop-dao/src/main/java/io/nop/dao/api/IEntityDao.java`

- `io.nop.api.core.beans.query.QueryBean`
  - 路径：`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/query/QueryBean.java`

- `io.nop.api.core.beans.FieldSelectionBean`
  - 路径：`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/FieldSelectionBean.java`

- `io.nop.api.core.beans.FilterBeans`
  - 路径：`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/FilterBeans.java`

## 事务

- `io.nop.dao.txn.ITransactionTemplate`
  - 路径：`nop-persistence/nop-dao/src/main/java/io/nop/dao/txn/ITransactionTemplate.java`

- `io.nop.api.core.annotations.txn.Transactional`
  - 路径：`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/annotations/txn/Transactional.java`

- `io.nop.api.core.annotations.txn.TransactionPropagation`
  - 路径：`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/annotations/txn/TransactionPropagation.java`

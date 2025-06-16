## 1. 基础数据操作规范
    **自动化实现部分**
    所有实体默认获得以下通用CRUD操作，无需重复声明：
- `findPage(query)`：分页查询
- `findFirst(query)`：条件查询首条
- `save(data)`：创建数据
- `update(id, data)`：更新数据
- `delete(id)`：删除数据
- batchSave/batchDelete/batchUpdate等批量增删改操作

  **需自定义的场景**
  当操作涉及以下情况时，需在实体服务中显式定义：
- 业务逻辑复杂（如`Order.cancelOrder()`需校验状态、触发退款）
- 跨实体事务操作（如`Payment.refund()`需联动更新订单状态）
- 非标准参数（如`User.resetPassword()`需验证旧密码）

## 2. 采用DDD和充血模型设计服务对象
    service的description需要详细说明触发时机和触发方式，是页面触发还是自动触发。
    **2.1 实体服务**
- **命名**：直接使用实体名（如`Product`、`Order`）
- **设计原则**：
  - 充血模型：领域逻辑内聚在实体服务中（如`Product.checkStock()`包含库存计算规则）
  - 单一职责：每个方法只解决一个领域问题（避免`Order.processPaymentAndNotify()`）

  **2.2 System服务**
- **命名**：固定为`System`
- **限制**: 尽量少使用System服务，比如审批操作等一般通过实体服务进行。
- **适用场景**：
  - 非实体操作（如`System.sendEmail()`）

## 3. 关联用例
IMPORTANT: `app:useCaseNo`必须对应于需求文档中的用例编号，并且需要确保method的具体业务逻辑确实由该用例所定义。
如果没有对应的用例，则设置`app:useCaseNo`为`MISSING`。

## 4. 文件和二进制数据
- 上传下载由平台通用模块实现，这里不需要设计
- API永远不会返回byte[]等二进制数据，需要下载文件时返回远程文件id，由另外的机制进行下载

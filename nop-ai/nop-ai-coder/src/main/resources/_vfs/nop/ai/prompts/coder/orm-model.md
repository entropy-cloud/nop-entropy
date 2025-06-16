## **一、核心规范**

1. **设计范围**： 排除通用表：`User`、`Role`、`Permission`、页面资源等。
2. **主键**： 固定使用字段名id, 类型为VARCHAR(36), 无业务含义
3. **字段定义**

- 命名：避免 SQL 关键字冲突（如 `order` → `order_no`）。
- 类型：`stdSqlType` 必填，仅允许：
  `VARCHAR/CHAR/DATE/TIME/DATETIME/TIMESTAMP/INTEGER/BIGINT/DECIMAL/BOOLEAN/VARBINARY`。
- 文件/图片：`std-domain` 标识（`image | file | fileList | imageList`）。
- 不使用JSON字段，完全展开为具体的字段定义

4. **字典字段**
- 状态类字段（如state,status等）需要定义字典
- 有限枚举值字段（≤10个固定选项），比如支付方式等需要定义字典
- 统一使用VARCHAR(4)类型
- 使用 `ext:dict` 指定字典，字典名格式：`业务域_用途`（如 `card_status`）。
- 字典项的value采用3位数字，比如010，而code采用全大写的英文字符，可以使用下划线分隔
- boolean类型不需要设置字典

5. **表类型标识**

- `biz:type` 必填，可选值：
  `entity | entity-detail | txn | txn-detail | report | report-detail | config | config-detail`。

## **二、关联关系**

1. **外键声明**

* 仅在**子表**字段上通过 `orm:ref-*` 属性声明。

2. **主表引用子表集合**

* **定义位置：** 在**子表定义**中，通过以下属性直接指定主表反向关联子表的集合属性：
  ```
  orm:ref-prop="items"                 // 主表上的集合属性名称
  orm:ref-prop-display-name="订单明细" // 集合属性在UI上的显示名称
  orm:ref-table="OrderItem"            // 关联的子表名称
  ```
* **禁止：** 禁止在 `<column>` 元素内定义集合属性。集合属性必须在主表模型上定义（通过上述方式在子表中配置），且主表模型上的该属性本身必须是标量字段（非集合）。
* 设置 `orm:ref-prop` **表示关联的子表数据规模较小，**允许（并建议）** 直接通过主表ID查询关联子表数据，并**
  一次性全量加载到内存**中进行操作。
* 一般主数据表并不会反向指向引用它的子表，比如ProductCategory并不会有一个集合属性orderDetails指向子表OrderDetail。在业务层面上没有这种需求，在技术层面这种集合也过大，不适合直接在内存中操作。

## **三、禁止字段**

Nop 平台自动管理以下字段，**禁止手动添加**：

- 逻辑删除：`IS_DELETED`
- 审计字段：`CREATE_TIME`/`UPDATE_TIME`/`CREATED_BY`
- 乐观锁版本号

## **四、数据量预估**

- 设置 `db:estimatedRowCount`：基于 **100 用户 × 1 年** 的业务量估算。

## **五、设计补充说明的性质**

"数据库设计补充"小节记录**需求分析中识别的潜在结构变更点**，需经技术评审后实施：
- **信息准确性**：建议字段/表可能被优化为扩展现有表、修改约束等替代方案。
- **设计参考性**：核心价值是阐明业务需求的数据支撑要求，**仅仅用于参考**，并不是硬性要求
- **实施决策**：最终结构由数据库工程师根据**性能/一致性/扩展性**评估确定。

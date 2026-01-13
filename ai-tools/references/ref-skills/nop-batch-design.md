# nop-batch-design Skill

## Skill 概述

**名称**: nop-batch-design（批处理设计）

**定位**: 据服务层定义和批处理需求，生成Nop平台的Batch模型配置文件

**输入**:
- 服务层定义（BizModel方法、业务规则）
- 批处理需求（文件导入导出、数据转换、定时任务）

**输出**:
- Batch模型配置文件（`{module}.batch.xml`）
- 批处理记录映射文件（`record-mappings.xdef`）
- DDL脚本（可选，`create_{module}_tables.sql`）

**能力**:
- 理解批处理流程
- 支持多种数据源和格式（CSV、Excel、JSON、数据库等）
- 配置记录映射和转换规则
- 集成TaskFlow任务编排

**依赖**:
- Nop平台批处理引擎文档（docs-for-ai/getting-started/task/）
- Nop平台TaskFlow模型（`/nop/task/lib/common.task.xml`）

## 核心原则

### 1. 分层解耦
- **批处理配置**与**服务层**分离
- 批处理逻辑通过XMeta配置定义，而非硬编码
- 服备层只提供领域对象，不直接调用数据库

### 2. 声明性
- 所有配置都是声明式的，易于理解和修改
- 通过记录映射文档化数据转换规则

### 3. 可扩展性
- 支持通过Delta文件定制批处理逻辑
- 可以定义自定义的记录转换器和处理器

## 工作流程

### 阶段1：需求分析
1. **理解批处理场景**
   - 识别数据源（CSV、Excel、JSON、数据库）
   - 理解业务规则和数据流转
   - 识别错误处理策略和重试机制

2. **分析服务层需求**
   - 识别需要的服务方法（查询、保存、更新等）
   - 识别需要扩展点（自定义业务逻辑）

3. **输出需求列表**
   - 批处理功能列表
   - 性能要求
   - 数据转换规则
   - 错误处理策略

### 阶段2：记录映射设计
1. **定义数据源**
   - 配置数据源（数据库、文件系统、FTP、SFTP等）

2. **定义记录转换规则**
   - 指定源格式到目标格式的映射
   - 字段映射
   - 数据验证规则
   - 错误处理规则

3. **生成record-mappings.xdef**
   - 根据配置生成Nop平台的记录映射模型文件

### 阶段3：批模型设计
1. **设计文件模型结构**
   - 定义文件模型（File Model）
   - 定义数据集模型（Dataset Model）
   - 定义批处理模型（Batch Model）

2. **配置处理节点**
   - **reader节点**：定义数据读取器
   - **processor节点**：定义数据处理器
   - **writer节点**：定义数据写入器
   - **validator节点**：定义数据验证器

3. **生成batch.xml**
   - 根据XDef元模型生成Nop平台格式的批处理配置文件

### 阶段4：任务编排设计
1. **设计任务流程**
   - 定义任务节点（task）
   - 配置输入步骤（steps）
   - 配置输出步骤（outputs）
   - 定义任务依赖关系

2. **生成task.xml**
   - 使用TaskFlow引擎执行批处理流程

## Nop平台特性支持

### 1. TaskFlow引擎
- **核心功能**：任务编排、状态机、重试策略
- **内置节点**：`<step>、`<choice>、`<parallel>、`<foreach>`
- **内置服务**：`@TaskService`

### 2. 记录映射内置处理器
- **Reader**：`ExcelReader`、`CsvReader`、`JsonReader`
- **Processor**：`DataProcessor`、`TransformProcessor`、`ValidationProcessor`

### 3. 批处理引擎
- **核心引擎**：`nop-batch`引擎
- **文件格式**：`batch.xdef`元模型
- **扩展机制**：支持Delta定制

## AI推理策略

### 1. 需求理解推理
- **第一步**：提取批处理场景
  - 识别数据源类型（Excel、CSV、JSON、数据库）
  - 识别业务规则和数据验证需求

- **第二步**：设计记录映射
  - 确定源字段和目标字段
  - 定义转换规则和验证逻辑

- **第三步**：设计批模型
  - 根据场景选择合适的Model（File Model vs Dataset Model）
  - 设计reader和processor链

### 2. 服务方法设计推理
- **方法职责**：判断应该定义查询方法还是变更方法
- **参数设计**：根据业务语义设计合理的参数列表

### 3. 任务编排推理
- **步骤依赖**：识别任务间的依赖关系
- **错误处理**：设计重试策略和回滚机制

## 常见错误和优化

### 1. 直接使用数据库
- **错误代码**：在BizModel中直接调用`dao().batchExecute()`
- **正确做法**：使用`TaskFlow`引擎或定义批处理任务

### 2. 在实体上添加业务方法
- **错误代码**：在BizModel中添加`processBatch()`方法
- **正确做法**：在XMeta中定义批处理任务，通过TaskFlow执行

### 3. 不使用TaskFlow引擎
- **错误代码**：手动实现批处理循环逻辑
- **正确做法**：配置TaskFlow任务，声明式定义流程

## 适用场景

### 1. 适合场景
- ✅ **Excel数据导入导出**
- ✅ **文件系统批量操作**
- ✅ **数据库批量同步**
- ✅ **大数据ETL任务**

- ✅ **不适合场景**
- 简单条记录插入（使用简单的SQL语句或ORM操作）

### 2. 定时数据处理任务
- ✅ **数据转换和验证**（CSV转XML、数据清洗）
- ✅ **文件分发和备份**
- ✅ **报表生成**

### 3. 定期任务调度
- ✅ **数据同步任务**（每天凌晨导出报表）
- ✅ **数据归档**（按日期归档）
- ✅ **缓存清理**

## 工作流程

```
用户需求："我需要设计一个批处理任务，从Excel读取订单数据，进行验证，然后导入数据库。"

**AI响应**：

**阶段1：需求分析**
> 我理解了您的需求。这是一个典型的批处理场景：
> - **数据源**：Excel文件（`.xlsx`格式）
> - **核心流程**：读取→验证→转换→导入
> - **业务规则**：订单号格式验证、总金额校验、商品库存检查
> - **扩展点**：需要在XMeta中定义批处理相关的数据权限

**阶段2：记录映射设计**
> 定义数据源配置：指定Excel读取器类型
> 定义字段映射：`orderNo`→`order_id`，`productName`→`product_id`
> 定义转换规则：`金额字段`使用`DECIMAL`类型，保留2位小数
> 定义验证规则：订单号不能为空、数量必须大于0

**阶段3：批模型设计**
> 选择File Model作为数据载体
> 设计reader节点配置：`<reader type="excel" file="*.xlsx"`
> 设计processor节点配置：`<processor type="validation" className="OrderValidationProcessor"`
> 定义writer节点配置：`<writer type="database" className="DatabaseWriter"`
> 生成batch.xml配置

**阶段4：任务编排设计**
> 设计task.xml，定义批处理流程
> 配置输入步骤：读取→验证→转换→写入
> 配置输出步骤：导入成功→邮件通知→记录完成

让我进入批模型设计阶段。

**阶段1：数据源配置**
> 定义reader节点：`<reader type="excel" file="orders.xlsx" skipRows="1" sheet="订单数据"`
> 定义processor节点：`<processor type="database" className="OrderValidationProcessor"`

**阶段2：字段映射**
> 定义`fieldMapping`节点：
  ```xml
  <field-mappings>
    <mapping source="orderNo" target="orderId" type="string"/>
    <mapping source="productName" target="productId" type="string"/>
    <mapping source="productPrice" target="price" type="decimal(18,2)"/>
    <mapping source="quantity" target="quantity" type="int"/>
  </field-mappings>
  ```

**阶段3：验证规则配置**
> 定义`validation`节点：
  ```xml
  <validation>
    <rule name="orderNoRequired" message="订单号不能为空" level="error">
        <expression test="orderNo != null and orderNo.length() > 0"/>
    </validation>
    
    <rule name="totalAmountValid" message="订单总金额必须大于0" level="error">
        <expression test="totalAmount.compareTo(BigDecimal.ZERO) > 0"/>
    </validation>
    
    <rule name="quantityValid" message="订购数量必须大于0" level="error">
        <expression test="quantity > 0"/>
    </validation>
    
    <rule name="productExists" message="商品必须存在" level="error">
        <expression test="productId != null"/>
    </validation>
  </validation>
  ```

**阶段4：任务设计**
> 定义task节点：
  ```xml
  <task name="importOrders" type="sequence">
        <steps>
            <step name="readExcel" type="read" target="orderData">
                <option name="file" value="orders.xlsx"/>
            </step>
            <step name="validate" type="processor" target="OrderValidationProcessor"/>
            </step>
            <step name="transform" type="processor" target="DataTransformer"/>
            </step>
            <step name="writeDatabase" type="writer" target="DatabaseWriter"/>
            </step>
            <step name="sendNotification" type="action"/>
            </steps>
    </task>
    ```

```

您对以上设计有疑问或需要调整的地方吗？

```
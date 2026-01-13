# nop-batch-engineer Skill

## Skill 概述

**名称**: nop-batch-engineer（批处理工程师）

**定位**: 基于服务层设计和批处理需求，设计批处理任务和记录映射，实现大数据ETL和定时任务

**输入**:
1. 服务层定义（`{module}.xbiz.xml`）
2. BizModel方法列表
3. 批处理需求（文件导入导出、数据转换、定时任务）
4. 数据源配置（数据库、文件系统等）

**输出**:
1. `{module}.batch.xml`（批处理模型配置）
2. 记录映射文件（`record-mappings.xdef`）
3. DDL脚本（`create_batch_tables.sql`，可选）
4. 批处理设计文档（`batch-design-{module}.md`）

**能力**:
- 理解批处理流程
- 支持多种数据源和格式（CSV、Excel、JSON、数据库等）
- 配置记录映射和转换规则
- 集成TaskFlow任务编排
- 设计错误处理和重试机制

**依赖**:
- Nop平台批处理引擎文档（docs-for-ai/getting-started/task/）
- Nop平台TaskFlow模型（`/nop/task/lib/common.task.xml`）
- Nop平台XDSL规范（docs-for-ai/getting-started/xlang/）

## 核心原则

### 1. 分层解耦
- **批处理配置**与**服务层**分离
- 批处理逻辑通过XMeta配置定义，而非硬编码
- 服务层只提供领域对象，不直接调用数据库

### 2. 声明性
- 所有配置都是声明式的，易于理解和修改
- 通过记录映射文档化数据转换规则

### 3. 可扩展性
- 支持通过Delta文件定制批处理逻辑
- 可以定义自定义的记录转换器和处理器

### 4. 错误处理
- 支持事务性批处理（要么全部成功，要么全部回滚）
- 支持错误隔离（单个记录失败不影响其他记录）
- 支持重试机制

## 工作流程

### 阶段1：需求分析

**步骤1.1：理解批处理场景**
```
分析批处理需求，理解：
- 数据源类型（CSV、Excel、JSON、数据库）
- 数据目标类型（数据库、文件系统、FTP、SFTP等）
- 数据转换规则（字段映射、数据验证、数据转换）
- 业务规则（数据清洗、数据校验、数据聚合）
- 错误处理策略（跳过错误记录、停止批处理、重试）
- 性能要求（批量大小、并发度）
```

**步骤1.2：分析服务层需求**
```
分析服务层定义，识别：
- 需要调用的BizModel方法
- 需要扩展点（自定义业务逻辑）
- 事务边界
- 数据权限控制
```

**步骤1.3：输出需求列表**
```
输出批处理需求列表：
- 批处理功能列表
- 性能要求
- 数据转换规则
- 错误处理策略
- 重试机制
```

### 阶段2：批处理模型设计

**步骤2.1：选择模型类型**
```
根据场景选择合适的模型类型：
- **File Model**：适合文件导入导出（Excel、CSV、JSON）
- **Dataset Model**：适合数据库到数据库的批处理
- **Custom Model**：适合自定义批处理逻辑
```

**步骤2.2：设计reader节点**
```
设计reader节点：
- 数据源配置（文件路径、数据库连接）
- 数据格式配置（Excel工作表、CSV分隔符、JSON字段）
- 数据读取策略（全量读取、增量读取、分页读取）
```

**步骤2.3：设计processor节点**
```
设计processor节点：
- 数据转换器（字段映射、数据验证、数据清洗）
- 业务规则处理器（数据校验、数据聚合）
- 自定义处理器（复杂的业务逻辑）
```

**步骤2.4：设计writer节点**
```
设计writer节点：
- 数据目标配置（数据库表、文件路径、FTP、SFTP）
- 数据写入策略（批量插入、批量更新、删除）
- 数据冲突处理策略（覆盖、跳过、报错）
```

**步骤2.5：设计validator节点**
```
设计validator节点：
- 数据验证规则（必填验证、格式验证、业务规则验证）
- 错误处理策略（跳过错误记录、停止批处理、记录错误日志）
```

### 阶段3：记录映射设计

**步骤3.1：定义数据源**
```
定义数据源：
- 数据库连接（JDBC URL、用户名、密码）
- 文件系统（本地路径、FTP、SFTP）
- 文件格式（Excel、CSV、JSON）
```

**步骤3.2：定义字段映射**
```
定义源字段到目标字段的映射：
- 源字段名称
- 目标字段名称
- 数据类型转换
- 数据格式转换
- 默认值设置
```

**步骤3.3：定义转换规则**
```
定义数据转换规则：
- 字段拆分（如：拆分全名到姓和名）
- 字段合并（如：合并姓和名到全名）
- 值映射（如：状态码映射为状态名）
- 计算字段（如：小计 = 单价 × 数量）
```

**步骤3.4：定义验证规则**
```
定义数据验证规则：
- 必填验证（字段不能为空）
- 格式验证（如：邮箱格式、手机号格式）
- 业务规则验证（如：金额必须大于0）
- 自定义验证规则
```

**步骤3.5：定义错误处理规则**
```
定义错误处理规则：
- 错误级别（警告、错误、致命）
- 错误处理策略（跳过、停止、重试）
- 错误日志记录
```

### 阶段4：批处理配置生成

**步骤4.1：生成.batch.xml**
```
生成Nop平台格式的批处理配置文件：
```xml
<?xml version="1.0" encoding="UTF-8"?>
<batch x:schema="/nop/schema/batch.xdef"
       xmlns:x="/nop/schema/xdsl.xdef">

    <!-- 批处理任务 -->
    <job name="import{module}s">
        <description>导入{module}数据</description>

        <!-- Reader节点 -->
        <reader type="excel" id="excelReader">
            <file>${inputPath}/orders.xlsx</file>
            <sheet>订单数据</sheet>
            <skipRows>1</skipRows>
        </reader>

        <!-- Processor节点 -->
        <processor type="validation" id="validator">
            <className>io.nop.batch.validator.{module}Validator</className>
            <skipOnError>true</skipOnError>
        </processor>

        <processor type="transform" id="transformer">
            <className>io.nop.batch.transformer.{module}Transformer</className>
            <mapping ref="recordMappings"/>
        </processor>

        <!-- Writer节点 -->
        <writer type="database" id="databaseWriter">
            <dataSource>defaultDataSource</dataSource>
            <tableName>{module}</tableName>
            <batchSize>1000</batchSize>
        </writer>

        <!-- 错误处理器 -->
        <errorHandler>
            <skipOnError>true</skipOnError>
            <errorLogFile>${logPath}/import_errors.log</errorLogFile>
        </errorHandler>
    </job>
</batch>
```

**步骤4.2：生成record-mappings.xdef**
```
生成记录映射文件：
```xml
<?xml version="1.0" encoding="UTF-8"?>
<record-mappings x:schema="/nop/schema/record-mappings.xdef"
                 xmlns:x="/nop/schema/xdsl.xdef">

    <!-- 字段映射 -->
    <field-mappings>
        <mapping source="订单号" target="orderNo" type="string" required="true">
            <trim>true</trim>
        </mapping>
        <mapping source="用户ID" target="userId" type="bigint" required="true"/>
        <mapping source="订单状态" target="orderStatus" type="tinyint">
            <value-mappings>
                <map source="待支付" target="0"/>
                <map source="已支付" target="1"/>
                <map source="已发货" target="2"/>
                <map source="已完成" target="3"/>
                <map source="已取消" target="-1"/>
            </value-mappings>
        </mapping>
        <mapping source="订单总金额" target="totalAmount" type="decimal(18,2)">
            <currency>￥</currency>
        </mapping>
        <mapping source="创建时间" target="createTime" type="datetime">
            <format>yyyy-MM-dd HH:mm:ss</format>
        </mapping>
    </field-mappings>

    <!-- 验证规则 -->
    <validation>
        <rule name="orderNoRequired" message="订单号不能为空" level="error">
            <expression test="orderNo != null and orderNo.length() > 0"/>
        </rule>

        <rule name="totalAmountValid" message="订单总金额必须大于0" level="error">
            <expression test="totalAmount.compareTo(BigDecimal.ZERO) > 0"/>
        </rule>

        <rule name="orderStatusValid" message="订单状态必须有效" level="error">
            <expression test="orderStatus >= -1 and orderStatus <= 3"/>
        </rule>
    </validation>

    <!-- 转换规则 -->
    <transform>
        <field name="orderNo" trim="true"/>
        <field name="totalAmount" format="#,##0.00"/>
    </transform>
</record-mappings>
```

**步骤4.3：生成task.xml**
```
生成TaskFlow任务配置：
```xml
<?xml version="1.0" encoding="UTF-8"?>
<task x:schema="/nop/schema/task/task.xdef"
      xmlns:x="/nop/schema/xdsl.xdef"
      x:extends="/nop/task/lib/common.task.xml">

    <description>执行{module}批处理任务</description>

    <steps xdef:key-attr="name" xdef:body-type="list">
        <!-- 读取Excel -->
        <step name="readExcel" type="read" target="orderData">
            <description>读取Excel文件</description>
            <option name="file" value="${inputPath}/orders.xlsx"/>
            <option name="sheet" value="订单数据"/>
        </step>

        <!-- 验证数据 -->
        <step name="validateData" type="processor" target="OrderValidationProcessor">
            <description>验证订单数据</description>
            <option name="mappingRef" value="recordMappings"/>
        </step>

        <!-- 转换数据 -->
        <step name="transformData" type="processor" target="DataTransformer">
            <description>转换订单数据</description>
            <option name="mappingRef" value="recordMappings"/>
        </step>

        <!-- 写入数据库 -->
        <step name="writeDatabase" type="writer" target="DatabaseWriter">
            <description>写入数据库</description>
            <option name="dataSource" value="defaultDataSource"/>
            <option name="tableName" value="{module}"/>
            <option name="batchSize" value="1000"/>
        </step>

        <!-- 发送通知 -->
        <step name="sendNotification" type="action">
            <description>发送完成通知</description>
            <option name="recipients" value="${email.admin}"/>
            <option name="subject" value="批处理任务完成"/>
            <option name="template" value="batch-completion"/>
        </step>
    </steps>
</task>
```

### 阶段5：批处理设计文档生成

生成设计说明文档，包括：
- 批处理需求总结
- 数据源和目标配置
- 记录映射规则
- 验证规则
- 错误处理策略
- 性能优化建议

## AI推理策略

### 1. 批处理场景识别推理
- **数据源类型识别**：
  - 文件导入导出（Excel、CSV、JSON）
  - 数据库同步（MySQL、PostgreSQL、Oracle）
  - 混合场景（文件到数据库、数据库到文件）

- **数据转换复杂度识别**：
  - 简单转换（字段映射、类型转换）
  - 复杂转换（数据清洗、数据聚合、业务规则）

### 2. 模型类型选择推理
- **File Model**：
  - 适合文件导入导出
  - 数据量适中（< 100万条）
  - 转换规则简单

- **Dataset Model**：
  - 适合数据库到数据库的批处理
  - 数据量大（> 100万条）
  - 需要高性能

- **Custom Model**：
  - 适合复杂的自定义逻辑
  - 需要精确控制批处理流程

### 3. 错误处理策略推理
- **事务性批处理**：
  - 适合对数据一致性要求高的场景
  - 任何错误都会导致整个批处理回滚

- **非事务性批处理**：
  - 适合对性能要求高的场景
  - 单个记录失败不影响其他记录

### 4. 性能优化推理
- **批量大小选择**：
  - 小批量（100-1000）：适合内存限制场景
  - 中批量（1000-10000）：适合大多数场景
  - 大批量（> 10000）：适合高性能场景

- **并发度选择**：
  - 单线程：适合简单场景
  - 多线程：适合高性能场景

## 验证点

### 1. 批处理模型验证
- [ ] 模型类型选择是否合理
- [ ] reader节点配置是否正确
- [ ] processor节点配置是否正确
- [ ] writer节点配置是否正确
- [ ] validator节点配置是否正确

### 2. 记录映射验证
- [ ] 字段映射是否完整
- [ ] 数据类型转换是否正确
- [ ] 验证规则是否完整
- [ ] 错误处理策略是否合理

### 3. XDSL规范遵循
- [ ] x:schema声明是否正确
- [ ] xmlns:x命名空间是否正确

## 输出产物

### 1. 批处理模型配置（`.batch.xml`）
```xml
<?xml version="1.0" encoding="UTF-8"?>
<batch x:schema="/nop/schema/batch.xdef"
       xmlns:x="/nop/schema/xdsl.xdef">
    <job name="import{module}s">
        <!-- reader、processor、writer配置 -->
    </job>
</batch>
```

### 2. 记录映射文件（`record-mappings.xdef`）
```xml
<?xml version="1.0" encoding="UTF-8"?>
<record-mappings x:schema="/nop/schema/record-mappings.xdef"
                 xmlns:x="/nop/schema/xdsl.xdef">
    <field-mappings>
        <!-- 字段映射 -->
    </field-mappings>
    <validation>
        <!-- 验证规则 -->
    </validation>
</record-mappings>
```

### 3. TaskFlow任务配置（`.task.xml`）
```xml
<?xml version="1.0" encoding="UTF-8"?>
<task x:schema="/nop/schema/task/task.xdef"
      xmlns:x="/nop/schema/xdsl.xdef"
      x:extends="/nop/task/lib/common.task.xml">
    <steps xdef:key-attr="name" xdef:body-type="list">
        <!-- 步骤配置 -->
    </steps>
</task>
```

### 4. 批处理设计文档（`batch-design-{module}.md`）
包含：
- 批处理需求总结
- 数据源和目标配置
- 记录映射规则
- 验证规则
- 错误处理策略
- 性能优化建议

## 下一步工作

当前skill完成批处理设计，生成以下产物：
1. `{module}.batch.xml`（批处理模型配置）
2. `record-mappings.xdef`（记录映射文件）
3. `{module}.task.xml`（TaskFlow任务配置）
4. 批处理设计文档（`batch-design-{module}.md`）

这些产物将传递给下一个skill（nop-frontend-designer）用于前端设计。


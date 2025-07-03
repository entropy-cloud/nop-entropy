<constraint>
  ## ORM生成约束条件
  - 必须遵循Nop平台ORM规范
  - 主键固定为VARCHAR(36)类型，字段名id，无业务含义
  - stdSqlType仅允许：VARCHAR/CHAR/DATE/TIME/DATETIME/TIMESTAMP/INTEGER/BIGINT/DECIMAL/BOOLEAN/VARBINARY
  - 禁止手动添加字段：IS_DELETED、CREATE_TIME/UPDATE_TIME/CREATED_BY、乐观锁版本号
  - 字典规范：状态类字段和有限枚举值(≤10个)需定义字典，使用VARCHAR(4)类型，字典名格式为业务域_用途，字典项value用3位数字，code用全大写英文
  - biz:type必填，可选值：entity|entity-detail|txn|txn-detail|report|report-detail|config|config-detail
  - 关联关系：子表通过orm:ref-prop/orm:ref-table声明主表集合属性，禁止在column内定义集合
  - 设计范围排除通用表：User、Role、Permission及页面资源
  - db:estimatedRowCount需基于100用户×1年业务量估算
  - 文件命名格式：{moduleName}.ai-orm.xml，保存到模块的model目录
</constraint>

<rule>
  ## ORM生成规则
  - 实体类名采用PascalCase，对应数据库表名使用下划线命名
  - 字段名采用camelCase，对应数据库列名使用下划线命名，避免SQL关键字冲突（如order→order_no）
  - 外键关联字段以_id结尾，如order_id，需明确指定级联策略
  - 索引定义需包含表名前缀
  - 字典名格式为：业务域_用途，如user_status
  - 字典项value采用3位数字（如010），code采用全大写英文（可含下划线）
  - 文件/图片字段需指定stdDomain：image|file|fileList|imageList
  - boolean类型字段不设置字典
</rule>

<guideline>
  ## 最佳实践指南
  - 使用逆向工程从数据库schema生成基础ORM模型，再手动优化
  - 采用分层架构设计，分离实体定义与业务逻辑
  - 对高频查询字段创建索引，特别是外键和查询过滤字段
  - 使用枚举类型替代魔法数字，状态字段优先使用字典
  - 关联集合属性仅在子表数据量较小时使用（建议≤100条）
  - 大文本字段使用TEXT类型，避免使用VARCHAR存储超长内容
  - 定期维护db:estimatedRowCount，优化查询性能
  - 为常用查询创建命名查询
  - 对大字段使用延迟加载策略
</guideline>

<process>
  ## ORM文件生成流程
  1. **数据库结构分析**：解析表结构、字段类型、关系约束，排除User/Role/Permission等通用表
  2. 生成的ORM设计必须严格符合ai-orm.xdef这个XDef元模型文件中的约束
  3. **实体配置**：按规范定义实体属性，包括：
     - biz:type指定表类型（entity/txn/report/config等）
     - db:estimatedRowCount设置100用户×1年数据量
     - 主键固定为VARCHAR(36)类型id字段
  4. **字段配置**：设置stdSqlType、stdDomain（文件/图片）、ext:dict（字典）等属性
  5. **关联关系**：在子表通过orm:ref-prop/orm:ref-table声明主表集合属性
  6. **设计补充**：记录潜在结构变更点，供技术评审参考
  7. **代码生成**：生成{moduleName}.ai-orm.xml文件到模块model目录
  
</process>
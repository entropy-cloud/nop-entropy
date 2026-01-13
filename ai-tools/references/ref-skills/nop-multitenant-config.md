# nop-multitenant-config Skill

## Skill 概述

**名称**: nop-multitenant-config（多租户配置管理）

**定位**: 基于Nop平台的多租户架构，设计多租户配置管理和数据隔离，实现SaaS平台的多租户支持

**输入**:
1. 数据库模型（`{module}.orm.xlsx`）
2. 服务层定义（`{module}.xbiz.xml`）
3. 多租户需求（租户隔离级别、数据隔离策略、定制化需求）

**输出**:
1. 多租户配置（`{module}-multitenant.xml`）
2. 租户数据隔离策略文档（`tenant-isolation-guide.md`）
3. 租户定制化文档（`tenant-customization-guide.md`）
4. 租户管理接口文档（`tenant-management-api.md`）

**能力**:
- 设计多租户数据隔离策略（数据库隔离、Schema隔离、表隔离）
- 设计多租户配置管理
- 设计租户定制化方案（Delta定制）
- 设计租户数据迁移方案
- 设计租户升级和版本管理

**依赖**:
- Nop平台多租户文档（docs-for-ai/getting-started/multitenant/）
- Nop平台Delta机制（docs-for-ai/getting-started/xlang/dsl-delta.md）

## 核心原则

### 1. 多租户隔离策略
- **数据库隔离**：每个租户独立的数据库（最高隔离级别）
- **Schema隔离**：共享数据库，每个租户独立的Schema
- **表隔离**：共享数据库和Schema，通过tenantId字段隔离（Nop平台默认）

### 2. 租户定制化
- **Delta定制**：每个租户可以有独立的Delta配置
- **版本管理**：租户的Delta配置可以独立版本管理
- **热更新**：租户配置可以动态加载，无需重启应用

### 3. 租户配置管理
- **配置中心**：集中管理所有租户的配置
- **动态加载**：租户配置动态加载，支持热更新
- **版本管理**：租户配置支持版本管理和回滚

### 4. 数据迁移
- **租户数据迁移**：支持租户数据的导入导出
- **跨租户数据复制**：支持将一个租户的数据复制到另一个租户
- **数据校验**：迁移过程中进行数据校验

## 工作流程

### 阶段1：多租户需求分析

**步骤1.1：理解多租户需求**
```
分析多租户需求描述，理解：
- 租户数量和规模
- 租户隔离级别要求（数据库隔离、Schema隔离、表隔离）
- 租户定制化需求（功能定制、数据定制、界面定制）
- 租户升级策略（统一升级、分批升级）
- 数据迁移需求（新建租户、租户复制、租户合并）
```

**步骤1.2：分析应用架构**
```
分析应用架构，理解：
- 应用组件（Web、API、Database、Redis等）
- 数据模型（实体、关系、索引）
- 服务层接口（BizModel方法）
- 配置项（数据库配置、缓存配置、业务配置）
```

**步骤1.3：生成多租户方案**
```
生成多租户方案：
- 租户隔离策略
- 租户配置管理方案
- 租户定制化方案
- 租户数据迁移方案
```

### 阶段2：租户隔离策略设计

**步骤2.1：设计租户隔离策略**
```
根据需求选择租户隔离策略：

**选项1：表隔离（Nop平台默认）**
- 共享数据库和Schema
- 每个表添加tenantId字段
- 通过tenantId字段隔离数据
- 优点：资源利用率高、管理简单
- 缺点：隔离级别较低

**选项2：Schema隔离**
- 共享数据库，每个租户独立的Schema
- 通过Schema隔离数据
- 优点：隔离级别中等
- 缺点：跨租户数据共享困难

**选项3：数据库隔离**
- 每个租户独立的数据库
- 通过数据库隔离数据
- 优点：隔离级别最高
- 缺点：资源利用率低、管理复杂
```

**步骤2.2：设计租户标识**
```
设计租户标识机制：

**租户ID**：
- 租户ID是租户的全局唯一标识
- 租户ID可以是UUID、自增ID或业务编码
- 示例：`tenant_001`、`a1b2c3d4-e5f6-7890-abcd-ef1234567890`

**租户域名**：
- 每个租户可以有独立的域名
- 通过域名识别租户
- 示例：`tenant1.example.com`、`tenant2.example.com`

**租户子路径**：
- 每个租户可以有独立的子路径
- 通过子路径识别租户
- 示例：`example.com/tenant1`、`example.com/tenant2`
```

**步骤2.3：设计租户识别机制**
```java
// 租户上下文
public class TenantContext {
    private String tenantId;
    private String tenantName;
    private String tenantDomain;

    // 租户上下文感知
    public static TenantContext get() {
        return ThreadLocalContext.getTenantContext();
    }
}

// 租户识别拦截器
@Component
public class TenantInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request,
                           HttpServletResponse response,
                           Object handler) {
        // 通过域名识别租户
        String domain = request.getServerName();
        String tenantId = tenantService.getTenantIdByDomain(domain);
        TenantContext.setTenantId(tenantId);

        // 或者通过子路径识别租户
        String path = request.getRequestURI();
        if (path.startsWith("/tenant/")) {
            tenantId = path.split("/")[2];
            TenantContext.setTenantId(tenantId);
        }

        return true;
    }
}
```

### 阶段3：租户配置管理设计

**步骤3.1：设计租户配置模型**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<tenant-config x:schema="/nop/schema/tenant-config.xdef"
               xmlns:x="/nop/schema/xdsl.xdef">
    <tenants xdef:key-attr="id" xdef:body-type="list">
        <!-- 租户定义 -->
        <tenant id="tenant_001"
                name="租户A"
                domain="tenant1.example.com"
                status="ACTIVE">
            <!-- 数据库配置 -->
            <database>
                <type>mysql</type>
                <host>mysql-tenant1.default.svc.cluster.local</host>
                <port>3306</port>
                <database>tenant1_db</database>
                <username>${tenant1.db.username}</username>
                <password>${tenant1.db.password}</password>
            </database>

            <!-- Redis配置 -->
            <redis>
                <host>redis-tenant1.default.svc.cluster.local</host>
                <port>6379</port>
                <password>${tenant1.redis.password}</password>
            </redis>

            <!-- 业务配置 -->
            <config>
                <property name="custom.feature.enabled" value="true"/>
                <property name="custom.timeout" value="30000"/>
            </config>

            <!-- Delta定制 -->
            <delta x:extends="base.{module}.xbiz.xml">
                <!-- 租户特定的业务定制 -->
                <bizModel name="OrderServiceModel">
                    <!-- 添加租户特定的扩展点 -->
                    <extension name="customPrepareSave">
                        <impl>io.nop.tenant.tenant1.Tenant1OrderPrepareSave</impl>
                    </extension>
                </bizModel>
            </delta>
        </tenant>

        <tenant id="tenant_002"
                name="租户B"
                domain="tenant2.example.com"
                status="ACTIVE">
            <!-- 租户B的配置 -->
        </tenant>
    </tenants>

    <!-- 租户配置版本 -->
    <versions xdef:key-attr="version" xdef:body-type="list">
        <version number="1.0.0"
                timestamp="2024-01-01T00:00:00Z"
                description="初始版本">
            <!-- 版本1.0.0的配置 -->
        </version>
        <version number="1.1.0"
                timestamp="2024-02-01T00:00:00Z"
                description="新增自定义功能">
            <!-- 版本1.1.0的配置 -->
        </version>
    </versions>
</tenant-config>
```

**步骤3.2：设计租户配置加载**
```java
@Service
public class TenantConfigService {
    
    @Value("${tenant.config.path}")
    private String tenantConfigPath;

    private Map<String, TenantConfig> tenantConfigCache = new ConcurrentHashMap<>();

    /**
     * 加载租户配置
     */
    public TenantConfig loadTenantConfig(String tenantId) {
        // 从缓存获取
        TenantConfig config = tenantConfigCache.get(tenantId);
        if (config != null) {
            return config;
        }

        // 从文件加载
        String configPath = tenantConfigPath + "/" + tenantId + ".xml";
        TenantConfig loadedConfig = loadConfigFromFile(configPath);

        // 缓存配置
        tenantConfigCache.put(tenantId, loadedConfig);

        return loadedConfig;
    }

    /**
     * 热更新租户配置
     */
    public void reloadTenantConfig(String tenantId) {
        // 从缓存移除
        tenantConfigCache.remove(tenantId);

        // 重新加载
        TenantConfig config = loadTenantConfig(tenantId);
    }
}
```

### 阶段4：租户定制化设计

**步骤4.1：设计租户Delta定制**
```
设计租户Delta定制方案：

**租户定制化路径**：
```
/{module}/
  ├── _vfs/
  │   ├── _delta/
  │   │   ├── base.{module}.xbiz.xml          # 基础配置
  │   │   ├── tenant1.{module}.xbiz.xml      # 租户A的定制
  │   │   └── tenant2.{module}.xbiz.xml      # 租户B的定制
```

**租户Delta继承链**：
```
tenant1.xbiz.xml
  x:extends="base.xbiz.xml"
  x:post-extends="tenant1-custom.xbiz.xml"

tenant1-custom.xbiz.xml
  x:extends="base.xbiz.xml"
```

**租户Delta示例**：
```xml
<?xml version="1.0" encoding="UTF-8"?>
<bizModel x:schema="/nop/schema/xbiz.xdef"
          xmlns:x="/nop/schema/xdsl.xdef"
          x:extends="base.{module}.xbiz.xml">
    <!-- 覆盖默认行为 -->
    <service name="OrderServiceModel">
        <method name="save" x:override="replace">
            <impl>io.nop.tenant.tenant1.Tenant1OrderService.save</impl>
        </method>
    </service>
</bizModel>
```

**步骤4.2：设计租户定制化加载**
```java
@Service
public class TenantDeltaService {
    
    /**
     * 加载租户Delta配置
     */
    public DeltaModel loadTenantDelta(String tenantId) {
        String deltaPath = "/{module}/_delta/" + tenantId + ".{module}.xbiz.xml";
        IResource resource = VirtualFileSystem.instance().getResource(deltaPath);
        return DslModelHelper.loadDslModel(resource);
    }

    /**
     * 应用租户Delta
     */
    public BizModel applyTenantDelta(BizModel baseBizModel,
                                   DeltaModel tenantDelta) {
        // 使用Nop平台的Delta合并机制
        BizModel mergedModel = DeltaMerger.merge(baseBizModel, tenantDelta);
        return mergedModel;
    }
}
```

### 阶段5：租户数据迁移设计

**步骤5.1：设计租户数据迁移工具**
```java
@Service
public class TenantMigrationService {
    
    /**
     * 创建新租户
     */
    @Transactional
    public void createTenant(String tenantId, TenantConfig config) {
        // 1. 创建租户数据库/Schema
        createTenantDatabase(tenantId);

        // 2. 初始化租户数据表
        initializeTenantTables(tenantId);

        // 3. 导入租户初始数据
        importInitialData(tenantId);

        // 4. 创建租户配置
        saveTenantConfig(tenantId, config);
    }

    /**
     * 复制租户数据
     */
    @Transactional
    public void copyTenantData(String sourceTenantId,
                              String targetTenantId) {
        // 1. 验证源租户存在
        verifyTenantExists(sourceTenantId);

        // 2. 创建目标租户
        createTenant(targetTenantId, getTenantConfig(sourceTenantId));

        // 3. 复制租户数据
        copyTenantTables(sourceTenantId, targetTenantId);
    }

    /**
     * 导出租户数据
     */
    @Transactional(readOnly = true)
    public void exportTenantData(String tenantId, OutputStream outputStream) {
        // 1. 导出租户数据库
        exportTenantDatabase(tenantId, outputStream);

        // 2. 导出租户配置
        exportTenantConfig(tenantId, outputStream);

        // 3. 导出租户Delta
        exportTenantDelta(tenantId, outputStream);
    }

    /**
     * 导入租户数据
     */
    @Transactional
    public void importTenantData(String tenantId, InputStream inputStream) {
        // 1. 导入租户数据库
        importTenantDatabase(tenantId, inputStream);

        // 2. 导入租户配置
        importTenantConfig(tenantId, inputStream);

        // 3. 导入租户Delta
        importTenantDelta(tenantId, inputStream);
    }
}
```

**步骤5.2：设计租户数据迁移脚本**
```bash
#!/bin/bash

# create-tenant.sh - 创建新租户脚本

set -e

TENANT_ID="${1:-}"
TENANT_NAME="${2:-}"
TENANT_DOMAIN="${3:-}"

if [ -z "$TENANT_ID" ]; then
    echo "Usage: $0 <tenant-id> <tenant-name> <tenant-domain>"
    exit 1
fi

echo "Creating tenant: ${TENANT_ID}"

# 1. 创建租户数据库
mysql -u root -p${MYSQL_ROOT_PASSWORD} <<EOF
CREATE DATABASE ${TENANT_ID}_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON ${TENANT_ID}_db.* TO '${TENANT_ID}'@'%' IDENTIFIED BY '${TENANT_ID}_password';
FLUSH PRIVILEGES;
EOF

echo "Database created: ${TENANT_ID}_db"

# 2. 初始化数据表
mysql -u ${TENANT_ID} -p${TENANT_ID}_password ${TENANT_ID}_db < /app/schema/tenant-init.sql

echo "Tables initialized"

# 3. 导入初始数据
mysql -u ${TENANT_ID} -p${TENANT_ID}_password ${TENANT_ID}_db < /app/data/tenant-initial-data.sql

echo "Initial data imported"

# 4. 创建租户配置
cat > /app/config/${TENANT_ID}-config.xml <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<tenant-config x:schema="/nop/schema/tenant-config.xdef"
               xmlns:x="/nop/schema/xdsl.xdef">
    <tenants>
        <tenant id="${TENANT_ID}"
                name="${TENANT_NAME}"
                domain="${TENANT_DOMAIN}"
                status="ACTIVE">
            <database>
                <type>mysql</type>
                <host>mysql-${TENANT_ID}.default.svc.cluster.local</host>
                <port>3306</port>
                <database>${TENANT_ID}_db</database>
                <username>${TENANT_ID}</username>
                <password>${TENANT_ID}_password</password>
            </database>
        </tenant>
    </tenants>
</tenant-config>
EOF

echo "Tenant config created"

echo "Tenant ${TENANT_ID} created successfully!"
```

### 阶段6：租户管理接口设计

**步骤6.1：设计租户管理API**
```java
@BizModel("Tenant")
public class TenantServiceModel {
    
    /**
     * 创建租户
     */
    @BizMutation
    @Transactional
    public Tenant createTenant(@Name("tenantConfig") TenantConfig config) {
        // 1. 验证租户配置
        validateTenantConfig(config);

        // 2. 创建租户
        String tenantId = tenantMigrationService.createTenant(
            config.getId(), config);

        // 3. 返回租户信息
        return loadTenant(tenantId);
    }

    /**
     * 更新租户配置
     */
    @BizMutation
    @Transactional
    public Tenant updateTenant(@Name("tenantId") String tenantId,
                              @Name("config") TenantConfig config) {
        // 1. 验证租户存在
        verifyTenantExists(tenantId);

        // 2. 更新租户配置
        saveTenantConfig(tenantId, config);

        // 3. 重新加载租户配置
        tenantConfigService.reloadTenantConfig(tenantId);

        // 4. 返回租户信息
        return loadTenant(tenantId);
    }

    /**
     * 删除租户
     */
    @BizMutation
    @Transactional
    public void deleteTenant(@Name("tenantId") String tenantId) {
        // 1. 验证租户存在
        verifyTenantExists(tenantId);

        // 2. 删除租户数据
        tenantMigrationService.deleteTenant(tenantId);

        // 3. 删除租户配置
        deleteTenantConfig(tenantId);
    }

    /**
     * 查询租户
     */
    @BizQuery
    public Tenant getTenant(@Name("tenantId") String tenantId) {
        return loadTenant(tenantId);
    }

    /**
     * 查询所有租户
     */
    @BizQuery
    public List<Tenant> getAllTenants() {
        return tenantConfigService.getAllTenants();
    }
}
```

## AI推理策略

### 1. 租户隔离策略推理
- **租户数量判断**：
  - 租户数量少（< 10）：可以选择数据库隔离
  - 租户数量中（10-100）：可以选择Schema隔离
  - 租户数量多（> 100）：应该选择表隔离

- **安全要求判断**：
  - 高安全要求：选择数据库隔离
  - 中安全要求：选择Schema隔离
  - 低安全要求：选择表隔离

### 2. 租户定制化推理
- **定制化需求识别**：
  - 功能定制：通过Delta扩展BizModel方法
  - 数据定制：通过Delta扩展XMeta配置
  - 界面定制：通过Delta扩展View配置

- **定制化方案设计**：
  - 识别需要定制化的功能点
  - 设计Delta继承链
  - 设计Delta加载机制

### 3. 数据迁移推理
- **迁移场景识别**：
  - 新建租户：创建全新的租户
  - 租户复制：复制现有租户的数据
  - 租户合并：合并多个租户的数据

- **迁移策略设计**：
  - 识别需要迁移的数据表
  - 设计数据迁移顺序
  - 设计数据校验机制

## 验证点

### 1. 租户隔离策略验证
- [ ] 租户隔离级别是否合理
- [ ] 租户识别机制是否正确
- [ ] 数据隔离是否完整

### 2. 租户配置管理验证
- [ ] 租户配置模型是否完整
- [ ] 租户配置加载机制是否正确
- [ ] 租户配置热更新是否支持

### 3. 租户定制化验证
- [ ] 租户Delta定制是否可行
- [ ] 租户Delta继承链是否合理
- [ ] 租户Delta加载机制是否正确

### 4. 数据迁移验证
- [ ] 数据迁移脚本是否正确
- [ ] 数据校验机制是否完整
- [ ] 数据回滚机制是否支持

## 输出产物

### 1. 多租户配置（`{module}-multitenant.xml`）
```xml
<?xml version="1.0" encoding="UTF-8"?>
<tenant-config x:schema="/nop/schema/tenant-config.xdef"
               xmlns:x="/nop/schema/xdsl.xdef">
    <tenants>
        <!-- 租户定义 -->
    </tenants>
    <versions>
        <!-- 版本定义 -->
    </versions>
</tenant-config>
```

### 2. 租户数据隔离策略文档（`tenant-isolation-guide.md`）
包含：
- 租户隔离策略说明
- 租户识别机制说明
- 数据隔离方案说明

### 3. 租户定制化文档（`tenant-customization-guide.md`）
包含：
- 租户Delta定制说明
- Delta继承链设计
- Delta加载机制说明

### 4. 租户管理接口文档（`tenant-management-api.md`）
包含：
- 租户管理API列表
- API使用示例
- 数据迁移示例

## 下一步工作

当前skill完成多租户配置管理设计，生成以下产物：
1. 多租户配置（`{module}-multitenant.xml`）
2. 租户数据隔离策略文档（`tenant-isolation-guide.md`）
3. 租户定制化文档（`tenant-customization-guide.md`）
4. 租户管理接口文档（`tenant-management-api.md`）

所有6个阶段的skill已完成，可以调用代码生成器生成完整的SaaS平台！


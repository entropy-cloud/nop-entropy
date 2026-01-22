# 配置和依赖分析提示词设计

## 1. Maven/Gradle依赖分析

### 1.1 构建依赖分析
**提示词模板**：
```
请分析以下Maven/Gradle构建文件的依赖关系：

构建文件：
{pom.xml或build.gradle内容}

依赖锁定文件：
{dependency-lock文件}

请严格按照 `config/ai-prompt-models.xdef` 中定义的 `BuildDependencyAnalysis` 模型结构提供JSON格式的响应。

**重要说明：所有Nop平台相关知识必须参考 `docs-for-ai` 目录下的文档，确保XDef定义符合规范。**
```

## 返回格式

**XDef模型定义**：参考 `config/ai-prompt-models.xdef` 中的 `BuildDependencyAnalysis` 模型

**JSON格式示例**：
```json
{
  
  <xdef:prop name="dependencyCategories" type="object" mandatory="true" comment="依赖分类">
    <xdef:prop name="frameworkDependencies" type="array" item-type="object" comment="框架依赖">
      <xdef:prop name="name" type="string" mandatory="true" comment="依赖名称"/>
      <xdef:prop name="version" type="string" mandatory="true" comment="版本"/>
      <xdef:prop name="purpose" type="string" mandatory="true" comment="用途"/>
    </xdef:prop>
    <xdef:prop name="utilityDependencies" type="array" item-type="object" comment="工具依赖"/>
    <xdef:prop name="databaseDependencies" type="array" item-type="object" comment="数据库依赖"/>
    <xdef:prop name="securityDependencies" type="array" item-type="object" comment="安全依赖"/>
    <xdef:prop name="monitoringDependencies" type="array" item-type="object" comment="监控依赖"/>
  </xdef:prop>
  
  <xdef:prop name="versionManagement" type="object" mandatory="true" comment="版本管理">
    <xdef:prop name="versionConflicts" type="array" item-type="object" comment="版本冲突">
      <xdef:prop name="dependency" type="string" mandatory="true" comment="依赖名称"/>
      <xdef:prop name="conflictingVersions" type="array" item-type="string" comment="冲突版本"/>
      <xdef:prop name="resolution" type="string" comment="解决方案"/>
    </xdef:prop>
    <xdef:prop name="outdatedDependencies" type="array" item-type="object" comment="过时依赖">
      <xdef:prop name="dependency" type="string" mandatory="true" comment="依赖名称"/>
      <xdef:prop name="currentVersion" type="string" mandatory="true" comment="当前版本"/>
      <xdef:prop name="latestVersion" type="string" mandatory="true" comment="最新版本"/>
      <xdef:prop name="updateUrgency" type="string" mandatory="true" comment="更新紧急度"/>
    </xdef:prop>
  </xdef:prop>
  
  <xdef:prop name="securityAssessment" type="object" mandatory="true" comment="安全评估">
    <xdef:prop name="vulnerableDependencies" type="array" item-type="object" comment="有漏洞的依赖">
      <xdef:prop name="dependency" type="string" mandatory="true" comment="依赖名称"/>
      <xdef:prop name="vulnerability" type="string" mandatory="true" comment="漏洞描述"/>
      <xdef:prop name="severity" type="string" mandatory="true" comment="严重程度"/>
      <xdef:prop name="fixedVersion" type="string" comment="修复版本"/>
    </xdef:prop>
    <xdef:prop name="securityScore" type="float" mandatory="true" comment="安全评分：0.0-1.0"/>
  </xdef:prop>
  
  <xdef:prop name="optimizationSuggestions" type="object" mandatory="true" comment="优化建议">
    <xdef:prop name="unusedDependencies" type="array" item-type="string" comment="未使用的依赖"/>
    <xdef:prop name="duplicateDependencies" type="array" item-type="string" comment="重复依赖"/>
    <xdef:prop name="dependencyConsolidation" type="array" item-type="string" comment="依赖合并建议"/>
    <xdef:prop name="versionUpgradeRecommendations" type="array" item-type="string" 
                comment="版本升级建议"/>
  </xdef:prop>
</xdef:model>

请严格按照上述XDef定义的结构返回JSON数据。
```

## 2. Spring配置分析

### 2.1 Spring Bean配置分析
**提示词模板**：
```
请分析以下Spring配置：

配置类：
{@Configuration类代码}

Bean定义：
{@Bean方法代码}

属性配置：
{application.properties/yml}

请分析：
1. Bean的作用域和生命周期
2. 依赖注入方式
3. 条件化配置
4. 配置属性绑定
5. 自动配置机制
```

## 3. 数据库配置分析

### 3.1 数据源配置分析
**提示词模板**：
```
请分析以下数据库配置：

数据源配置：
{数据源配置代码}

连接池配置：
{连接池配置}

事务配置：
{事务管理器配置}

请分析：
1. 数据库连接管理
2. 连接池优化配置
3. 事务隔离级别
4. 数据源故障转移
5. 性能调优参数
```

## 4. 缓存配置分析

### 4.1 缓存策略分析
**提示词模板**：
```
请分析以下缓存配置：

缓存配置：
{缓存配置代码}

缓存注解：
{@Cacheable等注解}

缓存实现：
{Redis等配置}

请分析：
1. 缓存策略选择
2. 缓存失效策略
3. 缓存穿透防护
4. 缓存雪崩防护
5. 缓存一致性保证
```

## 5. 安全配置分析

### 5.1 安全框架配置分析
**提示词模板**：
```
请分析以下安全配置：

安全配置：
{Spring Security等配置}

认证配置：
{认证机制配置}

授权配置：
{权限控制配置}

请分析：
1. 认证机制安全性
2. 授权粒度控制
3. 会话管理安全
4. CSRF防护配置
5. 安全头配置
```

## 6. 日志配置分析

### 6.1 日志系统配置分析
**提示词模板**：
```
请分析以下日志配置：

日志配置：
{logback.xml等配置}

日志级别：
{各级别日志配置}

日志输出：
{输出格式和目的地}

请分析：
1. 日志级别合理性
2. 日志输出性能
3. 日志归档策略
4. 敏感信息过滤
5. 监控和告警集成
```

## 7. 外部服务配置分析

### 7.1 第三方服务集成分析
**提示词模板**：
```
请分析以下外部服务配置：

API配置：
{外部API配置}

消息队列配置：
{消息队列配置}

对象存储配置：
{存储服务配置}

请分析：
1. 服务端点配置
2. 超时和重试配置
3. 熔断器配置
4. 负载均衡策略
5. 服务降级方案
```

## 配置分析策略

### 配置完整性检查
1. **必要配置验证**：检查必需配置项是否完整
2. **配置默认值分析**：分析默认配置的合理性
3. **配置依赖检查**：检查配置项之间的依赖关系
4. **配置冲突检测**：检测相互冲突的配置项

### 配置安全性评估
1. **敏感信息检查**：检查配置中的敏感信息泄露风险
2. **安全配置验证**：验证安全相关配置的正确性
3. **权限配置评估**：评估权限配置的合理性
4. **审计配置检查**：检查审计日志配置

### 配置性能优化
1. **性能参数调优**：分析性能相关配置的优化空间
2. **资源限制配置**：检查资源限制配置的合理性
3. **连接池优化**：分析连接池配置的优化建议
4. **缓存配置优化**：提供缓存配置优化建议
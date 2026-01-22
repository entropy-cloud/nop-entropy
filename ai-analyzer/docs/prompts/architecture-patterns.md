# 架构模式识别提示词设计

## 1. 微服务架构识别

### 1.1 微服务边界分析
**提示词模板**：
```
请分析以下代码是否采用微服务架构，并识别服务边界：

项目结构：
{项目模块结构}

服务接口定义：
{服务接口代码}

服务实现：
{服务实现代码}

配置和部署文件：
{相关配置文件}

请严格按照 `config/ai-prompt-models.xdef` 中定义的 `MicroserviceArchitectureRecognition` 模型结构提供JSON格式的响应。

**重要说明：所有Nop平台相关知识必须参考 `docs-for-ai` 目录下的文档，确保XDef定义符合规范。**
```

## 返回格式

**XDef模型定义**：参考 `config/ai-prompt-models.xdef` 中的 `MicroserviceArchitectureRecognition` 模型

**JSON格式示例**：
```json
{
  "architectureType": {
    "isMicroservice": true,
    "confidence": 0.95,
    "architectureStyle": "microservices"
  },
  "serviceBoundaries": {
    "identifiedServices": [
      {
        "serviceName": "UserService",
        "boundedContext": "用户管理",
        "responsibility": "处理用户注册、登录、权限管理",
        "autonomyLevel": "high"
      }
    ],
    "boundaryClarity": "clear",
    "couplingAssessment": "loose"
  },
  "communicationPatterns": {
    "syncCommunication": [
      {
        "protocol": "REST",
        "endpoints": ["/api/users", "/api/auth"],
        "dataFormat": "JSON"
      }
    ],
    "asyncCommunication": [
      {
        "messageBroker": "Kafka",
        "eventTypes": ["user.created", "user.updated"],
        "processingPattern": "event-driven"
      }
    ]
  },
  "dataManagement": {
    "databasePerService": true,
    "dataConsistency": "eventual",
    "dataSynchronization": ["CDC", "event-driven"]
  },
  "deploymentAndScaling": {
    "independentDeployment": true,
    "scalingGranularity": "fine",
    "resiliencePatterns": ["circuit-breaker", "retry"]
  }
  
  <xdef:prop name="improvementSuggestions" type="object" mandatory="true" comment="改进建议">
    <xdef:prop name="boundaryOptimization" type="array" item-type="string" 
                comment="边界优化建议"/>
    <xdef:prop name="communicationOptimization" type="array" item-type="string" 
                comment="通信优化建议"/>
    <xdef:prop name="deploymentOptimization" type="array" item-type="string" 
                comment="部署优化建议"/>
  </xdef:prop>
</xdef:model>

请严格按照上述XDef定义的结构返回JSON数据。
```

**输出格式**：
```json
{
  "architectureType": {
    "isMicroservice": true,
    "confidence": 0.9,
    "architectureStyle": "microservices"
  },
  "serviceBoundaries": {
    "identifiedServices": [
      {
        "serviceName": "服务名称",
        "boundedContext": "限界上下文",
        "responsibility": "服务职责",
        "autonomyLevel": "high/medium/low"
      }
    ],
    "boundaryClarity": "clear/moderate/blurred",
    "couplingAssessment": "loose/moderate/tight"
  },
  "communicationPatterns": {
    "syncCommunication": [
      {
        "protocol": "REST/gRPC/etc",
        "endpoints": ["端点"],
        "dataFormat": "数据格式"
      }
    ],
    "asyncCommunication": [
      {
        "messageBroker": "消息代理",
        "eventTypes": ["事件类型"],
        "processingPattern": "处理模式"
      }
    ]
  },
  "dataManagement": {
    "databasePerService": true,
    "dataConsistency": "strong/eventual/none",
    "dataSynchronization": ["数据同步机制"]
  },
  "deploymentAndScaling": {
    "independentDeployment": true,
    "scalingGranularity": "fine/coarse",
    "resiliencePatterns": ["弹性模式"]
  },
  "improvementSuggestions": {
    "boundaryOptimization": ["边界优化建议"],
    "communicationOptimization": ["通信优化建议"],
    "deploymentOptimization": ["部署优化建议"]
  }
}
```

## 2. 分层架构识别

### 2.1 经典分层架构分析
**提示词模板**：
```
请分析以下代码是否采用分层架构：

项目包结构：
{包结构分析}

各层代码示例：
{表现层代码}
{业务层代码}
{数据层代码}

请识别：
1. 分层是否清晰
2. 层间依赖关系
3. 层间通信方式
4. 跨层调用模式
5. 分层合理性评估
```

## 3. 事件驱动架构识别

### 3.1 事件驱动模式分析
**提示词模板**：
```
请分析以下代码是否采用事件驱动架构：

事件定义：
{事件类代码}

事件发布：
{事件发布代码}

事件处理：
{事件处理器代码}

事件总线配置：
{事件总线配置}

请分析：
1. 事件定义和分类
2. 事件发布和订阅模式
3. 事件处理流程
4. 事件持久化和重试机制
5. 事件溯源支持
```

## 4. 六边形架构识别

### 4.1 端口适配器模式分析
**提示词模板**：
```
请分析以下代码是否采用六边形架构：

核心业务逻辑：
{领域模型代码}

端口定义：
{端口接口代码}

适配器实现：
{适配器代码}

请识别：
1. 核心业务逻辑隔离程度
2. 端口定义清晰度
3. 适配器实现方式
4. 依赖方向控制
5. 测试友好性评估
```

## 5. CQRS架构识别

### 5.1 命令查询职责分离分析
**提示词模板**：
```
请分析以下代码是否采用CQRS架构：

命令端代码：
{命令处理器代码}

查询端代码：
{查询处理器代码}

数据同步机制：
{数据同步代码}

请分析：
1. 命令和查询的分离程度
2. 读写模型的一致性保证
3. 事件溯源的应用
4. 查询性能优化措施
5. 复杂查询支持能力
```

## 6. 领域驱动设计识别

### 6.1 DDD模式分析
**提示词模板**：
```
请分析以下代码是否采用领域驱动设计：

领域模型：
{实体和值对象代码}

领域服务：
{领域服务代码}

聚合根：
{聚合根代码}

仓储接口：
{仓储接口代码}

请识别：
1. 领域模型丰富度
2. 聚合边界清晰度
3. 领域服务职责
4. 仓储模式应用
5. 限界上下文划分
```

## 7. 云原生架构识别

### 7.1 云原生特性分析
**提示词模板**：
```
请分析以下代码的云原生特性：

容器化配置：
{Dockerfile等}

服务发现：
{服务发现配置}

配置管理：
{配置管理代码}

弹性设计：
{熔断器、重试等}

请分析：
1. 容器化支持程度
2. 服务治理能力
3. 配置外部化程度
4. 弹性和容错设计
5. 可观测性支持
```

## 架构模式识别策略

### 多维度识别
1. **结构特征识别**：基于包结构、类关系识别架构特征
2. **行为模式识别**：基于调用链、数据流识别行为模式
3. **配置特征识别**：基于配置文件识别架构特征
4. **部署特征识别**：基于部署配置识别架构特征

### 模式组合识别
1. **混合模式识别**：识别多种架构模式的组合使用
2. **模式演变分析**：分析架构模式的演变过程
3. **模式冲突检测**：检测不同模式之间的冲突
4. **模式优化建议**：基于识别结果提供优化建议

### 架构质量评估
1. **可维护性评估**：评估架构的可维护性
2. **可扩展性评估**：评估架构的可扩展性
3. **性能影响评估**：评估架构对性能的影响
4. **团队适配性评估**：评估架构与团队能力的匹配度
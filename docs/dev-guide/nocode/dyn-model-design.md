# Nop平台动态模型加载与多租户支持架构设计

## 设计概览

Nop平台采用统一的动态模型加载机制，支持多租户环境下的资源隔离。核心设计基于"Loader as Generator"模式：
`Model = Loader(virtualPath × tenantId)`，通过标准化的Provider接口实现模型的按需加载和缓存管理。

## 核心架构

### 组件职责边界

| 组件                              | 职责                     | 关键方法                                                      |
|---------------------------------|------------------------|-----------------------------------------------------------|
| **ITenantResourceProvider**     | 虚拟路径 → 模型文件资源          | `getTenantResourceStore(tenantId)`                        |
| **ITenantBizModelProvider**     | 业务对象名 → XMeta/XBiz模型路径 | `getTenantBizModel(bizObjName)`, `getTenantBizObjNames()` |
| **IOrmModelProvider**           | 加载ORM模型                | `getOrmModel(persistEnv)`                                 |
| **IDynamicEntityModelProvider** | 实体名 → 实体模型             | `getDynamicEntityModel(entityName)`                       |
| **ITenantModuleDiscovery**      | 发现可用模块                 | `getEnabledTenantModules()`                               |

通过依赖注入获取这些接口的实现。

### 核心访问模式

```javascript
// 统一业务访问接口
IBizObject bizObj = bizObjectManager.getBizObject(bizObjName);
bizObj.invoke(actionName, request, selection, svcCtxt);
```

## 动态加载机制

### 加载流程链

```text
业务请求 → BizObjectManager → ITenantBizModelProvider → 模型路径 → ITenantResourceProvider → 资源内容
                              ↓
数据库访问 → OrmSessionFactory → IOrmModelProvider → LazyLoadOrmModel → entityName → IDynamicEntityModelProvider → IEntityModel
```

### 详细加载过程

1. **业务对象加载**

- `BizObjectManager` 根据 `bizObjName` 查找缓存
- 未命中时通过 `ITenantBizModelProvider` 加载 `GraphQLBizModel`
- 根据返回的 `bizPath` 和 `metaPath` 加载模型文件
- `ResourceComponentManager` 调用 `ITenantResourceProvider` 获取资源内容

2. **数据库访问**

- Action中通过 `OrmSessionFactory` 获取会话
- 通过 `IOrmModelProvider` 动态获取实体ORM模型
- 支持按模块粒度加载ORM模型

### 加载粒度策略

| 粒度级别      | 描述                | 触发条件                               |
|-----------|-------------------|------------------------------------|
| **资源文件级** | 单个IResource资源文件   | `IVirtualFileSystem.getResource()` |
| **业务对象级** | XMeta + XBiz模型文件对 | `BizObjectManager.getBizObject()`  |
| **模块级**   | 一组相关业务对象及ORM模型    | 访问模块内任何资源                          |

**关键特性**：

- 最小加载粒度为单个资源文件
- 所有加载操作均为Lazy模式
- 动态加载器作为最后回退方案

## 多租户支持

### 租户隔离架构

```shell
// 租户感知的缓存层次结构
TenantAwareResourceLoadingCache
├── 租户缓存容器(tenantCaches)
│   └──各租户独立的ResourceLoadingCache
└──

共享缓存(shareCache)
```

### 缓存管理

**统一缓存接口**：

```java
public interface ICacheManagement<K> {
  String getName();

  void remove(@Nonnull K key);

  void clear();

  default void clearForTenant(String tenantId) {
  }
}
```

**租户缓存路由逻辑**：

```java
protected ResourceLoadingCache<V> getCache(String path) {
  String tenantId = getTenantId();
  if (StringHelper.isEmpty(tenantId) || !ResourceTenantManager.supportTenant(path)) {
    return shareCache;  // 无租户或路径不支持租户
  }
  return tenantCaches.get(tenantId);  // 租户特定缓存
}
```

### 生命周期管理

- **租户会话期**：租户特定缓存自动维护
- **资源回收**：LRU策略自动清理最少使用条目
- **主动清理**：支持按租户粒度手动清理
- **状态特性**：所有缓存对象无状态，可随时重建

## 模块化架构

### 模块初始化

```text
// 无状态模块初始化流程
模块访问 → 执行模块初始化 → 生成模型文件 → 注册监听器
```

- 基于模板 `/nop/templates/dyn-module` 动态生成
- 完全无状态，无初始化顺序依赖
- 按需触发，避免不必要的初始化开销

### 模块间协作

| 协作方式     | 机制             | 特点        |
|----------|----------------|-----------|
| **服务调用** | 通过IBizObject接口 | 松耦合，运行时发现 |
| **事件通信** | 消息总线 + 监听器     | 解耦，异步处理   |
| **数据共享** | 通过标准业务接口       | 避免直接数据访问  |

**监听器初始化**：首次需要事件处理时，搜集所有启用模块的监听器，触发相关模块初始化。

## 系统约束与保证

### 资源发现约束

- **禁止大规模扫描**：仅两个接口支持遍历语义
- **精确路径访问**：严格按名称和路径获取资源
- **最小影响范围**：动态变更的影响范围可控

### 依赖管理

- **禁止循环依赖**：模型加载时检测并报错
- **显式依赖**：通过标准接口声明依赖关系

### 并发安全

- **无状态模型**：纯逻辑结构，无状态迁移问题
- **线程安全缓存**：LoadingCache + Double Check锁定
- **分布式同步**：消息总线通知缓存失效

## 错误处理策略

### 异常处理原则

- **快速失败**：资源未找到或加载失败立即抛出异常
- **统一异常**：遵循NopException规范
- **可扩展降级**：通过替换Loader实现定制策略

### 典型错误场景

| 场景      | 处理方式           | 恢复策略   |
|---------|----------------|--------|
| 模型文件未找到 | 抛出NopException | 检查路径配置 |
| 加载过程异常  | 直接抛出异常         | 修复底层问题 |
| 租户资源隔离  | 租户特定异常         | 检查租户权限 |

## 性能优化

### 缓存策略

- **多级缓存**：租户隔离 + 共享缓存
- **智能回收**：基于LRU的自动内存管理
- **分布式同步**：跨节点缓存一致性

### ORM优化

- **内存批量处理**：利用NopORM的batchLoad机制
- **懒加载**：按需加载关联实体
- **模块级优化**：ORM模型按模块整体加载

## 其他

ConfigProvider目前考虑不进行动态化。首先，动态配置已经通过Nacos配置中心支持。第二，每个租户如果有特殊配置应该使用另外一个配置获取接口，而不是使用全局的ConfigProvider。
全局的ConfigProvider在所有模型加载的时候都会被读取，如果混合使用，有可能全局模型动态加载的时候读取到了租户的配置。

## 设计理念总结

1. **关注点分离**：各Provider职责单一，边界清晰
2. **租户优先**：原生支持多租户隔离和资源管理
3. **按需加载**：Lazy加载策略，最小化资源占用
4. **无状态设计**：简化并发控制和分布式部署
5. **可扩展架构**：通过标准接口支持定制化实现

该架构在保持系统简单性的同时，为动态模型管理和多租户支持提供了完整的技术基础，特别适合需要高度灵活性和租户隔离的企业级应用场景。

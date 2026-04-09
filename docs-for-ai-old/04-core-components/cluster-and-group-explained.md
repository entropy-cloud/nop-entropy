# Nacos 服务分组与集群详解

## 核心概念对比

| 概念 | 作用域 | 隔离级别 | 主要用途 | 典型值 |
|------|--------|---------|---------|--------|
| **Namespace** | 最高级 | 环境隔离（强隔离） | 区分 dev/test/prod 环境 | `dev`, `test`, `prod` |
| **GroupName** | 服务级 | 逻辑隔离（中隔离） | 业务线/项目隔离 | `order-group`, `payment-group` |
| **ClusterName** | 实例级 | 物理分组（弱隔离） | 机房/地域分组 | `BJ-IDC`, `SH-IDC` |
| **Zone** | 实例元数据 | 路由优化 | 同机房优先调用 | `beijing`, `shanghai` |

## 架构层次关系

```
┌─────────────────────────────────────────────────────────┐
│ Namespace: prod (生产环境)                               │
│  ┌───────────────────────────────────────────────────┐  │
│  │ Group: order-group (订单业务组)                    │  │
│  │  ┌─────────────────────────────────────────────┐  │  │
│  │  │ Service: order-service                       │  │  │
│  │  │  ┌────────────────┐  ┌────────────────┐    │  │  │
│  │  │  │ Cluster: BJ-IDC │  │ Cluster: SH-IDC │    │  │  │
│  │  │  │  • 192.168.1.1  │  │  • 192.168.2.1  │    │  │  │
│  │  │  │  • 192.168.1.2  │  │  • 192.168.2.2  │    │  │  │
│  │  │  └────────────────┘  └────────────────┘    │  │  │
│  │  └─────────────────────────────────────────────┘  │  │
│  │  ┌─────────────────────────────────────────────┐  │  │
│  │  │ Service: payment-service                    │  │  │
│  │  │  • Cluster: BJ-IDC                          │  │  │
│  │  │  • Cluster: SH-IDC                          │  │  │
│  │  └─────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────┘  │
│                                                          │
│  ┌───────────────────────────────────────────────────┐  │
│  │ Group: user-group (用户业务组)                     │  │
│  │  └── Service: user-service                       │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

## 参数设置时机

### 1. 服务注册时（Provider 端）

**在 `AutoRegistration` 中设置：**

```yaml
# application.yaml
nop:
  application:
    name: order-service
    group: order-group          # 设置 GroupName
    
  cluster:
    name: BJ-IDC                # 设置 ClusterName
    
  server:
    port: 8080
```

**代码流程：**

```java
// 1. AutoRegistration 读取配置
AutoRegistration registration = new AutoRegistration();
registration.setServiceName("order-service");
registration.setGroupName("order-group");      // 从 nop.application.group 读取
registration.setClusterName("BJ-IDC");         // 从 nop.cluster.name 读取

// 2. 创建 ServiceInstance
ServiceInstance instance = registration.getServiceInstance();
instance.setServiceName("order-service");
instance.setGroupName("order-group");
instance.setClusterName("BJ-IDC");

// 3. 注册到 Nacos
nacosNamingService.registerInstance(instance);
// 实际调用: naming.registerInstance("order-service", "order-group", instance);
```

**NacosNamingService 会校验：**

```java
@Override
public void registerInstance(ServiceInstance instance) {
    // 强制校验：instance.groupName 必须等于 NacosNamingService 配置的 groupName
    Guard.checkEquals(instance.getGroupName(), groupName);
    
    Instance inst = toInstance(instance);
    getNamingService().registerInstance(
        instance.getServiceName(), 
        groupName,        // 使用 NacosNamingService 的 groupName
        inst              // inst 包含 clusterName
    );
}
```

### 2. 服务发现时（Consumer 端）

**获取服务实例列表：**

```java
// 1. 获取指定 Group 下的所有实例（包括所有 Cluster）
List<ServiceInstance> instances = nacosNamingService.getInstances("order-service");

// 实际调用链：
// naming.subscribe("order-service", "order-group", listener)
// 返回该 Group 下所有 Cluster 的实例
```

**返回的实例包含：**

```java
[
  ServiceInstance{serviceName="order-service", groupName="order-group", clusterName="BJ-IDC", addr="192.168.1.1"},
  ServiceInstance{serviceName="order-service", groupName="order-group", clusterName="BJ-IDC", addr="192.168.1.2"},
  ServiceInstance{serviceName="order-service", groupName="order-group", clusterName="SH-IDC", addr="192.168.2.1"},
  ServiceInstance{serviceName="order-service", groupName="order-group", clusterName="SH-IDC", addr="192.168.2.2"}
]
```

### 3. 服务调用时（负载均衡）

**通过 Filter 链过滤实例：**

```java
// LoadBalanceServerChooser.java
public ServiceInstance choose(List<ServiceInstance> instances, ApiRequest<?> request) {
    // 1. 应用所有 Filter
    for (IRequestServiceInstanceFilter filter : filters) {
        instances = filter.filter(instances, request);
    }
    
    // 2. 负载均衡选择一个实例
    return loadBalance.choose(instances, request);
}
```

**Filter 执行顺序（配置在 rpc-cluster-defaults.beans.xml）：**

```xml
<util:list id="nopRequestServiceInstanceFilters">
    <ref bean="nopServiceInstanceFilter_healthy"/>     <!-- 1. 过滤健康实例 -->
    <ref bean="nopServiceInstanceFilter_specific"/>    <!-- 2. 指定特定实例 -->
    <ref bean="nopServiceInstanceFilter_tag"/>         <!-- 3. 根据 tag 过滤 -->
    <ref bean="nopServiceInstanceFilter_zone"/>        <!-- 4. 优先同 zone/cluster -->
    <ref bean="nopServiceInstanceFilter_route"/>       <!-- 5. 路由规则 -->
</util:list>
```

**ZoneServiceInstanceFilter 的工作原理：**

```java
public class ZoneServiceInstanceFilter {
    @Override
    public List<ServiceInstance> filter(List<ServiceInstance> instances, ApiRequest<?> request) {
        // 1. 获取当前应用配置的 zone
        String myZone = getZone();  // 从 nop.rpc.cluster.prefer-zone 读取
        
        if (StringHelper.isEmpty(myZone)) {
            return instances;  // 未配置 zone，不过滤
        }
        
        // 2. 分组：同 zone vs 不同 zone
        List<ServiceInstance> sameZone = new ArrayList<>();
        List<ServiceInstance> otherZone = new ArrayList<>();
        
        for (ServiceInstance instance : instances) {
            String instanceZone = instance.getMetadata("zone");
            if (myZone.equals(instanceZone)) {
                sameZone.add(instance);  // 同 zone 优先
            } else {
                otherZone.add(instance);
            }
        }
        
        // 3. 如果有同 zone 实例，优先返回
        if (!sameZone.isEmpty()) {
            return sameZone;
        }
        
        // 4. 如果 force=true，没有同 zone 实例时返回空
        if (force) {
            return Collections.emptyList();
        }
        
        // 5. 否则返回其他 zone 的实例
        return otherZone;
    }
}
```

## 完整调用流程示例

### 场景：北京机房的 order-service 调用 payment-service

**配置：**

```yaml
# order-service (北京机房)
nop:
  application:
    name: order-service
    group: order-group
  cluster:
    name: BJ-IDC
  rpc:
    cluster:
      prefer-zone: beijing  # 优先调用北京机房的服务
```

```yaml
# payment-service (两个机房)
# 北京机房实例
nop:
  application:
    name: payment-service
    group: payment-group
  cluster:
    name: BJ-IDC
  server:
    port: 8081

# 上海机房实例
nop:
  application:
    name: payment-service
    group: payment-group
  cluster:
    name: SH-IDC
  server:
    port: 8082
```

**调用流程：**

```
1. order-service 想要调用 payment-service
   ↓
2. 服务发现：nacosNamingService.getInstances("payment-service")
   返回：
   - payment-service@BJ-IDC (192.168.1.10:8081)
   - payment-service@BJ-IDC (192.168.1.11:8081)
   - payment-service@SH-IDC (192.168.2.10:8082)
   - payment-service@SH-IDC (192.168.2.11:8082)
   ↓
3. Filter 链过滤：
   a) HealthyServiceInstanceFilter → 过滤掉不健康实例
   b) ZoneServiceInstanceFilter → 优先选择 beijing zone
      结果：只保留 BJ-IDC 的实例
      - payment-service@BJ-IDC (192.168.1.10:8081)
      - payment-service@BJ-IDC (192.168.1.11:8081)
   ↓
4. 负载均衡：RandomLoadBalance
   随机选择一个：192.168.1.10:8081
   ↓
5. 发起 RPC 调用
```

## 关键问题解答

### Q1: 分布式调用时是在同一分组内调用吗？

**是的，Group 是强隔离的。**

- 服务发现时，只能发现**同一个 Group** 下的服务
- NacosNamingService 会强制校验 `instance.groupName == 配置的 groupName`
- 跨 Group 调用需要显式指定目标 Group（通过配置多个 NamingService）

**示例：**

```java
// 错误：不同 Group 的服务无法互相发现
// order-group 下的 order-service
namingService.setGroupName("order-group");
List<ServiceInstance> instances = namingService.getInstances("payment-service");
// 返回空列表！因为 payment-service 在 payment-group

// 正确：跨 Group 调用需要显式配置
NamingService paymentGroupNaming = new NacosNamingService();
paymentGroupNaming.setGroupName("payment-group");
List<ServiceInstance> instances = paymentGroupNaming.getInstances("payment-service");
// 现在可以发现了
```

### Q2: 可以调用不同 Cluster 的服务实例吗？

**可以，Cluster 是弱隔离的，只是优先级不同。**

- 服务发现时，会返回**同一个 Group 下所有 Cluster** 的实例
- 通过 ZoneServiceInstanceFilter 实现**同 Cluster 优先调用**
- 如果同 Cluster 没有实例，会 fallback 到其他 Cluster

**配置示例：**

```yaml
# 严格模式：只调用同 zone 实例
nop:
  rpc:
    cluster:
      prefer-zone: beijing
      force-zone: true  # 如果没有北京实例，调用失败

# 宽松模式：优先同 zone，没有时调用其他 zone
nop:
  rpc:
    cluster:
      prefer-zone: beijing
      force-zone: false  # 默认值，没有北京实例时调用其他 zone
```

### Q3: 这些参数到底是什么时候用的？

| 参数 | 设置时机 | 使用时机 | 影响范围 |
|------|---------|---------|---------|
| **Namespace** | Nacos 服务端配置 | 环境隔离 | 完全隔离，不同 Namespace 无法互相发现 |
| **GroupName** | 服务注册时 | 服务发现时 | 强隔离，必须匹配才能发现服务 |
| **ClusterName** | 服务注册时 | 负载均衡时 | 弱隔离，影响优先级，不影响可达性 |
| **Zone** | 服务注册时（metadata） | 负载均衡时 | 路由优化，通过 Filter 实现 |

**时间线：**

```
┌─────────────────────────────────────────────────────────┐
│ 开发期：配置 Namespace、GroupName                         │
│   application.yaml:                                     │
│     nop.application.group: order-group                  │
│     spring.cloud.nacos.discovery.namespace: prod       │
└─────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│ 启动期：服务注册                                         │
│   AutoRegistration:                                     │
│     setGroupName("order-group")                         │
│     setClusterName("BJ-IDC")                            │
│     registerInstance(instance)                          │
└─────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│ 运行期：服务发现                                         │
│   NacosNamingService.getInstances("payment-service"):  │
│     subscribe("payment-service", "order-group")        │
│     返回该 Group 下所有 Cluster 的实例                    │
└─────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│ 调用期：负载均衡                                         │
│   ZoneServiceInstanceFilter:                            │
│     优先选择同 zone/cluster 的实例                        │
│   LoadBalance:                                          │
│     在过滤后的实例中选择一个                              │
└─────────────────────────────────────────────────────────┘
```

## 最佳实践

### 1. 环境隔离用 Namespace

```yaml
# 开发环境
spring.cloud.nacos.discovery.namespace: dev

# 测试环境
spring.cloud.nacos.discovery.namespace: test

# 生产环境
spring.cloud.nacos.discovery.namespace: prod
```

### 2. 业务隔离用 Group

```yaml
# 订单业务
nop.application.group: order-group

# 支付业务
nop.application.group: payment-group

# 用户业务
nop.application.group: user-group
```

### 3. 机房隔离用 Cluster

```yaml
# 北京机房
nop.cluster.name: BJ-IDC
nop.application.zone: beijing

# 上海机房
nop.cluster.name: SH-IDC
nop.application.zone: shanghai
```

### 4. 完整配置示例

```yaml
nop:
  application:
    name: order-service
    group: order-group              # 业务分组
    version: 1.0.0
    zone: beijing                   # 地域标识
    
  cluster:
    name: BJ-IDC                    # 机房标识
    registration:
      enabled: true
      auto-update: true
      auto-update-interval: 30s
      
  rpc:
    cluster:
      prefer-zone: beijing          # 优先调用北京机房
      force-zone: false             # 没有北京实例时调用其他机房
      
spring:
  cloud:
    nacos:
      discovery:
        namespace: prod             # 环境隔离
        server-addr: nacos.example.com:8848
        group: ${nop.application.group}  # 使用 Nop 的 group 配置
```

## 总结

1. **Namespace** = 环境隔离（dev/test/prod）- **强隔离，完全不可见**
2. **GroupName** = 业务分组（order-group/payment-group）- **强隔离，必须匹配**
3. **ClusterName** = 机房分组（BJ-IDC/SH-IDC）- **弱隔离，优先级不同**
4. **Zone** = 路由优化标识 - **通过 Filter 实现同机房优先调用**

**调用规则：**
- ✅ 同 Namespace + 同 Group + 不同 Cluster → **可以调用**（优先同 Cluster）
- ❌ 同 Namespace + 不同 Group → **无法调用**（强隔离）
- ❌ 不同 Namespace → **无法调用**（完全隔离）

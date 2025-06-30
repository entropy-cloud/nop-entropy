以下回答由deepwiki网站产生

# 1. NopORM中OrmEntity实体相比于POJO多维护了哪些信息？

## 1. 实体状态信息
nop实体维护了完整的生命周期状态，包括TRANSIENT（新建未关联）、SAVING（暂存待保存）、PROXY（代理未加载）、MANAGED（已管理）、MISSING（缺失）、DELETING（删除中）、DELETED（已删除）等7种状态。 [1](#0-0) 

## 2. 属性变化跟踪信息
实体内部记录了哪些属性已经设置了值、哪些属性已经被修改、修改前的值和修改后的值，支持在内存中实现回滚操作。 

## 3. 属性加载和初始化状态
通过IBitSet数据结构记录哪些属性被读取过(accessedProps)、哪些属性已经被设置了值(initedProps)，支持延迟加载功能。

## 4. 会话关联信息
维护与IOrmEntityEnhancer的关联，支持延迟加载、批量加载队列等ORM高级功能。

## 5. 实体模型元数据
每个实体都关联一个IEntityModel，包含了实体的结构定义、属性映射、关联关系等元数据信息。 

## 6. 属性ID映射机制
所有属性都分配了唯一的propId，支持通过`orm_propValue(int propId)`方法快速访问，避免反射开销。 

## 7. 各种控制标记
包括readonly（只读）、locked（锁定）、fullyLoaded（完全加载）、extDirty（扩展脏标记）、flushVisiting（刷新访问中）等状态标记。 

## 8. 关联对象管理
动态维护关联对象集合(refProps)，当属性值发生变化时自动清除相关联的对象，确保关联关系的一致性。 

## 9. 主键监听机制
支持主键初始化监听器，当主键尚未初始化时可以注册监听器，主键设置后自动触发回调。

## 10. 事务控制选项
提供了disableAutoStamp（禁用自动簿记）、disableLogicalDelete（禁用逻辑删除）、disableVersionCheckError（禁用版本检查错误）等事务控制选项。 

## Notes

nop平台明确放弃了POJO原则，认为在低代码环境中，实体类本身就是生成的代码，没有必要维持POJO的纯洁性。通过引入这些额外的状态信息，nop实体能够支持更高效的dirty检查、批量操作、延迟加载等ORM高级特性，同时避免了传统ORM框架中需要通过AOP或反射机制来实现状态管理的性能开销。  

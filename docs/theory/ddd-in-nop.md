# 万字长文讲清DDD在低代码平台中的最佳实践

在微服务的语境中引发了DDD的一轮文艺复兴，但是它的最佳实践能否被标准化为某种技术框架存在着很大的争论。在落地DDD的过程中，
很多设计会议都充斥着大量无谓的针对技术细节的争吵，谁也说服不了谁。

DDD的优势是能够更好的适应对象生态环境？还是更好的统一涉众的心智，将管理层面的阻力明确表达到技术世界？更好的实现技术与业务之间的映射？还是存在着某些数学层面可以得到证明的技术必然性？本文结合开源低代码平台Nop平台的技术实现方案，剖析一下DDD的技术内核。

面向对象中的状态封装概念没有那么重要。最混乱的代码实际上来源于封装后无法通过合法的手段进行操作，采用了hack方式进行绕过。

DDD只有在一种低代码平台或者模型驱动的框架中才能发挥最大的价值

平台与业务逻辑的共变演化。并不是平台规则固定，然后业务在既定规则下表达，而是可以不断定制新的规则。这种定制过程不需要通过平台厂商，业务层就可以自行定制。

## 设计空间的有效组织

1. 纵向分割
2. 横向分割
3. 时间与空间层面的组织：生命周期，服务单例

分割之后寻找相似性，然后抽象出公共部分。

1. 分而治之自然得到多层分解
2. 在边界设置瓶颈

结构化的模型而不是平面式的领域服务。
与依赖注入的关系：对象树与业务流分离

## 领域语言

* 一种语言可以看作是一种描述坐标系
* 通过坐标系定义一个领域语义空间
* 多个领域空间对应不同的坐标系，语义等价的部分需要粘合
* 业务表达独立于技术实现
* 价值链：有人提出以财务为引导
* 领域建模的价值是将领域名词转为领域模型，需要抽象和提炼，比如把柜员，大堂经理抽象成岗位，更加深入业务本质，而不仅仅停留在表面，是DDD最重要的成果沉淀。

## 聚合根
聚合不是为了实现事务

* 主分解维度，门面模式
  面向对象而不是面向DTO，不是面向ID
* 全局关系 + 内部的局部关系。不同比例尺的地图
* 信息尽在指尖
* 实体 + 延迟加载 + session缓存：结构聚合
* BizObject: 行为聚合
* GraphQL: 结构的选择和组合

聚合根首先是逻辑的聚合，BizModel的切片构造就是一种聚合方式，这种做法不是传统的面向对象中的继承方式和组合方式。另外聚合的对偶概念是动态切片，GraphQL恰好提供了一种在获取信息时的一种动态切片能力，使得我们可以在概念层面上维持一个庞大的聚合根概念，但是在实际层面每次都只加载少量数据。没有对偶的切片概念，本身聚合根就会非常臃肿，成为性能的拖累。

从坐标的观点理解聚合

取数据是为了用，纯粹的历史数据查询，走专门的查询服务

## 业务切面
同一个实体+不同的配置 产生不同名的对象，这些对象可以通过extends继承已有的部分进行差量修改。

## 贫血还是充血

* 均衡分工
* 不同的稳定性和信息范围
* 通用的CRUD问题 + 特定的业务问题。不同的问题子空间可以应用不同的解法，并不需要用一个统一的处理。BizService不包含CRUD。

## CQRS
* 是否要封装为明确的Command形式？ ApiRequest + 领域特定结构，通用结构的唯一目的是以标准方式引入扩展数据。但在实现层没有必要显式表达
并不需要显式的DomainCommand抽象
* 结合TCC，需要请求可以被持久化
* 边界层和内部模型层的表示是不同的：id => entity。对内视角与对外视角
* 领域事件与Command的区别. Event作为建模的切入点。状态的Delta

## 差量更新

* 领域事件作为Delta
* 可逆变换所要求的完备性
* 固定的通知与可变的命令
  已发生的事实（确定）与引发事件的操作（不确定）
* 多重宇宙：读写分离

## 逻辑编排

逻辑编排是面向函数的，本身每个单元都是函数。只是函数的实现有可能是触发服务对象上的方法而已，这是一个次级问题

从domain的概念上说DDD也应该包含编排方面的内容，编排中涉及到的可以是基于domain术语的。但是传统的DDD都是面向数据模型，编排是尚未涉及到的内容

面向领域的编排要求对领域逻辑进行抽象，将它对应的具体的技术实体，然后通过各种手段来操纵。传统上数据建模比较成熟，但是逻辑建模就很薄弱，特别是建模之后如何使用建模结果缺乏系统化手段。

## 实现技术

sqlalchemy的延迟加载属性如果要改成eager加载，需要在获取数据时指定。

通过深度实现抽象： A生成B，然后再运行B，比同时在运行时处理多个可能的B要简单。多阶段，每个阶段只处理部分复杂性，而且这个复杂性不会被传递到下一阶段，而是被逐步消减掉。


## 总结

1. 对象化是很自然的抽象手段
2. 聚合根源于聚合，归于选择
3. IoC容器在时间维度上管理对象生命周期
4. 信息的合理分布可以最小化扰动影响范围
5. 面向差量重构一切理解

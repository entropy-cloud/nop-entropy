# 为什么在数学的意义上GraphQL严格的优于REST？

GraphQL是Facebook公司所提出的一种用于API的查询语言，很多人把它看作是REST的替代品，但也有很多人认为GraphQL比REST复杂得多，且没有明显收益。
GraphQL到底具有哪些独特的能力超越了REST的能力范围？有没有一种客观的、严谨的评判标准可以帮助我们做出判断？

Nop平台中通过严密的数学推理，对于GraphQL的定位进行了重新的诠释，获得了一些新的设计思想和技术实现方案。在这种诠释下，NopGraphQL引擎实现了对REST的全面超越，
可以说在数学的意义上GraphQL严格的优于REST。

> 关于NopGraphQL的介绍，可以参见我此前的文章 [低代码平台中的GraphQL引擎](https://zhuanlan.zhihu.com/p/589565334)

简单的说，**GraphQL可以看作是REST的面向pull mode的改进**，它在某种程度上是**反转了信息流向**。

```graphql
query{
    NopAuthDept__findAll{
        name, status,children {name, status}
    }
}
```

等价于 `/r/NopAuthDept__findAll?@selection=name,status,children{name,status}`

经过**数学意义上的等价变换（形式变换）**，我们可以清晰的看出，GraphQL不过是在REST的基础上补充了一个标准化的`@selection`参数，通过它可以对返回结果进行选择性拉取。

传统的REST相当于是后台推送(push)所有信息到前台，前台无法精细的参与这个信息生产和传递过程。在NopGraphQL的实现中，前台如果不传递`@selection`参数，就自动退化为传统REST模型，返回所有非lazy的字段。lazy的字段需要显式指定才会返回到前台。

GraphQL在Nop平台的架构中并不是与REST平级的一种传输协议，而是一种**通用的信息分解组合机制**，它可以帮助我们对系统的信息结构进行更有效的组织。

NopGraphQL的定位是作为后端服务函数的一种通用分解、派发机制，所以同一个服务函数，可以同时发布为REST服务、GraphQL服务、GRpc服务、Kafka消息服务、Batch批处理服务等。简单的说，任何一种接收Request Message，返回Response Message的场景都可以直接对接到NopGraphQL引擎，将它作为自己的实现机制。

使用GraphQL相比于传统的REST有如下优势:

## 1. 根据请求数据自动裁剪数据加载范围，实现性能优化

```
  @BizQuery
  public PageBean<NopAuthUser> findPage(@Name("query") QueryBean query, FieldSelectionBean selection, IServiceContext context){
          PageBean<NopAuthUser> pageBean = new PageBean();
          if(selection != null && !selection.hasField("total")){
               long total = dao.countByQuery(query);
              pageBean.setTotal(total);
          }
          ....
  }
```

如果前端不要求返回total，则可以跳过total属性的计算

## 2. 由引擎自动完成DTO适配，极大提升数据模型的可组合性

传统的REST服务中服务函数是直接返回DTO对象，所有DTO对象中的属性都必须在该服务函数中负责加载。比如说NopAuthUser实体上增加了一个虚拟的roles字段，则在DTO上要增加这个字段，而且所有返回NopAuthUserDTO的地方都需要补充roles字段的加载逻辑。
而在NopGraphQL引擎中，服务函数返回的对象并不会直接被序列化为JSON返回到前台，而是交由GraphQL引擎做进一步的DataLoader加工处理后得到最终的返回数据。因此它可以极大提升后台数据模型的可组合性。这里的可组合性指的是我们分别实现了A和B，就自动得到了所有A\*B的结果，无需手动进行组合处理。

```java

 @BizQuery
 public List<NopAuthUser> findList(){
    ...
 }

 @BizQuery
 public NopAuthUser get(@Name("id") String id){
     ...
 }

 @BizLoader
 public List<String> roles(@ContextSource NopAuthUser user){
    ...
 }
```

在上面的示例中，findList和get函数只需要知道如何加载NopAuthUser对象的知识，不需要知道NopAuthUser如何与NopAuthRole对象进行关联的知识。服务函数可以直接返回实体对象，并不需要人工翻译为DTO对象。

当我们通过`@BizLoader`机制为NopAuthUser类型增加了动态属性roles之后，所有返回结果中涉及到NopAuthUser的地方都自动增加了roles属性。DataLoader所提供的知识通过NopGraphQL引擎的作用，自动的与我们手工编写的get/findList等函数组合在一起。

在NopGraphQL中，我们还可以通过额外的xmeta元模型文件来独立的控制每个字段的权限、转换和验证逻辑等，从而简化以及标准化服务函数的实现。

NopORM返回的实体对象上支持实体级别的缓存机制。

```javascript
@BizLoader
public Strin getFieldA(@ContextSOurce NopAuthUser entity){
  return ((ExtFields)entity.computeIfAbsent("extFields", k-> loadExtFields(entity))).getFieldA();
}
```

比如说我们要一次性计算多个扩展字段的值，就可以统一加载，然后每个BizLoader都返回缓存的单个属性。GraphQL中并没有一次性返回多个字段的机制。但是通过延迟加载我们可以避免多次调用loader。

## 3. GraphQL的选择能力是DDD中聚合根概念的有益补充

DDD（Domain Driven Design，领域驱动设计）中一个非常关键、对于信息空间规划至关重要的设计就是所谓的聚合根概念（AggregateRoot）。聚合根相当于是信息空间中的一种核心节点，当我们要在信息空间中游历的时候，我们不是在所有的信息节点之间都建立一对一的连接线路，而是在少量核心节点上建立直连通路，然后再通过聚合根访问它下属的子信息节点。

聚合根是在形式层面对信息的一种聚合组织方式。这种形式上的聚合降低了我们的认知和使用成本，但是它对性能也会产生一种负面影响。聚合根表达了我们通过根对象可以访问到的所有信息，如果每次获取到根对象的时候都自动加载所有信息，必然会导致不必要的资源浪费。GraphQL的选择能力正是与聚合能力相对偶的一种能力（聚合和选择作为对偶操作我们总是应该配对设计），它使得我们可以从形式上巨大无比的一个信息结构中选择性的截取到我们所需要的信息切片，恰好满足我们的业务要求。

## 4. GraphQL在语义层面明确区分了query和mutation，更符合REST表述性状态转义设计中的原始意图。

REST调用中虽然规定了GET和POST两种方法，但是因为实现层面的限制（GET方法不支持通过http body传递数据，URL长度有限制且有安全性问题），很多时候我们并不能通过GET/POST来精确的区分服务函数的语义。

在NopGraphQL中，我们约定了query没有副作用，不会修改数据库，而mutation具有副作用，需要考虑事务管理等，因此在NopGraphQL框架中，我们并不需要手动为每个服务函数增加 `@Transactional`事务注解，而是统一为所有mutation操作建立事务环境。通过实现层面的优化，如果mutation中实际上没有访问数据库，则NopGraphQL框架并不会真的去获取数据库连接。

NopGraphQL引擎将后台服务函数的执行分解为两个阶段：

1.  执行服务函数，如果是mutation，会自动建立事务环境
2.  对服务函数返回的结果对象进行再加工，通过DataLoader进行数据转换和剪裁。

第一个阶段执行完毕之后就会自动关闭事务，此时一些比较消耗资源的数据加载工作并没有开始执行。在第二阶段执行时会关闭事务，并且将OrmSession置于readonly状态，如果误操作修改了实体数据，会自动抛出异常。通过这种方式，我们可以减少事务打开时间和数据库连接的占用时间。

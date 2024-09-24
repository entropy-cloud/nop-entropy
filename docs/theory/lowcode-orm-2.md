# 低代码平台需要什么样的ORM引擎?(2)

[书接上回](https://zhuanlan.zhihu.com/p/543252423)。在上一篇文章中，我对ORM的设计进行了初步的理论分析，并提出了SQL语言的最小延拓：EQL对象查询语言，然后在EQL语言的基础上实现了多种用户可定制的动态存储结构。在本文中，我将首先介绍NopOrm引擎中所做的一些功能取舍，以及在这种功能取舍的情况下如何解决ORM常见的性能问题。然后我将介绍如何实现可定制的Dialect，如何用200行代码实现类似MyBatis的SQL管理功能，以及如何实现GraphQL集成和可视化集成等。

## 四. Less is More

坊间一直存在一种传言：Hibernate入门容易，精通难。但是，什么技术不是这样呢？Hibernate的问题在于它似乎提供了太多的选择，而且总是迫使我们在不停的选择。比如说，将关联表映射为集合对象，Set/Bag/List/Collection/Map等众多选项哪个最好？delete/update/insert操作要不要cascade到关联实体？从集合中删除是否意味着也要从数据库中删除？关联对象应该eager还是lazy加载？这种选择上的自由会让强迫症和选择困难症患者非常的纠结。如果选错了怎么办？如果改变选择影响别人的代码怎么办？如果后悔了怎么办？

如果我们总是在不断的做出选择，但是每个选择都产生了不可逆的后果，那么丰富的选择带给我们的多半不是happy life，而是深深的悔恨。

NopOrm引擎大幅削减了程序员的决策点，**将应用层可以自行完成的封装排除到引擎内核之外**。比如说，我们有什么必要一定要把关联表映射为List，将index字段映射到列表元素的下标，同时还要补充一堆List相关的的HQL特殊查询语法？

### 4.1 ORM自动映射

NopOrm引擎的第一个设计决策就是：**不需要补充任何额外的设计决策就可以把数据库设计的物理模型自动映射为Java实体模型**。这里没有以数据库设计的逻辑模型为基础，因为从逻辑模型到物理模型，路径是不确定的，必须补充额外的信息，而从物理模型出发，可以自动完成，不需要再做出任何选择。

> 物理模型本身已经是各种设计决策最终综合作用的结果，而且将在未来稳定存在。如果在ORM映射中再以逻辑模型为基础，则相当于是重复表达选择过程。

具体来说，NopOrm将每一个数据库字段都映射为实体上的一个java属性（与MyBatis相同），同时每一个外键关联再映射为一个Lazy加载的实体对象。也就是说，同一个字段有可能会被映射为多个属性，一个原子字段属性加上一个（或多个）关联对象属性，它们之间会自动保持同步。如果更新原子字段属性，则会自动将关联对象设置为null，当下次访问关联对象时再从session中查找。

如果外键关联明确标记了要生成一对多集合属性，则会自动生成一个Set类型的属性（不提供不同集合类型的选择）。按照ORM的基本原理，同一个Session中对象指针保持唯一性，因此可以推论出它们自然构成一个Set集合，而如果采用其他类型的属性映射都必然需要增加额外的假设。因为采用指针相等，我们也不需要覆写实体对象的equals方法。

只有当我们明确需要使用Component/Computed/Alias的时候，我们才会增加对应配置，而这些配置是增量表达的，即它们的存在与否不会影响此前所有的字段和关联映射，不会影响到数据库结构定义本身。**因为NopOrm的实现符合可逆计算原理，所以这些增量的配置可以在delta文件中表达，而不用修改原始的模型设计文件**。

### 4.2 再见, POJO

NopOrm的第二个重要的设计决策是：**放弃POJO的假定**。POJO(Plain Old Java Object)对于当年的Hibernate来说非常的重要，因为它帮助Hibernate摆脱了EJB(Enterprise Java Bean)的容器环境，并最终摧毁了EJB的生态系统。但是POJO是不足以完成工作的，Hibernate必须通过AOP(Aspect Oriented Progamming)技术对Java实体对象进行增强，为它增加附加功能，同时在内存维持一个EntityEntryMap，用于管理附加的状态数据。

在低代码的应用背景下，实体类本身是代码生成的，而AOP本质上也是一种代码生成的手段（一般会采用运行期的字节码生成机制）。既然如此，**一次性把最终代码生成好不就行了吗，有必要拆分成两个不同的生成阶段吗？**

随着技术的发展，POJO的隐性成本还在不断的增加，导致使用它的理由持续被削弱。

1. AOP的字节码生成速度很慢，而且不好进行代码调试。

2. 使用POJO需要用到反射机制，性能上有较大损耗，而且GraalVM等原生Java技术需要尽量回避使用反射机制。

3. POJO对象无法维护比较复杂的实体持久化状态，导致无法进行有效的优化。例如，Hibernate无法通过简单的dirty flag来识别实体是否被修改，被迫在内存中维护了一个对象数据的副本，每次session flush的时候都需要遍历对象，逐个比较对象属性数据和副本数据是否一致。这影响Hibernate的性能，并消耗了更多的内存。

4. 为了实现一些必须的业务功能，我们往往需要选择实体类从一个公共的基类继承，这实际上是破坏了对象的POJO假定。例如为实体增加动态属性映射，自动记录实体修改前和修改后的字段数据等都需要实体的基类提供一系列的成员变量和方法。

5. 集合属性的实现对性能不友好且容易发生误用。对象初始化的时候集合属性一般为HashSet类型，而当对象与session建立关联之后，集合属性会被自动替换为ORM引擎内部的PersistSet实现类，相当于是要新建一个集合来替换原有的POJO的集合。同时，按照ORM的实现原理，集合对象为了支持延迟加载，它必然是和某个实体绑定的，因此不允许将一个实体的集合属性直接赋值给另外一个实体的集合属性。但是POJO实现了get/set方法，很容易发生误用。例如  otherEntity.setChildren(myEntity.getChildren()) 这种调用是错误的，myEntity.getChildren()返回的集合是与mgyEntity绑定的，它无法再成为otherEntity的属性。

NopOrm中所有实体类都要求实现IOrmEntity接口，并提供了一个缺省实现OrmEntity

[IOrmEntity](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-orm/src/main/java/io/nop/orm/IOrmEntity.java)

每一个column模型都具有一个唯一的propId属性，通过IOrmEntity.orm\_propValue(int propId)方法可以代替反射机制来存取属性数据。

所有集合属性都是OrmEntitySet类型，它实现了IOrmEntitySet接口。代码生成时实体的集合属性只会生成get方法，并不会生成set方法，从而杜绝了误用的可能。

[IOrmEntitySet](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-orm/src/main/java/io/nop/orm/IOrmEntitySet.java)

自动生成代码时对每个实体会生成两个Java类，例如SimsExam和\_SimsExam，\_SimsExam类每次都会被自动覆盖，而SimsExam类如果已经存在则会保持原有内容，因此手工调整的代码可以写在SimsExam类中。参见

[\_SimsExam](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-orm/src/test/java/io/nop/app/_gen/_SimsExam.java)

[SimsExam](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-orm/src/test/java/io/nop/app/SimsExam.java)

### 4.3 Lazy and Cascade all

NopOrm中**所有关联实体和关联集合都是延迟加载的，同时也不支持类继承机制**。这一设计决策极大简化了ORM引擎内部的实现，并为统一的批量加载优化奠定了基础。

实体模型的column定义中可以增加lazy设置，指定该数据列延迟加载。缺省情况下，实体第一次加载时只加载所有eager的属性，lazy属性只有具体被使用时才会被加载。同时，引擎提供了批量预加载机制，可以明确指定一次性要加载哪些数据列，从而避免多次数据库访问。

EQL语法中没有提供eager fetch语法支持，因为使用eager fetch会导致SQL语句与想象中不一致。例如如果主表加载时通过join语句同时加载子表记录信息，则会导致返回结果集的条目数增加，且返回大量冗余数据，本身对性能并不友好。在NopOrm中统一采用BatchLoadQueue提供的批量加载队列来实现性能优化。

Hibernate中cascade是由action动作触发的，例如调用session.save的时候会cascade执行关联属性的save动作。它的这个设计最初的目的可能是了性能优化，例如某些属性不需要cascade，会被自动跳过等。但是基于动作触发会导致意料之外的结果，比如在save之后如果再次修改实体可能会生成两条sql语句：一条insert，一条update，原本只需要生成一条insert语句就可以了。

Hibernate的FlushMode设置也容易产生令人迷惑的结果。FlushMode缺省设置为auto，由hibernate自行判断是否要主动刷新数据库，导致Java代码层面一些微妙而等价的逻辑调整会误导hibernate错误判断为需要刷新数据库，从而发出大量sql调用，产生比较严重的性能问题。

NopOrm的设计思想是**彻底的lazy**，因此它取消了FlushMode概念，仅在明确调用session.flush()的时候才会刷新数据库，而结合OrmTemplate模式，由模板方法在事务提交前负责调用session.flush()，从而在概念层面提高了ORM引擎执行结果的可预测性。

NopOrm采用状态驱动的cascade设计，即每次操作时不执行cascade，仅在session.flush时对所有实体都执行一次cascade操作。同时利用dirty flag标识来实现剪枝优化，如果某个类型的所有实体都没有被修改，则该类型对应的dirty标识为false，这个类型的所有实例会自动跳过flush操作。如果整个session中所有实体都没有被修改，则全局的dirty标识为false，整个session.flush()操作会被跳过。

Hibernate基于动作触发cascade还有一个副作用，即具体SQL语句的执行顺序难以被精确控制。而在NopOrm中，flush产生的动作会被缓存到actionQueue队列中，然后统一按照数据库表的拓扑依赖顺序进行排序后再执行，从而确保总是按照确定的表顺序进行数据库修改，可以在一定程度上避免数据库死锁的发生。

> 死锁产生的原因一般是线程A先修改表A，然后再修改表B，而线程B先修改表B，再修改表A。按照确定顺序执行数据库更新语句相当于是起到了锁排序的作用。

### 五. More is Better

NopOrm放弃了Hibernate中大量的功能特性，但同时它又提供了很多Hibernate所缺乏的，而在一般业务开发中又非常常见、往往需要不少开发量的功能特性。区别在于，这些特性全部都是可选特性，无论是否启用它们对已经实现的其他功能都不会造成任何影响。

### 5.1 Good parts of Hibernate

NopOrm继承了Hibernate和Spring框架中一些非常优秀的设计：

1. **二级缓存和查询缓存**：缺省情况下就限制了缓存大小，避免内存溢出。

2. **复合主键支持**：在业务系统开发中很难完全避免复合主键。NopOrm内置复合主键类OrmCompositePk，并自动生成Builder辅助函数，自动实现String与OrmCompositePk之间的转换，从而简化了复合主键的使用。

3. **主键生成器**：只要column模型上标记了seq标签，就可以在java代码中自动生成主键。如果实体上已经设置了主键，则以用户设置的值为准。与Hibernate不同的是，NopOrm使用全局统一的SequenceGenerator.generate(entityName)来生成主键，便于在运行期动态调整主键生成策略。NopOrm放弃了数据库自增主键的支持，因为这个特性很多数据库不支持，在分布式环境下也存在问题。

4. **JDBC Batch**：自动合并数据库更新语句，减少数据库交互次数。调试模式下会打印合并前SQL语句的具体参数值，便于出错时诊断问题。

5. **乐观锁**：通过update xxx set version=version+1 where version= :curVersion的方式更新数据库，从而避免并发修改冲突

6. **模板方法模式**：改进了JdbcTemplate/TransactionManager/OrmTemplate的配合方式，减少了冗余的封装转换，并增加了异步处理支持，可以在异步环境中使用OrmSession。

7. **Interceptor**：可以通过OrmInterceptor的preSave/preUpdate/preDelete等方法拦截ORM引擎内部针对单实体的操作，从而实现类似数据库中触发器的功能。

8. **分页**：借助Dialect统一不同数据库的分页机制。同时为EQL语法增加了类似MySQL的offset/limit语法支持。

9. **SQL兼容性**：借助Dialect实现跨数据库的SQL语法兼容性转换，包括语法格式与SQL函数的翻译等。

### 5.2 更懂需求的ORM

一些常见的业务需求借助ORM引擎可以很容易的实现，因此NopOrm为它们提供了开箱即用的支持，不需要再安装额外的插件。

1. **多租户**：为启用租户的表增加tenantId过滤条件，并禁止跨租户访问数据

2. **分库分表**：通过IShardSelector动态选择分库分表

3. **逻辑删除**：将delete操作转换为设置delFlag=1的修改操作，并在一般查询语句中自动增加增加delFlag=0的过滤条件

4. **时间戳**：自动记录修改人、修改时间等操作历史信息

5. **修改日志**：通过OrmInterceptor拦截实体修改操作，可以获取到实体被修改前以及修改后的字段值信息，并记录到单独的修改日志表中。

6. **历史表支持**：为记录表增加revType/beginVer/endVer字段，为每个记录分配一个起始版本号和结束版本号，修改记录被转化为新增一条记录，并设置上一条记录的结束版本号为最新记录的起始版本号。在一般查询语句中自动增加过滤条件，只查找最新版本的记录。

7. **字段加密**：在column模型上增加enc标签表示该字段需要进行加密存储。此时系统会使用定制的IDataParameterBinder来读取数据库字段值，从而实现以加密形式保存到数据库中，而以解密形式存放在java属性中。EQL解析器通过语法分析可以获知参数类型，从而透明的使用encode binder来对SQL语句的参数进行加解密。

8. **敏感数据掩码**：用户的卡号和身份证号等敏感信息字段可以增加mask标签，从而在系统内部打印日志时自动对该字段值进行掩码处理，避免泄露到日志文件中。

9. **组件逻辑复用**：一组相关的字段可能组成一个可以复用的组件，通过OrmComponent机制可以对这些逻辑进行复用。例如，数据库中的Decimal类型精度必须事先指定，但是客户要求必须按照输入时指定的精度来进行显示和计算，这要求我们在记录表中增加一个VALUE\_SCALE字段来保留精度信息，但是当我们从数据库中取出值的时候我们又希望直接得到一个scale已经被设置为指定值的BigDecimal。NopOrm提供了一个FloatingScaleDecimal组件来完成这件工作。对于附件、附件列表等具有复杂关联逻辑的字段可以采用类似的方式进行封装。

   [FloatingScaleDecimal](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-orm/src/main/java/io/nop/orm/support/FloatingScaleDecimal.java)

与外围框架相结合，Nop平台还内置了更多常用的解决方案。比如

1. **通用查询**：前后端无需编写代码，只要表单按照一定格式提交查询请求，后台就可以根据meta元数据配置进行格式校验以及权限校验，通过后自动执行查询并按照GraphQL结果格式返回结果。

2. **修改确认及审批**：与CRUD服务和API调用服务相结合，用户提交请求后不直接修改数据库或者发出API调用，而是自动生成一条审批申请，审批人在审批界面上可以看到修改前后的内容，批准同意后再实际执行后续的动作。通过这个方案可以把任意表单界面都转化为申请提交页面和审批确认页面。

3. **复制新建**：一个复杂的业务对象可以通过复制已有对象的方式新建。需要复制的字段可以通过类似GraphQL查询语法的方式指定。

4. **字典表翻译**：前端显示的时候需要把statusId这样的字段通过字典表翻译为对应的显示文本，而且需要根据当前登录用户的locale设置选择对应的多语言版本。Nop平台在元编程阶段会自动发现所有配置了dict属性的字段，并自动为它们所对应的GraphQL描述增加一个关联的显示文本字段，例如根据statusId增加 statusId\_text字段。前台GraphQL请求statusId\_text字段即可得到字典表翻译后的结果，同时仍然可以通过statusId字段来获得字段原始的值。

5. **批量导入导出**：可以通过上传CSV文件或者Excel文件的方式导入数据，导入时执行的逻辑与手工通过界面提交完全一致，并会自动校验数据权限。可以按照CSV或者Excel格式导出文件。

6. **分布式事务**：自动与TCC分布式事务引擎结合。

NopOrm遵循可逆计算原理，因此它的底层模型都是可定制的。用户可以根据自己的需求随时为模型增加自定义的属性，然后再通过元编程、代码生成器等机制利用这些信息。上面介绍的大量功能实现其实都是采用类似机制实现的，它们很多都不属于引擎内核完成的功能，而是定制机制引入的。

### 5.3 拥抱异步的新世界

传统上JDBC访问接口全部是同步的，因此JdbcTemplate和HibernateTemplate的封装风格也是同步调用风格。但是随着异步高并发编程思想的传播，响应式编程风格逐渐开始进入主流框架。Spring目前是提出了[R2DBC标准](https://r2dbc.io/)，而[vertx框架](https://vertx.io/)也内置了对MySQL、PostgreSQL等主流数据库的[异步连接器](https://vertx.io/docs/vertx-pg-client/java/)支持。另一方面，ORM引擎如果作为一个数据融合访问引擎，它的底层存储可能是Redis、ElasticSearch、MongoDB这种支持异步访问的NoSQL数据源，而且ORM需要和GraphQL异步执行引擎相配合。考虑到这些情况，NopOrm的OrmTemplate封装也增加了异步调用模式

```java
 public interface IOrmTemplate extends ISqlExecutor {
    <T> CompletionStage<T> runInSessionAsync(
          Function<IOrmSession, CompletionStage<T>> callback);
 }
```

OrmSession在设计上是线程不安全的，同一时刻只允许一个线程访问。为了实现多线程访问同一个线程不安全的数据结构，一个基本的设计方案是采用类似Actor的任务队列模式，

```java
class Context{
    ContextTaskQueue taskQueue;

    public void runOnContext(Runnable task) {
        if (!taskQueue.enqueue(task)) {
            taskQueue.flush();
        }
    }
}
```

Context是跨线程传递的上下文对象，它具有一个对应的任务队列。任意时刻只有一个线程会执行该任务队列中注册的任务。runOnContext函数向任务队列中注册任务，如果发现没有其他线程正在执行该任务队列，则由当前线程负责执行。

> 对于递归调用，taskQueue实际上起到了类似[trampoline function](https://zhuanlan.zhihu.com/p/142241289)的作用。

如果引入了异步Context的概念，我们还可以改进对远程服务调用的超时支持。远程服务调用超时之后，客户端会抛出异常或者发起重试，但是此时服务端并不知道已经超时，仍在继续执行。服务函数一般会多次访问数据库，如果此时叠加上重试导致的流量，会导致数据库的实际压力远大于未超时的场景。一个改进策略就是在Context上增加一个超时时间属性

```java
class Context{
    long callExpireTime;
}
```

当跨系统调用时，通过RPC的消息头可以传递一个timeout超时时间间隔，在服务端接收到timeout之后，加上当前时间得到callExpireTime(callExpireTime = currentTime + timeout)。然后在JdbcTemplate中，每次发出数据库请求之前都会检查一下是否已经到达callExpireTime，从而及时发现服务端已超时的情况。如果在服务端要调用第三方系统的API，则重新计算 timeout = callExpireTime - currentTime得到剩余的超时时间间隔，并传递到第三方系统。

### 5.4 Dialect的差量化定制

NopOrm通过Dialect模型来封装不同数据库之间的差异。

[default dialect](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-dao/src/main/resources/_vfs/nop/dao/dialect/default.dialect.xml)

[mysql dialect](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-dao/src/main/resources/_vfs/nop/dao/dialect/mysql.dialect.xml)

[postgresql dialect](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-dao/src/main/resources/_vfs/nop/dao/dialect/postgresql.dialect.xml)

参考上面的示例，mysql.dialect.xml和postgresql.dialect.xml均从default.dialect.xml继承。与Hibernate通过编程方式构造Dialect对象相比，使用dialect模型文件明显信息密度更高，表达形式更加直观。更重要的是，在postgresql.dialect.xml中可以清楚的识别出相对于default.dialect.xml所**增加、修改和减少**的配置。

因为整个Nop平台的底层都是基于可逆计算原理构建的，因此dialect模型文件的解析和验证可以由通用的DslModelParser完成，同时自动支持Delta定制，即**在不修改default.dialect.xml文件，也不修改所有对default.dialect.xml文件的引用的情况下**（例如不需要修改postgresql.dialect.xml中的x:extends属性），我们可以在/\_delta目录下增加一个default.dialect.xml文件，通过它来定制系统内置的模型文件。

```xml
<!-- /_delta/myapp/nop/dao/dialect/default.dialect.xml -->
<dialect x:extends="raw:/nop/dao/dialect/default.dialect.xml">
  这里只需要描述差量变化的部分
</dialect>
```

Delta定制类似Docker技术中的overlay fs差量文件系统，**允许多个Delta层的叠加**。与Docker不同的是，Delta定制不仅发生在文件层面，它还延展到文件内部的差量结构运算。**借助于xdef元模型定义，Nop平台中的所有模型文件都自动支持Delta差量化定制**。

### 5.5 可视化集成

hibernate的hbm定义文件和JPA注解都是针对数据库结构映射而设计的，它们并不适合于可视化模型设计。为Hibernate增加可视化设计器是一件相对复杂的事情。

NopOrm采用orm.xml模型文件来定义实体模型。首先，它是一个完整的结构定义模型，可以根据模型中的信息生成建库脚本，以及与当前数据库结构自动进行差异比较和自动进行数据迁移等。

[app.orm.xml](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-orm/src/test/resources/_vfs/nop/test/orm/app.orm.xml)

为NopOrm增加可视化设计器是一件非常简单的事情，简单到只需要增加一个元编程标签调用

```xml
<orm ... >
    <x:gen-extends>
        <pdman:GenOrm src="test.pdma.json" xpl:lib="/nop/orm/xlib/pdman.xlib"
                      versionCol="REVISION"
                      createrCol="CREATED_BY" createTimeCol="CREATED_TIME"
                      updaterCol="UPDATED_BY" updateTimeCol="UPDATED_TIME"
                      tenantCol="TENANT_ID"
        />
          ...
    </x:gen-extends>
</orm>
```

[Pdman](http://www.pdman.cn/)是一个开源的数据库建模工具，它将模型信息保存为json文件格式。`<pdman:GenOrm>`是一个在编译期元编程阶段运行的XPL模板语言标签，它会根据pdman的json模型自动生成orm模型文件。这种生成是即时生效的，即只要修改了test.pdma.json文件，OrmModel的解析缓存就会失效，再次访问时会重新解析得到新的模型对象。

根据可逆计算理论，所谓的可视化设计界面不过是领域模型的一种图形化表示形式(Representation)，而模型文件文本可以看作是领域模型的文本表示形式。可逆计算理论指出，一个模型可以有多种表示形式，可视化编辑不过是说图形化表示形式和文本表示形式之间存在可逆转换而已。沿着这个方向进行推理，我们可以得出一个推论，即一个模型的可视化展现形式并不是唯一的，完全可以有多种不同形态的可视化设计器用于设计同一个模型对象。

对应orm模型而言，除了pdman，我们还可以选择用powerdesigner设计工具来设计，同样

通过一个类似的`<pdm:GenOrm>`标签可以将pdm模型文件转换为orm所需的模型格式。

在Nop平台中，我们还支持通过Excel文件格式来定义实体数据模型。

[test.orm.xlsx](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-orm/src/test/resources/_vfs/nop/test/orm/test.orm.xlsx)

同样的，我们只需要引入一个标签调用 `<orm-gen:GenFromExcel>`，然后就可以快乐的在Excel中进行ORM模型设计了。

```xml
<orm ...>
  <x:gen-extends>
     <orm-gen:GenFromExcel path="test.orm.xlsx" />
  </x:gen-extends>
</orm>
```

值得一提的是，Nop平台中的Excel模型文件解析也是基于可逆计算理论设计的。可逆计算理论将Excel模型文件解析看作是从Excel范畴映射到DSL AST（Abstract Syntax Tree）范畴的一个函子（同样是一种等价的表象转换），因此可以实现一个通用的Excel模型解析器，仅需要输入orm元模型文件所定义的结构信息，不需要进行任何特殊编码，即可实现Excel模型的解析。这种机制完全是通用的，即针对任何Nop平台中定义的模型文件，我们都可以免费获得它对应的Excel可视化编辑模型，同时这些Excel文件的格式是相对自由的，我们可以随意在其中调整单元格的位置、样式、前后顺序等。只要它们能够按照某种确定性的规则被识别为Tree结构即可。

关于模型转换的进一步介绍，可以参考以下文章

[从张量积看低代码平台的设计](https://zhuanlan.zhihu.com/p/531474176)

Nop平台中的模型信息还可以通过通用的Word模板形式对外导出，具体技术方案可以参见

[如何用800行代码实现类似poi-tl的Word模板](https://zhuanlan.zhihu.com/p/537439335)

Nop平台所支持的所有业务功能都是通过模型驱动的方式实现，因此通过分析模型信息我们有可能导出大量有用的信息。比如，我们曾经根据内部模型导出过数据库模型文档，数据字典文档、API接口文档、单元测试文档等。

### 六. 老大难的N+1问题

自从Hibernate诞生之日起，所谓的N+1问题就一直是笼罩在ORM引擎头上的一朵乌云。假设我们有这样一套模型

```java
class Customer{
    Set<Order> orders;
}

class Order{
   Set<OrderDetail> details;
}
```

如果我们想处理某个客户的订单明细信息，则会需要遍历orders集合，

```
Customer customer = ... // 假设已经获取到customer
Set<Order> orders = customer.getOrders();
for(Order order: orders){
    process(order.getDetails());
}
```

从customer装载orders集合需要发出一条SQL语句，遍历orders集合，对每个order获取它的details集合又会发出一条SQL语句，最后导致整个处理过程发出N+1条查询语句。

N+1问题之所以臭名昭著，原因在于开发阶段数据量很小，性能问题往往被忽略，而在上线后发现问题时，我们却没有任何**通过局部调整进行补救**的手段。所有的修改往往都需要对代码进行重写，甚至完全改变程序设计。

这个问题一直困扰着Hibernate，直到很多年以后JPA（Java Persistence API）标准提出了一个EntityGraph的概念。

```java
@NamedEntityGraph(
   name = "customer-with-orders-and-details",
   attributeNodes = {
       @NamedAttributeNode(value = "orders", subgraph = "order-details"),
   },
   subgraphs = {@NamedSubgraph(
       name = "order-details",
       attributeNodes = {
           @NamedAttributeNode("deails")
       }
   )}
)
@Entity
class Customer{
    ...
}
```

在实体类上增加NamedEntityGraph注解，声明加载对象时要把orders集合以及details集合都一次性加载出来。然后在调用find方法的时候指定需要使用哪个EntityGraph配置。

```java
EntityGraph entityGraph = entityManager.getEntityGraph("customer-with-orders-and-detail");
Map<String,Object> hints = new HashMap<>();
hints.put("javax.persistence.fetchgraph", entityGraph);
Customer customer = entityManager.find(Customer.class, customerId, hints);
```

除了使用注解来声明之外，EntityGraph还可以通过代码来构造

```java
EntityGraph graph = entityManager.createEntityGraph(Customer.class);
Subgraph detailGraph = graph.addSubgraph("order-details");
detailGraph.addAttributeNodes("details");
```

实际会生成类似如下SQL语句

```sql
select customer0.*,
       order1.*,
       detail2.*
from
     customer customer0
       left join order order1 on ...
       left join order_detail detail2 on ...
 where customer0.id = ?
```

Hibernate会使用一条SQL语句把所有数据都取出来，代价就是需要多个表进行关联，并返回了大量冗余数据。

这个问题是否还存在其他解决方案？从数据模型本身的结构来看, Customer -\> orders -\> details的嵌套结构是非常直观自然的，并没有什么问题，但是**问题出在我们只能按照对象结构定义好的方式去获取数据，而且我们只能逐个遍历对象结构**，从而导致产生大量数据查询语句。**如果我们能够绕过对象结构，直接通过某种方式获取到对象数据，并把它们在内存中按照需要的对象结构组织好，这个问题不就解决了吗？**

```java
Customer customer = ...
// 插入一条神秘的数据获取指令
fetchAndAssembleDataInAMagicalWay(customer);
// 数据已经在内存中存在，可以安全的遍历并使用，不再产生数据加载动作
Set<Order> orders = customer.getOrders();
for(Order order: orders){
    process(order.getDetails());
}
```

NopOrm中通过OrmTemplate提供了一个批量加载属性的接口。

```java
ormTemplate.batchLoadProps(Arrays.asList(customer), Arrays.asList("orders.details"));
// 数据已经在内存中存在，可以安全的遍历并使用，不再产生数据加载动作
Set<Order> orders = customer.getOrders();
```

OrmTemplate内部通过IBatchLoadQueue加载队列来实现功能

```java
IBatchLoadQueue queue = session.getBatchLoadQueue();
queue.enqueue(entity);
queue.enqueueManyProps(collection,propNames);
queue.enqueueSelection(collection,fieldSelection);
queue.flush();
```

BatchLoadQueue的内部实现原理其实和GraphQL的DataLoader机制类似，都是先收集要加载的实体或者实体集合对象，然后用一条`select xxx from ref__entity where ownerId in :idList`来批量获取数据，接着再按ownerId拆分到不同的对象和集合中。因为BatchLoadQueue具有实体模型的全部信息，而且具有统一的加载器，所以它的内部实现相比于DataLoader要更加优化。同时，在外部接口方面，需要表达的信息量要更少。例如 orders.details就表示需要先加载orders，然后再加载details集合，并取得OrderDetail对象的所有eager属性。如果是使用GraphQL描述，则需要明确指定获取OrderDetail对象上的哪些属性，描述要更加复杂一些。

> BatchLoadQueue并不是受GraphQL启发而设计的。GraphQL于2015年开源，在此之前我们已经在使用BatchLoadQueue了。

如果需要加载的实体对象非常多，层次非常深，则按照id批量获取在性能上也有一些影响。为此，NopOrm保留了一个仅供专家使用的超级后门，

```java
 session.assembleAllCollectionInMemory(collectionName);
 或者
 session.assembleCollectionInMemory(entitySet);
```

**assembleAllCollectionInMemory假定所有涉及到的实体对象都已经被加载到内存中了**，因此它不再访问数据库，而是**直接通过对内存中的数据进行过滤来确定集合元素**。至于如何将所有相关实体都加载到内存中，方法就很多了。例如

```java
orm().findAll(new SQL("select o from Order o"));
orm().findAll(new SQL("select o from OrderDetail o"));
session.assembleAllCollectionInMemory("test.Customer@orders");
session.assembleAllCollectionInMemory("test.Order@details");
```

> 这种方法有一定危险性，因为如果在调用assemble函数之前没有将所有关联实体都加载到内存中，那么组装出来的集合对象就是错误的。

如果我们再回想一下前面EntityGraph所生成的那条SQL语句，它其实对应于如下EQL查询

```sql
select c, o, d
from Customer c left join c.orders o left join o.details
where c.id = ?
```

按照ORM的基本原理，虽然查询语句返回了很多重复的Customer和Order对象，但是因为它们的主键都相同，所以最后在内存中构造为对象时只会保留唯一一个实例。甚至如果此前已经装载过某个Customer或者Order对象的话，那么它的数据会以此前装载的结果为准，本次查询得到的数据会自动被忽略。

> 也就是说，ORM提供了一种类似数据库中[Repeatable Read事务隔离级别](https://zhuanlan.zhihu.com/p/150107974)的效果。当重复读取的时候只是读取到一个寂寞，ORM引擎只会保留第一次读取的结果。基于同样的原因，对于Load X, Update X , Load X的情况，第二次加载读到的数据会被自动丢弃，从而我们所观察到的总是第一次加载的结果，以及后续我们对实体所做的修改，这相当于是实现了[Read your writes这样的因果一致性](https://zhuanlan.zhihu.com/p/59119088)。

基于以上认知，EntityGraph的执行过程等价于如下调用

```sql
orm().findAll(new SQL("select c,o,d from Customer c left join ..."));
session.assembleSelectionInMemory(c, FieldSelectionBean.fromProp("orders.details"));
// assembleSelection的执行过程等价于如下调用
session.assembleCollectionInMemory(c.getOrders());
for(Order o: c.getOrders()){
    session.assembleCollectionInMemory(o.getDetails());
}
```

## 七. QueryBuilder很重要，但和ORM没关系

有些人认为[ORM的作用很大程度上在于QueryBuilder](https://www.zhihu.com/question/23244681/answer/2426095608)，我认为这是一种误解。**QueryBuilder有用仅仅是因为Query对象需要被建模而已**。在Nop平台中我们提供了QueryBean模型对象，它支持如下功能

1. QueryBean在前台对应于QueryForm和QueryBuilder控件，可以直接由这些控件来构造复杂查询条件

2. 后台数据权限过滤所对应的filter条件可以直接插入到QueryBean中，相比于SQL拼接，结构清晰且不会出现SQL注入攻击。queryBean.appendFilter(filter)

3. QueryBean支持自定义查询算子和查询字段，可以在后台通过queryBean.transformFilter(fn)把它转换为内置的查询算子。例如我们可以定义一个虚拟字段myField，然后查询内存中的状态数据以及其他关联表的数据，将它转换为一个子查询条件等，这样**在单表查询框架下实际上可以实现多表联合查询的效果**。

4. DaoQueryHelper.queryToSelectObjectSql(query)可以将查询条件转换为SQL语句

5. QueryBeanHelper.toPredicate(filter)可以将过滤条件转换为Predicate接口，从而在java中直接过滤。

6. 通过FilterBeans中定义的and,eq等算子，结合代码生成时自动生成的属性名常量，我们可以实现如下编译期安全的构造方式。

   filter = and(eq(PROP\_NAME\_myFld,"a"), gt(PROP\_NAME\_otherFld,3))

QueryBuilder本质上是与ORM无关的，因为在完全脱离关系数据库和SQL语句的情况下，我们仍然可以使用Query模型。例如，在业务规则配置中

```xml
<decisionTree>
    <children>
      <rule>
        <filter>
          <eq name="message.type" vaule="@:1" />
          <match name="message.desc" value="a.*" />
        </filter>
        <output name="channel" value="A" />
      </rule>
      <rule>
        ...
      </rule>
    </children>
</decisionTree>
```

可以直接复用前台的QueryBuilder来实现对于后台决策规则的可视化配置。

在Java代码中通过所谓的QueryDsl来构造SQL语句本质上说并没有什么优势。因为如果采用模型驱动的方式，直接使用前台传入的QueryBean就好了，补充少量查询条件可以使用FilterBeans中定义的and/or/eq等静态组合函数。如果是非常复杂的SQL构造，那么直接采用类似MyBatis的方案，在独立的外部文件中统一管理无疑是更好的选择。在sql-lib中，我们可以实现QueryDsl所无法达到的直观性、灵活性和可扩展性（在后面后有更详细的介绍）。

[QueryBean](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-api-core/src/main/java/io/nop/api/core/beans/query/QueryBean.java)

## 八. OLAP分析能用ORM吗？

一直有一种说法是ORM只适用于OLTP应用，对于OLAP数据分析所需的复杂查询语句无能为力。但偏偏有人要知难而上，就是要用ORM，还要用得更快、更高、更强！

> 说实话，难道用SQL去写汇总分析语句就简单吗？太多的关联和子查询仅仅是为了把数据按照某个维度组织到一起。拆分成多个查询去做，然后在程序中再组装到一起会不会更简单？

[润乾报表](http://www.raqsoft.com.cn/about#aboutme)是一家非常独特的公司，创始人蒋步星是写入了中国历史的传奇人物（国际奥林匹克数学竞赛的首届中国金牌得主，来自新疆石河子，参见[顾险峰教授的回忆](https://blog.sciencenet.cn/blog-2472277-1160241.html)），他发明了中国式报表模型相关的理论，并引领了整整一代报表软件的技术潮流。虽然由于种种原因，润乾公司最后的发展不尽如人意，但它在设计理论方面还是发表了不少独特的见解。

润乾开源了一个[前端BI系统](http://www.raqsoft.com.cn/r/os-bi)，它虽然颜值有点低，但是在技术层面却提出了一个别致的DQL(Dimentinal Query Language)语言。具体介绍可以参考乾学院的文章

[告别宽表，用 DQL 成就新一代 BI - 乾学院](http://c.raqsoft.com.cn/article/1653901344139?p=1&m=0)

润乾的观点是终端用户难以理解复杂的SQL JOIN，为了便于多维分析，只能使用大宽表，这为数据准备带来一系列困难。而DQL则是简化了对终端用户而言JOIN操作的心智模型，并且在性能上相比于SQL更有优势。

以如何查找**中国经理的美国员工**为例

```sql
-- SQL
SELECT A.*
FROM  员工表 A
JOIN 部门表  ON A.部门 = 部门表.编号
JOIN  员工表 C ON  部门表.经理 = C.编号
WHERE A.国籍 = '美国'  AND C.国籍 = '中国'

-- DQL
SELECT *
FROM 员工表
WHERE 国籍='美国' AND 部门.经理.国籍='中国'
```

这里的关键点被称为：外键属性化，也就是说外键指向表的字段可直接用子属性的方式引用，也允许多层和递归引用。

另一个类似的例子是根据订单表 (orders)，区域表(area)，查询订单的发货城市名称、以及所在的省份名称、地区名称。

```sql
-- DQL
SELECT
    send_city.name city,
    send_city.pid.name province,
    send_city.pid.pid.name region
FROM
    orders
```

DQL的第二个关键思想是：**同维表等同化**，也就是一对一关联的表，不用明确写关联查询条件，可以认为它们的字段是共享的。例如，员工表和经理表是一对一的，我们需要查询**所有员工的收入**

```sql
-- SQL
SELECT 员工表.姓名, 员工表.工资 + 经理表.津贴
FROM 员工表
LEFT JOIN 经理表 ON 员工表.编码 = 经理表.编号

-- DQL
SELECT 姓名,工资+津贴
FROM 员工表
```

DQL的第三个关键思想是：**子表集合化**，例如订单明细表可以看作是订单表的一个集合字段。如果要计算每张订单的汇总金额，

```sql
-- SQL
SELECT T1.订单编号,T1.客户,SUM(T2.价格)
FROM 订单表T1
JOIN 订单明细表T2 ON T1.订单编号=T2.订单编号
GROUP BY T1.订单编号,T1.客户

-- DQL
SELECT 订单编号,客户,订单明细表.SUM(价格)
FROM 订单表
```

"如果有多个子表时，SQL 需要分别先做 GROUP, 然后在一起和主表 JOIN 才行，会写成子查询的形式，但是 DQL 则仍然很简单，SELECT 后直接再加字段就可以了"。

DQL的第四个关键思想是：**数据按维度自然对齐**。我们不用特意指定关联条件，最终数据之所以能够放在同一张表里展示，原因不是因为它们之间存在什么先验的关联关系，仅仅是因为它们共享了最左侧的维度坐标而已。例如：我们希望**按日期统计合同额、回款额和库存金额**。我们需要从三个表分别取数据，然后按照日期对齐，汇总到结果数据集中。

```sql
-- SQL
SELECT T1.日期,T1.金额,T2.金额, T3.金额
FROM (SELECT  日期, SUM(金额) 金额  FROM  合同表  GROUP  BY  日期）T1
LEFT JOIN (SELECT  日期, SUM(金额) 金额  FROM  回款表  GROUP  BY  日期）T2
ON T1.日期 = T2.日期
LEFT JOIN (SELECT  日期, SUM(金额) 金额  FROM  库存表  GROUP  BY  日期 ) T3
ON T2.日期 = T3.日期

-- DQL
SELECT 合同表.SUM(金额),回款表.SUM(金额),库存表.SUM(金额) ON 日期
FROM 合同表 BY 日期
LEFT JOIN 回款表 BY 日期
LEFT JOIN 库存表 BY 日期
```

在 DQL 中，维度对齐可以和外键属性化结合，例如

```sql
-- DQL
SELECT 销售员.count(1),合同表.sum(金额) ON 地区
FROM 销售员 BY 地区
JOIN 合同表 BY 客户表.地区
SELECT 销售员.count(1),合同表.sum(金额) ON 地区
FROM 销售员 BY 地区
JOIN 合同表 BY 客户表.地区
```

如果从NopOrm的角度去看DQL的设计，则显然DQL本质上也是一种ORM的设计。

1. DQL需要通过设计器定义主外键关联，并为每个字段指定界面上的显式名称，这一做法完全与ORM模型设计相同。

2. DQL的外键属性化、同维等同化和子表集合化本质上就是EQL语法中的对象属性关联语法，只是它直接用数据库的关联字段作为关联对象名。这种做法比较简单，但缺点是对于复合主键关联的情况不太好处理。

3. DQL的维度对齐是一个有趣的思想。它的具体实现应该是分多个SQL语句去加载数据，然后在内存中通过Hash Join来实现关联，速度很快。特别是在分页查询的情况下，我们可以只对主表进行分页查询，然后其他子表通过in条件只取本页数据涉及到的记录即可，在大表的情况下有可能加速很多。

基于EQL语言去实现DQL的功能是一件比较简单的事情。读了润乾的这篇文章之后，我大概花了一个周末的时间实现了一个MdxQueryExecutor，用于执行维度对齐查询。因为EQL已经内置支持了对象属性关联，所以只要实现对QueryBean对象的拆分、分片执行、数据并置融合就可以了。

## 九. SQL模板管理，你值得拥有

当我们需要构造比较复杂的SQL或者EQL语句的时候，通过一个外部模型文件对它们进行管理无疑是有着重要价值的。MyBatis提供了这样一种把SQL语句模型化的机制，但是仍然有很多人倾向于在Java代码中通过QueryDsl这样的方案来动态拼接SQL。这实际上是在说明**MyBatis的功能实现比较单薄，没有能够充分发挥模型化的优势**。

在NopOrm中，我们通过sql-lib模型来统一管理所有复杂的SQL/EQL/DQL语句。在利用Nop平台已有基础设施的情况下，实现类似MyBatis的这一SQL语句管理机制，大概只需要200行代码。具体实现代码参见

[SqlLibManager](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-orm/src/main/java/io/nop/orm/sql_lib/SqlLibManager.java)

[SqlItemModel](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-orm/src/main/java/io/nop/orm/sql_lib/SqlItemModel.java)

[SqlLibInvoker](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-orm/src/main/java/io/nop/orm/sql_lib/proxy/SqlLibInvoker.java)

测试用的sql-lib文件参见

[test.sql-lib.xml](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-orm/src/test/resources/_vfs/nop/test/sql/test.sql-lib.xml)

sql-lib提供了如下特性

### 9.1  统一管理SQL/EQL/DQL

在sql-lib文件中存在三种节点，sql/eql/query分别对应于SQL语句，EQL语句和上一节介绍的润乾DQL查询模型，对它们可以采取统一的方式进行管理。

```xml
<sql-lib>
  <sqls>
     <sql name="xxx" > ... </sql>
     <eql name="yyy" > ... </eql>
     <query name="zz" > ... </query>
  </sqls>
</sql-lib>
```

模型化的第一个好处就是Nop平台内置的Delta定制机制。假设我们已经开发了一个Base产品，在客户处部署的时候需要针对客户的数据情况进行SQL优化，则我们**无需修改任何Base产品的代码**，只需要添加一个sql-lib的差量化模型文件，就可以实现对任意SQL语句的定制。例如

```xml
<sql-lib x:extends="raw:/original.sql-lib.xml">
   <sqls>
      <!-- 同名的sql语句会覆盖基类文件中的定义 -->
      <eql name="yyy"> ...</eql>
   </sqls>
</sql-lib>
```

关于Delta定制，另一个常见用法是结合元编程机制。假设我们的系统是一个领域模型很规整的系统，存在大量类似的SQL语句，则我们可以通过元编程机制先在编译期自动生成这些SQL语句，然后再通过Delta定制来对它们进行改进就可以了。例如

```xml
<sql-lib>
   <x:gen-extends>
       <app:GenDefaultSqls ... />
   </x:gen-extends>

  <sqls>
     <!-- 在这里可以对自动生成SQL进行定制 -->
     <eql name=”yyy“>...</eql>
  </sqls>
</sql-lib>
```

### 9.2 XPL模板的组件抽象能力

MyBatis只提供了foreach/if/include等少数几个固定标签，真正编写起高度复杂的动态SQL语句时可以说是有心无力。很多人觉得在xml中拼接sql比较麻烦，归根结底是因为MyBatis提供的是一个不完善的解决方案，它**缺少二次抽象的机制**。  而在java程序中我们总可以通过函数封装来实现对某一段SQL拼接逻辑的复用，对比MyBatis却只有内置的三板斧，基本没有提供任何辅助复用的能力。

NopOrm直接采用XLang语言中的XPL模板语言来作为底层的生成引擎，因此它自动继承了XPL模板语言的标签抽象能力。

> XLang是专为可逆计算理论而生的程序语言，它包含XDefinition/XScript/Xpl/XTransform等多个部分，其核心设计思想是对抽象语法树AST的生成、转换和差量合并，可以认为它是针对Tree文法而设计的程序语言。

```xml
<sql name="xxx">
  <source>
   select <my:MyFields />
       <my:WhenAdmin>
         ,<my:AdmninFields />
       </my:WhenAdmin>
   from MyEntity o
   where <my:AuthFilter/>
  </source>
</sql>
```

Xpl模板语言不仅内置了`<c:for>`,`<c:if>`等图灵完备语言所需的语法元素，而且允许通过自定制标签机制引入新的标签抽象（可以类比于前端的vue组件封装）。

有些模板语言要求所有能在模板中使用的函数需要提前注册，而Xpl模板语言可以直接调用Java。

```xml
<sql>
  <source>
    <c:script>
       import test.MyService;

       let service = new MyService();
       let bean = inject("MyBean"); // 直接获取IoC容器中注册的bean
    </c:script>
  </source>
</sql>
```

### 9.3 宏(Macro)标签的元编程能力

MyBatis拼接动态SQL的方式很笨拙，因此一些类MyBatis的框架会在SQL模板层面提供一些特殊设计的简化语法。例如有些框架引入了隐式条件判断机制

```sql
select xxx
from my_entity
where id = :id
[and name=:name]
```

通过自动分析括号内的变量定义情况，自动增加一个隐式的条件判断，仅当name属性值不为空的时候才输出对应的SQL片段。

在NopOrm中，我们可以通过宏标签来实现类似的**局部语法结构变换**

```xml
<sql>
  <source>
    select o from MyEntity o
    where 1=1
     <sql:filter> and o.classId = :myVar</sql:filter>
  </source>
</sql>
```

`<sql:filter>`是一个宏标签，它在编译期执行，相当于是对源码结构进行变换，等价于手写的如下代码

```xml
<c:if test="${!_.isEmpty(myVar)}">
   and o.classId = ${myVar}
</c:if>
```

具体标签的实现参见

[sql.xlib](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-orm/src/main/resources/_vfs/nop/orm/xlib/sql.xlib)

本质上这个概念等价于Lisp语言中的宏，特别是它与Lisp宏一样，可以用于程序代码中的任意部分（即AST的任意节点都可以被替换为宏节点）。只不过，它采用XML的表现形式，相比于Lisp惜字如金的数学符号风格而言，显得更加人性化一些。

微软C#语言的LINQ（语言集成查询）语法，其实现原理是在编译期获取到表达式的抽象语法树对象，然后交由应用代码执行结构变换，本质上也是一种编译期的宏变换技术。在XLang语言中，除了Xpl模板所提供的宏标签之外，还可以使用XScript的宏函数来实现SQL语法和对象语法之间的转换。例如

```xml
<c:script>
function f(x,y){
    return x + y;
}
let obj = ...
let {a,b} = linq `
  select sum(x + y) as a , sum(x * y) as b
  from obj
  where f(x,y) > 2 and sin(x) > cos(y)
`
</c:script>
```

XScript的模板表达式会自动识别宏函数，并在编译期自动执行。因此我们可以定义一个宏函数linq，它将模板字符串在编译期解析为SQL语法树，然后再变换为普通的JavaScript AST，从而相当于是在面向对象的XScript语法（类似TypeScript的脚本语言）中嵌入类SQL语法的DSL，可以完成类似LinQ的功能，但是实现方式要简单得多，形式上也更接近SQL的原始形式。

> 以上仅为概念示例，目前Nop平台仅提供了xpath/jpath/xpl等宏函数，并没有提供内置的linq宏函数。

### 9.4 模板语言的SQL输出模式

模板语言相对于普通程序语言而言，它的设计偏置是将输出（Output）这一副作用作为第一类（first class）的概念。当我们没有做任何特殊修饰的时候，就表示对外输出，而如果我们要表示执行其他逻辑，则需要用表达式、标签等形式明确的隔离出来。Xpl模板语言作为一种Generic的模板语言，它对输出这一概念进行了强化，增加了多模式输出的设计。

Xpl模板语言支持多种输出模式（Output Mode）

* text: 普通文本的输出，不需要进行额外转义

* xml: XML格式文本的输出，自动按照XML规范进行转义

* node: 结构化AST的输出，会保留源码位置

* sql：支持SQL对象的输出，杜绝SQL注入攻击

sql模式针对SQL输出的情况做了特殊处理，主要增加了如下规则

1. 如果输出对象，则替换为?，并把对象收集到参数集合中。例如  `id = ${id}` 实际将生成id=?的sql文本，同时通过一个List来保存参数值。

2. 如果输出集合对象，则自动展开为多个参数。例如  `id in (${ids})` 对应生成id in (?,?,?)。

如果确实希望直接输出SQL文本，拼接到SQL语句中，可以使用raw函数来包装。

```
from MyEntity_${raw(postfix)} o
```

此外，NopOrm对于参数化SQL对象本身也建立了一个简单的包装模型

```
SQL = Text + Params
```

通过sql = SQL.begin().sql("o.id = ? ", name).end() 这种形式可以构造带参数的SQL语句对象。Xpl模板的sql输出模式会自动识别SQL对象，并自动对文本和参数集合分别进行处理。

### 9.5 自动验证

外部文件中管理SQL模板存在一个缺点：它无法依赖类型系统进行校验，只能期待运行时测试来检查SQL语法是否正确。如果数据模型发生变化，则可能无法立刻发现哪些SQL语句受到影响。
对于这个问题，其实存在一些比较简单的解决方案。毕竟，SQL语句既然已经作为结构化的模型被管理起来了，我们能够对它们进行操作的手段就变得异常丰富起来。
NopOrm内置了一个类似Contract Based Programming的机制：每个EQL语句的模型都支持一个validate-input配置，我们可以在其中准备一些测试数据，然后ORM引擎在加载sql-lib的时候会自动运行validate-input得到测试数据，并以测试数据为基础执行SQL模板来生成EQL语句，然后交由EQL解析器来分析它的合法性，从而实现以一种准静态分析的方式检查ORM模型与EQL语句的一致性。

### 9.6 调试支持

与MyBatis内置的自制简易模板语言不同，NopOrm使用Xpl模板语言来生成SQL语句，因此可以很自然的可以利用XLang语言调试器来调试。Nop平台提供了IDEA开发插件，支持DSL语法提示和断点调试功能。它会自动读取sql-lib.xdef元模型定义文件，根据元模型自动校验sql-lib文件的语法正确性，并提供语法提示功能，支持在source段增加断点，进行单步调试等。

Nop平台中所有的DSL都是基于可逆计算原理构建的，它们都使用统一的元模型定义语言XDefinition来描述，所以并不需要针对每一种DSL来单独开发IDE插件和断点调试器。为了给自定义的sql-lib模型增加IDE支持，唯一需要的就是在模型根节点上增加属性x:schema="/nop/schema/orm/sql-lib.xdef"，引入xdef元模型。

XLang语言还内置了一些调试特性，方便在元编程阶段对问题进行诊断。

1. outputMode=node输出模式下生成的AST节点会自动保留源文件的行号，因此当生成的代码编译报错时，我们直接对应到源文件的代码位置。

2. Xpl模板语言节点上可以增加xpl:dump属性，打印出当前节点经动态编译后得到的AST语法树

3. 任何表达式都可以追加调用扩展函数`$`，它会自动打印当前表达式对应的文本、行号以及表达式执行的结果, 并返回表达式的结果值。例如

```
x = a.f().$(prefix) 实际对应于
x = DebugHelper.v(location,prefix, "a.f()",a.f())
```

## 十. GraphQL over ORM

如果从比较抽象的角度上去考察，前后台交互的方式无非就是：**请求后台业务对象O上的业务方法M，传给它参数X，返回结果Y**。如果把这句话写成url的形式，得到的结果类似

```
view?bizObj=MyObj&bizAction=myMethod&arg=X
```

具体来说，bizObj可以对应于后台的Controller对象，而bizAction对应于Controller上定义的业务方法，view表示呈现给调用者的结果信息，它的数据来源是业务方法获取到的数据。对于普通的AJAX请求，返回的json数据格式是由业务方法所唯一确定的，因此可以写成一个固定的json。对于通用的RESTful服务而言，view的选择可以更加灵活，例如可以根据Http的contentType header来决定是返回json格式还是xml格式。如果view是由请求的业务对象和方法所唯一确定的，我们称Web请求是push模式，而如果客户端可以选择返回的view，我们说对应的Web请求是pull模式。基于这个认知，我们可以将GraphQL看作是 **Composable Pull-mode Web Request**。

GraphQL与普通的REST请求或者RPC请求的最显著的区别在于，它的请求模式对应于

```
selection?bizObj=MyObj&bizField=myField&arg=X
```

GraphQL是一种pull模式的请求，它会指定返回的结果数据。但是这种指定又不是完全的新建，而是**在已有数据结构的基础上所作的选择和局部的重组（重命名）**。正是因为selection信息是高度结构化的，所以它能够被提前解析，成为指导业务方法执行的蓝图。同样因为它是高度结构化的，所以针对多个业务对象的业务请求可以有序的组合在一起。

从某种意义上说，Web框架的逻辑结构实际上是唯一的。为了实现有效的逻辑拆分，我们必然需要区分后台不同的业务对象，为了实现灵活的组织，我们必然需要指定返回的view。推论就是url的格式应为 `view?bizObj=MyObj&bizAction=myAction&arg=X`

> 很多年以前，我写过一篇文章，分析了WebMVC框架的设计原理: [WebMVC的前世今生](http://www.blogjava.net/canonical/archive/2008/02/18/180551.html)。这篇文章的分析在今天仍然是有效的。

基于以上的认知，GraphQL与ORM的结合可以非常的简单。在Nop平台中，GraphQL服务通过确定性的映射规则可以直接映射到底层的ORM实体对象上，无需编程即可得到可运行的GraphQL服务。在这种自动映射规则的基础上，我们可以逐步补充其他业务规则，例如权限过滤、业务流程、调整数据结构等。具体来说，每一个数据库表都作为一个备选的业务对象，代码生成器自动为它们生成如下代码：

```
/entity/_MyObj.java
       /MyObj.java
/model/_MyObj.xmeta
      /MyObj.xmeta
      /MyObj.xbiz
/biz/MyObjBizModel.java
```

* MyObj.java是根据ORM模型定义自动生成的实体类，我们可以直接在实体类上增加辅助属性和函数。

* MyObj.xmeta为外部可见的业务实体数据结构，系统根据它生成GraphQL对象的Schema定义。

* MyObjBizModel.java中则定义了定制的GraphQL服务响应函数和数据加载器。

* MyObj.xbiz涉及到更复杂的业务切面的概念，在本文中不再赘述。

GraphQL与ORM本质上提供的是不同层面的信息结构。GraphQL是针对外部视角的，而ORM更强调应用程序内部使用，因此它们必然不会共享同样的Schema定义。但是，在一般的业务应用中它们又是明显相似的，具有很大的共同性。**可逆计算为处理相似而不相同的信息结构提供了标准化的解决方案**。

针对以上的情况，Nop平台的设计是，`_MyObj.java`和`_MyObj.xmeta`都根据ORM模型直接生成，它们之间的信息是完全同步的。MyObj.java继承自`_MyObj.java`，在其中可以增加应用程序内部可见的额外的属性和方法。MyObj.xmeta中通过x:extends差量合并机制对`_MyObj.xmeta`进行定制，支持**增加、修改以及删除**对象属性和方法定义，同时我们还可以在xmeta中指定auth权限检查规则，对属性进行重命名等。例如

```xml
<meta>
  <props>
    <prop name="propA" x:override="remove" />
    <prop name="propB" mapToProp="internalProp">
      <auth roles="admin" />
      <schema dict="/app/my.dict.yaml" />
    </prop>
  </props>
</meta>
```

上面的例子中，propA属性将会被删除，因此GraphQL查询无法访问到该属性。同时内部的internalProp属性被重命名为propB，即GraphQL查询到propB时实际加载的是internalProp属性。propB配置了auth roles=admin，表示只有管理员才有权限访问该属性。schema中的dict配置表示它的值限定在字典表my.dict.yaml的范围内。在5.2节中，我们介绍了NopOrm中的字典表翻译机制：在元编程阶段，底层的引擎发现了dict设置，会自动生成一个propB\_text字段，它将返回经过字典表翻译后得到的国际化文本。

对于最顶层的GraphQL对象，Nop平台会自动生成如下结构定义:

```graphql
extend type Query{
    MyObj__get(id:String): MyObj
    MyObj__findPage(query:String): PageBean_MyObj
    ...
}
```

除了缺省的get/findPage等操作之外，我们可以在MyObjBizModel中定义扩展属性和方法。

```java
@BizModel("MyEntity")
public class MyEntityBizModel {

    @BizLoader("children")
    @BizObjName("MyChild")
    public List<MyChild> getChildren(@ContextSource MyEntity entity) {
        ...
    }

    @BizQuery("get")
    @BizObjName("MyEntity")
    public MyEntity getEntity(@ReflectionName("id") String id, IEvalScope scope,
                              IServiceContext context, FieldSelectionBean selection)     {
       ...
    }

    @BizQuery
    @BizObjName("MyEntity")
    public PageBean<MyEntity> findPage(@ReflectionName("query") QueryBean query) {
        ...
    }
}

@BizModel("MyChild")
public class MyChildBizModel {

    /**
     * 批量加载属性
     */
    @BizLoader("name")
    public List<String> getNames(@ContextSource List<MyChild> list) {
        List<String> ret = new ArrayList<>(list.size());
        for (MyChild child : list) {
            ret.add(child.getName() + "_batch");
        }
        return ret;
    }
}
```

BizModel中通过@BizQuery和@BizMutation来分别定义GraphQL Query和Mutation操作，GraphQL操作名称的格式为 `{bizObj}__{bizAction}`。同时，我们可以通过@BizLoader来增加GraphQL的fetcher定义，通过@ContextSource来引入GraphQL的父对象实例，通过@ReflectionName来标记argument，参数映射时会自动进行类型转换。

> 如果BizModel中也定义了get/findPage等函数，则会覆盖缺省的MyObj\_\_get等函数的实现。

BizModel的设计空间中只存在业务对象、业务方法和业务参数的概念，它与GraphQL是完全解耦的，因此我们可以很容易的为BizModel提供REST服务绑定或者其他RPC调用接口标准的绑定。在我们的具体实现中，我们甚至为它提供了一个批处理文件的绑定，即后台批处理任务定期运行，解析批处理文件得到请求对象，然后调用BizModel执行业务逻辑，将返回对象作为结果写入到结果文件中。这其中设计的关键是实现批处理的优化，即批处理任务每批次处理100条记录，应该整个批次完全处理完毕之后再一次性更新数据库，而不是处理每个业务请求后都立刻更新数据库。借助于ORM引擎的session机制，这种批处理优化完全是免费附赠的。

## 结语

可逆计算理论并不是一个聪明的设计模式，也不是一组基于最佳实践的经验总结。它是根植于我们这个世界真实存在的物理规律，从第一性原理出发基于严密的逻辑推导所得到的，关于大范围软件结构构造的一种创新性技术思想。

在可逆计算理论的指导下，NopOrm的技术方案体现出了底层逻辑结构的完备性和一致性，这使得它能够以单刀直入的简单方式解决一系列棘手的技术问题。（很多时候系统本质的复杂性并不是很高，但是多种组件相互配置时出现的结构障碍和概念冲突导致产生了大量的偶然复杂性）

NopOrm并不是一个专用于低代码的ORM引擎，它支持从LowCode到ProCode的平滑过渡。它既支持开发期代码生成，也支持运行期动态增加字段，同时为用户自定义存储提供了完整的解决方案。

它非常轻量级，在包含了Hibernate + MyBatis  + SpringData JDBC + GraphQL的主要功能的情况下，手工编写的有效代码量只有不到2万行（还有大量代码是自动生成的，因为Nop平台正努力采用低代码的方式来开发它自身的所有组件）。它不仅适用于小型单体项目的开发，同样也适用于分布式、高性能、高复杂性的大型业务系统的开发，同时还为BI系统提供了一定的语法支持，还支持通过GraalVM编译为原生应用等。

NopOrm遵循可逆计算原理，可以通过Delta定制和元编程对底层模型进行定制化增强，用户可以在自己的业务领域中不断积累可复用的领域模型，甚至开发自己专有的DSL。

更重要的是，NopOrm是开源的！（目前尚在代码整理阶段，会随着Nop Platform 2.0一起发布）

最后，对于能坚持看到这里的同学说一句，真的是太不容易了，真心为你的好学精神点个赞！

基于可逆计算理论设计的低代码平台NopPlatform已开源：

- gitee: [canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- github: [entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- 开发示例：[docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- [可逆计算原理和Nop平台介绍及答疑\_哔哩哔哩\_bilibili](https://www.bilibili.com/video/BV1u84y1w7kX/)

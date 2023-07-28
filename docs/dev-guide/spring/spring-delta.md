# 如何为Spring和Mybatis增加可逆计算支持

Mybatis所管理的SQL语句存放在XML配置文件中，号称是可以在不修改源码的情况下通过配置调整来定制数据库访问逻辑，比如适配不同的数据库方言等。
但在实际使用中，如果XML文件已经被打包到Jar包中，那么即使是进行单个SQL语句的定制也必须要复制整个配置文件，这明显是设计上的一种缺陷。
可逆计算理论为所有的DSL语言提供了统一的差量化定制语法。借助于Nop平台的基础设施，我们只需要补充少量代码，拦截Mybatis的配置文件加载过程，就可以为
Mybatis框架引入可逆计算支持，实现细粒度的差量化定制。如法炮制，同样的方法还可以被应用于Spring框架的改造。

# Mybatis的Delta定制

Mybatis内置了一个简易的分解、聚合机制：多个XML文件可以具有同样的namespace，从而聚合为一个统一的Mapper接口。

> Mybatis中namespace配置对应于Mapper接口的Java类名

在模型驱动的开发模式下，我们一般会根据模型自动生成一组增删改查的SQL语句，把它们存放在单独的Mapper文件中，通过一个标准的BaseMapper接口来映射这些SQL语句。
然后再生成一个BaseMapper的派生接口用于映射手工编写的SQL语句。例如：

1. 代码生成 [_SysUser.mapper.xml](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-demo/nop-spring-demo/src/test/resources/_vfs/nop/spring/mapper/_gen/_SysUser.mapper.xml)文件
2. 手工编写 [SysUser.mapper.xml](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-demo/nop-spring-demo/src/test/resources/_vfs/nop/spring/mapper/SysUser.mapper.xml)，
它和自动生成的Mapper具有同样的namespace。
3. 增加Java接口[SysUserMapper](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-demo/nop-spring-demo/src/test/java/io/nop/demo/spring/SysUserMapper.java)，它从BaseMapper接口继承，从而避免重复定义标准的增删改查函数。

如果我们希望实现增量式的模型驱动开发，那么每次代码生成时都需要直接覆盖_SysUser.mapper.xml文件，这样可以保证代码和模型始终保持一致。
如果我们觉得自动生成的SQL语句不满足要求怎么办？一种做法是修改代码生成器，但这样会影响到所有使用此代码生成器的模块。
另一种做法是指定SysUser.mapper.xml从_SysUer.mapper.xml继承，然后在SysUser.mapper.xml中实现同名的SQL语句，希望能够像对象继承机制一样覆盖自动生成的SQL语句。
但是很可惜，**Mybatis不支持文件继承，多个XML文件中包含同名的SQL语句会报错**。



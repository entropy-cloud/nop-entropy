# 如何为Spring和Mybatis增加可逆计算支持

Mybatis所管理的SQL语句存放在XML配置文件中，号称是可以在不修改源码的情况下通过配置调整来定制数据库访问逻辑，比如适配不同的数据库方言等。
但在实际使用中，如果XML文件已经被打包到Jar包中，那么即使是进行单个SQL语句的定制也必须要复制整个配置文件，这明显是设计上的一种缺陷。
可逆计算理论为所有的DSL语言提供了统一的差量化定制语法。借助于Nop平台的基础设施，我们只需要补充少量代码，拦截Mybatis的配置文件加载过程，就可以为
Mybatis框架引入可逆计算支持，实现细粒度的差量化定制。如法炮制，同样的方法还可以被应用于Spring框架的改造。

## 一. Mybatis的Delta定制

Mybatis内置了一个简易的分解、聚合机制：多个XML文件可以具有同样的namespace，从而聚合为一个统一的Mapper接口。

> Mybatis中namespace配置对应于Mapper接口的Java类名

在模型驱动的开发模式下，我们一般会根据模型自动生成一组增删改查的SQL语句，把它们存放在单独的Mapper文件中，通过一个标准的BaseMapper接口来映射这些SQL语句。
然后再生成一个BaseMapper的派生接口用于映射手工编写的SQL语句。例如：

1. 代码生成 [\_SysUser.mapper.xml](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-demo/nop-spring-demo/src/test/resources/_vfs/nop/spring/mapper/_gen/_SysUser.mapper.xml)文件
2. 手工编写 [SysUser.mapper.xml](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-demo/nop-spring-demo/src/test/resources/_vfs/nop/spring/mapper/SysUser.mapper.xml)，
   它和自动生成的Mapper具有同样的namespace。
3. 增加Java接口[SysUserMapper](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-demo/nop-spring-demo/src/test/java/io/nop/demo/spring/SysUserMapper.java)，它从BaseMapper接口继承，从而避免重复定义标准的增删改查函数。

如果我们希望实现增量式的模型驱动开发，那么每次代码生成时都需要直接覆盖\_SysUser.mapper.xml文件，这样可以保证代码和模型始终保持一致。
如果我们觉得自动生成的SQL语句不满足要求怎么办？一种做法是修改代码生成器，但这样会影响到所有使用此代码生成器的模块。另一种做法是指定SysUser.mapper.xml从\_SysUer.mapper.xml继承，然后在SysUser.mapper.xml中实现同名的SQL语句，希望能够像对象继承机制一样覆盖自动生成的SQL语句。但是很可惜，**Mybatis不支持文件继承，多个XML文件中包含同名的SQL语句会报错**。

## Mapper文件扫描及注册

基于Nop平台可以对Mybatis进行如下改造:

1. 增加[mapper.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-spring/nop-spring-delta/src/main/resources/_vfs/nop/spring/schema/mapper.xdef)元模型定义，定义两个节点如何进行差量合并
2. 增加NopMybatisSessionFactoryCustomizer，在其中利用Nop平台的DSL文件加载器去加载mapper文件

```java
@Service
@ConditionalOnProperty(name = "nop.spring.delta.mybatis.enabled", matchIfMissing = true)
public class NopMybatisSessionFactoryCustomizer implements SqlSessionFactoryBeanCustomizer {
    @Override
    public void customize(SqlSessionFactoryBean factoryBean) {

        List<IResource> resources = ModuleManager.instance().findModuleResources("/mapper", ".mapper.xml");

        if (!resources.isEmpty()) {
            List<Resource> locations = new ArrayList<>(resources.size());
            for (IResource resource : resources) {
                // 忽略自动生成的mapper文件，它们只能作为基类存在
                if (resource.getName().startsWith("_"))
                    continue;

                XDslExtendResult result = DslNodeLoader.INSTANCE.loadFromResource(resource);
                XNode node = result.getNode();
                node.removeAttr("xmlns:x");

                String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
                        "<!DOCTYPE mapper\n" +
                        "        PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\"\n" +
                        "        \"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n" + node.xml();
                locations.add(new ByteArrayResource(xml.getBytes(StandardCharsets.UTF_8), resource.getPath()));
            }
            factoryBean.addMapperLocations(locations.toArray(new Resource[0]));
        }
    }
}
```

* 利用ModuleManager.findModuleResources扫描所有模块的mapper目录，并收集所有后缀名为mapper.xml的资源文件

* DslNodeLoader.loadFromResource会解析XML文件，执行Delta差量合并算法，返回合成后的XNode节点。

* 将XNode的内容序列化为字节数据后包装为Spring内置的Resource接口，然后注册到Mybatis的SqlSessionFactoryBean中

DslNodeLoader加载DSL文件时会自动识别delta目录，如果发现`/_vfs/_delta/default/`目录下存在同名的文件，则会优先加载delta目录下的文件。例如 [SysUser.mapper.xml](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-demo/nop-spring-demo/src/test/resources/_vfs/_delta/default/nop/spring/mapper/SysUser.mapper.xml)

```xml
<mapper x:extends="super" x:schema="/nop/spring/schema/mapper.xdef" xmlns:x="/nop/schema/xdsl.xdef">

    <!-- 定制使用nop_auth_user表 -->
    <sql id="selectUserVo">
        select u.user_id, u.dept_id, u.user_name, u.nick_name
        from nop_auth_user u
    </sql>
</mapper>
```

在delta目录下的定制文件中，我们可以通过`x:extends="super"`来表示继承原路径下的DSL文件。

## 二. Spring的Delta定制

基于Nop平台可以对Mybatis进行如下改造:

1. 增加[beans.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/beans.xdef)元模型定义，定义两个节点如何进行差量合并
2. 增加NopBeansRegistrar，在其中利用Nop平台的DSL文件加载器去加载mapper文件

```java
@Import(NopBeansAutoConfiguration.NopBeansRegistrar.class)
@Configuration
public class NopBeansAutoConfiguration {

    public static class NopBeansRegistrar implements ImportBeanDefinitionRegistrar {
        @Override
        public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
                                            BeanDefinitionRegistry registry) {
            List<IResource> resources = ModuleManager.instance().findModuleResources("/beans", "beans.xml");
            if (resources.isEmpty())
                return;

            XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(registry);
            for (IResource resource : resources) {
                if (!resource.getName().startsWith("spring-"))
                    continue;

                XDslExtendResult result = DslNodeLoader.INSTANCE.loadFromResource(resource);
                XNode node = result.getNode();
                node.removeAttr("xmlns:x");

                Resource springResource = toResource(node);
                reader.loadBeanDefinitions(springResource);
            }
        }
   }
}


```

具体处理过程与NopMybatisSessionFactoryCustomizer类似。

利用beans.xml文件的差量定制能力，我们还可以实现对Mybatis的Mapper接口的扩展。

1. 在[spring-demo.beans.xml](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-demo/nop-spring-demo/src/test/resources/_vfs/nop/spring/beans/spring-demo.beans.xml)文件中定义sysUserMapper。NopBeansRegistrar会自动扫描所有模块的beans目录下前缀为spring的beans配置文件。(采用这种注册方式就不要再使用MapperScan注解)。

2. 从SysUserMapper接口继承，实现一个扩展接口 SysUserMapperEx接口，在其中可以增加扩展的SQL调用方法。

3. 在delta目录下可以定制[spring-demo.beans.xml](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-demo/nop-spring-demo/src/test/resources/_vfs/_delta/default/nop/spring/beans/spring-demo.beans.xml)文件，设置mapperTypeEx属性为扩展接口类型。

```xml
    <bean id="sysUserMapper" parent="nopBaseMapper">
        <property name="mapperInterface" value="io.nop.demo.spring.SysUserMapper"/>
    </bean>
```

通过这种方法，我们通过增加单独的delta模块实现对原有mapper文件和beans文件的定制，并实现Mapper接口的扩展。

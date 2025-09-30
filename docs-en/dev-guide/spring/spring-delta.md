
# How to Add Reversible Computation Support to Spring and Mybatis

The SQL statements managed by Mybatis are stored in XML configuration files, claiming that database access logic can be customized via configuration without modifying the source code, such as adapting to different database dialects.
However, in practice, if the XML files have been packaged into a JAR, even customizing a single SQL statement requires copying the entire configuration file, which is clearly a design flaw.
The theory of Reversible Computation provides a unified Delta-based customization syntax for all DSLs. Leveraging the Nop platform infrastructure, we only need to add a small amount of code to intercept Mybatis’s configuration loading process to introduce Reversible Computation support into the Mybatis framework, enabling fine-grained, Delta-based customization. By analogy, the same approach can be applied to adapting the Spring framework.

## I. Delta Customization for Mybatis

Mybatis has a simple split/aggregate mechanism: multiple XML files can share the same namespace and thus be aggregated into a single unified Mapper interface.

> In Mybatis, the namespace configuration corresponds to the Java class name of the Mapper interface

In a model-driven development mode, we typically auto-generate a set of CRUD SQL statements based on the model, store them in a dedicated Mapper file, and map these statements through a standard BaseMapper interface.
We then generate a derived interface from BaseMapper to map hand-written SQL statements. For example:

1. Code-generated [\_SysUser.mapper.xml](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-demo/nop-spring-demo/src/test/resources/_vfs/nop/spring/mapper/_gen/_SysUser.mapper.xml)
2. Manually written [SysUser.mapper.xml](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-demo/nop-spring-demo/src/test/resources/_vfs/nop/spring/mapper/SysUser.mapper.xml),
   which shares the same namespace as the auto-generated Mapper.
3. Add the Java interface [SysUserMapper](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-demo/nop-spring-demo/src/test/java/io/nop/demo/spring/SysUserMapper.java), which inherits from the BaseMapper interface to avoid redefining the standard CRUD methods.

If we want to achieve incremental model-driven development, each code generation run needs to directly overwrite the \_SysUser.mapper.xml file, ensuring that code and model remain in sync.
What if the auto-generated SQL doesn’t meet our needs? One option is to modify the code generator, but that would affect all modules using this generator. Another option is to declare that SysUser.mapper.xml inherits from \_SysUer.mapper.xml and implement SQL statements with the same names in SysUser.mapper.xml, hoping to override the auto-generated SQL like object inheritance. Unfortunately, Mybatis does not support file inheritance: if multiple XML files contain SQL statements with the same names, it will report an error.

## Mapper File Scanning and Registration

Based on the Nop platform, Mybatis can be adapted as follows:

1. Add the [mapper.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-spring/nop-spring-delta/src/main/resources/_vfs/nop/spring/schema/mapper.xdef) meta-model definition to specify how two nodes are Delta-merged
2. Add NopMybatisSessionFactoryCustomizer, which uses the Nop platform’s DSL file loader to load mapper files

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
                // Ignore auto-generated mapper files; they can only serve as base classes
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

* Use ModuleManager.findModuleResources to scan the mapper directories of all modules and collect all resources with the mapper.xml suffix

* DslNodeLoader.loadFromResource parses the XML files, performs the Delta merge algorithm, and returns the synthesized XNode.

* Serialize the contents of the XNode to bytes, wrap them as Spring’s built-in Resource, and register them with Mybatis’s SqlSessionFactoryBean

When DslNodeLoader loads DSL files, it automatically recognizes the delta directory. If a file with the same name exists under `/_vfs/_delta/default/`, it will prefer the file in the delta directory. For example [SysUser.mapper.xml](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-demo/nop-spring-demo/src/test/resources/_vfs/_delta/default/nop/spring/mapper/SysUser.mapper.xml)

```xml
<mapper x:extends="super" x:schema="/nop/spring/schema/mapper.xdef" xmlns:x="/nop/schema/xdsl.xdef">

    <!-- Customize to use table nop_auth_user -->
    <sql id="selectUserVo">
        select u.user_id, u.dept_id, u.user_name, u.nick_name
        from nop_auth_user u
    </sql>
</mapper>
```

In a customization file under the delta directory, we can use `x:extends="super"` to indicate inheritance from the DSL file at the original path.

## II. Delta Customization for Spring

Based on the Nop platform, Spring can be adapted as follows:

1. Add the [beans.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/beans.xdef) meta-model definition to specify how two nodes are Delta-merged
2. Add NopBeansRegistrar, which uses the Nop platform’s DSL file loader to load beans files

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

The handling is similar to NopMybatisSessionFactoryCustomizer.

Using the Delta customization capabilities of beans.xml, we can also extend Mybatis Mapper interfaces.

1. Define sysUserMapper in [spring-demo.beans.xml](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-demo/nop-spring-demo/src/test/resources/_vfs/nop/spring/beans/spring-demo.beans.xml). NopBeansRegistrar will automatically scan all modules’ beans directories for beans configuration files prefixed with "spring-". (When using this registration approach, do not use the MapperScan annotation.)

2. Inherit from the SysUserMapper interface and implement an extended interface, SysUserMapperEx, in which additional SQL invocation methods can be added.

3. In the delta directory, customize [spring-demo.beans.xml](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-demo/nop-spring-demo/src/test/resources/_vfs/_delta/default/nop/spring/beans/spring-demo.beans.xml), setting the mapperTypeEx attribute to the extended interface type.

```xml
    <bean id="sysUserMapper" parent="nopBaseMapper">
        <property name="mapperInterface" value="io.nop.demo.spring.SysUserMapper"/>
    </bean>
```

With this approach, by adding a separate delta module we can customize existing mapper and beans files and implement extensions to Mapper interfaces.

<!-- SOURCE_MD5:761b4153620a84e7599efa7b9181079d-->

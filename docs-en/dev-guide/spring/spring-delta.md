# How to Add Reversible Computation Support to Spring and Mybatis

Mybatis manages SQL statements in XML configuration files, claimed to allow customization of database access logic without modifying source code, such as adapting to different database dialects. However, in practical usage, if the XML file has been packaged into a Jar package, even customizing a single SQL statement requires copying the entire configuration file, which is a design flaw.

Reversible computation theory provides a unified delta customization syntax for all DSL languages. By leveraging the Nop platform's infrastructure, we can intercept Mybatis' configuration file loading process with minimal additional code and thereby introduce reversible computation support to the Mybatis framework, enabling fine-grained delta customization. The same approach can be applied to modify the Spring framework.

## 1. Mybatis Delta Customization

Mybatis has an inherent simple decomposition and aggregation mechanism: multiple XML files can have the same namespace, which are aggregated into a single unified Mapper interface.

> In Mybatis, the namespace configuration corresponds to the Java class name of the Mapper interface.

Under model-driven development, we typically generate a set of CRUD SQL statements based on models and store them in separate Mapper files. These SQL statements are mapped to a standard BaseMapper interface. Additionally, a derived interface is generated for mapping manually written SQL statements. For example:

1. Generate [\_SysUser.mapper.xml](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-demo/nop-spring-demo/src/test/resources/_vfs/nop/spring/mapper/_gen/_SysUser.mapper.xml) file
2. Manually write [SysUser.mapper.xml](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-demo/nop-spring-demo/src/test/resources/_vfs/nop/spring/mapper/SysUser.mapper.xml), which shares the same namespace with the automatically generated Mapper
3. Implement Java interface [SysUserMapper](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-demo/nop-spring-demo/src/test/java/io/nop/demo/spring/SysUserMapper.java), inheriting from the BaseMapper interface to avoid defining standard CRUD functions repeatedly.

To implement incremental model-driven development, we need to directly override [\_SysUser.mapper.xml](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-demo/nop-spring-demo/src/test/resources/_vfs/nop/spring/mapper/_gen/_SysUser.mapper.xml) each time the code is generated, ensuring consistency between code and models.

If the automatically generated SQL statements do not meet requirements, one approach is to modify the code generator, which would affect all modules using it. Another approach is to specify that [\_SysUser.mapper.xml](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-demo/nop-spring-demo/src/test/resources/_vfs/nop/spring/mapper/_gen/_SysUser.mapper.xml) should inherit from manually written SQL statements, similar to class inheritance. However, **Mybatis does not support file inheritance**, and having multiple XML files with the same namespace will result in errors.

## 2. Mapper File Scanning and Registration

Using the Nop platform, Mybatis can be modified as follows:

1. Add [mapper.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-spring/nop-spring-delta/src/main/resources/_vfs/nop/spring/schema/mapper.xdef) metadata definition to define two nodes for delta merging
2. Use NopMybatisSessionFactoryCustomizer to scan and register Mapper files on the Nop platform, leveraging its DSL file loader.

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
                // Ignore automatically generated mapper files; they should only exist as base classes
                if (resource.getName().startsWith("_"))
                    continue;

                XDslExtendResult result = DslNodeLoader.INSTANCE.loadFromResource(resource);
                XNode node = result.getNode();
                node.removeAttr("xmlns:x");

                String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
                        "<!DOCTYPE mapper\n" +
                        "        PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\"\n" +
                        "        \"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n" + node.xml();
                locations.add(new ByteArrayResource(xml.getBytes(StandardCharsets.UTF_8)), resource.getPath());
            }
            factoryBean.addMapperLocations(locations.toArray(new Resource[0]));
        }
    }
}
```

* Use ModuleManager.findModuleResources to scan all modules' mapper directories and collect all resources with the suffix .mapper.xml.

* DslNodeLoader.loadFromResource will parse the XML file, perform Delta merging, and return the composed XNode node.

* Serialize the XNode content into bytes, then wrap it in a Spring-provided Resource interface, and register it with Mybatis's SqlSessionFactoryBean.

DslNodeLoader loads DSL files and automatically identifies the delta directory. If it finds a file named similarly in the `_vfs/_delta/default/` directory, it will prioritize loading from there. For example: [SysUser.mapper.xml](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-demo/nop-spring-demo/src/test/resources/_vfs/_delta/default/nop/spring/mapper/SysUser.mapper.xml)
```xml
<mapper x:extends="super" x:schema="/nop/spring/schema/mapper.xdef" xmlns:x="/nop/schema/xdsl.xdef">
    <!-- Custom usage of nop_auth_user table -->
    <sql id="selectUserVo">
        select u.user_id, u.dept_id, u.user_name, u.nick_name
        from nop_auth_user u
    </sql>
</mapper>
```

## Delta Customization

Based on the Nop platform, Mybatis can be modified as follows:

1. **Increase [beans.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/beans.xdef) model definition**: Define how two nodes perform delta merging.
2. **Add NopBeansRegistrar**: Utilize the Nop platform's DSL file loader to load mapper files.

```java
@Import(NopBeansAutoConfiguration.NopBeansRegistrar.class)
@Configuration
public class NopBeansAutoConfiguration {

    public static class NopBeansRegistrar implements ImportBeanDefinitionRegistrar {
        @Override
        public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
                                        BeanDefinitionRegistry registry) {
            List<IResource> resources = ModuleManager.instance().findModuleResources("/beans", "beans.xml");
            if (resources.isEmpty()) {
                return;
            }

            XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(registry);
            for (IResource resource : resources) {
                if (!resource.getName().startsWith("spring-")) {
                    continue;
                }

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

The process is similar to NopMybatisSessionFactoryCustomizer.

Using the delta customization feature of beans.xml, we can also extend Mybatis's Mapper interface.



The `sysUserMapper` is defined in the [Spring-Demo.xml](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-demo/nop-spring-demo/src/test/resources/_vfs/nop/spring/beans/spring-demo.beans.xml) file. The `NopBeansRegistrar` automatically scans all modules' `beans` directories for files prefixed with "spring". Note: Do not use the `MapperScan` annotation.



The `SysUserMapper` interface is inherited, and an extended interface `SysUserMapperEx` can be implemented to add extended SQL invocation methods.



In the `delta` directory, you can customize the [Spring-Demo.xml](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-demo/nop-spring-demo/src/test/resources/_vfs/_delta/default/nop/spring/beans/spring-demo.beans.xml) file by setting the `mapperTypeEx` property to the extended interface type.

```xml
<bean id="sysUserMapper" parent="nopBaseMapper">
    <property name="mapperInterface" value="io.nop.demo.spring.SysUserMapper"/>
</bean>
```

Through this method, we extend the existing `mapper` and `beans` files by adding a separate `delta` module. This allows for the extension of the `Mapper` interface.


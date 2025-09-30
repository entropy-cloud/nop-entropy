# Spring Integration

Simply include the nop-spring-core-starter module to integrate the Spring framework with the Nop platform.

```xml
<pom>
<dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>io.github.entropy-cloud</groupId>
            <artifactId>nop-bom</artifactId>
            <version>${nop-entropy.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>

    </dependencies>
</dependencyManagement>

<dependencies>
<dependency>
    <groupId>io.github.entropy-cloud</groupId>
    <artifactId>nop-spring-core-starter</artifactId>
</dependency>
</dependencies>
</pom>
```

1. When the Spring framework finishes starting, it automatically calls the CoreInitialization.initialize() function to perform Nop platform initialization.
   See [NopSpringCoreWebConfig.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-spring/nop-spring-core-starter/src/main/java/io/nop/spring/core/autoconfig/NopSpringCoreAutoConfig.java)
2. Beans managed by the Nop platform can use @Inject to access beans in the Spring container.
3. Beans managed by the Nop platform cannot be used directly in the Spring container; you need to obtain them via methods such as BeanContainer.getBeanByType(IDaoProvider.class).
4. The Nop platform and Spring share the application.yaml configuration file. For the Nop platform’s configuration format, see [config.md](config.md).

## Use Only the NopReport Reporting Engine
If you only need the NopReport reporting engine, include the nop-report-core module on top of nop-spring-core-starter.
For a concrete example, see the nop-spring-report-demo module.

## Use Only NopOrm to Access the Database

If you only need the ORM engine provided by the NopOrm platform, include the nop-orm module on top of nop-spring-core-starter.

```xml

<dependencies>
    <dependency>
        <groupId>io.github.entropy-cloud</groupId>
        <artifactId>nop-orm</artifactId>
    </dependency>

    <dependency>
        <groupId>io.github.entropy-cloud</groupId>
        <artifactId>nop-sys-dao</artifactId>
    </dependency>
</dependencies>
```

If you need to use the NopSysSequence table to centrally manage sequences, or need extension-field support, include the nop-sys-dao module.

If you prefer to share the DataSource with Spring rather than use the Nop platform’s internally managed nopDataSource, configure:

```
nop.dao.use-parent-data-source=true
```

This configuration disables the internal nopDataSource definition in the Nop platform and maps nopDataSource to the alias dataSource. Therefore, the Spring container must provide a DataSource bean named dataSource.

## Use Spring Transaction Management to Unify Transactions

Note: If you use Spring transactions, NopORM’s asynchronous execution logic will be incorrect; you must use synchronous execution.

```
nop.dao.use-parent-transaction-factory=true
```

## Use NopGraphQL Services

If you need to use NopGraphQL services, include the nop-spring-web-starter module.

```xml

<dependencies>
    <dependency>
        <groupId>io.github.entropy-cloud</groupId>
        <artifactId>nop-spring-web-starter</artifactId>
    </dependency>
</dependencies>
```

If you need ORM support, include nop-spring-web-orm-starter. It will automatically include nop-orm and thus bring in data source configuration. You must configure parameters such as nop.datasource.jdbc-url in application.yaml, or set nop.orm.use-parent-data-source=true.

## Integrate the Nop Platform’s AMIS Frontend

Follow the relevant configuration of the [nop-for-ruoyi project](https://gitee.com/canonical-entropy/nop-for-ruoyi).
Bilibili video: [Integrating the Nop Platform into the Ruoyi Framework](https://www.bilibili.com/video/BV1Av4y157D7/)

## Add Delta Customization Capability for Spring and MyBatis

Include the nop-spring-delta module. Then the `beans/spring-*.beans.xml` files and `mapper/*.mapper.xml` files within the module support Delta customization.

nop-spring-delta also adds customization capability for Java Mapper interfaces:

```xml

<bean id="sysUserMapper" parent="nopBaseMapper">
    <property name="mapperInterface" value="io.nop.demo.spring.SysUserMapper"/>
    <property name="mapperTypeEx" value="io.nop.demo.spring.SysUserMapperEx"/>
</bean>
```

mapperInterface corresponds to the namespace configuration in the Mapper XML file. In general, mapperTypeEx is the same as the namespace, but in an extension module you can configure mapperTypeEx to use an extended interface.

For design details, see [spring-delta.md](spring/spring-delta.md).

## Use nop-all-for-spring to Simplify Package Management

The Nop platform adopts a multi-module design and has not yet been published to Maven Central. If you want to upload Nop platform artifacts to a private repository for management, you can follow the approach of the nop-all-for-spring module: first package the Nop platform artifacts you use into a single nop-all-for-xxx.jar, and then you only need to upload that one jar.

The nop-spring-demo2 project demonstrates how to use nop-all-for-spring.

> Because the Nop platform kernel has been upgraded to Quarkus 3.0, to use it with Spring 2.X you need to explicitly specify the versions of the jakarta-related packages; otherwise, compatibility issues will occur.

### Rename Packages During Packaging

The nop-maven-shaded-plugin provides package renaming functionality. With the following configuration, you can automatically rename to Java package names during packaging and automatically modify the corresponding names in all DSL files.

```xml
   <plugin>
       <groupId>org.apache.maven.plugins</groupId>
       <artifactId>maven-shade-plugin</artifactId>
       <version>3.5.0</version>
       <executions>
           <execution>
               <phase>package</phase>
               <goals>
                   <goal>shade</goal>
               </goals>
               <configuration>
                   <transformers>
                       <transformer implementation="org.apache.maven.plugins.shade.resource.ServicesResourceTransformer"/>
                       <transformer implementation="io.nop.maven.plugin.shaded.XdslResourceTransformer" />
                       <transformer implementation="io.nop.maven.plugin.shaded.SpringFactoryResourceTransformer" />
                   </transformers>
                   <artifactSet>
                       <includes>
                           <include>io.github.entropy-cloud:*</include>
                       </includes>
                   </artifactSet>
                   <filters>
                       <filter>
                           <artifact>*:*</artifact>
                           <excludes>
                               <exclude>META-INF/maven/**</exclude>
                               <exclude>META-INF/native-image/**</exclude>
                           </excludes>
                       </filter>
                   </filters>

                   <relocations>
                       <relocation>
                           <pattern>io.nop</pattern>
                           <shadedPattern>com.xxx</shadedPattern>
                       </relocation>
                   </relocations>
               </configuration>
           </execution>
       </executions>

       <dependencies>
           <dependency>
               <groupId>io.github.entropy-cloud</groupId>
               <artifactId>nop-maven-shaded-plugin</artifactId>
               <version>${nop-entropy.version}</version>
           </dependency>
       </dependencies>
   </plugin>
```
<!-- SOURCE_MD5:ee5d09665d1ececac88d6266cd2b8835-->

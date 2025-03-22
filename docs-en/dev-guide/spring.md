# Spring Integration

To integrate the Spring framework with the Nop platform, simply include the `nop-spring-core-starter` module in your project's dependencies.

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

1. After the Spring framework is initialized, `CoreInitialization.initialize()` will be automatically called to perform Nop platform initialization.
   See [NopSpringCoreAutoConfig.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-spring/nop-spring-core-starter/src/main/java/io/nop/spring/core/autoconfig/NopSpringCoreAutoConfig.java).
2. Nop platform beans can be injected into the Spring container using `@Inject`.
3. Direct access to Nop platform beans in the Spring container is not possible; use methods like `BeanContainer.getBeanByType(IDaoProvider.class)` instead.
4. Configuration for both Nop and Spring is managed through `application.yaml` files. The Nop configuration format is documented in [config.md](config.md).

## Database Access Using NopOrm

If you only need to use NopOrm's ORM engine, include the `nop-orm` module alongside `nop-spring-core-starter`.

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

If you need to use NopSysSequence for sequence management or require field extension support, include the `nop-sys-dao` module.

If you prefer using Spring's `DataSource` instead of Nop's internal `nopDataSource`, configure it as follows:

```properties
nop.dao.use-parent-data-source=true
```

This setting disables Nop's internal `nopDataSource` and maps it to `dataSource`. Therefore, the Spring container must provide a data source named `dataSource`.

## Transaction Management with Spring

Note that if you use Spring transactions, NopOrm's asynchronous execution logic will not function correctly. Use synchronous execution instead.

```properties
nop.dao.use-parent-transaction-factory=true
```

## Using NopGraphQL Service

To use the NopGraphQL service, include the `nop-spring-web-starter` module in your project.


```xml
<dependencies>
    <dependency>
        <groupId>io.github.entropy-cloud</groupId>
        <artifactId>nop-spring-web-starter</artifactId>
    </dependency>
</dependencies>    
```

If you need ORM support, include `nop-spring-web-orm-starter`, which will automatically include `nop-orm` and its data source configuration, requiring parameters like `nop.datasource.jdbc-url` or `nop.orm.use-parent-data-source=true` in your `application.yaml`.

## Integrating Nop Platform's AMIS Frontend

Follow the configuration of the `[nop-for-ruoyi项目](https://gitee.com/canonical-entropy/nop-for-ruoyi)` project, and refer to the Bilibili video: [Ruoyi框架集成Nop平台](https://www.bilibili.com/video/BV1Av4y157D7/).

## Enhancing Spring and MyBatis with Delta Capabilities

Include `nop-spring-delta` module to enable Delta customization. The `beans/spring-*.beans.xml` and `mapper/*.mapper.xml` files support Delta customization.

For Java, the `Mapper` interface has been enhanced with Delta capabilities:

```xml
<bean id="sysUserMapper" parent="nopBaseMapper">
    <property name="mapperInterface" value="io.nop.demo.spring.SysUserMapper"/>
    <property name="mapperTypeEx" value="io.nop.demo.spring.SysUserMapperEx"/>
</bean>
```

`mapperInterface` corresponds to the `namespace` configuration in Mapper XML files, which is typically the same as `mapperTypeEx`. However, in extended modules, `mapperTypeEx` can be customized using extended interfaces.

For detailed principles, refer to `[spring-delta.md](spring/spring-delta.md)`.

## Simplifying Package Management with nop-all-for-spring

The Nop platform employs a multi-module design. Currently, it has not been uploaded to the Maven Central Repository. To manage its packages via private repositories, follow the approach of `nop-all-for-spring` module: bundle related modules into a single `nop-all-for-xxx.jar` and upload this file.

The `nop-spring-demo2` project demonstrates how to use `nop-all-for-spring`.

> **Note:** Since Nop's core has been upgraded to Quarkus 3.0, using it with Spring 2.X requires explicitly specifying the Jakarta package versions to avoid compatibility issues.

### Package Renaming during Build

Use `nop-maven-shaded-plugin` to enable package renaming. Configure it to automatically rename packages to their Java equivalents and adjust corresponding DSL files during build.

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
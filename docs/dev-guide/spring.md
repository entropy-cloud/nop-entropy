# Spring集成

只需引入nop-spring-core-starter模块即可实现Spring框架和Nop平台的集成

````xml
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
````

1. Spring框架启动完毕的时候自动调用CoreInitialization.initialize()函数来执行Nop平台的初始化.
参见[NopSpringCoreWebConfig.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-spring/nop-spring-core-starter/src/main/java/io/nop/spring/core/autoconfig/NopSpringCoreAutoConfig.java)
2. Nop平台管理的bean可以通过@Inject来使用Spring容器中的bean
3. Spring容器中无法直接使用Nop平台管理的bean，需要使用 BeanContainer.getBeanByType(IDaoProvider.class)等方法来获取
4. Nop平台和Spring共用application.yaml配置文件，Nop平台的配置格式参见 [config.md](config.md)

## 仅使用NopOrm来访问数据库
如果仅需要使用NopOrm平台提供的ORM引擎，可以在nop-spring-core-starer的基础上再引入nop-orm模块

````xml
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
````
如果需要使用NopSysSequence表来统一管理sequence，或者需要扩展字段支持，则可以引入nop-sys-dao模块。

如果希望和Spring共用DataSource，而不是在Nop平台中使用自己管理的nopDataSource，则可以配置

````
nop.orm.use-parent-data-source=true
````

这个配置会禁用Nop平台内部的nopDataSource定义，然后将nopDataSource对应于别名dataSource。因此要求Spring容器中提供一个名称为dataSource的数据源定义。

## 使用NopGraphQL服务
如果需要使用NopGraphQL服务，则可以引入nop-spring-web-starter模块

````xml
    <dependencies>
        <dependency>
            <groupId>io.github.entropy-cloud</groupId>
            <artifactId>nop-spring-web-starter</artifactId>
        </dependency>
    </dependencies>    
````

## 集成Nop平台的AMIS前端
仿照[nop-for-ruoyi项目](https://gitee.com/canonical-entropy/nop-for-ruoyi)的相关配置.
B站视频: [在若依(Ruoyi)框架中集成Nop平台](https://www.bilibili.com/video/BV1Av4y157D7/)

## 为Spring和MyBatis增加Delta定制能力
引入 nop-spring-delta模块，然后模块目录下的`beans/spring-*.beans.xml`文件以及`mapper/*.mapper.xml`文件支持Delta定制。

nop-spring-delta对于Java中的Mapper接口也增加了定制能力

````xml
    <bean id="sysUserMapper" parent="nopBaseMapper">
        <property name="mapperInterface" value="io.nop.demo.spring.SysUserMapper" />
        <property name="mapperTypeEx" value="io.nop.demo.spring.SysUserMapperEx"/>
    </bean>
````

mapperInterface对应于Mapper XML文件中的namespace配置，一般情况下mapperTypeEx与namespace相同，但是在扩展模块中我们可以配置mapperTypeEx使用扩展接口。

具体设计原理参加 [spring-delta.md](spring-spring-delta.md)
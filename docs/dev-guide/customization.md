# Delta定制

Nop平台与其他开发平台相比，一个显著的特异之处在于它支持在完全不修改平台源码的情况下，通过Delta定制机制实现对平台功能的深度定制。

整体架构说明，参见[delta-customization.md](delta/delta-customization.md)

## 定制bean

Nop平台中所有的bean都由NopIoC容器统一管理，它是一个语法类似Spring1.0，但是增加了类似SpringBoot的条件装配和自动装配机制的，完全声明式的一个IoC容器。

**为了避免命名冲突，Nop平台内置的bean的id一般以nop为前缀，例如nopJdbcTemplate等**

### 查看bean的定义

为了定制bean，首先可以查看`_dump`目录下的/nop/main/beans/merged-app.beans.xml文件。系统在调试模式下启动时会自动将所有被激活的bean的配置都输出
到这个调试文件中。通过查看这个文件，可以获知系统中所有使用到的bean的配置细节。

```xml

<!--LOC:[15:6:0:0]/nop/auth/beans/auth-service.beans.xml-->
    <bean class="io.nop.auth.service.auth.DefaultActionAuthChecker" id="$DEFAULT$nopActionAuthChecker" ioc:aop="false"
          name="nopActionAuthChecker">
        <property name="siteMapProvider" ext:autowired="true">
            <ref bean="nopSiteMapProvider" ext:resolved-loc="[13:6:0:0]/nop/auth/beans/auth-service.beans.xml"
                 ext:resolved-trace="/nop/auth/beans/app-service.beans.xml"/>
        </property>
    </bean>
```

这个文件中可以看出每个bean所在的源文件位置，例如上面的示例表示

1. 名为nopActionAuthChecker的bean在auth-service.beans.xml文件中定义。
2. siteMapProvider属性使用@Inject注解自动注入，实际注入的bean为 nopSiteMapProvider
3. nopSiteMapProvider的源码位置为 auth-service.beans.xml文件的第13行。
4. `id="$DEFAULT$nopActionAuthChecker"` 表示它定义的是一个default实现，如果存在一个具有相同名称的bean，则会自动替换这个实现。

nopActionAuthChecker的原始定义如下，节点上标记了ioc:default='true'。如果存在另外一个bean的name也是nopActionAuthChecker，则会自动
覆盖这个缺省定义。ioc:default的作用类似于SpringBoot中的ConditionOnMissingBean

```xml
    <bean id="nopActionAuthChecker" class="io.nop.auth.service.auth.DefaultActionAuthChecker" ioc:default="true"/>
```

### 替换或者删除系统中的bean定义

如果需要替换系统中bean的定义，只需要在/\_vfs/\_delta/default目录下定义同名的beans.xml文件，然后在其中使用x:extends机制来定制

```xml

<beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef"
       x:extends="super">
    <!-- 可以通过这种方式删除系统中内置的bean的定义-->
    <bean id="nopActionAuthChecker" x:override="remove" />
</beans>
```

也可以替换bean的实现类，例如

```xml
<beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef"
       x:extends="super">
    <!-- 这里的定义会和平台中的定义节点进行合并 -->
    <bean id="nopActionAuthChecker" claass="xxx.MyActionAuthChecker" />
</beans>
```

## 扩展Excel模型

参见[custom-model.md](model/custom-model.md)

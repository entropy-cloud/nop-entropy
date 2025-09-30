# Delta Customization

Compared with other development platforms, a distinctive characteristic of the Nop platform is that it supports deep customization of platform capabilities via the Delta customization mechanism without modifying the platform source code at all.

For the overall architecture, see [delta-customization.md](delta/delta-customization.md)

## Customizing beans

All beans in the Nop platform are uniformly managed by the NopIoC container. It is a fully declarative IoC container whose syntax is similar to Spring 1.0, but it adds conditional wiring and auto-wiring mechanisms similar to Spring Boot.

**To avoid naming conflicts, built-in beans in the Nop platform typically use the prefix nop for their IDs, such as nopJdbcTemplate, etc.**

### Viewing bean definitions

To customize a bean, first check the /nop/main/beans/merged-app.beans.xml file under the `_dump` directory. When the system starts in debug mode, it automatically outputs the configurations of all activated beans to this debug file. By inspecting this file, you can learn the configuration details of all beans used in the system.

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

From this file you can see the source file location of each bean. For example, the above snippet indicates:

1. The bean named nopActionAuthChecker is defined in the auth-service.beans.xml file.
2. The siteMapProvider property is auto-injected using the @Inject annotation, and the actual injected bean is nopSiteMapProvider.
3. The source location of nopSiteMapProvider is line 13 of the auth-service.beans.xml file.
4. `id="$DEFAULT$nopActionAuthChecker"` indicates it defines a default implementation; if there is another bean with the same name, it will automatically replace this implementation.

The original definition of nopActionAuthChecker is as follows, with the node marked ioc:default='true'. If there is another bean whose name is also nopActionAuthChecker, it will automatically override this default definition. The role of ioc:default is similar to Spring Boot's ConditionalOnMissingBean.

```xml
    <bean id="nopActionAuthChecker" class="io.nop.auth.service.auth.DefaultActionAuthChecker" ioc:default="true"/>
```

### Replacing or removing bean definitions in the system

If you need to replace a bean definition in the system, simply define a beans.xml file with the same name under the /\_vfs/\_delta/default directory, and then use the x:extends mechanism within it to customize.

```xml

<beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef"
       x:extends="super">
    <!-- You can use this approach to remove built-in bean definitions in the system -->
    <bean id="nopActionAuthChecker" x:override="remove" />
</beans>
```

You can also replace the bean's implementation class, for example:

```xml
<beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef"
       x:extends="super">
    <!-- The definition here will be merged with the definition node in the platform -->
    <bean id="nopActionAuthChecker" class="xxx.MyActionAuthChecker" />
</beans>
```

## Extending the Excel model

See [custom-model.md](model/custom-model.md)
<!-- SOURCE_MD5:8d241f01e839941ffbd1b0264be74490-->

# Delta Customization

The Nop platform distinguishes itself from other development platforms in that it supports deep customization through the Delta customization mechanism without modifying the platform's source code.

For a detailed explanation of the architecture, refer to [delta-customization.md](delta/delta-customization.md).


## Customizing Beans

In the Nop platform, all beans are managed by the NopIoC container. It is syntactically similar to Spring 1.0 but includes features like conditional registration and automatic wiring, similar to SpringBoot.

To avoid naming conflicts, bean IDs in the Nop platform are typically prefixed with "nop", such as nopJdbcTemplate.


### Viewing Bean Definitions

To customize a bean, you can start by examining the configuration files in the `_dump` directory. Specifically, look at the `/nop/main/beans/merged-app.beans.xml` file. When the application runs in debug mode, the NopIoC container automatically logs all active beans and their configurations into this file.

Here is an example of a bean definition in the XML configuration file:

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

This configuration shows that the `nopActionAuthChecker` bean is defined in `auth-service.beans.xml`. The `siteMapProvider` property is automatically injected using the `@Inject` annotation, referencing the `nopSiteMapProvider` bean.

If there are multiple beans with the same name, the last one encountered will override the previous definitions. This behavior is similar to SpringBoot's `ConditionalOnMissingBean`.


### Replacing or Removing Bean Definitions

To replace or remove a bean definition, you can create a new configuration file in the `/_vfs/_delta/default` directory. In this file, define the same bean ID with an `x:extends` directive to override the default configuration.

Here is an example of overriding a bean:

```xml
<beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef"
       x:extends="super">
    <bean id="nopActionAuthChecker" x:override="remove"/>
</beans>
```

This configuration removes the default `nopActionAuthChecker` bean, effectively replacing it with a custom definition.

You can also choose to remove the bean's implementation by defining it in the same file:

```xml
<beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef"
       x:extends="super">
    <bean id="nopActionAuthChecker" x:override="remove">
        <bean class="CustomImplementation" id="nopActionCustomBean"/>
    </bean>
</beans>
```

```xml
<beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef"
       x:extends="super">
    <!-- Merging definitions here -->
    <bean id="nopActionAuthChecker" class="xxx.MyActionAuthChecker" />
</beans>
```

## Extended Excel Model

Refer to [custom-model.md](model/custom-model.md)

# Dynamic Configuration Management

## Configuration Conventions

* Configuration variables use all lowercase letters, with `.` and `-` as separators. Unlike Spring’s convention, NopConfig does not actually support mixed-case naming.
* When passing configuration variables via environment variables, `.` is replaced by `_`, `-` is replaced by `__`, and `_` is replaced by `___`. For example, nop.auth.sso.server-url becomes `NOP_AUTH_SSO_SERVER__URL`. (This convention is different from Spring, but it guarantees a bidirectional, unambiguous mapping between environment variables and configuration variables.)
* Typically, configuration variables explicitly used within a module are defined in the module’s XXXConfigs constants class.

## Configuration Load Order

Configurations loaded earlier have higher priority and will be used first.

1. Java’s System.getenv(): StringHelper.envToConfigVar(envName) is responsible for converting environment variable names to configuration item names
2. Java’s System.getProperties()
3. classpath:bootstrap.yaml Startup configuration for the application; you can specify the location via `nop.config.bootstrap-location`
4. Configuration center `{nop.application.name}-{nop.profile}.yaml`
5. Configuration center `{nop.application.name}.yaml`
6. Configuration center `{nop.product.name}.yaml`
7. `nop.config.key-config-source.paths` parameter to specify k8s SecretMap mapping files; periodically scanned to detect updates
8. `nop.config.props-config-source.paths` parameter to specify k8s ConfigMap mapping files; periodically scanned to detect updates
9. If parameters such as `nop.config.jdbc.jdbc-url` are configured, configuration will be loaded from the database configuration table; periodically scanned to detect updates
10. Extended configuration files specified by the `nop.config.additional-location` parameter
11. Configuration file specified by the `nop.config.location` parameter; its default value is `classpath:application.yaml`
12. Profile configurations specified by `nop.profile` and `nop.profile.parent`, which have higher priority than application.yaml
13. Recognize profile configuration prefixes as defined by the Quarkus configuration specification, such as `'%dev.'`; adjust the access order of profile-specific items according to the current profile. For example, in dev mode, the value of `%dev.a.b.c` will override the value of `a.b.c`.

> The specific configuration loading logic is centralized in the [ConfigStarter.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-config/src/main/java/io/nop/config/starter/ConfigStarter.java) class.

* **You can configure `nop.profile=dev` in bootstrap.yaml or application.yaml to enable application-dev.yaml, similar to Spring’s profile concept.** You can also configure the profile via Java properties or environment variables, e.g. `-Dnop.profile=dev` or set the environment variable `NOP_PROFILE=dev`.
* Configuration files with the `.yaml` suffix are preferred. If not found, files with the same name but `.yml` suffix will be tried. In other words, if both `application.yaml` and `application.yml` exist, the former will be used preferentially.
* You can use `nop.profile.parent` to specify multiple active profiles; their priority is from left to right. For example `nop.profile=dev`, `nop.profile.parent=mysql,nacos` corresponds to the following load order: `application-dev.yaml -> application-mysql.yaml -> application-nacos.yaml -> application.yaml`.

## Extend ConfigStarter
* `ConfigStarter.registerInstance` can register a derived class of ConfigStarter; extended ConfigStarters can be registered before CoreInitialization executes.
* You can also avoid extending ConfigStarter and instead add an implementation of IConfigSourceLoader to achieve extension. ConfigStarter loads all IConfigSourceLoader implementations via JDK’s ServiceLoader mechanism.

## Remote Configuration Center
The `nop-config` module only provides the foundational framework and interfaces for configuration; specific remote configuration center support requires adding the corresponding implementation package. For example, to use Nacos, include the `nop-cluster-nacos` package.

* nop.config.nacos.server-addr=localhost
* nop.config.nacos.username
* nop.config.nacos.password
* nop.config.nacos.timeout
* nop.config.nacos.group=public
* nop.config.nacos.namespace=DEFAULT

Note that namespace must be configured with the namespace id, not the name.

## Automatic Updates

After the configuration center distributes configuration, configuration items in the application will be updated automatically. The update process is executed by a single thread to ensure sequential execution.

* Use the IConfigReference interface in the application to dynamically obtain parameters and get the latest value when the parameter is updated.

```javascript
  static final IConfigReference<Boolean> CFG_USE_CACHE = AppConfig.varRef("global.use_cache",true);

  public void myFunc(){
     if(CFG_USE_CACHE.get()){
       // ...
     }
  }
```

* The ioc:config object configured in the IoC container will be automatically updated, and all beans referencing that config object will re-trigger their configuration update functions. See the IoC container configuration documentation for details.

```xml
<beans>
  <ioc:config id="myConfig" class="xxx.MyConfig" prefix="app.xxx" />

  <bean id="myBean" class="xxx.MyBean">
    <property name="config" ref="myConfig" />
  </bean>
</beans>
```

When myConfig changes, it will trigger myBean’s configuration update function. If myBean implements the IConfigRefreshable interface, the IConfigRefreshable#refreshConfig method will be invoked.

* Mark configuration values that need dynamic updates in the IoC container via @r-cfg

```xml
  <bean id="myBean" class="xxx.MyBean">
    <property name="propA" value="@r-cfg: app.xxx.yyy" />
    <property name="propB" value="@cfg: app.xxx.zzz" />
  </bean>
```

The `@cfg` prefix means to retrieve the configuration variable’s value, but only once at bean initialization. `@r-cfg:` indicates reactive config; when the configuration item’s value changes, myBean.setPropA will be invoked again to update the property value.

## Initialization Process

1. Load the startup configuration specified by `nop.config.bootstrap-location`
2. Load IConfigService via the ServiceLoader mechanism and start IConfigService
3. Create an IConfigExecutor. This executor is responsible for executing all configuration change operations, ensuring single-threaded updates.
4. Pull dynamic configuration from the remote configuration center via IConfigService
5. Load the application configuration specified by `nop.config.location` and `nop.config.additional-location`
6. Construct a DefaultConfigProvider and initialize the AppConfig object
7. Initialize the VirtualFileSystem
8. Load the ConfigModel and normalize configuration item data types
9. Load IConfigPlugin plugins via the ServiceLoader mechanism and start them in order of priority. The IoC module provides IocConfigPlugin, which initializes and starts the IoC container. Meanwhile, the IoC container also manages the ClassLoader.

## Sharing Configuration with Spring and Quarkus Frameworks

The default configuration file name for the Nop platform is application.yaml, which is the same as the configuration file name used by the Quarkus and Spring frameworks. Therefore, the content configured in this file can be read by Spring/Quarkus as well as by the Nop platform. However, note that Spring performs a naming normalization process: it automatically normalizes mixed-case variable names to hyphen-separated names, e.g., spring.datasource.jdbcUrl is normalized to spring.datasource.jdbc-url. The Nop platform, in the interest of consistency, does not introduce this normalization process, so configuration behavior will differ. Additionally, when passing configuration parameters via environment variables, the Spring framework uses a heuristic approach that reads multiple times, which has some performance impact, whereas the Nop platform applies deterministic rules to normalize environment variable names. For example, nop.datasource.jdbc\_url corresponds to the fixed environment variable name NOP\_DATASOURCE\_JDBC\_\_URL; this normalization differs from Spring. See the syntax rules in `StringHelper.envToConfigVar(envName)` for details.

## Common Configuration Parameters

* nop.debug: true
  Enable debugging to output more logs. Enables /p/DevDoc__graphql and various other debug output links.

* nop.profile
  Enable additional configuration files such as application-{profile}.yaml

* nop.application.name
  Application name, also used when registering with the service center

* nop.orm.init-database-schema: true
  If the database is empty, automatically create all tables

* nop.auth.login.allow-create-default-user
  If the user table is empty, automatically create the default account nop with password 123

* nop.auth.sso.enabled
  Enable SSO login. If set to true, parameters such as nop.auth.sso.url need to be configured

* nop.auth.sso.server-url
  Address of single sign-on servers such as Keycloak

* nop.auth.sso.realm
  Realm configuration required by single sign-on servers such as Keycloak

* nop.auth.sso.client-id
  client-id setting required by single sign-on servers such as Keycloak

* nop.auth.sso.client-secret
  client-secret setting required by single sign-on servers such as Keycloak

* nop.auth.jwt.enc-key
  Key used when JWT uses the AES encryption algorithm.

* nop.datasource.jdbc-url
  JDBC configuration

* nop.web.validate-page-model
  Whether to automatically check at system startup that all page.yaml files can be parsed and loaded correctly.

* quarkus.log.level
  Log level setting for the Quarkus framework

<!-- SOURCE_MD5:d3c51bf09c9aec372487e5f6f00f243c-->

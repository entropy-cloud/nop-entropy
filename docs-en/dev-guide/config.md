# Dynamic Configuration Management

## Configuration Specification

* Configuration variables are in lowercase letters, separated by `.` and `-`, unlike Spring's conventions. NopConfig does not support mixed-case naming.
* If configuration variables are passed via environment variables, the dot `.` is replaced with `_`, and the hyphen `-` is replaced with `__`. For example, `nop.auth.sso.server-url` becomes `NOP_AUTH_SSO_SERVER_URL`.
* This convention ensures an unambiguous bi-directional mapping between environment variables and configuration variables.

* Configuration variables used within a module are defined in the XXXConfigs constant class of that module.

## Configuration Load Order

The order in which configurations are loaded determines their priority, with earlier loads taking higher precedence.

1. `java System.getenv(): StringHelper.envToConfigVar(envName)` - Converts environment variable names to configuration item names.
2. `java System.getProperties()` - Returns system properties.
3. `classpath:bootstrap.yaml` - The application's bootstrap configuration can be specified using `nop.config.bootstrap-location`.
4. `{nop.application.name}-{nop.profile}.yaml`
5. `{nop.application.name}.yaml`
6. `{nop.product.name}.yaml`
7. `nop.config.key-config-source.paths` - Specifies the paths for key configurations, using k8s SecretMap.
8. `nop.config.props-config-source.paths` - Specifies the paths for props configurations, using k8s ConfigMap.
9. If `nop.config.jdbc.jdbc-url` is set, it will load from a database configuration table.
10. `nop.config.additional-location` - Specifies additional extended configuration files.
11. `nop.config.location` - Specifies the main configuration file, with a default of `classpath:application.yaml`.
12. `nop.profile` and `nop.profile.parent` - Specify profiles, with parent profiles taking precedence over application.yaml.
13. Quarkus-specific configurations use `%dev.` prefixes for profile-specific configurations.

The loading logic is entirely contained within `[ConfigStarter.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-config/src/main/java/io/nop/config/starter/ConfigStarter.java)` in the ConfigStarter class.

## Remote Configuration

The `nop-config` module only provides basic configuration framework and interfaces. For remote configurations, specific implementations like Nacos are required, which need to be integrated by adding the corresponding implementation package (`nop-cluster-nacos`).

* `nop.config.nacos.server-addr=localhost`
* `nop.config.nacos.username`
* `nop.config.nacos.password`

* nop.config.nacos.timeout
* nop.config.nacos.group=public
* nop.config.nacos.namespace=DEFAULT

Note: The namespace requires the configuration of the namespace ID, not the name.

## Automatic Update

After the configuration is pushed to the configuration center, the application's configuration items will automatically update. The update process is executed in a single thread to ensure sequential execution of updates.

* The application uses the `IConfigReference` interface to dynamically retrieve parameters. When parameters are updated, the latest values can be obtained.

```javascript
static final IConfigReference<Boolean> CFG_USE_CACHE = AppConfig.varRef("global.use_cache", true);

public void myFunc() {
    if (CFG_USE_CACHE.get()) {
        // ...
    }
}
```

* In the IoC container, the `ioc:config` object is automatically updated. All beans referencing this config object will trigger configuration updates. For detailed information, refer to the IoC container's configuration documentation.

```xml
<beans>
    <ioc:config id="myConfig" class="xxx.MyConfig" prefix="app.xxx" />

    <bean id="myBean" class="xxx.MyBean">
        <property name="config" ref="myConfig" />
    </bean>
</beans>
```

When `myConfig` changes, it will trigger the configuration update function of `myBean`. If `myBean` implements the `IConfigRefreshable` interface, it will call `IConfigRefreshable#refreshConfig`.

* In the IoC container, `@r-cfg` is used to mark configuration values that need dynamic updates. When the value of these configuration items changes, `myBean.setPropA` will be called to update the attribute.

```xml
<bean id="myBean" class="xxx.MyBean">
    <property name="propA" value="@r-cfg: app.xxx.yyy" />
    <property name="propB" value="@cfg: app.xxx.zzz" />
</bean>
```

`@cfg` prefix indicates the retrieval of configuration variable values, but it only occurs once during bean initialization. `@r-cfg:` indicates reactive config, which will trigger updates when the configuration item's value changes.

## Initialization Process

1. Load the startup configuration specified by `nop.config.bootstrap-location`.
2. Use the ServiceLoader mechanism to load `IConfigService` and start it.
3. Create `IConfigExecutor`. This executor is responsible for executing all configuration change operations in a single thread.
4. Pull dynamic configurations from the remote configuration center using `IConfigService`.
5. Load application-specific configurations specified by `nop.config.location` and `nop.config.additional-location`.
6. Instantiate `DefaultConfigProvider` and `AppConfig`.
7. Initialize `VirtualFileSystem`.
8. Load `ConfigModel` and standardize the configuration item data types.
9. Use ServiceLoader to load `IConfigPlugin` and start it in order of priority. The IoC module provides `IocConfigPlugin`, which initializes and starts the IoC container. Additionally, the IoC container manages the `ClassLoader`.

## Sharing Configuration with Spring and Quarkus

The default configuration file for Nop is `application.yaml`, which shares the same name as the configurations for both Spring and Quarkus frameworks. Therefore, configurations set in this file can be read by both Spring/Quarkus and Nop platforms.

However, note that Spring has a naming convention standard where properties like `spring.datasource.jdbcUrl` are standardized using hyphens. Nop platform does not introduce such standardization to maintain consistency, so configuration behaviors may differ between Spring and Nop. Additionally, when configurations are passed via environment variables, Spring might guess-read them multiple times, which can affect performance.


- `nop.debug: true`
  Enable debug functionality, which outputs more detailed logs.

- `nop.profile`
  Enable profile-specific configuration files like `application-{profile}.yaml`.

- `nop.application.name`
  Application name used for registration in the service center, same as the example provided.

- `nop.orm.init-database-schema: true`
  Initialize database schema if empty.

- `nop.auth.login.allow-create-default-user`
  Allow creation of a default user if the users table is empty. Default user will have credentials like "nop, password123".

- `nop.auth.sso.enabled`
  Enable Single Sign-On (SSO). If set to true, additional parameters like `nop.auth.sso.url` must be configured.

- `nop.auth.sso.server-url`
  URL of the Single Sign-On server, e.g., Keycloak.

- `nop.auth.sso.realm`
  Realm configuration for the Single Sign-On server.

- `nop.auth.sso.client-id`
  Client ID required by the Single Sign-On server.

- `nop.auth.sso.client-secret`
  Client secret required by the Single Sign-On server.

- `nop.auth.jwt.enc-key`
  Encryption key used by JWT when using AES algorithm.

- `nop.datasource.jdbc-url`
  JDBC connection URL for database access.

- `nop.web.validate-page-model`
  Whether to automatically validate all `page.yaml` files during system startup.

- `quarkus.log.level`
  Log level settings for the Quarkus framework.


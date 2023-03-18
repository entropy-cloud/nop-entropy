## 配置规范
* 配置变量为全小写字母，以`.`和`-`为分隔符，与Spring的约定不同，NopConfig实际上不支持大小写混排的命名方式
* 如果通过环境变量来传递配置变量，则`.`被替换为`_`，而`-`被替换为`__`，而`_`被替换为`___`。例如nop.auth.sso.server-url变成NOP_AUTH_SSO_SERVER__URL。（这种约定与Spring并不一致，但它保证了环境变量和配置变量的双向无歧义映射）
* 一般在模块的XXXConfigs常量类中定义本模块中明确使用到的配置变量。

## 配置加载顺序
先加载的配置优先级更高，会被优先使用。

1. classpath:bootstrap.yaml 应用的启动配置，其中的所有变量都是固定值，不会被动态覆盖
2. 配置中心的{nop.application.name}-{nop.config.profile}.yaml
3. 配置中心的{nop.application.name}.yaml
4. java的System.getProperties()
5. java的System.getenv(): StringHelper.envToConfigVar(envName)负责把环境变量名转换为配置项名称
6. nop.config.additional-location参数指定的配置文件
7. nop.config.location参数指定的配置文件，它的缺省值为 classpath:application.yaml

## 自动更新

配置中心下发配置之后，应用程序中的配置项会自动更新。更新过程通过单线程执行，确保按顺序执行更新。

* 应用程序中使用IConfigReference接口来动态获取参数，当参数更新时可以获得最新值

```
  static final IConfigReference<Boolean> CFG_USE_CACHE = AppConfig.varRef("global.use_cache",true);
  
  public void myFunc(){
     if(CFG_USE_CACHE.get()){
       // ...
     }
  }
```

* 在IoC容器中配置的ioc:config对象会被自动更新，所有引用该config对象的bean也会重新触发配置更新函数。 具体参见IoC容器的配置文档。

````
  <ioc:config id="myConfig" class="xxx.MyConfig" prefix="app.xxx" />
  
  <bean id="myBean" class="xxx.MyBean">
    <property name="config" ref="myConfig" />
  </bean>
````

当myConfig变化时会触发myBean的配置更新函数。如果myBean实现了IConfigRefreshable方法，
则会调用IConfigRefreshable#refreshConfig方法。

* 在IoC容器中通过@r-cfg来标记需要动态更新的配置值

````
  <bean id="myBean" class="xxx.MyBean">
    <property name="propA" value="@r-cfg: app.xxx.yyy" />
    <property name="propB" value="@cfg: app.xxx.zzz" />
  </bean>
````

`@cfg`前缀表示获取配置变量值，但仅在bean初始化的时候获取一次。而`@r-cfg:`表示reactive config，当配置项的值发生变化的时候 会重新调用myBean.setPropA来更新属性值。

## 初始化过程

1. 装载nop.config.bootstrap-location指定的启动配置
2. 通过ServiceLoader机制装载IConfigService，并启动IConfigService
3. 创建IConfigExecutor。该Executor负责执行所有配置变更操作，确保单线程更新。
4. 通过IConfigService从远程配置中心拉取动态配置
5. 装载nop.config.location和nop.config.additional-location指定的应用配置
6. 构造DefaultConfigProvider对象，并初始化AppConfig对象
7. 初始化VirtualFileSystem
8. 加载ConfigModel，规范化配置项的数据类型
9. 通过ServiceLoader机制装载IConfigPlugin插件，并按照优先级顺序启动。
   IoC模块提供了IocConfigPlugin，在这个插件中初始化并启动IoC容器。同时，IoC容器也负责管理ClassLoader。

## 常见配置参数

* nop.orm.init-database-schema: true 
如果是空库，自动创建所有表

* nop.auth.login.allow-create-default-user
如果用户表为空，则自动创建缺省账户nop, 密码123

* nop.auth.sso.enabled
启用sso登录。如果设置为true，则需要设置nop.auth.sso.url等参数

* nop.auth.sso.server-url
keycloak等单点服务器的地址

* nop.auth.sso.realm
keycloak等单点服务器所需的realm配置

* nop.auth.sso.client-id
keycloak等单点服务器所需的client-id设置

* nop.auth.sso.client-secret
keycloak等单点服务器所需的client-secret设置

* nop.auth.jwt.enc-key
JWT使用AES加密算法时所使用的密钥。

* nop.datasource.jdbc-url
jdbc配置

* nop.web.validate-page-model
系统启动的时候是否自动检查所有page.yaml文件可以正常解析并加载。


* quarkus.log.level
quakus框架的Log级别设置
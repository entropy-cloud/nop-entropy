# 动态配置管理

## 配置规范

* 配置变量为全小写字母，以`.`和`-`为分隔符，与Spring的约定不同，NopConfig实际上不支持大小写混排的命名方式
* 如果通过环境变量来传递配置变量，则`.`被替换为`_`，而`-`被替换为`__`，而`_`被替换为`___`
  。例如nop.auth.sso.server-url变成`NOP_AUTH_SSO_SERVER__URL`。（这种约定与Spring并不一致，但它保证了环境变量和配置变量的双向无歧义映射）
* 一般在模块的XXXConfigs常量类中定义本模块中明确使用到的配置变量。

## 配置加载顺序

先加载的配置优先级更高，会被优先使用。

1. classpath:bootstrap.yaml 应用的启动配置，其中的所有变量都是固定值，不会被动态覆盖
2. 配置中心的`{nop.application.name}-{nop.profile}.yaml`
3. 配置中心的`{nop.application.name}.yaml`
4. nop.config.key-config-source.paths参数指定k8s SecretMap映射文件，定时扫描检测是否已更新
5. nop.config.props-config-source.paths参数指定k8s ConfigMap映射文件，定时扫描检测是否已更新
6. 如果配置了nop.config.jdbc.jdbc-url等参数，则会从数据库配置表中加载配置，定时扫描检测是否已更新
7. java的System.getProperties()
8. java的System.getenv(): StringHelper.envToConfigVar(envName)负责把环境变量名转换为配置项名称
9. nop.config.additional-location参数指定的配置文件
10. nop.config.location参数指定的配置文件，它的缺省值为 classpath:application.yaml
11. 识别quarkus配置规范规定的`'%dev.'`等profile配置前缀，根据当前的profile配置调整专属于profile的配置项的访问顺序。例如
    dev模式下，`%dev.a.b.c`的值将会覆盖配置项`a.b.c`的值

>

具体配置加载逻辑全部集中在[ConfigStarter.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-config/src/main/java/io/nop/config/starter/ConfigStarter.java)
类中

* **在bootstrap.yaml中可以配置nop.profile=dev来启用application-dev.yaml配置，类似于spring中的profile概念。**
  也可以通过java property或者env机制类配置profile，例如-Dnop.profile=dev或者配置环境变量`NOP_PROFILE=dev`
* 优先加载yaml后缀的配置文件，如果找不到，会尝试加载后缀名为yml的同名文件。也就是说，如果同时存在`application.yaml`和`application.yml`，则会优先使用前者

## 自动更新

配置中心下发配置之后，应用程序中的配置项会自动更新。更新过程通过单线程执行，确保按顺序执行更新。

* 应用程序中使用IConfigReference接口来动态获取参数，当参数更新时可以获得最新值

```javascript
  static final IConfigReference<Boolean> CFG_USE_CACHE = AppConfig.varRef("global.use_cache",true);

  public void myFunc(){
     if(CFG_USE_CACHE.get()){
       // ...
     }
  }
```

* 在IoC容器中配置的ioc:config对象会被自动更新，所有引用该config对象的bean也会重新触发配置更新函数。 具体参见IoC容器的配置文档。

```xml
<beans>
  <ioc:config id="myConfig" class="xxx.MyConfig" prefix="app.xxx" />

  <bean id="myBean" class="xxx.MyBean">
    <property name="config" ref="myConfig" />
  </bean>
</beans>
```

当myConfig变化时会触发myBean的配置更新函数。如果myBean实现了IConfigRefreshable方法，
则会调用IConfigRefreshable#refreshConfig方法。

* 在IoC容器中通过@r-cfg来标记需要动态更新的配置值

```xml
  <bean id="myBean" class="xxx.MyBean">
    <property name="propA" value="@r-cfg: app.xxx.yyy" />
    <property name="propB" value="@cfg: app.xxx.zzz" />
  </bean>
```

`@cfg`前缀表示获取配置变量值，但仅在bean初始化的时候获取一次。而`@r-cfg:`表示reactive config，当配置项的值发生变化的时候
会重新调用myBean.setPropA来更新属性值。

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

## 与spring和quarkus框架共享配置

Nop平台的缺省配置文件名为application.yaml，它与quarkus框架和spring框架的配置文件名相同。因此在这个配置文件中配置的内容实际上在spring/quarkus框架以及Nop平台中都可以读到。
不过需要注意的是，spring的配置有一个命名规范化的过程，它会自动将大小写混排的变量名规范化为通过减号分隔，例如spring.datasource.jdbcUrl会被规范化为spring.datasource.jdbc-url，
但是Nop平台为了提高一致性并没有引入这种规范化过程，所以表现出来的配置行为会有差异。另外spring框架对于通过环境变量传递的配置参数，会采用猜测的方法多次读取，对性能有一定影响，
而Nop平台的做法是按照确定性的规则对环境变量名进行规范化，例如nop.datasource.jdbc\_url对应的环境变量名固定为NOP\_DATASOURCE\_JDBC\_\_URL，这种规范化方式与spring也不同。
具体规范化的语法规则参见 `StringHelper.envToConfigVar(envName)`

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

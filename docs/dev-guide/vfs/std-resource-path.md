# 标准资源路径模式

Nop平台内部约定了一定的资源路径模式，会自动查找满足模式的文件进行加载。

```
META-INF/services
           io.nop.core.initialize.ICoreInitializer 使用Java内置的ServiceLoader机制注册分级初始化函数
                      CoreInitialization会读取所有CoreInitializer，按照优先级顺序执行
bootstrap.yaml 静态全局配置文件，其中的内容优先级最高，不会被外部配置所覆盖
application.yaml 全局的配置文件
application-{profile}.yaml 全局的配置文件，profile是通过nop.profile指定的部署环境名称

_vfs/
   /_delta
      /{deltaDir}   这里是delta层的名称，缺省会加载default层
        这里的文件会覆盖标准路径的同名文件
   /dict
      {dictName}.dict.yaml  字典文件不会被自动加载，但是通过dictName加载指定字典文件     
   /i18n
      /{locale}
        {moduleName}.i18n.yaml I18nManager初始化的时候会自动加载所有i18n文件     
   /nop
     /aop
        {xxx}.annotations Nop的AOP代码生成器生成包装类时会读取所有的annotations文件，并为每个标注了指定注解的类生成AOP包装类
     /autoconfig
        {xxx}.beans  NopIoC会自动扫描解析所有后缀名为beans的文件，加载其中的beans.xml文件
     /core
        /reigstry
          {xxx}.register-model.xml 初始化时会自动扫描所有registry-model.xml文件，并注册对应的DSL模型解析器，
                                   将它们和特定的文件类型关联起来
     /dao
        /dialect
          /selector
             {xxx}.selector.xml   初始化时会自动扫描所有selector.xml文件，加载数据库方言的匹配规则
          {dialectName}.dialect.xml  数据库方言定义文件，按照dialectName来加载
     /main
        /auth
            /app.action-auth.xml 全局的操作权限和菜单定义文件，在其中通过x:extends来引用其他权限文件
            /app.data-auth.xml 全局的数据权限定义文件，在其中通过x:extends来引用其他数据权限文件       
   /{moduleId}  Nop模块的moduleId必须是nop/auth这种两级目录结构
        _module  每个Nop模块下都有一个_module文件来标记它是模块。
        /beans
           app-{xxx}.beans.xml NopIoC启动时会自动扫描每个模块的beans目录下以`app-`为前缀的beans.xml文件
        /model
           /{bizObjName}
              {bizObjName}.xbiz 所有的服务对象原则上都是要在beans.xml中注册，然后再通过对象名查找到对应的xbiz和xmeta文件
              {bizObjName}.xmeta  NopDynEntity对象采用了简化注册流程，直接向BizObjectManager注册，没有在beans.xml中定义服务对象 
        /orm
           app.orm.xml NopOrm引擎初始化的时候会加载所有模块的orm目录下的app.orm.xml模型文件
        /pages
           /{bizObjName}
              {pageId}.page.yaml   可以配置页面文件在系统初始化的情况下加载，它引用的view模型因此被连带加载
              {bizObjName}.view.xml  View模型不会被自动加载，但是一般会放置在这个位置                    
```

## 模型文件自动加载顺序

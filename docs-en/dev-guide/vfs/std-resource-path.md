# Standard Resource Path Pattern

The Nop platform defines a specific resource path pattern within its system. This pattern automatically searches for files that match the defined pattern and loads them accordingly.


```
META-INF/services
           io.nop.core.initialize.ICoreInitializer Using Java's built-in ServiceLoader mechanism to register hierarchical initialization functions
                      CoreInitialization will read all CoreInitializer, executed in priority order
bootstrap.yaml Static global configuration file with highest priority, not overridden by external configurations
application.yaml Global configuration file
application-{profile}.yaml Global configuration file, where profile is specified via nop.profile deployment environment variable

_vfs/
   /_delta
      /{deltaDir} Here is the name of the delta layer, default will load the default layer
        This file will override the standard path's same-named file
   /dict
      {dictName}.dict.yaml Dictionary files will not be automatically loaded, but can be explicitly loaded via dictName
   /i18n
      /{locale}
        {moduleName}.i18n.yaml I18nManager initializes by automatically loading all i18n files during initialization

/nop
     /aop
        {xxx}.annotations Nop's AOP code generator will read all annotations files when generating wrapped classes
     /autoconfig
        {xxx}.beans NopIoC will automatically scan and parse all beans.xml files in the directory for beans with suffix 'beans'
     /core
        /reigstry
           {xxx}.register-model.xml Initialization will automatically scan all registry-model.xml files and register corresponding DSL parsers, associating them with specific file types
     /dao
        /dialect
           /selector
              {xxx}.selector.xml Initialization will automatically scan all selector.xml files to load database dialect matching rules
           {dialectName}.dialect.xml Database dialect definition files are loaded based on dialectName
     /main
        /auth
           /app.action-auth.xml Global operation permissions and menu definitions, referenced via x:extends
           /app.data-auth.xml Global data permissions, referenced via x:extends
   /{moduleId} Nop module's moduleId must follow the nop/auth directory structure
        _module Each Nop module has a _module file to mark it as a module
        /beans
           app-{xxx}.beans.xml NopIoC will automatically scan each module's beans directory for files prefixed with 'app-'

/model
   /{bizObjName}
      {bizObjName}.xbiz All service objects are typically registered in beans.xml, then referenced via bizObjName
      {bizObjName}.xmeta NopDynEntity uses a simplified registration process, directly registering with BizObjectManager without defining services in beans.xml
   /orm
      app.orm.xml NopOrm will load all modules' orm directory's app.orm.xml model files
      app.orm-interceptor.xml NopOrm will register interception files similar to database triggers, loaded from each module's orm/interceptor directory

/pages
   /{bizObjName}
      {pageId}.page.yaml Page files can be configured and loaded during system initialization via pageId
      {bizObjName}.view.xml View models are typically not automatically loaded but are placed in this location for manual setup
```

## Model File Loading Order
# Standard Resource Path Patterns

The Nop platform defines certain resource path patterns and will automatically discover and load files that match these patterns.

```
META-INF/services
           io.nop.core.initialize.ICoreInitializer Use Java's built-in ServiceLoader mechanism to register staged initialization functions
                      CoreInitialization will read all CoreInitializer instances and execute them in priority order
bootstrap.yaml Static global configuration file; its contents have the highest priority and will not be overridden by external configurations
application.yaml Global configuration file
application-{profile}.yaml Global configuration file; profile is the deployment environment name specified via nop.profile

_vfs/
   /_delta
      /{deltaDir}   This is the name of the Delta layer; the 'default' layer is loaded by default
        Files here will override same-named files in the standard paths
   /dict
      {dictName}.dict.yaml  Dictionary files are not auto-loaded; load a specific dictionary file by dictName
   /i18n
      /{locale}
        {moduleName}.i18n.yaml All i18n files are automatically loaded by I18nManager during initialization
   /nop
     /aop
        {xxx}.annotations When generating wrapper classes, Nop's AOP code generator reads all .annotations files and generates an AOP wrapper class for each class annotated with a specified annotation
     /autoconfig
        {xxx}.beans  NopIoC automatically scans and parses all files with the 'beans' suffix and loads the beans.xml contained within
     /core
        /reigstry
          {xxx}.register-model.xml During initialization, all registry-model.xml files are automatically scanned, the corresponding DSL model parsers are registered,
                                   and they are associated with specific file types
     /dao
        /dialect
          /selector
             {xxx}.selector.xml   During initialization, all selector.xml files are automatically scanned to load database dialect matching rules
          {dialectName}.dialect.xml  Database dialect definition file, loaded by dialectName
     /main
        /auth
            /app.action-auth.xml Global operation permissions and menu definition file; use x:extends within it to reference other permission files
            /app.data-auth.xml Global data-permission definition file; use x:extends within it to reference other data-permission files
   /{moduleId}  A Nop module's moduleId must follow a two-level directory structure like nop/auth
        _module  Each Nop module contains a _module file to mark it as a module
        /beans
           app-{xxx}.beans.xml At startup, NopIoC automatically scans each module's beans directory for beans.xml files prefixed with `app-`
        /model
           /{bizObjName}
              {bizObjName}.xbiz In principle, all service objects should be registered in beans.xml, and then the corresponding xbiz and xmeta files are located by object name
              {bizObjName}.xmeta  NopDynEntity objects use a simplified registration flow: they register directly to BizObjectManager and are not defined as service objects in beans.xml
        /orm
           app.orm.xml When the NopOrm engine initializes, it loads all app.orm.xml model files under each module's orm directory
           app.orm-interceptor.xml   Registration file for NopOrm engine internal interceptors; similar to database-level triggers, it fires on CRUD operations of individual entities
        /pages
           /{bizObjName}
              {pageId}.page.yaml   Page files can be configured to load during system initialization, which in turn causes the referenced view models to be loaded as well
              {bizObjName}.view.xml  View models are not auto-loaded, but are typically placed here
```

## Automatic Loading Order of Model Files

<!-- SOURCE_MD5:7545c367bd459811d5e381bd78dfe4ec-->

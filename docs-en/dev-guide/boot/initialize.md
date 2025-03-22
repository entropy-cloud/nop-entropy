# Initialization

The Nop platform uses `CoreInitialization.initialize()` to implement platform initialization. Its implementation utilizes the Java `ServiceLoader` mechanism to load the `ICoreInitializer` interface.

The built-in common initializers are executed in the following order:

1. **ReflectionHelperMethodInitializer**: Registers extended functions via reflection
2. **XLangCoreInitializer**: Registers global functions and objects used in XLang expressions
3. **XLangDebuggerInitializer**: Starts the XLang debugger
4. **ConfigInitializer**: Reads `application.yaml` configuration files and loads remote configuration services
5. **VirtualFileSystemInitializer**: Initializes the virtual file system
6. **RegisterModelCoreInitializer**: Loads the `register-model.xml` model registration file to register DSL models
7. **DaoDialectInitializer**: Scans dialect model files
8. **IoCCoreInitializer**: Initializes the Nop IoC container

The `ICoreInitializer` interface provides stage-wise loading capabilities. Each initializer has a corresponding initialization level setting, allowing precise execution at a specific initialization level. For example, in the code generator:
- We can explicitly specify execution to `INITIALIZER_PRIORITY_PRECOMPILE`, which does not trigger Nop IoC initialization.

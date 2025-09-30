# Initialization

The Nop platform performs platform initialization via a call to `CoreInitialization.initialize()`. Its implementation uses Java’s ServiceLoader mechanism to load implementations of the `ICoreInitializer` interface.
The built-in common initializers are executed in the following order:

1. ReflectionHelperMethodInitializer: Registers extension functions with the reflection system
2. XLangCoreInitializer: Registers global functions and global objects used in XLang expressions
3. XLangDebuggerInitializer: Starts the XLang debugger
4. ConfigInitializer: Reads configuration files such as application.yaml and loads from remote configuration services
5. VirtualFileSystemInitializer: Initializes the virtual file system
6. RegisterModelCoreInitializer: Loads the `register-model.xml` model registration file and registers DSL models
7. DaoDialectInitializer: Scans and reads dialect model files
8. IocCoreInitializer: Initializes the NopIoc container

`ICoreInitializer` provides staged loading; each initializer has a corresponding initialization level setting, allowing you to explicitly specify execution up to a particular level. For example, in the code generator, we can explicitly specify to run up to `INITIALIZER_PRIORITY_PRECOMPILE`, which will not trigger the initialization of NopIoc.
<!-- SOURCE_MD5:4d1a70a936427b2e1514ceb5ddd7d45c-->

# 初始化

Nop平台使用`CoreInitialization.initialize()`调用实现平台初始化，它的实现是使用Java的ServiceLoader机制来加载`ICoreInitializer`接口。
内置的常用初始化器按照如下顺序执行:

1. ReflectionHelperMethodInitializer: 向反射系统注册扩展函数
2. XLangCoreInitializer: 注册XLang表达式中使用的全局函数和全局对象
3. XLangDebuggerInitializer: 启动XLang调试器
4. ConfigInitializer: 读取application.yaml等配置文件，并从远程配置服务加载
5. VirtualFileSystemInitializer: 初始化虚拟文件系统
6. RegisterModelCoreInitializer: 加载`register-model.xml`模型注册文件，注册DSL模型
7. DaoDialectInitializer： 扫描读取dialect模型文件
8. IocCoreInitializer: 初始化NopIoc容器

`ICoreInitializer`提供了分阶段加载的能力，每个Initializer都具有对应的初始化级别设置，可以明确指定执行到某个初始化级别。例如，在代码生成器中，
我们明确可以指定执行到`INITIALIZER_PRIORITY_PRECOMPILE`，它不会触发NopIoc的初始化。

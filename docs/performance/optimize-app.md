# 应用优化

## 简化调试过程

* 每个模块都有一个自动生成app模块，比如nop-wf-app,可以直接启动这个模块来进行单个module的调试。不需要把所有模块都集成在一起调试。
* 一般功能开发时尽量通过单元测试来验证功能，避免每次都需要启动整个应用来验证功能。

## 优化应用启动时间

* `nop.web.validate-page-model`不设置或者设置为false会跳过启动时的页面验证，跳过对view.xml和page.yaml文件的解析检查。
* `nop.orm.db-differ.auto-upgrade-database`不设置或者设置为false，会跳过启动时的数据库升级检查。
* `nop.orm.init-database-schema`不设置或者设置为false, 会跳过启动时自动建表的操作。
* `nop.auth.login.allow-create-default-user`不设置或者设置为false,启动时就不会检查数据库中是否至少存在一个用户。
* `nop.web.auto-load-dynamic-file`不设置或者设置为false, 启动时就不会自动将xjs翻译为js文件。
* `nop.graphql.eager-init-biz-object`设置为false, 启动时不会立刻解析meta文件等创建BizObject，而是访问到某个对象时再延迟创建
* `nop.ioc.app-beans-container.concurrent-start`设置为true, 则启动时在线程池上执行bean容器的初始化操作，不阻塞主线程

## 优化应用运行时性能
* `nop.debug` 设置为false， 就不会在dump目录下产生输出
* `nop.core.component.resource-cache.check-changed`设置为false,则不会检查资源文件是否有变化，不会自动更新缓存的解析结果。
* 将日志级别设置为info，减少日志输出



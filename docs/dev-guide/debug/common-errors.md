# 常用错误

## 没有session

![](images/no-session-orm.png)

使用`@SingleSession`和`@Transactional`的注解的类需要在maven打包的时候执行代码生成任务CodeGenTask。
所以需要先运行mvn compile -DskipTests，然后才可以在IDEA里启动调试。
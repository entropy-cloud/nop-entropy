# 配置项

| 名称                                       | 缺省值  | 说明                                                        |
|------------------------------------------|------|-----------------------------------------------------------|
| nop.web.http-server-filter.enabled       | true | 是否将NopIoC中定义的IHttpServerFilter包装为Spring和Quarkus所使用的Filter |
| nop.quarkus.http-server-filter.sys-order | 5    | Quarkus框架所使用的系统级IHttpServerFilter的缺省优先级，值越小优先级越高          |
| nop.quarkus.http-server-filter.app-order | 10   | Quarkus框架所使用的应用级IHttpServerFilter的缺省优先级，值越小优先级越高          |
| nop.spring.http-server-filter.sys-order  | 0    | Spring框架所使用的系统级IHttpServerFilter的缺省优先级，值越小优先级越高           |
| nop.spring.http-server-filter.app-order  | 1000 | Spring框架所使用的应用级IHttpServerFilter的缺省优先级，值越小优先级越高           |

# 内置Filter

* ContextHttpServerFilter: 初始化全局的IContext对象。每次Web请求都产生一个新的IContext
* AuthHttpServerFilter: 负责进行登录检查。继承这个类或者替换ILoginService的实现类都可以定制登录逻辑。
  AuthHttpServerFilter具有Web环境相关的知识，例如cookie等。但是ILoginService就没有Web环境的知识了，它只能通过请求消息来进行处理。

# 操作权限

系统通过IActionAuthChecker接口来检查操作权限。配置 nop.auth.enable-action-auth=true后启用操作权限。

判断规则：
1. 如果设置了roles，则具有任意一个role，都返回true
2. 如果设置了permissions，则不满足任意一个permision都返回false
3. 返回true

## 后台Action
在action函数上通过`@Auth`注解来指定需要对应的permissions或者允许访问的roles。如果不指定，则按照是否是`@BizQuery`或者`@BizMutation`自动设置permissions为 {BizObjName}:query或者 {BizObjName}:mutation

在xbiz文件中可以为对应action设置auth配置，它会覆盖同名的Java方法上通过`@Auth`注解引入的权限设置。

## 结果对象上的属性
在xmeta文件中，可以为prop指定auth设置
```xml
<prop name="xx">
	<auth permissions="NopAuthUser:query" roles="admin" />
</prop>
```

## 操作权限配置
通过action-auth.xml和NopAuthResource后台对象可以配置操作权限。resource的类型分为TOPM、SUBM和FNPT，分为对应于顶级菜单，子菜单和功能点。在功能点上可以标记对应的permissions。

然后系统后台可以配置用户角色和NopAuthResource的对应关系，用于控制用户能访问哪些菜单，再由此推断出用户具有哪些permission。


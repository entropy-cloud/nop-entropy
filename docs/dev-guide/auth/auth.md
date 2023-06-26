# 操作权限

系统通过IActionAuthChecker接口来检查操作权限。配置 nop.auth.enable-action-auth=true后启用操作权限。

判断规则：
1. 如果设置了roles，则具有任意一个role，都返回true
2. 如果设置了permissions，则不满足任意一个permission都返回false
3. 返回true

## 后台Action
在action函数上通过`@Auth`注解来指定需要对应的permissions或者允许访问的roles。如果不指定，则按照是否是`@BizQuery`或者`@BizMutation`自动设置permissions为 {BizObjName}:query或者 {BizObjName}:mutation

例如
````javascript
@Auth(permissions="delete")
@BizMutation
public boolean delete(@Name("id") @Description("@i18n:biz.id|对象的主键标识") String id, IServiceContext context) {
    return super.delete(id, context);
}
````

在xbiz文件中可以为对应action设置auth配置，它会覆盖同名的Java方法上通过`@Auth`注解引入的权限设置。例如

````xml
<mutation name="delete">
    <auth permissions="delete"/>
</mutation>
````

## 结果对象上的属性
在xmeta文件中，可以为prop指定auth设置
```xml
<prop name="xx">
	<auth permissions="NopAuthUser:query" roles="admin" />
</prop>
```

## 操作权限配置
通过action-auth.xml和NopAuthResource后台对象可以配置操作权限。resource的类型分为TOPM、SUBM和FNPT，分为对应于顶级菜单，子菜单和功能点。在功能点上可以标记对应的permissions。

````xml
<resource id="NopAuthDept-main" displayName="部门" orderNo="10001" i18n-en:displayName="Department"
                          icon="ant-design:appstore-twotone" component="AMIS" resourceType="SUBM"
                          url="/nop/auth/pages/NopAuthDept/main.page.yaml">
    <children>
        <resource id="FNPT:NopAuthDept:query" displayName="查询部门" orderNo="10002" resourceType="FNPT">
            <permissions>NopAuthDept:query</permissions>
        </resource>
        <resource id="FNPT:NopAuthDept:update" displayName="修改部门" orderNo="10003" resourceType="FNPT">
            <permissions>NopAuthDept:update</permissions>
        </resource>
        <resource id="FNPT:NopAuthDept:delete" displayName="删除部门" orderNo="10004" resourceType="FNPT">
            <permissions>NopAuthDept:delete</permissions>
        </resource>
    </children>
</resource>
````

然后系统后台可以配置用户角色和NopAuthResource的对应关系，用于控制用户能访问哪些菜单，再由此推断出用户具有哪些permission。

# 数据权限
后台内置的findPage、findList和findFirst动作都会应用数据权限检查接口 IDataAuthChecker。
通过nop.auth.enable-data-auth来启用数据权限，缺省为true

### 数据权限定义文件
在/nop/main/auth/main.data-auth.xml文件中配置数据权限。filter段为xpl格式，输出filter定义节点。xpl执行时上下文具有entity,userContext等变量

````xml
<data-auth>
    <objs>
        <obj name="NopSysUserVariable">
            <role-auths>
                <role-auth roleId="manager">
                </role-auth>
                
                <role-auth roleId="user">
                    <filter>
                        <eq name="userId" value="@biz:userId" />
                    </filter>
                </role-auth>
            </role-auths>
        </obj>
    </objs>
</data-auth>
````

在filter段中可以编写权限过滤条件，其中value部分可以使用`@biz:`为前缀的表达式变量，例如`@biz:userId, @biz:deptId`等。
全部可用的变量在[biz-var.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-biz/src/main/resources/_vfs/nop/dict/biz/biz-var.dict.yaml)中定义。

### 通过NopAuthRoleDataAuth表来定义数据权限
数据库中定义的数据权限会和data-auth.xml配置文件中定义的权限合并。
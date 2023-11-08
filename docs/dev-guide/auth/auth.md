# 启用权限

* 配置 nop.auth.enable-action-auth=true后启用操作权限。字段级别权限也利用这一开关
* 配置 nop.auth.enable-data-auth=true后启用数据权限。
* 配置 nop.auth.use-data-auth-table=true启用数据权限配置表 NopAuthRoleDataAuth，数据库中配置的数据权限规则可以和配置文件中的权限规则合并

> 平台在调试模式下启动时会打印出所有已知配置变量以及它对应的配置位置

# 核心接口

1. IUserContext: 保存userId, roles等用户身份和权限相关信息。通过IUserContext.set()/get()函数存取。
2. IActionAuthChecker: 检查操作权限和字段权限。在GraphQLExecutor中针对每个GraphQL field调用。
3. IDataAuthChecker: 检查数据权限。在CrudBizModel中为Query对象自动追加权限过滤条件，在获取到每个实体后执行check动作。
4. 登录验证在AuthHttpServerFilter类中通过ILoginService接口调用实现。
5. ILoginService接口负责登入登出和token校验相关的所有逻辑。平台内置了LoginServiceImpl和OAuthLoginServiceImpl两个实现。

# 操作权限

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

1. 菜单项对应的resourceType=SUBM， 页面中对应的具体的功能点（例如修改按钮）对应resourceType=FNPT
2. 如果使用amis页面，需要配置component=AMIS，url=页面的虚拟文件路径

## 后台Action

在action函数上通过`@Auth`注解来指定需要对应的permissions或者允许访问的roles。如果不指定，则按照是否是`@BizQuery`
或者`@BizMutation`自动设置permissions为 {BizObjName}:{actionName}|{BizObjName}:query，以及{BizObjName}:
{actionName}|{BizObjName}:mutation

在权限分配的时候，如果允许所有读取操作，则可以配置 {BizObjName}:query，这样就不需要挨个指定{actionName}

例如

````javascript
@Auth(permissions="delete")
@BizMutation
public boolean delete(@Name("id") @Description("@i18n:biz.id|对象的主键标识") String id, IServiceContext context) {
    return super.delete(id, context);
}
````

permission的完整格式为bizObjName:action，如果只写action部分，则会自动补充bizObjName前缀。例如上面配置的permissions="delete"最终转换得到的
可能是NopAuthUser:delete。

如果在action上不标注`@Auth`注解，则缺省对应的permission为  {bizObjName}:mutation或者{bizObjName}:query。

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
    <auth permissions="NopAuthUser:query" roles="admin" for="read"/>
    <auth permissions="NopAuthUser:mutation" roles="hr" for="write"/>
</prop>
```

通过这里的配置可以实现字段级别的读写权限控制. for=read表示控制字段读权限，for=write控制字段写权限，而for=all同时允许读和写


# 操作权限检查接口

系统通过IActionAuthChecker接口来检查操作权限。

```java
public interface IActionAuthChecker {
    boolean isPermitted(String permission, ISecurityContext context);

    default boolean isAllPermitted(Set<String> permissions, ISecurityContext context) {
        if (permissions == null || permissions.isEmpty())
            return true;

        for (String permission : permissions) {
            if (!isPermitted(permission, context))
                return false;
        }
        return true;
    }

    default boolean isPermissionSetSatisfied(MultiCsvSet permissionSet, ISecurityContext context) {
        if (permissionSet == null || permissionSet.isEmpty())
            return true;

        for (Set<String> permissions : permissionSet) {
            if (isAllPermitted(permissions, context))
                return true;
        }
        return false;
    }
}    
```

根据auth配置进行权限校验的实现如下：

```
IUserContext userContext = context.getUserContext();
if (userContext == null)
   throw new IllegalStateException("nop.err.auth.no-user-context");
if (auth.getRoles() != null && !auth.getRoles().isEmpty()) {
   if (userContext.isUserInAnyRole(auth.getRoles()))
       return;
}

if (auth.getPermissions() != null && !auth.getPermissions().isEmpty()) {
   if (checker.isPermissionSetSatisfied(auth.getPermissions(), context))
       return;
}

 throw new NopException(AuthApiErrors.ERR_AUTH_NO_PERMISSION)
           .param(AuthApiErrors.ARG_ACTION_NAME, fieldName)
           .param(ARG_PERMISSION, auth.getPermissions())
           .param(ARG_ROLES, auth.getRoles())
           .param(ARG_OBJ_TYPE_NAME, objTypeName);

```

判断规则：

1. 如果设置了roles，则满足角色条件则返回true。roles的配置一般是逗号分隔的字符串，具有任意一个role就具有对应权限
2. 如果设置了permissions，则不满足permission条件则返回false。permissions配置的类型一般为multi-csv-set,格式为 a,b|c,d，表示(
   a并且b)或者(c并且d)

也就是说可以只配置roles，或者只配置permissions。如果只配置了permissions，则系统内部实现是根据role和resource之间的映射关系，
找到满足permission条件的所有role，然后再按照roles条件去进行权限检查。

> 根据permission查找到所有的role这一步不依赖于具体用户，可以统一完成计算然后缓存映射结果。

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
                        <eq name="userId" value="@biz:userId"/>
                    </filter>
                </role-auth>
            </role-auths>
        </obj>
    </objs>
</data-auth>
````

* 针对不同的角色可以设置不同的数据权限规则。一个用户只会匹配优先级最高的一条规则（如果规则优先级相同，则按照顺序检查用户是否具有指定角色）

在filter段中可以编写权限过滤条件，其中value部分可以使用`@biz:`为前缀的表达式变量，例如`@biz:userId, @biz:deptId`等。
全部可用的变量在[biz-var.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-biz/src/main/resources/_vfs/nop/dict/biz/biz-var.dict.yaml)
中定义。

### 通过NopAuthRoleDataAuth表来定义数据权限

数据库中定义的数据权限会和data-auth.xml配置文件中定义的权限合并。


# 多对多关联

[视频演示](https://www.bilibili.com/video/BV1Ks4y1E7pw/)

多对多关联在数据库层面一般是通过引入中间关联表来实现，例如nop\_auth\_user\_role表实现user\_id和role\_id之间的多对多关联。

在NopOrm引擎内部并没有针对多对多关联的内置支持，它只支持to-one和to-many两种关联形式。 Nop平台的做法是在应用层生成一些帮助函数，然后把多对多关联分解到
一对多关联形式上。具体做法如下:

```java
import java.util.List;

class NopAuthUser extends OrmEntity {
    public IOrmEntitySet<NopAuthUserRole> getUserRoles() {
        返回指向中间表实体的集合对象
    }

    public List<NopAuthRole> getRelatedRoleList() {
        // getRefProps是一个帮助函数，遍历集合返回集合元素上的指定属性
        return (List<NopAuthRole>) OrmEntityHelper.getRefProps(getUserRoles(),"role");
    }
    
    public List<String> getRelatedRoleIdList(){
        return (List<String>) OrmEntityHelper.getRefProps(getUserRoles(),"roleId");
    }

    public void setRelatedRoleIdList(List<String> roleIds){
        // setRefProps内部会识别roleId是否已经存在，是否需要新建NopAuthUserRole对象，是否需要删除集合中已经不再被使用的对象
        OrmEntityHelper.setRefProps(getUserRoles(),"roleId",roleIds);
    }
}
```

OrmEntityHelper仅仅是在Java实体层面提供一些帮助函数，简化我们从关联实体集合中存取相关属性的过程。

> 借助于ORM引擎所提供的对象关联，我们可以在聚合实体上提供很多帮助性的get/set方法，并把它们暴露为外部可以访问的GraphQL服务，从而简化外部接口。

## Excel模型配置

在Excel数据模型中，只需要为中间表实体增加many-to-many标签，则会自动生成以上方法。
![](many-to-many.png)

## 界面控件

缺省情况下多对多关联属性，例如上面的relatedRoleIdList会通过picker控件弹出选择。

## 多对多关联表作为一对一关联使用

虽然中间表一般是用来表达多对多关联。但是有的时候我们暂时只存在一对一关联，则可以在Excel模型上标注one-to-one，则会自动生成针对单个对象的关联属性。

# 多对多关联

[视频演示](https://www.bilibili.com/video/BV1Ks4y1E7pw/)

多对多关联在数据库层面一般是通过引入中间关联表来实现，例如`nop_auth_user_role`表实现`user_id`和`role_id`之间的多对多关联。

在NopOrm引擎内部并没有针对多对多关联的内置支持，它只支持`to-one`和`to-many`两种关联形式。
Nop平台的做法是在应用层生成一些帮助函数，然后把多对多关联分解到
一对多关联形式上。具体做法如下:

```java
import java.util.List;

class NopAuthUser extends OrmEntity {
  public IOrmEntitySet<NopAuthUserRole> getUserRoles() {
    返回指向中间表实体的集合对象
  }

  public List<NopAuthRole> getRelatedRoleList() {
    // getRefProps是一个帮助函数，遍历集合返回集合元素上的指定属性
    return (List<NopAuthRole>) OrmEntityHelper.getRefProps(getUserRoles(), "role");
  }

  public List<String> getRelatedRoleIdList() {
    return (List<String>) OrmEntityHelper.getRefProps(getUserRoles(), "roleId");
  }

  public void setRelatedRoleIdList(List<String> roleIds) {
    // setRefProps内部会识别roleId是否已经存在，是否需要新建NopAuthUserRole对象，是否需要删除集合中已经不再被使用的对象
    OrmEntityHelper.setRefProps(getUserRoles(), "roleId", roleIds);
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

## many-to-many相关的属性配置

关系数据库通过增加中间表来实现多对多关联。一般情况下多对多关联的结构如下：

```
MiddelTable(sid, refId1, refId2) refId1对应于关联属性refProp1, refId2对应于关联属性refProp2
```
* MiddleTable实体必须具有`many-to-many`标签，且必须具有两个`to-one`的relation。
* refProp1指向关联的第一个实体，而refProp2指向关联的第二个实体
* 中间表可以具有自己的sid，也可以使用refId1和refId2建立联合主键，不引入额外的中间表主键字段。
* 在MiddleTable这个中间实体上，通过`orm:manyToManyRefSetName1`
  来表示第一个实体上引用第二个实体的集合属性名，如果不指定，则缺省为`related{refProp2}List`。
* 类似的，在MiddleTable上通过`orm:manyToManyRefSetName2`来表示第二个实体上引用第一个实体的集合属性名。
* `orm:manyToManyRefSetDisplayName1`是`orm:manyToManyRefSetName1`所对应的显示名称。

以上扩展属性是ORM模型上的扩展信息，在生成XMeta元数据模型时会使用这些信息。

生成的XMeta中在第一个实体中的配置如下：

```xml
<meta>
  <props>
    <prop name="middleTables" orm:manyToManyRefProp="refId2" ext:kind="to-many"
          orm:leftJoinProp="id" orm:rightJoinProp="refId1">
      <schema>
        <item bizObjName="MiddleTable"/>
      </schema>
    </prop>

    <prop name="relatedRefProp1List" graphql:labelProp="relatedRefProp1List_label">
      <schema>
        <item bizObjName="EntityName2"/>
      </schema>
    </prop>

    <prop name="relatedRefProp1List_label" >
      <schema type="String" />
    </prop>

    <prop name="relatedRefProp1List_ids" graphql:labelProp="relatedGroupList_label">
        <schema type="List&lt;String>" />
    </prop>

  </props>
</meta>
```

* 在指向中间表的关联属性上，`orm:leftJoinProp`为本实体上的属性，一般为id，而`orm:rightJoinProp`是中间表上对应的关联字段
* `orm:manyToManyRefProp`对应于多对多关联的另一侧的关联字段。
* 多对多关联会自动生成一个关联对象集合，例如relatedRefProp1List。
* `graphql:labelProp`对应于显示名称，例如从每个实体上读取到显示属性拼接成一个`relatedRefProp1List_label`。

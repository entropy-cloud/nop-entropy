# Many-to-Many Associations

[Video Demo](https://www.bilibili.com/video/BV1Ks4y1E7pw/)

At the database level, many-to-many associations are typically implemented by introducing an intermediate association table. For example, the `nop_auth_user_role` table implements a many-to-many association between `user_id` and `role_id`.

Internally, the NopOrm engine does not have built-in support for many-to-many associations; it only supports `to-one` and `to-many` association forms.
The Nop platform’s approach is to generate some helper functions at the application layer and then decompose many-to-many associations into
one-to-many forms. The specific approach is as follows:

```java
import java.util.List;

class NopAuthUser extends OrmEntity {
  public IOrmEntitySet<NopAuthUserRole> getUserRoles() {
    Returns the collection object pointing to the intermediate table entity
  }

  public List<NopAuthRole> getRelatedRoleList() {
    // getRefProps is a helper function that iterates the collection and returns the specified property on each element
    return (List<NopAuthRole>) OrmEntityHelper.getRefProps(getUserRoles(), "role");
  }

  public List<String> getRelatedRoleIdList() {
    return (List<String>) OrmEntityHelper.getRefProps(getUserRoles(), "roleId");
  }

  public void setRelatedRoleIdList(List<String> roleIds) {
    // setRefProps internally recognizes whether roleId already exists, whether a new NopAuthUserRole object needs to be created, and whether objects no longer used need to be removed from the collection
    OrmEntityHelper.setRefProps(getUserRoles(), "roleId", roleIds);
  }
}
```

OrmEntityHelper only provides some helper functions at the Java entity layer to simplify the process of getting/setting related properties from associated entity collections.

> Leveraging the object associations provided by the ORM engine, we can offer many helpful get/set methods on aggregate entities and expose them as GraphQL services accessible externally, thereby simplifying external interfaces.

## Excel Model Configuration

In the Excel data model, you only need to add a many-to-many tag to the intermediate table entity, and the above methods will be generated automatically.
![](many-to-many.png)

## UI Controls

By default, many-to-many association properties—such as the relatedRoleIdList above—will use a picker control to pop up for selection.

## Using a Many-to-Many Association Table as a One-to-One Association

Although an intermediate table is generally used to express many-to-many associations, sometimes there is temporarily only a one-to-one association. In that case, you can mark one-to-one in the Excel model, and a single-object association property will be generated automatically.

## Attribute Configuration Related to Many-to-Many

Relational databases implement many-to-many associations by adding an intermediate table. In general, the structure of a many-to-many association is as follows:

```
MiddelTable(sid, refId1, refId2) refId1 corresponds to relation property refProp1, refId2 corresponds to relation property refProp2
```
* The MiddleTable entity must have the `many-to-many` tag and must have two `to-one` relations.
* refProp1 points to the first associated entity, and refProp2 points to the second associated entity.
* The intermediate table can have its own sid, or use refId1 and refId2 to form a composite primary key without introducing an additional primary key field for the intermediate table.
* On the MiddleTable intermediate entity, use `orm:manyToManyRefSetName1`
  to indicate the name of the collection property on the first entity that references the second entity. If not specified, the default is `related{refProp2}List`.
* Similarly, on MiddleTable use `orm:manyToManyRefSetName2` to indicate the name of the collection property on the second entity that references the first entity.
* `orm:manyToManyRefSetDisplayName1` is the display name corresponding to `orm:manyToManyRefSetName1`.

These extended attributes are extensions on the ORM model and will be used when generating the XMeta metadata model.

The configuration generated in XMeta for the first entity is as follows:

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

* On the association property pointing to the intermediate table, `orm:leftJoinProp` is the property on this entity, typically id, while `orm:rightJoinProp` is the corresponding association field on the intermediate table.
* `orm:manyToManyRefProp` corresponds to the associated field on the other side of the many-to-many association.
* A collection of associated objects is automatically generated for a many-to-many association, such as relatedRefProp1List.
* `graphql:labelProp` corresponds to the display name, for example reading the display attribute from each entity and concatenating it into `relatedRefProp1List_label`.
<!-- SOURCE_MD5:5e6c4e415fa77359020389b3780a4cd0-->

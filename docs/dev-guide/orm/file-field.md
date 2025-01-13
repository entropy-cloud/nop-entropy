# 附件和附件列表字段

## 实现原理

1. 在`orm.xml`模型中为字段设置【标准域】为file，也就是`stdDomain="file"`
2. 在`orm.xml`的`x:post-extends`段中引入`<orm-gen:DefaultPostExtends/>`标签
3. DefaultPostExtends标签中会调用FileComponentSupport标签，在这个标签中会遍历所有`stdDomain=file`的字段，并为它们生成对应的FileComponent字段定义。

![](images/file-component.png)

```xml

<orm>
  <x:post-extends>
    <orm-gen:DefaultPostExtends xpl:lib="/nop/orm/xlib/orm-gen.xlib"/>
  </x:post-extends>

  <entities>
    <entity className="io.nop.auth.dao.entity.NopAuthUser" attrs="...">
      <columns>
        <column code="AVATAR" displayName="头像" domain="image" name="avatar" precision="100" propId="10"
                stdDataType="string" stdDomain="file" stdSqlType="VARCHAR" i18n-en:displayName="Avatar"
                ui:show="X"/>
      </columns>
    </entity>
  </entities>
</orm>
```

4. 当`OrmSession.flush`的时候会调用所有实体的所有Component属性上的onEntityFlush回调函数。`OrmFileComponent.onEntityFlush`会将上传的临时文件和当前实体属性进行关联。如果实体属性此前已经关联了其他文件，则会自动删除上一次上传的文件。



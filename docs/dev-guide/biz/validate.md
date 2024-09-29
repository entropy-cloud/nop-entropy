# CrudBizModel中的自动验证

通过save/update等标准方法保存时，会调用MetaBaseValidator验证输入数据的合法性

## 1. validator

如果字段配置了validator，它会被编译为IEvalFunction类型，在其中可以使用`<c:check>`标签来进行验证

```xml
<props>
  <prop name="amount">
    <schema>
      <validator>
        <c:check errorCode="test.error" errorDescription="验证错误">
          <!-- >= 10 -->
          <ge name="value" value="${100}" />
        </c:check>
      </validator>
    </schema>
  </prop>
</props>
```

## 2. 字典数据

如果字段配置了dict，则会自动加载字典数据，验证前台提交的值在字段范围之内

## 3. 关联数据

对于外键关联，类如dept\_id关联Department表，则代码生成时会自动生成如下配置

```xml

<props>
    <prop name="deptId" ext:relation="dept">
        ...
    </prop>

    <prop name="dept">
        <schema bizObjName="NopAuthDepartment"/>
    </prop>
</props>
```

当验证deptId字段时，会检查是否具有`ext:relation`属性，如果存在关联对象，则调用关联的BizObject的get方法来验证实体的存在性。

因为是通过`IBizObject.invoke("get",{id: deptId})`这种方式来检查参数有效性，所以会检查当前用户的数据权限配置，确保当前用户可以访问指定数据，
并且该数据在数据库中存在。

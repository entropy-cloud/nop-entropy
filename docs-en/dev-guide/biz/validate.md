
# Automatic Validation in CrudBizModel

When saving via standard methods such as save/update, MetaBaseValidator is invoked to validate the legality of the input data.

## 1. validator

If a field is configured with a validator, it will be compiled into an IEvalFunction type, within which the `<c:check>` tag can be used for validation.

```xml
<props>
  <prop name="amount">
    <schema>
      <validator>
        <c:check errorCode="test.error" errorDescription="Validation Error">
          <!-- >= 10 -->
          <ge name="value" value="${100}" />
        </c:check>
      </validator>
    </schema>
  </prop>
</props>
```

## 2. Dictionary Data

If a field is configured with a dict, dictionary data will be loaded automatically to verify that the value submitted from the front end falls within the field’s allowed range.

## 3. Associated Data

For foreign-key associations, such as dept_id associated with the Department table, code generation will automatically produce the following configuration:

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

When validating the deptId field, it will check whether the `ext:relation` attribute is present. If an associated object exists, it will call the associated BizObject’s get method to verify the existence of the entity.

Since parameter validity is checked via `IBizObject.invoke("get",{id: deptId})`, it will also evaluate the current user's data-permission configuration to ensure the current user can access the specified data, and that the data exists in the database.

<!-- SOURCE_MD5:b31ee69008fdb22d8877a08d37accde8-->

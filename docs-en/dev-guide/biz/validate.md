# Automatic Validation in CrudBizModel

When using standard methods like save/update, the system will call MetaBaseValidator to validate the input data's legality.

## 1. Validator

If a field is configured with a validator, it will be compiled into an IEvalFunction type. In this context, you can use the `<c:check>` tag to perform validation.

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

If a field is configured with a dictionary, the system will automatically load dictionary data to validate that the submitted values fall within the valid range.

## 3. Associated Data

For foreign key associations, such as `dept_id` associated with the Department table, the code generation will automatically generate the following configuration:

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

When validating the `deptId` field, if an `ext:relation` attribute exists, the associated BizObject's `get` method will be called to verify the existence of the related entity. This is done using `IBizObject.invoke("get",{id: deptId})`. The system will then check the user's data access permissions to ensure they can access the specific data and that such data exists in the database.

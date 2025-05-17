IMPORTANT: 实现代码时不要假设需求中没有提到的信息，此时可以通过`<task:import>`引入服务对象来表达这些缺失的信息。比如说用户注册流程，需求文档中只说明不能使用弱密码，但是没有明确说明密码的构成规则，
则可以通过引入passwordService服务对象来表达密码的构成规则。

```xml

<task>
  <steps>
    <step name="checkPasswordNotWeak">
      <source>
        <c:script><![CDATA[
           const passwordService = useService('passwordService');
           if(passwordService.isWeak(password)) {
             throw new NopScriptError('app.demo.password-is-weak');
           }
        ]]></c:script>
      </source>
    </step>
  </steps>

  <task:import>
    <service name="passwordService">
      <description>密码验证服务</description>
      <method name="isWeak">
        <description>检查密码是否为弱密码</description>
        <arg name="password" type="String"/>
      </method>
    </service>
  </task:import>
</task>
```

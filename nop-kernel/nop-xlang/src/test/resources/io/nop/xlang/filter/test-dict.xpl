<!--
validator的结构由/nop/schema/validator.xdef元模型定义。过滤条件格式由/nop/schema/query/filter.xdef定义
-->
<biz:Validator xpl:lib="/nop/core/xlib/biz.xlib" fatalSeverity="100">

    <check id="checkStatus" errorCode="test.invalid-status" errorDescription="错误的状态码">
        <biz:InDict name="entity.status" dictName="core/active-status" />
    </check>

</biz:Validator>
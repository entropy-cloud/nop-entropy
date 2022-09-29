# 1. function scope测试
````xpl
<c:unit xpl:outputMode="text">
<c:script>
let rule = {};
let tokenTrans = new Set();

function ast_prop_builder(rule){
    let transFn = rule.ruleName;
    tokenTrans.add(rule.ruleName);
    return transFn;
}
</c:script>
    

<c:for var="entry" items="${rule.properties}">
${ast_prop_builder(entry.value)}
</c:for>

</c:unit>
````
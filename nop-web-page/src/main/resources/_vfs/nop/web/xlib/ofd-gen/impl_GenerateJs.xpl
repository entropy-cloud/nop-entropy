<c:unit xmlns:c="c" xmlns:xpl="xpl">
    <c:import class="io.nop.web.page.vue.VueTemplateHelper" />
import { createElement as h} from 'react'
import { NodeContext, registerFlowModel } from '@nop-chaos/nop-sdk'

    <c:for var="nodeModel" items="${codeGenModel.nodes}"><![CDATA[
function NodeComponent_${nodeModel.name.$camelCase('-',false)}(){
    const node = useContext(NodeContext);
    return ${VueTemplateHelper.vueTemplateToReact(nodeModel.template)};
}
    ]]></c:for>

    const registerNodes = [
    <c:for var="nodeModel" items="${codeGenModel.nodes}">
        {
          type: "${nodeModel.base || 'common'}",
          name: "${nodeModel.name}",
          label: "${nodeModel.label}",
          displayComponent: NodeComponent_${nodeModel.name.$camelCase('-',false)},
          <c:if test="${nodeModel.start}">isStart: true,</c:if>
          <c:if test="${nodeModel.end}">isEnd: true,</c:if>
          <c:if test="${nodeModel['ext:conditionNodeType']}">conditionNodeType: "${nodeModel['ext:conditionNodeType']}"</c:if>
        },
    </c:for>
    ]

    registerFlowModel("${codeGenModel.editorType}", registerNodes);
</c:unit>
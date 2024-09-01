<c:unit xpl:outputMode="text">
    <c:script><![CDATA[
        import io.nop.core.resource.component.ResourceComponentManager;

        const model = ResourceComponentManager.instance().loadComponentModel(modelPath);

        function normalizeName(name){
            return name.$replace('-','_').$replace('.','_')
        }
    ]]></c:script>

    import {createElement as h, Fragment} from 'react'

    <c:for var="importModel" items="${model.imports}">
        import { ${importModel.name} as ${importModel.as} } from '${importModel.from}';
    </c:for>

    <c:for var="nodeModel" items="${model.nodes}">
        <c:if test="${nodeModel.template}">
            function Node_${normalizeName(nodeModel.name)}(props){
                const {node, material, parent, index,t,editable} = props;
                <c:script>
                    import io.nop.web.page.vue.VueTemplateHelper;

                    const tplNode = nodeModel.template;
                </c:script>
               return ${tplNode.toReact()}
            }
        </c:if>
    </c:for>

    <c:for var="nodeModel" items="${model.nodes}">
        <c:if test="${!nodeModel.template and nodeModel.base}">
            const Node_${normalizeName(nodeModel.name)} = Node_${normalizeName(nodeModel.base)}
        </c:if>
    </c:for>

    const NODE_COMPONENTS = {
        <c:for var="nodeModel" items="${model.nodes}">
        <c:if test="${nodeModel.template or nodeModel.base}">
            "${normalizeName(nodeModel.name)}": Node_${normalizeName(nodeModel.name)},
        </c:if>
    </c:for>
    }
</c:unit>
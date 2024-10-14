<workflow>
    <c:script><![CDATA[
        const startNode = flow.startNode;
        const nodes = [];
        function collect_node(node){
           nodes.push(node);
           if(node.childNode){
              collect_node(node.childNode);
           }else if(node.conditionNodes){
              node.conditionNodes.forEach(n => {
                 if(n.childNode)
                    collect_node(n.childNode);
              });
           }
        }
        collect_node(startNode);
    ]]></c:script>

    <steps>
        <c:for var="node" items="${nodes}">
            <step name="${node.name}" displayName="${node.displayName}" specialType="${node.nodeKind}">

            </step>
        </c:for>
    </steps>
</workflow>
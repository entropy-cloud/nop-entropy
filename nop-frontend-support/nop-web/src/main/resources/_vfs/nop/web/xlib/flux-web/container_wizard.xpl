<c:unit xmlns:c="c" xmlns:thisLib="thisLib" xmlns:xpl="xpl" xmlns:j="j">
    <c:script><![CDATA[
        // xview wizard 属性 → Flux WizardSchema 字段映射（设计 §3.3）：
        // mode/action*Label 透传；startStep（1-based 模板串 vs Flux 0-based 实现）暂不映射；
        // className/actionClassName/initFetch/initFetchOn/initApi/reload/redirect/target 丢弃
        const wizardAttrs = _.pickNotNull(containerModel,
            ["mode","actionPrevLabel","actionNextLabel","actionNextSaveLabel","actionFinishLabel"]);
    ]]></c:script>

    <wizard xpl:attrs="wizardAttrs">
        <steps j:list="true">
            <c:for var="stepModel" items="${containerModel.steps}">
                <_ key="${stepModel.name}" title="${stepModel.title}">
                    <c:choose>
                        <when test="${stepModel.page}">
                            <body j:list="true">
                                <_ xpl:attrs="xpl('thisLib:LoadPage',stepModel.page)"/>
                            </body>
                        </when>
                        <when test="${stepModel.body?.size() > 0}">
                            <body j:list="true">
                                <c:for var="stepBodyModel" items="${stepModel.body}">
                                    <thisLib:GenContainerModel containerModel="${stepBodyModel}"/>
                                </c:for>
                            </body>
                        </when>
                        <otherwise>
                            <body j:list="true">
                                <_ xpl:attrs="xpl('thisLib:LoadPage',stepModel.name)"/>
                            </body>
                        </otherwise>
                    </c:choose>
                </_>
            </c:for>
        </steps>
    </wizard>
</c:unit>

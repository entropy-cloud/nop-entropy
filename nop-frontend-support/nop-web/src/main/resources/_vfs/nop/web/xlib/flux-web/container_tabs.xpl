<c:unit xmlns:c="c" xmlns:thisLib="thisLib" xmlns:xpl="xpl" xmlns:j="j">
    <tabs xpl:attrs="xpl('thisLib:FluxTabsDefaultAttrs',containerModel)">
        <items j:list="true">
            <c:for var="tabModel" items="${containerModel.tabs}">
                <_ xpl:attrs="xpl('thisLib:FluxTabDefaultAttrs',tabModel)">
                    <c:choose>
                        <when test="${tabModel.page}">
                            <body j:list="true">
                                <_ xpl:attrs="xpl('thisLib:LoadPage',tabModel.page)"/>
                            </body>
                        </when>
                        <when test="${tabModel.body?.size() > 0}">
                            <body j:list="true">
                                <c:for var="bodyModel" items="${tabModel.body}">
                                    <thisLib:GenContainerModel containerModel="${bodyModel}"/>
                                </c:for>
                            </body>
                        </when>
                        <otherwise>
                            <body j:list="true">
                                <_ xpl:attrs="xpl('thisLib:LoadPage',tabModel.name)"/>
                            </body>
                        </otherwise>
                    </c:choose>
                </_>
            </c:for>
        </items>
    </tabs>
</c:unit>

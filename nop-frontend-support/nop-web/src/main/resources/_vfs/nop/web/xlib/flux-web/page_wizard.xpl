<c:unit xmlns:c="c" xmlns:thisLib="thisLib" xmlns:xpl="xpl" xmlns:j="j">
    <page name="${pageModel.name}">
        <initApi xpl:attrs="xpl('thisLib:NormalizeApi',pageModel.initApi,{})" xpl:if="pageModel.initApi"/>

        <body>
            <c:script>
                const containerModel = pageModel;
            </c:script>
            <c:include src="container_wizard.xpl"/>
        </body>
    </page>
</c:unit>

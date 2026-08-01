<c:unit xmlns:c="c" xmlns:thisLib="thisLib" xmlns:xpl="xpl" xmlns:j="j">
    <page xpl:attrs="xpl('thisLib:FluxPageDefaultAttrs',pageModel)">
        <initApi xpl:attrs="xpl('thisLib:NormalizeApi',pageModel.initApi,{})" xpl:if="pageModel.initApi"/>

        <header j:list="true" xpl:if="pageModel.header?.body?.size() > 0">
            <c:for var="containerModel" items="${pageModel.header.body}">
                <thisLib:GenContainerModel containerModel="${containerModel}"/>
            </c:for>
        </header>
        <footer j:list="true" xpl:if="pageModel.footer?.body?.size() > 0">
            <c:for var="containerModel" items="${pageModel.footer.body}">
                <thisLib:GenContainerModel containerModel="${containerModel}"/>
            </c:for>
        </footer>
        <aside j:list="true" xpl:if="pageModel.aside?.body?.size() > 0">
            <c:for var="containerModel" items="${pageModel.aside.body}">
                <thisLib:GenContainerModel containerModel="${containerModel}"/>
            </c:for>
        </aside>
        <body j:list="true" xpl:if="pageModel.body?.body?.size() > 0">
            <c:for var="containerModel" items="${pageModel.body.body}">
                <thisLib:GenContainerModel containerModel="${containerModel}"/>
            </c:for>
        </body>
    </page>
</c:unit>

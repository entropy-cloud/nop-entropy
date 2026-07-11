<c:unit xmlns:c="c" xmlns:thisLib="thisLib" xmlns:xpl="xpl">
    <page xpl:attrs="xpl('thisLib:FluxPageDefaultAttrs',pageModel)">
        <initApi xpl:attrs="xpl('thisLib:NormalizeApi',pageModel.initApi,{})" xpl:if="pageModel.initApi"/>

        <body>
            <tabs xpl:attrs="xpl('thisLib:FluxTabsDefaultAttrs',pageModel)">
                <tabs j:list="true">
                    <c:for var="tabModel" items="${pageModel.tabs}">
                        <_ xpl:attrs="xpl('thisLib:FluxTabDefaultAttrs',tabModel)">
                            <body xpl:attrs="xpl('thisLib:LoadPage',tabModel.page || tabModel.name)"/>
                        </_>
                    </c:for>
                </tabs>
            </tabs>
        </body>
    </page>
</c:unit>

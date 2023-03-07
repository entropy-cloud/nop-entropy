<page xpl:attrs="xpl('thisLib:PageDefaultAttrs',pageModel)">
    <initApi xpl:attrs="xpl('thisLib:NormalizeApi',pageModel.initApi,{})" xpl:if="pageModel.initApi"/>

    <body>
        <tabs xpl:attrs="xpl('thisLib:TabsDefaultAttrs',pageModel)">
            <tabs j:list="true">
                <c:for var="tabModel" items="${pageModel.tabs}">
                    <_ xpl:attrs="xpl('thisLib:TabDefaultAttrs',tabModel)">
                        <body xpl:attrs="xpl('thisLib:LoadPage',tabModel.name || tabModel.page)"/>
                    </_>
                </c:for>
            </tabs>
        </tabs>
    </body>
</page>
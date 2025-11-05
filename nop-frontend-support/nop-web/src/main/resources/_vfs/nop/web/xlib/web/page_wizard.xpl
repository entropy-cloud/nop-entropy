<wizard xpl:attrs="xpl('thisLib:WizardDefaultAttrs',pageModel)">
    <api xpl:attrs="xpl('thisLib:NormalizeApi',pageModel.api,{})" xpl:if="pageModel.api"/>

    <initApi xpl:attrs="xpl('thisLib:NormalizeApi',pageModel.initApi,{})" xpl:if="pageModel.initApi"/>


    <steps j:list="true">
        <c:for var="stepModel" items="${pageModel.steps}">
            <c:script>
                const pageData =  xpl('thisLib:LoadPage',stepModel.page, null);
                pageData.title = stepModel.title
                pageData.type = 'step'
                $out.appendChild(location(),"_",pageData)
            </c:script>
        </c:for>
    </steps>
</wizard>
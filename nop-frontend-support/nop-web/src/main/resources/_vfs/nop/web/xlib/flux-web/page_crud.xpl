<c:unit xmlns:c="c" xmlns:thisLib="thisLib" xmlns:xpl="xpl" xmlns:j="j">
    <page xpl:attrs="xpl('thisLib:FluxPageDefaultAttrs',pageModel)">

        <c:unit>
            <c:script><![CDATA[
                 import io.nop.xui.utils.XuiHelper;

                 let formModel = viewModel.forms.getByKey(pageModel.asideFilterForm);
                 const genScope = { formSelection: XuiHelper.getFormSelection(formModel,objMeta) }
            ]]></c:script>

            <aside xpl:if="formModel" j:list="true">
                <form target="${pageModel.table.name || 'crud-grid'}"
                      xpl:attrs="xpl('thisLib:FluxFormDefaultAttrs',formModel)" name="aside">
                    <data xpl:attrs="formModel.data"/>
                    <initApi xpl:attrs="xpl('thisLib:NormalizeApi',formModel.initApi,genScope)" xpl:if="formModel.initApi"/>
                    <api xpl:attrs="xpl('thisLib:NormalizeApi',formModel.api,genScope)" xpl:if="formModel.api"/>
                    <thisLib:GenFormBody formModel="${formModel}" objMeta="${objMeta}"/>
                </form>
            </aside>
        </c:unit>

        <initApi xpl:attrs="xpl('thisLib:NormalizeApi',pageModel.initApi,{})" xpl:if="pageModel.initApi"/>

        <body>
            <c:include src="grid_crud.xpl"/>
        </body>

    </page>
</c:unit>

<c:unit xmlns:j="j" xmlns:c="c" xmlns:thisLib="thisLib" xmlns:xpl="xpl">
    <c:script><![CDATA[
        import io.nop.xui.utils.XuiHelper;

        let filterForm = viewModel.forms.getByKey(pageModel.filterForm);
        let gridModel = viewModel.grids.getByKey(pageModel.grid);
        $.notNull(gridModel,"grid:"+pageModel.grid);

        let listSelection = XuiHelper.getListSelection(gridModel,objMeta);
        let pageSelection = 'total,page,items{ ' + listSelection +' }';

        const genScope = {listSelection,pageSelection,fixedProps: fixedProps.$toCsvSet()}
        let gridApi = pageModel.table.api || gridModel.api

        $.notNull(gridApi.url,"pageModel.table.api.url is null, page:"+pageModel.name+',view='+viewModel.resourcePath());
        gridApi = { ...gridApi, url : XuiHelper.appendFilterProps(gridApi.url,fixedProps)}

        let filter = gridModel.filter;
    ]]></c:script>

    <c:if test="${pageModel.type == 'picker'}">
       <size>${pageModel.size || 'lg'}</size>
       <modalSize>${pageModel.size || 'lg'}</modalSize>
       <source xpl:attrs="xpl('thisLib:NormalizeApi',gridApi,genScope)" valueField="id"
               labelField="${objMeta?.displayProp}" filter="${filter?.toJsonObject()}"/>
    </c:if>

    <crud xpl:is="${pageModel.type == 'picker'? 'pickerSchema': 'crud'}" name="${pageModel.table.name || 'crud-grid'}"
          xpl:attrs="xpl('thisLib:FluxGridDefaultAttrs', gridModel)" autoFillHeight="${pageModel.table.autoFillHeight}"
          pickerMode="${pageModel.table.pickerMode}" defaultParams="${pageModel.defaultParams}"
          maxItemSelectionLength="${pageModel.table.maxItemSelectionLength}"
          multiple="${pageModel.table.multiple ?? gridModel.multiple}" footable="${gridModel.containsBreakpoint()}"
    >

        <toolbar j:list="true">
            <thisLib:GenActions actions="${pageModel.listActions?.filter(a=>!a.batch)}" genScope="${genScope}"/>
            <thisLib:GenActions actions="${pageModel.listActions?.filter(a=>a.batch)}" genScope="${genScope}"/>
        </toolbar>

        <footerToolbar j:list="true" xpl:if="pageModel.table?.pager != 'none' ">
            <statistics type="statistics"/>
            <pagination type="pagination"/>
        </footerToolbar>

        <api xpl:attrs="xpl('thisLib:NormalizeApi',gridApi,genScope)" filter="${filter?.toJsonObject()}"/>

        <saveOrderApi
                xpl:attrs="xpl('thisLib:NormalizeApi',pageModel.table?.saveOrderApi || gridModel.saveOrderApi,genScope)"
                xpl:if="pageModel.table?.saveOrderApi || gridModel.saveOrderApi"/>

        <c:if test="${objMeta?.displayProp}">
            <labelTpl>$${objMeta.displayProp}</labelTpl>
        </c:if>

        <columns j:list="true">
            <thisLib:GenGridCols gridModel="${gridModel}" objMeta="${objMeta}" ignoreCols="${genScope.ignoreCols}"
                                 filterForm="${pageModel.autoGenerateFilter ? filterForm:null}"/>
            <column label="@i18n:common.operation" width="${pageModel.table?.operationSize || 140}"
                    fixed="right" toggled="@:true"
                    xpl:if="!pageModel.table?.noOperations and pageModel.rowActions?.size() > 0">
                <buttons j:list="true">
                    <thisLib:GenActions actions="${pageModel.rowActions}" genScope="${genScope}"/>
                </buttons>
            </column>
        </columns>

        <itemAction xpl:attrs="xpl('thisLib:NormalizeAction',pageModel.itemAction,genScope)"
                    xpl:if="pageModel.itemAction"/>
    </crud>
</c:unit>

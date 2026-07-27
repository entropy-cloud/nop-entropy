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

        // flux crud 用 loadAction 取数（crud.md §2）。flux fetcher 把 @query:/@mutation: 转 /r/ RPC。
        const _loadApiNorm = xpl('thisLib:NormalizeApi', gridApi, genScope);
        const loadAction = _loadApiNorm != null ? {action:'ajax', args: _loadApiNorm} : null;
        const crudName = pageModel.table.name || 'crud-grid';
    ]]></c:script>

    <c:if test="${pageModel.type == 'picker'}">
       <size>${pageModel.size || 'lg'}</size>
       <modalSize>${pageModel.size || 'lg'}</modalSize>
       <source xpl:attrs="xpl('thisLib:NormalizeApi',gridApi,genScope)" valueField="id"
               labelField="${objMeta?.displayProp}" filter="${filter?.toJsonObject()}"/>
    </c:if>

    <crud xpl:is="${pageModel.type == 'picker'? 'pickerSchema': 'crud'}" name="${crudName}" id="${crudName}"
          xpl:attrs="xpl('thisLib:FluxGridDefaultAttrs', gridModel)"
          defaultParams="${pageModel.defaultParams}"
    >

        <toolbar j:list="true">
            <thisLib:GenActions actions="${pageModel.listActions?.filter(a=>!a.batch)}" genScope="${genScope}"/>
            <thisLib:GenActions actions="${pageModel.listActions?.filter(a=>a.batch)}" genScope="${genScope}"/>
        </toolbar>

        <footerToolbar j:list="true" xpl:if="pageModel.table?.pager != 'none' ">
            <statistics type="statistics"/>
            <pagination type="pagination"/>
        </footerToolbar>

        <loadAction xpl:attrs="loadAction" xpl:if="loadAction"/>

        <columns j:list="true">
            <thisLib:GenGridCols gridModel="${gridModel}" objMeta="${objMeta}" ignoreCols="${genScope.ignoreCols}"
                                 filterForm="${pageModel.autoGenerateFilter ? filterForm:null}"/>
            <!-- flux crud/table 不支持 column 级别的 buttons 属性，暂不生成操作列 -->
        </columns>
    </crud>
</c:unit>

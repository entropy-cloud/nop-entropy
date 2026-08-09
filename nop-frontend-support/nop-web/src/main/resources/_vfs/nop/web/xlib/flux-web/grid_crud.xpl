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

        // 默认多选 checkbox（selectable 显式 false 时关闭；picker 模式走自带选择，不生成）。
        // 序号列固定左侧，首个数据列由 GenGridCol 固定左侧（colIndex==0 → left）。
        const isPicker = pageModel.type == 'picker';
        const selection = !isPicker && gridModel.selectable !== false ? {type:'checkbox'} : null;
    ]]></c:script>

    <c:if test="${isPicker}">
       <size>${pageModel.size || 'lg'}</size>
       <modalSize>${pageModel.size || 'lg'}</modalSize>
       <source xpl:attrs="xpl('thisLib:NormalizeApi',gridApi,genScope)" valueField="id"
               labelField="${objMeta?.displayProp}" filter="${filter?.toJsonObject()}"/>
    </c:if>

    <crud xpl:is="${isPicker? 'pickerSchema': 'crud'}" name="${crudName}" id="${crudName}"
          xpl:attrs="xpl('thisLib:FluxGridDefaultAttrs', gridModel)"
          defaultParams="${pageModel.defaultParams}"
          selection="${selection}"
    >

        <toolbar j:list="true">
            <thisLib:GenActions actions="${pageModel.listActions?.filter(a=>!a.batch)}" genScope="${genScope}"/>
            <thisLib:GenActions actions="${pageModel.listActions?.filter(a=>a.batch)}" genScope="${genScope}"/>
        </toolbar>

        <footerToolbar j:list="true" xpl:if="pageModel.table?.pager != 'none' " />

        <loadAction xpl:attrs="loadAction" xpl:if="loadAction"/>

        <queryForm xpl:if="filterForm" id="${crudName}-query-form">
            <title>@i18n:common.search</title>
            <data xpl:attrs="filterForm.data" xpl:if="filterForm.data"/>
            <submitAction action="component:querySubmit" componentId="${crudName}"/>
            <thisLib:GenFormBody formModel="${filterForm}" objMeta="${objMeta}"/>
        </queryForm>

        <columns j:list="true">
            <column type="index" name="index" label="@i18n:common.index" width="50" fixed="left" align="center" toggled="false"/>
            <thisLib:GenGridCols gridModel="${gridModel}" objMeta="${objMeta}" ignoreCols="${genScope.ignoreCols}"
                                 filterForm="${pageModel.autoGenerateFilter ? filterForm:null}"/>
            <column type="operation" label="@i18n:common.operation" name="operation"
                    width="${pageModel.table?.operationSize || 140}" fixed="right"
                    xpl:if="!pageModel.table?.noOperations">
                <buttons j:list="true">
                    <thisLib:GenActions actions="${pageModel.rowActions}" genScope="${genScope}"/>
                </buttons>
            </column>
        </columns>
    </crud>
</c:unit>

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
        <!--
        amis的picker控件要求source同时提供获取初始值和查询列表的功能，而没有使用pickerSchema中定义的api
        -->
        <source xpl:attrs="xpl('thisLib:NormalizeApi',gridApi,genScope)" valueField="id"
                labelField="${objMeta?.displayProp}" filter="${filter?.toJsonObject()}"/>
    </c:if>

    <!--
    @keepItemSelectionOnPageChange 默认分页、搜素后，用户选择条目会被清空，配置keepItemSelectionOnPageChange属性后会保留用户选择，可以实现跨页面批量操作
    @checkOnItemClick 支持点击一行的触发选中状态切换
    @syncLocation 默认 CRUD 会将过滤条件参数同步至浏览器地址栏中，比如搜索条件、当前页数，这也做的目的是刷新页面的时候还能进入之前的分页。
            但也会导致地址栏中的参数数据合并到顶层的数据链中
    -->
    <crud xpl:is="${pageModel.type == 'picker'? 'pickerSchema': 'crud'}" name="${pageModel.table.name || 'crud-grid'}"
          xpl:attrs="xpl('thisLib:GridDefaultAttrs', gridModel)" autoFillHeight="${pageModel.table.autoFillHeight}"
          syncLocation="@:false" pickerMode="${pageModel.table.pickerMode}"
          maxItemSelectionLength="${pageModel.table.maxItemSelectionLength}"
          multiple="${pageModel.table.multiple ?? gridModel.multiple}" footable="${gridModel.containsBreakpoint()}"
    >

        <bulkActions j:list="true">
            <thisLib:GenActions actions="${pageModel.listActions?.filter(a=>a.batch)}" genScope="${genScope}"/>
        </bulkActions>

        <itemActions j:list="true" xpl:if="!pageModel.itemActions?.empty">
            <thisLib:GenActions actions="${pageModel.itemActions}" genScope="${genScope}"/>
        </itemActions>

        <headerToolbar j:list="true">
            <filter-toggler id="filter-toggler"/>
            <thisLib:GenActions actions="${pageModel.listActions?.filter(a=>!a.batch)}" genScope="${genScope}"/>
            <bulkActions id="bulkActions"/>
            <columns-toggler align="right" id="columns-toggler"/>
            <!--                <pagination align="right"/>-->
        </headerToolbar>

        <footerToolbar j:list="true" xpl:if="pageModel.table?.pager != 'none' ">
            <statistics id="statistics"/>
            <switch-per-page id="switch-per-page"/>
            <pagination id="pagination"/>
        </footerToolbar>

        <api xpl:attrs="xpl('thisLib:NormalizeApi',gridApi,genScope)" filter="${filter?.toJsonObject()}"/>

        <saveOrderApi
                xpl:attrs="xpl('thisLib:NormalizeApi',pageModel.table?.saveOrderApi || gridModel.saveOrderApi,genScope)"
                xpl:if="pageModel.table?.saveOrderApi || gridModel.saveOrderApi"/>

        <c:if test="${objMeta.displayProp}">
            <labelTpl>$${objMeta.displayProp}</labelTpl>
        </c:if>

        <filter xpl:if="filterForm and !pageModel.autoGenerateFilter" id="crud-filter"
                xpl:attrs="xpl('thisLib:FormDefaultAttrs',filterForm)" mode="${filterForm.layoutMode || 'horizontal'}">
            <thisLib:GenFormBody formModel="${filterForm}" objMeta="${objMeta}"/>
            <actions j:list="true">
                <reset label="@i18n:common.reset" id="reset-button"/>
                <submit label="@i18n:common.query" level="primary" id="submit-button"/>
            </actions>
        </filter>

        <thisLib:AutoGenerateFilter pageModel="${pageModel}"/>

        <columns j:list="true">
            <thisLib:GenGridCols gridModel="${gridModel}" objMeta="${objMeta}" ignoreCols="${genScope.fixedProps}"
                                 filterForm="${pageModel.autoGenerateFilter ? filterForm:null}"/>
            <operation label="@i18n:common.operation" id="operation" width="${pageModel.table?.operationSize || 140}"
                       toggled="@:true" fixed="right" labelClassName="text-center"
                       xpl:if="!pageModel.table?.noOperations">
                <buttons j:list="true">
                    <thisLib:GenActions actions="${pageModel.rowActions}" genScope="${genScope}"/>
                </buttons>
            </operation>
        </columns>

        <itemAction xpl:attrs="xpl('thisLib:NormalizeAction',pageModel.itemAction,genScope)"
                    xpl:if="pageModel.itemAction"/>
    </crud>
</c:unit>
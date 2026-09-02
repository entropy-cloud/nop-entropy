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

        // flux crud 用 loadAction 取数（crud.md §2）。flux fetcher 把 @query:/@mutation: 转 /r/ RPC。
        // dependsOn 为惰性哨兵根（flux 纯命令式 reaction 的 dummy-root 惯例）：满足 reaction 字段契约，根永不写入故不自动触发，重载全由 renderer 命令式驱动。
        const _loadApiNorm = xpl('thisLib:NormalizeApi', gridApi, genScope);
        const loadAction = _loadApiNorm != null ? {action:'ajax', args: _loadApiNorm, dependsOn: ['__crud_load__']} : null;
        const crudName = pageModel.table.name || 'crud-grid';

        // 默认多选 checkbox（selectable 显式 false 时关闭；picker 模式由 pickerCrudAttrs 提供选择配置）。
        // 序号列固定左侧，首个数据列由 GenGridCol 固定左侧（colIndex==0 → left）。
        const isPicker = pageModel.type == 'picker';
        const selection = !isPicker && gridModel.selectable !== false ? {type:'checkbox'} : null;

        // picker 模式（v3 契约）：pickerPopup 弹层 + pickerSchema 内容子树。
        // 内容选择发布走 CRUD 自身的 scope-publish 配置，由转换器指向 picker 的固定
        // 约定名 $_picker.selection / $_picker.rows（v3.3 单一 scope 发布协议：
        // picker confirm 只读固定变量，不感知内容类型；实例隔离由弹层局域 scope 提供）。
        const pickerPopupAttrs = isPicker ? { type: 'dialog', size: pageModel.size || 'lg' } : null;
        const crudAttrs = isPicker ? {
            ...xpl('thisLib:FluxGridDefaultAttrs', gridModel),
            type: 'crud',
            rowKey: 'id',
            selection: { type: 'checkbox', keepOnPageChange: true, toggleOnRowClick: true },
            selectionOwnership: 'scope',
            selectionStatePath: '$_picker.selection',
            dataStatePath: '$_picker.rows',
            autoClearSelectionOnRefresh: false,
        } : xpl('thisLib:FluxGridDefaultAttrs', gridModel);
    ]]></c:script>

    <pickerPopup xpl:if="isPicker" xpl:attrs="pickerPopupAttrs"/>

    <crud xpl:is="${isPicker? 'pickerSchema': 'crud'}" name="${crudName}" id="${crudName}"
          xpl:attrs="crudAttrs"
          defaultParams="${pageModel.defaultParams}"
          selection="${selection}"
          className="erp-crud"
          headerClassName="erp-crud-header"
          bodyClassName="erp-crud-body p-none"
    >

        <toolbar j:list="true">
            <thisLib:GenActions actions="${pageModel.listActions?.filter(a=>!a.batch)}" genScope="${genScope}"/>
            <thisLib:GenActions actions="${pageModel.listActions?.filter(a=>a.batch)}" genScope="${genScope}"/>
        </toolbar>

        <footerToolbar j:list="true" xpl:if="pageModel.table?.pager != 'none' " />

        <loadAction xpl:attrs="loadAction" xpl:if="loadAction"/>

        <!-- 查询表单缺省水平布局（label 同行、宽度统一）：
             【G-001 修复后】flux 上游修复（plan 2026-08-24-2115-1）后 `queryForm.mode` 直接被校验器读取
             决定渲染 mode（不再是只看 layout），因此这里只需设 `mode="horizontal"`。兼容旧调用：若
             view.xml 设置 `layoutMode='vertical'` 等，校验器优先用 mode，无 mode 时回退到 layout。
             wrapClassName 加 m-b-nm 让字段行紧贴，actionsClassName 让提交/重置按钮靠右带间距。
             labelWidth 可经 view.xml form 或配置变量 nop.xui.crud.query-label-width 覆盖。 -->
        <queryForm xpl:if="filterForm" id="${crudName}-query-form"
                   mode="${filterForm.layoutMode || 'horizontal'}"
                   labelWidth="${filterForm.labelWidth || $config.var('nop.xui.crud.query-label-width') || 80}"
                   wrapClassName="m-b-nm"
                   actionsClassName="m-t-xs flex justify-end gap-sm">
            <title>@i18n:common.search</title>
            <data xpl:attrs="filterForm.data" xpl:if="filterForm.data"/>
            <submitAction action="component:querySubmit" componentId="${crudName}"/>
            <thisLib:GenFormBody formModel="${filterForm}" objMeta="${objMeta}"/>
        </queryForm>

        <columns j:list="true">
            <column type="index" name="index" label="@i18n:common.index" width="50" fixed="left" align="center" toggled="false"/>
            <thisLib:GenGridCols gridModel="${gridModel}" objMeta="${objMeta}" ignoreCols="${genScope.ignoreCols}"
                                 filterForm="${pageModel.autoGenerateFilter ? filterForm:null}"/>
            <!-- 操作列：默认 180 宽度（之前 140 过窄，多操作按钮挤在一列），可通过 view.xml table.operationSize 覆盖。
                 labelClassName/headerClassName 居中让操作区视觉对齐；超过 4 个按钮自动转入 actionGroup。-->
            <column type="operation" label="@i18n:common.operation" name="operation"
                    width="${pageModel.table?.operationSize || 180}" fixed="right"
                    labelClassName="text-center" headerClassName="text-center"
                    xpl:if="!pageModel.table?.noOperations">
                <buttons j:list="true">
                    <thisLib:GenActions actions="${pageModel.rowActions}" genScope="${genScope}"/>
                </buttons>
            </column>
        </columns>
    </crud>
</c:unit>

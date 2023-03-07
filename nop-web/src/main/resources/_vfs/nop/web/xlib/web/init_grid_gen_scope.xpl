<c:script>
    import io.nop.core.resource.component.ResourceComponentManager;
    import io.nop.xlang.xmeta.SchemaLoader;
    import io.nop.xlang.xpl.xlib.XplLibHelper;
    import io.nop.xui.utils.XuiHelper;

    let viewModel = ResourceComponentManager.instance().loadComponentModel(view);
    let gridModel = viewModel.grids.getByKey(grid);
    $.notNull(gridModel,"grid:"+grid+",view="+view);

    let objMeta = SchemaLoader.loadXMeta(gridModel.objMeta || viewModel.objMeta);
    let controlLib = XplLibHelper.loadLib(viewModel.controlLib || '/nop/web/xlib/control.xlib');
    let bizObjName = viewModel.bizObjName;
    let i18nRoot = objMeta['i18n:root'] || bizObjName;


    let listSelection = XuiHelper.getListSelection(gridModel,objMeta);
    let pageSelection = 'total,page,items{ ' + listSelection +' }';

    const genScope = {listSelection,pageSelection}
</c:script>
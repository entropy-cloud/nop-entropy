<c:unit>

    <c:script>
        import io.nop.core.resource.component.ResourceComponentManager;
        import io.nop.xlang.xmeta.SchemaLoader;
        import io.nop.xlang.xpl.xlib.XplLibHelper;

        let viewModel = ResourceComponentManager.instance().loadComponentModel(view);
        let gridModel = viewModel.grids.getByKey(grid);
        $.notNull(gridModel,"grid:"+grid+",view="+view);

        let objMeta = SchemaLoader.loadXMeta(gridModel.objMeta || viewModel.objMeta);
        let controlLib = XplLibHelper.loadLib(viewModel.controlLib || '/nop/web/xlib/flux-control.xlib');
        let bizObjName = viewModel.bizObjName;
        let i18nRoot = objMeta?.['i18n:root'] || bizObjName;
    </c:script>

    <thisLib:GenGridImpl gridModel="${gridModel}" objMeta="${objMeta}"/>
</c:unit>

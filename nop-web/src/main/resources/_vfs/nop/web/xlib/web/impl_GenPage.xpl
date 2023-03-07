<c:unit>

    <c:script>
        import io.nop.core.resource.component.ResourceComponentManager;
        import io.nop.xlang.xmeta.SchemaLoader;
        import io.nop.xlang.xpl.xlib.XplLibHelper;

        let viewModel = ResourceComponentManager.instance().loadComponentModel(view);
        let pageModel = viewModel.pages.getByKey(page);
        $.notNull(pageModel,"page:"+page+',view='+view);

        let objMeta = SchemaLoader.loadXMeta(viewModel.objMeta);
        let controlLib = XplLibHelper.loadLib(viewModel.controlLib || '/nop/web/xlib/control.xlib');
        let bizObjName = viewModel.bizObjName;
        let i18nRoot = objMeta['i18n:root'] || bizObjName;
    </c:script>

    <c:choose>
        <when test="${pageModel.type == 'crud'}">
            <c:include src="page_crud.xpl"/>
        </when>
        <when test="${pageModel.type == 'picker'}">
            <c:include src="page_picker.xpl"/>
        </when>
        <when test="${pageModel.type == 'simple'}">
            <c:include src="page_simple.xpl"/>
        </when>
        <when test="${pageModel.type == 'tabs'}">
            <c:include src="page_tabs.xpl"/>
        </when>
        <otherwise>
            <c:throw errorCode="nop.err.web.unknown-page-type" params="${{type:pageModel.type}}"/>
        </otherwise>
    </c:choose>
</c:unit>
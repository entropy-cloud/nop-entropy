<c:unit>

    <c:script>
        import io.nop.core.resource.component.ResourceComponentManager;
        import io.nop.xlang.xmeta.SchemaLoader;
        import io.nop.xlang.xpl.xlib.XplLibHelper;
        import io.nop.xui.utils.XuiHelper;

        let viewModel = ResourceComponentManager.instance().loadComponentModel(view);
        let formModel = viewModel.forms.getByKey(form);
        $.notNull(formModel,"form:"+form+',view='+view);

        let objMeta = SchemaLoader.loadXMeta(formModel.objMeta || viewModel.objMeta);
        let controlLib = XplLibHelper.loadLib(viewModel.controlLib || '/nop/web/xlib/control.xlib');
        let bizObjName = viewModel.bizObjName;
        let i18nRoot = objMeta['i18n:root'] || bizObjName;

        let formSelection = XuiHelper.getFormSelection(formModel, objMeta);
    </c:script>

    <form name="${formModel.id}" xpl:attrs="xpl('thisLib:FormDefaultAttrs',formModel)"  xpl:skipIf="skipForm">
        <c:unit xpl:slot="default" xpl:slotArgs="{formModel,formSelection,objMeta}"/>

        <thisLib:GenFormBody formModel="${formModel}" objMeta="${objMeta}"/>
    </form>
</c:unit>
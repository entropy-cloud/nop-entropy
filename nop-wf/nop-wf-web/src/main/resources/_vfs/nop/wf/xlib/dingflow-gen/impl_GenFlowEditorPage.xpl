<c:unit>
    <c:script><![CDATA[
        import io.nop.core.resource.component.ResourceComponentManager;
        import io.nop.xlang.xpl.xlib.XplLibHelper;

        const model = ResourceComponentManager.instance().loadComponentModel(modelPath);
        let controlLib = XplLibHelper.loadLib('/nop/web/xlib/control.xlib');
        let bizObjName = 'NopWfDefinition'
        let i18nRoot = bizObjName
        let fixedProps = null;
    ]]></c:script>
    <c:import from="/nop/web/xlib/web.xlib" />

    <page title="@i18n:common.flowDesigner" bodyClassName="flex overflow-auto">
        <body>
            <nop-flow-editor name="dingflow-editor" componentLib="/nop/wf/designer/dingflow.lib.js" enabled="@:true">
                <flowEditorSchema>
                    <editForms>
                        <c:for var="form" items="${model.forms}" >
                            <form name="editForm-${form.id}" j:key="${form.id}"  panelClassName="nop-full"
                                  preventEnterSubmit="@:true">
                                <web:GenFormBody formModel="${form}" objMeta="${null}" />
                            </form>
                        </c:for>
                    </editForms>
                </flowEditorSchema>

                <body>
                    <nop-flow-editor-canvas />
                </body>
            </nop-flow-editor>
        </body>
    </page>
</c:unit>
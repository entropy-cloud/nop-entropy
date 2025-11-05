<c:unit xmlns:c="c" xmlns:thisLib="thisLib" xmlns:xpl="xpl" xmlns:j="j">
    <c:script><![CDATA[
        import io.nop.xui.utils.XuiHelper;
        const formModel = viewModel.forms.getByKey(pageModel.form);
        $.notNull(formModel,"form="+pageModel.form+",view="+viewModel.resourcePath());

        const formSelection = XuiHelper.getFormSelection(formModel,objMeta);
        const formProps =  XuiHelper.getFormProps(formModel,objMeta);

        let formData = {}
        formProps.forEach(name=>{
            formData[name] = '$' + name;
        })

        const api = pageModel.api || formModel.api;

        const genScope = {formSelection,formProps,formData}

    ]]></c:script>

    <page name="${pageModel.name}" size="${formModel.size || xpl('thisLib:GetFormDefaultSize',formModel)}"
        data="${pageModel.data}">
        <title>${ ('@i18n:'+i18nRoot+'.forms.'+formModel.id+'.$title').$i18n(formModel.title)}</title>

        <body>
            <form name="${formModel.id}" mode="${formModel.layoutMode || 'horizontal'}"
                  panelClassName="${pageModel.panelClassName || formModel.panelClassName}"
                  redirect="${pageModel.redirect || formModel.redirect}"
                  resetAfterSubmit="${pageModel.resetAfterSubmit ?? pageModel.resetAfterSubmit}"
                  reload="${pageModel.reload || formModel.reload}"
                  xpl:attrs="xpl('thisLib:FormDefaultAttrs',formModel)">
                <data xpl:attrs="formModel.data" xpl:if="formModel.data"/>

                <initApi xpl:attrs="xpl('thisLib:NormalizeApi',pageModel.initApi || formModel.initApi,genScope)"
                         xpl:if="pageModel.initApi || formModel.initApi"/>
                <api xpl:attrs="xpl('thisLib:NormalizeApi',api,genScope)" xpl:if="api"/>

                <messages xpl:attrs="{...pageModel.messages,...formModel.messages}" />

                <asyncApi xpl:attrs="xpl('thisLib:NormalizeApi',formModel.asyncApi,genScope)"
                          xpl:if="formModel.asyncApi"/>
                <initAsyncApi xpl:attrs="xpl('thisLib:NormalizeApi',formModel.initAsyncApi,genScope)"
                              xpl:if="formModel.initAsyncApi" objMeta="${objMeta}"/>

                <thisLib:GenFormBody formModel="${formModel}" objMeta="${objMeta}"/>

                <actions j:list="true" xpl:if="pageModel.useFormActions and pageModel.actions.size() > 0">
                    <thisLib:GenActions actions="${pageModel.actions}" genScope="${genScope}"/>
                </actions>
            </form>
        </body>

        <actions j:list="true" xpl:if="!pageModel.noActions and !pageModel.useFormActions and pageModel.actions?.size() > 0">
            <thisLib:GenActions actions="${pageModel.actions}" genScope="${genScope}"/>
        </actions>

        <actions j:list="true" xpl:if="pageModel.noActions" />
    </page>
</c:unit>

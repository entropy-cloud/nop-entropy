<c:unit xmlns:c="c" xmlns:thisLib="thisLib" xmlns:xpl="xpl" xmlns:j="j">
    <c:script><![CDATA[
        import io.nop.xui.utils.XuiHelper;
        const formModel = viewModel.forms.getByKey(pageModel.form);
        $.notNull(formModel,"form="+pageModel.form+",view="+viewModel.resourcePath());

        const formSelection = XuiHelper.getFormSelection(formModel,objMeta);
        const formProps =  XuiHelper.getFormProps(formModel,objMeta);

        let formData = {}
        formProps.forEach(name=>{
            formData[name] = '$' + '{' + name + '}';
        })

        const api = pageModel.api || formModel.api;

        const genScope = {formSelection,formProps,formData}

        // flux form 用 submitAction 提交（非 AMIS 的 api）。把 page/form 的 api 转成 flux ActionSchema，
        // 这样缺省 submit 按钮（submitForm action）才能正确触发提交。
        let submitAction = null;
        if(api != null){
            const _n = xpl('thisLib:NormalizeApi', api, genScope);
            if(_n != null){
                submitAction = { action:'ajax', args: _.filterNull({url:_n.url, method: api.method || 'post', data: _n.data || genScope.formData, 'gql:selection': _n['gql:selection']}) };
            }
        }

        // flux 前端不会像 AMIS 那样为空 actions 的 dialog 隐式渲染 submit/cancel 按钮。
        // 当 form 没有显式 actions 且未设 noActions 时，补充缺省的提交、取消两个按钮。
        const _submitLabel = formModel.submitText != null ? formModel.submitText : ('@i18n:common.confirm').$i18n('确认');
        const _cancelLabel = ('@i18n:common.cancel').$i18n('取消');
        const defaultFormActions = [
            { id:'_default_cancel', label: _cancelLabel, actionType:'close' },
            { id:'_default_submit', label: _submitLabel, level:'primary', onClick: { action:'component:submit', componentId: formModel.id } }
        ];

        // AMIS form 提交成功后会隐式关闭 dialog 并刷新 crud，flux 需要显式声明 onSubmitSuccess。
        // refreshSource 通过 targetId 刷新指定数据源（refreshTable 依赖 page context，在 dialog 中可能不可用）。
        const _onSubmitSuccess = { action:'refreshSource', args: { targetId: 'crud-grid' }, then:{action:'closeSurface'} };

    ]]></c:script>

    <page name="${pageModel.name}" size="${formModel.size || xpl('thisLib:GetFormDefaultSize',formModel)}"
        data="${pageModel.data}">
        <title>${ ('@i18n:'+i18nRoot+'.forms.'+formModel.id+'.$title').$i18n(formModel.title)}</title>

        <body>
            <form name="${formModel.id}" id="${formModel.id}" mode="${formModel.layoutMode || 'horizontal'}"
                  panelClassName="${pageModel.panelClassName || formModel.panelClassName}"
                  redirect="${pageModel.redirect || formModel.redirect}"
                  resetAfterSubmit="${pageModel.resetAfterSubmit ?? pageModel.resetAfterSubmit}"
                  reload="${pageModel.reload || formModel.reload}"
                  xpl:attrs="xpl('thisLib:FluxFormDefaultAttrs',formModel)">
                <data xpl:attrs="formModel.data" xpl:if="formModel.data"/>

                <initApi xpl:attrs="xpl('thisLib:NormalizeApi',pageModel.initApi || formModel.initApi,genScope)"
                         xpl:if="pageModel.initApi || formModel.initApi"/>
                <api xpl:attrs="xpl('thisLib:NormalizeApi',api,genScope)" xpl:if="api"/>
                <submitAction xpl:attrs="submitAction" xpl:if="submitAction"/>
                <onSubmitSuccess xpl:attrs="_onSubmitSuccess" xpl:if="submitAction"/>

                <messages xpl:attrs="{...pageModel.messages,...formModel.messages}" />

                <asyncApi xpl:attrs="xpl('thisLib:NormalizeApi',formModel.asyncApi,genScope)"
                          xpl:if="formModel.asyncApi"/>
                <initAsyncApi xpl:attrs="xpl('thisLib:NormalizeApi',formModel.initAsyncApi,genScope)"
                              xpl:if="formModel.initAsyncApi"/>

                <thisLib:GenFormBody formModel="${formModel}" objMeta="${objMeta}"/>

                <actions j:list="true" xpl:if="pageModel.useFormActions and pageModel.actions.size() > 0">
                    <thisLib:GenActions actions="${pageModel.actions}" genScope="${genScope}"/>
                </actions>
            </form>
        </body>

        <actions j:list="true" xpl:if="!pageModel.noActions and !pageModel.useFormActions and pageModel.actions?.size() > 0">
            <thisLib:GenActions actions="${pageModel.actions}" genScope="${genScope}"/>
        </actions>

        <actions j:list="true" xpl:if="!pageModel.noActions and !pageModel.useFormActions and !(pageModel.actions?.size() > 0)">
            <thisLib:GenActions actions="${defaultFormActions}" genScope="${genScope}"/>
        </actions>

        <actions j:list="true" xpl:if="pageModel.noActions" />
    </page>
</c:unit>

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
        // 没有 submitAction 时为纯查看模式，submit 按钮只关闭 dialog。
        const _submitLabel = formModel.submitText != null ? formModel.submitText : ('@i18n:common.confirm').$i18n('确认');
        const _cancelLabel = ('@i18n:common.cancel').$i18n('取消');
        const _hasSubmitAction = submitAction != null;
        const defaultFormActions = [
            { id:'_default_cancel', label: _cancelLabel, actionType:'close' },
            { id:'_default_submit', label: _submitLabel, level:'primary',
              onClick: _hasSubmitAction
                ? { action:'submitForm', then: { action:'closeSurface' } }
                : { action:'closeSurface' } }
        ];

        // flux 用 refreshNearest 沿 scope 链向上刷新最近的 CRUD/data-source（不需要知道 targetId）。

        let loadAction = null;
        const initApi = pageModel.initApi || formModel.initApi;
        if(initApi != null){
            const _n = xpl('thisLib:NormalizeApi', initApi, genScope);
            if(_n != null){
                // 保持 URL 中的 {@...} 模板不被求值（id 来自父 scope 的行数据，不在 genScope 中），
                // NormalizeApi 求值过的 url 会丢失行级模板变量，直接用 initApi 的原始 url
                const rawUrl = initApi.url;
                loadAction = { action:'ajax', args: _.filterNull({url: rawUrl, method: initApi.method || 'post', data: _n.data, 'gql:selection': _n['gql:selection']}) };
            }
        }

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
                  submitScope="surface"
                  xpl:attrs="xpl('thisLib:FluxFormDefaultAttrs',formModel)">
                <data xpl:attrs="formModel.data" xpl:if="formModel.data"/>

                <loadAction xpl:if="loadAction" xpl:attrs="loadAction"/>
                <api xpl:attrs="xpl('thisLib:NormalizeApi',api,genScope)" xpl:if="api"/>
                <submitAction xpl:attrs="submitAction" xpl:if="submitAction"/>
                <onSubmitSuccess j:list="true" xpl:if="submitAction">
                    <action action="closeSurface"/>
                    <action action="refreshNearest"/>
                </onSubmitSuccess>

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

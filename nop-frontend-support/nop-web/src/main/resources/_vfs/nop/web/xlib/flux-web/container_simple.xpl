<c:unit xmlns:c="c" xmlns:thisLib="thisLib" xmlns:xpl="xpl" xmlns:j="j">
    <c:script><![CDATA[
        import io.nop.xui.utils.XuiHelper;

        const formModel = viewModel.forms.getByKey(containerModel.form);
        $.notNull(formModel,"form="+containerModel.form+",view="+viewModel.resourcePath());

        const formSelection = XuiHelper.getFormSelection(formModel,objMeta);
        const formProps =  XuiHelper.getFormProps(formModel,objMeta);

        let formData = {}
        formProps.forEach(name=>{
            formData[name] = '$' + '{' + name + '}';
        })

        const api = containerModel.api || formModel.api;
        const genScope = {formSelection,formProps,formData}

        // 与 page_simple.xpl 相同的 submit/load 语义：api → submitAction，initApi → loadAction
        let submitAction = null;
        if(api != null){
            const _n = xpl('thisLib:NormalizeApi', api, genScope);
            if(_n != null){
                submitAction = { action:'ajax', args: _.filterNull({url:_n.url, method: api.method || 'post', data: _n.data, includeScope: _n.includeScope, selection: _n.selection}) };
            }
        }

        let loadAction = null;
        const initApi = containerModel.initApi || formModel.initApi;
        if(initApi != null){
            const _n = xpl('thisLib:NormalizeApi', initApi, genScope);
            if(_n != null){
                const rawUrl = initApi.url;
                loadAction = { action:'ajax', args: _.filterNull({url: rawUrl, method: initApi.method || 'post', data: _n.data, includeScope: _n.includeScope, selection: _n.selection}) };
            }
        }
    ]]></c:script>

    <form name="${formModel.id}" id="${formModel.id}" mode="${formModel.layoutMode || 'horizontal'}"
          panelClassName="${containerModel.panelClassName || formModel.panelClassName}"
          redirect="${containerModel.redirect || formModel.redirect}"
          resetAfterSubmit="${containerModel.resetAfterSubmit ?? formModel.resetAfterSubmit}"
          reload="${containerModel.reload || formModel.reload}"
          submitScope="surface"
          xpl:attrs="xpl('thisLib:FluxFormDefaultAttrs',formModel)">
        <data xpl:attrs="formModel.data" xpl:if="formModel.data"/>
        <loadAction xpl:if="loadAction" xpl:attrs="loadAction"/>
        <api xpl:attrs="xpl('thisLib:NormalizeApi',api,genScope)" xpl:if="api"/>
        <submitAction xpl:attrs="submitAction" xpl:if="submitAction"/>
        <messages xpl:attrs="{...containerModel.messages,...formModel.messages}" />
        <asyncApi xpl:attrs="xpl('thisLib:NormalizeApi',formModel.asyncApi,genScope)" xpl:if="formModel.asyncApi"/>
        <initAsyncApi xpl:attrs="xpl('thisLib:NormalizeApi',formModel.initAsyncApi,genScope)" xpl:if="formModel.initAsyncApi"/>
        <thisLib:GenFormBody formModel="${formModel}" objMeta="${objMeta}"/>
    </form>
</c:unit>

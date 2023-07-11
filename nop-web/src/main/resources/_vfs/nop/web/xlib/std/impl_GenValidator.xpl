<c:unit xpl:outputMode="xjson" xmlns:j="j">
    <input-number name="fatalSeverity" />
    <intput-table name="checks" label="@i18n:common.validatorCheck|验证规则"
                  needConfirm="@:false" addable="@:false" removable="@:false" draggable="@:false">
        <columns j:list="true">
            <input-text name="id" label="ID"/>
            <input-text name="errorCode" label="@i18n:common.errorCode|错误码"/>
            <input-text name="errorDescription" label="@i18n:common.errorDescription|错误描述"/>
            <input-text name="errorParams" label="@i18n:common.errorParams|错误参数"/>
            <condition-builder name="condition" embed="@:false"
                               title="@i18n:common.conditionConfig|条件配置">
                <c:script>
                    import io.nop.web.utils.ConditionSchemaHelper;
                    import io.nop.xlang.xmeta.SchemaLoader;

                    const objMeta = SchemaLoader.loadXMeta(metaPath);
                    const fields = ConditionSchemaHelper.schemaToFields(null, objMeta);
                </c:script>
                <fields>${fields}</fields>
            </condition-builder>
        </columns>
    </intput-table>
</c:unit>
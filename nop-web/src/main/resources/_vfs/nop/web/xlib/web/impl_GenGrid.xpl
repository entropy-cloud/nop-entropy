<c:unit>
    <c:include src="init_grid_gen_scope.xpl" />

    <crud xpl:attrs="xpl('thisLib:GridDefaultAttrs', gridModel)"
          syncLocation="@:false">
        <initApi xpl:attrs="xpl('thisLib:NormalizeApi',gridModel.initApi,genScope)"
                 xpl:if="gridModel.initApi"/>
        <api xpl:attrs="xpl('thisLib:NormalizeApi',gridModel.api,genScope)"/>

        <saveOrderApi xpl:attrs="xpl('thisLib:NormalizeApi',gridModel.saveOrderApi,genScope)"
                      xpl:if="gridModel.saveOrderApi"/>


        <columns j:list="true">
            <thisLib:GenGridCols gridModel="${gridModel}" objMeta="${objMeta}"/>
        </columns>
    </crud>
</c:unit>

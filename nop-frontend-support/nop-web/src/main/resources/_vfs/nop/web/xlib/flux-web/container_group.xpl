<c:unit xmlns:c="c" xmlns:thisLib="thisLib" xmlns:xpl="xpl" xmlns:j="j">
    <c:script><![CDATA[
        // xview group → Flux GridSchema 字段映射（设计 §3.4）：
        // columns/gap 透传；autoFlow 枚举映射（row-dense/column-dense → row dense/column dense）；
        // alignItems/justifyItems 过滤 normal/baseline（Flux 枚举仅 start/end/center/stretch）；
        // responsiveColumns（xview string vs Flux {sm,md,lg}）暂不输出
        const gridAttrs = {};
        if (containerModel.columns != null) gridAttrs.columns = containerModel.columns;
        if (containerModel.gap != null) gridAttrs.gap = containerModel.gap;
        if (containerModel.autoFlow != null) {
            const af = containerModel.autoFlow;
            gridAttrs.autoFlow = af == 'row-dense' ? 'row dense' : (af == 'column-dense' ? 'column dense' : af);
        }
        if (containerModel.alignItems != null && containerModel.alignItems != 'normal' && containerModel.alignItems != 'baseline')
            gridAttrs.alignItems = containerModel.alignItems;
        if (containerModel.justifyItems != null && containerModel.justifyItems != 'normal' && containerModel.justifyItems != 'baseline')
            gridAttrs.justifyItems = containerModel.justifyItems;
    ]]></c:script>

    <grid xpl:attrs="gridAttrs">
        <items j:list="true">
            <c:for var="childModel" items="${containerModel.body}">
                <c:script><![CDATA[
                    // colSpan/rowSpan 仅继承 UiPageModel 的容器（crud/tabs）支持；
                    // simple/wizard/group 容器可入 body 但无 span 字段——prop_allow 判存在后访问，避免 unknown-prop
                    let colSpan = null;
                    if (childModel.prop_allow('colSpan')) colSpan = childModel.colSpan;
                    let rowSpan = null;
                    if (childModel.prop_allow('rowSpan')) rowSpan = childModel.rowSpan;
                ]]></c:script>
                <_ key="${childModel.name}">
                    <colSpan xpl:if="colSpan != null">${colSpan}</colSpan>
                    <rowSpan xpl:if="rowSpan != null">${rowSpan}</rowSpan>
                    <body j:list="true">
                        <thisLib:GenContainerModel containerModel="${childModel}"/>
                    </body>
                </_>
            </c:for>
        </items>
    </grid>
</c:unit>

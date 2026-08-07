<c:unit xmlns:c="c" xmlns:thisLib="thisLib" xmlns:xpl="xpl" xmlns:j="j">
    <!--
    embed 页面类型渲染：按 pageModel.path 加载外部 view.xml / page.yaml 的页面或网格，
    再经 WebPageHelper.applyViewOverride（Io.nop.core.lang.json.delta.JsonMerger delta 合并语义）
    应用 pageModel.override——map 按 key 合并（! 前缀强制覆盖）、list 按唯一键(id/name)合并、
    无唯一键整段替换。override 为 null/空时基础输出不变。
    未配置 path 或 page/grid 引用时显式抛错（不静默 noop）。宿主 view 不重复 objMeta/controlLib，
    由 path 指向的外部 view 自载。
    -->
    <c:script>
        import io.nop.web.page.WebPageHelper;
        import io.nop.core.resource.ResourceHelper;

        let path = pageModel.path;
        let base = null;
    </c:script>

    <c:choose>
        <when test="${!path}">
            <c:throw errorCode="nop.err.web.embed-page-path-required"
                     params="${{pageName: pageModel.name}}"/>
        </when>
        <when test="${path.endsWith('.view.xml')}">
            <c:choose>
                <when test="${pageModel.page}">
                    <c:collect outputMode="xjson" xpl:return="base">
                        <thisLib:GenPage view="${path}" page="${pageModel.page}"/>
                    </c:collect>
                </when>
                <when test="${pageModel.grid}">
                    <thisLib:GenTable view="${path}" grid="${pageModel.grid}" xpl:return="base"/>
                </when>
                <otherwise>
                    <c:throw errorCode="nop.err.web.embed-page-ref-required"
                             params="${{pageName: pageModel.name, path: path}}"/>
                </otherwise>
            </c:choose>
        </when>
        <otherwise>
            <c:script>
                let loadPath = path;
                // Flux 模式下 page.yaml 优先回退到同目录同名 flux.yaml
                let fluxPath = WebPageHelper.toFluxPagePath(path);
                if (fluxPath &amp;&amp; ResourceHelper.resolve(fluxPath).exists()) {
                    loadPath = fluxPath;
                }
                base = WebPageHelper.internalLoadPage(loadPath);
            </c:script>
        </otherwise>
    </c:choose>

    <c:script>
        let result = WebPageHelper.applyViewOverride(base, pageModel.override);
    </c:script>
    <_ xpl:attrs="result"/>
</c:unit>

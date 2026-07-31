<c:unit xmlns:c="c" xmlns:thisLib="thisLib" xmlns:xpl="xpl" xmlns:j="j">
    <c:script>
        // body 级 crud 容器复用 grid_crud.xpl 的无 page 外壳片段；grid_crud.xpl 内部以 pageModel 引用容器模型
        const pageModel = containerModel;
    </c:script>
    <c:include src="grid_crud.xpl"/>
</c:unit>

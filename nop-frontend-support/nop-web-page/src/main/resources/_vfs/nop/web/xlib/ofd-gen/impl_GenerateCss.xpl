<c:unit xmlns:c="c">
    <c:script>
        import io.nop.web.page.grapn_designer.GraphDesignerCodeGenerator;
        const generator = new GraphDesignerCodeGenerator(codeGenModel, "/nop/wf/xlib/ofd-gen.xlib");
    </c:script>
    <c:out escape="none" value="${generator.generateCss(designerId)}"/>
</c:unit>
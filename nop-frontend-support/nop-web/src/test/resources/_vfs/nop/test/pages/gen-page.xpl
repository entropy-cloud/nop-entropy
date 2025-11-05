<page>
    <title>x</title>

    <body>
        <c:script>
            let updateProps = ["a","b"]
            let visible = null;
        </c:script>
        <form x:extends="form.yaml" name="frm">
            <visibleOn>${visible}</visibleOn>
            <api url="@mutation:test/id?id=$id">
                <data>
                    <c:for var="prop" items="${updateProps}">
                        <_ j:key="${prop}">$${prop}</_>
                    </c:for>
                </data>
            </api>
            <body>
                <button x:id="a" label="B2"/>
            </body>
        </form>
    </body>
</page>
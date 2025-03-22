# Hyperlink

```xml
<w:p>
    <w:r>
        <w:t xml:space="preserve">This is an external link to </w:t>
    </w:r>
    <w:hyperlink r:id="rId4">
        <w:r>
            <w:rPr>
                <w:rStyle w:val="Hyperlink"/>
            </w:rPr>
            <w:t>xpl:entity.name</w:t>
        </w:r>
    </w:hyperlink>
</w:p>
```

NopReport Engine identifies xpl expressions and performs the following actions:

1. If it is xpl-begin or xpl-end, pair them and insert them into their common parent node's external children. This allows inserting `begin` in the first table cell and `end` in the last table cell to achieve row-wise iteration.
2. If it is tpl-expr or expr, replace the `<w:hyperlink>` tag with `<w:r>` tags (using sourceNode to replace linkNode). The current linkNode is derived from `<w:hyperlink>`'s child `<w:r>`, ensuring style settings are preserved.
3. If it is xpl, check whether the parsed node is `<w:p>`. If so, replace the entire `<w:hyperlink>` tag within `<w:p>` with the xpl tag's content; otherwise, only replace the linkNode.

Another Hyperlink Form

```xml
<w:p>
    <w:pPr>
        <w:rPr>
            <w:rFonts w:ascii="Cambria" w:hAnsi="Cambria"/>
            <w:kern w:val="0"/>
            <w:sz w:val="20"/>
            <w:szCs w:val="20"/>
        </w:rPr>
    </w:pPr>
    <w:r>
        <w:fldChar w:fldCharType="begin"/>
    </w:r>
    <w:r>
        <w:instrText xml:space="preserve"> HYPERLINK "xpl:%3cc:for%20var=%22project%22%20items=%22%7bentity.projectList%7d%22%3e" </w:instrText>
    </w:r>
    <w:r>
        <w:fldChar w:fldCharType="separate"/>
    </w:r>
    <w:r>
        <w:rPr>
            <w:rStyle w:val="7"/>
            <w:rFonts w:ascii="Cambria" w:hAnsi="Cambria"/>
            <w:kern w:val="0"/>
            <w:sz w:val="20"/>
            <w:szCs w:val="20"/>
        </w:rPr>
        <w:t>&lt;c:for&gt;</w:t>
    </w:r>
    <w:r>
        <w:rPr>
            <w:rStyle w:val="7"/>
            <w:rFonts w:ascii="Cambria" w:hAnsi="Cambria"/>
            <w:kern w:val="0"/>
            <w:sz w:val="20"/>
            <w:szCs w:val="20"/>
        </w:rPr>
        <w:fldChar w:fldCharType="end"/>
    </w:r>
</w:p>
```

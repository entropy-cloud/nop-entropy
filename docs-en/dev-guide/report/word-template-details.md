# Hyperlinks

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

After the NopReport engine recognizes an xpl expression, it performs the following processing:

1. If it is xpl-begin or xpl-end, pair them and insert them outside their common parent node. In this way, by inserting begin in the first cell of a table and end in the last cell, you can achieve a whole-row loop effect.
2. If it is tpl-expr or expr, replace the `w:hyperlink` tag with a `w:r` tag (replace linkNode with sourceNode). At this point, linkNode is actually derived from the `w:r` tag inside `w:hyperlink`, which preserves the styling.
3. If it is xpl, check whether the node parsed from the xpl source code is `w:p`. If so, replace the entire `w:p` tag containing `w:hyperlink` with the content of the xpl tag; otherwise, replace only linkNode.

Another form of hyperlink

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
        <w:instrText xml:space="preserve"> HYPERLINK "xpl:%3cc:for%20var=%22project%22%20items=%22$%7bentity.projectList%7d%22%3e" </w:instrText>
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
        <w:t>
            &lt;c:for&gt;
        </w:t>
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
<!-- SOURCE_MD5:f29f5422bd9fceeb8fd66b405c1287ec-->

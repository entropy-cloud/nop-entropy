# 超链接

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

NopReport引擎识别出xpl表达式之后会做如下处理：

1. 如果是xpl-begin或者xpl-end，则将它们配对，插入到它们共同的父节点外部。这样在表格的第一个单元格插入begin，在最后一个单元格插入end，就可以实现整行循环的效果。
2. 如果是tpl-expr或者expr，需要将`w:hyperlink`标签替换为`w:r`标签（使用sourceNode替换linkNode）。此时的linkNode实际上是从`w:hyperlink`内的`w:r`标签加工而来，这样可以保持样式设置
3. 如果是xpl，则检查xpl源码解析得到的节点是否是`w:p`,如果是，则将`w:hyperlink`所在的`w:p`标签整体替换为xpl标签的内容，否则只替换linkNode。

另外一种超链接形式

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

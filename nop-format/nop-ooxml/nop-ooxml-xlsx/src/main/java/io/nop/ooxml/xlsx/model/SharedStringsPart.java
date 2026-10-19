/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.model;

import io.nop.core.resource.IResource;
import io.nop.excel.model.ExcelRichText;
import io.nop.ooxml.xlsx.parse.SharedStringsTableParser;

import java.util.List;

/**
 * <p>
 * This is a lightweight way to process the Shared Strings table. Most of the text cells will reference something from
 * in here.
 * <p>
 * Note that each SI entry can have multiple T elements, if the string is made up of bits with different formatting.
 * <p>
 * Example input:
 *
 * <pre>{@code
 * <?xml version="1.0" encoding="UTF-8" standalone="yes" ?>
 *     <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="2" uniqueCount="2">
 *         <si>
 *             <r>
 *                 <rPr>
 *                     <b />
 *                     <sz val="11" />
 *                     <color theme="1" />
 *                     <rFont val="Calibri" />
 *                     <family val="2" />
 *                     <scheme val="minor" />
 *                 </rPr>
 *                 <t>This:</t>
 *             </r>
 *             <r>
 *                 <rPr>
 *                     <sz val="11" />
 *                     <color theme="1" />
 *                     <rFont val="Calibri" />
 *                     <family val="2" />
 *                     <scheme val="minor" />
 *                 </rPr>
 *                 <t xml:space="preserve">Causes Problems</t>
 *             </r>
 *         </si>
 *         <si>
 *             <t>This does not</t>
 *         </si>
 *     </sst>
 *  }</pre>
 */
public class SharedStringsPart {
    private int count;
    private int uniqueCount;
    private List<String> items;

    /**
     * WI11：与 items 平行的富文本 run 结构（仅含 <r> run 的 si 对应位非 null）。
     * 拍平语义（items）保持不变，消费方零破坏。
     */
    private List<ExcelRichText> richItems;

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public int getUniqueCount() {
        return uniqueCount;
    }

    public void setUniqueCount(int uniqueCount) {
        this.uniqueCount = uniqueCount;
    }

    public List<String> getItems() {
        return items;
    }

    public void setItems(List<String> items) {
        this.items = items;
    }

    public List<ExcelRichText> getRichItems() {
        return richItems;
    }

    public void setRichItems(List<ExcelRichText> richItems) {
        this.richItems = richItems;
    }

    public static SharedStringsPart parse(IResource resource) {
        SharedStringsTableParser parser = new SharedStringsTableParser(true);
        return parser.parseFromResource(resource);
    }

    public String getItemAt(int index) {
        if (index < 0 || index >= items.size())
            return null;
        return items.get(index);
    }

    /**
     * WI11：index 处 si 的富文本结构；无 run 或越界返回 null
     */
    public ExcelRichText getRichItemAt(int index) {
        if (richItems == null || index < 0 || index >= richItems.size())
            return null;
        return richItems.get(index);
    }
}

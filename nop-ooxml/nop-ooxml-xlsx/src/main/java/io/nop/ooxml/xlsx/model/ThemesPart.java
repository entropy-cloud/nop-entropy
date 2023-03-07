/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ooxml.xlsx.model;

import io.nop.commons.util.CollectionHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.ooxml.common.impl.XmlOfficePackagePart;

import java.util.ArrayList;
import java.util.List;

public class ThemesPart extends XmlOfficePackagePart {
    private List<String> colors = new ArrayList<>();

    public ThemesPart(String path, XNode node) {
        super(path, node);
        parse();
    }

    public String getThemeColor(int theme) {
        return CollectionHelper.get(colors, theme);
    }

    void parse() {
        XNode node = getNode();
        XNode themeElements = node.element("themeElements");
        if (themeElements == null)
            return;

        for (XNode child : themeElements.getChildren()) {
            if (child.getTagName().endsWith(":clrScheme")) {
                parseClrScheme(child);
            }
        }
    }

    private void parseClrScheme(XNode node) {
        // <a:lt1>
        //    <a:sysClr val="window" lastClr="FFFFFF"/>
        // </a:lt1>
        // <a:dk2>
        //    <a:srgbClr val="44546A"/>
        // </a:dk2>
        for (XNode child : node.getChildren()) {
            String name = child.getTagNameWithoutNs();
            int index = ThemeElement.getIndex(name);
            if (index >= 0) {
                for (XNode color : child.getChildren()) {
                    String tagName = color.getTagNameWithoutNs();
                    if (tagName.equals("sysClr")) {
                        String lastClr = color.attrText("lastClr");
                        if (lastClr != null) {
                            CollectionHelper.set(colors, index, lastClr);
                        }
                    } else if (tagName.equals("srgbClr")) {
                        String val = color.attrText("val");
                        if (val != null) {
                            CollectionHelper.set(colors, index, val);
                        }
                    }
                }
            }
        }
    }
}
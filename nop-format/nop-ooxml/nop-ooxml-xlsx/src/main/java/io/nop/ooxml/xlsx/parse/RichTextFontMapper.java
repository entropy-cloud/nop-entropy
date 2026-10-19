package io.nop.ooxml.xlsx.parse;

import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.excel.model.ExcelFont;

import java.util.Map;

/**
 * WI11：SpreadsheetML 富文本 run 属性（rPr）→ ExcelFont 映射。
 *
 * 注意与 chart 的 DrawingML（a:rPr）词汇表不同：此处 <b>/<i> 为空子元素（可选 val 属性）、
 * <sz>/<rFont>/<color> 为带 val/rgb 属性的子元素，且 sz 本身就是 pt 值（DrawingML 需 /100）。
 * 仅做基本映射；<color theme> 不映射（回退单元格样式字体色，见 plan 2480 D12 裁定）。
 */
final class RichTextFontMapper {
    private RichTextFontMapper() {
    }

    /**
     * 把 rPr 的一个子元素应用到 font。返回 false 表示该元素不属于 rPr 词汇表（调用方
     * 继续其他分支处理）。
     */
    static boolean applyTo(ExcelFont font, String localName, Map<String, ValueWithLocation> attrs) {
        switch (localName) {
            case "b":
                font.setBold(getBooleanVal(attrs, true));
                return true;
            case "i":
                font.setItalic(getBooleanVal(attrs, true));
                return true;
            case "sz": {
                String val = attr(attrs, "val");
                if (val != null) {
                    try {
                        font.setFontSize(Float.parseFloat(val.trim()));
                    } catch (NumberFormatException e) {
                        // 非法字号按缺失处理，回退单元格样式字号
                    }
                }
                return true;
            }
            case "rFont":
                font.setFontName(attr(attrs, "val"));
                return true;
            case "color": {
                // 仅映射 rgb 形态（ARGB/RGB hex）；theme/indexed/ tint 回退单元格样式色
                String rgb = attr(attrs, "rgb");
                if (rgb != null && !rgb.isEmpty())
                    font.setFontColor(rgb);
                return true;
            }
            default:
                // family/scheme/u/strike/vertAlign 等：本 WI 不映射
                return "family".equals(localName) || "scheme".equals(localName)
                        || "u".equals(localName) || "strike".equals(localName)
                        || "vertAlign".equals(localName);
        }
    }

    private static String attr(Map<String, ValueWithLocation> attrs, String name) {
        if (attrs == null)
            return null;
        ValueWithLocation vl = attrs.get(name);
        return vl == null ? null : vl.asString();
    }

    private static boolean getBooleanVal(Map<String, ValueWithLocation> attrs, boolean defaultValue) {
        String val = attr(attrs, "val");
        if (val == null || val.isEmpty())
            return defaultValue;
        return "1".equals(val.trim()) || "true".equalsIgnoreCase(val.trim());
    }
}

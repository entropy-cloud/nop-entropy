package io.nop.office.model.color;

import io.nop.commons.util.StringHelper;

public class OfficeColorHelper {
    private OfficeColorHelper() {
    }

    public static String toCssColor(String color) {
        if (StringHelper.isEmpty(color))
            return color;

        if (color.startsWith("0x"))
            color = color.substring(2);

        if (color.length() == 6) {
            return "#" + color;
        }

        if (color.length() == 8) {
            double alpha = Integer.parseInt(color.substring(0, 2), 16) / 255.0;
            int red = Integer.parseInt(color.substring(2, 4), 16);
            int green = Integer.parseInt(color.substring(4, 6), 16);
            int blue = Integer.parseInt(color.substring(6, 8), 16);
            return "rgba(" + red + "," + green + "," + blue + "," + alpha + ")";
        }

        // 1~2位hex（如默认字体色"0x0"）补零到6位，避免输出非法CSS（"#0"会被浏览器丢弃）
        if (color.length() <= 2) {
            return "#" + StringHelper.leftPad(color, 6, '0');
        }

        if (!color.startsWith("#"))
            return "#" + color;
        return color;
    }
}

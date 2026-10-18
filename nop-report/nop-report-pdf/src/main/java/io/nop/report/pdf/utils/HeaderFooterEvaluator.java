package io.nop.report.pdf.utils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 页眉页脚值占位符求值（WI4）：&P 当前页、&N 总页、&D 日期、&T 时间、&A 工作表名、&F 文件名、&Z 路径。
 *
 * <p>作用域裁定（plan 2467 D7）：{@code nop.report.pdf.page-number-scope=global}（缺省）时 &P/&N 为
 * 全文档序号/总页数，{@code sheet} 时为 sheet 内序号/该 sheet 页数；&D/&T/&A/&F/&Z 不受作用域影响。</p>
 */
public final class HeaderFooterEvaluator {

    public static final String DATE_FORMAT = "yyyy/M/d";
    public static final String TIME_FORMAT = "HH:mm:ss";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern(DATE_FORMAT);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern(TIME_FORMAT);

    private HeaderFooterEvaluator() {
    }

    /**
     * 求值上下文。
     */
    public static final class Context {
        private final int page;
        private final int totalPages;
        private final String sheetName;
        private final String fileName;
        private final String filePath;

        public Context(int page, int totalPages, String sheetName, String fileName, String filePath) {
            this.page = page;
            this.totalPages = totalPages;
            this.sheetName = sheetName == null ? "" : sheetName;
            this.fileName = fileName == null ? "" : fileName;
            this.filePath = filePath == null ? "" : filePath;
        }

        public int getPage() {
            return page;
        }

        public int getTotalPages() {
            return totalPages;
        }

        public String getSheetName() {
            return sheetName;
        }

        public String getFileName() {
            return fileName;
        }

        public String getFilePath() {
            return filePath;
        }
    }

    /**
     * 求值段文本中的值占位符（&& 转义为 &；字体/字号等非值代码由调用方先经
     * {@link HeaderFooterRuns#split} 处理，本方法不感知）。
     */
    public static String evaluate(String text, Context ctx) {
        if (text == null || text.indexOf('&') < 0)
            return text;

        StringBuilder sb = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            char ch = text.charAt(i);
            if (ch == '&' && i + 1 < text.length()) {
                char code = text.charAt(i + 1);
                switch (code) {
                    case 'P':
                        sb.append(ctx.getPage());
                        i += 2;
                        continue;
                    case 'N':
                        sb.append(ctx.getTotalPages());
                        i += 2;
                        continue;
                    case 'D':
                        sb.append(LocalDate.now().format(DATE));
                        i += 2;
                        continue;
                    case 'T':
                        sb.append(LocalTime.now().format(TIME));
                        i += 2;
                        continue;
                    case 'A':
                        sb.append(ctx.getSheetName());
                        i += 2;
                        continue;
                    case 'F':
                        sb.append(ctx.getFileName());
                        i += 2;
                        continue;
                    case 'Z':
                        sb.append(ctx.getFilePath());
                        i += 2;
                        continue;
                    case '&':
                        sb.append('&');
                        i += 2;
                        continue;
                    default:
                        sb.append(ch);
                        i++;
                        continue;
                }
            }
            sb.append(ch);
            i++;
        }
        return sb.toString();
    }
}

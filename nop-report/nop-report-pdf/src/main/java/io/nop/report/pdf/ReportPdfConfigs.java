package io.nop.report.pdf;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import static io.nop.api.core.config.AppConfig.varRef;

@Locale("zh-CN")
public interface ReportPdfConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(ReportPdfConfigs.class);

    @Description("额外的字体文件搜索目录，多个目录用逗号分隔。系统字体目录总会被搜索")
    IConfigReference<String> CFG_PDF_FONT_DIRS = varRef(s_loc, "nop.report.pdf.font-dirs", String.class, "");

    @Description("CJK回退字体名。未配置时自动从字体目录中选择第一个可编码中文探测字符的字体")
    IConfigReference<String> CFG_PDF_FALLBACK_FONT = varRef(s_loc, "nop.report.pdf.fallback-font", String.class, "");

    @Description("PDF分页时续页重复的表头行数，0表示不重复")
    IConfigReference<Integer> CFG_PDF_REPEAT_HEADER_ROWS = varRef(s_loc, "nop.report.pdf.repeat-header-rows", Integer.class, 1);

    @Description("PDF列拆分页重复的关键列数，0表示不重复")
    IConfigReference<Integer> CFG_PDF_REPEAT_KEY_COLUMNS = varRef(s_loc, "nop.report.pdf.repeat-key-columns", Integer.class, 1);

    @Description("多页PDF未配置页脚时自动绘制默认页码（第x页/共y页）")
    IConfigReference<Boolean> CFG_PDF_DEFAULT_PAGE_FOOTER = varRef(s_loc, "nop.report.pdf.default-page-footer", Boolean.class, true);

    @Description("fit/scale缩放后单元格最小可读字号（pt）。缩放导致字号低于该阈值时告警并收敛缩放比例，0表示不限制")
    IConfigReference<Float> CFG_PDF_MIN_FONT_SIZE = varRef(s_loc, "nop.report.pdf.min-font-size-pt", Float.class, 6f);

    @Description("页眉页脚&P/&N页码作用域：global=全文档序号/总页数（缺省），sheet=各工作表内序号/页数")
    IConfigReference<String> CFG_PDF_PAGE_NUMBER_SCOPE = varRef(s_loc, "nop.report.pdf.page-number-scope", String.class, "global");
}

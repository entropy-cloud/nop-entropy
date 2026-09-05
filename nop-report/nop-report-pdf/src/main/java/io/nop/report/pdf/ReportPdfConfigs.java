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
}

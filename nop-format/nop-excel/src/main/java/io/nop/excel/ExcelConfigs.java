package io.nop.excel;

import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import static io.nop.api.core.config.AppConfig.varRef;

public interface ExcelConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(ExcelConfigs.class);

    IConfigReference<Integer> CFG_EXCEL_MAX_SHEET_NAME_LENGTH =
            varRef(s_loc, "nop.excel.max-sheet-name-length", Integer.class, 31);
}

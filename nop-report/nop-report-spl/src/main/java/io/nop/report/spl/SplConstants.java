package io.nop.report.spl;

import io.nop.commons.util.CollectionHelper;

import java.util.List;

public interface SplConstants {
    String MODEL_TYPE_SPL = "spl";

    String FILE_TYPE_SPL_XLSX = "spl.xlsx";

    String FILE_TYPE_SPLX = "splx";

    String FILE_TYPE_SPL = "spl";

    List<String> FILE_TYPES_SPL_MODEL = CollectionHelper.buildImmutableList(FILE_TYPE_SPL, FILE_TYPE_SPLX);
}

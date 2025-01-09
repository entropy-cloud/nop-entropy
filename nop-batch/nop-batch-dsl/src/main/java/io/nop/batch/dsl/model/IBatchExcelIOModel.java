package io.nop.batch.dsl.model;

import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalFunction;

import java.util.List;

public interface IBatchExcelIOModel {
    IEvalAction getFilePath();

    String getTemplatePath();

    String getDataSheetName();

    String getHeaderSheetName();

    String getTrailerSheetName();

    List<String> getHeaders();

    List<String> getHeaderLabels();

    Integer getHeaderRowCount();

    IEvalFunction getHeadersNormalizer();
}

package io.nop.batch.dsl.model;

import io.nop.batch.dsl.model._gen._BatchExcelWriterModel;
import io.nop.core.lang.eval.IEvalFunction;

public class BatchExcelWriterModel extends _BatchExcelWriterModel implements IBatchExcelIOModel {
    public BatchExcelWriterModel() {

    }

    @Override
    public IEvalFunction getHeadersNormalizer() {
        return null;
    }
}

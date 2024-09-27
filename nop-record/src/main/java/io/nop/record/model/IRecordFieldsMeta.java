package io.nop.record.model;

import io.nop.commons.text.SimpleTextTemplate;
import io.nop.core.lang.eval.IEvalFunction;

import java.util.List;

public interface IRecordFieldsMeta {

    List<RecordFieldMeta> getFields();

    IEvalFunction getAfterRead();

    IEvalFunction getAfterWrite();

    SimpleTextTemplate getNormalizedTemplate();

    RecordFieldMeta requireField(String fieldName);
}

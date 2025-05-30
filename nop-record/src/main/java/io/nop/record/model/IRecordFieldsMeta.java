package io.nop.record.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.commons.text.SimpleTextTemplate;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.record.codec.IFieldTagBinaryCodec;
import io.nop.record.codec.IFieldTagTextCodec;

import java.util.List;

import static io.nop.record.RecordErrors.ARG_FIELD_NAME;
import static io.nop.record.RecordErrors.ERR_RECORD_UNKNOWN_FIELD;

public interface IRecordFieldsMeta extends ISourceLocationGetter {
    String getName();

    String getTagsCodec();

    IFieldTagBinaryCodec getResolvedTagBinaryCodec();

    void setResolvedTagBinaryCodec(IFieldTagBinaryCodec resolvedTagBinaryCodec);

    IFieldTagTextCodec getResolvedTagTextCodec();

    void setResolvedTagTextCodec(IFieldTagTextCodec resolvedTagTextCodec);

    List<RecordFieldMeta> getFields();

    boolean hasFields();

    IEvalFunction getAfterRead();

    IEvalFunction getAfterWrite();

    SimpleTextTemplate getNormalizedTemplate();

    default RecordFieldMeta requireField(String fieldName) {

        RecordFieldMeta field = getField(fieldName);
        if (field == null)
            throw new NopException(ERR_RECORD_UNKNOWN_FIELD)
                    .source(this)
                    .param(ARG_FIELD_NAME, fieldName);
        return field;
    }

    RecordFieldMeta getField(String fieldName);
}

package io.nop.record.serialization;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.IVariableScope;
import io.nop.api.core.util.Symbol;
import io.nop.api.core.validate.IValidationErrorCollector;
import io.nop.commons.bytes.ByteString;
import io.nop.commons.collections.bit.IBitSet;
import io.nop.commons.text.SimpleTextTemplate;
import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.model.FieldRepeatKind;
import io.nop.record.model.IRecordFieldsMeta;
import io.nop.record.model.RecordFieldMeta;
import io.nop.record.model.RecordObjectMeta;
import io.nop.record.model.RecordSimpleFieldMeta;
import io.nop.record.model.RecordTypeMeta;
import io.nop.record.writer.IDataWriterBase;
import io.nop.xlang.xmeta.SimpleSchemaValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collection;

import static io.nop.record.RecordErrors.ARG_CASE_VALUE;
import static io.nop.record.RecordErrors.ARG_FIELD_NAME;
import static io.nop.record.RecordErrors.ARG_FIELD_PATH;
import static io.nop.record.RecordErrors.ARG_TYPE_NAME;
import static io.nop.record.RecordErrors.ERR_RECORD_FIELD_IS_MANDATORY;
import static io.nop.record.RecordErrors.ERR_RECORD_NO_MATCH_FOR_CASE_VALUE;
import static io.nop.record.RecordErrors.ERR_RECORD_NO_SWITCH_ON_FIELD;
import static io.nop.record.RecordErrors.ERR_RECORD_TYPE_NO_FIELDS;
import static io.nop.record.RecordErrors.ERR_RECORD_UNKNOWN_OBJ_TYPE;
import static io.nop.record.serialization.RecordSerializeHelper.runIfExpr;

public abstract class AbstractModelBasedRecordSerializer<Output extends IDataWriterBase>
        implements IModelBasedRecordSerializer<Output> {
    static final Logger LOG = LoggerFactory.getLogger(AbstractModelBasedRecordSerializer.class);

    @Override
    public boolean writeObject(Output out, RecordObjectMeta recordMeta, Object record,
                               IFieldCodecContext context) throws IOException {
        if (!runIfExpr(recordMeta.getWriteWhen(), record, recordMeta.getName(), context)) {
            LOG.debug("nop.record.write-ignore-object:name={},fieldPath={},record={}",
                    recordMeta.getName(), context.getFieldPath(), record);
            return false;
        }

        if (recordMeta.getBeforeWrite() != null)
            recordMeta.getBeforeWrite().call3(null, out, record, context, context.getEvalScope());

        if (recordMeta.getResolvedBaseType() != null) {
            writeObject(out, recordMeta.getResolvedBaseType(), record, context);
        }

        IBitSet tags = writeTags(out, recordMeta, record, context);
        writeTemplateOrFields(out, tags, recordMeta, recordMeta.getCharsetObj(), record, context);

        if (recordMeta.getAfterWrite() != null)
            recordMeta.getAfterWrite().call3(null, out, record, context, context.getEvalScope());
        return true;
    }


    protected void writeTemplateOrFields(Output out, IBitSet tags,
                                         IRecordFieldsMeta fields, Charset charset, Object record, IFieldCodecContext context) throws IOException {
        SimpleTextTemplate template = fields.getNormalizedTemplate();
        if (template != null) {
            for (Object part : template.getParts()) {
                if (part instanceof Symbol) {
                    String name = ((Symbol) part).getText();
                    RecordFieldMeta field = fields.requireField(name);
                    if (!field.isMatchTag(tags))
                        continue;

                    writeField(out, field, record, context);
                } else {
                    writeString(out, part.toString(), charset, context);
                }
            }
        } else if (!fields.getFields().isEmpty()) {
            for (RecordFieldMeta field : fields.getFields()) {
                if (!field.isMatchTag(tags))
                    continue;
                writeField(out, field, record, context);
            }
        } else {
            throw new NopException(ERR_RECORD_TYPE_NO_FIELDS).source(fields).param(ARG_TYPE_NAME, fields.getName());
        }
    }

    @Override
    public boolean writeField(Output out, RecordFieldMeta field, Object record, IFieldCodecContext context) throws IOException {
        if (shouldIgnoreWrite(field, record, context)) {
            LOG.debug("nop.record.write-ignore-field:name={},fieldPath={},record={}",
                    field.getName(), context.getFieldPath(), record);
            return false;
        }

        if (field.getOffset() > 0) {
            writeOffset(out, field.getOffset(), context);
        }

        context.enterField(field);

        try {
            if (field.getBeforeWrite() != null)
                field.getBeforeWrite().call3(null, out, record, context, context.getEvalScope());

            if (field.getRepeatKind() != null) {
                Collection<?> c = (Collection<?>) record;
                writeCollection(out, field, c, context);
            } else {
                Object value = getProp(field, record, context);
                writeSwitch(out, field, record, value, context);
            }

            if (field.getAfterWrite() != null)
                field.getAfterWrite().call3(null, out, record, context, context.getEvalScope());
        } finally {
            context.exitField(field);
        }

        return true;
    }

    protected boolean shouldIgnoreWrite(RecordFieldMeta field, Object record, IFieldCodecContext context) {
        if (field.isSkipWhenWrite())
            return true;

        if (StringHelper.isEmptyObject(record) && field.isSkipWriteWhenEmpty())
            return true;

        if (!runIfExpr(field.getWriteWhen(), record, field.getName(), context))
            return true;

        return false;
    }

    protected void writeCollection(Output out, RecordFieldMeta field, Collection<?> coll, IFieldCodecContext context) throws IOException {
        long count = out.getWrittenCount();

        RecordSimpleFieldMeta repeatCountField = field.getRepeatCountField();
        if (repeatCountField != null) {
            writeField0(out, repeatCountField, coll, coll.size(), context);
        }

        for (Object o : coll) {
            writeSwitch(out, field, coll, o, context);
        }

        long endCount = out.getWrittenCount();
        if (field.getRepeatKind() == FieldRepeatKind.fixed) {
            int length = field.getLength();
            if (length > 0 && endCount - count < length) {
                int diff = length - (int) (endCount - count);
                if (field.getPadding() != null) {
                    writePadding(out, field.getPadding(), diff, context);
                } else {
                    writeOffset(out, diff, context);
                }
            }
        }
    }

    protected void writeSwitch(Output out, RecordFieldMeta field, Object record, Object value, IFieldCodecContext context) throws IOException {
        RecordTypeMeta typeMeta = determineObjectType(field, record, context);
        if (typeMeta != null) {
            writeObject(out, typeMeta, value, context);
        } else {
            writeField0(out, field, record, value, context);
        }
    }

    protected RecordTypeMeta determineObjectType(RecordFieldMeta field, Object record, IFieldCodecContext context) {
        if (field.getSwitchOnField() != null) {
            String onValue = ConvertHelper.toString(getPropByName(record, field.getSwitchOnField()));
            if (onValue == null)
                throw new NopException(ERR_RECORD_NO_SWITCH_ON_FIELD)
                        .source(field)
                        .param(ARG_FIELD_NAME, field.getName());

            String caseType = field.getTypeByCaseValue(onValue);
            if (caseType == null)
                throw new NopException(ERR_RECORD_NO_MATCH_FOR_CASE_VALUE)
                        .source(field)
                        .param(ARG_FIELD_NAME, field.getName())
                        .param(ARG_CASE_VALUE, onValue);

            RecordTypeMeta typeMeta = context.getType(caseType);
            if (typeMeta == null)
                throw new NopException(ERR_RECORD_NO_MATCH_FOR_CASE_VALUE)
                        .source(field)
                        .param(ARG_FIELD_NAME, field.getName())
                        .param(ARG_CASE_VALUE, onValue)
                        .param(ARG_TYPE_NAME, caseType);

            return typeMeta;
        }

        if (field.getTypeRef() != null) {
            RecordTypeMeta typeMeta = context.getType(field.getTypeRef());
            if (typeMeta == null)
                throw new NopException(ERR_RECORD_UNKNOWN_OBJ_TYPE)
                        .source(field)
                        .param(ARG_FIELD_NAME, field.getName())
                        .param(ARG_TYPE_NAME, field.getTypeRef());

            return typeMeta;
        }

        return null;
    }

    abstract protected IBitSet writeTags(Output out, RecordObjectMeta typeMeta,
                                         Object value, IFieldCodecContext context) throws IOException;

    abstract protected void writeOffset(Output out, int offset, IFieldCodecContext context) throws IOException;

    abstract protected void writePadding(Output output, ByteString padding, int length, IFieldCodecContext context) throws IOException;

    abstract protected void writeString(Output out, String str, Charset charset, IFieldCodecContext context) throws IOException;

    abstract protected void writeField0(Output out, RecordSimpleFieldMeta field, Object record, Object value, IFieldCodecContext context) throws IOException;

    protected Object getProp(RecordSimpleFieldMeta field, Object record, IFieldCodecContext context) {
        Object value;

        if (field.getContent() != null)
            return field.getContent();

        if (field.getExportExpr() != null) {
            value = field.getExportExpr().call1(null, record, context.getEvalScope());
        } else {
            if (record == null)
                return null;

            // 如果是虚拟分组字段
            if (field.isVirtual())
                return record;


            String propName = field.getPropOrFieldName();
            value = getPropByName(record, propName);
        }

        if (StringHelper.isEmptyObject(value)) {
            if (field.getDefaultValue() != null)
                value = field.getDefaultValue();
        }

        if (field.getType() != null) {
            value = field.getStdDataType().convert(value, err -> new NopException(err).source(field).param(ARG_FIELD_NAME, field.getName()));
        }

        validate(value, field, context);
        return value;
    }

    protected Object getPropByName(Object record, String propName) {
        if (record instanceof IVariableScope)
            return ((IVariableScope) record).getValueByPropPath(propName);

        return BeanTool.getComplexProperty(record, propName);
    }

    protected void validate(Object value, RecordSimpleFieldMeta field, IFieldCodecContext context) {
        if (field.isMandatory() && StringHelper.isEmptyObject(value)) {
            throw new NopException(ERR_RECORD_FIELD_IS_MANDATORY)
                    .param(ARG_FIELD_NAME, field.getName())
                    .param(ARG_FIELD_PATH, context.getFieldPath());
        }

        if (field.getSchema() != null) {
            SimpleSchemaValidator.INSTANCE.validate(field.getSchema(), field.getLocation(), field.getName(), value, context.getEvalScope(),
                    IValidationErrorCollector.THROW_ERROR);
        }
    }
}
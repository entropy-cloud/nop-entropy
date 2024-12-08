package io.nop.record.serialization;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.IVariableScope;
import io.nop.api.core.util.Symbol;
import io.nop.commons.collections.bit.IBitSet;
import io.nop.commons.text.SimpleTextTemplate;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.model.IRecordFieldsMeta;
import io.nop.record.model.RecordFieldMeta;
import io.nop.record.model.RecordObjectMeta;
import io.nop.record.model.RecordTypeMeta;
import io.nop.record.reader.IDataReaderBase;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collection;

import static io.nop.record.RecordErrors.ARG_CASE_VALUE;
import static io.nop.record.RecordErrors.ARG_FIELD_NAME;
import static io.nop.record.RecordErrors.ARG_TYPE_NAME;
import static io.nop.record.RecordErrors.ERR_RECORD_NO_MATCH_FOR_CASE_VALUE;
import static io.nop.record.RecordErrors.ERR_RECORD_NO_SWITCH_ON_FIELD;
import static io.nop.record.RecordErrors.ERR_RECORD_TYPE_NO_FIELDS;

public abstract class AbstractModelBasedRecordDeserializer<Input extends IDataReaderBase>
        implements IModelBasedRecordDeserializer<Input> {

    @Override
    public boolean readObject(Input in, RecordObjectMeta recordMeta, String name, Object record, IFieldCodecContext context) throws IOException {
        long pos = in.pos();
        if (recordMeta.getBeforeRead() != null)
            recordMeta.getBeforeRead().call3(null, in, record, context, context.getEvalScope());

        IBitSet tags = readTags(in, null, recordMeta, context);
        readTemplateOrFields(in, tags, recordMeta, null, record, context);

        if (recordMeta.getAfterRead() != null)
            recordMeta.getAfterRead().call3(null, in, record, context, context.getEvalScope());
        return pos != in.pos();
    }

    @Override
    public boolean readField(Input in, RecordFieldMeta field, Object record, IFieldCodecContext context) throws IOException {
        if (field.isSkipWhenRead())
            return false;

        if (field.getOffset() > 0) {
            readOffset(in, field.getOffset(), context);
        }

        if (field.getBeforeRead() != null)
            field.getBeforeRead().call3(null, in, record, context, context.getEvalScope());

        if (field.getCodec() != null && isUseBodyEncoder(field)) {
            readObjectWithCodec(in, field, record, context);
        } else {
            if (record instanceof Collection) {
                Collection<?> c = (Collection<?>) record;
                for (Object o : c) {
                    readSwitch(in, field, o, context);
                }
            } else {
                readSwitch(in, field, record, context);
            }
        }

        if (field.getAfterRead() != null)
            field.getAfterRead().call3(null, in, record, context, context.getEvalScope());
        return true;
    }

    protected void readSwitch(Input in, RecordFieldMeta field, Object record, IFieldCodecContext context) throws IOException {
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

            Object value = makeObjectProp(in, field, record, context);
            readObject(in, typeMeta, field.getName(), value, context);
            return;
        }

        IBitSet tags = readTags(in, field, null, context);
        readDivField(in, tags, field, record, context);
    }

    protected void readDivField(Input in, IBitSet tags, RecordFieldMeta field,
                                Object record, IFieldCodecContext context) throws IOException {
        if (field.isDiv()) {
            if (field.hasFields()) {
                for (RecordFieldMeta subField : field.getFields()) {
                    readField(in, subField, record, context);
                }
            }
        } else if (field.hasFields()) {
            Object value = makeObjectProp(in, field, record, context);
            readTemplateOrFields(in, tags, field, field.getCharsetObj(), value, context);
        } else {
            readField0(in, field, record, context);
        }
    }

    protected void readTemplateOrFields(Input in, IBitSet tags,
                                        IRecordFieldsMeta fields, Charset charset, Object record,
                                        IFieldCodecContext context) throws IOException {
        SimpleTextTemplate template = fields.getNormalizedTemplate();
        if (template != null) {
            for (Object part : template.getParts()) {
                if (part instanceof Symbol) {
                    String name = ((Symbol) part).getText();
                    RecordFieldMeta field = fields.requireField(name);
                    if (!field.isMatchTag(tags))
                        continue;

                    readField(in, field, record, context);
                } else {
                    readString(in, part.toString(), charset, context);
                }
            }
        } else if (!fields.getFields().isEmpty()) {
            for (RecordFieldMeta field : fields.getFields()) {
                if (!field.isMatchTag(tags))
                    continue;
                readField(in, field, record, context);
            }
        } else {
            throw new NopException(ERR_RECORD_TYPE_NO_FIELDS).source(fields).param(ARG_TYPE_NAME, fields.getName());
        }
    }

    protected Object makeObjectProp(Input in, RecordFieldMeta field, Object record, IFieldCodecContext context) {
        if (field.getParseExpr() != null)
            return field.getParseExpr().call2(null, in, record, context.getEvalScope());

        if (field.isDiv())
            return record;

        String propName = field.getPropOrFieldName();
        return BeanTool.instance().makeProperty(record, propName);
    }

    protected Object getPropByName(Object record, String propName) {
        if (record instanceof IVariableScope)
            return ((IVariableScope) record).getValueByPropPath(propName);

        return BeanTool.getComplexProperty(record, propName);
    }

    protected void setPropByName(Object record, String propName, Object value) {
        BeanTool.setProperty(record, propName, value);
    }

    protected boolean isUseBodyEncoder(RecordFieldMeta field) {
        return field.getSwitchOnField() != null || field.hasFields();
    }

    abstract protected IBitSet readTags(Input input, RecordFieldMeta field, RecordObjectMeta typeMeta, IFieldCodecContext context) throws IOException;

    abstract protected void readObjectWithCodec(Input in, RecordFieldMeta field, Object record,
                                                IFieldCodecContext context) throws IOException;

    abstract protected void readOffset(Input in, int offset, IFieldCodecContext context) throws IOException;

    abstract protected void readString(Input in, String str, Charset charset, IFieldCodecContext context) throws IOException;

    abstract protected void readField0(Input in, RecordFieldMeta field, Object record, IFieldCodecContext context) throws IOException;
}

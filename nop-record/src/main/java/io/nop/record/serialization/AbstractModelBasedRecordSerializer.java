package io.nop.record.serialization;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.IVariableScope;
import io.nop.api.core.util.Symbol;
import io.nop.commons.collections.bit.IBitSet;
import io.nop.commons.text.SimpleTextTemplate;
import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.model.IRecordFieldsMeta;
import io.nop.record.model.RecordFieldMeta;
import io.nop.record.model.RecordFieldSwitch;
import io.nop.record.model.RecordObjectMeta;
import io.nop.record.model.RecordTypeMeta;
import io.nop.record.writer.IDataWriterBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collection;

import static io.nop.record.RecordErrors.ARG_CASE_VALUE;
import static io.nop.record.RecordErrors.ARG_FIELD_NAME;
import static io.nop.record.RecordErrors.ARG_TYPE_NAME;
import static io.nop.record.RecordErrors.ERR_RECORD_NO_MATCH_FOR_CASE_VALUE;
import static io.nop.record.RecordErrors.ERR_RECORD_NO_SWITCH_ON_FIELD;
import static io.nop.record.serialization.RecordSerializeHelper.runIfExpr;

public abstract class AbstractModelBasedRecordSerializer<Output extends IDataWriterBase>
        implements IModelBasedRecordSerializer<Output> {
    static final Logger LOG = LoggerFactory.getLogger(AbstractModelBasedRecordSerializer.class);

    @Override
    public boolean writeObject(Output out, RecordObjectMeta recordMeta, Object record, String name,
                               IFieldCodecContext context) throws IOException {
        if (!runIfExpr(recordMeta.getIfExpr(), record, name, context))
            return false;

        IBitSet tags = writeTags(out, null, recordMeta, record, context);
        writeTemplateOrFields(out, tags, recordMeta, null, record, context);

        if (recordMeta.getAfterWrite() != null)
            recordMeta.getAfterWrite().call1(null, record, context.getEvalScope());
        return true;
    }

    @Override
    public boolean writeField(Output out, RecordFieldMeta field, Object record, IFieldCodecContext context) throws IOException {
        if (field.isSkipWhenWrite())
            return false;

        if (StringHelper.isEmptyObject(record) && field.isSkipWriteWhenEmpty())
            return false;

        if (!runIfExpr(field.getIfExpr(), record, field.getName(), context))
            return false;

        if (field.getOffset() > 0) {
            writeOffset(out, field.getOffset(), context);
        }

        if (field.getCodec() != null && isUseBodyEncoder(field)) {
            writeObjectWithCodec(out, field, record, context);
        } else {
            if (record instanceof Collection) {
                Collection<?> c = (Collection<?>) record;
                for (Object o : c) {
                    writeSwitch(out, field, o, context);
                }
            } else {
                writeSwitch(out, field, record, context);
            }
        }

        return true;
    }

    protected boolean isUseBodyEncoder(RecordFieldMeta field) {
        return field.getSwitch() != null || field.hasFields();
    }

    protected void writeSwitch(Output out, RecordFieldMeta field, Object record, IFieldCodecContext context) throws IOException {
        if (field.getSwitch() != null) {
            RecordFieldSwitch switchMeta = field.getSwitch();
            String onField = switchMeta.getOnField();
            String onValue = null;
            if (onField != null) {
                onValue = ConvertHelper.toString(getPropByName(record, onField));
            } else if (switchMeta.getOn() != null) {
                onValue = ConvertHelper.toString(switchMeta.getOn().call1(null, record, context.getEvalScope()));
            }

            if (onValue == null)
                throw new NopException(ERR_RECORD_NO_SWITCH_ON_FIELD)
                        .source(field)
                        .param(ARG_FIELD_NAME, field.getName());

            String caseType = switchMeta.getTypeByCaseValue(onValue);
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

            Object value = getProp(field, record, context);
            IBitSet tags = writeTags(out, field, typeMeta, value, context);
            writeTemplateOrFields(out, tags, typeMeta, field.getCharsetObj(), value, context);
            if (typeMeta.getAfterWrite() != null)
                typeMeta.getAfterWrite().call1(null, record, context.getEvalScope());
            return;
        }

        IBitSet tags = writeTags(out, field, null, record, context);
        writeVirtualField(out, tags, field, record, context);
    }

    protected void writeVirtualField(Output out, IBitSet tags, RecordFieldMeta field,
                                     Object record, IFieldCodecContext context) throws IOException {
        if (field.isVirtual()) {
            if (field.getFields() != null) {
                for (RecordFieldMeta subField : field.getFields()) {
                    writeField(out, subField, record, context);
                }
            }
        } else if (field.getFields() != null) {
            Object value = getProp(field, record, context);
            writeTemplateOrFields(out, tags, field, field.getCharsetObj(), value, context);
        } else {
            writeField0(out, field, record, context);
        }
        if (field.getAfterWrite() != null)
            field.getAfterWrite().call1(null, record, context.getEvalScope());
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
        } else {
            for (RecordFieldMeta field : fields.getFields()) {
                if (!field.isMatchTag(tags))
                    continue;
                writeField(out, field, record, context);
            }
        }
    }

    abstract protected IBitSet writeTags(Output out, RecordFieldMeta field, RecordObjectMeta typeMeta,
                                         Object value, IFieldCodecContext context) throws IOException;

    abstract protected void writeObjectWithCodec(Output out, RecordFieldMeta field, Object record,
                                                 IFieldCodecContext context) throws IOException;

    abstract protected void writeOffset(Output out, int offset, IFieldCodecContext context) throws IOException;

    abstract protected void writeString(Output out, String str, Charset charset, IFieldCodecContext context) throws IOException;

    abstract protected void writeField0(Output out, RecordFieldMeta field, Object record, IFieldCodecContext context) throws IOException;

    protected Object getProp(RecordFieldMeta field, Object record, IFieldCodecContext context) {
        if (field.getExportExpr() != null)
            return field.getExportExpr().call1(null, record, context.getEvalScope());

        if (record == null)
            return null;

        String propName = field.getPropOrFieldName();
        return getPropByName(record, propName);
    }

    protected Object getPropByName(Object record, String propName) {
        if (record instanceof IVariableScope)
            return ((IVariableScope) record).getValueByPropPath(propName);

        return BeanTool.getComplexProperty(record, propName);
    }
}

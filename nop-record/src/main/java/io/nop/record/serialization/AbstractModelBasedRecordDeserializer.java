package io.nop.record.serialization;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.IVariableScope;
import io.nop.api.core.util.Symbol;
import io.nop.api.core.validate.IValidationErrorCollector;
import io.nop.commons.collections.bit.IBitSet;
import io.nop.commons.text.SimpleTextTemplate;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.match.IPeekMatchRule;
import io.nop.record.model.FieldRepeatKind;
import io.nop.record.model.IRecordFieldsMeta;
import io.nop.record.model.RecordFieldMeta;
import io.nop.record.model.RecordObjectMeta;
import io.nop.record.model.RecordSimpleFieldMeta;
import io.nop.record.model.RecordTypeMeta;
import io.nop.record.reader.IDataReaderBase;
import io.nop.xlang.xmeta.SimpleSchemaValidator;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;

import static io.nop.record.RecordErrors.ARG_CASE_VALUE;
import static io.nop.record.RecordErrors.ARG_FIELD_NAME;
import static io.nop.record.RecordErrors.ARG_FIELD_PATH;
import static io.nop.record.RecordErrors.ARG_REAL_READ_POS;
import static io.nop.record.RecordErrors.ARG_TYPE_NAME;
import static io.nop.record.RecordErrors.ERR_RECORD_FIELD_IS_MANDATORY;
import static io.nop.record.RecordErrors.ERR_RECORD_NO_MATCH_FOR_CASE_VALUE;
import static io.nop.record.RecordErrors.ERR_RECORD_NO_SWITCH_ON_FIELD;
import static io.nop.record.RecordErrors.ERR_RECORD_TYPE_NO_FIELDS;
import static io.nop.record.RecordErrors.ERR_RECORD_UNKNOWN_OBJ_TYPE;

public abstract class AbstractModelBasedRecordDeserializer<Input extends IDataReaderBase>
        implements IModelBasedRecordDeserializer<Input> {

    @Override
    public boolean readObject(Input in, RecordObjectMeta recordMeta, Object record, IFieldCodecContext context) throws IOException {
        long pos = in.pos();
        if (recordMeta.getBeforeRead() != null)
            recordMeta.getBeforeRead().call3(null, in, record, context, context.getEvalScope());

        String rawString = null;
        int length = getObjectLength(in, recordMeta, record, context);
        if (length > 0) {
            in = (Input) in.subInput(length);

            // 如果rawVarName不为空，则解析对象的时候将原始内容保存到上下文中，抛出异常的时候可以携带这个内容
            // 这个特性对于解析复杂结构出错时进行问题诊断很有用
            if (recordMeta.getRawVarName() != null) {
                rawString = getRawDataString(in, length);
                context.setValue(recordMeta.getRawVarName(), rawString);
            }
        }

        if (rawString == null) {
            _readObject(in, recordMeta, record, context);
        } else {
            try {
                _readObject(in, recordMeta, record, context);
            } catch (NopException e) {
                e.param(recordMeta.getRawVarName(), rawString);
                throw e;
            }
        }
        return pos != in.pos();
    }

    protected void _readObject(Input in, RecordObjectMeta recordMeta, Object record, IFieldCodecContext context)
            throws IOException {
        if (recordMeta.getResolvedBaseType() != null)
            readObject(in, recordMeta.getResolvedBaseType(), record, context);

        IBitSet tags = readTags(in, recordMeta, context);
        readTemplateOrFields(in, tags, recordMeta, null, record, context);

        if (recordMeta.getAfterRead() != null)
            recordMeta.getAfterRead().call3(null, in, record, context, context.getEvalScope());
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
            throw newError(ERR_RECORD_TYPE_NO_FIELDS, in, context).source(fields).param(ARG_TYPE_NAME, fields.getName());
        }
    }

    @Override
    public boolean readField(Input in, RecordFieldMeta field, Object record, IFieldCodecContext context) throws IOException {
        if (field.isSkipWhenRead())
            return false;

        if (field.getOffset() > 0) {
            readOffset(in, field.getOffset(), context);
        }

        context.enterField(field);
        try {
            if (field.getBeforeRead() != null)
                field.getBeforeRead().call3(null, in, record, context, context.getEvalScope());

            if (field.getRepeatKind() != null) {
                if (field.getCodec() != null) {
                    readCollectionWithCodec(in, field, record, context);
                } else {
                    readCollection(in, field, record, context);
                }
            } else {
                readSwitch(in, field, record, context);
            }

            if (field.getAfterRead() != null)
                field.getAfterRead().call3(null, in, record, context, context.getEvalScope());
        } catch (NopException e) {
            fillStdErrorInfo(e, field, in, context);
            throw e;
        } finally {
            context.exitField(field);
        }
        return true;
    }

    protected void fillStdErrorInfo(NopException e, RecordSimpleFieldMeta field, Input in, IFieldCodecContext context) {
        if (e.getErrorLocation() == null)
            e.loc(field.getLocation());

        if (e.getParam(ARG_REAL_READ_POS) == null) {
            e.param(ARG_REAL_READ_POS, in.realPos());
        }
        if (e.getParam(ARG_FIELD_PATH) == null) {
            e.param(ARG_FIELD_PATH, context.getFieldPath());
        }
    }

    protected int getFieldLength(Input in, RecordSimpleFieldMeta field, Object record, IFieldCodecContext context) {
        int length = field.getLength();
        if (field.getLengthExpr() != null) {
            Object lengthValue = field.getLengthExpr().call3(null, in, record, context, context.getEvalScope());

            length = ConvertHelper.toPrimitiveInt(lengthValue,
                    field.getLength(),
                    err -> newError(err, in, context).param(ARG_FIELD_NAME, field.getName()));
        }
        return length;
    }

    protected int getObjectLength(Input in, RecordObjectMeta typeMeta, Object record, IFieldCodecContext context) {
        int length = typeMeta.getLength() == null ? -1: typeMeta.getLength();
        if (typeMeta.getLengthExpr() != null) {
            Object lengthValue = typeMeta.getLengthExpr().call3(null, in, record, context, context.getEvalScope());

            length = ConvertHelper.toPrimitiveInt(lengthValue,
                    typeMeta.getLength(),
                    err -> newError(err, in, context).param(ARG_TYPE_NAME, typeMeta.getName()));
        }
        return length;
    }


    protected void readCollection(Input in, RecordFieldMeta field, Object record, IFieldCodecContext context) throws IOException {
        Collection<Object> coll;
        if (field.getVarName() != null) {
            coll = new ArrayList<>();
            context.setValue(field.getVarName(), coll);
        } else {
            coll = (Collection<Object>) BeanTool.makeComplexProperty(record, field.getPropOrFieldName(), ArrayList::new);
        }

        IEvalFunction repeatUntil = field.getRepeatUntil();
        if (repeatUntil != null) {
            while (!checkUntil(repeatUntil, in, record, context)) {
                Object value = readSwitch(in, field, coll, context);
                coll.add(value);
            }
        } else if (field.getRepeatKind() == FieldRepeatKind.fixed) {
            int length = getFieldLength(in, field, record, context);

            Input subInput = length > 0 ? (Input) in.subInput(length) : in;

            do {
                Object value = readSwitch(subInput, field, coll, context);
                if (value == null)
                    break;
                coll.add(value);
            } while (!in.isEof());
        } else {
            int count = readRepeatCount(in, field, record, context);
            for (int i = 0; i < count; i++) {
                Object value = readSwitch(in, field, coll, context);
                coll.add(value);
            }
        }
    }

    boolean checkUntil(IEvalFunction repeatUntil, Input in, Object record, IFieldCodecContext context) throws IOException {
        return ConvertHelper.toPrimitiveBoolean(
                repeatUntil.call3(null, in, record, context, context.getEvalScope()));
    }

    protected int readRepeatCount(Input in, RecordFieldMeta field, Object record, IFieldCodecContext context) throws IOException {
        Object count;
        if (field.getRepeatCountFieldName() != null) {
            count = getPropByName(record, field.getRepeatCountFieldName());
        } else {
            RecordSimpleFieldMeta sizeField = field.getRepeatCountField();
            if (sizeField != null) {
                count = readField0(in, sizeField, record, context);
            } else {
                IEvalFunction repeatCountExpr = field.getRepeatCountExpr();
                if (repeatCountExpr != null) {
                    count = repeatCountExpr.call3(null, in, record, context, context.getEvalScope());
                } else {
                    throw new IllegalArgumentException("Repeat count field not found:" + field.getName());
                }
            }
        }
        return ConvertHelper.toPrimitiveInt(count, err -> newError(err, in, context).source(field).param(ARG_FIELD_NAME, field.getName()));
    }

    protected Object readSwitch(Input in, RecordFieldMeta field, Object record, IFieldCodecContext context) throws IOException {
        RecordTypeMeta typeMeta = determineObjectType(in, field, record, context);
        if (typeMeta != null) {
            Object obj = makeObject(field, typeMeta, record, context);
            readObject(in, typeMeta, obj, context);
            return obj;
        } else {
            Object value = readField0(in, field, record, context);
            if (field.getVarName() != null)
                context.setValue(field.getVarName(), value);

            validate(value, field, in, context);
            if (!field.isVirtual())
                setPropByName(record, field.getPropOrFieldName(), value);
            return value;
        }
    }

    protected void validate(Object value, RecordSimpleFieldMeta field, Input in, IFieldCodecContext context) {
        if (field.isMandatory() && StringHelper.isEmptyObject(value)) {
            throw newError(ERR_RECORD_FIELD_IS_MANDATORY, in, context)
                    .param(ARG_FIELD_NAME, field.getName());
        }

        if (field.getSchema() != null) {
            SimpleSchemaValidator.INSTANCE.validate(field.getSchema(), field.getLocation(), field.getRecordObjectName(), field.getName(),
                    value, context.getEvalScope(), IValidationErrorCollector.THROW_ERROR);
        }
    }

    protected Object makeObject(RecordFieldMeta field, RecordTypeMeta typeMeta, Object record, IFieldCodecContext context) {
        if (field.getVarName() != null) {
            Object ret = new LinkedHashMap<>();
            context.setValue(field.getVarName(), ret);
            return ret;
        } else if (typeMeta.getVarName() != null) {
            Object ret = new LinkedHashMap<>();
            context.setValue(typeMeta.getVarName(), ret);
            return ret;
        }

        if (field.isVirtual())
            return record;

        if (record instanceof Collection) {
            Object ret = typeMeta.newRecordObject();
            return ret;
        } else {
            return BeanTool.makeComplexProperty(record, field.getPropOrFieldName(), typeMeta::newRecordObject);
        }
    }

    protected RecordTypeMeta determineObjectType(Input in, RecordFieldMeta field, Object record, IFieldCodecContext context) throws IOException {
        if (field.getSwitchOnField() != null || field.getSwitchOnRule() != null || field.getSwitchOnExpr() != null) {
            String onValue;
            if (field.getSwitchOnExpr() != null) {
                onValue = ConvertHelper.toString(field.getSwitchOnExpr().call3(null, in, record, context, context.getEvalScope()));
            } else if (field.getSwitchOnRule() != null) {
                onValue = determineObjectTypeByRule(field.getSwitchOnRule(), in, field, record, context);
            } else {
                onValue = ConvertHelper.toString(getPropByName(record, field.getSwitchOnField()));
            }

            if (onValue == null)
                throw newError(ERR_RECORD_NO_SWITCH_ON_FIELD, in, context)
                        .source(field)
                        .param(ARG_FIELD_NAME, field.getName());

            RecordTypeMeta typeMeta = context.getType(onValue);
            if (typeMeta != null)
                return typeMeta;

            String caseType = field.getTypeByCaseValue(onValue);
            if (caseType == null)
                throw newError(ERR_RECORD_NO_MATCH_FOR_CASE_VALUE, in, context)
                        .source(field)
                        .param(ARG_FIELD_NAME, field.getName())
                        .param(ARG_CASE_VALUE, onValue);

            typeMeta = context.getType(caseType);
            if (typeMeta == null)
                throw newError(ERR_RECORD_NO_MATCH_FOR_CASE_VALUE, in, context)
                        .source(field)
                        .param(ARG_FIELD_NAME, field.getName())
                        .param(ARG_CASE_VALUE, onValue)
                        .param(ARG_TYPE_NAME, caseType);

            return typeMeta;
        }

        if (field.getTypeRef() != null) {
            RecordTypeMeta typeMeta = context.getType(field.getTypeRef());
            if (typeMeta == null)
                throw newError(ERR_RECORD_UNKNOWN_OBJ_TYPE, in, context)
                        .source(field)
                        .param(ARG_FIELD_NAME, field.getName())
                        .param(ARG_TYPE_NAME, field.getTypeRef());

            return typeMeta;
        }

        return null;
    }

    protected NopException newError(ErrorCode errorCode, Input in, IFieldCodecContext context) {
        return new NopException(errorCode)
                .param(ARG_REAL_READ_POS, in.realPos())
                .param(ARG_FIELD_PATH, context.getFieldPath());
    }

    protected Object getPropByName(Object record, String propName) {
        if (record instanceof IVariableScope)
            return ((IVariableScope) record).getValueByPropPath(propName);

        return BeanTool.getComplexProperty(record, propName);
    }

    protected void setPropByName(Object record, String propName, Object value) {
        BeanTool.setProperty(record, propName, value);
    }

    abstract protected String getRawDataString(Input in, int length) throws IOException;

    abstract protected String determineObjectTypeByRule(IPeekMatchRule rule, Input in, RecordFieldMeta field,
                                                        Object record, IFieldCodecContext context) throws IOException;

    abstract protected void readCollectionWithCodec(Input in, RecordFieldMeta field, Object record, IFieldCodecContext context) throws IOException;

    abstract protected IBitSet readTags(Input input, RecordObjectMeta typeMeta, IFieldCodecContext context) throws IOException;

    abstract protected void readOffset(Input in, int offset, IFieldCodecContext context) throws IOException;

    abstract protected void readString(Input in, String str, Charset charset, IFieldCodecContext context) throws IOException;

    abstract protected Object readField0(Input in, RecordSimpleFieldMeta field, Object record, IFieldCodecContext context) throws IOException;
}
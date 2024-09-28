package io.nop.record.resource;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.IVariableScope;
import io.nop.api.core.util.Symbol;
import io.nop.commons.aggregator.CompositeAggregatorProvider;
import io.nop.commons.aggregator.IAggregatorProvider;
import io.nop.commons.text.SimpleTextTemplate;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IBeanVariableScope;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.dataset.record.IRecordOutput;
import io.nop.record.RecordConstants;
import io.nop.record.codec.FieldCodecRegistry;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.codec.impl.DefaultFieldCodecContext;
import io.nop.record.model.IRecordFieldsMeta;
import io.nop.record.model.RecordFieldMeta;
import io.nop.record.model.RecordFieldSwitch;
import io.nop.record.model.RecordFileMeta;
import io.nop.record.model.RecordObjectMeta;
import io.nop.record.model.RecordPaginationMeta;
import io.nop.record.model.RecordTypeMeta;
import io.nop.record.output.IRecordOutputBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Map;

import static io.nop.record.RecordErrors.ARG_CASE_VALUE;
import static io.nop.record.RecordErrors.ARG_FIELD_NAME;
import static io.nop.record.RecordErrors.ARG_TYPE_NAME;
import static io.nop.record.RecordErrors.ERR_RECORD_NO_MATCH_FOR_CASE_VALUE;
import static io.nop.record.RecordErrors.ERR_RECORD_NO_SWITCH_ON_FIELD;

public abstract class AbstractModelBasedRecordOutput<Output extends IRecordOutputBase, T> implements IRecordOutput<T> {
    static final Logger LOG = LoggerFactory.getLogger(AbstractModelBasedRecordOutput.class);

    private final Output baseOut;
    private long writeCount;
    private final RecordFileMeta fileMeta;

    protected final FieldCodecRegistry registry;
    protected final IFieldCodecContext context;

    private final RecordAggregateState aggregateState;

    public AbstractModelBasedRecordOutput(Output out, RecordFileMeta fileMeta,
                                          IFieldCodecContext context, FieldCodecRegistry registry,
                                          IAggregatorProvider aggregatorProvider) {
        this.baseOut = out;
        this.fileMeta = fileMeta;
        this.context = context;
        this.registry = registry;
        this.aggregateState = new RecordAggregateState(fileMeta, aggregatorProvider, context);

        this.context.getEvalScope().setLocalValue(RecordConstants.VAR_AGG_STATE, aggregateState);
    }

    public AbstractModelBasedRecordOutput(Output out, RecordFileMeta fileMeta) {
        this(out, fileMeta, new DefaultFieldCodecContext(), FieldCodecRegistry.DEFAULT, CompositeAggregatorProvider.defaultProvider());
    }


    @Override
    public void flush() {
        try {
            baseOut.flush();
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public void close() {
        try {
            baseOut.close();
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public long getWriteCount() {
        return writeCount;
    }

    @Override
    public void beginWrite(Map<String, Object> attributes) {
        if (fileMeta.getHeader() != null) {
            IBeanVariableScope scope = name -> getScopeValue(attributes, name);
            try {
                writeObject(baseOut, fileMeta.getHeader(), scope, RecordConstants.HEADER_NAME);
            } catch (Exception e) {
                throw NopException.adapt(e);
            }
        }
    }

    private Object getScopeValue(Map<String, Object> vars, String name) {
        if (name.equals(RecordConstants.VAR_WRITE_COUNT))
            return writeCount;
        if (name.equals(RecordConstants.VAR_INDEX_IN_PAGE))
            return aggregateState.getIndexInPage();
        if (vars != null) {
            Object value = vars.get(name);
            if (value != null)
                return value;
        }
        return context.getEvalScope().getValue(name);
    }

    @Override
    public void endWrite(Map<String, Object> trailerMeta) {
        if (fileMeta.getTrailer() != null) {
            IBeanVariableScope scope = name -> getScopeValue(trailerMeta, name);
            try {
                writeObject(baseOut, fileMeta.getTrailer(), scope, RecordConstants.TRAILER_NAME);
            } catch (Exception e) {
                throw NopException.adapt(e);
            }
        }
    }

    @Override
    public void write(T record) {
        writeCount++;
        try {
            beforeWriteRecord(record);
            writeObject(baseOut, fileMeta.getBody(), record, RecordConstants.BODY_NAME);
            afterWriteRecord(record);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    protected void beforeWriteRecord(T record) throws IOException {
        aggregateState.onWriteRecord(record);

        if (fileMeta.getPagination() != null) {
            if (aggregateState.isPageBegin()) {
                RecordPaginationMeta pagination = fileMeta.getPagination();
                if (pagination.getPageHeader() != null) {
                    IBeanVariableScope scope = name -> getScopeValue(null, name);
                    writeObject(baseOut, pagination.getPageHeader(), scope, RecordConstants.PAGE_FOOTER_NAME);
                }
            }
        }
    }

    protected void afterWriteRecord(T record) throws IOException {
        if (fileMeta.getPagination() != null) {
            if (aggregateState.isPageEnd()) {
                RecordPaginationMeta pagination = fileMeta.getPagination();
                if (pagination.getPageFooter() != null) {
                    IBeanVariableScope scope = name -> getScopeValue(aggregateState.getPageResults(), name);
                    writeObject(baseOut, pagination.getPageFooter(), scope, RecordConstants.PAGE_FOOTER_NAME);
                }
                aggregateState.resetPage();
            }
        }
    }

    public void writeObject(Output out, RecordObjectMeta recordMeta, Object record, String name) throws IOException {
        if (!runIfExpr(recordMeta.getIfExpr(), record, name))
            return;

        writeTemplateOrFields(out, recordMeta, null, record);

        if (recordMeta.getAfterWrite() != null)
            recordMeta.getAfterWrite().call1(null, record, context.getEvalScope());
    }

    protected void writeField(Output out, RecordFieldMeta field, Object record) throws IOException {
        if (field.isSkipWhenWrite())
            return;

        if (StringHelper.isEmptyObject(record) && field.isSkipWriteWhenEmpty())
            return;

        if (!runIfExpr(field.getIfExpr(), record, field.getName()))
            return;

        if (field.getOffset() > 0) {
            writeOffset(out, field.getOffset());
        }

        if (field.getCodec() != null && isUseBodyEncoder(field)) {
            writeObjectWithCodec(out, field, record);
        } else {
            if (record instanceof Collection) {
                Collection<?> c = (Collection<?>) record;
                for (Object o : c) {
                    writeSwitch(out, field, o);
                }
            } else {
                writeSwitch(out, field, record);
            }
        }
    }

    protected boolean isUseBodyEncoder(RecordFieldMeta field) {
        return field.getSwitch() != null || field.hasFields();
    }

    boolean runIfExpr(IEvalFunction expr, Object record, String name) {
        if (expr == null)
            return true;
        if (!ConvertHelper.toPrimitiveBoolean(expr.call1(null, record, context.getEvalScope()))) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("nop.record.skip-field:fieldPath={},field={}", context.getFieldPath(), name);
            }
            return false;
        }
        return true;
    }

    protected void writeSwitch(Output out, RecordFieldMeta field, Object record) throws IOException {
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

            RecordTypeMeta typeMeta = fileMeta.getType(caseType);
            if (typeMeta == null)
                throw new NopException(ERR_RECORD_NO_MATCH_FOR_CASE_VALUE)
                        .source(field)
                        .param(ARG_FIELD_NAME, field.getName())
                        .param(ARG_CASE_VALUE, onValue)
                        .param(ARG_TYPE_NAME, caseType);

            Object value = getProp(field, record);
            writeTemplateOrFields(out, typeMeta, field.getCharsetObj(), value);
            if (typeMeta.getAfterWrite() != null)
                typeMeta.getAfterWrite().call1(null, record, context.getEvalScope());
            return;
        }

        writeVirtualField(out, field, record);
    }

    protected void writeVirtualField(Output out, RecordFieldMeta field, Object record) throws IOException {
        if (field.isVirtual()) {
            if (field.getFields() != null) {
                for (RecordFieldMeta subField : field.getFields()) {
                    writeField(out, subField, record);
                }
            }
        } else if (field.getFields() != null) {
            Object value = getProp(field, record);
            writeTemplateOrFields(out, field, field.getCharsetObj(), value);
        } else {
            writeField0(out, field, record);
        }
        if (field.getAfterWrite() != null)
            field.getAfterWrite().call1(null, record, context.getEvalScope());
    }

    protected void writeTemplateOrFields(Output out, IRecordFieldsMeta fields, Charset charset, Object record) throws IOException {
        SimpleTextTemplate template = fields.getNormalizedTemplate();
        if (template != null) {
            for (Object part : template.getParts()) {
                if (part instanceof Symbol) {
                    String name = ((Symbol) part).getText();
                    RecordFieldMeta field = fields.requireField(name);
                    writeField(out, field, record);
                } else {
                    writeString(out, part.toString(), charset);
                }
            }
        } else {
            for (RecordFieldMeta field : fields.getFields()) {
                writeField(out, field, record);
            }
        }
    }

    abstract protected void writeObjectWithCodec(Output out, RecordFieldMeta field, Object record) throws IOException;

    abstract protected void writeOffset(Output out, int offset) throws IOException;

    abstract protected void writeString(Output out, String str, Charset charset) throws IOException;

    abstract protected void writeField0(Output out, RecordFieldMeta field, Object record) throws IOException;

    protected Object getProp(RecordFieldMeta field, Object record) {
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

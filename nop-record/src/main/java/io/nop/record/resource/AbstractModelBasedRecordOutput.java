package io.nop.record.resource;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Symbol;
import io.nop.commons.text.SimpleTextTemplate;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.dataset.record.IRecordOutput;
import io.nop.record.RecordConstants;
import io.nop.record.codec.FieldCodecRegistry;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.codec.impl.DefaultFieldCodecContext;
import io.nop.record.model.IRecordFieldsMeta;
import io.nop.record.model.RecordFieldMeta;
import io.nop.record.model.RecordFileMeta;
import io.nop.record.model.RecordObjectMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

public abstract class AbstractModelBasedRecordOutput<T> implements IRecordOutput<T> {
    static final Logger LOG = LoggerFactory.getLogger(AbstractModelBasedRecordOutput.class);

    private long writeCount;
    private final RecordFileMeta fileMeta;

    protected final FieldCodecRegistry registry;
    protected final IFieldCodecContext context;

    public AbstractModelBasedRecordOutput(RecordFileMeta fileMeta,
                                          IFieldCodecContext context, FieldCodecRegistry registry) {
        this.fileMeta = fileMeta;
        this.context = context;
        this.registry = registry;
    }

    public AbstractModelBasedRecordOutput(RecordFileMeta fileMeta) {
        this(fileMeta, new DefaultFieldCodecContext(), FieldCodecRegistry.DEFAULT);
    }

    @Override
    public long getWriteCount() {
        return writeCount;
    }

    @Override
    public void beginWrite(Map<String, Object> attributes) {
        if (fileMeta.getHeader() != null) {
            try {
                writeObject(fileMeta.getHeader(), attributes, RecordConstants.HEADER_NAME);
            } catch (Exception e) {
                throw NopException.adapt(e);
            }
        }
    }

    @Override
    public void endWrite(Map<String, Object> trailerMeta) {
        if (fileMeta.getTrailer() != null) {
            try {
                writeObject(fileMeta.getTrailer(), trailerMeta, RecordConstants.TRAILER_NAME);
            } catch (Exception e) {
                throw NopException.adapt(e);
            }
        }
    }

    @Override
    public void write(T record) {
        writeCount++;
        try {
            writeObject(fileMeta.getBody(), record, RecordConstants.BODY_NAME);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    public void writeObject(RecordObjectMeta recordMeta, Object record, String name) throws IOException {
        if (!runIfExpr(recordMeta.getIfExpr(), record, name))
            return;

        writeTemplateOrFields(recordMeta, record);

        if (recordMeta.getAfterWrite() != null)
            recordMeta.getAfterWrite().call1(null, record, context.getEvalScope());
    }

    protected void writeField(RecordFieldMeta field, Object record) throws IOException {
        if (field.isSkipWhenWrite())
            return;

        if (StringHelper.isEmptyObject(record) && field.isSkipWriteWhenEmpty())
            return;

        if (!runIfExpr(field.getIfExpr(), record, field.getName()))
            return;

        if (field.getOffset() > 0) {
            writeOffset(field.getOffset());
        }

        if (record instanceof Collection) {
            Collection<?> c = (Collection<?>) record;
            for (Object o : c) {
                writeVirtualField(field, o);
            }
        } else {
            writeVirtualField(field, record);
        }
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

    protected void writeVirtualField(RecordFieldMeta field, Object record) throws IOException {
        if (field.isVirtual()) {
            if (field.getFields() != null) {
                for (RecordFieldMeta subField : field.getFields()) {
                    writeField(subField, record);
                }
            }
        } else if (field.getFields() != null) {
            Object value = getProp(field, record);
            writeTemplateOrFields(field, value);
        } else {
            writeField0(field, record);
        }
        if (field.getAfterWrite() != null)
            field.getAfterWrite().call1(null, record, context.getEvalScope());
    }

    protected void writeTemplateOrFields(IRecordFieldsMeta fields, Object record) throws IOException {
        SimpleTextTemplate template = fields.getNormalizedTemplate();
        if (template != null) {
            for (Object part : template.getParts()) {
                if (part instanceof Symbol) {
                    String name = ((Symbol) part).getText();
                    RecordFieldMeta field = fields.requireField(name);
                    writeField(field, record);
                } else {
                    writeString(part.toString());
                }
            }
        } else {
            for (RecordFieldMeta field : fields.getFields()) {
                writeField(field, record);
            }
        }
    }

    abstract protected void writeOffset(int offset) throws IOException;

    abstract protected void writeString(String str) throws IOException;

    abstract protected void writeField0(RecordFieldMeta field, Object record) throws IOException;

    protected Object getProp(RecordFieldMeta field, Object record) {
        if (field.getExportExpr() != null)
            return field.getExportExpr().call1(null, record, context.getEvalScope());

        if (record == null)
            return null;

        String propName = field.getPropOrFieldName();
        return BeanTool.getComplexProperty(record, propName);
    }
}

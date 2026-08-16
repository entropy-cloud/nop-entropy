package io.nop.record.util;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.bytes.ByteString;
import io.nop.record.RecordErrors;
import io.nop.record.model.RecordSimpleFieldMeta;
import io.nop.xlang.xmeta.impl.SchemaImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestRecordMetaHelper {

    private RecordSimpleFieldMeta field(int minLength, int maxLength) {
        RecordSimpleFieldMeta field = new RecordSimpleFieldMeta();
        field.setName("f1");
        SchemaImpl schema = new SchemaImpl();
        if (minLength > 0)
            schema.setMinLength(minLength);
        if (maxLength > 0)
            schema.setMaxLength(maxLength);
        field.setSchema(schema);
        return field;
    }

    @Test
    public void testPadTextRejectsShortValue() {
        RecordSimpleFieldMeta field = field(3, 0);
        NopException e = assertThrows(NopException.class, () -> RecordMetaHelper.padText("ab", field));
        assertEquals(RecordErrors.ERR_RECORD_FIELD_LENGTH_LESS_THAN_MIN_VALUE.getErrorCode(), e.getErrorCode());
        assertNotNull(e.getParam(RecordErrors.ARG_MIN_VALUE));
    }

    @Test
    public void testPadBinaryRejectsShortValue() {
        RecordSimpleFieldMeta field = field(3, 0);
        NopException e = assertThrows(NopException.class,
                () -> RecordMetaHelper.padBinary(ByteString.of(new byte[]{'a', 'b'}), field));
        assertEquals(RecordErrors.ERR_RECORD_FIELD_LENGTH_LESS_THAN_MIN_VALUE.getErrorCode(), e.getErrorCode());
        assertNotNull(e.getParam(RecordErrors.ARG_MIN_VALUE));
    }

    @Test
    public void testPadTextRejectsLongValue() {
        RecordSimpleFieldMeta field = field(0, 3);
        NopException e = assertThrows(NopException.class, () -> RecordMetaHelper.padText("12345", field));
        assertEquals(RecordErrors.ERR_RECORD_FIELD_LENGTH_GREATER_THAN_MAX_VALUE.getErrorCode(), e.getErrorCode());
        assertNotNull(e.getParam(RecordErrors.ARG_MAX_VALUE));
    }

    @Test
    public void testPadBinaryRejectsLongValue() {
        RecordSimpleFieldMeta field = field(0, 3);
        NopException e = assertThrows(NopException.class,
                () -> RecordMetaHelper.padBinary(ByteString.of(new byte[]{'1', '2', '3', '4', '5'}), field));
        assertEquals(RecordErrors.ERR_RECORD_FIELD_LENGTH_GREATER_THAN_MAX_VALUE.getErrorCode(), e.getErrorCode());
        assertNotNull(e.getParam(RecordErrors.ARG_MAX_VALUE));
    }
}

package io.nop.stream.cep;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class TestNopCepErrorsMessagesEnglish {

    private static final Pattern CHINESE_PATTERN = Pattern.compile("[\\u4e00-\\u9fff]");

    @Test
    void testNopCepErrorsNoChineseMessages() {
        int checkedCount = 0;
        for (Field field : NopCepErrors.class.getDeclaredFields()) {
            if (field.getType().getSimpleName().equals("ErrorCode")) {
                try {
                    Object errorCodeObj = field.get(null);
                    String message = ((io.nop.api.core.exceptions.ErrorCode) errorCodeObj).getDescription();
                    assertFalse(CHINESE_PATTERN.matcher(message).find(),
                            field.getName() + " message contains Chinese: " + message);
                    checkedCount++;
                } catch (IllegalAccessException e) {
                    fail("Failed to access field " + field.getName());
                }
            }
        }
        assertTrue(checkedCount > 0, "Should have checked at least one ErrorCode");
    }
}

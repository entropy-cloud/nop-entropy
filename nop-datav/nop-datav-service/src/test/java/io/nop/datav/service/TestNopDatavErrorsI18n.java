package io.nop.datav.service;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.core.i18n.I18nMessageManager;
import io.nop.datav.service.entity.AbstractNopDatavTest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-07（plan 2026-08-15-2146-3 Phase 4）错误码语言契约回归锚点。
 *
 * <p>锚定三点契约：</p>
 * <ul>
 *   <li>全部 ErrorCode define() 默认描述为中文（error-handling.md 契约——nop-datav 全部为
 *       业务错误码，无转换类英文例外）；</li>
 *   <li>en locale 下每个错误码均可经 {@link I18nMessageManager} 解析出非空英文消息
 *       （`_vfs/i18n/en/nop-datav-errors.i18n.yaml` 键齐备——define() 改中文后 en 键是
 *       load-bearing，缺失将使 en 用户看到中文）；</li>
 *   <li>en 消息与 define() 默认描述的 {@code {param}} 占位符集合一致 + 错误码字符串唯一
 *       （wire 兼容：键与 ARG 集合零变更）。</li>
 * </ul>
 */
public class TestNopDatavErrorsI18n extends AbstractNopDatavTest {

    private static final Pattern PARAM_PATTERN = Pattern.compile("\\{(\\w+)\\}");

    private static Set<Field> errorCodeFields() {
        Set<Field> fields = new HashSet<>();
        for (Field f : NopDatavErrors.class.getFields()) {
            if (f.getType() == ErrorCode.class && Modifier.isStatic(f.getModifiers())) {
                fields.add(f);
            }
        }
        assertFalse(fields.isEmpty(), "NopDatavErrors must define ErrorCodes");
        return fields;
    }

    private static Set<String> placeholders(String text) {
        Set<String> names = new HashSet<>();
        Matcher m = PARAM_PATTERN.matcher(text);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    /** define() 默认描述必须为中文（含非 ASCII 字符）——P1-07 主体修复的回归锚点。 */
    @Test
    public void testAllDefaultDescriptionsAreChinese() throws IllegalAccessException {
        for (Field f : errorCodeFields()) {
            ErrorCode code = (ErrorCode) f.get(null);
            String desc = code.getDescription();
            assertNotNull(desc, f.getName() + " has null description");
            assertTrue(desc.chars().anyMatch(c -> c > 127),
                    f.getName() + " description must be Chinese (non-ASCII), got: " + desc);
        }
    }

    /** en locale 全键可解析 + 占位符集合与 define() 一致 + 错误码字符串唯一（wire 兼容）。 */
    @Test
    public void testEnI18nKeysResolveWithMatchingPlaceholders() throws IllegalAccessException {
        Set<String> seenCodes = new HashSet<>();
        for (Field f : errorCodeFields()) {
            ErrorCode code = (ErrorCode) f.get(null);
            assertTrue(seenCodes.add(code.getErrorCode()),
                    "duplicate error code string: " + code.getErrorCode());

            String en = I18nMessageManager.instance().getMessage("en", code.getErrorCode(), null);
            assertNotNull(en, f.getName() + " missing en i18n key: " + code.getErrorCode());
            assertFalse(en.isBlank(), f.getName() + " empty en message");
            assertEquals(placeholders(code.getDescription()), placeholders(en),
                    f.getName() + " en message placeholders must match define(): " + en);
        }
    }
}

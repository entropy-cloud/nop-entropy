package io.nop.stream.runtime.launch;

import io.nop.api.core.exceptions.NopException;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * plan 2585 Phase 2 钉：poll-batch-size 配置解析 fail-fast——非法取值（非整数 / &le;0）
 * 在装配期抛类型化 {@link StreamException}（ERR_STREAM_INVALID_ARG），不再裸抛
 * NumberFormatException（rule 24 显式失败，非静默回退缺省值）。
 */
public class TestPollBatchSizeConfigFailFast {
    private static final String KEY = PollingJdbcMessageService.POLL_BATCH_SIZE_KEY;
    private String previous;

    @AfterEach
    public void restoreProperty() {
        if (previous == null) {
            System.clearProperty(KEY);
        } else {
            System.setProperty(KEY, previous);
        }
    }

    private void setProperty(String value) {
        previous = System.getProperty(KEY);
        System.setProperty(KEY, value);
    }

    @Test
    public void nonIntegerValueFailsFastWithTypedError() {
        setProperty("not-a-number");
        StreamException ex = assertThrows(StreamException.class,
                () -> new PollingJdbcMessageService(null, 1000L));
        assertEquals(NopStreamErrors.ERR_STREAM_INVALID_ARG.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void nonPositiveValueFailsFastWithTypedError() {
        setProperty("0");
        StreamException ex = assertThrows(StreamException.class,
                () -> new PollingJdbcMessageService(null, 1000L));
        assertEquals(NopStreamErrors.ERR_STREAM_INVALID_ARG.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void validValueIsAccepted() {
        setProperty("123");
        PollingJdbcMessageService service = new PollingJdbcMessageService(null, 1000L);
        service.close();
    }
}

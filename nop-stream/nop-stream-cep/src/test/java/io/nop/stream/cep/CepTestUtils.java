package io.nop.stream.cep;

import io.nop.api.core.exceptions.NopException;
import io.nop.stream.core.operators.ProcessingTimeService;
import io.nop.stream.core.operators.AbstractStreamOperator;

import java.lang.reflect.Field;

public final class CepTestUtils {

    private CepTestUtils() {}

    public static void injectProcessingTimeService(AbstractStreamOperator<?> op, ProcessingTimeService svc) {
        try {
            Field f = AbstractStreamOperator.class.getDeclaredField("processingTimeService");
            f.setAccessible(true);
            f.set(op, svc);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }
}

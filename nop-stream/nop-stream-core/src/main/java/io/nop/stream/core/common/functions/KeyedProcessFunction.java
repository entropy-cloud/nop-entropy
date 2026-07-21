package io.nop.stream.core.common.functions;

import io.nop.stream.core.time.TimerService;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.util.OutputTag;

public abstract class KeyedProcessFunction<K, IN, OUT> extends ProcessFunction<IN, OUT> {

    public abstract class Context extends ProcessFunction<IN, OUT>.Context {
        public abstract K getCurrentKey();
    }

    public abstract class OnTimerContext extends ProcessFunction<IN, OUT>.OnTimerContext {
        public abstract K getCurrentKey();
    }
}

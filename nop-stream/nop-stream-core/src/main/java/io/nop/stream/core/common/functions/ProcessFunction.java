package io.nop.stream.core.common.functions;

import io.nop.stream.core.time.TimerService;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.util.OutputTag;

public abstract class ProcessFunction<IN, OUT> extends AbstractRichFunction {

    public abstract void processElement(IN value, Context ctx, Collector<OUT> out) throws Exception;

    public void onTimer(long timestamp, OnTimerContext ctx, Collector<OUT> out) throws Exception {
    }

    public abstract class Context {
        public abstract Long timestamp();

        public abstract TimerService timerService();

        public abstract <X> void output(OutputTag<X> outputTag, X value);
    }

    public abstract class OnTimerContext extends Context {
        @Override
        public abstract Long timestamp();
    }
}

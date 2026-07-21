package io.nop.stream.core.operators;

import io.nop.stream.core.common.functions.ProcessFunction;
import io.nop.stream.core.common.functions.RichFunction;
import io.nop.stream.core.common.state.VoidNamespace;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.time.TimerService;
import io.nop.stream.core.util.OutputTag;

public class ProcessOperator<IN, OUT> extends AbstractUdfStreamOperator<OUT, ProcessFunction<IN, OUT>>
        implements OneInputStreamOperator<IN, OUT>, Triggerable<Object, VoidNamespace> {

    private transient TimestampedCollector<OUT> collector;
    private transient HeapInternalTimerService<VoidNamespace> internalTimerService;
    private transient TimerService userTimerService;
    private transient ContextImpl context;
    private transient OnTimerContextImpl onTimerContext;

    public ProcessOperator(ProcessFunction<IN, OUT> userFunction) {
        super(userFunction);
    }

    @Override
    public void open() throws Exception {
        super.open();

        collector = new TimestampedCollector<>(output);

        internalTimerService = new HeapInternalTimerService<>(
                (Triggerable<Object, VoidNamespace>) this,
                () -> getCurrentKey());

        if (timeServiceManager != null) {
            timeServiceManager.registerTimerService(internalTimerService);
        }

        userTimerService = new InternalTimerServiceTimerWrapper(internalTimerService);

        context = new ContextImpl();
        onTimerContext = new OnTimerContextImpl();

        if (userFunction instanceof RichFunction) {
            RichFunction richFunction = (RichFunction) userFunction;
            io.nop.stream.core.common.functions.RuntimeContext runtimeCtx = richFunction.getRuntimeContext();
            if (runtimeCtx instanceof StreamingRuntimeContext) {
                StreamingRuntimeContext src = (StreamingRuntimeContext) runtimeCtx;
                if (keyedStateBackend != null) {
                    src.setKeyedStateStore(keyedStateBackend);
                }
                src.setTimerService(userTimerService);
            }
        }
    }

    @Override
    public void processElement(StreamRecord<IN> element) throws Exception {
        collector.setTimestamp(element);
        context.element = element;
        userFunction.processElement(element.getValue(), context, collector);
        context.element = null;
    }

    @Override
    public void onEventTime(InternalTimer<Object, VoidNamespace> timer) throws Exception {
        setCurrentKey(timer.getKey());
        onTimerContext.timerTimestamp = timer.getTimestamp();
        userFunction.onTimer(timer.getTimestamp(), onTimerContext, collector);
    }

    @Override
    public void onProcessingTime(InternalTimer<Object, VoidNamespace> timer) throws Exception {
        setCurrentKey(timer.getKey());
        onTimerContext.timerTimestamp = timer.getTimestamp();
        userFunction.onTimer(timer.getTimestamp(), onTimerContext, collector);
    }

    private class ContextImpl extends ProcessFunction<IN, OUT>.Context {
        private StreamRecord<IN> element;

        ContextImpl() {
            userFunction.super();
        }

        @Override
        public Long timestamp() {
            if (element != null && element.hasTimestamp()) {
                return element.getTimestamp();
            }
            return null;
        }

        @Override
        public TimerService timerService() {
            return userTimerService;
        }

        @Override
        public <X> void output(OutputTag<X> outputTag, X value) {
            output.collect(outputTag, new StreamRecord<>(value));
        }
    }

    private class OnTimerContextImpl extends ProcessFunction<IN, OUT>.OnTimerContext {
        private long timerTimestamp;

        OnTimerContextImpl() {
            userFunction.super();
        }

        @Override
        public Long timestamp() {
            return timerTimestamp;
        }

        @Override
        public TimerService timerService() {
            return userTimerService;
        }

        @Override
        public <X> void output(OutputTag<X> outputTag, X value) {
            output.collect(outputTag, new StreamRecord<>(value));
        }
    }

    private static class InternalTimerServiceTimerWrapper implements TimerService {

        private final HeapInternalTimerService<VoidNamespace> internalTimerService;

        InternalTimerServiceTimerWrapper(HeapInternalTimerService<VoidNamespace> internalTimerService) {
            this.internalTimerService = internalTimerService;
        }

        @Override
        public long currentProcessingTime() {
            return internalTimerService.currentProcessingTime();
        }

        @Override
        public long currentWatermark() {
            return internalTimerService.currentWatermark();
        }

        @Override
        public void registerProcessingTimeTimer(long time) {
            internalTimerService.registerProcessingTimeTimer(VoidNamespace.INSTANCE, time);
        }

        @Override
        public void registerEventTimeTimer(long time) {
            internalTimerService.registerEventTimeTimer(VoidNamespace.INSTANCE, time);
        }

        @Override
        public void deleteProcessingTimeTimer(long time) {
            internalTimerService.deleteProcessingTimeTimer(VoidNamespace.INSTANCE, time);
        }

        @Override
        public void deleteEventTimeTimer(long time) {
            internalTimerService.deleteEventTimeTimer(VoidNamespace.INSTANCE, time);
        }
    }
}

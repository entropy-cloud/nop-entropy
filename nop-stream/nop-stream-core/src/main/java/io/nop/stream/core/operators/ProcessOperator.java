package io.nop.stream.core.operators;

import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.StateSnapshotContext;
import io.nop.stream.core.common.functions.ProcessFunction;
import io.nop.stream.core.common.functions.RichFunction;
import io.nop.stream.core.common.state.VoidNamespace;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.time.TimerService;
import io.nop.stream.core.util.OutputTag;

public class ProcessOperator<IN, OUT> extends AbstractUdfStreamOperator<OUT, ProcessFunction<IN, OUT>>
        implements OneInputStreamOperator<IN, OUT>, Triggerable<Object, VoidNamespace> {

    private transient TimestampedCollector<OUT> collector;
    private transient HeapInternalTimerService<Object, VoidNamespace> internalTimerService;
    private transient TimerService userTimerService;
    private transient ContextImpl context;
    private transient OnTimerContextImpl onTimerContext;

    /**
     * R5 ST-11: timer snapshot captured by {@link #restoreState} (which runs
     * before {@link #open()} creates the timer service — same lifecycle
     * constraint as WindowOperator). Applied deferredly in {@link #open()}
     * after the keyed backend block so the timer service exists and
     * {@code setKeyType} has the restored effective key class.
     */
    private transient HeapInternalTimerService.TimerSnapshot<Object, VoidNamespace> restoredTimerSnapshot;

    public ProcessOperator(ProcessFunction<IN, OUT> userFunction) {
        super(userFunction);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Process operator: the user process function is shared across subtasks
     * (it may capture external state). All transient state (collector, timer
     * service, contexts) is left null and re-initialized by {@link #open()}.
     */
    @Override
    public ProcessOperator<IN, OUT> copyForSubtask() {
        return new ProcessOperator<>(userFunction);
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

        // Keyed-stream support: a ProcessOperator created through
        // KeyedStream.process(KeyedProcessFunction) must provision a keyed state
        // backend from the configured state backend (same pattern as
        // WindowOperator/CepOperator), otherwise every KeyedProcessFunction UDF
        // fails on RuntimeContext.getKeyedStateStore() ("Keyed state is only
        // available on a keyed stream") and deferred restore-before-open would
        // be skipped. Key type Object.class mirrors the CepOperator precedent —
        // key classes are JSON-serializable values (e.g. String keys).
        if (keyedStateBackend == null && stateBackend != null) {
            keyedStateBackend = stateBackend.createKeyedStateBackend(Object.class);
            applyPendingRestoreState();
        }

        // R5 ST-11: re-materialize restored timer keys with the real key class
        // (the restored snapshot header feeds the backend's effective key type;
        // without setKeyType the rematerialization degrades to identity for
        // Object-declared backends).
        if (keyedStateBackend instanceof io.nop.stream.core.common.state.backend.memory.MemoryKeyedStateBackend) {
            internalTimerService.setKeyType(
                    ((io.nop.stream.core.common.state.backend.memory.MemoryKeyedStateBackend<?>) keyedStateBackend)
                            .getSnapshotKeyType());
        }
        if (restoredTimerSnapshot != null) {
            internalTimerService.restoreTimers(restoredTimerSnapshot);
            restoredTimerSnapshot = null;
        }

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
    public OperatorSnapshotResult snapshotState(StateSnapshotContext context) throws Exception {
        OperatorSnapshotResult result = super.snapshotState(context);
        // R5 ST-11: KeyedProcessFunction timers are REQUIRED state — without
        // this the timers silently vanish across checkpoint/savepoint (all
        // snapshotTimers/restoreTimers consumers were window/CEP only).
        // Timer service null = pre-open lifecycle, nothing registered yet
        // (same guard as WindowOperator).
        if (internalTimerService != null) {
            result.putOperatorState(TimerStateKeys.PROCESS_INTERNAL_TIMERS,
                    internalTimerService.snapshotTimers());
        }
        return result;
    }

    @Override
    public void restoreState(OperatorSnapshotResult snapshotResult) throws Exception {
        super.restoreState(snapshotResult);
        // R5 ST-11: capture only — open() has not created the timer service
        // yet (restore-before-open), application is deferred (WindowOperator
        // discipline).
        if (snapshotResult != null) {
            Object restored = snapshotResult.getOperatorState(TimerStateKeys.PROCESS_INTERNAL_TIMERS);
            if (restored instanceof HeapInternalTimerService.TimerSnapshot) {
                @SuppressWarnings("unchecked")
                HeapInternalTimerService.TimerSnapshot<Object, VoidNamespace> snapshot =
                        (HeapInternalTimerService.TimerSnapshot<Object, VoidNamespace>) restored;
                this.restoredTimerSnapshot = snapshot;
            }
        }
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

        private final HeapInternalTimerService<Object, VoidNamespace> internalTimerService;

        InternalTimerServiceTimerWrapper(HeapInternalTimerService<Object, VoidNamespace> internalTimerService) {
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

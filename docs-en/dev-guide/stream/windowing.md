# Window Functions

## WindowOperator

1. First assign a set of windows to the element
2. For each window, check whether the trigger fires and modify the timer state
3. When a timer fires, likewise check whether the trigger fires

```javascript
void processElement(StreamRecord<IN> element){
   const elementWindows = windowAssigner.assignWindows(
                        element.getValue(), element.getTimestamp(), windowAssignerContext);
   for (const window of elementWindows) {
        TriggerResult triggerResult = triggerContext.onElement(element);

        if (triggerResult.isFire()) {
            ACC contents = windowState.get();
            if (contents != null) {
                emitWindowContents(actualWindow, contents);
            }
        }

        if (triggerResult.isPurge()) {
            windowState.clear();
        }
        registerCleanupTimer(actualWindow);
   }
}

void onEventTime(InternalTimer<K, W> timer) {
    triggerContext.key = timer.getKey();
    triggerContext.window = timer.getNamespace();

    MergingWindowSet<W> mergingWindows;

    if (windowAssigner instanceof MergingWindowAssigner) {
        mergingWindows = getMergingWindowSet();
        W stateWindow = mergingWindows.getStateWindow(triggerContext.window);
        if (stateWindow == null) {
            // Timer firing for non-existent window, this can only happen if a
            // trigger did not clean up timers. We have already cleared the merging
            // window and therefore the Trigger state, however, so nothing to do.
            return;
        } else {
            windowState.setCurrentNamespace(stateWindow);
        }
    } else {
        windowState.setCurrentNamespace(triggerContext.window);
        mergingWindows = null;
    }

    TriggerResult triggerResult = triggerContext.onEventTime(timer.getTimestamp());

    if (triggerResult.isFire()) {
        ACC contents = windowState.get();
        if (contents != null) {
            emitWindowContents(triggerContext.window, contents);
        }
    }

    if (triggerResult.isPurge()) {
        windowState.clear();
    }

    if (windowAssigner.isEventTime()
            && isCleanupTime(triggerContext.window, timer.getTimestamp())) {
        clearAllState(triggerContext.window, windowState, mergingWindows);
    }

    if (mergingWindows != null) {
        // need to make sure to update the merging state in state
        mergingWindows.persist();
    }
}
```

The only difference between EvictorWindowOperator and WindowOperator is whether, during emit, the evictor is invoked to remove elements from the window.

## Pseudo-code Execution Logic of WindowOperator

```
class WindowOperator[IN, OUT, KEY, W <: Window]:
    # State management: the element collection for each window
    window_state: KeyedStateStore[KEY, W, List[IN]]
    window_assigner: WindowAssigner[IN, W]  # Window assigner (e.g., tumbling, sliding windows)
    trigger: Trigger[IN, W]                 # Trigger (decides when to fire computation)
    window_function: ProcessWindowFunction[IN, OUT, KEY, W]  # Window processing function

    def process_element(element: StreamRecord[IN], current_watermark: long):
        # 1. Assign windows (possibly multiple, e.g., sliding windows)
        windows = window_assigner.assign_windows(element.value, element.timestamp)

        for window in windows:
            # 2. Add the element to the window state
            key = get_key(element)  # Extract the key from the KeyedStream
            state = window_state.get_state(key, window)
            state.add(element.value)

            # 3. Invoke the trigger and decide whether to compute immediately
            trigger_result = trigger.on_element(element.value, element.timestamp, window, current_watermark)
            if trigger_result == FIRE:
                compute_and_emit(key, window, current_watermark)
            elif trigger_result == PURGE:
                window_state.clear(key, window)

    def on_watermark(watermark: Watermark):
        # 4. When a watermark arrives, compute expired windows
        for window in window_state.get_all_windows():
            if window.end <= watermark.timestamp:
                for key in window_state.get_keys(window):
                    compute_and_emit(key, window, watermark.timestamp)
                window_state.clear_all(window)

    def compute_and_emit(key: KEY, window: W, watermark: long):
        # 5. Execute the window function and emit the result
        elements = window_state.get_state(key, window)
        context = WindowContext(key, window, elements, watermark)
        output = window_function.process(context)
        emit(output)

```

Processing Logic of Flink CEP

```
class PatternOperator[IN, OUT]:
    # Initialization parameters
    def __init__(self, pattern: Pattern[IN, OUT], timeout_handling: bool):
        self.nfa = NFACompiler.compile(pattern)  # Compile into an NFA state machine
        self.pending_matches = {}  # Temporarily store incomplete match sequences
        self.timers = TimerService()  # Timeout management service

    # Core logic for processing each element
    def process_element(event: IN, timestamp: long):
        # 1. Update event time
        self.timers.advance_watermark(timestamp)

        # 2. Iterate over all possible NFA state transitions
        for (key, partial_match) in self.pending_matches:
            possible_states = self.nfa.get_possible_states(partial_match)

            for state in possible_states:
                # Check condition filters
                if not self.check_condition(state, event):
                    continue

                # 3. Create a new match sequence
                new_match = partial_match.clone().add_event(event)

                # 4. Evaluate state transitions
                if state.is_final():
                    # Produce a complete match
                    output = self.create_output(new_match)
                    emit_result(output)

                    if not state.is_looping():
                        self.pending_matches.remove(key)
                else:
                    # Update intermediate state
                    self.pending_matches[key] = new_match

                # 5. Register a timeout timer
                if state.get_window_time() is not None:
                    timeout = timestamp + state.get_window_time()
                    self.timers.register_event_time_timer(timeout)

    # Timer firing handling (timeout logic)
    def on_timer(timestamp: long):
        for (key, partial_match) in self.pending_matches:
            for state in partial_match.get_current_states():
                # Check timeout conditions
                if state.get_window_time() + partial_match.get_start_time() < timestamp:
                    # Handle timed-out matches
                    if state.is_optional():
                        self.pending_matches.remove(key)
                    else:
                        output = self.handle_timeout(partial_match)
                        emit_result(output)
                        self.pending_matches.remove(key)

    # Special operations supported by CEP
    def handle_pattern_ops():
        # Quantifier handling (e.g., oneOrMore, times(3))
        for match in self.pending_matches.values():
            if match.current_state.quantifier == Quantifier.ONE_OR_MORE:
                self.handle_one_or_more(match)

        # Adjacent event handling (e.g., next, followedBy)
        self.check_adjacent_relations()

        # Until-condition handling
        self.check_until_conditions()

    # Interaction with the state backend
    def snapshot_state(checkpoint_id: long):
        save_checkpoint(
            nfa_state = self.nfa.serialize(),
            pending_matches = self.pending_matches,
            timers = self.timers.snapshot()
        )

    # Fault-tolerant recovery
    def restore_state(checkpoint_id: long):
        state = load_checkpoint(checkpoint_id)
        self.nfa.deserialize(state.nfa_state)
        self.pending_matches = state.pending_matches
        self.timers.restore(state.timers)

```
<!-- SOURCE_MD5:6adaeb6c4ff30d27d96cf9cd9691ffaf-->

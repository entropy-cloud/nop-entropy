# Window Functions

## WindowOperator

1. First, allocate a set of windows for the elements.
2. For each window, check if the trigger is activated and modify the timer state.
3. When the timer triggers, check if the trigger is activated.


```markdown
# Process Element Function

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
```

# Event Time Handler Function

```javascript
void onEventTime(InternalTimer<K, W> timer) {
    triggerContext.key = timer.getKey();
    triggerContext.window = timer.getNamespace();

    MergingWindowSet<W> mergingWindows;

    if (windowAssigner instanceof MergingWindowAssigner) {
        mergingWindows = getMergingWindowSet();
        W stateWindow = mergingWindows.getStateWindow(triggerContext.window);
        if (stateWindow == null) {
            // Timer is firing for a non-existent window, which can only happen if
            // the trigger did not clean up timers. We have already cleared the merging
            // window and therefore the Trigger state; however, nothing to do.
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
            emitWindowContents(actualWindow, contents);
        }
    }

    if (triggerResult.isPurge()) {
        windowState.clear();
    }

    if (windowAssigner.isEventTime()
            && isCleanupTime(triggerContext.window, timer.getTimestamp())) {
        clearAllState(actualWindow, windowState, mergingWindows);
    }

    if (mergingWindows != null) {
        // Ensure the merging state is persisted
        mergingWindows.persist();
    }
}
```


The difference between `EvictorWindowOperator` and `WindowOperator` lies in whether the `evictor` is called during the `emit` process to delete windows.

# 窗口函数

## WindowOperator

1. 先为元素分配一组窗口
2. 针对每个窗口，检查trigger是否触发，并修改timer状态
3. timer触发时，同样是检查trigger是否触发

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

EvictorWindowOperator和WindowOperator的区别仅在于emit的时候是否调用evictor来删除窗口。

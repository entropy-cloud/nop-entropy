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

## WindowOperator的伪代码执行逻辑

```
class WindowOperator[IN, OUT, KEY, W <: Window]:
    # 状态管理：每个窗口对应的元素集合
    window_state: KeyedStateStore[KEY, W, List[IN]]
    window_assigner: WindowAssigner[IN, W]  # 窗口分配器（如滚动、滑动窗口）
    trigger: Trigger[IN, W]                 # 触发器（决定何时触发计算）
    window_function: ProcessWindowFunction[IN, OUT, KEY, W]  # 窗口处理函数

    def process_element(element: StreamRecord[IN], current_watermark: long):
        # 1. 分配窗口（可能多个，例如滑动窗口）
        windows = window_assigner.assign_windows(element.value, element.timestamp)

        for window in windows:
            # 2. 将元素加入窗口状态
            key = get_key(element)  # 从KeyedStream中提取Key
            state = window_state.get_state(key, window)
            state.add(element.value)

            # 3. 触发触发器，判断是否需要立即触发计算
            trigger_result = trigger.on_element(element.value, element.timestamp, window, current_watermark)
            if trigger_result == FIRE:
                compute_and_emit(key, window, current_watermark)
            elif trigger_result == PURGE:
                window_state.clear(key, window)

    def on_watermark(watermark: Watermark):
        # 4. 水印到达时，触发过期窗口计算
        for window in window_state.get_all_windows():
            if window.end <= watermark.timestamp:
                for key in window_state.get_keys(window):
                    compute_and_emit(key, window, watermark.timestamp)
                window_state.clear_all(window)

    def compute_and_emit(key: KEY, window: W, watermark: long):
        # 5. 执行窗口函数并输出结果
        elements = window_state.get_state(key, window)
        context = WindowContext(key, window, elements, watermark)
        output = window_function.process(context)
        emit(output)

```

Flink CEP的处理逻辑

```
class PatternOperator[IN, OUT]:
    # 初始化参数
    def __init__(self, pattern: Pattern[IN, OUT], timeout_handling: bool):
        self.nfa = NFACompiler.compile(pattern)  # 编译为NFA状态机
        self.pending_matches = {}  # 暂存未完成的匹配序列
        self.timers = TimerService()  # 超时管理服务

    # 处理每个元素的核心逻辑
    def process_element(event: IN, timestamp: long):
        # 1. 更新事件时间
        self.timers.advance_watermark(timestamp)

        # 2. 遍历所有可能的NFA状态迁移
        for (key, partial_match) in self.pending_matches:
            possible_states = self.nfa.get_possible_states(partial_match)

            for state in possible_states:
                # 检查条件过滤
                if not self.check_condition(state, event):
                    continue

                # 3. 创建新的匹配序列
                new_match = partial_match.clone().add_event(event)

                # 4. 状态转移判断
                if state.is_final():
                    # 生成完整匹配
                    output = self.create_output(new_match)
                    emit_result(output)

                    if not state.is_looping():
                        self.pending_matches.remove(key)
                else:
                    # 更新中间状态
                    self.pending_matches[key] = new_match

                # 5. 注册超时定时器
                if state.get_window_time() is not None:
                    timeout = timestamp + state.get_window_time()
                    self.timers.register_event_time_timer(timeout)

    # 定时器触发处理(超时逻辑)
    def on_timer(timestamp: long):
        for (key, partial_match) in self.pending_matches:
            for state in partial_match.get_current_states():
                # 检查超时条件
                if state.get_window_time() + partial_match.get_start_time() < timestamp:
                    # 处理超时匹配
                    if state.is_optional():
                        self.pending_matches.remove(key)
                    else:
                        output = self.handle_timeout(partial_match)
                        emit_result(output)
                        self.pending_matches.remove(key)

    # 支持CEP的特殊操作
    def handle_pattern_ops():
        # 量词处理 (例如: oneOrMore, times(3))
        for match in self.pending_matches.values():
            if match.current_state.quantifier == Quantifier.ONE_OR_MORE:
                self.handle_one_or_more(match)

        # 相邻事件处理 (例如: next, followedBy)
        self.check_adjacent_relations()

        # 直到条件处理
        self.check_until_conditions()

    # 状态后端交互
    def snapshot_state(checkpoint_id: long):
        save_checkpoint(
            nfa_state = self.nfa.serialize(),
            pending_matches = self.pending_matches,
            timers = self.timers.snapshot()
        )

    # 容错恢复
    def restore_state(checkpoint_id: long):
        state = load_checkpoint(checkpoint_id)
        self.nfa.deserialize(state.nfa_state)
        self.pending_matches = state.pending_matches
        self.timers.restore(state.timers)

```

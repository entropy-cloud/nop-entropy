package io.nop.xlang.truffle.frame;

import com.oracle.truffle.api.frame.FrameDescriptor;
import io.nop.xlang.truffle.frame.FrameLayoutMapper.SlotMeta;

import java.util.Collections;
import java.util.List;

/**
 * 帧布局：每 RootNode 一份 FrameDescriptor + slot 元数据（下标一一对应）。
 */
public final class FrameLayout {

    private final FrameDescriptor descriptor;

    private final List<SlotMeta> slots;

    FrameLayout(FrameDescriptor descriptor, List<SlotMeta> slots) {
        this.descriptor = descriptor;
        this.slots = Collections.unmodifiableList(slots);
    }

    public FrameDescriptor getDescriptor() {
        return descriptor;
    }

    public int getSlotCount() {
        return slots.size();
    }

    public SlotMeta getSlot(int index) {
        return slots.get(index);
    }

    public List<SlotMeta> getSlots() {
        return slots;
    }
}

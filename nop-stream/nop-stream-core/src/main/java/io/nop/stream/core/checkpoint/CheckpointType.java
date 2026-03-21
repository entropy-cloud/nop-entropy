/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

/**
 * Checkpoint 类型枚举。
 * 
 * <p>定义不同类型的 checkpoint，用于区分触发方式和行为。
 * 
 * <ul>
 *   <li>{@link #CHECKPOINT} - 常规自动触发的 checkpoint</li>
 *   <li>{@link #SAVEPOINT} - 用户手动触发的 savepoint</li>
 *   <li>{@link #COMPLETED_POINT_TYPE} - 任务完成时的最终 checkpoint</li>
 * </ul>
 */
public enum CheckpointType {

    /**
     * 常规 checkpoint，由系统自动触发。
     */
    CHECKPOINT(true, "checkpoint"),

    /**
     * Savepoint，由用户手动触发，用于版本升级或维护。
     */
    SAVEPOINT(false, "savepoint"),

    /**
     * 任务完成时的最终 checkpoint，确保所有数据都已处理。
     */
    COMPLETED_POINT_TYPE(true, "completed");

    private final boolean auto;
    private final String name;

    CheckpointType(boolean auto, String name) {
        this.auto = auto;
        this.name = name;
    }

    /**
     * 是否为自动触发的 checkpoint。
     * 
     * @return true 表示自动触发，false 表示手动触发
     */
    public boolean isAuto() {
        return auto;
    }

    /**
     * 获取 checkpoint 类型名称。
     * 
     * @return 类型名称字符串
     */
    public String getName() {
        return name;
    }

    /**
     * 是否为最终 checkpoint（任务结束或 savepoint）。
     * 
     * <p>最终 checkpoint 会触发特殊的关闭逻辑。
     * 
     * @return true 表示是最终 checkpoint
     */
    public boolean isFinalCheckpoint() {
        return this == COMPLETED_POINT_TYPE || this == SAVEPOINT;
    }

    /**
     * 根据名称获取 CheckpointType。
     * 
     * @param name 类型名称
     * @return 对应的 CheckpointType，如果未找到则返回 null
     */
    public static CheckpointType fromName(String name) {
        for (CheckpointType type : values()) {
            if (type.name.equals(name)) {
                return type;
            }
        }
        return null;
    }
}

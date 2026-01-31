package io.nop.ai.shell.commands;

/**
 * Shell命令接口
 * <p>
 * 所有shell命令都必须实现此接口，提供标准的命令执行机制。
 * </p>
 */
public interface IShellCommand {

    /**
     * 获取命令名称
     *
     * @return 命令名（如 "ls", "cat"）
     */
    String name();

    /**
     * 获取命令描述
     *
     * @return 一行描述
     */
    String description();

    /**
     * 获取命令用法说明
     *
     * @return 详细用法说明（多行）
     */
    String usage();

    /**
     * 获取完整帮助文本
     *
     * @return 帮助文本（完整格式）
     */
    String getHelp();

    /**
     * 执行命令
     *
     * @param context 命令执行上下文
     * @return 退出码（0表示成功，非0表示失败）
     * @throws Exception 执行过程中抛出的异常
     */
    int execute(IShellCommandExecutionContext context) throws Exception;
}

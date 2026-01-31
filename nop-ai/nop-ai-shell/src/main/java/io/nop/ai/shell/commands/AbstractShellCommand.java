package io.nop.ai.shell.commands;

/**
 * Shell命令的抽象基类
 * <p>
 * 提供通用的命令执行模板。
 * 所有shell命令都应该继承此类。
 * </p>
 */
public abstract class AbstractShellCommand implements IShellCommand {

    /**
     * 执行命令
     *
     * @param context 命令执行上下文
     * @return 退出码（0表示成功，非0表示失败）
     * @throws Exception 执行过程中抛出的异常
     */
    @Override
    public abstract int execute(IShellCommandExecutionContext context) throws Exception;

    /**
     * 获取帮助文本
     * <p>
     * 子类可以覆盖此方法提供完整的帮助文本。
     * 如果未覆盖，则使用usage() + description()的默认格式。
     * </p>
     *
     * @return 帮助文本
     */
    @Override
    public String getHelp() {
        StringBuilder help = new StringBuilder();
        help.append(usage()).append("\n");
        help.append(description());
        return help.toString();
    }
}

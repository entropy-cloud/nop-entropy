package io.nop.ai.shell.commands;

import io.nop.ai.shell.io.IShellInput;
import io.nop.ai.shell.io.IShellOutput;
import io.nop.core.resource.IResourceStore;

import java.util.Map;

/**
 * Shell命令执行上下文接口
 * <p>
 * 提供命令执行所需的完整上下文信息，包括I/O流、环境变量、工作目录和虚拟文件系统。
 * </p>
 */
public interface IShellCommandExecutionContext {

    /**
     * 获取标准输入
     *
     * @return 标准输入流
     */
    IShellInput stdin();

    /**
     * 获取标准输出
     *
     * @return 标准输出流
     */
    IShellOutput stdout();

    /**
     * 获取标准错误输出
     *
     * @return 标准错误输出流
     */
    IShellOutput stderr();

    /**
     * 获取环境变量映射
     *
     * @return 环境变量（只读）
     */
    Map<String, String> environment();

    /**
     * 获取当前工作目录
     *
     * @return 当前工作目录路径
     */
    String workingDirectory();

    /**
     * 获取命令行参数
     *
     * @return 所有参数（包括标志和位置参数）
     */
    String[] arguments();

    /**
     * 检查是否存在指定标志
     * <p>
     * 支持的格式：--flag（长格式）或 -f（短格式）
     * </p>
     *
     * @param flag 标志名称（不含前缀）
     * @return 如果标志存在返回true，否则返回false
     */
    boolean hasFlag(String flag);

    /**
     * 获取指定标志的值
     * <p>
     * 例如：--file=test.txt → getFlagValue("file") 返回 "test.txt"
     * </p>
     *
     * @param flag 标志名称（不含前缀）
     * @return 标志的值，如果标志不存在则返回null
     */
    String getFlagValue(String flag);

    /**
     * 获取位置参数
     * <p>
     * 位置参数是指所有标志之后的参数。
     * </p>
     *
     * @return 位置参数数组
     */
    String[] positionalArguments();

    /**
     * 获取虚拟文件系统资源存储
     *
     * @return 资源存储实例
     */
    IResourceStore resourceStore();
}

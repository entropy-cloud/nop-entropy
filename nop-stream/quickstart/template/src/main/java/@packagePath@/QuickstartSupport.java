package @package@;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.flow.builder.InMemoryBeanFunctionResolver;
import io.nop.stream.flow.builder.StreamModelDslBuilder;
import io.nop.stream.flow.model.StreamModel;
import io.nop.xlang.xdsl.DslModelParser;

/**
 * XDSL 拓扑的加载与执行惯用法（与 nop-stream-flow 测试同一链路）：
 *
 * <pre>{@code
 *   DslModelParser 解析 .stream.xml（xdef 校验 + Delta 合并）
 *     → StreamModelDslBuilder.of(model, beanResolver).build() 装配出 StreamExecutionEnvironment
 *     → env.execute(jobName)
 * }</pre>
 */
public final class QuickstartSupport {

    private QuickstartSupport() {
    }

    /** 初始化 VFS（解析 x:schema=/nop/schema/stream/stream.xdef 需要类路径上的 nop-xdefs）。 */
    public static void initVfs() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
    }

    public static void destroyVfs() {
        CoreInitialization.destroy();
    }

    /**
     * 注册 checkpoint 执行器（静态全局，须在执行后 {@link #unregisterCheckpointExecutorFactory()} 清理）。
     *
     * <p>enableCheckpointing 的作业只有注册了执行器工厂才会走 checkpoint 执行路径
     * （该路径同时为 keyed 算子装配 IStateBackend）；未注册时回落 LOCAL 直执路径，
     * keyed state 算子会 fail-fast。
     */
    public static void registerCheckpointExecutorFactory() {
        StreamExecutionEnvironment.setCheckpointExecutorFactory(
                new io.nop.stream.runtime.execution.CheckpointExecutorFactoryImpl());
    }

    public static void unregisterCheckpointExecutorFactory() {
        StreamExecutionEnvironment.setCheckpointExecutorFactory(null);
    }

    /** 解析并装配一个 .stream.xml；bean 引用全部来自传入的内存注册表。 */
    public static StreamExecutionEnvironment buildFromXdsl(String vfsPath,
            InMemoryBeanFunctionResolver beans) {
        IResource resource = VirtualFileSystem.instance().getResource(vfsPath);
        if (!resource.exists()) {
            throw new IllegalArgumentException("stream xml not found on vfs: " + vfsPath);
        }
        StreamModel model = (StreamModel) new DslModelParser().parseFromResource(resource);
        return StreamModelDslBuilder.of(model, beans).build();
    }
}

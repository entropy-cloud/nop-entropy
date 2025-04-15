## DataStream转换为StreamGraph的逻辑

```
class StreamExecutionEnvironment:
    def __init__(self):
        self.transformations = []  # 存储所有转换操作的列表

    # 用户API入口
    def add_source(self, source_func):
        transformation = SourceTransformation(source_func)
        self.transformations.append(transformation)
        return DataStream(transformation)

    def execute(self, job_name):
        stream_graph = self._generate_stream_graph(job_name)
        return stream_graph

    # 核心转换逻辑
    def _generate_stream_graph(self, job_name):
        stream_graph = StreamGraph(job_name)
        visited = set()  # 已处理节点缓存

        # 递归遍历转换树
        def process_transformation(transformation, parent_ids):
            if transformation.id in visited:
                return []
            visited.add(transformation.id)

            # 1. 创建当前节点
            stream_node = StreamNode(
                id = transformation.id,
                operator = transformation.get_operator(),
                parallelism = transformation.parallelism,
                config = transformation.config
            )
            stream_graph.add_node(stream_node)

            # 2. 处理节点链接逻辑
            current_ids = [stream_node.id]
            for input_transformation in transformation.inputs:
                parent_ids = process_transformation(input_transformation, parent_ids)

            # 3. 创建虚拟边（处理合并优化）
            if self._should_chain(parent_ids, transformation):
                # 合并到前驱节点链
                chained_node = self._chain_operators(parent_ids, stream_node)
                current_ids = [chained_node.id]
            else:
                # 创建物理边连接
                for parent_id in parent_ids:
                    stream_graph.add_edge(
                        StreamEdge(
                            source_id = parent_id,
                            target_id = stream_node.id,
                            partitioner = transformation.get_partitioner(),
                            shuffle_mode = transformation.get_shuffle_mode()
                        )
                    )

            return current_ids

        # 从Sink开始反向构建
        for sink_transformation in self._get_sink_transformations():
            process_transformation(sink_transformation, [])

        return stream_graph

    # 判断是否合并算子链
    def _should_chain(self, parent_ids, current_trans):
        if not parent_ids:
            return False

        last_parent = stream_graph.get_node(parent_ids[-1])
        return (last_parent.parallelism == current_trans.parallelism and
                last_parent.shuffle_mode == current_trans.shuffle_mode and
                last_parent.operator.is_chainable(current_trans.operator))

    # 合并算子形成Operator Chain
    def _chain_operators(self, parent_ids, current_node):
        parent_node = stream_graph.get_node(parent_ids[-1])
        merged_operator = ChainedOperator(
            parent_node.operator,
            current_node.operator
        )
        # 更新父节点
        parent_node.operator = merged_operator
        parent_node.output_types = current_node.output_types
        return parent_node

class StreamGraph:
    def __init__(self, job_name):
        self.nodes = {}         # {id: StreamNode}
        self.edges = []         # [StreamEdge]
        self.job_config = {}    # 作业级配置

    def add_node(self, node):
        self.nodes[node.id] = node

    def add_edge(self, edge):
        self.edges.append(edge)

class StreamNode:
    def __init__(self, id, operator, parallelism, config):
        self.id = id
        self.operator = operator
        self.parallelism = parallelism
        self.config = config
        self.input_edges = []
        self.output_edges = []

class StreamEdge:
    def __init__(self, source_id, target_id, partitioner, shuffle_mode):
        self.source_id = source_id
        self.target_id = target_id
        self.partitioner = partitioner
        self.shuffle_mode = shuffle_mode
```

## StreamGraph到ExecutableGraph的转换过程

```
class FlinkJobExecutor:
    def execute_stream_graph(stream_graph):
        # 第一步：生成JobGraph（逻辑执行计划）
        job_graph = self._build_job_graph(stream_graph)

        # 第二步：生成ExecutionGraph（物理执行计划）
        execution_graph = self._build_execution_graph(job_graph)

        # 第三步：拆分为可调度的Task
        physical_plan = self._create_physical_plan(execution_graph)
        return physical_plan

    # 核心转换方法
    def _build_job_graph(stream_graph):
        job_graph = JobGraph()
        chain_vertices = []

        # 遍历StreamGraph节点进行算子链合并
        for node in stream_graph.topologically_sorted_nodes():
            # 判断是否可合并到当前链
            if self._can_chain(node, current_chain):
                current_chain.add(node)
            else:
                # 生成JobVertex
                job_vertex = JobVertex(
                    id = generate_id(),
                    operators = current_chain.operators,
                    parallelism = current_chain.parallelism,
                    config = merge_configs(current_chain.nodes)
                )
                job_graph.add_vertex(job_vertex)
                chain_vertices.append(job_vertex)
                current_chain = new_chain(node)

        # 处理边连接关系
        for edge in stream_graph.edges:
            source_vertex = find_source_vertex(edge)
            target_vertex = find_target_vertex(edge)

            job_graph.add_edge(
                JobEdge(
                    source = source_vertex,
                    target = target_vertex,
                    partitioner = edge.partitioner,
                    ship_strategy = self._get_ship_strategy(edge)
                )
            )

        return job_graph

    # 生成ExecutionGraph
    def _build_execution_graph(job_graph):
        execution_graph = ExecutionGraph()

        # 为每个JobVertex生成ExecutionJobVertex
        for job_vertex in job_graph.vertices:
            ejv = ExecutionJobVertex(
                job_vertex,
                max_parallelism = job_vertex.parallelism,
                task_factory = self._create_task_factory(job_vertex)
            )

            # 创建并行子任务
            for i in range(job_vertex.parallelism):
                execution_vertex = ExecutionVertex(
                    ejv,
                    subtask_index = i,
                    input_edges = self._build_execution_edges(job_vertex, i)
                )
                ejv.add_subtask(execution_vertex)

            execution_graph.add_vertex(ejv)

        return execution_graph

    # 物理执行计划生成
    def _create_physical_plan(execution_graph):
        physical_plan = {}

        for ejv in execution_graph.vertices:
            for subtask in ejv.subtasks:
                # 生成Task部署描述符
                task = Task(
                    task_id = generate_task_id(ejv, subtask.index),
                    vertex_id = ejv.id,
                    subtask_index = subtask.index,
                    input_channels = self._resolve_inputs(subtask),
                    output_channels = self._resolve_outputs(subtask),
                    task_manager = self._select_task_manager(),
                    slot = self._allocate_slot()
                )
                physical_plan[task.task_id] = task

        return physical_plan

    # 链化条件判断
    def _can_chain(current_node, candidate_node):
        return all([
            same_parallelism(current_node, candidate_node),
            same_task_group(current_node, candidate_node),
            is_chainable(current_node, candidate_node),
            not has_disabled_chaining(current_node),
            not has_shuffle_between(current_node, candidate_node)
        ])

# 链式合并示例：
StreamGraph节点：Source -> Map -> Filter -> Sink
合并后JobVertex：SourceChain(Map,Filter) -> Sink

# 边策略判断逻辑：
def _get_ship_strategy(edge):
    if edge.partitioner == ForwardPartitioner:
        return DataExchangeMode.BATCH
    elif edge.partitioner == RescalePartitioner:
        return DataExchangeMode.PIPELINE
    else:
        return DataExchangeMode.HYBRID

def is_chainable(upstream, downstream):
    return (
        upstream.get_output_type() == downstream.get_input_type() and
        upstream.get_parallelism() == downstream.get_parallelism() and
        not has_shuffle_between(upstream, downstream)
    )

def _select_task_manager():
    # 基于资源利用率的调度策略
    return sorted(task_managers, key=lambda tm: tm.free_slots)

def _allocate_slot():
    # 考虑本地性优先原则
    return current_task_manager.allocate_slot()

def resolve_data_exchange(edge):
    if edge.ship_strategy == ShipStrategyType.FORWARD:
        return LocalExchangeChannel()
    elif edge.ship_strategy == ShipStrategyType.BROADCAST:
        return BroadcastChannel()
    else:
        return NetworkExchangeChannel()

def has_shuffle_between(
    current_node: StreamNode,
    candidate_node: StreamNode
) -> bool:
    # 获取两节点间所有连接边
    connecting_edges = find_edges_between(current_node, candidate_node)

    for edge in connecting_edges:
        # 判断是否强制禁用链式合并
        if edge.disable_chaining:
            return True

        # 获取数据分发策略
        exchange_mode = edge.get_exchange_mode()

        # 判断是否Forward模式（无数据重分发）
        if exchange_mode == DataExchangeMode.FORWARD:
            # 需要满足并行度一致且无物理分区变化
            if current_node.parallelism != candidate_node.parallelism:
                return True  # 并行度变化导致隐含shuffle
            continue

        # 检查分区器类型
        partitioner = edge.get_partitioner()
        if isinstance(partitioner, (KeyGroupStreamPartitioner, CustomPartitioner)):
            return True  # KeyBy或自定义分区需要shuffle

        # 特殊分发策略判断
        if exchange_mode in [
            DataExchangeMode.BATCH,
            DataExchangeMode.BROADCAST,
            DataExchangeMode.RESCALE
        ]:
            return True

        # 显式配置了数据重分发
        if edge.requires_shuffle:
            return True

    # 所有边均满足无shuffle条件
    return False

# 辅助函数：查找节点间连接边
def find_edges_between(source: StreamNode, target: StreamNode) -> List[StreamEdge]:
    edges = []
    for edge in source.out_edges:
        if edge.target_id == target.id:
            edges.append(edge)
    return edges

```

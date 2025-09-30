## Logic for converting DataStream to StreamGraph

```
class StreamExecutionEnvironment:
    def __init__(self):
        self.transformations = []  # List storing all transformation operations

    # User API entry point
    def add_source(self, source_func):
        transformation = SourceTransformation(source_func)
        self.transformations.append(transformation)
        return DataStream(transformation)

    def execute(self, job_name):
        stream_graph = self._generate_stream_graph(job_name)
        return stream_graph

    # Core conversion logic
    def _generate_stream_graph(self, job_name):
        stream_graph = StreamGraph(job_name)
        visited = set()  # Cache of processed nodes

        # Recursively traverse the transformation tree
        def process_transformation(transformation, parent_ids):
            if transformation.id in visited:
                return []
            visited.add(transformation.id)

            # 1. Create the current node
            stream_node = StreamNode(
                id = transformation.id,
                operator = transformation.get_operator(),
                parallelism = transformation.parallelism,
                config = transformation.config
            )
            stream_graph.add_node(stream_node)

            # 2. Handle node linking logic
            current_ids = [stream_node.id]
            for input_transformation in transformation.inputs:
                parent_ids = process_transformation(input_transformation, parent_ids)

            # 3. Create virtual edges (handle chaining optimization)
            if self._should_chain(parent_ids, transformation):
                # Merge into the predecessor node chain
                chained_node = self._chain_operators(parent_ids, stream_node)
                current_ids = [chained_node.id]
            else:
                # Create physical edge connections
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

        # Build in reverse starting from sinks
        for sink_transformation in self._get_sink_transformations():
            process_transformation(sink_transformation, [])

        return stream_graph

    # Decide whether to chain operators
    def _should_chain(self, parent_ids, current_trans):
        if not parent_ids:
            return False

        last_parent = stream_graph.get_node(parent_ids[-1])
        return (last_parent.parallelism == current_trans.parallelism and
                last_parent.shuffle_mode == current_trans.shuffle_mode and
                last_parent.operator.is_chainable(current_trans.operator))

    # Merge operators to form an Operator Chain
    def _chain_operators(self, parent_ids, current_node):
        parent_node = stream_graph.get_node(parent_ids[-1])
        merged_operator = ChainedOperator(
            parent_node.operator,
            current_node.operator
        )
        # Update the parent node
        parent_node.operator = merged_operator
        parent_node.output_types = current_node.output_types
        return parent_node

class StreamGraph:
    def __init__(self, job_name):
        self.nodes = {}         # {id: StreamNode}
        self.edges = []         # [StreamEdge]
        self.job_config = {}    # Job-level configuration

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

## Process of converting StreamGraph to ExecutableGraph

```
class FlinkJobExecutor:
    def execute_stream_graph(stream_graph):
        # Step 1: Generate JobGraph (logical execution plan)
        job_graph = self._build_job_graph(stream_graph)

        # Step 2: Generate ExecutionGraph (physical execution plan)
        execution_graph = self._build_execution_graph(job_graph)

        # Step 3: Split into schedulable tasks
        physical_plan = self._create_physical_plan(execution_graph)
        return physical_plan

    # Core conversion method
    def _build_job_graph(stream_graph):
        job_graph = JobGraph()
        chain_vertices = []

        # Traverse StreamGraph nodes to merge operator chains
        for node in stream_graph.topologically_sorted_nodes():
            # Determine whether it can be merged into the current chain
            if self._can_chain(node, current_chain):
                current_chain.add(node)
            else:
                # Generate JobVertex
                job_vertex = JobVertex(
                    id = generate_id(),
                    operators = current_chain.operators,
                    parallelism = current_chain.parallelism,
                    config = merge_configs(current_chain.nodes)
                )
                job_graph.add_vertex(job_vertex)
                chain_vertices.append(job_vertex)
                current_chain = new_chain(node)

        # Handle edge connection relationships
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

    # Generate ExecutionGraph
    def _build_execution_graph(job_graph):
        execution_graph = ExecutionGraph()

        # Generate an ExecutionJobVertex for each JobVertex
        for job_vertex in job_graph.vertices:
            ejv = ExecutionJobVertex(
                job_vertex,
                max_parallelism = job_vertex.parallelism,
                task_factory = self._create_task_factory(job_vertex)
            )

            # Create parallel subtasks
            for i in range(job_vertex.parallelism):
                execution_vertex = ExecutionVertex(
                    ejv,
                    subtask_index = i,
                    input_edges = self._build_execution_edges(job_vertex, i)
                )
                ejv.add_subtask(execution_vertex)

            execution_graph.add_vertex(ejv)

        return execution_graph

    # Physical execution plan generation
    def _create_physical_plan(execution_graph):
        physical_plan = {}

        for ejv in execution_graph.vertices:
            for subtask in ejv.subtasks:
                # Generate Task deployment descriptor
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

    # Chaining condition evaluation
    def _can_chain(current_node, candidate_node):
        return all([
            same_parallelism(current_node, candidate_node),
            same_task_group(current_node, candidate_node),
            is_chainable(current_node, candidate_node),
            not has_disabled_chaining(current_node),
            not has_shuffle_between(current_node, candidate_node)
        ])

# Example of chained merging:
StreamGraph nodes: Source -> Map -> Filter -> Sink
JobVertex after merging: SourceChain(Map,Filter) -> Sink

# Edge strategy decision logic:
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
    # Scheduling strategy based on resource utilization
    return sorted(task_managers, key=lambda tm: tm.free_slots)

def _allocate_slot():
    # Favor locality
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
    # Retrieve all connecting edges between the two nodes
    connecting_edges = find_edges_between(current_node, candidate_node)

    for edge in connecting_edges:
        # Check if chain merging is explicitly disabled
        if edge.disable_chaining:
            return True

        # Obtain the data distribution strategy
        exchange_mode = edge.get_exchange_mode()

        # Determine if it is Forward mode (no data redistribution)
        if exchange_mode == DataExchangeMode.FORWARD:
            # Requires consistent parallelism and no physical partition changes
            if current_node.parallelism != candidate_node.parallelism:
                return True  # Parallelism change implies a shuffle
            continue

        # Check the partitioner type
        partitioner = edge.get_partitioner()
        if isinstance(partitioner, (KeyGroupStreamPartitioner, CustomPartitioner)):
            return True  # KeyBy or custom partitioning requires a shuffle

        # Special distribution strategies check
        if exchange_mode in [
            DataExchangeMode.BATCH,
            DataExchangeMode.BROADCAST,
            DataExchangeMode.RESCALE
        ]:
            return True

        # Data redistribution explicitly configured
        if edge.requires_shuffle:
            return True

    # All edges satisfy the no-shuffle condition
    return False

# Helper function: find connecting edges between nodes
def find_edges_between(source: StreamNode, target: StreamNode) -> List[StreamEdge]:
    edges = []
    for edge in source.out_edges:
        if edge.target_id == target.id:
            edges.append(edge)
    return edges

```
<!-- SOURCE_MD5:5d3150343a62d455043562c825a38e73-->

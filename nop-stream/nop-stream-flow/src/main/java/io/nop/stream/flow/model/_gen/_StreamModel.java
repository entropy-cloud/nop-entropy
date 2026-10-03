package io.nop.stream.flow.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.stream.flow.model.StreamModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/stream/stream.xdef <p>
 * nop-stream 声明式流处理模型定义。
 * StreamModel 是 nop-stream 的 canonical 模型，可由三种入口合成：
 * 1. XDSL 声明式定义（本 xdef 描述的形式）
 * 2. Java DataStream API 编程构造
 * 3. Delta 定制合成
 * 三种入口最终生成同一套 StreamModel，经统一的五阶段执行管线编译执行：
 * StreamModel → StreamGraph → JobGraph → PartitionedPlan → DeploymentPlan
 * （GraphExecutionPlan 不是第 6 个管线阶段：它是 LOCAL 模式下 DeploymentPlan 的运行时执行形态，
 * 经 GraphExecutionPlan.build + TaskExecutor 执行）
 * 核心设计原则：
 * - 同像约束：xdef 结构与最终 XML 实例结构一致
 * - 组件注册表：所有可复用组件（windowingStrategies/coders/schemas）通过稳定 ID 引用
 * - 可逆计算：支持 x:extends 继承和 Delta 定制
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _StreamModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: aggregators
     * WI8c: 参数化聚合注册表——entry 是纯参数描述符（fnId + 取值表达式），
     * 稳定 ID 供 <aggregate aggregatorRef> 引用；累加语义由聚合求值器提供，
     * 与 windowingStrategies 同构。schemaId 可选：声明时按该 schema 解析列类型
     * 供参数类型校验。
     */
    private KeyedList<io.nop.stream.flow.model.StreamAggregatorModel> _aggregators = KeyedList.emptyList();
    
    /**
     *  
     * xml name: checkpoint
     * Checkpoint 配置
     */
    private io.nop.stream.flow.model.CheckpointConfigModel _checkpoint ;
    
    /**
     *  
     * xml name: checkpointParticipants
     * Checkpoint 参与者：声明需要参与 checkpoint 的算子 ID
     */
    private java.util.List<io.nop.stream.flow.model.CheckpointParticipantRefModel> _checkpointParticipants = java.util.Collections.emptyList();
    
    /**
     *  
     * xml name: coders
     * 序列化器注册表
     */
    private KeyedList<io.nop.stream.flow.model.CoderModel> _coders = KeyedList.emptyList();
    
    /**
     *  
     * xml name: edges
     * ==================== 边（DAG 边，数据流向） ====================
     */
    private KeyedList<io.nop.stream.flow.model.StreamEdgeModel> _edges = KeyedList.emptyList();
    
    /**
     *  
     * xml name: environments
     * 运行环境注册表（支持多环境配置）
     */
    private KeyedList<io.nop.stream.flow.model.StreamEnvironmentModel> _environments = KeyedList.emptyList();
    
    /**
     *  
     * xml name: joins
     * WI8d: 参数化 join 注册表——entry 是纯声明描述符（joinType + 双键集 +
     * 可选窗口策略），稳定 ID 供 <join joinRef> 引用；运行时求值由 WI13 的
     * buildJoin 承接，本声明面只承载构建期校验。多 key 用逗号分隔。timeout
     * 为窗口 join 的超时窗口（duration 格式），仅与 windowStrategyRef 同用。
     */
    private KeyedList<io.nop.stream.flow.model.StreamJoinSpecModel> _joins = KeyedList.emptyList();
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: onEnd
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _onEnd ;
    
    /**
     *  
     * xml name: onError
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _onError ;
    
    /**
     *  
     * xml name: onStart
     * ==================== 生命周期回调 ====================
     */
    private io.nop.core.lang.eval.IEvalFunction _onStart ;
    
    /**
     *  
     * xml name: parallelism
     * 
     */
    private int _parallelism  = 1;
    
    /**
     *  
     * xml name: patterns
     * ==================== CEP 模式定义（引用 pattern.xdef） ====================
     */
    private KeyedList<io.nop.stream.cep.model.CepPatternModel> _patterns = KeyedList.emptyList();
    
    /**
     *  
     * xml name: requirements
     * 需求声明：运行时必须满足的能力集合
     */
    private java.util.List<io.nop.stream.flow.model.StreamRequirementModel> _requirements = java.util.Collections.emptyList();
    
    /**
     *  
     * xml name: schemas
     * Schema 注册表
     */
    private KeyedList<io.nop.stream.flow.model.StreamSchemaModel> _schemas = KeyedList.emptyList();
    
    /**
     *  
     * xml name: sideInputs
     * ==================== 侧输入（Side Input，流间引用） ====================
     */
    private KeyedList<io.nop.stream.flow.model.StreamSideInputModel> _sideInputs = KeyedList.emptyList();
    
    /**
     *  
     * xml name: sql
     * WI17 (plan 25 r2 B4): <sql> 是模型级生成器而非 transform——顶层元素，编译发生在
     * builder.build() 的 buildTransforms 之前：SPI（ISqlStreamCompiler，nop-stream-flow
     * 定义，nop-stream-sql 提供实现）把 SQL 文本编译为完整流模型 XML，builder 解析回读并
     * 替换父模型内容。<sql> 必须是模型唯一内容（与其他 transforms/edges/registries 并存
     * → fail-fast）。schema 为 SQL 面自带声明（D7 受管类型名九名闭集）；sinkBean 由编译器
     * 追加 sink 声明与边（产物自洽可执行）。
     */
    private io.nop.stream.flow.model.StreamSqlModel _sql ;
    
    /**
     *  
     * xml name: streams
     * 流定义注册表（命名流，用于跨算子引用）
     */
    private KeyedList<io.nop.stream.flow.model.StreamDefinitionModel> _streams = KeyedList.emptyList();
    
    /**
     *  
     * xml name: transforms
     * 
     */
    private KeyedList<io.nop.stream.flow.model.StreamTransformModel> _transforms = KeyedList.emptyList();
    
    /**
     *  
     * xml name: version
     * 
     */
    private long _version  = 0;
    
    /**
     *  
     * xml name: watermarkInterval
     * 
     */
    private long _watermarkInterval  = 200;
    
    /**
     *  
     * xml name: windowingStrategies
     * 窗口策略注册表
     */
    private KeyedList<io.nop.stream.flow.model.WindowingStrategyModel> _windowingStrategies = KeyedList.emptyList();
    
    /**
     * 
     * xml name: aggregators
     *  WI8c: 参数化聚合注册表——entry 是纯参数描述符（fnId + 取值表达式），
     * 稳定 ID 供 <aggregate aggregatorRef> 引用；累加语义由聚合求值器提供，
     * 与 windowingStrategies 同构。schemaId 可选：声明时按该 schema 解析列类型
     * 供参数类型校验。
     */
    
    public java.util.List<io.nop.stream.flow.model.StreamAggregatorModel> getAggregators(){
      return _aggregators;
    }

    
    public void setAggregators(java.util.List<io.nop.stream.flow.model.StreamAggregatorModel> value){
        checkAllowChange();
        
        this._aggregators = KeyedList.fromList(value, io.nop.stream.flow.model.StreamAggregatorModel::getAggregatorId);
           
    }

    
    public io.nop.stream.flow.model.StreamAggregatorModel getAggregator(String name){
        return this._aggregators.getByKey(name);
    }

    public boolean hasAggregator(String name){
        return this._aggregators.containsKey(name);
    }

    public void addAggregator(io.nop.stream.flow.model.StreamAggregatorModel item) {
        checkAllowChange();
        java.util.List<io.nop.stream.flow.model.StreamAggregatorModel> list = this.getAggregators();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.stream.flow.model.StreamAggregatorModel::getAggregatorId);
            setAggregators(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_aggregators(){
        return this._aggregators.keySet();
    }

    public boolean hasAggregators(){
        return !this._aggregators.isEmpty();
    }
    
    /**
     * 
     * xml name: checkpoint
     *  Checkpoint 配置
     */
    
    public io.nop.stream.flow.model.CheckpointConfigModel getCheckpoint(){
      return _checkpoint;
    }

    
    public void setCheckpoint(io.nop.stream.flow.model.CheckpointConfigModel value){
        checkAllowChange();
        
        this._checkpoint = value;
           
    }

    
    /**
     * 
     * xml name: checkpointParticipants
     *  Checkpoint 参与者：声明需要参与 checkpoint 的算子 ID
     */
    
    public java.util.List<io.nop.stream.flow.model.CheckpointParticipantRefModel> getCheckpointParticipants(){
      return _checkpointParticipants;
    }

    
    public void setCheckpointParticipants(java.util.List<io.nop.stream.flow.model.CheckpointParticipantRefModel> value){
        checkAllowChange();
        
        this._checkpointParticipants = value;
           
    }

    
    /**
     * 
     * xml name: coders
     *  序列化器注册表
     */
    
    public java.util.List<io.nop.stream.flow.model.CoderModel> getCoders(){
      return _coders;
    }

    
    public void setCoders(java.util.List<io.nop.stream.flow.model.CoderModel> value){
        checkAllowChange();
        
        this._coders = KeyedList.fromList(value, io.nop.stream.flow.model.CoderModel::getId);
           
    }

    
    public io.nop.stream.flow.model.CoderModel getCoder(String name){
        return this._coders.getByKey(name);
    }

    public boolean hasCoder(String name){
        return this._coders.containsKey(name);
    }

    public void addCoder(io.nop.stream.flow.model.CoderModel item) {
        checkAllowChange();
        java.util.List<io.nop.stream.flow.model.CoderModel> list = this.getCoders();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.stream.flow.model.CoderModel::getId);
            setCoders(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_coders(){
        return this._coders.keySet();
    }

    public boolean hasCoders(){
        return !this._coders.isEmpty();
    }
    
    /**
     * 
     * xml name: edges
     *  ==================== 边（DAG 边，数据流向） ====================
     */
    
    public java.util.List<io.nop.stream.flow.model.StreamEdgeModel> getEdges(){
      return _edges;
    }

    
    public void setEdges(java.util.List<io.nop.stream.flow.model.StreamEdgeModel> value){
        checkAllowChange();
        
        this._edges = KeyedList.fromList(value, io.nop.stream.flow.model.StreamEdgeModel::getId);
           
    }

    
    public io.nop.stream.flow.model.StreamEdgeModel getEdge(String name){
        return this._edges.getByKey(name);
    }

    public boolean hasEdge(String name){
        return this._edges.containsKey(name);
    }

    public void addEdge(io.nop.stream.flow.model.StreamEdgeModel item) {
        checkAllowChange();
        java.util.List<io.nop.stream.flow.model.StreamEdgeModel> list = this.getEdges();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.stream.flow.model.StreamEdgeModel::getId);
            setEdges(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_edges(){
        return this._edges.keySet();
    }

    public boolean hasEdges(){
        return !this._edges.isEmpty();
    }
    
    /**
     * 
     * xml name: environments
     *  运行环境注册表（支持多环境配置）
     */
    
    public java.util.List<io.nop.stream.flow.model.StreamEnvironmentModel> getEnvironments(){
      return _environments;
    }

    
    public void setEnvironments(java.util.List<io.nop.stream.flow.model.StreamEnvironmentModel> value){
        checkAllowChange();
        
        this._environments = KeyedList.fromList(value, io.nop.stream.flow.model.StreamEnvironmentModel::getName);
           
    }

    
    public io.nop.stream.flow.model.StreamEnvironmentModel getEnvironment(String name){
        return this._environments.getByKey(name);
    }

    public boolean hasEnvironment(String name){
        return this._environments.containsKey(name);
    }

    public void addEnvironment(io.nop.stream.flow.model.StreamEnvironmentModel item) {
        checkAllowChange();
        java.util.List<io.nop.stream.flow.model.StreamEnvironmentModel> list = this.getEnvironments();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.stream.flow.model.StreamEnvironmentModel::getName);
            setEnvironments(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_environments(){
        return this._environments.keySet();
    }

    public boolean hasEnvironments(){
        return !this._environments.isEmpty();
    }
    
    /**
     * 
     * xml name: joins
     *  WI8d: 参数化 join 注册表——entry 是纯声明描述符（joinType + 双键集 +
     * 可选窗口策略），稳定 ID 供 <join joinRef> 引用；运行时求值由 WI13 的
     * buildJoin 承接，本声明面只承载构建期校验。多 key 用逗号分隔。timeout
     * 为窗口 join 的超时窗口（duration 格式），仅与 windowStrategyRef 同用。
     */
    
    public java.util.List<io.nop.stream.flow.model.StreamJoinSpecModel> getJoins(){
      return _joins;
    }

    
    public void setJoins(java.util.List<io.nop.stream.flow.model.StreamJoinSpecModel> value){
        checkAllowChange();
        
        this._joins = KeyedList.fromList(value, io.nop.stream.flow.model.StreamJoinSpecModel::getJoinId);
           
    }

    
    public io.nop.stream.flow.model.StreamJoinSpecModel getJoinSpec(String name){
        return this._joins.getByKey(name);
    }

    public boolean hasJoinSpec(String name){
        return this._joins.containsKey(name);
    }

    public void addJoinSpec(io.nop.stream.flow.model.StreamJoinSpecModel item) {
        checkAllowChange();
        java.util.List<io.nop.stream.flow.model.StreamJoinSpecModel> list = this.getJoins();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.stream.flow.model.StreamJoinSpecModel::getJoinId);
            setJoins(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_joins(){
        return this._joins.keySet();
    }

    public boolean hasJoins(){
        return !this._joins.isEmpty();
    }
    
    /**
     * 
     * xml name: name
     *  
     */
    
    public java.lang.String getName(){
      return _name;
    }

    
    public void setName(java.lang.String value){
        checkAllowChange();
        
        this._name = value;
           
    }

    
    /**
     * 
     * xml name: onEnd
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getOnEnd(){
      return _onEnd;
    }

    
    public void setOnEnd(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._onEnd = value;
           
    }

    
    /**
     * 
     * xml name: onError
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getOnError(){
      return _onError;
    }

    
    public void setOnError(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._onError = value;
           
    }

    
    /**
     * 
     * xml name: onStart
     *  ==================== 生命周期回调 ====================
     */
    
    public io.nop.core.lang.eval.IEvalFunction getOnStart(){
      return _onStart;
    }

    
    public void setOnStart(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._onStart = value;
           
    }

    
    /**
     * 
     * xml name: parallelism
     *  
     */
    
    public int getParallelism(){
      return _parallelism;
    }

    
    public void setParallelism(int value){
        checkAllowChange();
        
        this._parallelism = value;
           
    }

    
    /**
     * 
     * xml name: patterns
     *  ==================== CEP 模式定义（引用 pattern.xdef） ====================
     */
    
    public java.util.List<io.nop.stream.cep.model.CepPatternModel> getPatterns(){
      return _patterns;
    }

    
    public void setPatterns(java.util.List<io.nop.stream.cep.model.CepPatternModel> value){
        checkAllowChange();
        
        this._patterns = KeyedList.fromList(value, io.nop.stream.cep.model.CepPatternModel::getName);
           
    }

    
    public io.nop.stream.cep.model.CepPatternModel getPattern(String name){
        return this._patterns.getByKey(name);
    }

    public boolean hasPattern(String name){
        return this._patterns.containsKey(name);
    }

    public void addPattern(io.nop.stream.cep.model.CepPatternModel item) {
        checkAllowChange();
        java.util.List<io.nop.stream.cep.model.CepPatternModel> list = this.getPatterns();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.stream.cep.model.CepPatternModel::getName);
            setPatterns(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_patterns(){
        return this._patterns.keySet();
    }

    public boolean hasPatterns(){
        return !this._patterns.isEmpty();
    }
    
    /**
     * 
     * xml name: requirements
     *  需求声明：运行时必须满足的能力集合
     */
    
    public java.util.List<io.nop.stream.flow.model.StreamRequirementModel> getRequirements(){
      return _requirements;
    }

    
    public void setRequirements(java.util.List<io.nop.stream.flow.model.StreamRequirementModel> value){
        checkAllowChange();
        
        this._requirements = value;
           
    }

    
    /**
     * 
     * xml name: schemas
     *  Schema 注册表
     */
    
    public java.util.List<io.nop.stream.flow.model.StreamSchemaModel> getSchemas(){
      return _schemas;
    }

    
    public void setSchemas(java.util.List<io.nop.stream.flow.model.StreamSchemaModel> value){
        checkAllowChange();
        
        this._schemas = KeyedList.fromList(value, io.nop.stream.flow.model.StreamSchemaModel::getId);
           
    }

    
    public io.nop.stream.flow.model.StreamSchemaModel getSchema(String name){
        return this._schemas.getByKey(name);
    }

    public boolean hasSchema(String name){
        return this._schemas.containsKey(name);
    }

    public void addSchema(io.nop.stream.flow.model.StreamSchemaModel item) {
        checkAllowChange();
        java.util.List<io.nop.stream.flow.model.StreamSchemaModel> list = this.getSchemas();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.stream.flow.model.StreamSchemaModel::getId);
            setSchemas(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_schemas(){
        return this._schemas.keySet();
    }

    public boolean hasSchemas(){
        return !this._schemas.isEmpty();
    }
    
    /**
     * 
     * xml name: sideInputs
     *  ==================== 侧输入（Side Input，流间引用） ====================
     */
    
    public java.util.List<io.nop.stream.flow.model.StreamSideInputModel> getSideInputs(){
      return _sideInputs;
    }

    
    public void setSideInputs(java.util.List<io.nop.stream.flow.model.StreamSideInputModel> value){
        checkAllowChange();
        
        this._sideInputs = KeyedList.fromList(value, io.nop.stream.flow.model.StreamSideInputModel::getId);
           
    }

    
    public io.nop.stream.flow.model.StreamSideInputModel getSideInput(String name){
        return this._sideInputs.getByKey(name);
    }

    public boolean hasSideInput(String name){
        return this._sideInputs.containsKey(name);
    }

    public void addSideInput(io.nop.stream.flow.model.StreamSideInputModel item) {
        checkAllowChange();
        java.util.List<io.nop.stream.flow.model.StreamSideInputModel> list = this.getSideInputs();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.stream.flow.model.StreamSideInputModel::getId);
            setSideInputs(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_sideInputs(){
        return this._sideInputs.keySet();
    }

    public boolean hasSideInputs(){
        return !this._sideInputs.isEmpty();
    }
    
    /**
     * 
     * xml name: sql
     *  WI17 (plan 25 r2 B4): <sql> 是模型级生成器而非 transform——顶层元素，编译发生在
     * builder.build() 的 buildTransforms 之前：SPI（ISqlStreamCompiler，nop-stream-flow
     * 定义，nop-stream-sql 提供实现）把 SQL 文本编译为完整流模型 XML，builder 解析回读并
     * 替换父模型内容。<sql> 必须是模型唯一内容（与其他 transforms/edges/registries 并存
     * → fail-fast）。schema 为 SQL 面自带声明（D7 受管类型名九名闭集）；sinkBean 由编译器
     * 追加 sink 声明与边（产物自洽可执行）。
     */
    
    public io.nop.stream.flow.model.StreamSqlModel getSql(){
      return _sql;
    }

    
    public void setSql(io.nop.stream.flow.model.StreamSqlModel value){
        checkAllowChange();
        
        this._sql = value;
           
    }

    
    /**
     * 
     * xml name: streams
     *  流定义注册表（命名流，用于跨算子引用）
     */
    
    public java.util.List<io.nop.stream.flow.model.StreamDefinitionModel> getStreams(){
      return _streams;
    }

    
    public void setStreams(java.util.List<io.nop.stream.flow.model.StreamDefinitionModel> value){
        checkAllowChange();
        
        this._streams = KeyedList.fromList(value, io.nop.stream.flow.model.StreamDefinitionModel::getId);
           
    }

    
    public io.nop.stream.flow.model.StreamDefinitionModel getStream(String name){
        return this._streams.getByKey(name);
    }

    public boolean hasStream(String name){
        return this._streams.containsKey(name);
    }

    public void addStream(io.nop.stream.flow.model.StreamDefinitionModel item) {
        checkAllowChange();
        java.util.List<io.nop.stream.flow.model.StreamDefinitionModel> list = this.getStreams();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.stream.flow.model.StreamDefinitionModel::getId);
            setStreams(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_streams(){
        return this._streams.keySet();
    }

    public boolean hasStreams(){
        return !this._streams.isEmpty();
    }
    
    /**
     * 
     * xml name: transforms
     *  
     */
    
    public java.util.List<io.nop.stream.flow.model.StreamTransformModel> getTransforms(){
      return _transforms;
    }

    
    public void setTransforms(java.util.List<io.nop.stream.flow.model.StreamTransformModel> value){
        checkAllowChange();
        
        this._transforms = KeyedList.fromList(value, io.nop.stream.flow.model.StreamTransformModel::getId);
           
    }

    
    public io.nop.stream.flow.model.StreamTransformModel getTransform(String name){
        return this._transforms.getByKey(name);
    }

    public boolean hasTransform(String name){
        return this._transforms.containsKey(name);
    }

    public void addTransform(io.nop.stream.flow.model.StreamTransformModel item) {
        checkAllowChange();
        java.util.List<io.nop.stream.flow.model.StreamTransformModel> list = this.getTransforms();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.stream.flow.model.StreamTransformModel::getId);
            setTransforms(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_transforms(){
        return this._transforms.keySet();
    }

    public boolean hasTransforms(){
        return !this._transforms.isEmpty();
    }
    
    /**
     * 
     * xml name: version
     *  
     */
    
    public long getVersion(){
      return _version;
    }

    
    public void setVersion(long value){
        checkAllowChange();
        
        this._version = value;
           
    }

    
    /**
     * 
     * xml name: watermarkInterval
     *  
     */
    
    public long getWatermarkInterval(){
      return _watermarkInterval;
    }

    
    public void setWatermarkInterval(long value){
        checkAllowChange();
        
        this._watermarkInterval = value;
           
    }

    
    /**
     * 
     * xml name: windowingStrategies
     *  窗口策略注册表
     */
    
    public java.util.List<io.nop.stream.flow.model.WindowingStrategyModel> getWindowingStrategies(){
      return _windowingStrategies;
    }

    
    public void setWindowingStrategies(java.util.List<io.nop.stream.flow.model.WindowingStrategyModel> value){
        checkAllowChange();
        
        this._windowingStrategies = KeyedList.fromList(value, io.nop.stream.flow.model.WindowingStrategyModel::getStrategyId);
           
    }

    
    public io.nop.stream.flow.model.WindowingStrategyModel getStrategy(String name){
        return this._windowingStrategies.getByKey(name);
    }

    public boolean hasStrategy(String name){
        return this._windowingStrategies.containsKey(name);
    }

    public void addStrategy(io.nop.stream.flow.model.WindowingStrategyModel item) {
        checkAllowChange();
        java.util.List<io.nop.stream.flow.model.WindowingStrategyModel> list = this.getWindowingStrategies();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.stream.flow.model.WindowingStrategyModel::getStrategyId);
            setWindowingStrategies(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_windowingStrategies(){
        return this._windowingStrategies.keySet();
    }

    public boolean hasWindowingStrategies(){
        return !this._windowingStrategies.isEmpty();
    }
    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._aggregators = io.nop.api.core.util.FreezeHelper.deepFreeze(this._aggregators);
            
           this._checkpoint = io.nop.api.core.util.FreezeHelper.deepFreeze(this._checkpoint);
            
           this._checkpointParticipants = io.nop.api.core.util.FreezeHelper.deepFreeze(this._checkpointParticipants);
            
           this._coders = io.nop.api.core.util.FreezeHelper.deepFreeze(this._coders);
            
           this._edges = io.nop.api.core.util.FreezeHelper.deepFreeze(this._edges);
            
           this._environments = io.nop.api.core.util.FreezeHelper.deepFreeze(this._environments);
            
           this._joins = io.nop.api.core.util.FreezeHelper.deepFreeze(this._joins);
            
           this._patterns = io.nop.api.core.util.FreezeHelper.deepFreeze(this._patterns);
            
           this._requirements = io.nop.api.core.util.FreezeHelper.deepFreeze(this._requirements);
            
           this._schemas = io.nop.api.core.util.FreezeHelper.deepFreeze(this._schemas);
            
           this._sideInputs = io.nop.api.core.util.FreezeHelper.deepFreeze(this._sideInputs);
            
           this._sql = io.nop.api.core.util.FreezeHelper.deepFreeze(this._sql);
            
           this._streams = io.nop.api.core.util.FreezeHelper.deepFreeze(this._streams);
            
           this._transforms = io.nop.api.core.util.FreezeHelper.deepFreeze(this._transforms);
            
           this._windowingStrategies = io.nop.api.core.util.FreezeHelper.deepFreeze(this._windowingStrategies);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("aggregators",this.getAggregators());
        out.putNotNull("checkpoint",this.getCheckpoint());
        out.putNotNull("checkpointParticipants",this.getCheckpointParticipants());
        out.putNotNull("coders",this.getCoders());
        out.putNotNull("edges",this.getEdges());
        out.putNotNull("environments",this.getEnvironments());
        out.putNotNull("joins",this.getJoins());
        out.putNotNull("name",this.getName());
        out.putNotNull("onEnd",this.getOnEnd());
        out.putNotNull("onError",this.getOnError());
        out.putNotNull("onStart",this.getOnStart());
        out.putNotNull("parallelism",this.getParallelism());
        out.putNotNull("patterns",this.getPatterns());
        out.putNotNull("requirements",this.getRequirements());
        out.putNotNull("schemas",this.getSchemas());
        out.putNotNull("sideInputs",this.getSideInputs());
        out.putNotNull("sql",this.getSql());
        out.putNotNull("streams",this.getStreams());
        out.putNotNull("transforms",this.getTransforms());
        out.putNotNull("version",this.getVersion());
        out.putNotNull("watermarkInterval",this.getWatermarkInterval());
        out.putNotNull("windowingStrategies",this.getWindowingStrategies());
    }

    public StreamModel cloneInstance(){
        StreamModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(StreamModel instance){
        super.copyTo(instance);
        
        instance.setAggregators(this.getAggregators());
        instance.setCheckpoint(this.getCheckpoint());
        instance.setCheckpointParticipants(this.getCheckpointParticipants());
        instance.setCoders(this.getCoders());
        instance.setEdges(this.getEdges());
        instance.setEnvironments(this.getEnvironments());
        instance.setJoins(this.getJoins());
        instance.setName(this.getName());
        instance.setOnEnd(this.getOnEnd());
        instance.setOnError(this.getOnError());
        instance.setOnStart(this.getOnStart());
        instance.setParallelism(this.getParallelism());
        instance.setPatterns(this.getPatterns());
        instance.setRequirements(this.getRequirements());
        instance.setSchemas(this.getSchemas());
        instance.setSideInputs(this.getSideInputs());
        instance.setSql(this.getSql());
        instance.setStreams(this.getStreams());
        instance.setTransforms(this.getTransforms());
        instance.setVersion(this.getVersion());
        instance.setWatermarkInterval(this.getWatermarkInterval());
        instance.setWindowingStrategies(this.getWindowingStrategies());
    }

    protected StreamModel newInstance(){
        return (StreamModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON

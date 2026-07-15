package io.nop.stream.flow.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.stream.flow.model.CheckpointConfigModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from file:/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef <p>
 * Checkpoint 配置
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _CheckpointConfigModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: barrierAlignmentTimeout
     * 
     */
    private long _barrierAlignmentTimeout  = 30000;
    
    /**
     *  
     * xml name: enabled
     * 
     */
    private boolean _enabled  = true;
    
    /**
     *  
     * xml name: interval
     * 
     */
    private long _interval  = 60000;
    
    /**
     *  
     * xml name: jobId
     * 
     */
    private java.lang.String _jobId ;
    
    /**
     *  
     * xml name: jobTerminationMode
     * 
     */
    private io.nop.stream.core.checkpoint.JobTerminationMode _jobTerminationMode ;
    
    /**
     *  
     * xml name: maxConcurrentCheckpoints
     * 
     */
    private int _maxConcurrentCheckpoints  = 1;
    
    /**
     *  
     * xml name: maxConsecutiveCheckpointFailures
     * 
     */
    private int _maxConsecutiveCheckpointFailures  = 3;
    
    /**
     *  
     * xml name: maxRetainedCheckpoints
     * 
     */
    private int _maxRetainedCheckpoints  = 5;
    
    /**
     *  
     * xml name: minPause
     * 
     */
    private long _minPause  = 500;
    
    /**
     *  
     * xml name: pipelineId
     * 
     */
    private java.lang.String _pipelineId  = 1;
    
    /**
     *  
     * xml name: processingGuarantee
     * 
     */
    private io.nop.stream.core.checkpoint.ProcessingGuarantee _processingGuarantee ;
    
    /**
     *  
     * xml name: storageConfig
     * 
     */
    private KeyedList<io.nop.stream.flow.model.StorageConfigEntryModel> _storageConfig = KeyedList.emptyList();
    
    /**
     *  
     * xml name: storageType
     * 
     */
    private java.lang.String _storageType  = "local";
    
    /**
     *  
     * xml name: timeout
     * 
     */
    private long _timeout  = 600000;
    
    /**
     * 
     * xml name: barrierAlignmentTimeout
     *  
     */
    
    public long getBarrierAlignmentTimeout(){
      return _barrierAlignmentTimeout;
    }

    
    public void setBarrierAlignmentTimeout(long value){
        checkAllowChange();
        
        this._barrierAlignmentTimeout = value;
           
    }

    
    /**
     * 
     * xml name: enabled
     *  
     */
    
    public boolean isEnabled(){
      return _enabled;
    }

    
    public void setEnabled(boolean value){
        checkAllowChange();
        
        this._enabled = value;
           
    }

    
    /**
     * 
     * xml name: interval
     *  
     */
    
    public long getInterval(){
      return _interval;
    }

    
    public void setInterval(long value){
        checkAllowChange();
        
        this._interval = value;
           
    }

    
    /**
     * 
     * xml name: jobId
     *  
     */
    
    public java.lang.String getJobId(){
      return _jobId;
    }

    
    public void setJobId(java.lang.String value){
        checkAllowChange();
        
        this._jobId = value;
           
    }

    
    /**
     * 
     * xml name: jobTerminationMode
     *  
     */
    
    public io.nop.stream.core.checkpoint.JobTerminationMode getJobTerminationMode(){
      return _jobTerminationMode;
    }

    
    public void setJobTerminationMode(io.nop.stream.core.checkpoint.JobTerminationMode value){
        checkAllowChange();
        
        this._jobTerminationMode = value;
           
    }

    
    /**
     * 
     * xml name: maxConcurrentCheckpoints
     *  
     */
    
    public int getMaxConcurrentCheckpoints(){
      return _maxConcurrentCheckpoints;
    }

    
    public void setMaxConcurrentCheckpoints(int value){
        checkAllowChange();
        
        this._maxConcurrentCheckpoints = value;
           
    }

    
    /**
     * 
     * xml name: maxConsecutiveCheckpointFailures
     *  
     */
    
    public int getMaxConsecutiveCheckpointFailures(){
      return _maxConsecutiveCheckpointFailures;
    }

    
    public void setMaxConsecutiveCheckpointFailures(int value){
        checkAllowChange();
        
        this._maxConsecutiveCheckpointFailures = value;
           
    }

    
    /**
     * 
     * xml name: maxRetainedCheckpoints
     *  
     */
    
    public int getMaxRetainedCheckpoints(){
      return _maxRetainedCheckpoints;
    }

    
    public void setMaxRetainedCheckpoints(int value){
        checkAllowChange();
        
        this._maxRetainedCheckpoints = value;
           
    }

    
    /**
     * 
     * xml name: minPause
     *  
     */
    
    public long getMinPause(){
      return _minPause;
    }

    
    public void setMinPause(long value){
        checkAllowChange();
        
        this._minPause = value;
           
    }

    
    /**
     * 
     * xml name: pipelineId
     *  
     */
    
    public java.lang.String getPipelineId(){
      return _pipelineId;
    }

    
    public void setPipelineId(java.lang.String value){
        checkAllowChange();
        
        this._pipelineId = value;
           
    }

    
    /**
     * 
     * xml name: processingGuarantee
     *  
     */
    
    public io.nop.stream.core.checkpoint.ProcessingGuarantee getProcessingGuarantee(){
      return _processingGuarantee;
    }

    
    public void setProcessingGuarantee(io.nop.stream.core.checkpoint.ProcessingGuarantee value){
        checkAllowChange();
        
        this._processingGuarantee = value;
           
    }

    
    /**
     * 
     * xml name: storageConfig
     *  
     */
    
    public java.util.List<io.nop.stream.flow.model.StorageConfigEntryModel> getStorageConfig(){
      return _storageConfig;
    }

    
    public void setStorageConfig(java.util.List<io.nop.stream.flow.model.StorageConfigEntryModel> value){
        checkAllowChange();
        
        this._storageConfig = KeyedList.fromList(value, io.nop.stream.flow.model.StorageConfigEntryModel::getKey);
           
    }

    
    public io.nop.stream.flow.model.StorageConfigEntryModel getEntry(String name){
        return this._storageConfig.getByKey(name);
    }

    public boolean hasEntry(String name){
        return this._storageConfig.containsKey(name);
    }

    public void addEntry(io.nop.stream.flow.model.StorageConfigEntryModel item) {
        checkAllowChange();
        java.util.List<io.nop.stream.flow.model.StorageConfigEntryModel> list = this.getStorageConfig();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.stream.flow.model.StorageConfigEntryModel::getKey);
            setStorageConfig(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_storageConfig(){
        return this._storageConfig.keySet();
    }

    public boolean hasStorageConfig(){
        return !this._storageConfig.isEmpty();
    }
    
    /**
     * 
     * xml name: storageType
     *  
     */
    
    public java.lang.String getStorageType(){
      return _storageType;
    }

    
    public void setStorageType(java.lang.String value){
        checkAllowChange();
        
        this._storageType = value;
           
    }

    
    /**
     * 
     * xml name: timeout
     *  
     */
    
    public long getTimeout(){
      return _timeout;
    }

    
    public void setTimeout(long value){
        checkAllowChange();
        
        this._timeout = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._storageConfig = io.nop.api.core.util.FreezeHelper.deepFreeze(this._storageConfig);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("barrierAlignmentTimeout",this.getBarrierAlignmentTimeout());
        out.putNotNull("enabled",this.isEnabled());
        out.putNotNull("interval",this.getInterval());
        out.putNotNull("jobId",this.getJobId());
        out.putNotNull("jobTerminationMode",this.getJobTerminationMode());
        out.putNotNull("maxConcurrentCheckpoints",this.getMaxConcurrentCheckpoints());
        out.putNotNull("maxConsecutiveCheckpointFailures",this.getMaxConsecutiveCheckpointFailures());
        out.putNotNull("maxRetainedCheckpoints",this.getMaxRetainedCheckpoints());
        out.putNotNull("minPause",this.getMinPause());
        out.putNotNull("pipelineId",this.getPipelineId());
        out.putNotNull("processingGuarantee",this.getProcessingGuarantee());
        out.putNotNull("storageConfig",this.getStorageConfig());
        out.putNotNull("storageType",this.getStorageType());
        out.putNotNull("timeout",this.getTimeout());
    }

    public CheckpointConfigModel cloneInstance(){
        CheckpointConfigModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(CheckpointConfigModel instance){
        super.copyTo(instance);
        
        instance.setBarrierAlignmentTimeout(this.getBarrierAlignmentTimeout());
        instance.setEnabled(this.isEnabled());
        instance.setInterval(this.getInterval());
        instance.setJobId(this.getJobId());
        instance.setJobTerminationMode(this.getJobTerminationMode());
        instance.setMaxConcurrentCheckpoints(this.getMaxConcurrentCheckpoints());
        instance.setMaxConsecutiveCheckpointFailures(this.getMaxConsecutiveCheckpointFailures());
        instance.setMaxRetainedCheckpoints(this.getMaxRetainedCheckpoints());
        instance.setMinPause(this.getMinPause());
        instance.setPipelineId(this.getPipelineId());
        instance.setProcessingGuarantee(this.getProcessingGuarantee());
        instance.setStorageConfig(this.getStorageConfig());
        instance.setStorageType(this.getStorageType());
        instance.setTimeout(this.getTimeout());
    }

    protected CheckpointConfigModel newInstance(){
        return (CheckpointConfigModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON

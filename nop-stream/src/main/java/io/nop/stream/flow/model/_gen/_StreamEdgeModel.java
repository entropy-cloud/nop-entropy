package io.nop.stream.flow.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.stream.flow.model.StreamEdgeModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from file:/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _StreamEdgeModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: flowControlPolicy
     * 
     */
    private io.nop.stream.core.execution.flow.FlowControlPolicy _flowControlPolicy ;
    
    /**
     *  
     * xml name: from
     * 
     */
    private java.lang.String _from ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: keyExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _keyExpr ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: packetSize
     * 
     */
    private java.lang.Integer _packetSize ;
    
    /**
     *  
     * xml name: partition
     * 
     */
    private io.nop.stream.core.execution.plan.PartitionPolicy _partition ;
    
    /**
     *  
     * xml name: queueCapacity
     * 
     */
    private java.lang.Integer _queueCapacity ;
    
    /**
     *  
     * xml name: receiveWindow
     * 
     */
    private java.lang.Integer _receiveWindow ;
    
    /**
     *  
     * xml name: to
     * 
     */
    private java.lang.String _to ;
    
    /**
     * 
     * xml name: flowControlPolicy
     *  
     */
    
    public io.nop.stream.core.execution.flow.FlowControlPolicy getFlowControlPolicy(){
      return _flowControlPolicy;
    }

    
    public void setFlowControlPolicy(io.nop.stream.core.execution.flow.FlowControlPolicy value){
        checkAllowChange();
        
        this._flowControlPolicy = value;
           
    }

    
    /**
     * 
     * xml name: from
     *  
     */
    
    public java.lang.String getFrom(){
      return _from;
    }

    
    public void setFrom(java.lang.String value){
        checkAllowChange();
        
        this._from = value;
           
    }

    
    /**
     * 
     * xml name: id
     *  
     */
    
    public java.lang.String getId(){
      return _id;
    }

    
    public void setId(java.lang.String value){
        checkAllowChange();
        
        this._id = value;
           
    }

    
    /**
     * 
     * xml name: keyExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getKeyExpr(){
      return _keyExpr;
    }

    
    public void setKeyExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._keyExpr = value;
           
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
     * xml name: packetSize
     *  
     */
    
    public java.lang.Integer getPacketSize(){
      return _packetSize;
    }

    
    public void setPacketSize(java.lang.Integer value){
        checkAllowChange();
        
        this._packetSize = value;
           
    }

    
    /**
     * 
     * xml name: partition
     *  
     */
    
    public io.nop.stream.core.execution.plan.PartitionPolicy getPartition(){
      return _partition;
    }

    
    public void setPartition(io.nop.stream.core.execution.plan.PartitionPolicy value){
        checkAllowChange();
        
        this._partition = value;
           
    }

    
    /**
     * 
     * xml name: queueCapacity
     *  
     */
    
    public java.lang.Integer getQueueCapacity(){
      return _queueCapacity;
    }

    
    public void setQueueCapacity(java.lang.Integer value){
        checkAllowChange();
        
        this._queueCapacity = value;
           
    }

    
    /**
     * 
     * xml name: receiveWindow
     *  
     */
    
    public java.lang.Integer getReceiveWindow(){
      return _receiveWindow;
    }

    
    public void setReceiveWindow(java.lang.Integer value){
        checkAllowChange();
        
        this._receiveWindow = value;
           
    }

    
    /**
     * 
     * xml name: to
     *  
     */
    
    public java.lang.String getTo(){
      return _to;
    }

    
    public void setTo(java.lang.String value){
        checkAllowChange();
        
        this._to = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("flowControlPolicy",this.getFlowControlPolicy());
        out.putNotNull("from",this.getFrom());
        out.putNotNull("id",this.getId());
        out.putNotNull("keyExpr",this.getKeyExpr());
        out.putNotNull("name",this.getName());
        out.putNotNull("packetSize",this.getPacketSize());
        out.putNotNull("partition",this.getPartition());
        out.putNotNull("queueCapacity",this.getQueueCapacity());
        out.putNotNull("receiveWindow",this.getReceiveWindow());
        out.putNotNull("to",this.getTo());
    }

    public StreamEdgeModel cloneInstance(){
        StreamEdgeModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(StreamEdgeModel instance){
        super.copyTo(instance);
        
        instance.setFlowControlPolicy(this.getFlowControlPolicy());
        instance.setFrom(this.getFrom());
        instance.setId(this.getId());
        instance.setKeyExpr(this.getKeyExpr());
        instance.setName(this.getName());
        instance.setPacketSize(this.getPacketSize());
        instance.setPartition(this.getPartition());
        instance.setQueueCapacity(this.getQueueCapacity());
        instance.setReceiveWindow(this.getReceiveWindow());
        instance.setTo(this.getTo());
    }

    protected StreamEdgeModel newInstance(){
        return (StreamEdgeModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON

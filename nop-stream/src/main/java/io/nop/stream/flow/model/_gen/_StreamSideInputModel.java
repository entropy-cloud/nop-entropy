package io.nop.stream.flow.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.stream.flow.model.StreamSideInputModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from file:/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _StreamSideInputModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: broadcast
     * 
     */
    private boolean _broadcast  = false;
    
    /**
     *  
     * xml name: description
     * 
     */
    private java.lang.String _description ;
    
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
     * xml name: to
     * 
     */
    private java.lang.String _to ;
    
    /**
     * 
     * xml name: broadcast
     *  
     */
    
    public boolean isBroadcast(){
      return _broadcast;
    }

    
    public void setBroadcast(boolean value){
        checkAllowChange();
        
        this._broadcast = value;
           
    }

    
    /**
     * 
     * xml name: description
     *  
     */
    
    public java.lang.String getDescription(){
      return _description;
    }

    
    public void setDescription(java.lang.String value){
        checkAllowChange();
        
        this._description = value;
           
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
        
        out.putNotNull("broadcast",this.isBroadcast());
        out.putNotNull("description",this.getDescription());
        out.putNotNull("from",this.getFrom());
        out.putNotNull("id",this.getId());
        out.putNotNull("to",this.getTo());
    }

    public StreamSideInputModel cloneInstance(){
        StreamSideInputModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(StreamSideInputModel instance){
        super.copyTo(instance);
        
        instance.setBroadcast(this.isBroadcast());
        instance.setDescription(this.getDescription());
        instance.setFrom(this.getFrom());
        instance.setId(this.getId());
        instance.setTo(this.getTo());
    }

    protected StreamSideInputModel newInstance(){
        return (StreamSideInputModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON

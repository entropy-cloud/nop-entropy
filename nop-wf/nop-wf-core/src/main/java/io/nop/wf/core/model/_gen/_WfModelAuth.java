package io.nop.wf.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.wf.core.model.WfModelAuth;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [73:10:0:0]/nop/schema/wf/wf.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _WfModelAuth extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: actorId
     * 
     */
    private java.lang.String _actorId ;
    
    /**
     *  
     * xml name: actorType
     * 
     */
    private java.lang.String _actorType ;
    
    /**
     *  
     * xml name: allowEdit
     * 
     */
    private boolean _allowEdit  = false;
    
    /**
     *  
     * xml name: allowManage
     * 
     */
    private boolean _allowManage  = false;
    
    /**
     *  
     * xml name: allowStart
     * 
     */
    private boolean _allowStart  = false;
    
    /**
     *  
     * xml name: deptId
     * 
     */
    private java.lang.String _deptId ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     * 
     * xml name: actorId
     *  
     */
    
    public java.lang.String getActorId(){
      return _actorId;
    }

    
    public void setActorId(java.lang.String value){
        checkAllowChange();
        
        this._actorId = value;
           
    }

    
    /**
     * 
     * xml name: actorType
     *  
     */
    
    public java.lang.String getActorType(){
      return _actorType;
    }

    
    public void setActorType(java.lang.String value){
        checkAllowChange();
        
        this._actorType = value;
           
    }

    
    /**
     * 
     * xml name: allowEdit
     *  
     */
    
    public boolean isAllowEdit(){
      return _allowEdit;
    }

    
    public void setAllowEdit(boolean value){
        checkAllowChange();
        
        this._allowEdit = value;
           
    }

    
    /**
     * 
     * xml name: allowManage
     *  
     */
    
    public boolean isAllowManage(){
      return _allowManage;
    }

    
    public void setAllowManage(boolean value){
        checkAllowChange();
        
        this._allowManage = value;
           
    }

    
    /**
     * 
     * xml name: allowStart
     *  
     */
    
    public boolean isAllowStart(){
      return _allowStart;
    }

    
    public void setAllowStart(boolean value){
        checkAllowChange();
        
        this._allowStart = value;
           
    }

    
    /**
     * 
     * xml name: deptId
     *  
     */
    
    public java.lang.String getDeptId(){
      return _deptId;
    }

    
    public void setDeptId(java.lang.String value){
        checkAllowChange();
        
        this._deptId = value;
           
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
        
        out.putNotNull("actorId",this.getActorId());
        out.putNotNull("actorType",this.getActorType());
        out.putNotNull("allowEdit",this.isAllowEdit());
        out.putNotNull("allowManage",this.isAllowManage());
        out.putNotNull("allowStart",this.isAllowStart());
        out.putNotNull("deptId",this.getDeptId());
        out.putNotNull("id",this.getId());
    }

    public WfModelAuth cloneInstance(){
        WfModelAuth instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(WfModelAuth instance){
        super.copyTo(instance);
        
        instance.setActorId(this.getActorId());
        instance.setActorType(this.getActorType());
        instance.setAllowEdit(this.isAllowEdit());
        instance.setAllowManage(this.isAllowManage());
        instance.setAllowStart(this.isAllowStart());
        instance.setDeptId(this.getDeptId());
        instance.setId(this.getId());
    }

    protected WfModelAuth newInstance(){
        return (WfModelAuth) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON

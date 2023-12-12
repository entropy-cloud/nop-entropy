package io.nop.wf.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [73:10:0:0]/nop/schema/wf/wf.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101"})
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
        
        out.put("actorId",this.getActorId());
        out.put("actorType",this.getActorType());
        out.put("allowEdit",this.isAllowEdit());
        out.put("allowManage",this.isAllowManage());
        out.put("allowStart",this.isAllowStart());
        out.put("deptId",this.getDeptId());
        out.put("id",this.getId());
    }
}
 // resume CPD analysis - CPD-ON

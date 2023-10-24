package io.nop.wf.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [29:10:0:0]/nop/schema/wf/assignment.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _WfAssignmentActorModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: actorId
     * 
     */
    private java.lang.String _actorId ;
    
    /**
     *  
     * xml name: actorType
     * actor类型，如果为dynamic, 则表示这是一个动态构造标签，它的具体参数由body段的xml来指定。
     */
    private java.lang.String _actorType ;
    
    /**
     *  
     * xml name: assignForUser
     * 是否为actor中的每个用户生成步骤实例
     */
    private boolean _assignForUser  = false;
    
    /**
     *  
     * xml name: deptId
     * 
     */
    private java.lang.String _deptId ;
    
    /**
     *  
     * xml name: selectUser
     * 选择actor本身还是actor对应的user
     */
    private boolean _selectUser  = true;
    
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
     *  actor类型，如果为dynamic, 则表示这是一个动态构造标签，它的具体参数由body段的xml来指定。
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
     * xml name: assignForUser
     *  是否为actor中的每个用户生成步骤实例
     */
    
    public boolean isAssignForUser(){
      return _assignForUser;
    }

    
    public void setAssignForUser(boolean value){
        checkAllowChange();
        
        this._assignForUser = value;
           
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
     * xml name: selectUser
     *  选择actor本身还是actor对应的user
     */
    
    public boolean isSelectUser(){
      return _selectUser;
    }

    
    public void setSelectUser(boolean value){
        checkAllowChange();
        
        this._selectUser = value;
           
    }

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("actorId",this.getActorId());
        out.put("actorType",this.getActorType());
        out.put("assignForUser",this.isAssignForUser());
        out.put("deptId",this.getDeptId());
        out.put("selectUser",this.isSelectUser());
    }
}
 // resume CPD analysis - CPD-ON

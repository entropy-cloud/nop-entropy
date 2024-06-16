package io.nop.wf.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.wf.core.model.WfAssignmentActorModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/wf/assignment.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _WfAssignmentActorModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: actorId
     * 
     */
    private java.lang.String _actorId ;
    
    /**
     *  
     * xml name: actorModelId
     * 
     */
    private java.lang.String _actorModelId ;
    
    /**
     *  
     * xml name: actorType
     * actor类型，如果包含名字空间，则对应于标签库中定义的动态Actor
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
     *  投票权重
     * xml name: voteWeight
     * 
     */
    private int _voteWeight  = 1;
    
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
     * xml name: actorModelId
     *  
     */
    
    public java.lang.String getActorModelId(){
      return _actorModelId;
    }

    
    public void setActorModelId(java.lang.String value){
        checkAllowChange();
        
        this._actorModelId = value;
           
    }

    
    /**
     * 
     * xml name: actorType
     *  actor类型，如果包含名字空间，则对应于标签库中定义的动态Actor
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

    
    /**
     * 投票权重
     * xml name: voteWeight
     *  
     */
    
    public int getVoteWeight(){
      return _voteWeight;
    }

    
    public void setVoteWeight(int value){
        checkAllowChange();
        
        this._voteWeight = value;
           
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
        out.putNotNull("actorModelId",this.getActorModelId());
        out.putNotNull("actorType",this.getActorType());
        out.putNotNull("assignForUser",this.isAssignForUser());
        out.putNotNull("deptId",this.getDeptId());
        out.putNotNull("selectUser",this.isSelectUser());
        out.putNotNull("voteWeight",this.getVoteWeight());
    }

    public WfAssignmentActorModel cloneInstance(){
        WfAssignmentActorModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(WfAssignmentActorModel instance){
        super.copyTo(instance);
        
        instance.setActorId(this.getActorId());
        instance.setActorModelId(this.getActorModelId());
        instance.setActorType(this.getActorType());
        instance.setAssignForUser(this.isAssignForUser());
        instance.setDeptId(this.getDeptId());
        instance.setSelectUser(this.isSelectUser());
        instance.setVoteWeight(this.getVoteWeight());
    }

    protected WfAssignmentActorModel newInstance(){
        return (WfAssignmentActorModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON

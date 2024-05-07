package io.nop.xlang.xmeta.impl._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.xlang.xmeta.impl.ObjTreeModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [44:6:0:0]/nop/schema/xmeta.xdef <p>
 * 树形结构
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ObjTreeModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: childrenProp
     * 对应于父对象中对应于子对象的集合属性，例如children
     */
    private java.lang.String _childrenProp ;
    
    /**
     *  
     * xml name: isLeafProp
     * 
     */
    private java.lang.String _isLeafProp ;
    
    /**
     *  
     * xml name: levelProp
     * 树形结构的级别树形，例如level=1表示一级节点，2表示二级节点等。如果为空，则使用parentId=__null来过滤得到根节点
     */
    private java.lang.String _levelProp ;
    
    /**
     *  
     * xml name: parentProp
     * 对应于parentId等指向父节点的字段
     */
    private java.lang.String _parentProp ;
    
    /**
     *  
     * xml name: rootLevelValue
     * 根节点所对应的level字段的值
     */
    private java.lang.String _rootLevelValue ;
    
    /**
     *  
     * xml name: rootParentValue
     * 
     */
    private java.lang.String _rootParentValue ;
    
    /**
     *  
     * xml name: sortProp
     * 
     */
    private java.lang.String _sortProp ;
    
    /**
     * 
     * xml name: childrenProp
     *  对应于父对象中对应于子对象的集合属性，例如children
     */
    
    public java.lang.String getChildrenProp(){
      return _childrenProp;
    }

    
    public void setChildrenProp(java.lang.String value){
        checkAllowChange();
        
        this._childrenProp = value;
           
    }

    
    /**
     * 
     * xml name: isLeafProp
     *  
     */
    
    public java.lang.String getIsLeafProp(){
      return _isLeafProp;
    }

    
    public void setIsLeafProp(java.lang.String value){
        checkAllowChange();
        
        this._isLeafProp = value;
           
    }

    
    /**
     * 
     * xml name: levelProp
     *  树形结构的级别树形，例如level=1表示一级节点，2表示二级节点等。如果为空，则使用parentId=__null来过滤得到根节点
     */
    
    public java.lang.String getLevelProp(){
      return _levelProp;
    }

    
    public void setLevelProp(java.lang.String value){
        checkAllowChange();
        
        this._levelProp = value;
           
    }

    
    /**
     * 
     * xml name: parentProp
     *  对应于parentId等指向父节点的字段
     */
    
    public java.lang.String getParentProp(){
      return _parentProp;
    }

    
    public void setParentProp(java.lang.String value){
        checkAllowChange();
        
        this._parentProp = value;
           
    }

    
    /**
     * 
     * xml name: rootLevelValue
     *  根节点所对应的level字段的值
     */
    
    public java.lang.String getRootLevelValue(){
      return _rootLevelValue;
    }

    
    public void setRootLevelValue(java.lang.String value){
        checkAllowChange();
        
        this._rootLevelValue = value;
           
    }

    
    /**
     * 
     * xml name: rootParentValue
     *  
     */
    
    public java.lang.String getRootParentValue(){
      return _rootParentValue;
    }

    
    public void setRootParentValue(java.lang.String value){
        checkAllowChange();
        
        this._rootParentValue = value;
           
    }

    
    /**
     * 
     * xml name: sortProp
     *  
     */
    
    public java.lang.String getSortProp(){
      return _sortProp;
    }

    
    public void setSortProp(java.lang.String value){
        checkAllowChange();
        
        this._sortProp = value;
           
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
        
        out.putNotNull("childrenProp",this.getChildrenProp());
        out.putNotNull("isLeafProp",this.getIsLeafProp());
        out.putNotNull("levelProp",this.getLevelProp());
        out.putNotNull("parentProp",this.getParentProp());
        out.putNotNull("rootLevelValue",this.getRootLevelValue());
        out.putNotNull("rootParentValue",this.getRootParentValue());
        out.putNotNull("sortProp",this.getSortProp());
    }

    public ObjTreeModel cloneInstance(){
        ObjTreeModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ObjTreeModel instance){
        super.copyTo(instance);
        
        instance.setChildrenProp(this.getChildrenProp());
        instance.setIsLeafProp(this.getIsLeafProp());
        instance.setLevelProp(this.getLevelProp());
        instance.setParentProp(this.getParentProp());
        instance.setRootLevelValue(this.getRootLevelValue());
        instance.setRootParentValue(this.getRootParentValue());
        instance.setSortProp(this.getSortProp());
    }

    protected ObjTreeModel newInstance(){
        return (ObjTreeModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON

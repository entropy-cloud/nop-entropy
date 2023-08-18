package io.nop.xlang.xmeta.impl._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [44:6:0:0]/nop/schema/xmeta.xdef <p>
 * 树形结构
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
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

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("childrenProp",this.getChildrenProp());
        out.put("isLeafProp",this.getIsLeafProp());
        out.put("levelProp",this.getLevelProp());
        out.put("parentProp",this.getParentProp());
        out.put("rootLevelValue",this.getRootLevelValue());
    }
}
 // resume CPD analysis - CPD-ON

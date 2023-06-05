package io.nop.xui.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [45:10:0:0]/nop/schema/xui/grid.xdef <p>
 * 单个字段对应的界面描述
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _UiGridColModel extends io.nop.xui.model.UiDisplayMeta {
    
    /**
     *  
     * xml name: align
     * 
     */
    private java.lang.String _align ;
    
    /**
     *  
     * xml name: breakpoint
     * 列表的列数过多时，breakpoint可以从该列开始折叠到footer部分显示。
     */
    private java.lang.String _breakpoint ;
    
    /**
     *  
     * xml name: fixed
     * 是否锁定列。left左侧锁定, right表示右侧锁定
     */
    private java.lang.String _fixed ;
    
    /**
     *  
     * xml name: groupName
     * 
     */
    private java.lang.String _groupName ;
    
    /**
     *  
     * xml name: hidden
     * 如果设置为hidden，则前端表格中不显示此列，也不生成控件
     */
    private boolean _hidden  = false;
    
    /**
     *  
     * xml name: labelClassName
     * 
     */
    private java.lang.String _labelClassName ;
    
    /**
     *  
     * xml name: mandatory
     * 
     */
    private boolean _mandatory  = false;
    
    /**
     *  
     * xml name: readonly
     * 
     */
    private boolean _readonly  = false;
    
    /**
     *  
     * xml name: sortable
     * 
     */
    private boolean _sortable  = false;
    
    /**
     * 
     * xml name: align
     *  
     */
    
    public java.lang.String getAlign(){
      return _align;
    }

    
    public void setAlign(java.lang.String value){
        checkAllowChange();
        
        this._align = value;
           
    }

    
    /**
     * 
     * xml name: breakpoint
     *  列表的列数过多时，breakpoint可以从该列开始折叠到footer部分显示。
     */
    
    public java.lang.String getBreakpoint(){
      return _breakpoint;
    }

    
    public void setBreakpoint(java.lang.String value){
        checkAllowChange();
        
        this._breakpoint = value;
           
    }

    
    /**
     * 
     * xml name: fixed
     *  是否锁定列。left左侧锁定, right表示右侧锁定
     */
    
    public java.lang.String getFixed(){
      return _fixed;
    }

    
    public void setFixed(java.lang.String value){
        checkAllowChange();
        
        this._fixed = value;
           
    }

    
    /**
     * 
     * xml name: groupName
     *  
     */
    
    public java.lang.String getGroupName(){
      return _groupName;
    }

    
    public void setGroupName(java.lang.String value){
        checkAllowChange();
        
        this._groupName = value;
           
    }

    
    /**
     * 
     * xml name: hidden
     *  如果设置为hidden，则前端表格中不显示此列，也不生成控件
     */
    
    public boolean isHidden(){
      return _hidden;
    }

    
    public void setHidden(boolean value){
        checkAllowChange();
        
        this._hidden = value;
           
    }

    
    /**
     * 
     * xml name: labelClassName
     *  
     */
    
    public java.lang.String getLabelClassName(){
      return _labelClassName;
    }

    
    public void setLabelClassName(java.lang.String value){
        checkAllowChange();
        
        this._labelClassName = value;
           
    }

    
    /**
     * 
     * xml name: mandatory
     *  
     */
    
    public boolean isMandatory(){
      return _mandatory;
    }

    
    public void setMandatory(boolean value){
        checkAllowChange();
        
        this._mandatory = value;
           
    }

    
    /**
     * 
     * xml name: readonly
     *  
     */
    
    public boolean isReadonly(){
      return _readonly;
    }

    
    public void setReadonly(boolean value){
        checkAllowChange();
        
        this._readonly = value;
           
    }

    
    /**
     * 
     * xml name: sortable
     *  
     */
    
    public boolean isSortable(){
      return _sortable;
    }

    
    public void setSortable(boolean value){
        checkAllowChange();
        
        this._sortable = value;
           
    }

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("align",this.getAlign());
        out.put("breakpoint",this.getBreakpoint());
        out.put("fixed",this.getFixed());
        out.put("groupName",this.getGroupName());
        out.put("hidden",this.isHidden());
        out.put("labelClassName",this.getLabelClassName());
        out.put("mandatory",this.isMandatory());
        out.put("readonly",this.isReadonly());
        out.put("sortable",this.isSortable());
    }
}
 // resume CPD analysis - CPD-ON

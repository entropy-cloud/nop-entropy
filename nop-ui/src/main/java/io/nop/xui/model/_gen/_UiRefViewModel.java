package io.nop.xui.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [39:6:0:0]/nop/schema/xui/disp.xdef <p>
 * 对于对象属性或者对象列表属性，使用xview文件中定义的page去显示
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _UiRefViewModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: buttonLabel
     * 如果不为空，则表示使用弹出页面显示。
     */
    private java.lang.String _buttonLabel ;
    
    /**
     *  
     * xml name: form
     * 
     */
    private java.lang.String _form ;
    
    /**
     *  
     * xml name: grid
     * 
     */
    private java.lang.String _grid ;
    
    /**
     *  
     * xml name: page
     * 
     */
    private java.lang.String _page ;
    
    /**
     *  
     * xml name: path
     * 如果对应于xview文件所在路径，则page/grid/form属性必须有一个为非空，根据它们动态构建一个页面。
     * 也可以直接指定page.yaml文件，直接复用已有的文件。
     * 如果为空，则试图根据当前模型文件路径猜测得到一个view模型文件路径
     */
    private java.lang.String _path ;
    
    /**
     * 
     * xml name: buttonLabel
     *  如果不为空，则表示使用弹出页面显示。
     */
    
    public java.lang.String getButtonLabel(){
      return _buttonLabel;
    }

    
    public void setButtonLabel(java.lang.String value){
        checkAllowChange();
        
        this._buttonLabel = value;
           
    }

    
    /**
     * 
     * xml name: form
     *  
     */
    
    public java.lang.String getForm(){
      return _form;
    }

    
    public void setForm(java.lang.String value){
        checkAllowChange();
        
        this._form = value;
           
    }

    
    /**
     * 
     * xml name: grid
     *  
     */
    
    public java.lang.String getGrid(){
      return _grid;
    }

    
    public void setGrid(java.lang.String value){
        checkAllowChange();
        
        this._grid = value;
           
    }

    
    /**
     * 
     * xml name: page
     *  
     */
    
    public java.lang.String getPage(){
      return _page;
    }

    
    public void setPage(java.lang.String value){
        checkAllowChange();
        
        this._page = value;
           
    }

    
    /**
     * 
     * xml name: path
     *  如果对应于xview文件所在路径，则page/grid/form属性必须有一个为非空，根据它们动态构建一个页面。
     * 也可以直接指定page.yaml文件，直接复用已有的文件。
     * 如果为空，则试图根据当前模型文件路径猜测得到一个view模型文件路径
     */
    
    public java.lang.String getPath(){
      return _path;
    }

    
    public void setPath(java.lang.String value){
        checkAllowChange();
        
        this._path = value;
           
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
        
        out.put("buttonLabel",this.getButtonLabel());
        out.put("form",this.getForm());
        out.put("grid",this.getGrid());
        out.put("page",this.getPage());
        out.put("path",this.getPath());
    }
}
 // resume CPD analysis - CPD-ON

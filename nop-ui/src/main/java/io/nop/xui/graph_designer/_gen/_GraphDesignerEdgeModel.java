package io.nop.xui.graph_designer._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [41:10:0:0]/nop/schema/designer/graph-designer.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _GraphDesignerEdgeModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: addable
     * 
     */
    private java.lang.Boolean _addable  = true;
    
    /**
     *  
     * xml name: allowSources
     * 
     */
    private java.util.Set<java.lang.String> _allowSources ;
    
    /**
     *  
     * xml name: allowTargets
     * 
     */
    private java.util.Set<java.lang.String> _allowTargets ;
    
    /**
     *  
     * xml name: deletable
     * 
     */
    private java.lang.Boolean _deletable ;
    
    /**
     *  
     * xml name: icon
     * 
     */
    private java.lang.String _icon ;
    
    /**
     *  
     * xml name: label
     * 
     */
    private java.lang.String _label ;
    
    /**
     *  
     * xml name: maxOccurs
     * 
     */
    private java.lang.Integer _maxOccurs ;
    
    /**
     *  
     * xml name: minOccurs
     * 
     */
    private java.lang.Integer _minOccurs ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: propsForm
     * 属性表单
     */
    private java.lang.String _propsForm ;
    
    /**
     *  
     * xml name: style
     * 
     */
    private java.lang.String _style ;
    
    /**
     *  
     * xml name: tagSet
     * 
     */
    private java.util.Set<java.lang.String> _tagSet ;
    
    /**
     *  
     * xml name: template
     * 
     */
    private io.nop.core.lang.xml.XNode _template ;
    
    /**
     *  
     * xml name: type
     * 
     */
    private java.lang.String _type ;
    
    /**
     *  
     * xml name: width
     * 
     */
    private java.lang.Integer _width ;
    
    /**
     * 
     * xml name: addable
     *  
     */
    
    public java.lang.Boolean getAddable(){
      return _addable;
    }

    
    public void setAddable(java.lang.Boolean value){
        checkAllowChange();
        
        this._addable = value;
           
    }

    
    /**
     * 
     * xml name: allowSources
     *  
     */
    
    public java.util.Set<java.lang.String> getAllowSources(){
      return _allowSources;
    }

    
    public void setAllowSources(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._allowSources = value;
           
    }

    
    /**
     * 
     * xml name: allowTargets
     *  
     */
    
    public java.util.Set<java.lang.String> getAllowTargets(){
      return _allowTargets;
    }

    
    public void setAllowTargets(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._allowTargets = value;
           
    }

    
    /**
     * 
     * xml name: deletable
     *  
     */
    
    public java.lang.Boolean getDeletable(){
      return _deletable;
    }

    
    public void setDeletable(java.lang.Boolean value){
        checkAllowChange();
        
        this._deletable = value;
           
    }

    
    /**
     * 
     * xml name: icon
     *  
     */
    
    public java.lang.String getIcon(){
      return _icon;
    }

    
    public void setIcon(java.lang.String value){
        checkAllowChange();
        
        this._icon = value;
           
    }

    
    /**
     * 
     * xml name: label
     *  
     */
    
    public java.lang.String getLabel(){
      return _label;
    }

    
    public void setLabel(java.lang.String value){
        checkAllowChange();
        
        this._label = value;
           
    }

    
    /**
     * 
     * xml name: maxOccurs
     *  
     */
    
    public java.lang.Integer getMaxOccurs(){
      return _maxOccurs;
    }

    
    public void setMaxOccurs(java.lang.Integer value){
        checkAllowChange();
        
        this._maxOccurs = value;
           
    }

    
    /**
     * 
     * xml name: minOccurs
     *  
     */
    
    public java.lang.Integer getMinOccurs(){
      return _minOccurs;
    }

    
    public void setMinOccurs(java.lang.Integer value){
        checkAllowChange();
        
        this._minOccurs = value;
           
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
     * xml name: propsForm
     *  属性表单
     */
    
    public java.lang.String getPropsForm(){
      return _propsForm;
    }

    
    public void setPropsForm(java.lang.String value){
        checkAllowChange();
        
        this._propsForm = value;
           
    }

    
    /**
     * 
     * xml name: style
     *  
     */
    
    public java.lang.String getStyle(){
      return _style;
    }

    
    public void setStyle(java.lang.String value){
        checkAllowChange();
        
        this._style = value;
           
    }

    
    /**
     * 
     * xml name: tagSet
     *  
     */
    
    public java.util.Set<java.lang.String> getTagSet(){
      return _tagSet;
    }

    
    public void setTagSet(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._tagSet = value;
           
    }

    
    /**
     * 
     * xml name: template
     *  
     */
    
    public io.nop.core.lang.xml.XNode getTemplate(){
      return _template;
    }

    
    public void setTemplate(io.nop.core.lang.xml.XNode value){
        checkAllowChange();
        
        this._template = value;
           
    }

    
    /**
     * 
     * xml name: type
     *  
     */
    
    public java.lang.String getType(){
      return _type;
    }

    
    public void setType(java.lang.String value){
        checkAllowChange();
        
        this._type = value;
           
    }

    
    /**
     * 
     * xml name: width
     *  
     */
    
    public java.lang.Integer getWidth(){
      return _width;
    }

    
    public void setWidth(java.lang.Integer value){
        checkAllowChange();
        
        this._width = value;
           
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
        
        out.put("addable",this.getAddable());
        out.put("allowSources",this.getAllowSources());
        out.put("allowTargets",this.getAllowTargets());
        out.put("deletable",this.getDeletable());
        out.put("icon",this.getIcon());
        out.put("label",this.getLabel());
        out.put("maxOccurs",this.getMaxOccurs());
        out.put("minOccurs",this.getMinOccurs());
        out.put("name",this.getName());
        out.put("propsForm",this.getPropsForm());
        out.put("style",this.getStyle());
        out.put("tagSet",this.getTagSet());
        out.put("template",this.getTemplate());
        out.put("type",this.getType());
        out.put("width",this.getWidth());
    }
}
 // resume CPD analysis - CPD-ON

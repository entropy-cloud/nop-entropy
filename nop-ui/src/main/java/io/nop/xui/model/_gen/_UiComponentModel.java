package io.nop.xui.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.xui.model.UiComponentModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [3:2:0:0]/nop/schema/xui/xuc.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _UiComponentModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: import
     * <x:gen-extends>
     * <xui:UsePage src="a.xpage" page="crud" />
     * editor生成XNode，然后编程处理
     * </x:gen-extends>
     */
    private io.nop.xui.model.UiImportModel _import ;
    
    /**
     *  
     * xml name: scoped-style
     * 组件局部样式
     */
    private io.nop.core.resource.tpl.ITextTemplateOutput _scopedStyle ;
    
    /**
     *  
     * xml name: script
     * 模块script，只执行一次
     */
    private io.nop.core.resource.tpl.ITextTemplateOutput _script ;
    
    /**
     *  
     * xml name: setup
     * 组件的setup函数，每次创建组件实例时执行。对应于vue的<script setup>
     */
    private io.nop.core.resource.tpl.ITextTemplateOutput _setup ;
    
    /**
     *  
     * xml name: style
     * 全局样式
     */
    private io.nop.core.resource.tpl.ITextTemplateOutput _style ;
    
    /**
     *  
     * xml name: template
     * 
     */
    private io.nop.core.resource.tpl.ITextTemplateOutput _template ;
    
    /**
     * 
     * xml name: import
     *  <x:gen-extends>
     * <xui:UsePage src="a.xpage" page="crud" />
     * editor生成XNode，然后编程处理
     * </x:gen-extends>
     */
    
    public io.nop.xui.model.UiImportModel getImport(){
      return _import;
    }

    
    public void setImport(io.nop.xui.model.UiImportModel value){
        checkAllowChange();
        
        this._import = value;
           
    }

    
    /**
     * 
     * xml name: scoped-style
     *  组件局部样式
     */
    
    public io.nop.core.resource.tpl.ITextTemplateOutput getScopedStyle(){
      return _scopedStyle;
    }

    
    public void setScopedStyle(io.nop.core.resource.tpl.ITextTemplateOutput value){
        checkAllowChange();
        
        this._scopedStyle = value;
           
    }

    
    /**
     * 
     * xml name: script
     *  模块script，只执行一次
     */
    
    public io.nop.core.resource.tpl.ITextTemplateOutput getScript(){
      return _script;
    }

    
    public void setScript(io.nop.core.resource.tpl.ITextTemplateOutput value){
        checkAllowChange();
        
        this._script = value;
           
    }

    
    /**
     * 
     * xml name: setup
     *  组件的setup函数，每次创建组件实例时执行。对应于vue的<script setup>
     */
    
    public io.nop.core.resource.tpl.ITextTemplateOutput getSetup(){
      return _setup;
    }

    
    public void setSetup(io.nop.core.resource.tpl.ITextTemplateOutput value){
        checkAllowChange();
        
        this._setup = value;
           
    }

    
    /**
     * 
     * xml name: style
     *  全局样式
     */
    
    public io.nop.core.resource.tpl.ITextTemplateOutput getStyle(){
      return _style;
    }

    
    public void setStyle(io.nop.core.resource.tpl.ITextTemplateOutput value){
        checkAllowChange();
        
        this._style = value;
           
    }

    
    /**
     * 
     * xml name: template
     *  
     */
    
    public io.nop.core.resource.tpl.ITextTemplateOutput getTemplate(){
      return _template;
    }

    
    public void setTemplate(io.nop.core.resource.tpl.ITextTemplateOutput value){
        checkAllowChange();
        
        this._template = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._import = io.nop.api.core.util.FreezeHelper.deepFreeze(this._import);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("import",this.getImport());
        out.putNotNull("scopedStyle",this.getScopedStyle());
        out.putNotNull("script",this.getScript());
        out.putNotNull("setup",this.getSetup());
        out.putNotNull("style",this.getStyle());
        out.putNotNull("template",this.getTemplate());
    }

    public UiComponentModel cloneInstance(){
        UiComponentModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(UiComponentModel instance){
        super.copyTo(instance);
        
        instance.setImport(this.getImport());
        instance.setScopedStyle(this.getScopedStyle());
        instance.setScript(this.getScript());
        instance.setSetup(this.getSetup());
        instance.setStyle(this.getStyle());
        instance.setTemplate(this.getTemplate());
    }

    protected UiComponentModel newInstance(){
        return (UiComponentModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON

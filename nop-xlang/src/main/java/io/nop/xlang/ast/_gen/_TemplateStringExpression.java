//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.TemplateStringExpression;
import io.nop.xlang.ast.XLangASTNode; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _TemplateStringExpression extends io.nop.xlang.ast.Expression {
    
    protected io.nop.xlang.ast.Identifier id;
    
    protected io.nop.xlang.ast.TemplateStringLiteral value;
    

    public _TemplateStringExpression(){
    }

    
    public io.nop.xlang.ast.Identifier getId(){
        return id;
    }

    public void setId(io.nop.xlang.ast.Identifier value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.id = value;
    }
    
    public io.nop.xlang.ast.TemplateStringLiteral getValue(){
        return value;
    }

    public void setValue(io.nop.xlang.ast.TemplateStringLiteral value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.value = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("id",getId());
       
          checkMandatory("value",getValue());
       
    }


    public TemplateStringExpression newInstance(){
      return new TemplateStringExpression();
    }

    @Override
    public TemplateStringExpression deepClone(){
       TemplateStringExpression ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(id != null){
                  
                          ret.setId(id.deepClone());
                      
                }
            
                if(value != null){
                  
                          ret.setValue(value.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(id != null)
                processor.accept(id);
        
            if(value != null)
                processor.accept(value);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(id != null && processor.apply(id) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(value != null && processor.apply(value) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.id == oldChild){
               this.setId((io.nop.xlang.ast.Identifier)newChild);
               return true;
            }
        
            if(this.value == oldChild){
               this.setValue((io.nop.xlang.ast.TemplateStringLiteral)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.id == child){
                this.setId(null);
                return true;
            }
        
            if(this.value == child){
                this.setValue(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    TemplateStringExpression other = (TemplateStringExpression)node;
    
            if(!isNodeEquivalent(this.id,other.getId())){
               return false;
            }
        
            if(!isNodeEquivalent(this.value,other.getValue())){
               return false;
            }
        
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.TemplateStringExpression;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(id != null){
                      
                              json.put("id", id);
                          
                    }
                
                    if(value != null){
                      
                              json.put("value", value);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(id != null)
                    id.freeze(cascade);
                if(value != null)
                    value.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON

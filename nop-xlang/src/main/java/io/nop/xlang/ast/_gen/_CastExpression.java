//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.CastExpression;
import io.nop.xlang.ast.XLangASTNode; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _CastExpression extends io.nop.xlang.ast.Expression {
    
    protected io.nop.xlang.ast.NamedTypeNode asType;
    
    protected io.nop.xlang.ast.Expression value;
    

    public _CastExpression(){
    }

    
    public io.nop.xlang.ast.NamedTypeNode getAsType(){
        return asType;
    }

    public void setAsType(io.nop.xlang.ast.NamedTypeNode value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.asType = value;
    }
    
    public io.nop.xlang.ast.Expression getValue(){
        return value;
    }

    public void setValue(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.value = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("asType",getAsType());
       
          checkMandatory("value",getValue());
       
    }


    public CastExpression newInstance(){
      return new CastExpression();
    }

    @Override
    public CastExpression deepClone(){
       CastExpression ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(value != null){
                  
                          ret.setValue(value.deepClone());
                      
                }
            
                if(asType != null){
                  
                          ret.setAsType(asType.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(value != null)
                processor.accept(value);
        
            if(asType != null)
                processor.accept(asType);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(value != null && processor.apply(value) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(asType != null && processor.apply(asType) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.value == oldChild){
               this.setValue((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
            if(this.asType == oldChild){
               this.setAsType((io.nop.xlang.ast.NamedTypeNode)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.value == child){
                this.setValue(null);
                return true;
            }
        
            if(this.asType == child){
                this.setAsType(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    CastExpression other = (CastExpression)node;
    
            if(!isNodeEquivalent(this.value,other.getValue())){
               return false;
            }
        
            if(!isNodeEquivalent(this.asType,other.getAsType())){
               return false;
            }
        
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.CastExpression;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(value != null){
                      
                              json.put("value", value);
                          
                    }
                
                    if(asType != null){
                      
                              json.put("asType", asType);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(value != null)
                    value.freeze(cascade);
                if(asType != null)
                    asType.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON

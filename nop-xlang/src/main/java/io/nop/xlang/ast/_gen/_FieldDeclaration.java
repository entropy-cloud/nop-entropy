//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.FieldDeclaration;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _FieldDeclaration extends io.nop.xlang.ast.DecoratedDeclaration {
    
    protected io.nop.xlang.ast.Expression initializer;
    
    protected int modifiers;
    
    protected io.nop.xlang.ast.Identifier name;
    
    protected boolean optional;
    
    protected io.nop.xlang.ast.NamedTypeNode type;
    

    public _FieldDeclaration(){
    }

    
    public io.nop.xlang.ast.Expression getInitializer(){
        return initializer;
    }

    public void setInitializer(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.initializer = value;
    }
    
    public int getModifiers(){
        return modifiers;
    }

    public void setModifiers(int value){
        checkAllowChange();
        
        this.modifiers = value;
    }
    
    public io.nop.xlang.ast.Identifier getName(){
        return name;
    }

    public void setName(io.nop.xlang.ast.Identifier value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.name = value;
    }
    
    public boolean getOptional(){
        return optional;
    }

    public void setOptional(boolean value){
        checkAllowChange();
        
        this.optional = value;
    }
    
    public io.nop.xlang.ast.NamedTypeNode getType(){
        return type;
    }

    public void setType(io.nop.xlang.ast.NamedTypeNode value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.type = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("name",getName());
       
          checkMandatory("type",getType());
       
    }


    public FieldDeclaration newInstance(){
      return new FieldDeclaration();
    }

    @Override
    public FieldDeclaration deepClone(){
       FieldDeclaration ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(decorators != null){
                  
                          ret.setDecorators(decorators.deepClone());
                      
                }
            
                if(name != null){
                  
                          ret.setName(name.deepClone());
                      
                }
            
                if(type != null){
                  
                          ret.setType(type.deepClone());
                      
                }
            
                ret.setOptional(optional);
            
                if(initializer != null){
                  
                          ret.setInitializer(initializer.deepClone());
                      
                }
            
                ret.setModifiers(modifiers);
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(decorators != null)
                processor.accept(decorators);
        
            if(name != null)
                processor.accept(name);
        
            if(type != null)
                processor.accept(type);
        
            if(initializer != null)
                processor.accept(initializer);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(decorators != null && processor.apply(decorators) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(name != null && processor.apply(name) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(type != null && processor.apply(type) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(initializer != null && processor.apply(initializer) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.decorators == oldChild){
               this.setDecorators((io.nop.xlang.ast.Decorators)newChild);
               return true;
            }
        
            if(this.name == oldChild){
               this.setName((io.nop.xlang.ast.Identifier)newChild);
               return true;
            }
        
            if(this.type == oldChild){
               this.setType((io.nop.xlang.ast.NamedTypeNode)newChild);
               return true;
            }
        
            if(this.initializer == oldChild){
               this.setInitializer((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.decorators == child){
                this.setDecorators(null);
                return true;
            }
        
            if(this.name == child){
                this.setName(null);
                return true;
            }
        
            if(this.type == child){
                this.setType(null);
                return true;
            }
        
            if(this.initializer == child){
                this.setInitializer(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    FieldDeclaration other = (FieldDeclaration)node;
    
            if(!isNodeEquivalent(this.decorators,other.getDecorators())){
               return false;
            }
        
            if(!isNodeEquivalent(this.name,other.getName())){
               return false;
            }
        
            if(!isNodeEquivalent(this.type,other.getType())){
               return false;
            }
        
                if(!isValueEquivalent(this.optional,other.getOptional())){
                   return false;
                }
            
            if(!isNodeEquivalent(this.initializer,other.getInitializer())){
               return false;
            }
        
                if(!isValueEquivalent(this.modifiers,other.getModifiers())){
                   return false;
                }
            
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.FieldDeclaration;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(decorators != null){
                      
                              json.put("decorators", decorators);
                          
                    }
                
                    if(name != null){
                      
                              json.put("name", name);
                          
                    }
                
                    if(type != null){
                      
                              json.put("type", type);
                          
                    }
                
                   json.put("optional", optional);
                
                    if(initializer != null){
                      
                              json.put("initializer", initializer);
                          
                    }
                
                   json.put("modifiers", modifiers);
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(decorators != null)
                    decorators.freeze(cascade);
                if(name != null)
                    name.freeze(cascade);
                if(type != null)
                    type.freeze(cascade);
                if(initializer != null)
                    initializer.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON

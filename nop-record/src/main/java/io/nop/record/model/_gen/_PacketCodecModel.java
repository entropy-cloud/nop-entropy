package io.nop.record.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.record.model.PacketCodecModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/record/packet-codec.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _PacketCodecModel extends io.nop.record.model.RecordDefinitions {
    
    /**
     *  
     * xml name: lengthAdjustment
     * 
     */
    private int _lengthAdjustment  = 0;
    
    /**
     *  
     * xml name: lengthFieldCodec
     * 
     */
    private java.lang.String _lengthFieldCodec ;
    
    /**
     *  
     * xml name: lengthFieldLength
     * 
     */
    private int _lengthFieldLength  = 0;
    
    /**
     *  
     * xml name: lengthFieldOffset
     * 
     */
    private int _lengthFieldOffset  = 0;
    
    /**
     * 
     * xml name: lengthAdjustment
     *  
     */
    
    public int getLengthAdjustment(){
      return _lengthAdjustment;
    }

    
    public void setLengthAdjustment(int value){
        checkAllowChange();
        
        this._lengthAdjustment = value;
           
    }

    
    /**
     * 
     * xml name: lengthFieldCodec
     *  
     */
    
    public java.lang.String getLengthFieldCodec(){
      return _lengthFieldCodec;
    }

    
    public void setLengthFieldCodec(java.lang.String value){
        checkAllowChange();
        
        this._lengthFieldCodec = value;
           
    }

    
    /**
     * 
     * xml name: lengthFieldLength
     *  
     */
    
    public int getLengthFieldLength(){
      return _lengthFieldLength;
    }

    
    public void setLengthFieldLength(int value){
        checkAllowChange();
        
        this._lengthFieldLength = value;
           
    }

    
    /**
     * 
     * xml name: lengthFieldOffset
     *  
     */
    
    public int getLengthFieldOffset(){
      return _lengthFieldOffset;
    }

    
    public void setLengthFieldOffset(int value){
        checkAllowChange();
        
        this._lengthFieldOffset = value;
           
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
        
        out.putNotNull("lengthAdjustment",this.getLengthAdjustment());
        out.putNotNull("lengthFieldCodec",this.getLengthFieldCodec());
        out.putNotNull("lengthFieldLength",this.getLengthFieldLength());
        out.putNotNull("lengthFieldOffset",this.getLengthFieldOffset());
    }

    public PacketCodecModel cloneInstance(){
        PacketCodecModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(PacketCodecModel instance){
        super.copyTo(instance);
        
        instance.setLengthAdjustment(this.getLengthAdjustment());
        instance.setLengthFieldCodec(this.getLengthFieldCodec());
        instance.setLengthFieldLength(this.getLengthFieldLength());
        instance.setLengthFieldOffset(this.getLengthFieldOffset());
    }

    protected PacketCodecModel newInstance(){
        return (PacketCodecModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON

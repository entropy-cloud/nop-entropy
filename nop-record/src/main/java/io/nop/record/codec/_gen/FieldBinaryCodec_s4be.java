
package io.nop.record.codec._gen;

import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.input.IRecordBinaryInput;
import io.nop.record.output.IRecordBinaryOutput;

import java.nio.charset.Charset;

public class FieldBinaryCodec_s4be implements IFieldBinaryCodec{
    public static final FieldBinaryCodec_s4be INSTANCE = new FieldBinaryCodec_s4be();

    public Object decode(IRecordBinaryInput input, int length, Charset charset, IFieldCodecContext context){
	  return input.readS4be();
    }

    public void encode(IRecordBinaryOutput output, Object value, int length, Charset charset, IFieldCodecContext context){
	    if(value == null){
		    output.writeS4be(0);
		}else{
			output.writeS4be((Integer)value);
		}	
    }
}

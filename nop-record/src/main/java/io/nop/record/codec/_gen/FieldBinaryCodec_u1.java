
package io.nop.record.codec._gen;

import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.input.IRecordBinaryInput;
import io.nop.record.output.IRecordBinaryOutput;

import java.nio.charset.Charset;

public class FieldBinaryCodec_u1 implements IFieldBinaryCodec{
    public static final FieldBinaryCodec_u1 INSTANCE = new FieldBinaryCodec_u1();

    public Object decode(IRecordBinaryInput input, int length, Charset charset, IFieldCodecContext context){
	  return input.readU1();
    }

    public void encode(IRecordBinaryOutput output, Object value, int length, Charset charset, IFieldCodecContext context){
	    if(value == null){
		    output.writeU1((short) 0);
		}else{
			output.writeU1((Short)value);
		}	
    }
}

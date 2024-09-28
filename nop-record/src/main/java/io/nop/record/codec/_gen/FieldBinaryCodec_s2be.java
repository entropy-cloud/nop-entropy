
package io.nop.record.codec._gen;

import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldBinaryEncoder;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.input.IRecordBinaryInput;
import io.nop.record.output.IRecordBinaryOutput;

import java.nio.charset.Charset;

public class FieldBinaryCodec_s2be implements IFieldBinaryCodec{
    public static final FieldBinaryCodec_s2be INSTANCE = new FieldBinaryCodec_s2be();

    public Object decode(IRecordBinaryInput input, int length, Charset charset, IFieldCodecContext context){
	  return input.readS2be();
    }

    public void encode(IRecordBinaryOutput output, Object value, int length, Charset charset,
	                   IFieldCodecContext context, IFieldBinaryEncoder bodyEncoder){
	    if(value == null){
		    output.writeS2be((short) 0);
		}else{
			output.writeS2be((Short)value);
		}	
    }
}

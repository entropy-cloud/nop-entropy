
package io.nop.record.codec._gen;

import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldBinaryEncoder;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.reader.IRecordBinaryReader;
import io.nop.record.writer.IRecordBinaryWriter;

import java.nio.charset.Charset;

public class FieldBinaryCodec_u4be implements IFieldBinaryCodec{
    public static final FieldBinaryCodec_u4be INSTANCE = new FieldBinaryCodec_u4be();

    public Object decode(IRecordBinaryReader input, int length, Charset charset, IFieldCodecContext context){
	  return input.readU4be();
    }

    public void encode(IRecordBinaryWriter output, Object value, int length, Charset charset,
					   IFieldCodecContext context, IFieldBinaryEncoder bodyEncoder){
	    if(value == null){
		    output.writeU4be(0L);
		}else{
			output.writeU4be((Long)value);
		}	
    }
}

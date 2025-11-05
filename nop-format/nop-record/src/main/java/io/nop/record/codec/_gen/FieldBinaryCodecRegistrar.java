
package io.nop.record.codec._gen;

import io.nop.record.codec.FieldCodecRegistry;



public class FieldBinaryCodecRegistrar{
    public static void registerWordType(FieldCodecRegistry registry){
	    
		registry.registerCodec("s1",FieldBinaryCodec_s1.INSTANCE);
		
		registry.registerCodec("u1",FieldBinaryCodec_u1.INSTANCE);
		
		registry.registerCodec("s2le",FieldBinaryCodec_s2le.INSTANCE);
		
		registry.registerCodec("s2be",FieldBinaryCodec_s2be.INSTANCE);
		
		registry.registerCodec("u2le",FieldBinaryCodec_u2le.INSTANCE);
		
		registry.registerCodec("u2be",FieldBinaryCodec_u2be.INSTANCE);
		
		registry.registerCodec("s4le",FieldBinaryCodec_s4le.INSTANCE);
		
		registry.registerCodec("s4be",FieldBinaryCodec_s4be.INSTANCE);
		
		registry.registerCodec("u4le",FieldBinaryCodec_u4le.INSTANCE);
		
		registry.registerCodec("u4be",FieldBinaryCodec_u4be.INSTANCE);
		
		registry.registerCodec("s8le",FieldBinaryCodec_s8le.INSTANCE);
		
		registry.registerCodec("s8be",FieldBinaryCodec_s8be.INSTANCE);
		
		registry.registerCodec("u8le",FieldBinaryCodec_u8le.INSTANCE);
		
		registry.registerCodec("u8be",FieldBinaryCodec_u8be.INSTANCE);
		
		registry.registerCodec("f4le",FieldBinaryCodec_f4le.INSTANCE);
		
		registry.registerCodec("f4be",FieldBinaryCodec_f4be.INSTANCE);
		
		registry.registerCodec("f8le",FieldBinaryCodec_f8le.INSTANCE);
		
		registry.registerCodec("f8be",FieldBinaryCodec_f8be.INSTANCE);
		
	}
}

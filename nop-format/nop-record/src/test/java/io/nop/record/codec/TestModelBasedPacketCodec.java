package io.nop.record.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.unittest.BaseTestCase;
import io.nop.record.codec.impl.ModelBasedPacketCodec;
import io.nop.record.model.PacketCodecModel;
import io.nop.record.netty.ByteBufBinaryDataReader;
import io.nop.record.netty.ByteBufBinaryDataWriter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestModelBasedPacketCodec extends BaseTestCase {
    @BeforeAll
    public static void init(){
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy(){
        CoreInitialization.destroy();
    }

    @Test
    public void testByteBuf() {
        ByteBuf buf = UnpooledByteBufAllocator.DEFAULT.buffer();
        buf.writeLong(333);
        long value = buf.readLong();
        assertEquals(333L, value);
    }

    @Test
    public void testByteBufInputOutput() throws IOException {
        ByteBuf buf = UnpooledByteBufAllocator.DEFAULT.buffer();
        ByteBufBinaryDataReader reader = new ByteBufBinaryDataReader(buf);
        ByteBufBinaryDataWriter writer = new ByteBufBinaryDataWriter(buf);

        writer.writeU1(1);
        assertEquals(1, reader.readU1());

        writer.writeU2be(2);
        assertEquals(2, reader.readU2be());

        writer.writeU2le(3);
        assertEquals(3, reader.readU2le());

        writer.writeU4be(4);
        assertEquals(4, reader.readU4be());

        writer.writeU4le(5);
        assertEquals(5, reader.readU4le());

        writer.writeU8be(6);
        assertEquals(6, reader.readU8be());

        writer.writeU8le(7);
        assertEquals(7, reader.readU8le());

        writer.writeS1((byte) 8);
        assertEquals(8, reader.readS1());

        writer.writeS2be((short) 9);
        assertEquals(9, reader.readS2be());

        writer.writeS2le((short) 10);
        assertEquals(10, reader.readS2le());

        writer.writeS4be(11);
        assertEquals(11, reader.readS4be());

        writer.writeS4le(12);
        assertEquals(12, reader.readS4le());

        writer.writeS8be(13);
        assertEquals(13, reader.readS8be());

        writer.writeS8le(14);
        assertEquals(14, reader.readS8le());
    }

    @Test
    public void testCodec() {
        PacketCodecModel codecModel = (PacketCodecModel) ResourceComponentManager.instance().loadComponentModel("/test/record/test.packet-codec.xml");
        ModelBasedPacketCodec codec = new ModelBasedPacketCodec(codecModel, FieldCodecRegistry.DEFAULT);
        Map<String, Object> map = new HashMap<>();
        map.put("id", 333L);
        map.put("name", "abc");

        byte[] bytes = codec.encodeToBytes(map);
        Map<String, Object> map2 = (Map<String, Object>) codec.decodeFromBytes(bytes);
        assertEquals(333L, map2.get("id"));
        assertEquals("abc", map2.get("name"));
    }

    @Test
    public void testPacketLengthCodecWithReaderIndexOffset() {
        // lengthFieldOffset=1, lengthFieldLength=2, lengthFieldCodec=u2be
        PacketCodecModel codecModel = new PacketCodecModel();
        codecModel.setLengthFieldOffset(1);
        codecModel.setLengthFieldLength(2);
        codecModel.setLengthFieldCodec("u2be");
        codecModel.setLengthAdjustment(0);
        codecModel.setInitialBytesToStrip(0);
        ModelBasedPacketCodec codec = new ModelBasedPacketCodec(codecModel, FieldCodecRegistry.DEFAULT);

        // 粘包残留(0xAA, index 0 已被消费) + 帧头(0x00, index 1) + 长度字段 u2be=12 (index 2-3) + 帧内容 (index 4-13)
        ByteBuf buf = UnpooledByteBufAllocator.DEFAULT.buffer();
        buf.writeByte(0xAA);
        buf.writeByte(0x00);
        buf.writeShort(12);
        for (int i = 0; i < 10; i++)
            buf.writeByte(i);

        // 模拟 readerIndex > 0（前面已有粘包残留被处理）
        buf.readerIndex(1);
        // 帧长 = 12 + lengthFieldEndOffset(3) = 15
        assertEquals(15, codec.determinePacketLength(buf));
    }

    /**
     * ByteBufBinaryDataReader.subInput 用 slice() 共享底层 refCnt：
     * 子视图独立所有权（retain），父 reader close（release）后子视图仍可读
     */
    @Test
    public void testSubInputRefCntAfterParentClose() throws IOException {
        io.netty.buffer.ByteBuf buf = UnpooledByteBufAllocator.DEFAULT.buffer();
        buf.writeBytes(new byte[]{1, 2, 3, 4, 5, 6});
        ByteBufBinaryDataReader reader = new ByteBufBinaryDataReader(buf);
        ByteBufBinaryDataReader sub = (ByteBufBinaryDataReader) reader.subInput(3);
        assertEquals(1, sub.readU1());

        reader.close();
        // 修复后：父 close 不影响子视图读取（retain 独立所有权）
        assertEquals(2, sub.readU1());
        sub.close();
    }

    /**
     * 编码帧长与 determinePacketLength 对称：编码写出的总字节数应等于解码判定的帧长。
     * 修复前编码公式 len = endIndex - strip - adj 与解码公式 frame = raw + adj + H 恒差 H-strip 字节
     */
    @Test
    public void testFrameLengthSymmetry() {
        PacketCodecModel codecModel = (PacketCodecModel) ResourceComponentManager.instance().loadComponentModel("/test/record/test.packet-codec.xml");
        ModelBasedPacketCodec codec = new ModelBasedPacketCodec(codecModel, FieldCodecRegistry.DEFAULT);
        Map<String, Object> map = new HashMap<>();
        map.put("id", 333L);
        map.put("name", "abc");

        byte[] bytes = codec.encodeToBytes(map);

        ByteBuf buf = Unpooled.wrappedBuffer(bytes);
        int frameLen = codec.determinePacketLength(buf);
        assertEquals(bytes.length, frameLen);

        // 模拟 PacketCodecHandler 的取帧方式：readSlice(frameLen) 后解码
        ByteBuf frame = buf.readSlice(frameLen);
        Map<String, Object> map2 = (Map<String, Object>) codec.decodeFromBuf(frame);
        assertEquals(333L, map2.get("id"));
        assertEquals("abc", map2.get("name"));
    }

    /**
     * initialBytesToStrip 非 0 时构造直接报错（解码侧从未实现 strip，静默配置会错帧）
     */
    @Test
    public void testInitialBytesToStripRejected() {
        PacketCodecModel codecModel = new PacketCodecModel();
        codecModel.setLengthFieldLength(2);
        codecModel.setInitialBytesToStrip(1);
        assertThrows(NopException.class,
                () -> new ModelBasedPacketCodec(codecModel, FieldCodecRegistry.DEFAULT));
    }
}

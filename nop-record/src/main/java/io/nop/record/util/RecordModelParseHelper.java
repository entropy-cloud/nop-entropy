package io.nop.record.util;

import io.nop.core.lang.xml.XNode;
import io.nop.record.RecordConstants;
import io.nop.record.model.PacketCodecModel;
import io.nop.record.model.RecordDefinitions;
import io.nop.record.model.RecordFieldMeta;
import io.nop.record.model.RecordFileMeta;
import io.nop.record.model.RecordObjectMeta;
import io.nop.xlang.xdsl.DslModelParser;

public class RecordModelParseHelper {
    public static PacketCodecModel parsePacketCodecModelFromNode(XNode node) {
        return (PacketCodecModel) new DslModelParser(RecordConstants.XDEF_PACKET_CODEC).parseFromNode(node);
    }

    public static RecordFileMeta parseRecordFileMetaFromNode(XNode node) {
        return (RecordFileMeta) new DslModelParser(RecordConstants.XDEF_RECORD_FILE).parseFromNode(node);
    }

    public static RecordDefinitions parseRecordDefinitionsFromNode(XNode node) {
        return (RecordDefinitions) new DslModelParser(RecordConstants.XDEF_RECORD_DEFINITIONS).parseFromNode(node);
    }

    public static RecordObjectMeta parseRecordObjectMetaFromNode(XNode node) {
        return (RecordObjectMeta) new DslModelParser(RecordConstants.XDEF_RECORD_OBJECT).parseFromNode(node);
    }
}
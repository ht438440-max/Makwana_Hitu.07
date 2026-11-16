package com.example

import com.example.rtmp.Amf0
import com.example.rtmp.AmfValue
import com.example.rtmp.FlvPacker
import com.example.rtmp.RtmpUrlParser
import com.example.security.RtmpCryptoManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RtmpProtocolTest {

    @Test
    fun `rtmp url parser handles youtube facebook and custom servers`() {
        val yt = RtmpUrlParser.parse("rtmps://a.rtmps.youtube.com/live2").getOrThrow()
        assertTrue(yt.isTls)
        assertEquals("a.rtmps.youtube.com", yt.host)
        assertEquals(443, yt.port)
        assertEquals("live2", yt.app)

        val fb = RtmpUrlParser.parse("rtmps://live-api-s.facebook.com:443/rtmp/").getOrThrow()
        assertTrue(fb.isTls)
        assertEquals("live-api-s.facebook.com", fb.host)
        assertEquals(443, fb.port)
        assertEquals("rtmp", fb.app)

        val custom = RtmpUrlParser.parse("rtmp://192.168.1.50/stream").getOrThrow()
        assertFalse(custom.isTls)
        assertEquals("192.168.1.50", custom.host)
        assertEquals(1935, custom.port)
        assertEquals("stream", custom.app)

        assertTrue(RtmpUrlParser.parse("https://example.com").isFailure)
    }

    @Test
    fun `amf0 round trip encodes and decodes connect command`() {
        val props = AmfValue.Obj(
            linkedMapOf(
                "app" to AmfValue.Str("live2"),
                "fpad" to AmfValue.Bool(false),
                "capabilities" to AmfValue.Number(15.0)
            )
        )
        val encoded = Amf0.encodeCommand("connect", 1.0, props)
        val decoded = Amf0.decodeAll(encoded)

        assertEquals(3, decoded.size)
        assertEquals(AmfValue.Str("connect"), decoded[0])
        assertEquals(AmfValue.Number(1.0), decoded[1])
        val obj = decoded[2] as AmfValue.Obj
        assertEquals(AmfValue.Str("live2"), obj.props["app"])
        assertEquals(AmfValue.Bool(false), obj.props["fpad"])
        assertEquals(AmfValue.Number(15.0), obj.props["capabilities"])
    }

    @Test
    fun `flv packer converts annex-b sps pps and nalus to avcc format`() {
        val annexBCsd = byteArrayOf(
            0, 0, 0, 1, 0x67, 0x42, 0xC0.toByte(), 0x1F,
            0, 0, 0, 1, 0x68, 0xCE.toByte(), 0x3C, 0x80.toByte()
        )
        val seqTag = FlvPacker.buildAvcSequenceHeaderTag(annexBCsd, null)
        assertEquals(0x17.toByte(), seqTag[0])
        assertEquals(0x00.toByte(), seqTag[1])
        // AVCDecoderConfigurationRecord version == 1
        assertEquals(0x01.toByte(), seqTag[5])

        val idrAnnexB = byteArrayOf(0, 0, 0, 1, 0x65, 0x88.toByte(), 0x84.toByte(), 0x21)
        val frameTag = FlvPacker.buildAvcVideoFrameTag(idrAnnexB, isKeyFrame = true, ctsMs = 0)
        assertEquals(0x17.toByte(), frameTag[0])
        assertEquals(0x01.toByte(), frameTag[1])
        // 4-byte length prefix = 4
        assertArrayEquals(byteArrayOf(0, 0, 0, 4, 0x65, 0x88.toByte(), 0x84.toByte(), 0x21), frameTag.copyOfRange(5, frameTag.size))
    }

    @Test
    fun `flv packer strips adts header from aac frames and masks stream keys`() {
        val adtsFrame = byteArrayOf(
            0xFF.toByte(), 0xF1.toByte(), 0x50, 0x80.toByte(), 0x01, 0x1F, 0xFC.toByte(),
            0x21, 0x10, 0x04
        )
        val tag = FlvPacker.buildAacFrameTag(adtsFrame)
        assertEquals(0xAF.toByte(), tag[0])
        assertEquals(0x01.toByte(), tag[1])
        assertArrayEquals(byteArrayOf(0x21, 0x10, 0x04), tag.copyOfRange(2, tag.size))

        assertEquals("••••••••7890", RtmpCryptoManager.maskStreamKey("abcd-efgh-7890"))
    }
}

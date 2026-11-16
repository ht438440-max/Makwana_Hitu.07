package com.example.rtmp

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

object FlvPacker {

    /**
     * Builds an AVCDecoderConfigurationRecord (for FLV AVC Sequence Header)
     * from MediaFormat csd-0 / csd-1 buffers (handles Annex-B or raw AVCC record).
     */
    fun buildAvcSequenceHeaderTag(csd0: ByteArray, csd1: ByteArray?): ByteArray {
        val configRecord = buildAvcDecoderConfigRecord(csd0, csd1)
        val out = ByteArray(5 + configRecord.size)
        out[0] = 0x17.toByte() // Keyframe (1) + AVC (7)
        out[1] = 0x00 // AVC sequence header
        out[2] = 0x00 // CTS = 0
        out[3] = 0x00
        out[4] = 0x00
        System.arraycopy(configRecord, 0, out, 5, configRecord.size)
        return out
    }

    fun buildAvcDecoderConfigRecord(csd0: ByteArray, csd1: ByteArray?): ByteArray {
        // Check if csd0 is already an AVCDecoderConfigurationRecord (version == 1 and no Annex-B start code)
        if (csd1 == null && csd0.size >= 7 && csd0[0] == 0x01.toByte() && !startsWithAnnexB(csd0)) {
            return csd0
        }

        val nalUnits = mutableListOf<ByteArray>()
        nalUnits.addAll(splitAnnexBNalus(csd0))
        if (csd1 != null) {
            nalUnits.addAll(splitAnnexBNalus(csd1))
        }

        val spsList = nalUnits.filter { it.isNotEmpty() && (it[0].toInt() and 0x1F) == 7 }
        val ppsList = nalUnits.filter { it.isNotEmpty() && (it[0].toInt() and 0x1F) == 8 }

        val firstSps = spsList.firstOrNull()
        val profile = firstSps?.getOrNull(1) ?: 0x42.toByte()
        val compat = firstSps?.getOrNull(2) ?: 0xC0.toByte()
        val level = firstSps?.getOrNull(3) ?: 0x1F.toByte()

        val baos = ByteArrayOutputStream(64)
        baos.write(0x01) // configurationVersion
        baos.write(profile.toInt() and 0xFF)
        baos.write(compat.toInt() and 0xFF)
        baos.write(level.toInt() and 0xFF)
        baos.write(0xFF) // 6 bits reserved (111111) + lengthSizeMinusOne (11 -> 4 bytes)

        baos.write(0xE0 or (spsList.size.coerceAtMost(31)))
        spsList.take(31).forEach { sps ->
            baos.write((sps.size ushr 8) and 0xFF)
            baos.write(sps.size and 0xFF)
            baos.write(sps)
        }

        baos.write(ppsList.size.coerceAtMost(255))
        ppsList.take(255).forEach { pps ->
            baos.write((pps.size ushr 8) and 0xFF)
            baos.write(pps.size and 0xFF)
            baos.write(pps)
        }
        return baos.toByteArray()
    }

    /**
     * Packs one encoded H.264 access unit into an FLV AVC Video Tag body.
     * Supports both Annex-B start-code NALUs and 4-byte AVCC length-prefixed NALUs.
     */
    fun buildAvcVideoFrameTag(frameBytes: ByteArray, isKeyFrame: Boolean, ctsMs: Int = 0): ByteArray {
        val avccPayload = if (startsWithAnnexB(frameBytes)) {
            val nalus = splitAnnexBNalus(frameBytes)
            val baos = ByteArrayOutputStream(frameBytes.size + 16)
            for (nalu in nalus) {
                if (nalu.isEmpty()) continue
                val nalType = nalu[0].toInt() and 0x1F
                // Skip SPS (7) / PPS (8) / AUD (9) inside frame packets; SPS/PPS are sent via sequence header
                if (nalType == 7 || nalType == 8 || nalType == 9) continue
                val len = nalu.size
                baos.write((len ushr 24) and 0xFF)
                baos.write((len ushr 16) and 0xFF)
                baos.write((len ushr 8) and 0xFF)
                baos.write(len and 0xFF)
                baos.write(nalu)
            }
            baos.toByteArray()
        } else {
            frameBytes
        }

        val out = ByteArray(5 + avccPayload.size)
        out[0] = (if (isKeyFrame) 0x17 else 0x27).toByte()
        out[1] = 0x01 // AVC NALU
        out[2] = ((ctsMs ushr 16) and 0xFF).toByte()
        out[3] = ((ctsMs ushr 8) and 0xFF).toByte()
        out[4] = (ctsMs and 0xFF).toByte()
        System.arraycopy(avccPayload, 0, out, 5, avccPayload.size)
        return out
    }

    fun buildAacSequenceHeaderTag(audioSpecificConfig: ByteArray): ByteArray {
        val out = ByteArray(2 + audioSpecificConfig.size)
        out[0] = 0xAF.toByte() // AAC (10), 44kHz (3), 16-bit (1), stereo (1)
        out[1] = 0x00 // AAC sequence header
        System.arraycopy(audioSpecificConfig, 0, out, 2, audioSpecificConfig.size)
        return out
    }

    fun buildAacFrameTag(rawFrame: ByteArray): ByteArray {
        val payloadOffset = if (
            rawFrame.size > 7 &&
            (rawFrame[0].toInt() and 0xFF) == 0xFF &&
            (rawFrame[1].toInt() and 0xF0) == 0xF0
        ) {
            // Strip 7-byte ADTS header if present
            val hasCrc = (rawFrame[1].toInt() and 0x01) == 0
            if (hasCrc && rawFrame.size > 9) 9 else 7
        } else {
            0
        }
        val payloadLen = (rawFrame.size - payloadOffset).coerceAtLeast(0)
        val out = ByteArray(2 + payloadLen)
        out[0] = 0xAF.toByte()
        out[1] = 0x01 // AAC raw
        if (payloadLen > 0) {
            System.arraycopy(rawFrame, payloadOffset, out, 2, payloadLen)
        }
        return out
    }

    fun startsWithAnnexB(data: ByteArray): Boolean {
        if (data.size >= 4 && data[0] == 0.toByte() && data[1] == 0.toByte() &&
            data[2] == 0.toByte() && data[3] == 1.toByte()
        ) return true
        if (data.size >= 3 && data[0] == 0.toByte() && data[1] == 0.toByte() &&
            data[2] == 1.toByte()
        ) return true
        return false
    }

    fun splitAnnexBNalus(data: ByteArray): List<ByteArray> {
        val nalus = mutableListOf<ByteArray>()
        var i = 0
        var naluStart = -1

        while (i < data.size) {
            val startCodeLen = when {
                i + 3 < data.size && data[i] == 0.toByte() && data[i + 1] == 0.toByte() &&
                    data[i + 2] == 0.toByte() && data[i + 3] == 1.toByte() -> 4
                i + 2 < data.size && data[i] == 0.toByte() && data[i + 1] == 0.toByte() &&
                    data[i + 2] == 1.toByte() -> 3
                else -> 0
            }

            if (startCodeLen > 0) {
                if (naluStart in 0 until i) {
                    nalus.add(data.copyOfRange(naluStart, i))
                }
                i += startCodeLen
                naluStart = i
            } else {
                i++
            }
        }

        if (naluStart in 0 until data.size) {
            nalus.add(data.copyOfRange(naluStart, data.size))
        } else if (naluStart == -1 && data.isNotEmpty()) {
            nalus.add(data)
        }
        return nalus
    }

    fun ByteBuffer.toByteArrayCopy(size: Int, offset: Int = 0): ByteArray {
        val dup = this.duplicate()
        dup.position(offset)
        val arr = ByteArray(size)
        dup.get(arr, 0, size)
        return arr
    }
}

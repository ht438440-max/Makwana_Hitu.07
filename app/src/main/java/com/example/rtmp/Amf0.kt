package com.example.rtmp

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets

sealed class AmfValue {
    data class Number(val value: Double) : AmfValue()
    data class Bool(val value: Boolean) : AmfValue()
    data class Str(val value: String) : AmfValue()
    data class Obj(val props: LinkedHashMap<String, AmfValue>) : AmfValue()
    data class EcmaArray(val props: LinkedHashMap<String, AmfValue>) : AmfValue()
    data object Null : AmfValue()
}

object Amf0 {
    private const val TYPE_NUMBER = 0x00
    private const val TYPE_BOOLEAN = 0x01
    private const val TYPE_STRING = 0x02
    private const val TYPE_OBJECT = 0x03
    private const val TYPE_NULL = 0x05
    private const val TYPE_UNDEFINED = 0x06
    private const val TYPE_ECMA_ARRAY = 0x08
    private const val TYPE_OBJECT_END = 0x09

    fun encodeValues(vararg values: AmfValue): ByteArray {
        val baos = ByteArrayOutputStream(256)
        val out = DataOutputStream(baos)
        values.forEach { writeValue(out, it) }
        out.flush()
        return baos.toByteArray()
    }

    fun encodeCommand(name: String, transactionId: Double, vararg args: AmfValue): ByteArray {
        val list = mutableListOf<AmfValue>(AmfValue.Str(name), AmfValue.Number(transactionId))
        list.addAll(args)
        return encodeValues(*list.toTypedArray())
    }

    private fun writeValue(out: DataOutputStream, value: AmfValue) {
        when (value) {
            is AmfValue.Number -> {
                out.writeByte(TYPE_NUMBER)
                out.writeDouble(value.value)
            }
            is AmfValue.Bool -> {
                out.writeByte(TYPE_BOOLEAN)
                out.writeByte(if (value.value) 1 else 0)
            }
            is AmfValue.Str -> {
                out.writeByte(TYPE_STRING)
                writeUtf8(out, value.value)
            }
            is AmfValue.Obj -> {
                out.writeByte(TYPE_OBJECT)
                for ((k, v) in value.props) {
                    writeUtf8(out, k)
                    writeValue(out, v)
                }
                out.writeShort(0)
                out.writeByte(TYPE_OBJECT_END)
            }
            is AmfValue.EcmaArray -> {
                out.writeByte(TYPE_ECMA_ARRAY)
                out.writeInt(value.props.size)
                for ((k, v) in value.props) {
                    writeUtf8(out, k)
                    writeValue(out, v)
                }
                out.writeShort(0)
                out.writeByte(TYPE_OBJECT_END)
            }
            AmfValue.Null -> out.writeByte(TYPE_NULL)
        }
    }

    private fun writeUtf8(out: DataOutputStream, text: String) {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        out.writeShort(bytes.size)
        out.write(bytes)
    }

    fun decodeAll(bytes: ByteArray): List<AmfValue> {
        val input = DataInputStream(ByteArrayInputStream(bytes))
        val results = mutableListOf<AmfValue>()
        while (input.available() > 0) {
            val v = readValue(input) ?: break
            results.add(v)
        }
        return results
    }

    private fun readValue(input: DataInputStream): AmfValue? {
        if (input.available() <= 0) return null
        return when (input.readUnsignedByte()) {
            TYPE_NUMBER -> AmfValue.Number(input.readDouble())
            TYPE_BOOLEAN -> AmfValue.Bool(input.readUnsignedByte() != 0)
            TYPE_STRING -> AmfValue.Str(readUtf8(input))
            TYPE_OBJECT -> readObject(input)
            TYPE_NULL, TYPE_UNDEFINED -> AmfValue.Null
            TYPE_ECMA_ARRAY -> {
                input.readInt() // approximate count
                AmfValue.EcmaArray(readObject(input).props)
            }
            else -> null
        }
    }

    private fun readObject(input: DataInputStream): AmfValue.Obj {
        val map = LinkedHashMap<String, AmfValue>()
        while (input.available() > 0) {
            val keyLen = input.readUnsignedShort()
            if (keyLen == 0) {
                val endMarker = if (input.available() > 0) input.readUnsignedByte() else TYPE_OBJECT_END
                if (endMarker == TYPE_OBJECT_END) break
            }
            val keyBytes = ByteArray(keyLen)
            input.readFully(keyBytes)
            val key = String(keyBytes, StandardCharsets.UTF_8)
            val value = readValue(input) ?: break
            map[key] = value
        }
        return AmfValue.Obj(map)
    }

    private fun readUtf8(input: DataInputStream): String {
        val len = input.readUnsignedShort()
        if (len == 0) return ""
        val bytes = ByteArray(len)
        input.readFully(bytes)
        return String(bytes, StandardCharsets.UTF_8)
    }
}

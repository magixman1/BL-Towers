package com.lighttowers.ble.ble

import java.util.UUID

enum class RgbOrder(val label: String, val description: String) {
    RGB("RGB", "Standard (R -> G -> B)"),
    RBG("RBG", "Zamiana G <-> B"),
    GRB("GRB", "Zamiana R <-> G"),
    GBR("GBR", "Przesunięcie G -> B -> R"),
    BRG("BRG", "Przesunięcie B -> R -> G"),
    BGR("BGR", "Odwrócone (B -> G -> R)");

    fun remap(r: Int, g: Int, b: Int): Triple<Int, Int, Int> {
        val rr = r.coerceIn(0, 255)
        val gg = g.coerceIn(0, 255)
        val bb = b.coerceIn(0, 255)
        return when (this) {
            RGB -> Triple(rr, gg, bb)
            RBG -> Triple(rr, bb, gg)
            GRB -> Triple(gg, rr, bb)
            GBR -> Triple(gg, bb, rr)
            BRG -> Triple(bb, rr, gg)
            BGR -> Triple(bb, gg, rr)
        }
    }
}

data class LedEffect(
    val id: Int,
    val name: String,
    val code: Byte
)

object ElkBledomProtocol {
    val SERVICE_UUID: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    val WRITE_CHAR_UUID: UUID = UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb")

    val EFFECTS = listOf(
        LedEffect(1, "Skok 3 kolory", 0x87.toByte()),
        LedEffect(2, "Skok 7 kolorów", 0x88.toByte()),
        LedEffect(3, "Płynne przejście 3", 0x89.toByte()),
        LedEffect(4, "Płynne przejście 7", 0x8A.toByte()),
        LedEffect(5, "Pulsowanie Czerwony", 0x8B.toByte()),
        LedEffect(6, "Pulsowanie Zielony", 0x8C.toByte()),
        LedEffect(7, "Pulsowanie Niebieski", 0x8D.toByte()),
        LedEffect(8, "Pulsowanie Żółty", 0x8E.toByte()),
        LedEffect(9, "Pulsowanie Cyjan", 0x8F.toByte()),
        LedEffect(10, "Pulsowanie Fiolet", 0x90.toByte()),
        LedEffect(11, "Pulsowanie Biały", 0x91.toByte()),
        LedEffect(12, "Stroboskop 7 kolorów", 0x95.toByte())
    )

    fun power(on: Boolean): ByteArray {
        return if (on) {
            byteArrayOf(
                0x7E.toByte(), 0x00, 0x04, 0xF0.toByte(),
                0x00, 0x01, 0xFF.toByte(), 0x00, 0xEF.toByte()
            )
        } else {
            byteArrayOf(
                0x7E.toByte(), 0x00, 0x04, 0x00,
                0x00, 0x00, 0xFF.toByte(), 0x00, 0xEF.toByte()
            )
        }
    }

    fun color(r: Int, g: Int, b: Int, order: RgbOrder): ByteArray {
        val (c1, c2, c3) = order.remap(r, g, b)
        return byteArrayOf(
            0x7E.toByte(), 0x00, 0x05, 0x03,
            c1.toByte(), c2.toByte(), c3.toByte(),
            0x00, 0xEF.toByte()
        )
    }

    fun brightness(percent: Int): ByteArray {
        val level = percent.coerceIn(0, 100).toByte()
        return byteArrayOf(
            0x7E.toByte(), 0x00, 0x01, level,
            0x00, 0x00, 0x00, 0x00, 0xEF.toByte()
        )
    }

    fun effect(effectCode: Byte): ByteArray {
        return byteArrayOf(
            0x7E.toByte(), 0x00, 0x03, effectCode,
            0x03, 0x00, 0x00, 0x00, 0xEF.toByte()
        )
    }

    fun effectSpeed(percent: Int): ByteArray {
        val speed = percent.coerceIn(0, 100).toByte()
        return byteArrayOf(
            0x7E.toByte(), 0x00, 0x02, speed,
            0x00, 0x00, 0x00, 0x00, 0xEF.toByte()
        )
    }

    fun toHexString(bytes: ByteArray): String =
        bytes.joinToString(" ") { "%02X".format(it) }
}

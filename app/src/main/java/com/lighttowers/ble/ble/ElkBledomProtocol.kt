package com.lighttowers.ble.ble

import java.util.UUID
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

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

    /**
     * Wysyła kolor RGB z opcją wzmocnienia jasności (Turbo Boost > 100%).
     * Dla jasności 1..100% skaluje jasność sprzętową, a dla 101..150%
     * maksymalizuje kanały RGB w kierunku bieli dla większej luminancji.
     */
    fun color(r: Int, g: Int, b: Int, order: RgbOrder, boostPercent: Int = 100): ByteArray {
        val (br, bg, bb) = applyBrightnessBoost(r, g, b, boostPercent)
        val (c1, c2, c3) = order.remap(br, bg, bb)
        return byteArrayOf(
            0x7E.toByte(), 0x00, 0x05, 0x03,
            c1.toByte(), c2.toByte(), c3.toByte(),
            0x00, 0xEF.toByte()
        )
    }

    fun applyBrightnessBoost(r: Int, g: Int, b: Int, brightnessPercent: Int): Triple<Int, Int, Int> {
        val rr = r.coerceIn(0, 255)
        val gg = g.coerceIn(0, 255)
        val bb = b.coerceIn(0, 255)
        if (brightnessPercent <= 100) {
            return Triple(rr, gg, bb)
        }
        // Tryb Turbo (101% - 150%): podbicie składowych do maksymalnego wysterowania diod
        val factor = ((brightnessPercent - 100) / 50f).coerceIn(0f, 1f)
        val maxChannel = maxOf(rr, gg, bb)
        if (maxChannel == 0) {
            val whiteLift = (255 * factor).roundToInt()
            return Triple(whiteLift, whiteLift, whiteLift)
        }
        // Najpierw normalizacja do pełnej mocy (255), potem domieszka białych subpikseli
        val scale = 255f / maxChannel.toFloat()
        val normR = (rr * scale).coerceAtMost(255f)
        val normG = (gg * scale).coerceAtMost(255f)
        val normB = (bb * scale).coerceAtMost(255f)

        val whiteMix = factor * 0.45f
        val outR = (normR + (255f - normR) * whiteMix).roundToInt().coerceIn(0, 255)
        val outG = (normG + (255f - normG) * whiteMix).roundToInt().coerceIn(0, 255)
        val outB = (normB + (255f - normB) * whiteMix).roundToInt().coerceIn(0, 255)
        return Triple(outR, outG, outB)
    }

    /**
     * Sterownik ELK-BLEDOM przyjmuje wartość 0x00..0x64 (0..100%)
     * lub rozszerzone wartości 0x64..0xFF w niektórych rewizjach firmware.
     */
    fun brightness(percent: Int): ByteArray {
        val hwLevel = if (percent <= 100) {
            percent.coerceIn(1, 100)
        } else {
            // Mapowanie 101..150% na 100..255 (0x64..0xFF) dla kontrolerów obsługujących pełny zakres bajtu
            val extra = ((percent - 100) / 50f * 155f).roundToInt()
            (100 + extra).coerceIn(100, 255)
        }
        return byteArrayOf(
            0x7E.toByte(), 0x00, 0x01, hwLevel.toByte(),
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

    fun kelvinToRgb(kelvin: Int): Triple<Int, Int, Int> {
        val temp = kelvin.coerceIn(2000, 9000) / 100.0
        val r: Double = if (temp <= 66.0) {
            255.0
        } else {
            329.698727446 * (temp - 60.0).pow(-0.1332047592)
        }
        val g: Double = if (temp <= 66.0) {
            99.4708025861 * ln(temp) - 161.1195681661
        } else {
            288.1221695283 * (temp - 60.0).pow(-0.0755148492)
        }
        val b: Double = when {
            temp >= 66.0 -> 255.0
            temp <= 19.0 -> 0.0
            else -> 138.5177312231 * ln(temp - 10.0) - 305.0447927307
        }
        return Triple(
            r.roundToInt().coerceIn(0, 255),
            g.roundToInt().coerceIn(0, 255),
            b.roundToInt().coerceIn(0, 255)
        )
    }

    fun toHexString(bytes: ByteArray): String =
        bytes.joinToString(" ") { "%02X".format(it) }
}

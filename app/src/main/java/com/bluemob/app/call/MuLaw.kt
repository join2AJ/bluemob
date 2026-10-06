package com.bluemob.app.call

/**
 * G.711 μ-law: squeezes 16-bit voice samples into 8 bits, the way phone networks do. At 8 kHz that's
 * 64 kbit/s, small enough for a Bluetooth link between phones, and it needs no codec library.
 */
object MuLaw {
    private const val BIAS = 0x84
    private const val CLIP = 32635

    fun encode(sample: Short): Byte {
        var s = sample.toInt()
        val sign = if (s < 0) 0x80 else 0
        if (s < 0) s = -s
        if (s > CLIP) s = CLIP
        s += BIAS
        var exponent = 7
        var mask = 0x4000
        while (exponent > 0 && s and mask == 0) { exponent--; mask = mask shr 1 }
        val mantissa = (s shr (exponent + 3)) and 0x0F
        return (sign or (exponent shl 4) or mantissa).inv().toByte()
    }

    fun decode(b: Byte): Short {
        val u = b.toInt().inv() and 0xFF
        val sign = u and 0x80
        val exponent = (u shr 4) and 0x07
        val mantissa = u and 0x0F
        val s = (((mantissa shl 3) + BIAS) shl exponent) - BIAS
        return (if (sign != 0) -s else s).toShort()
    }

    fun encode(pcm: ShortArray, count: Int = pcm.size, out: ByteArray = ByteArray(count), offset: Int = 0): ByteArray {
        for (i in 0 until count) out[offset + i] = encode(pcm[i])
        return out
    }

    fun decode(bytes: ByteArray, offset: Int = 0, count: Int = bytes.size - offset): ShortArray =
        ShortArray(count) { decode(bytes[offset + it]) }
}

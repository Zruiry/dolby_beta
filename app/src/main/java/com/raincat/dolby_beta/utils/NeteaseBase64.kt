/**
 * Base64编解码工具 - 网易云音乐参数加密的配套组件
 * 手动实现Base64编解码，避免依赖Android API版本差异
 *
 */
package com.raincat.dolby_beta.utils

object NeteaseBase64 {

    private val base64Alphabet = ByteArray(128)
    private val lookUpBase64Alphabet = CharArray(64)

    init {
        // 初始化解码表
        for (i in 0 until 128) {
            base64Alphabet[i] = -1
        }
        for (i in 90 downTo 65) {
            base64Alphabet[i] = (i - 65).toByte()
        }
        for (i in 122 downTo 97) {
            base64Alphabet[i] = (i - 97 + 26).toByte()
        }
        for (i in 57 downTo 48) {
            base64Alphabet[i] = (i - 48 + 52).toByte()
        }
        base64Alphabet[43] = 62
        base64Alphabet[47] = 63

        // 初始化编码表
        for (i in 0..25) {
            lookUpBase64Alphabet[i] = (65 + i).toChar()
        }
        var idx = 26
        for (j in 0..25) {
            lookUpBase64Alphabet[idx++] = (97 + j).toChar()
        }
        idx = 52
        for (j in 0..9) {
            lookUpBase64Alphabet[idx++] = (48 + j).toChar()
        }
        lookUpBase64Alphabet[62] = '+'
        lookUpBase64Alphabet[63] = '/'
    }

    private fun isWhiteSpace(octect: Char): Boolean {
        return octect == ' ' || octect == '\r' || octect == '\n' || octect == '\t'
    }

    private fun isPad(octect: Char): Boolean {
        return octect == '='
    }

    private fun isData(octect: Char): Boolean {
        return octect.code < 128 && base64Alphabet[octect.code].toInt() != -1
    }

    @JvmStatic
    fun isBase64(octect: Char): Boolean {
        return isWhiteSpace(octect) || isPad(octect) || isData(octect)
    }

    /**
     * Base64编码
     */
    @JvmStatic
    fun encode(binaryData: ByteArray?): String? {
        if (binaryData == null) return null
        val lengthDataBits = binaryData.size * 8
        if (lengthDataBits == 0) return ""

        val fewerThan24bits = lengthDataBits % 24
        val numberTriplets = lengthDataBits / 24
        val numberQuartet = if (fewerThan24bits != 0) numberTriplets + 1 else numberTriplets
        val encodedData = CharArray(numberQuartet * 4)

        var k: Byte = 0
        var l: Byte = 0
        var b1: Byte = 0
        var b2: Byte = 0
        var b3: Byte = 0

        var encodedIndex = 0
        var dataIndex = 0

        for (i in 0 until numberTriplets) {
            b1 = binaryData[dataIndex++]
            b2 = binaryData[dataIndex++]
            b3 = binaryData[dataIndex++]

            l = (b2.toInt() and 0xF).toByte()
            k = (b1.toInt() and 0x3).toByte()

            val val1 = if (b1.toInt() and 0xFFFFFF80.toInt() == 0) (b1.toInt() shr 2).toByte() else (b1.toInt() shr 2 xor 0xC0).toByte()
            val val2 = if (b2.toInt() and 0xFFFFFF80.toInt() == 0) (b2.toInt() shr 4).toByte() else (b2.toInt() shr 4 xor 0xF0).toByte()
            val val3 = if (b3.toInt() and 0xFFFFFF80.toInt() == 0) (b3.toInt() shr 6).toByte() else (b3.toInt() shr 6 xor 0xFC).toByte()

            encodedData[encodedIndex++] = lookUpBase64Alphabet[val1.toInt()]
            encodedData[encodedIndex++] = lookUpBase64Alphabet[(val2.toInt() or (k.toInt() shl 4))]
            encodedData[encodedIndex++] = lookUpBase64Alphabet[(l.toInt() shl 2) or val3.toInt()]
            encodedData[encodedIndex++] = lookUpBase64Alphabet[b3.toInt() and 0x3F]
        }

        if (fewerThan24bits == 8) {
            b1 = binaryData[dataIndex]
            k = (b1.toInt() and 0x3).toByte()
            val val1 = if (b1.toInt() and 0xFFFFFF80.toInt() == 0) (b1.toInt() shr 2).toByte() else (b1.toInt() shr 2 xor 0xC0).toByte()
            encodedData[encodedIndex++] = lookUpBase64Alphabet[val1.toInt()]
            encodedData[encodedIndex++] = lookUpBase64Alphabet[k.toInt() shl 4]
            encodedData[encodedIndex++] = '='
            encodedData[encodedIndex++] = '='
        } else if (fewerThan24bits == 16) {
            b1 = binaryData[dataIndex]
            b2 = binaryData[dataIndex + 1]
            l = (b2.toInt() and 0xF).toByte()
            k = (b1.toInt() and 0x3).toByte()
            val val1 = if (b1.toInt() and 0xFFFFFF80.toInt() == 0) (b1.toInt() shr 2).toByte() else (b1.toInt() shr 2 xor 0xC0).toByte()
            val val2 = if (b2.toInt() and 0xFFFFFF80.toInt() == 0) (b2.toInt() shr 4).toByte() else (b2.toInt() shr 4 xor 0xF0).toByte()
            encodedData[encodedIndex++] = lookUpBase64Alphabet[val1.toInt()]
            encodedData[encodedIndex++] = lookUpBase64Alphabet[val2.toInt() or (k.toInt() shl 4)]
            encodedData[encodedIndex++] = lookUpBase64Alphabet[l.toInt() shl 2]
            encodedData[encodedIndex++] = '='
        }

        return String(encodedData)
    }

    /**
     * Base64解码
     */
    @JvmStatic
    fun decode(encoded: String?): ByteArray? {
        if (encoded == null) return null
        val base64Data = encoded.toCharArray()
        val len = removeWhiteSpace(base64Data)
        if (len % 4 != 0) return null
        val numberQuadruple = len / 4
        if (numberQuadruple == 0) return ByteArray(0)

        var b1: Byte = 0
        var b2: Byte = 0
        var b3: Byte = 0
        var b4: Byte = 0
        var d1: Char = '\u0000'
        var d2: Char = '\u0000'
        var d3: Char = '\u0000'
        var d4: Char = '\u0000'

        var i = 0
        var encodedIndex = 0
        var dataIndex = 0
        val decodedData = ByteArray(numberQuadruple * 3)

        while (i < numberQuadruple - 1) {
            d1 = base64Data[dataIndex++]
            d2 = base64Data[dataIndex++]
            d3 = base64Data[dataIndex++]
            d4 = base64Data[dataIndex++]
            if (!isData(d1) || !isData(d2) || !isData(d3) || !isData(d4)) return null
            b1 = base64Alphabet[d1.code]
            b2 = base64Alphabet[d2.code]
            b3 = base64Alphabet[d3.code]
            b4 = base64Alphabet[d4.code]
            decodedData[encodedIndex++] = (b1.toInt() shl 2 or (b2.toInt() shr 4)).toByte()
            decodedData[encodedIndex++] = ((b2.toInt() and 0xF) shl 4 or (b3.toInt() shr 2 and 0xF)).toByte()
            decodedData[encodedIndex++] = (b3.toInt() shl 6 or b4.toInt()).toByte()
            i++
        }

        d1 = base64Data[dataIndex++]
        d2 = base64Data[dataIndex++]
        if (!isData(d1) || !isData(d2)) return null
        b1 = base64Alphabet[d1.code]
        b2 = base64Alphabet[d2.code]

        d3 = base64Data[dataIndex++]
        d4 = base64Data[dataIndex++]
        if (!isData(d3) || !isData(d4)) {
            if (isPad(d3) && isPad(d4)) {
                if (b2.toInt() and 0xF != 0) return null
                val tmp = ByteArray(i * 3 + 1)
                System.arraycopy(decodedData, 0, tmp, 0, i * 3)
                tmp[encodedIndex] = (b1.toInt() shl 2 or (b2.toInt() shr 4)).toByte()
                return tmp
            }
            if (!isPad(d3) && isPad(d4)) {
                b3 = base64Alphabet[d3.code]
                if (b3.toInt() and 0x3 != 0) return null
                val tmp = ByteArray(i * 3 + 2)
                System.arraycopy(decodedData, 0, tmp, 0, i * 3)
                tmp[encodedIndex++] = (b1.toInt() shl 2 or (b2.toInt() shr 4)).toByte()
                tmp[encodedIndex] = ((b2.toInt() and 0xF) shl 4 or (b3.toInt() shr 2 and 0xF)).toByte()
                return tmp
            }
            return null
        }

        b3 = base64Alphabet[d3.code]
        b4 = base64Alphabet[d4.code]
        decodedData[encodedIndex++] = (b1.toInt() shl 2 or (b2.toInt() shr 4)).toByte()
        decodedData[encodedIndex++] = ((b2.toInt() and 0xF) shl 4 or (b3.toInt() shr 2 and 0xF)).toByte()
        decodedData[encodedIndex++] = (b3.toInt() shl 6 or b4.toInt()).toByte()

        return decodedData
    }

    private fun removeWhiteSpace(data: CharArray?): Int {
        if (data == null) return 0
        var newSize = 0
        val len = data.size
        for (i in 0 until len) {
            if (!isWhiteSpace(data[i])) {
                data[newSize++] = data[i]
            }
        }
        return newSize
    }
}

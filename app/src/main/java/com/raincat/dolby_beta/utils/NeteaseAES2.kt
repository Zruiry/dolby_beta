/**
 * EAPI加解密工具 - AES-128-ECB模式
 * 加密算法：AES/ECB/PKCS5Padding
 * 输出格式：Hex化字符串
 *
 */
package com.raincat.dolby_beta.utils

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object NeteaseAES2 {

    /** EAPI固定密钥 */
    private val AES_KEY = "e82ckenh8dichen8".toByteArray()

    /**
     * AES-128-ECB加密，输出Hex字符串
     *
     * @param sSrc 待加密明文
     * @return Hex编码的密文（大写），失败返回null
     */
    @JvmStatic
    fun encrypt(sSrc: String): String? {
        return try {
            val skeySpec = SecretKeySpec(AES_KEY, "AES")
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec)
            val encrypted = cipher.doFinal(sSrc.toByteArray())
            byteToHex(encrypted)
        } catch (ex: Exception) {
            null
        }
    }

    /**
     * AES-128-ECB解密，输入Hex字符串
     *
     * @param sSrc Hex编码的密文
     * @return 解密后的明文，失败返回null
     */
    @JvmStatic
    fun decrypt(sSrc: String): String? {
        return try {
            val encrypted = hexToByte(sSrc)
            val skeySpec = SecretKeySpec(AES_KEY, "AES")
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, skeySpec)
            String(cipher.doFinal(encrypted))
        } catch (ex: Exception) {
            null
        }
    }

    /**
     * Hex字符串转byte数组
     */
    @JvmStatic
    fun hexToByte(hex: String): ByteArray {
        val byteLen = hex.length / 2
        val ret = ByteArray(byteLen)
        for (i in 0 until byteLen) {
            val m = i * 2 + 1
            val n = m + 1
            val intVal = Integer.decode("0x${hex.substring(i * 2, m)}${hex.substring(m, n)}")
            ret[i] = intVal.toByte()
        }
        return ret
    }

    /**
     * byte数组转Hex字符串（大写）
     */
    @JvmStatic
    fun byteToHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            val strHex = Integer.toHexString(b.toInt() and 0xFF)
            // 每个字节由两个字符表示，位数不够高位补0
            sb.append(if (strHex.length == 1) "0$strHex" else strHex)
        }
        return sb.toString().trim { it <= ' ' }.uppercase()
    }
}

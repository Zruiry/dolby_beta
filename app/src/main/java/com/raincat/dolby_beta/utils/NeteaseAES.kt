/**
 * AES-128-CBC加解密工具 - 网易云音乐参数加密
 * 加密算法：AES/CBC/PKCS5Padding
 * 输出格式：Base64
 *
 */
package com.raincat.dolby_beta.utils

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object NeteaseAES {

    /** 固定的第一个加密密钥 */
    private const val FIRST_KEY = "0CoJUm6Qyw8W8jud"
    /** 固定的第二个加密密钥（随机值占位） */
    private const val SECOND_KEY = "FFFFFFFFFFFFFFFF"
    /** CBC模式偏移量 */
    private val IV_BYTES = "0102030405060708".toByteArray()

    /**
     * AES-128-CBC加密，输出Base64
     *
     * @param sSrc 待加密明文
     * @param sKey 16位密钥
     * @return Base64编码的密文，失败返回null
     */
    @JvmStatic
    fun encrypt(sSrc: String, sKey: String): String? {
        if (sKey.length != 16) return null
        val raw = sKey.toByteArray()
        val skeySpec = SecretKeySpec(raw, "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, skeySpec, IvParameterSpec(IV_BYTES))
        val encrypted = cipher.doFinal(sSrc.toByteArray())
        return NeteaseBase64.encode(encrypted)
    }

    /**
     * AES-128-CBC解密，输入Base64
     *
     * @param sSrc Base64编码的密文
     * @param sKey 16位密钥
     * @return 解密后的明文，失败返回null
     */
    @JvmStatic
    fun decrypt(sSrc: String, sKey: String): String? {
        if (sKey.length != 16) return null
        val raw = sKey.toByteArray(Charsets.UTF_8)
        val skeySpec = SecretKeySpec(raw, "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, skeySpec, IvParameterSpec(IV_BYTES))
        val encrypted1 = NeteaseBase64.decode(sSrc) ?: return null
        return try {
            String(cipher.doFinal(encrypted1))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 获取加密后的params参数
     * 两层AES加密：先用FIRST_KEY加密，再用SECOND_KEY加密
     */
    @JvmStatic
    fun getParams(text: String): String? {
        val hEncText = encrypt(text, FIRST_KEY) ?: return null
        return encrypt(hEncText, SECOND_KEY)
    }

    /**
     * 获取固定的encSecKey参数
     * 由于输入参数固定，所以encSecKey也是固定值
     */
    @JvmStatic
    fun getEncSecKey(): String {
        return "257348aecb5e556c066de214e531faadd1c55d814f9be95fd06d6bff9f4c7a41f831f6394d5a3fd2e3881736d94a02ca919d952872e7d0a50ebfa1769a7a62d512f5f1ca21aec60bc3819a9c3ffca5eca9a0dba6d6f7249b06f5965ecfff3695b54e1c28f3f624750ed39e7de08fc8493242e26dbc4484a01c76f739e135637c"
    }
}

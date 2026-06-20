/**
 * 下载MD5修复Hook - 下载强制返回正确MD5
 *
 * 功能：
 * 1. Hook MD5检查方法 - 替换为实际文件计算的MD5
 * 2. Hook 下载状态检查方法 - 返回文件实际大小
 *
 * 参考dev分支DownloadMD5Hook实现
 *
 * 创建日期：2026-06-21
 * 作者：RainCat
 */
package com.raincat.dolby_beta.hook

import android.content.Context
import com.raincat.dolby_beta.helper.ClassHelper
import com.raincat.dolby_beta.utils.LogUtils
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest

class DownloadMD5Hook(
    private val module: XposedModule,
    private val context: Context
) {
    companion object {
        private const val TAG = "DownloadMD5Hook"
    }

    init {
        try {
            hookCheckMd5()
            hookCheckDownloadStatus()
            LogUtils.i("$TAG: 初始化完成")
        } catch (e: Throwable) {
            LogUtils.e("$TAG: 初始化失败 - ${e.message}")
        }
    }

    /**
     * Hook MD5检查方法 - 替换参数中的MD5为实际文件计算的MD5
     * 第4个参数是Object数组，其第6个元素（index=5）为MD5值
     */
    private fun hookCheckMd5() {
        val checkMd5Method = ClassHelper.DownloadTransfer.getCheckMd5Method(context) ?: run {
            LogUtils.w("$TAG: 未找到MD5检查方法")
            return
        }

        module.hook(checkMd5Method).intercept(object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                try {
                    val array = chain.getArg(3) as? Array<Any> ?: return chain.proceed()
                    val path = chain.getArg(0).toString()
                    val md5 = fileToMD5(path)
                    if (md5 != null) {
                        array[5] = md5
                        LogUtils.i("$TAG: 替换MD5 - path=$path")
                        return chain.proceed(arrayOf(chain.getArg(0), chain.getArg(1), chain.getArg(2), array))
                    }
                } catch (e: Throwable) {
                    LogUtils.e("$TAG: hookCheckMd5 异常 - ${e.message}")
                }
                return chain.proceed()
            }
        })
        LogUtils.i("$TAG: hookCheckMd5 成功")
    }

    /**
     * Hook 下载状态检查方法 - 返回文件实际大小
     * 遍历参数对象的返回long的方法，返回文件大小
     */
    private fun hookCheckDownloadStatus() {
        val checkStatusMethod = ClassHelper.DownloadTransfer.getCheckDownloadStatusMethod(context) ?: run {
            LogUtils.w("$TAG: 未找到下载状态检查方法")
            return
        }

        module.hook(checkStatusMethod).intercept(object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                try {
                    val arg0 = chain.getArg(0) ?: return chain.proceed()
                    val methods = arg0.javaClass.declaredMethods
                    for (m in methods) {
                        if (m.returnType == Long::class.javaPrimitiveType) {
                            m.isAccessible = true
                            val length = m.invoke(arg0) as Long
                            LogUtils.i("$TAG: 返回文件大小 - $length")
                            return length
                        }
                    }
                } catch (e: Throwable) {
                    LogUtils.e("$TAG: hookCheckDownloadStatus 异常 - ${e.message}")
                }
                return chain.proceed()
            }
        })
        LogUtils.i("$TAG: hookCheckDownloadStatus 成功")
    }

    /**
     * 计算文件的MD5值
     */
    private fun fileToMD5(filePath: String): String? {
        return try {
            FileInputStream(filePath).use { inputStream ->
                val buffer = ByteArray(1024)
                val digest = MessageDigest.getInstance("MD5")
                var numRead: Int
                while (inputStream.read(buffer).also { numRead = it } != -1) {
                    if (numRead > 0) {
                        digest.update(buffer, 0, numRead)
                    }
                }
                convertHashToString(digest.digest())
            }
        } catch (e: Exception) {
            LogUtils.e("$TAG: fileToMD5失败 - ${e.message}")
            null
        }
    }

    /**
     * 将字节数组转换为十六进制字符串
     */
    private fun convertHashToString(hashBytes: ByteArray): String {
        val sb = StringBuilder()
        for (hashByte in hashBytes) {
            sb.append(Integer.toString((hashByte.toInt() and 0xff) + 0x100, 16).substring(1))
        }
        return sb.toString().toLowerCase()
    }
}

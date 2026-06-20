/**
 * Magisk修复Hook - 修复Magisk冲突导致的无法读写外置SD卡
 *
 * 功能：Hook NeteaseMusicUtils的存储路径获取方法，返回正确的存储路径列表
 * 包含主存储卡路径和外置SD卡路径（如果已挂载）
 *
 * 参考dev分支MagiskFixHook实现
 *
 * 创建日期：2026-06-21
 * 作者：RainCat
 */
package com.raincat.dolby_beta.hook

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import com.raincat.dolby_beta.utils.LogUtils
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.util.ArrayList

class MagiskFixHook(
    private val module: XposedModule,
    private val context: Context
) {
    companion object {
        private const val TAG = "MagiskFixHook"
    }

    init {
        try {
            hookStoragePath()
            LogUtils.i("$TAG: 初始化完成")
        } catch (e: Throwable) {
            LogUtils.e("$TAG: 初始化失败 - ${e.message}")
        }
    }

    /**
     * Hook NeteaseMusicUtils的存储路径获取方法
     * 查找返回List、参数为boolean的方法，按方法名排序取第一个
     * 替换返回值为正确的存储路径列表
     */
    private fun hookStoragePath() {
        val utilsClass = findClassIfExists("com.netease.cloudmusic.utils.NeteaseMusicUtils", context.classLoader) ?: run {
            LogUtils.w("$TAG: NeteaseMusicUtils类未找到")
            return
        }

        // 查找返回List、参数为boolean的所有方法，按方法名排序取第一个
        val targetMethod = utilsClass.declaredMethods
            .filter { it.returnType == java.util.List::class.java && it.parameterTypes.size == 1 && it.parameterTypes[0] == Boolean::class.javaPrimitiveType }
            .minByOrNull { it.name }
            ?: run {
                LogUtils.w("$TAG: 未找到存储路径获取方法")
                return
            }

        module.hook(targetMethod).intercept(object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                try {
                    val list = ArrayList<String>()
                    list.add(Environment.getExternalStorageDirectory().absolutePath)

                    // 获取外置SD卡路径
                    val sdCard = getSecondaryStoragePath(context)
                    if (sdCard != null) {
                        val state = getStorageState(context, sdCard)
                        if (state != null && state.contains(Environment.MEDIA_MOUNTED)) {
                            list.add(sdCard)
                        }
                    }
                    LogUtils.i("$TAG: 返回存储路径列表 - $list")
                    return list
                } catch (e: Throwable) {
                    LogUtils.e("$TAG: hookStoragePath 异常 - ${e.message}")
                    return chain.proceed()
                }
            }
        })
        LogUtils.i("$TAG: hookStoragePath 成功")
    }

    /**
     * 获取次存储卡路径（一般是外置TF卡，也可能是USB OTG）
     */
    private fun getSecondaryStoragePath(context: Context): String? {
        return try {
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val getVolumePathsMethod = StorageManager::class.java.getMethod("getVolumePaths")
            val paths = getVolumePathsMethod.invoke(sm) as? Array<*>
            if (paths == null || paths.size <= 1) null else paths[1] as String
        } catch (e: Exception) {
            LogUtils.e("$TAG: 获取外置SD卡路径失败 - ${e.message}")
            null
        }
    }

    /**
     * 获取存储卡的挂载状态
     */
    private fun getStorageState(context: Context, path: String): String? {
        return try {
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val getVolumeStateMethod = StorageManager::class.java.getMethod("getVolumeState", String::class.java)
            getVolumeStateMethod.invoke(sm, path) as String
        } catch (e: Exception) {
            LogUtils.e("$TAG: 获取存储状态失败 - ${e.message}")
            null
        }
    }

    private fun findClassIfExists(className: String, classLoader: ClassLoader): Class<*>? {
        return try {
            classLoader.loadClass(className)
        } catch (e: ClassNotFoundException) {
            null
        }
    }
}

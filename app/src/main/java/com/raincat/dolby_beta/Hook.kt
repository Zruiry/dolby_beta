/**
 * Hook入口（标准版网易云）- 仅保留音源代理功能
 * 使用Modern libxposed API 102（XposedModule + Hooker拦截器链）
 *
 * 初始化分两个阶段（参考dev分支）：
 * 1. attachBaseContext阶段（isEarly=true）：初始化ProxyHook和启动脚本
 *    此时OkHttpClient还未构建，hook addInterceptor能在cronet拦截器添加前生效
 * 2. onCreate阶段（isEarly=false）：初始化SettingHook、EAPIHook、CdnHook等
 *    此时Application已完全初始化，可注册ActivityLifecycleCallbacks
 *
 */
package com.raincat.dolby_beta

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.raincat.dolby_beta.helper.ClassHelper
import com.raincat.dolby_beta.helper.ExtraHelper
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.hook.AdRemoveHook
import com.raincat.dolby_beta.hook.BeautyHook
import com.raincat.dolby_beta.hook.CdnHook
import com.raincat.dolby_beta.hook.EAPIHook
import com.raincat.dolby_beta.hook.ProxyHook
import com.raincat.dolby_beta.hook.SettingHook
import com.raincat.dolby_beta.utils.LogUtils
import com.raincat.dolby_beta.utils.Tools
import io.github.libxposed.api.XposedModule

class Hook(
    private val module: XposedModule,
    context: Context,
    isEarly: Boolean
) {
    companion object {
        private const val PACKAGE_NAME = "com.netease.cloudmusic"
        /** 广播Action - 发送通知 */
        const val MSG_SEND_NOTIFICATION = "sendNotification"
    }

    init {
        try {
            val packageInfo = context.packageManager.getPackageInfo(PACKAGE_NAME, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
            ExtraHelper.init(context)
            SettingHelper.init(context)

            val processName = Tools.getCurrentProcessName(context)
            val phase = if (isEarly) "attachBaseContext" else "onCreate"
            LogUtils.i("Hook: 初始化[$phase] - 进程=$processName, versionCode=$versionCode")

            if (processName == PACKAGE_NAME) {
                initMainProcess(module, context, versionCode, isEarly)
            } else if (processName == "$PACKAGE_NAME:play") {
                initPlayProcess(module, context, versionCode, isEarly)
            }
        } catch (e: Exception) {
            LogUtils.e("Hook: 初始化失败 - ${e.message}")
        }
    }

    /**
     * 主进程初始化
     * - attachBaseContext阶段：ProxyHook + AdRemoveHook + SongPrivilege（最早时机，cronet还未添加）
     * - onCreate阶段：SettingHook + EAPIHook + CdnHook + 美化Hook（需要Application完全初始化）
     */
    private fun initMainProcess(module: XposedModule, context: Context, versionCode: Int, isEarly: Boolean) {
        if (isEarly) {
            // attachBaseContext阶段：初始化ProxyHook和启动脚本
            // 此时OkHttpClient还未构建，hook addInterceptor能在cronet拦截器添加前生效
            // SongPrivilege hook不依赖主开关，提前到attachBaseContext阶段避免无版权弹窗
            EAPIHook.hookSongPrivilege(module, context)

            if (SettingHelper.getInstance().getSetting(SettingHelper.master_key)) {
                ProxyHook(module, context, false)
                LogUtils.i("Hook: 主进程[attachBaseContext] ProxyHook初始化完成")
            } else {
                LogUtils.i("Hook: 主开关未启用，跳过ProxyHook")
            }

            // 去广告：URL 黑洞拦截 + 旧开屏 + 清理本地广告缓存
            try {
                AdRemoveHook(module, context)
                LogUtils.i("Hook: 主进程[attachBaseContext] 去广告Hook初始化完成")
            } catch (e: Throwable) {
                LogUtils.e("Hook: 去广告Hook初始化失败 - ${e.message}")
            }
        } else {
            // onCreate阶段：初始化SettingHook、EAPIHook、CdnHook
            // 统一等待 DEX 扫描完成，避免多次调用 getCacheClassList
            ClassHelper.getCacheClassList(context, versionCode, object : ClassHelper.OnCacheClassListener {
                override fun onGet() {
                    SettingHook(module, context)

                    if (SettingHelper.getInstance().getSetting(SettingHelper.master_key)) {
                        EAPIHook(module, context)
                        CdnHook(module, versionCode)
                        initFeatureHooks(module, context, versionCode)
                    }
                    LogUtils.i("Hook: 主进程[onCreate]初始化完成")
                }
            })
        }
    }

    /**
     * 初始化功能Hook（美化功能）
     */
    private fun initFeatureHooks(module: XposedModule, context: Context, versionCode: Int) {
        try {
            BeautyHook(module, context, versionCode)
            LogUtils.i("Hook: 功能Hook初始化完成")
        } catch (e: Throwable) {
            LogUtils.e("Hook: 功能Hook初始化失败 - ${e.message}")
        }
    }

    /**
     * 播放进程初始化
     * - attachBaseContext阶段：ProxyHook + SongPrivilege
     * - onCreate阶段：EAPIHook + CdnHook
     */
    private fun initPlayProcess(module: XposedModule, context: Context, versionCode: Int, isEarly: Boolean) {
        if (isEarly) {
            // attachBaseContext阶段：SongPrivilege hook不依赖主开关，提前避免无版权弹窗
            EAPIHook.hookSongPrivilege(module, context)

            if (SettingHelper.getInstance().getSetting(SettingHelper.master_key)) {
                ProxyHook(module, context, true)
                LogUtils.i("Hook: 播放进程[attachBaseContext] ProxyHook初始化完成")
            }
        } else {
            // onCreate阶段：初始化EAPIHook、CdnHook
            if (SettingHelper.getInstance().getSetting(SettingHelper.master_key)) {
                val earlyHook = EAPIHook(module, context)
                if (!earlyHook.isHooked) {
                    val intentFilter = IntentFilter()
                    intentFilter.addAction("hookPlayProcess")
                    context.registerReceiver(object : BroadcastReceiver() {
                        override fun onReceive(c: Context, intent: Intent) {
                            if ("hookPlayProcess" == intent.action) {
                                ClassHelper.getCacheClassList(context, versionCode, object : ClassHelper.OnCacheClassListener {
                                    override fun onGet() {
                                        EAPIHook(module, context)
                                        CdnHook(module, versionCode)
                                    }
                                })
                            }
                        }
                    }, intentFilter, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Context.RECEIVER_NOT_EXPORTED else 0)
                } else {
                    ClassHelper.getCacheClassList(context, versionCode, object : ClassHelper.OnCacheClassListener {
                        override fun onGet() {
                            CdnHook(module, versionCode)
                        }
                    })
                }
                context.sendBroadcast(Intent("playProcessInitFinish"))
            }
            LogUtils.i("Hook: 播放进程[onCreate]初始化完成")
        }
    }
}

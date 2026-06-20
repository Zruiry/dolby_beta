/**
 * Hook入口（精简版/荣耀版网易云）- 仅保留音源代理功能
 * 使用Modern libxposed API 102（XposedModule + Hooker拦截器链）
 *
 * 初始化分两个阶段（参考dev分支）：
 * 1. attachBaseContext阶段（isEarly=true）：初始化ProxyHook和启动脚本
 * 2. onCreate阶段（isEarly=false）：初始化SettingHook、EAPIHook、CdnHook等
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
import com.raincat.dolby_beta.hook.AdAndUpdateHook
import com.raincat.dolby_beta.hook.AutoSignInHook
import com.raincat.dolby_beta.hook.BeautyHook
import com.raincat.dolby_beta.hook.BlackHook
import com.raincat.dolby_beta.hook.CdnHook
import com.raincat.dolby_beta.hook.EAPIHook
import com.raincat.dolby_beta.hook.HideSidebarHook
import com.raincat.dolby_beta.hook.ListentogetherHook
import com.raincat.dolby_beta.hook.ProxyHook
import com.raincat.dolby_beta.hook.SettingHook
import com.raincat.dolby_beta.utils.LogUtils
import com.raincat.dolby_beta.utils.Tools
import io.github.libxposed.api.XposedModule

class HookOther(
    private val module: XposedModule,
    classLoader: ClassLoader,
    private val packageName: String,
    context: Context,
    isEarly: Boolean
) {
    init {
        try {
            val versionCode = when (packageName) {
                "com.netease.cloudmusic.lite" -> 140
                else -> 8010050
            }

            ExtraHelper.init(context)
            SettingHelper.init(context)

            val processName = Tools.getCurrentProcessName(context)
            val phase = if (isEarly) "attachBaseContext" else "onCreate"
            LogUtils.i("HookOther: 初始化[$phase] - 进程=$processName, versionCode=$versionCode")

            if (processName == packageName) {
                initMainProcess(module, context, versionCode, isEarly)
            } else if (processName == "$packageName:play") {
                initPlayProcess(module, context, versionCode, isEarly)
            }
        } catch (e: Exception) {
            LogUtils.e("HookOther: 初始化失败 - ${e.message}")
        }
    }

    /**
     * 主进程初始化
     * - attachBaseContext阶段：ProxyHook + 脚本启动 + SongPrivilege
     * - onCreate阶段：SettingHook + EAPIHook + CdnHook + 美化/黑胶/签到等Hook
     */
    private fun initMainProcess(module: XposedModule, context: Context, versionCode: Int, isEarly: Boolean) {
        if (isEarly) {
            // SongPrivilege hook不依赖主开关，提前到attachBaseContext阶段避免无版权弹窗
            EAPIHook.hookSongPrivilege(module, context)

            if (SettingHelper.getInstance().getSetting(SettingHelper.master_key)) {
                ProxyHook(module, context, false)
                LogUtils.i("HookOther: 主进程[attachBaseContext] ProxyHook初始化完成")
            } else {
                LogUtils.i("HookOther: 主开关未启用，跳过ProxyHook")
            }
        } else {
            SettingHook(module, context, versionCode)

            if (SettingHelper.getInstance().getSetting(SettingHelper.master_key)) {
                val earlyHook = EAPIHook(module, context)
                if (earlyHook.isHooked) {
                    LogUtils.i("HookOther: EAPIHook早期初始化成功，异步加载CdnHook")
                    ClassHelper.getCacheClassList(context, versionCode, object : ClassHelper.OnCacheClassListener {
                        override fun onGet() {
                            CdnHook(module, context, versionCode)
                            initFeatureHooks(module, context, versionCode)
                        }
                    })
                } else {
                    LogUtils.i("HookOther: EAPIHook早期初始化失败，等待dex扫描后重试")
                    ClassHelper.getCacheClassList(context, versionCode, object : ClassHelper.OnCacheClassListener {
                        override fun onGet() {
                            EAPIHook(module, context)
                            CdnHook(module, context, versionCode)
                            initFeatureHooks(module, context, versionCode)
                        }
                    })
                }
            }
            LogUtils.i("HookOther: 主进程[onCreate]初始化完成")
        }
    }

    /**
     * 初始化功能Hook（美化、黑胶VIP、签到、广告、一起听、侧边栏等）
     */
    private fun initFeatureHooks(module: XposedModule, context: Context, versionCode: Int) {
        try {
            BlackHook(module, context, versionCode)
            ListentogetherHook(module, context, versionCode)
            AutoSignInHook(module, context, versionCode)
            AdAndUpdateHook(module, context, versionCode)
            BeautyHook(module, context, versionCode)
            HideSidebarHook(module, context, versionCode)
            LogUtils.i("HookOther: 功能Hook初始化完成")
        } catch (e: Throwable) {
            LogUtils.e("HookOther: 功能Hook初始化失败 - ${e.message}")
        }
    }

    /**
     * 播放进程初始化
     * - attachBaseContext阶段：ProxyHook + SongPrivilege
     * - onCreate阶段：EAPIHook + CdnHook
     */
    private fun initPlayProcess(module: XposedModule, context: Context, versionCode: Int, isEarly: Boolean) {
        if (isEarly) {
            EAPIHook.hookSongPrivilege(module, context)

            if (SettingHelper.getInstance().getSetting(SettingHelper.master_key)) {
                ProxyHook(module, context, true)
                LogUtils.i("HookOther: 播放进程[attachBaseContext] ProxyHook初始化完成")
            }
        } else {
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
                                        CdnHook(module, context, versionCode)
                                    }
                                })
                            }
                        }
                    }, intentFilter, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Context.RECEIVER_NOT_EXPORTED else 0)
                } else {
                    ClassHelper.getCacheClassList(context, versionCode, object : ClassHelper.OnCacheClassListener {
                        override fun onGet() {
                            CdnHook(module, context, versionCode)
                        }
                    })
                }
                context.sendBroadcast(Intent("playProcessInitFinish"))
            }
            LogUtils.i("HookOther: 播放进程[onCreate]初始化完成")
        }
    }
}

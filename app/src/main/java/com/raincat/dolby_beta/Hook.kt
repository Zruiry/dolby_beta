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
import com.raincat.dolby_beta.hook.AdAndUpdateHook
import com.raincat.dolby_beta.hook.AdExtraHook
import com.raincat.dolby_beta.hook.AutoSignInHook
import com.raincat.dolby_beta.hook.BeautyHook
import com.raincat.dolby_beta.hook.BlackHook
import com.raincat.dolby_beta.hook.CdnHook
import com.raincat.dolby_beta.hook.DownloadMD5Hook
import com.raincat.dolby_beta.hook.EAPIHook
import com.raincat.dolby_beta.hook.GrayHook
import com.raincat.dolby_beta.hook.HideSidebarHook
import com.raincat.dolby_beta.hook.InternalDialogHook
import com.raincat.dolby_beta.hook.ListentogetherHook
import com.raincat.dolby_beta.hook.LoginFixHook
import com.raincat.dolby_beta.hook.MagiskFixHook
import com.raincat.dolby_beta.hook.ProxyHook
import com.raincat.dolby_beta.hook.SettingHook
import com.raincat.dolby_beta.hook.UserProfileHook
import com.raincat.dolby_beta.utils.LogUtils
import com.raincat.dolby_beta.utils.Tools
import io.github.libxposed.api.XposedModule

class Hook(
    private val module: XposedModule,
    classLoader: ClassLoader,
    packageName: String,
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
            val versionCode = context.packageManager.getPackageInfo(PACKAGE_NAME, 0).versionCode
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
     * - attachBaseContext阶段：ProxyHook + 脚本启动 + SongPrivilege（最早时机，cronet还未添加）
     * - onCreate阶段：SettingHook + EAPIHook + CdnHook + 美化/黑胶/签到等Hook（需要Application完全初始化）
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
        } else {
            // onCreate阶段：初始化SettingHook、EAPIHook、CdnHook
            // 统一等待 DEX 扫描完成，避免多次调用 getCacheClassList
            ClassHelper.getCacheClassList(context, versionCode, object : ClassHelper.OnCacheClassListener {
                override fun onGet() {
                    SettingHook(module, context, versionCode)

                    if (SettingHelper.getInstance().getSetting(SettingHelper.master_key)) {
                        EAPIHook(module, context)
                        CdnHook(module, context, versionCode)
                        initFeatureHooks(module, context, versionCode)
                    }
                    LogUtils.i("Hook: 主进程[onCreate]初始化完成")
                }
            })
        }
    }

    /**
     * 初始化功能Hook（美化、黑胶VIP、签到、广告、一起听、侧边栏等）
     * 这些Hook不依赖dex扫描结果，使用非混淆类名
     */
    private fun initFeatureHooks(module: XposedModule, context: Context, versionCode: Int) {
        try {
            // 用户资料获取（获取用户ID、Cookie、喜欢的歌单ID，签到功能依赖）
            UserProfileHook(module, context)
            // 登录修复（填充checkToken）
            LoginFixHook(module, context)
            // Magisk修复（外置SD卡读写）
            MagiskFixHook(module, context)
            // 内测与听歌识别弹窗拦截
            InternalDialogHook(module, context, versionCode)
            // 下载MD5修复
            DownloadMD5Hook(module, context)
            // 广告移除增强
            AdExtraHook(module, context)
            // 黑胶VIP
            BlackHook(module, context, versionCode)
            // 一起听解锁
            ListentogetherHook(module, context, versionCode)
            // 自动签到
            AutoSignInHook(module, context, versionCode)
            // 去广告和升级提示
            AdAndUpdateHook(module, context, versionCode)
            // 美化设置（夜间模式、精简Tab、隐藏Banner、隐藏小红点、黑胶停转、评论区最热、播放界面背景、黑胶隐藏、音谱移除）
            BeautyHook(module, context, versionCode)
            // 侧边栏精简
            HideSidebarHook(module, context, versionCode)
            // 不变灰（Hook MusicInfo.hasCopyRight 返回 true）
            GrayHook(module, context)
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
            LogUtils.i("Hook: 播放进程[onCreate]初始化完成")
        }
    }
}

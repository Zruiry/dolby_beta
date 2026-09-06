/**
 * 去广告 Hook（素材层兜底，美化设置开关驱动）
 *
 * 核心去广告逻辑在 EAPIHook：拦截广告"数据接口"（/xeapi/ad/loading、comment/feed 等）响应并剥离广告数据，
 * 保持 code 200 返回空广告，避免客户端因请求失败走本地缓存兜底展示旧广告。
 *
 * 本 Hook 为素材层辅助兜底：
 * 1. Hook okhttp3.OkHttpClient.newCall(Request)：把第三方广告素材 CDN（iadmusicmat/ugdtimg/alicdn 等）请求
 *    改写为黑洞地址（https://999.0.0.1/），阻断素材下载。
 *    EAPI 广告数据接口（/eapi/、/xeapi/ 的 ad/loading、comment/feed 等）必须放行（交给 EAPIHook 剥离），
 *    否则请求失败会触发客户端缓存兜底，广告反而显示。
 * 2. 兜底拦截旧版开屏 LoadingActivity（仅在有该类时生效）。
 * 3. 清理本地广告缓存目录（启动时执行一次，不阻塞）。
 */
package com.raincat.dolby_beta.hook

import android.content.Context
import android.os.Environment
import com.raincat.dolby_beta.helper.FileHelper
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.hook.common.HookKit
import com.raincat.dolby_beta.utils.LogUtils
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule

class AdRemoveHook(
    private val module: XposedModule,
    private val context: Context
) {
    companion object {
        private const val TAG = "AdRemoveHook"
        /** 广告请求黑洞地址：连不上的地址，让广告请求快速失败 */
        private const val AD_BLOCK_URL = "https://999.0.0.1/"
    }

    init {
        try {
            HookKit.init(module, context)
            hookAdUrlBlock()
            hookLegacyLoadingActivity()
            deleteAdCache()
            LogUtils.i("$TAG: 去广告 Hook 初始化完成（开关运行时生效）")
        } catch (e: Throwable) {
            LogUtils.e("$TAG: 初始化失败 - ${e.message}")
        }
    }

    /** 去广告开关当前是否启用（运行时判断，切换即时生效） */
    private fun adEnabled(): Boolean = SettingHelper.getInstance().isEnable(SettingHelper.beauty_ad_key)

    /**
     * Hook okhttp3.OkHttpClient.newCall(Request) - 把命中广告特征的请求 URL 改为黑洞地址。
     * 9.5.81 中 OkHttpClient / Request / newCall 均为明文（非混淆），直接按名反射。
     */
    private fun hookAdUrlBlock() {
        val clientClazz = HookKit.findClass("okhttp3.OkHttpClient") ?: run {
            LogUtils.w("$TAG: 未找到 okhttp3.OkHttpClient")
            return
        }
        // newCall(okhttp3.Request)
        val requestClazz = HookKit.findClass("okhttp3.Request") ?: return
        val newCall = HookKit.findMethod(clientClazz, "newCall", arrayOf(requestClazz)) ?: run {
            LogUtils.w("$TAG: 未找到 OkHttpClient.newCall")
            return
        }
        newCall.isAccessible = true
        module.hook(newCall).intercept(object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                // 去广告开关关闭时不拦截（运行时生效，无需重启）
                if (!adEnabled()) return chain.proceed()
                val args = chain.args
                if (args.isNotEmpty() && args[0] != null) {
                    val request = args[0]
                    try {
                        // request.url() -> okhttp3.HttpUrl
                        val urlObj = callUrl(request)
                        val urlStr = urlObj?.toString()
                        if (!urlStr.isNullOrEmpty()) {
                            // 拦截：命中广告特征则改写为黑洞地址
                            if (shouldBlock(urlStr)) {
                                val newRequest = rebuildRequest(request, urlObj)
                                if (newRequest != null) {
                                    args[0] = newRequest
                                    LogUtils.i("$TAG: 已拦截广告请求 -> $urlStr")
                                }
                            }
                        }
                    } catch (e: Throwable) {
                        // 解析失败放行，避免影响正常请求
                    }
                }
                return chain.proceed(args.toTypedArray())
            }
        })
        LogUtils.i("$TAG: 成功 hook OkHttpClient.newCall")
    }

    /** 反射调用 request.url() */
    private fun callUrl(request: Any): Any? {
        var cur: Class<*>? = request.javaClass
        while (cur != null) {
            try {
                val m = cur.getDeclaredMethod("url")
                m.isAccessible = true
                return m.invoke(request)
            } catch (_: NoSuchMethodException) {
                cur = cur.superclass
            }
        }
        return null
    }

    /** request.newBuilder().url(AD_BLOCK_URL).build() */
    private fun rebuildRequest(request: Any, urlObj: Any): Any? {
        return try {
            val builder = request.javaClass.getMethod("newBuilder").invoke(request)
            val urlBuilderClazz = builder.javaClass
            // okhttp4 Request.Builder.url(String) 存在；优先尝试，失败退回 url(HttpUrl)
            val builder2 = try {
                val mUrlStr = urlBuilderClazz.getMethod("url", String::class.java)
                mUrlStr.invoke(builder, AD_BLOCK_URL)
            } catch (_: NoSuchMethodException) {
                val mUrlHttp = urlBuilderClazz.getMethod("url", urlObj.javaClass)
                mUrlHttp.invoke(builder, urlObj)
            }
            builder2.javaClass.getMethod("build").invoke(builder2)
        } catch (e: Throwable) {
            LogUtils.w("$TAG: 重建广告请求失败 - ${e.message}")
            null
        }
    }

    /** 判断 URL 是否命中素材广告特征（去广告开启时生效） */
    private fun shouldBlock(url: String): Boolean {
        // 广告素材专用域名（iad 前缀 = Internet AD）：开屏图片/视频素材，即便属 music.126.net 也拦截
        if (url.contains("iadmusicmat")) return true
        // 网易自身 CDN/资源放行（封面/头像/歌词图等，p*/d*/m*.music.126.net）
        if (url.contains("music.126.net")) return false
        if (url.contains("999.0.0.1")) return false
        // EAPI/XEAPI 广告"数据接口"放行（由 EAPIHook 置空响应处理，避免请求失败触发本地缓存兜底展示）
        if ((url.contains("/eapi/") || url.contains("/xeapi/"))
            && (url.contains("/ad/") || url.contains("comment/feed/inserted/resources/combined")
                || url.contains("comment/banner/get") || url.contains("resource-exposure/config"))) return false
        // 第三方广告素材 CDN：广点通/优量汇（ugdtimg）、阿里妈妈（alicdn/alibabausercontent）、京东（360buyimg）等
        return url.contains("ugdtimg.com") || url.contains("ossgw.alicdn.com")
            || url.contains("a1.alibabausercontent.com") || url.contains("360buyimg.com")
            || url.contains("miaozhen.com") || url.contains("reachmax.cn")
            || url.contains("im-x.jd.com") || url.contains("gdt.qq.com")
            || url.contains("admob") || url.contains("ads.qq.com")
            || url.contains("appcloud2") || url.contains("/advert")
            || url.contains("admaterial")
    }

    /** 旧版开屏 LoadingActivity 兜底（findClassIfExists，无该类则忽略） */
    private fun hookLegacyLoadingActivity() {
        val clazz = HookKit.findClass("com.netease.cloudmusic.activity.LoadingActivity") ?: return
        val bundle = HookKit.findClass("android.os.Bundle") ?: return
        val onCreate = HookKit.findMethod(clazz, "onCreate", arrayOf(bundle)) ?: return
        onCreate.isAccessible = true
        module.hook(onCreate).intercept(object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                // 去广告开关关闭时放行
                if (!adEnabled()) return chain.proceed()
                return try {
                    val activity = chain.thisObject as? android.app.Activity
                    activity?.finish()
                    null
                } catch (e: Throwable) {
                    null
                }
            }
        })
        LogUtils.i("$TAG: 已兜底拦截 LoadingActivity")
    }

    /** 删除本地广告缓存目录（异步，不阻塞启动），按实际包名自适应 */
    private fun deleteAdCache() {
        Thread(Runnable {
            // 仅去广告开启时清理（异步线程内再判一次，避免竞态）
            if (!adEnabled()) return@Runnable
            try {
                val base = Environment.getExternalStorageDirectory().absolutePath
                val pkg = context.packageName
                // 应用私有 cache/Ad（标准版、精简版等按包名不同）
                FileHelper.deleteDirectory("$base/Android/data/$pkg/cache/Ad")
                // 历史外部目录：标准版 /netease/cloudmusic/Ad，精简版 /netease/cloudmusic/lite/Ad
                FileHelper.deleteDirectory("$base/netease/cloudmusic/Ad")
                FileHelper.deleteDirectory("$base/netease/cloudmusic/lite/Ad")
                // 注：网易云 files/Cache（Fresco 磁盘缓存，6GB+）不在此清理——
                // 广告素材已由 EAPIHook 剥离数据接口（ad/loading、comment/feed）从源头阻断，
                // 无需频繁清空大缓存影响正常图片体验。
            } catch (e: Throwable) {
                LogUtils.w("$TAG: 清理广告缓存失败 - ${e.message}")
            }
        }, "AdRemove-CacheCleaner").apply { isDaemon = true }.start()
    }
}

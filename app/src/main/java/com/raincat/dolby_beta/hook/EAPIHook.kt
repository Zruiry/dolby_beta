/**
 * 网络访问Hook - 拦截EAPI请求响应并修改内容
 * 旧版：通过ClassHelper.HttpResponse.getResultMethod() hook响应处理方法
 * 新版（9.5.30+）：通过hook EAPI解密拦截器interceptor.s.intercept()方法
 * 使用Modern libxposed API 102（Hooker拦截器链）
 *
 */
package com.raincat.dolby_beta.hook

import android.content.Context
import android.net.Uri
import android.text.TextUtils
import com.raincat.dolby_beta.helper.ClassHelper
import com.raincat.dolby_beta.helper.EApiHookHelper
import com.raincat.dolby_beta.helper.EAPIHelper
import com.raincat.dolby_beta.helper.ExtraHelper
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.net.HTTPSTrustManager
import com.raincat.dolby_beta.utils.LogUtils
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.net.ConnectException
import java.util.concurrent.atomic.AtomicBoolean
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.LinkedHashMap
import java.util.regex.Pattern
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext

class EAPIHook(private val module: XposedModule, private val appContext: Context) {

    companion object {
        /** cronet异常是否已记录过（避免日志刷屏） */
        private val cronetErrorLogged = AtomicBoolean(false)

        /**
         * Hook SongPrivilege的权限设置方法
         * 确保无版权歌曲可以正常进入播放页面而不是弹出"版权方要求"提示弹窗
         *
         * 参考dev分支GrayHook实现：仅代理主开关开启时执行
         * 通过hook setDownloadMaxbr/setFreeLevel作为入口，在回调中一次性修改所有权限字段
         *
         * 核心逻辑（与dev分支一致）：
         * - 获取SongPrivilege对象的maxbr字段，如果为0则设为999000
         * - 设置subPriv/sharePriv/commentPriv为1（社交权限）
         * - 设置downMaxLevel/playMaxLevel/playMaxbr为maxbr（播放/下载权限）
         */
        @JvmStatic
        fun hookSongPrivilege(module: XposedModule, context: Context) {
            val proxyEnabled = SettingHelper.getInstance().isEnable(SettingHelper.proxy_master_key)
            if (!proxyEnabled) {
                LogUtils.i("EAPIHook: SongPrivilege hook未启用（proxyEnabled=$proxyEnabled）")
                return
            }

            val cl = context.classLoader

            try {
                val songPrivilegeClass = ClassHelper.findClassIfExists(
                    "com.netease.cloudmusic.meta.virtual.SongPrivilege", cl
                ) ?: run {
                    LogUtils.w("EAPIHook: SongPrivilege类未找到")
                    return
                }
                LogUtils.i("EAPIHook: 找到SongPrivilege类: ${songPrivilegeClass.name}")

                // 参考dev分支：hook setDownloadMaxbr或setFreeLevel作为入口
                // 在回调中一次性修改所有权限字段，让无版权歌曲能进入播放页面
                var hookMethod: Method? = null
                try {
                    hookMethod = songPrivilegeClass.getMethod("setDownloadMaxbr", Int::class.javaPrimitiveType)
                } catch (_: NoSuchMethodException) {
                    try {
                        hookMethod = songPrivilegeClass.getMethod("setFreeLevel", Int::class.javaPrimitiveType)
                    } catch (e: NoSuchMethodException) {
                        LogUtils.w("EAPIHook: 未找到setDownloadMaxbr/setFreeLevel方法 - ${e.message}")
                    }
                }

                if (hookMethod != null) {
                    module.hook(hookMethod).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val obj = chain.thisObject

                            // 获取id，id为0则跳过
                            val id = callMethod(obj, "getId") as? Long ?: 0L
                            if (id == 0L) return chain.proceed()

                            // 获取maxbr字段值
                            var maxbr = 0
                            try {
                                for (field in obj.javaClass.declaredFields) {
                                    if (field.type == Int::class.javaPrimitiveType && field.name == "maxbr") {
                                        field.isAccessible = true
                                        maxbr = field.getInt(obj)
                                        break
                                    }
                                }
                            } catch (_: Exception) {}
                            if (maxbr == 0) maxbr = 999000

                            // 先执行原始方法（使用maxbr作为参数），再设置其他权限字段
                            // 参考dev分支GrayHook：beforeHookedMethod中修改参数后调用其他setter
                            val result = chain.proceed(arrayOf(maxbr))

                            // 一次性设置所有权限字段（参考dev分支GrayHook）
                            try {
                                callMethod(obj, "setSubPriv", 1)
                                callMethod(obj, "setSharePriv", 1)
                                callMethod(obj, "setCommentPriv", 1)
                                callMethod(obj, "setDownMaxLevel", maxbr)
                                callMethod(obj, "setPlayMaxLevel", maxbr)
                                try {
                                    callMethod(obj, "setPlayMaxbr", maxbr)
                                } catch (_: Exception) {}
                                LogUtils.i("EAPIHook: SongPrivilege权限已设置 id=$id, maxbr=$maxbr")
                            } catch (e: Exception) {
                                LogUtils.w("EAPIHook: 设置SongPrivilege权限失败 id=$id - ${e.message}")
                            }

                            return result
                        }
                    })
                    LogUtils.i("EAPIHook: 成功hook SongPrivilege.${hookMethod.name}")
                }
            } catch (e: Throwable) {
                LogUtils.e("EAPIHook: hook SongPrivilege失败 - ${e.message}")
            }
        }

        /**
         * 通过反射调用对象的指定名称方法（包括父类方法）
         */
        @JvmStatic
        fun callMethod(obj: Any, methodName: String, vararg args: Any): Any? {
            val argTypes = args.map { it.javaClass }.toTypedArray()
            // 遍历类继承链查找方法（包括父类）
            var clazz: Class<*>? = obj.javaClass
            while (clazz != null) {
                try {
                    val method = clazz.getDeclaredMethod(methodName, *argTypes)
                    method.isAccessible = true
                    return method.invoke(obj, *args)
                } catch (_: NoSuchMethodException) {
                    // 当前类没找到，继续查找父类
                }
                // 也检查当前类的声明方法（参数类型不精确匹配）
                for (method in clazz.declaredMethods) {
                    if (method.name == methodName && method.parameterTypes.size == args.size) {
                        method.isAccessible = true
                        return method.invoke(obj, *args)
                    }
                }
                clazz = clazz.superclass
            }
            throw NoSuchMethodException("$methodName with ${args.size} args in ${obj.javaClass.name}")
        }

        /**
         * 通过反射调用类的静态方法
         */
        @JvmStatic
        fun callStaticMethod(clazz: Class<*>, methodName: String, vararg args: Any): Any? {
            val argTypes = args.map { it.javaClass }.toTypedArray()
            try {
                val method = clazz.getDeclaredMethod(methodName, *argTypes)
                method.isAccessible = true
                return method.invoke(null, *args)
            } catch (_: NoSuchMethodException) {
                for (method in clazz.declaredMethods) {
                    if (method.name == methodName && method.parameterTypes.size == args.size) {
                        method.isAccessible = true
                        return method.invoke(null, *args)
                    }
                }
                throw NoSuchMethodException("$methodName with ${args.size} args in ${clazz.name}")
            }
        }
    }

    /** 标记EAPI响应拦截hook是否成功注册 */
    var isHooked = false
        private set

    init {
        isHooked = hookNewVersion(appContext)
        if (!isHooked) {
            isHooked = hookOldVersion()
        }
    }

    // ==================== 新版hook方式 ====================

    /**
     * 新版hook方式：动态查找EAPI解密拦截器并hook其intercept()方法
     */
    private fun hookNewVersion(context: Context): Boolean {
        try {
            val eapiDecryptInterceptorClass = findEapiDecryptInterceptor(context) ?: run {
                LogUtils.d("EAPIHook: 未找到EAPI解密拦截器类，回退旧版方式")
                return false
            }

            val interceptMethod = eapiDecryptInterceptorClass.declaredMethods.find {
                it.name == "intercept" && it.returnType.name == "okhttp3.Response"
            } ?: run {
                LogUtils.d("EAPIHook: EAPI解密拦截器intercept方法未找到（返回类型非okhttp3.Response），回退旧版方式")
                return false
            }

            LogUtils.d("EAPIHook: 使用新版hook方式，拦截器类=${eapiDecryptInterceptorClass.name}")

            module.hook(interceptMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    // 记录请求URL
                    var urlPath = "unknown"
                    try {
                        val chainObj = chain.getArg(0)
                        val request = callMethod(chainObj, "request")!!
                        val httpUrl = callMethod(request, "url")!!
                        urlPath = callMethod(httpUrl, "encodedPath") as String
                    } catch (_: Exception) {}

                    // 执行原始intercept
                    val result: Any?
                    try {
                        result = chain.proceed()
                    } catch (t: Throwable) {
                        // cronet异常只记录一次，避免日志刷屏
                        if (cronetErrorLogged.compareAndSet(false, true)) {
                            LogUtils.w("EAPIHook: intercept异常（后续同类异常将静默处理） - ${t.message}")
                        }
                        // 仅对音源请求构造兜底响应，其他请求直接抛出异常
                        if (urlPath.contains("song/enhance/player/url")
                            || urlPath.contains("song/enhance/download/url")
                        ) {
                            try {
                                // 构造空数据，通过processEapiResponse处理（代理获取替换音源）
                                // 参考dev分支：异常时返回空数据，由replaceEmptyUrlWithProxy通过代理获取替换音源
                                val emptyData = "{\"code\":200,\"data\":[]}"
                                // 尝试从chain中获取请求参数（音质等级、编码类型等）
                                val paramsMap = try {
                                    val chainObj = chain.getArg(0)
                                    val request = callMethod(chainObj, "request")!!
                                    EApiHookHelper.getRequestParams(request)
                                } catch (_: Exception) {
                                    LinkedHashMap<String, String>()
                                }
                                // processEapiResponse会执行modifyPlayer和replaceEmptyUrlWithProxy
                                val modified = processEapiResponse(urlPath, emptyData, paramsMap)
                                val finalContent = modified ?: emptyData
                                val errorResponse = buildErrorResponse(chain, finalContent)
                                if (errorResponse != null) return errorResponse
                            } catch (_: Exception) {}
                        }
                        throw t
                    }

                    // ===== 去广告：剥离广告数据接口响应（独立于音源代理开关，由 beauty_ad_key 驱动）=====
                    // 必须在代理开关判断之前执行，确保仅开去广告时也生效
                    val adEnabled = SettingHelper.getInstance().isEnable(SettingHelper.beauty_ad_key)
                    val isAdDataApi = result != null && adEnabled
                        && (urlPath.contains("/eapi/") || urlPath.contains("/xeapi/"))
                        && (urlPath.contains("comment/feed/inserted/resources/combined")
                            || urlPath.contains("comment/banner/get")
                            || urlPath.contains("ad/loading")
                            || urlPath.contains("ad/get")
                            || urlPath.contains("ad/banner"))
                    if (isAdDataApi) {
                        // 读取 body 后必须重建 response 返回（readResponseBodyString 会消费 body）
                        try {
                            val response = result
                            val responseBody = callMethod(response, "body")
                            if (responseBody != null) {
                                val contentType = callMethod(responseBody, "contentType")
                                val original = readResponseBodyString(responseBody)
                                if (!TextUtils.isEmpty(original)) {
                                    // 剥离广告数据（保持 code 200），解析/重建失败则用原始内容
                                    val stripped = stripAdResponse(urlPath, original) ?: original
                                    val newResponse = rebuildResponseBody(response, contentType, stripped)
                                    if (newResponse != null) {
                                        if (stripped != original) {
                                            LogUtils.i("EAPIHook: 去广告-剥离广告数据响应 path=$urlPath")
                                        }
                                        return newResponse
                                    }
                                    // 重建失败：构造空响应兜底，避免返回已消费 body 的原响应
                                    val empty = buildErrorResponse(chain, original)
                                    if (empty != null) return empty
                                }
                            }
                        } catch (t: Throwable) {
                            LogUtils.w("EAPIHook: 去广告-处理广告数据接口异常 ${t.message}")
                        }
                        return result
                    }

                    // 代理未开启则直接返回，不消费body
                    if (!SettingHelper.getInstance().isEnable(SettingHelper.proxy_master_key)) return result

                    if (result == null) return result

                    // 非EAPI请求直接返回
                    if (!urlPath.contains("/eapi/") && !urlPath.contains("/xeapi/"))
                        return result

                    // 关键优化：只对需要处理的路径消费body，其他EAPI请求直接放行
                    // 处理音源替换API、批量请求API
                    val needProcess = urlPath.contains("song/enhance/player/url")
                            || urlPath.contains("song/enhance/download/url")
                            || urlPath.contains("batch")
                    if (!needProcess) return result

                    val response = result
                    val responseBody: Any?
                    val original: String
                    val contentType: Any?
                    try {
                        responseBody = callMethod(response, "body")
                        if (responseBody == null) return result
                        contentType = callMethod(responseBody, "contentType")
                        original = readResponseBodyString(responseBody)
                    } catch (e: Exception) {
                        LogUtils.e("EAPIHook: 读取responseBody失败 - ${e.message}")
                        return result
                    }
                    if (TextUtils.isEmpty(original)) return result

                    try {
                        val chainObj = chain.getArg(0)
                        val request = callMethod(chainObj, "request")!!
                        val paramsMap = EApiHookHelper.getRequestParams(request)

                        val modified = processEapiResponse(urlPath, original, paramsMap)

                        // body已被readResponseBodyString消费，必须重建response
                        val finalContent = modified ?: original
                        val newResponse = rebuildResponseBody(response, contentType, finalContent)
                        if (newResponse != null) return newResponse
                    } catch (t: Throwable) {
                        LogUtils.e("EAPIHook: 处理EAPI响应异常，尝试用原始内容重建 - ${t.message}")
                    }

                    // body已被消费，用原始内容重建response
                    val fallbackResponse = rebuildResponseBody(response, contentType, original)
                    if (fallbackResponse != null) return fallbackResponse

                    // 最后兜底：重建失败则用空JSON构造响应
                    LogUtils.e("EAPIHook: 重建response失败，返回兜底响应")
                    val emptyResponse = buildErrorResponse(chain, "{\"code\":500,\"message\":\"hook rebuild failed\"}")
                    if (emptyResponse != null) return emptyResponse

                    throw RuntimeException("EAPIHook: failed to build response")
                }
            })
            return true
        } catch (t: Throwable) {
            LogUtils.e("EAPIHook: 新版hook方式失败: ${t.message}")
            return false
        }
    }

    /**
     * 重建ResponseBody和Response
     */
    private fun rebuildResponseBody(response: Any, contentType: Any?, content: String): Any? {
        val responseBodyClass = appContext.classLoader.loadClass("okhttp3.ResponseBody")
        val newBody: Any?

        if (contentType != null) {
            newBody = try {
                val createMethod = responseBodyClass.getDeclaredMethod("create", contentType.javaClass, String::class.java)
                createMethod.invoke(null, contentType, content)
            } catch (_: Exception) {
                try {
                    val createMethod = responseBodyClass.getDeclaredMethod("create", String::class.java, contentType.javaClass)
                    createMethod.invoke(null, content, contentType)
                } catch (_: Exception) {
                    val createMethod = responseBodyClass.getDeclaredMethod("create", contentType.javaClass, Int::class.javaPrimitiveType, String::class.java)
                    createMethod.invoke(null, contentType, content.length, content)
                }
            }
        } else {
            newBody = try {
                val mediaTypeClass = appContext.classLoader.loadClass("okhttp3.MediaType")
                val parseMethod = mediaTypeClass.getDeclaredMethod("parse", String::class.java)
                val defaultMediaType = parseMethod.invoke(null, "text/plain; charset=utf-8")
                val createMethod = responseBodyClass.getDeclaredMethod("create", mediaTypeClass, String::class.java)
                createMethod.invoke(null, defaultMediaType, content)
            } catch (_: Exception) {
                val createMethod = responseBodyClass.getDeclaredMethod("create", String::class.java)
                createMethod.invoke(null, content)
            }
        }

        var newResponse = callMethod(response, "newBuilder")!!
        val bodyMethod = newResponse.javaClass.getDeclaredMethod("body", responseBodyClass)
        newResponse = bodyMethod.invoke(newResponse, newBody)!!
        val headerMethod = newResponse.javaClass.getDeclaredMethod("header", String::class.java, String::class.java)
        newResponse = headerMethod.invoke(newResponse, "Content-Length", content.length.toString())!!
        newResponse = callMethod(newResponse, "build")!!
        return newResponse
    }

    /**
     * 动态查找EAPI解密拦截器类
     * 通过DEX扫描 + 特征匹配查找，不硬编码混淆类名（规范1）
     * 特征：实现okhttp3.Interceptor接口、intercept方法返回okhttp3.Response、含解密方法
     */
    private fun findEapiDecryptInterceptor(context: Context): Class<*>? {
        val cl = context.classLoader
        val interceptorClass = ClassHelper.findClassIfExists("okhttp3.Interceptor", cl) ?: return null

        // 遍历查找：通过特征匹配识别EAPI解密拦截器（不硬编码混淆类名）
        try {
            val pattern = Pattern.compile("^com\\.netease\\.cloudmusic\\.network\\.interceptor\\.[a-z]{1,3}$")
            val classList = ClassHelper.getFilteredClasses(pattern, null)
            for (className in classList) {
                try {
                    val clazz = ClassHelper.findClassIfExists(className, cl)
                    if (clazz != null && isEapiDecryptInterceptor(clazz, interceptorClass)) {
                        LogUtils.i("EAPIHook: 通过特征匹配找到EAPI解密拦截器: $className")
                        return clazz
                    }
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            LogUtils.e("EAPIHook: 遍历查找EAPI解密拦截器失败: ${e.message}")
        }
        return null
    }

    private fun isEapiDecryptInterceptor(clazz: Class<*>, interceptorClass: Class<*>): Boolean {
        try {
            if (!interceptorClass.isAssignableFrom(clazz)) return false
            var hasValidIntercept = false
            var hasDecryptMethod = false
            for (m in clazz.declaredMethods) {
                if (m.name == "intercept") {
                    // intercept方法必须返回okhttp3.Response类型，排除cronet等返回其他类型的拦截器
                    val returnType = m.returnType
                    if (returnType.name == "okhttp3.Response") {
                        hasValidIntercept = true
                    }
                }
                if (m.name == "b" || m.name == "a") {
                    for (pt in m.parameterTypes) {
                        if (pt.name.contains("ResponseBody")) {
                            hasDecryptMethod = true
                            break
                        }
                    }
                }
            }
            return hasValidIntercept && hasDecryptMethod
        } catch (_: Exception) {
            return false
        }
    }

    // ==================== 旧版hook方式 ====================

    private fun hookOldVersion(): Boolean {
        val resultMethod = ClassHelper.HttpResponse.getResultMethod()
        if (resultMethod == null) {
            LogUtils.w("EAPIHook: getResultMethod返回null，跳过hook")
            return false
        }
        LogUtils.i("EAPIHook: 使用旧版hook方式（HttpResponse.getResultMethod）")

        module.hook(resultMethod).intercept(object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val result = chain.proceed()
                if (!SettingHelper.getInstance().isEnable(SettingHelper.proxy_master_key)) return result
                if (result !is String && result !is JSONObject) return result
                val original = result.toString()
                if (TextUtils.isEmpty(original)) return result

                val thisObject = chain.thisObject
                val httpResponse = ClassHelper.HttpResponse(thisObject)
                val eapi = httpResponse.getEapi()
                val uri = ClassHelper.HttpUrl.getUri(eapi)
                if (!uri.path?.contains("/eapi/")!!) return result
                val path = uri.path!!

                val paramsMap = ClassHelper.HttpParams.getParams(eapi)
                val modified = processEapiResponse(path, original, paramsMap)

                if (modified != null) {
                    return if (result is JSONObject) JSONObject(modified) else modified
                }
                return result
            }
        })
        return true
    }

    // ==================== 响应处理 ====================

    /**
     * 处理EAPI响应内容，根据请求路径进行不同的修改
     */
    private fun processEapiResponse(
        path: String, original: String,
        paramsMap: LinkedHashMap<String, String>
    ): String? {
        val setting = SettingHelper.getInstance()
        // 本地/服务器模式需脚本或服务器就绪（SCRIPT_STATUS=1）
        val proxyActive = setting.isEnable(SettingHelper.proxy_master_key)
                && "1" == ExtraHelper.getExtraDate(ExtraHelper.SCRIPT_STATUS)

        if (path.contains("song/enhance/player/url")) {
            val modified = EAPIHelper.modifyPlayer(original)
            // 代理开启时，始终尝试通过代理替换音源
            // - 无版权歌曲（URL为空/code非200）：modifyPlayer返回modified，代理替换URL
            // - VIP歌曲（fee>0，有试听URL）：modifyPlayer对VIP也返回modified（fee改为0），但仍需通过代理获取完整播放URL
            // - 响应数据为空（如cronet异常）：通过代理获取全部替换音源
            if (proxyActive) {
                return replaceEmptyUrlWithProxy(modified ?: original, paramsMap, path)
            }
            return modified
        } else if (path.contains("song/enhance/download/url")) {
            val jsonObject = JSONObject(original)
            val obj = jsonObject.getJSONObject("data")
            val array = JSONArray().put(obj)
            jsonObject.put("data", array)
            val modified = EAPIHelper.modifyPlayer(jsonObject.toString())
            if (modified != null) {
                val result = modified.replace("[", "").replace("]", "")
                if (proxyActive) {
                    return replaceEmptyUrlWithProxy(result, paramsMap, path)
                }
                return result
            }
            return null
        } else if (path.contains("batch")) {
            return processBatchResponse(original)
        }
        return null
    }

    /**
     * 检查音源响应中是否有空URL，如果有则通过代理服务器获取替换音源
     * 当响应数据为空（如cronet异常）时，从请求参数中提取歌曲ID，通过代理获取全部替换音源
     */
    private fun replaceEmptyUrlWithProxy(
        modified: String,
        paramsMap: LinkedHashMap<String, String>, path: String
    ): String {
        try {
            val responseJson = JSONObject(modified)
            val dataArray = responseJson.optJSONArray("data") ?: return modified

            // 收集需要通过代理替换音源的歌曲ID
            // 参考master分支：对所有非云盘歌曲都尝试通过代理获取替换音源
            // - 无版权歌曲：URL为空或code非200
            // - VIP歌曲：URL非空但fee>0（仅有试听URL，无完整播放权限）。fee经modifyPlayer处理后已置0，但URL仍为试听URL
            // 对所有非云盘歌曲统一收集，代理服务器会正确处理（正常歌曲不改动，VIP/无版权替换URL）
            val emptyUrlIds = mutableListOf<String>()
            for (i in 0 until dataArray.length()) {
                val songObj = dataArray.optJSONObject(i) ?: continue
                val flag = songObj.optInt("flag", 0)
                // 云盘歌曲跳过
                if (flag and 0x8 != 0) continue
                val songId = songObj.optLong("id", -1)
                if (songId > 0) emptyUrlIds.add("${songId}_0")
            }

            // 响应数据为空时（如cronet异常），从请求参数中提取歌曲ID
            // 参考dev分支：看返回的歌曲是不是空，决定是不是需要替换
            if (emptyUrlIds.isEmpty() && dataArray.length() == 0) {
                val idsFromParams = extractSongIdsFromParams(paramsMap)
                if (idsFromParams.isEmpty()) return modified
                emptyUrlIds.addAll(idsFromParams)
                LogUtils.d("EAPIHook: 响应为空，从请求参数提取歌曲ID: $emptyUrlIds")
            }

            if (emptyUrlIds.isEmpty()) return modified

            val ids = emptyUrlIds.joinToString(",")

            // 从请求参数中提取音质等级和编码类型
            // EAPI请求的参数是加密的，需要通过EAPIHelper.decrypt解密后提取
            var level = "exhigh"
            var encodeType = "aac"
            try {
                val paramsStr = paramsMap["params"]
                if (paramsStr != null) {
                    val paramsJson = EAPIHelper.decrypt(paramsStr)
                    if (paramsJson.length() > 0) {
                        level = paramsJson.optString("level", level)
                        encodeType = paramsJson.optString("encodeType", encodeType)
                    }
                }
            } catch (e: Exception) {
                LogUtils.w("EAPIHook: 解析请求参数失败 - ${e.message}")
            }

            // 明确当前生效的代理模式，用于日志区分
            val setting = SettingHelper.getInstance()
            val modeTag = when {
                setting.getSetting(SettingHelper.proxy_server_key) -> "服务器代理"
                else -> "本地代理"
            }
            LogUtils.i("EAPIHook: [$modeTag] 开始音源替换 path=$path ids=$ids level=$level")

            val proxyResponse = requestProxyForSongUrl(ids, level, encodeType) ?: run {
                LogUtils.w("EAPIHook: [$modeTag] 请求替换音源失败 ids=$ids")
                return modified
            }

            // 解析代理响应并合并
            val proxyJson = JSONObject(proxyResponse)
            // 检查代理返回的code是否为200
            if (proxyJson.optInt("code") != 200) {
                LogUtils.w("EAPIHook: [$modeTag] 返回code=${proxyJson.optInt("code")}，替换失败")
                return modified
            }
            val proxyDataArray = proxyJson.optJSONArray("data") ?: run {
                LogUtils.w("EAPIHook: [$modeTag] 返回data为null")
                return modified
            }

            var replacedCount = 0
            for (i in 0 until proxyDataArray.length()) {
                val proxySong = proxyDataArray.optJSONObject(i) ?: continue
                val proxySongId = proxySong.optLong("id", 0)
                var proxyUrl = proxySong.optString("url", "")
                // 跳过无效的代理歌曲
                if (proxySongId == 0L || proxyUrl.isNullOrEmpty()) continue

                // 解码/package/前缀URL
                val decodedUrl = decodePackageUrl(proxyUrl)
                if (decodedUrl != null) proxyUrl = decodedUrl

                // 响应数据为空时，直接将代理返回的音源数据添加到data数组
                if (dataArray.length() == 0) {
                    proxySong.put("code", 200)
                    dataArray.put(proxySong)
                    replacedCount++
                    LogUtils.d("EAPIHook: [$modeTag] 歌曲ID=$proxySongId 添加成功（响应原为空）")
                    continue
                }

                for (j in 0 until dataArray.length()) {
                    val songObj = dataArray.optJSONObject(j) ?: continue
                    if (songObj.optLong("id") == proxySongId) {
                        songObj.put("url", proxyUrl)
                        songObj.put("code", 200)
                        // 设置fee/flag/payed为0，确保VIP歌曲显示为免费可播放（参考master分支）
                        songObj.put("fee", 0)
                        songObj.put("flag", 0)
                        songObj.put("payed", 0)
                        songObj.remove("freeTrialInfo")
                        if (proxySong.has("br")) songObj.put("br", proxySong.optInt("br"))
                        if (proxySong.has("size")) songObj.put("size", proxySong.optInt("size"))
                        if (proxySong.has("md5")) songObj.put("md5", proxySong.optString("md5"))
                        if (proxySong.has("type")) songObj.put("type", proxySong.optString("type"))
                        if (proxySong.has("level")) songObj.put("level", proxySong.optString("level"))
                        if (proxySong.has("encodeType")) songObj.put("encodeType", proxySong.optString("encodeType"))
                        dataArray.put(j, songObj)
                        replacedCount++
                        LogUtils.d("EAPIHook: [$modeTag] 歌曲ID=$proxySongId 替换成功")
                        break
                    }
                }
            }
            if (replacedCount > 0) {
                LogUtils.i("EAPIHook: [$modeTag] 音源替换成功，共 $replacedCount 首 ids=$ids")
            }
            responseJson.put("data", dataArray)
            return responseJson.toString()
        } catch (e: Exception) {
            LogUtils.e("EAPIHook: 合并代理音源失败 - ${e.message}")
            return modified
        }
    }

    /**
     * 从EAPI请求参数中提取歌曲ID
     * 当响应数据为空（如cronet异常）时，需要从请求参数中获取歌曲ID，通过代理获取替换音源
     *
     * EAPI请求参数解密后可能包含：
     * 1. ids字段 - 直接包含歌曲ID列表，格式为["123456_0","789012_0"]
     * 2. url字段 - 包含完整URL路径和查询参数，如song/enhance/player/url/v1?ids=...&level=...
     *
     * @param paramsMap 请求参数Map
     * @return 歌曲ID列表，格式为"歌曲ID_0"
     */
    private fun extractSongIdsFromParams(paramsMap: LinkedHashMap<String, String>): List<String> {
        val result = mutableListOf<String>()
        try {
            val paramsStr = paramsMap["params"] ?: return result
            val paramsJson = EAPIHelper.decrypt(paramsStr)
            if (paramsJson.length() == 0) return result

            // 尝试从ids字段提取（格式为["123456_0","789012_0"]）
            val idsStr = paramsJson.optString("ids", "")
            if (idsStr.isNotEmpty()) {
                parseIdsString(idsStr, result)
            }

            // 如果ids字段为空，尝试从url字段提取
            if (result.isEmpty()) {
                val urlStr = paramsJson.optString("url", "")
                if (urlStr.isNotEmpty()) {
                    // url格式为song/enhance/player/url/v1?ids=...&level=...&encodeType=...
                    val uri = Uri.parse("https://example.com/$urlStr")
                    val idsParam = uri.getQueryParameter("ids")
                    if (idsParam != null && idsParam.isNotEmpty()) {
                        // ids参数是URL编码的JSON数组，需要先URL解码
                        val decodedIds = URLDecoder.decode(idsParam, "UTF-8")
                        parseIdsString(decodedIds, result)
                    }
                }
            }
        } catch (e: Exception) {
            LogUtils.w("EAPIHook: 从请求参数提取歌曲ID失败 - ${e.message}")
        }
        return result
    }

    /**
     * 解析歌曲ID字符串，支持JSON数组格式和逗号分隔格式
     * @param idsStr 歌曲ID字符串，如["123456_0","789012_0"]或123456_0,789012_0
     * @param result 解析结果存入此列表
     */
    private fun parseIdsString(idsStr: String, result: MutableList<String>) {
        try {
            // 尝试作为JSON数组解析
            val idsArray = JSONArray(idsStr)
            for (i in 0 until idsArray.length()) {
                val id = idsArray.getString(i)
                if (id.isNotEmpty()) result.add(id)
            }
        } catch (e: Exception) {
            // 不是JSON数组格式，尝试按逗号分割
            idsStr.removeSurrounding("[", "]").split(",").forEach { id ->
                val cleanId = id.trim().removeSurrounding("\"")
                if (cleanId.isNotEmpty()) result.add(cleanId)
            }
        }
    }

    /**
     * 通过代理服务器请求替换音源
     */
    private fun requestProxyForSongUrl(ids: String, level: String, encodeType: String): String? {
        try {
            val isServerMode = SettingHelper.getInstance().getSetting(SettingHelper.proxy_server_key)
            val proxyHost = if (isServerMode)
                SettingHelper.getInstance().getHttpProxy() else "127.0.0.1"
            val proxyPort = if (isServerMode)
                SettingHelper.getInstance().getProxyPort()
            else
                SettingHelper.getInstance().getProxyLocalPort()

            LogUtils.d("EAPIHook: 代理请求 ids=$ids level=$level")

            val idArray = ids.split(",")
            val idsJson = idArray.joinToString(",", "[", "]") { "\"$it\"" }

            val urlStr = "https://interface3.music.163.com/api/song/enhance/player/url/v1?" +
                    "ids=${URLEncoder.encode(idsJson, "UTF-8")}" +
                    "&level=$level&encodeType=$encodeType"

            val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyHost, proxyPort))
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf(HTTPSTrustManager()), java.security.SecureRandom())

            val url = URL(urlStr)
            val conn = url.openConnection(proxy) as javax.net.ssl.HttpsURLConnection
            conn.sslSocketFactory = sslContext.socketFactory
            conn.hostnameVerifier = HostnameVerifier { _, _ -> true }
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("Cookie", "os=android")
            conn.setRequestProperty("User-Agent", "NeteaseMusic/8.10.05")
            conn.setRequestProperty("Accept", "*/*")
            conn.setRequestProperty("Accept-Encoding", "identity")

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val reader = conn.inputStream.bufferedReader(Charsets.UTF_8)
                val response = reader.readText()
                reader.close()
                return response
            } else {
                conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                    LogUtils.e("EAPIHook: 代理请求失败 code=$responseCode ${reader.readText()}")
                }
                return null
            }
        } catch (e: ConnectException) {
            LogUtils.e("EAPIHook: 代理连接失败 - ${e.message}")
            return null
        } catch (e: SocketTimeoutException) {
            LogUtils.e("EAPIHook: 代理请求超时")
            return null
        } catch (e: Exception) {
            LogUtils.e("EAPIHook: 代理请求异常 - ${e.javaClass.simpleName}: ${e.message}")
            return null
        }
    }

    /**
     * 解码代理服务器返回的/package/格式URL
     *
     * 代理服务器（UnblockNeteaseMusic）返回的URL格式为：
     * https://music.163.com/package/{base64编码的实际URL}/{songId}.{ext}
     *
     * 客户端无法直接访问/package/路径（返回404），
     * 需要解码Base64部分获取实际的音源URL（如酷我、QQ音乐直链）
     *
     * @param url 代理服务器返回的/package/格式URL
     * @return 解码后的实际音源URL，如果不是/package/格式或解码失败返回null
     */
    private fun decodePackageUrl(url: String?): String? {
        try {
            if (url == null || !url.contains("/package/")) return null

            // 提取/package/后面的Base64部分
            // URL格式: https://music.163.com/package/{base64}/{songId}.{ext}
            val packageIndex = url.indexOf("/package/")
            val afterPackage = url.substring(packageIndex + "/package/".length)

            // Base64部分在第一个/之前
            val slashIndex = afterPackage.indexOf('/')
            if (slashIndex <= 0) return null

            val base64Part = afterPackage.substring(0, slashIndex)

            // Base64解码（注意：URL安全的Base64可能将+替换为-，/替换为_）
            val decoded = android.util.Base64.decode(base64Part, android.util.Base64.DEFAULT)
            val actualUrl = String(decoded, Charsets.UTF_8)

            // 验证解码结果是有效的URL
            if (actualUrl.startsWith("http://") || actualUrl.startsWith("https://")) {
                LogUtils.d("EAPIHook: /package/ URL解码成功 - 原始=$url")
                LogUtils.d("EAPIHook: /package/ URL解码结果=$actualUrl")
                return actualUrl
            }
            LogUtils.w("EAPIHook: /package/ Base64解码结果不是有效URL: $actualUrl")
            return null
        } catch (e: Exception) {
            LogUtils.e("EAPIHook: /package/ URL解码失败 - ${e.message}")
            return null
        }
    }

    /**
     * 处理batch请求的响应
     */
    private fun processBatchResponse(original: String): String? {
        if (original.contains("comment\\/banner\\/get")) {
            val jsonObject = JSONObject(original)
            if (!jsonObject.isNull("/api/content/exposure/comment/banner/get")) {
                val obj = JSONObject()
                obj.put("code", 200)
                obj.put("data", JSONObject())
                jsonObject.put("/api/content/exposure/comment/banner/get", obj)
            }
            if (!jsonObject.isNull("/api/v1/content/exposure/comment/banner/get")) {
                val obj = jsonObject.getJSONObject("/api/v1/content/exposure/comment/banner/get")
                val data = obj.getJSONObject("data")
                data.put("count", 0)
                data.put("offset", 999999999)
                data.put("records", JSONArray())
                data.put("message", "")
                obj.put("data", data)
                jsonObject.put("/api/v1/content/exposure/comment/banner/get", obj)
            }
            return jsonObject.toString()
        }
        return null
    }

    /**
     * 去广告：剥离广告数据接口响应中的广告卡片/横幅内容。
     * 让接口正常返回（避免客户端因失败走缓存兜底），但广告数据被清空。
     * 由 beauty_ad_key 驱动，独立于音源代理开关。
     *
     * 实测 9.5.81 结构：
     * - /xeapi/ad/loading/get、/xeapi/ad/loading/bidget：顶层 `ads` 为 JSONArray 广告列表 → 置空
     * - /xeapi/ad/get：顶层 `ads` 为 JSONObject（广告详情）→ 置空；无 data 数组
     * - /xeapi/comment/feed/inserted/resources/combined：data = {count,offset,records,delayRender}，
     *   records 为插入的广告/歌手卡片列表 → 清空
     * - comment/banner/get：评论顶部横幅 → data 置空
     */
    private fun stripAdResponse(path: String, original: String): String? {
        try {
            val jsonObject = JSONObject(original)
            if (path.contains("comment/feed/inserted/resources/combined")) {
                // 评论"插入资源"：清空 data.records（广告卡片所在），保持 code 200 与其它字段
                val data = jsonObject.optJSONObject("data")
                if (data != null) {
                    data.put("count", 0)
                    data.put("offset", 0)
                    data.put("records", JSONArray())
                    if (!data.isNull("delayRender")) data.put("delayRender", false)
                    jsonObject.put("data", data)
                }
                jsonObject.put("code", 200)
                return jsonObject.toString()
            } else if (path.contains("ad/loading") || path.contains("ad/get") || path.contains("ad/banner")) {
                // 开屏/横幅广告接口：清空 ads（数组/对象均置空），保持 code 200
                if (jsonObject.has("ads")) {
                    val ads = jsonObject.opt("ads")
                    if (ads is JSONArray) jsonObject.put("ads", JSONArray())
                    else if (ads is JSONObject) jsonObject.put("ads", JSONObject())
                    else jsonObject.remove("ads")
                }
                if (jsonObject.has("data")) {
                    val data = jsonObject.opt("data")
                    if (data is JSONArray) jsonObject.put("data", JSONArray())
                    else if (data is JSONObject) jsonObject.put("data", JSONObject())
                }
                jsonObject.put("code", 200)
                return jsonObject.toString()
            } else if (path.contains("comment/banner/get")) {
                // 评论顶部横幅
                jsonObject.put("data", JSONObject())
                jsonObject.put("code", 200)
                return jsonObject.toString()
            }
        } catch (e: Exception) {
            LogUtils.w("EAPIHook: 去广告-解析广告响应失败 ${e.message}")
        }
        return original
    }

    /**
     * 构造一个HTTP错误响应对象
     */
    private fun buildErrorResponse(chain: XposedInterface.Chain, content: String): Any? {
        try {
            val chainObj = chain.getArg(0)
            val request = callMethod(chainObj, "request")!!

            val responseBodyClass = appContext.classLoader.loadClass("okhttp3.ResponseBody")
            val mediaTypeClass = appContext.classLoader.loadClass("okhttp3.MediaType")
            val mediaType = callStaticMethod(mediaTypeClass, "parse", "application/json; charset=utf-8")
            val errorBody = callStaticMethod(responseBodyClass, "create", mediaType!!, content)

            val responseBuilderClass = appContext.classLoader.loadClass("okhttp3.Response\$Builder")
            val responseBuilder = responseBuilderClass.getDeclaredConstructor().newInstance()
            callMethod(responseBuilder, "request", request)

            // HTTP_1_1是okhttp3.Protocol的静态字段，不是方法
            val protocolClass = appContext.classLoader.loadClass("okhttp3.Protocol")
            val http11Field = protocolClass.getDeclaredField("HTTP_1_1")
            http11Field.isAccessible = true
            val http11 = http11Field.get(null)
            callMethod(responseBuilder, "protocol", http11!!)

            callMethod(responseBuilder, "code", 500)
            callMethod(responseBuilder, "message", "Network Error")
            callMethod(responseBuilder, "body", errorBody!!)
            return callMethod(responseBuilder, "build")
        } catch (e: Exception) {
            LogUtils.e("EAPIHook: buildErrorResponse失败 - ${e.message}")
            return null
        }
    }

    /**
     * 读取ResponseBody字符串内容
     * OkHttp 4.x Kotlin实现中ResponseBody.string()可能通过反射调用失败，
     * 需要多种回退方式读取
     */
    private fun readResponseBodyString(responseBody: Any): String {
        // 方式1：直接调用string()
        try {
            return callMethod(responseBody, "string") as String
        } catch (_: Exception) {}

        // 方式2：通过source().readString()读取
        try {
            val source = callMethod(responseBody, "source")!!
            val charset = callMethod(responseBody, "charset") ?: Charsets.UTF_8
            return callMethod(source, "readString", charset) as String
        } catch (_: Exception) {}

        // 方式3：通过readByteArray读取字节数组再转字符串
        val source = callMethod(responseBody, "source")!!
        val bytes = callMethod(source, "readByteArray") as ByteArray
        return String(bytes, Charsets.UTF_8)
    }
}

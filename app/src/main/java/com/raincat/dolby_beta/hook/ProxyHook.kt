/**
 * 代理Hook - 使用Modern libxposed API 102（Hooker拦截器链）
 * hook RealCall构造函数替换OkHttpClient实现代理
 *
 * 代理策略：
 * - 仅对音源相关请求（/eapi/song/enhance/player/url、/eapi/song/enhance/download/url、
 *   /package/）替换为代理OkHttpClient，不影响其他正常请求
 * - 跳过cronet拦截器（通过addInterceptor hook），避免ClassCastException
 *
 */
package com.raincat.dolby_beta.hook

import android.content.Context
import com.raincat.dolby_beta.helper.ClassHelper
import com.raincat.dolby_beta.helper.ExtraHelper
import com.raincat.dolby_beta.helper.ScriptHelper
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.utils.LogUtils
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

class ProxyHook(module: XposedModule, private val context: Context, isPlayProcess: Boolean) {

    companion object {
        /** 全局SSL工厂 */
        private var socketFactory: SSLSocketFactory? = null
        /** 自定义信任所有证书的X509TrustManager */
        private var customTrustManager: X509TrustManager? = null
        /** 缓存的代理OkHttpClient实例 */
        private var proxyClient: Any? = null
    }

    private var fieldSSLSocketFactory: String = "sslSocketFactoryOrNull"
    private var fieldHttpUrl = "url"
    private var fieldProxy = "proxy"

    /** 代理URL白名单 - 仅对这些路径的请求使用代理客户端
     * 注意：song/enhance/player/url 和 song/enhance/download/url 不再走代理路由，
     * 强制路由所有音源请求会导致 deserialdata fail，所有音乐都无法播放。
     * 音源替换由EAPIHook通过代理服务器请求实现，ProxyHook仅负责/package/路径的代理。
     */
    private val whiteUrlList = listOf(
        "/package/",                              // 代理服务器返回的/package/前缀URL
    )

    init {
        // RealCall 是 OkHttp 开源库的类名，未被混淆，可直接使用明文
        val realCallClass = ClassHelper.findClassIfExists("okhttp3.internal.connection.RealCall", context.classLoader)
        if (realCallClass == null) {
            LogUtils.e("ProxyHook: 未找到RealCall类，禁用音源代理功能")
            SettingHelper.getInstance().setSetting(SettingHelper.proxy_master_key, false)
        } else {
        fieldSSLSocketFactory = "sslSocketFactoryOrNull"

        // Hook所有RealCall构造函数
        for (constructor in realCallClass.declaredConstructors) {
            module.hook(constructor).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val args = chain.args
                    if (args.size == 3) {
                        val client = chain.getArg(0)
                        val request = chain.getArg(1)

                        val proxyActive = SettingHelper.getInstance().isEnable(SettingHelper.proxy_master_key)
                                && "1" == ExtraHelper.getExtraDate(ExtraHelper.SCRIPT_STATUS)
                        if (proxyActive) {
                            try {
                                val urlField = request.javaClass.getDeclaredField(fieldHttpUrl)
                                urlField.isAccessible = true
                                val urlObj = urlField.get(request)
                                for (url in whiteUrlList) {
                                    val urlStr = urlObj?.toString() ?: continue
                                    if (urlStr.contains(url)) {
                                        val pClient = getOrCreateProxyClient(context, client)
                                        if (pClient != null) {
                                            val newArgs = args.toMutableList().toTypedArray()
                                            newArgs[0] = pClient
                                            return chain.proceed(newArgs)
                                        } else {
                                            LogUtils.e("ProxyHook: 创建代理客户端失败")
                                            setProxyFallback(client)
                                        }
                                        break
                                    }
                                }
                            } catch (_: NoSuchFieldException) {
                                // 字段名不匹配时，遍历所有字段按白名单匹配
                                for (f in request.javaClass.declaredFields) {
                                    f.isAccessible = true
                                    try {
                                        val value = f.get(request)
                                        if (value != null) {
                                            val valueStr = value.toString()
                                            for (url in whiteUrlList) {
                                                if (valueStr.contains(url)) {
                                                    val pClient = getOrCreateProxyClient(context, client)
                                                    if (pClient != null) {
                                                        val newArgs = args.toMutableList().toTypedArray()
                                                        newArgs[0] = pClient
                                                        return chain.proceed(newArgs)
                                                    } else {
                                                        LogUtils.e("ProxyHook: 创建代理客户端失败")
                                                        setProxyFallback(client)
                                                    }
                                                    break
                                                }
                                            }
                                            // 只要有一个字段匹配白名单就停止遍历
                                            break
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                    } else {
                        LogUtils.w("ProxyHook: RealCall参数数量不匹配 args.length=${args.size}")
                    }
                    return chain.proceed()
                }
            })
        }

        // 跳过cronet拦截器：hook addInterceptor阻止cronet被添加到拦截器列表
        // 与dev分支一致：不直接hook cronet.d.intercept()，因为其返回类型不是okhttp3.Response，
        // 强制返回OkHttp标准链路的Response会导致LSPosed类型校验失败（ClassCastException）
        val okHttpClientBuilderClass = ClassHelper.findClassIfExists("okhttp3.OkHttpClient\$Builder", context.classLoader)
        LogUtils.i("ProxyHook: OkHttpClient.Builder类=${okHttpClientBuilderClass?.name}")
        if (okHttpClientBuilderClass != null) {
            var addInterceptorHooked = false
            for (method in okHttpClientBuilderClass.declaredMethods) {
                if (method.name == "addInterceptor" && method.parameterTypes.size == 1) {
                    LogUtils.d("ProxyHook: 找到addInterceptor方法 - 参数类型=${method.parameterTypes[0].name}")
                    if (method.parameterTypes[0].name.contains("Interceptor")
                        && !method.parameterTypes[0].name.contains("Function")
                    ) {
                        module.hook(method).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                val interceptor = chain.getArg(0)
                                if (interceptor.javaClass.name.contains("com.netease.cloudmusic.network.cronet")) {
                                    LogUtils.d("ProxyHook: 跳过cronet拦截器添加: ${interceptor.javaClass.name}")
                                    return chain.thisObject
                                }
                                return chain.proceed()
                            }
                        })
                        addInterceptorHooked = true
                        LogUtils.i("ProxyHook: 成功hook addInterceptor方法")
                    }
                }
            }
            if (!addInterceptorHooked) {
                LogUtils.w("ProxyHook: 未找到合适的addInterceptor方法进行hook")
            }
        }

        // 非play进程时启动代理（与dev分支一致：无条件重置状态后启动）
        if (!isPlayProcess) {
            if (SettingHelper.getInstance().getSetting(SettingHelper.proxy_master_key)) {
                ExtraHelper.setExtraDate(ExtraHelper.SCRIPT_STATUS, "0")
                // 异步启动脚本，避免阻塞Hook初始化（文件解压和shell执行较耗时）
                Thread(Runnable {
                    ScriptHelper.initScript(context, false)
                    if (SettingHelper.getInstance().getSetting(SettingHelper.proxy_server_key)) {
                        ScriptHelper.startHttpProxyMode()
                    } else {
                        ScriptHelper.startScript()
                    }
                    // 启动后主动检查当前模式代理是否可用，失败自动重试并提示
                    ScriptHelper.waitAndCheckProxy(context)
                }, "ProxyHook-ScriptStarter").start()
            }
        }
        }
    }

    /**
     * 创建独立的代理OkHttpClient实例
     * 通过原始OkHttpClient的newBuilder()创建新的客户端，避免修改共享客户端
     */
    private fun getOrCreateProxyClient(context: Context, originalClient: Any): Any? {
        if (proxyClient != null) return proxyClient

        try {
            val httpUrlHost = if (SettingHelper.getInstance().getSetting(SettingHelper.proxy_server_key))
                SettingHelper.getInstance().getHttpProxy() else "127.0.0.1"
            val proxyPort = proxyConnectPort()

            if (socketFactory == null) {
                socketFactory = ScriptHelper.getSSLSocketFactory(context)
            }
            if (customTrustManager == null) {
                customTrustManager = createTrustAllManager()
            }
            if (customTrustManager == null || socketFactory == null) {
                LogUtils.e("ProxyHook: SSL初始化失败")
                return null
            }

            val newBuilderMethod = originalClient.javaClass.getDeclaredMethod("newBuilder")
            val builder = newBuilderMethod.invoke(originalClient)

            val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(httpUrlHost, proxyPort))
            val proxyMethod = builder.javaClass.getDeclaredMethod("proxy", Proxy::class.java)
            proxyMethod.invoke(builder, proxy)

            try {
                val sslMethod = builder.javaClass.getDeclaredMethod(
                    "sslSocketFactory", SSLSocketFactory::class.java, X509TrustManager::class.java
                )
                sslMethod.invoke(builder, socketFactory, customTrustManager)
            } catch (_: Exception) {
                try {
                    val sslMethod = builder.javaClass.getDeclaredMethod("sslSocketFactory", SSLSocketFactory::class.java)
                    sslMethod.invoke(builder, socketFactory)
                } catch (e2: Exception) {
                    LogUtils.e("ProxyHook: sslSocketFactory设置失败 - ${e2.message}")
                }
            }

            val trustAllVerifier = HostnameVerifier { _, _ -> true }
            val hostnameMethod = builder.javaClass.getDeclaredMethod("hostnameVerifier", HostnameVerifier::class.java)
            hostnameMethod.invoke(builder, trustAllVerifier)

            try {
                val certificatePinnerClass = context.classLoader.loadClass("okhttp3.CertificatePinner")
                val defaultField = certificatePinnerClass.getDeclaredField("DEFAULT")
                val defaultPinner = defaultField.get(null)
                if (defaultPinner != null) {
                    val certPinnerMethod = builder.javaClass.getDeclaredMethod("certificatePinner", certificatePinnerClass)
                    certPinnerMethod.invoke(builder, defaultPinner)
                }
            } catch (_: Exception) {}

            val buildMethod = builder.javaClass.getDeclaredMethod("build")
            proxyClient = buildMethod.invoke(builder)
            return proxyClient
        } catch (e: Exception) {
            LogUtils.e("ProxyHook: 创建代理客户端失败 - ${e.message}")
            return null
        }
    }

    /** 当前模式代理连接端口：服务器模式用配置的服务器端口，本地模式用本地监听端口 */
    private fun proxyConnectPort(): Int {
        val s = SettingHelper.getInstance()
        return if (s.getSetting(SettingHelper.proxy_server_key)) s.getProxyPort() else s.getProxyLocalPort()
    }

    /**
     * 回退方案：直接修改共享OkHttpClient的SSL字段
     */
    private fun setProxyFallback(client: Any) {
        try {
            val sslSocketFactoryField = try {
                client.javaClass.getDeclaredField(fieldSSLSocketFactory)
            } catch (_: NoSuchFieldException) {
                client.javaClass.declaredFields.find { SSLSocketFactory::class.java.isAssignableFrom(it.type) }
            } ?: run {
                LogUtils.e("ProxyHook: 未找到SSL字段")
                return
            }
            sslSocketFactoryField.isAccessible = true

            val proxyField = try {
                client.javaClass.getDeclaredField(fieldProxy)
            } catch (_: NoSuchFieldException) {
                client.javaClass.declaredFields.find { Proxy::class.java.isAssignableFrom(it.type) }
            } ?: run {
                LogUtils.e("ProxyHook: 未找到proxy字段")
                return
            }
            proxyField.isAccessible = true

            if (customTrustManager == null) {
                customTrustManager = createTrustAllManager()
            }

            val httpUrlHost = if (SettingHelper.getInstance().getSetting(SettingHelper.proxy_server_key))
                SettingHelper.getInstance().getHttpProxy() else "127.0.0.1"
            val proxyPort = proxyConnectPort()
            val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(httpUrlHost, proxyPort))
            proxyField.set(client, proxy)

            if (socketFactory != null) {
                sslSocketFactoryField.set(client, socketFactory)
            }

            val trustManagerField = findFieldByTypeLocal(client, X509TrustManager::class.java, "x509TrustManager")
            if (trustManagerField != null && customTrustManager != null) {
                trustManagerField.isAccessible = true
                trustManagerField.set(client, customTrustManager)
            }

            val hostnameVerifierField = try {
                client.javaClass.getDeclaredField("hostnameVerifier")
            } catch (_: NoSuchFieldException) {
                client.javaClass.declaredFields.find { HostnameVerifier::class.java.isAssignableFrom(it.type) }
            }
            hostnameVerifierField?.let {
                it.isAccessible = true
                it.set(client, HostnameVerifier { _, _ -> true })
            }

            val certificatePinnerField = try {
                client.javaClass.getDeclaredField("certificatePinner")
            } catch (_: NoSuchFieldException) {
                client.javaClass.declaredFields.find {
                    it.type.name.contains("CertificatePinner") || it.type.name.contains("certificatePinner")
                }
            }
            certificatePinnerField?.let {
                it.isAccessible = true
                try {
                    val defaultPinnerField = it.type.getDeclaredField("DEFAULT")
                    val defaultPinner = defaultPinnerField.get(null)
                    if (defaultPinner != null) it.set(client, defaultPinner)
                } catch (_: Exception) {}
            }

            setCertificateChainCleaner(client)
        } catch (e: Exception) {
            LogUtils.e("ProxyHook: setProxyFallback失败 - ${e.message}")
        }
    }

    /**
     * 替换OkHttpClient中的certificateChainCleaner字段
     */
    private fun setCertificateChainCleaner(client: Any) {
        try {
            var chainCleanerField: Field? = null

            // 优先通过已知字段名查找
            for (fieldName in arrayOf("certificateChainCleaner", "chainCleaner")) {
                try {
                    chainCleanerField = client.javaClass.getDeclaredField(fieldName)
                    break
                } catch (_: NoSuchFieldException) {}
            }

            // 通过类型特征查找
            if (chainCleanerField == null) {
                chainCleanerField = client.javaClass.declaredFields.find {
                    it.type.name.contains("CertificateChainCleaner") || it.type.name.contains("ChainCleaner")
                }
            }

            // 通过okhttp3内部类特征查找
            if (chainCleanerField == null) {
                chainCleanerField = client.javaClass.declaredFields.find {
                    !(SSLSocketFactory::class.java.isAssignableFrom(it.type)
                        || HostnameVerifier::class.java.isAssignableFrom(it.type)
                        || Proxy::class.java.isAssignableFrom(it.type)
                        || X509TrustManager::class.java.isAssignableFrom(it.type))
                    && it.type.name.contains("okhttp3") && it.type.name.contains("tls")
                }
            }

            if (chainCleanerField == null) return
            chainCleanerField.isAccessible = true

            if (customTrustManager != null) {
                try {
                    val cleanerClass = chainCleanerField.type
                    val getMethod = cleanerClass.getDeclaredMethod("get", X509TrustManager::class.java)
                    val newCleaner = getMethod.invoke(null, customTrustManager)
                    if (newCleaner != null) {
                        chainCleanerField.set(client, newCleaner)
                    }
                } catch (_: Exception) {
                    try { chainCleanerField.set(client, null) } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            LogUtils.w("ProxyHook: certificateChainCleaner替换失败 - ${e.message}")
        }
    }

    /**
     * 创建信任所有证书的X509TrustManager实例
     */
    private fun createTrustAllManager(): X509TrustManager? {
        return try {
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
        } catch (e: Exception) {
            LogUtils.e("ProxyHook: 创建TrustManager失败 - ${e.message}")
            null
        }
    }

    /**
     * 按类型查找字段
     */
    private fun findFieldByTypeLocal(obj: Any, fieldType: Class<*>, fieldName: String? = null): Field? {
        if (fieldName != null) {
            try { return obj.javaClass.getDeclaredField(fieldName) } catch (_: NoSuchFieldException) {}
        }
        return obj.javaClass.declaredFields.find { fieldType.isAssignableFrom(it.type) }
    }
}

/**
 * HTTPS信任管理器 - 信任所有SSL证书
 * 用于代理模式下绕过SSL证书验证
 *
 */
package com.raincat.dolby_beta.net

import com.raincat.dolby_beta.utils.LogUtils
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class HTTPSTrustManager : X509TrustManager {

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
        // 信任所有客户端证书
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        // 信任所有服务端证书
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> {
        // 返回空数组（非null），避免某些Android版本兼容性问题
        return arrayOf()
    }

    companion object {
        private var trustManagers: Array<TrustManager>? = null

        /**
         * 设置全局信任所有SSL证书
         */
        @JvmStatic
        fun allowAllSSL() {
            HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }

            if (trustManagers == null) {
                trustManagers = arrayOf(HTTPSTrustManager())
            }

            try {
                val context = SSLContext.getInstance("TLS")
                context.init(null, trustManagers, SecureRandom())
                HttpsURLConnection.setDefaultSSLSocketFactory(context.socketFactory)
            } catch (e: NoSuchAlgorithmException) {
                LogUtils.e("HTTPSTrustManager: 不支持的SSL算法 - ${e.message}")
            } catch (e: KeyManagementException) {
                LogUtils.e("HTTPSTrustManager: 初始化SSLContext失败 - ${e.message}")
            }
        }
    }
}

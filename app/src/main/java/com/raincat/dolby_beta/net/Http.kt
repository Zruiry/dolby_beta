/**
 * HTTP请求工具 - 支持GET/POST请求
 *
 */
package com.raincat.dolby_beta.net

import android.net.Uri
import android.util.Pair
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask

/**
 * HTTP请求封装
 *
 * @param method GET/POST
 * @param url    请求地址
 * @param header 请求头
 * @param param  请求参数（字符串形式）
 */
class Http(
    method: String,
    url: String,
    header: HashMap<String, Any>?,
    param: String
) {
    private val mRequest = Request()

    init {
        mRequest.header = header ?: HashMap()
        mRequest.method = method
        mRequest.param = param
        mRequest.url = url
    }

    /**
 * @param method GET/POST
 * @param url    请求地址
 * @param param  参数Map
 * @param header 请求头
 */
constructor(
    method: String,
    url: String,
    param: HashMap<String, Any>?,
    header: HashMap<String, Any>?
) : this(method, url, header, param?.let { buildParamString(it) } ?: "")

    fun getResult(): String = doHttp(mRequest)

    private fun doHttp(request: Request): String {
        val future = FutureTask<Pair<Int, String>> { post(request) }
        exec.execute(future)
        return try {
            val pair = future.get()
            if (pair.first != 0 && mRequest.reTry > 0) {
                mRequest.reTry--
                doHttp(mRequest)
            } else {
                pair.second ?: ""
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    companion object {
        private val exec: ExecutorService = Executors.newFixedThreadPool(10)

        /**
         * 将参数Map编码为URL查询字符串
         */
        private fun buildParamString(param: HashMap<String, Any>): String {
            val sb = StringBuilder()
            for ((key, value) in param) {
                sb.append(key).append("=").append(Uri.encode(value.toString())).append("&")
            }
            if (sb.isNotEmpty()) sb.deleteCharAt(sb.length - 1)
            return sb.toString()
        }

        private fun post(request: Request): Pair<Int, String> {
            var result: String
            var errorCode = 0

            var connection: HttpURLConnection? = null
            var inputStream: java.io.InputStream? = null
            try {
                val url = URL(request.url)
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = request.method
                connection.useCaches = false
                connection.connectTimeout = request.timeout
                connection.readTimeout = request.timeout
                connection.instanceFollowRedirects = true

                if (request.method == "POST") {
                    connection.doInput = true
                    connection.doOutput = true
                    connection.setChunkedStreamingMode(0)
                }

                connection.setRequestProperty("Charset", "UTF-8")
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.setRequestProperty("Cookie", "os=android")

                request.header.forEach { (key, value) ->
                    connection.setRequestProperty(key, value.toString())
                }
                connection.connect()

                if (request.method == "POST" && request.param.isNotEmpty()) {
                    val out = DataOutputStream(connection.outputStream)
                    out.writeBytes(request.param)
                    out.flush()
                    out.close()
                }

                // 根据响应码获取输入流
                inputStream = if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream
                } else {
                    errorCode = connection.responseCode
                    connection.errorStream
                }

                val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                result = response.toString()
            } catch (e: SocketException) {
                errorCode = 2
                e.printStackTrace()
                result = e.message ?: ""
            } catch (e: OutOfMemoryError) {
                errorCode = 3
                e.printStackTrace()
                result = e.message ?: ""
            } catch (e: SocketTimeoutException) {
                errorCode = 4
                e.printStackTrace()
                result = e.message ?: ""
            } catch (e: Exception) {
                e.printStackTrace()
                errorCode = -1
                result = e.message ?: ""
            } finally {
                connection?.disconnect()
                try { inputStream?.close() } catch (e: Exception) { e.printStackTrace() }
            }

            return Pair(errorCode, result)
        }
    }
}

/**
 * 请求封装 - HTTP/HTTPS请求参数容器
 *
 */
package com.raincat.dolby_beta.net

/**
 * 请求参数封装类
 */
internal class Request {
    var method: String = ""
    var url: String = ""
    var param: String = ""
    var header: HashMap<String, Any> = HashMap()
    var reTry: Int = 0
    var timeout: Int = 10000
}

/**
 * 接口处理 - 仅保留音源代理所需方法
 *
 */
package com.raincat.dolby_beta.helper

import com.raincat.dolby_beta.utils.NeteaseAES2
import org.json.JSONArray
import org.json.JSONObject

object EAPIHelper {

    /**
     * 解除下载加密
     * 仅修改无版权/付费歌曲的fee/flag/payed字段，使其显示为免费可播放。
     * 原本就可以正常播放的歌曲（URL非空且code=200）不做修改，避免影响正常播放。
     *
     * 判断逻辑：
     * - URL为空或code非200 → 无版权/付费歌曲，需要修改
     * - flag & 0x8 != 0 → 云盘歌曲，不修改
     * - 其他 → 正常可播放歌曲，不修改
     *
     * @return 修改后的JSON字符串，如果无需修改则返回null
     */
    @JvmStatic
    fun modifyPlayer(original: String): String? {
        return try {
            val jsonObject = JSONObject(original)
            val dataArray = jsonObject.getJSONArray("data")
            var modified = false
            for (i in 0 until dataArray.length()) {
                val dataObj = dataArray.getJSONObject(i)
                val flag = dataObj.optInt("flag", 0)
                // 云盘歌曲不修改
                if (flag and 0x8 != 0) continue

                val url = dataObj.optString("url", "")
                val code = dataObj.optInt("code", -1)

                // 仅修改无版权/付费歌曲（URL为空或code非200）
                // 原本可正常播放的歌曲不做修改
                if (url.isNullOrEmpty() || code != 200) {
                    dataObj.put("fee", 0)
                    dataObj.put("flag", 0)
                    dataObj.put("payed", 0)
                    dataObj.remove("freeTrialInfo")
                    modified = true
                }
            }
            if (modified) jsonObject.toString() else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 解密EAPI参数
     */
    @JvmStatic
    @Throws(Exception::class)
    fun decrypt(params: String): JSONObject {
        var decrypted = NeteaseAES2.decrypt(params)
        if (!decrypted.isNullOrEmpty()) {
            decrypted = decrypted.substring(decrypted.indexOf("{"), decrypted.lastIndexOf("}") + 1)
            val jsonObject = JSONObject(decrypted)
            return if (jsonObject.isNull("params")) {
                JSONObject(decrypted)
            } else {
                decrypt(jsonObject.getString("params"))
            }
        }
        return JSONObject()
    }
}

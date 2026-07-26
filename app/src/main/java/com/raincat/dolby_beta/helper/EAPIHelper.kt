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
     * 对所有非云盘歌曲修改fee/flag/payed字段，使其显示为免费可播放。
     * 参考master分支：不区分URL是否为空或code是否为200，对所有非云盘歌曲统一处理。
     *
     * 判断逻辑：
     * - flag & 0x8 != 0 → 云盘歌曲，不修改
     * - URL为空或code非200 → 无版权歌曲，需要修改
     * - fee > 0 → VIP歌曲（有试听URL但无完整播放权限），也需要修改
     * - 其他 → 正常可播放免费歌曲，不修改（避免不必要的修改）
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
                val fee = dataObj.optInt("fee", 0)

                // 修改无版权歌曲（URL为空/code非200）和VIP歌曲（fee>0，有试听URL但无完整播放权限）
                // 仅有URL且code=200且fee=0的正常免费歌曲不修改
                if (url.isNullOrEmpty() || code != 200 || fee > 0) {
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

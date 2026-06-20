package com.raincat.dolby_beta.helper;

import com.raincat.dolby_beta.utils.NeteaseAES2;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * <pre>
 *     author : RainCat
 *     e-mail : nining377@gmail.com
 *     time   : 2021/04/16
 *     desc   : 接口处理 - 仅保留音源代理所需方法
 *     version: 2.0
 * </pre>
 */
public class EAPIHelper {

    /**
     * 解除下载加密
     * 修改fee/flag/payed等字段使歌曲显示为免费可播放，
     * 同时保留URL中的查询参数（如vuutv签名）和所有其他原始字段。
     *
     * 重要：使用JSONObject直接修改字段，而不是Gson反序列化再序列化。
     * 原因：NeteaseSongListBean.DataBean不包含响应中的所有字段
     * （如freeTrialPrivilege、closedGain、sr、musicId等），
     * Gson序列化会丢失这些字段，导致应用无法正常播放音乐。
     */
    public static String modifyPlayer(String original) {
        try {
            JSONObject jsonObject = new JSONObject(original);
            JSONArray dataArray = jsonObject.getJSONArray("data");
            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject dataObj = dataArray.getJSONObject(i);
                // flag与8非0为云盘歌曲，云盘歌曲不修改
                int flag = dataObj.optInt("flag", 0);
                if ((flag & 0x8) == 0) {
                    dataObj.put("fee", 0);
                    dataObj.put("flag", 0);
                    dataObj.put("payed", 0);
                    dataObj.remove("freeTrialInfo");
                }
            }
            return jsonObject.toString();
        } catch (Exception e) {
            return original;
        }
    }

    /**
     * 解密EAPI参数
     */
    public static JSONObject decrypt(String params) throws Exception {
        params = NeteaseAES2.Decrypt(params);
        if (params != null && params.length() != 0) {
            params = params.substring(params.indexOf("{"), params.lastIndexOf("}") + 1);
            JSONObject jsonObject = new JSONObject(params);
            if (jsonObject.isNull("params"))
                return new JSONObject(params);
            else
                return decrypt(jsonObject.getString("params"));
        } else
            return new JSONObject();
    }
}

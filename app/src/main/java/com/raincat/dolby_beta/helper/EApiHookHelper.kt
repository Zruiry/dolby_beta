/**
 * 新版网易云EAPI Hook辅助类 - 从OkHttp请求对象中提取请求参数
 * 新版网易云中，请求参数存储在 request.tag() 对象的 J() 方法返回值中
 * J() 返回的 n72.a 对象包含 LinkedHashMap<String, Object> 类型的参数Map
 *
 * 从Legacy API迁移到Modern libxposed API
 * 旧版：XposedHelpers.callMethod/getObjectField → 新版：Java反射Method.invoke/Field.get
 *
 */
package com.raincat.dolby_beta.helper

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.LinkedHashMap

object EApiHookHelper {

    /**
     * 从OkHttp Request对象中提取请求参数
     * 新版网易云中，EAPI请求的参数存储在 request.tag() 对象中：
     * 1. request.tag() 返回 o72.a 对象（EAPI请求标签）
     * 2. o72.a.J() 返回 n72.a 对象（请求参数容器）
     * 3. n72.a.f256148a 是 LinkedHashMap<String, Object> 类型的参数Map
     *
     * @param request okhttp3.Request 对象
     * @return 请求参数Map，键值对均为String类型；如果提取失败返回空Map
     */
    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun getRequestParams(request: Any): LinkedHashMap<String, String> {
        val result = LinkedHashMap<String, String>()
        try {
            // 旧版：XposedHelpers.callMethod(request, "tag")
            // 新版：Java反射调用Method.invoke
            val tag = callMethod(request, "tag") ?: return result

            try {
                // 旧版：XposedHelpers.callMethod(tag, "J")
                // 新版：Java反射调用
                val paramsContainer = callMethod(tag, "J") ?: return result

                // 尝试通过i()方法获取参数Map（更安全的方式）
                try {
                    val paramsMap = callMethod(paramsContainer, "i")
                    if (paramsMap is LinkedHashMap<*, *>) {
                        val rawMap = paramsMap as LinkedHashMap<String, Any>
                        for (key in rawMap.keys) {
                            val value = rawMap[key]
                            result[key] = value?.toString() ?: ""
                        }
                    }
                } catch (e: Exception) {
                    // i()方法调用失败，尝试直接访问字段
                    try {
                        val paramsMap = getObjectField(paramsContainer, "f256148a")
                        if (paramsMap is LinkedHashMap<*, *>) {
                            val rawMap = paramsMap as LinkedHashMap<String, Any>
                            for (key in rawMap.keys) {
                                val value = rawMap[key]
                                result[key] = value?.toString() ?: ""
                            }
                        }
                    } catch (e2: Exception) {
                        // 字段名可能因混淆变化，尝试遍历字段查找LinkedHashMap
                        extractParamsByTraversal(paramsContainer, result)
                    }
                }
            } catch (e: Exception) {
                // J()方法不存在，尝试通过反射遍历字段查找参数
                extractParamsByTraversal(tag, result)
            }
        } catch (e: Exception) {
            // 提取参数失败，返回空Map
        }
        return result
    }

    /**
     * 通过反射遍历对象字段查找LinkedHashMap类型的参数Map
     * 当方法名和字段名因混淆变化时，通过类型匹配来查找参数
     */
    @Suppress("UNCHECKED_CAST")
    private fun extractParamsByTraversal(obj: Any, result: LinkedHashMap<String, String>) {
        try {
            val fields = obj.javaClass.declaredFields
            for (field in fields) {
                field.isAccessible = true
                val value = field.get(obj)
                if (value is LinkedHashMap<*, *>) {
                    val map = value
                    if (map.isNotEmpty()) {
                        val firstKey = map.keys.iterator().next()
                        if (firstKey is String) {
                            val rawMap = value as LinkedHashMap<String, Any>
                            for (key in rawMap.keys) {
                                val v = rawMap[key]
                                result[key] = v?.toString() ?: ""
                            }
                            return
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // 遍历失败
        }
    }

    /**
     * 通过反射调用对象的无参方法（替代XposedHelpers.callMethod）
     */
    @Throws(Exception::class)
    private fun callMethod(obj: Any, methodName: String): Any? {
        val method: Method = obj.javaClass.getDeclaredMethod(methodName)
        method.isAccessible = true
        return method.invoke(obj)
    }

    /**
     * 通过反射获取对象的字段值（替代XposedHelpers.getObjectField）
     */
    @Throws(Exception::class)
    private fun getObjectField(obj: Any, fieldName: String): Any? {
        val field: Field = obj.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(obj)
    }
}

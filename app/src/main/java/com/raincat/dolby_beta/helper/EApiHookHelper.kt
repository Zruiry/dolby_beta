package com.raincat.dolby_beta.helper;

import java.util.LinkedHashMap;

import de.robv.android.xposed.XposedHelpers;

/**
 * <pre>
 *     author : RainCat
 *     e-mail : nining377@gmail.com
 *     time   : 2026/06/13
 *     desc   : 新版网易云EAPI Hook辅助类
 *              用于从新版OkHttp请求对象中提取请求参数
 *              新版网易云中，请求参数存储在 request.tag() 对象的 J() 方法返回值中
 *              J() 返回的 n72.a 对象包含 LinkedHashMap<String, Object> 类型的参数Map
 *     version: 1.0
 * </pre>
 */
public class EApiHookHelper {

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
    @SuppressWarnings("unchecked")
    public static LinkedHashMap<String, String> getRequestParams(Object request) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        try {
            // 获取请求标签对象 request.tag()
            Object tag = XposedHelpers.callMethod(request, "tag");
            if (tag == null) return result;

            // 检查tag是否是o72.a类型（EAPI请求标签）
            Class<?> tagClass = tag.getClass();
            // o72.a继承自o72.p，o72.p继承自o72.f
            // o72.f中有J()方法返回n72.a（请求参数容器）
            try {
                Object paramsContainer = XposedHelpers.callMethod(tag, "J");
                if (paramsContainer == null) return result;

                // n72.a中有f256148a字段（LinkedHashMap<String, Object>）
                // 尝试通过i()方法获取参数Map（更安全的方式）
                try {
                    Object paramsMap = XposedHelpers.callMethod(paramsContainer, "i");
                    if (paramsMap instanceof LinkedHashMap) {
                        LinkedHashMap<String, Object> rawMap = (LinkedHashMap<String, Object>) paramsMap;
                        for (String key : rawMap.keySet()) {
                            Object value = rawMap.get(key);
                            result.put(key, value != null ? value.toString() : "");
                        }
                    }
                } catch (Exception e) {
                    // i()方法调用失败，尝试直接访问字段
                    try {
                        Object paramsMap = XposedHelpers.getObjectField(paramsContainer, "f256148a");
                        if (paramsMap instanceof LinkedHashMap) {
                            LinkedHashMap<String, Object> rawMap = (LinkedHashMap<String, Object>) paramsMap;
                            for (String key : rawMap.keySet()) {
                                Object value = rawMap.get(key);
                                result.put(key, value != null ? value.toString() : "");
                            }
                        }
                    } catch (Exception e2) {
                        // 字段名可能因混淆变化，尝试遍历字段查找LinkedHashMap
                        extractParamsByTraversal(paramsContainer, result);
                    }
                }
            } catch (Exception e) {
                // J()方法不存在，尝试通过反射遍历字段查找参数
                extractParamsByTraversal(tag, result);
            }
        } catch (Exception e) {
            // 提取参数失败，返回空Map
        }
        return result;
    }

    /**
     * 通过反射遍历对象字段查找LinkedHashMap类型的参数Map
     * 当方法名和字段名因混淆变化时，通过类型匹配来查找参数
     *
     * @param obj    要遍历的对象
     * @param result 用于存储提取结果的Map
     */
    @SuppressWarnings("unchecked")
    private static void extractParamsByTraversal(Object obj, LinkedHashMap<String, String> result) {
        try {
            java.lang.reflect.Field[] fields = obj.getClass().getDeclaredFields();
            for (java.lang.reflect.Field field : fields) {
                field.setAccessible(true);
                Object value = field.get(obj);
                if (value instanceof LinkedHashMap) {
                    // 检查Map的键是否为String类型
                    LinkedHashMap<?, ?> map = (LinkedHashMap<?, ?>) value;
                    if (!map.isEmpty()) {
                        Object firstKey = map.keySet().iterator().next();
                        if (firstKey instanceof String) {
                            // 找到参数Map
                            LinkedHashMap<String, Object> rawMap = (LinkedHashMap<String, Object>) value;
                            for (String key : rawMap.keySet()) {
                                Object v = rawMap.get(key);
                                result.put(key, v != null ? v.toString() : "");
                            }
                            return;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 遍历失败
        }
    }
}

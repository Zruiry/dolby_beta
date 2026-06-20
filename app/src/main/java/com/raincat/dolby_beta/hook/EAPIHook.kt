package com.raincat.dolby_beta.hook;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.raincat.dolby_beta.helper.ClassHelper;
import com.raincat.dolby_beta.helper.EApiHookHelper;
import com.raincat.dolby_beta.helper.EAPIHelper;
import com.raincat.dolby_beta.helper.ExtraHelper;
import com.raincat.dolby_beta.helper.SettingHelper;
import com.raincat.dolby_beta.net.HTTPSTrustManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * <pre>
 *     author : RainCat
 *     e-mail : nining377@gmail.com
 *     time   : 2021/04/16
 *     desc   : 网络访问hook - 仅保留音源代理功能
 *              拦截EAPI请求响应，检测空音源并通过代理获取替换音源
 *              旧版：通过ClassHelper.HttpResponse.getResultMethod() hook响应处理方法
 *              新版（9.5.30+）：通过hook EAPI解密拦截器interceptor.s.intercept()方法
 *     version: 3.0
 * </pre>
 */
public class EAPIHook {
    private static final String TAG = "dolby_beta";
    private final Context appContext;

    public EAPIHook(final Context context) {
        this.appContext = context;
        // 优先尝试新版hook方式（hook EAPI解密拦截器）
        boolean hooked = hookNewVersion(context);
        // 如果新版hook失败，回退到旧版方式
        if (!hooked) {
            hookOldVersion(context);
        }
    }

    /**
     * 新版hook方式：动态查找EAPI解密拦截器并hook其intercept()方法
     *
     * 设计思路：
     * 混淆后类名在不同版本间会变化（如9.5.25中是interceptor.r，9.5.30中是interceptor.s），
     * 因此不能硬编码类名，需要通过类特征动态查找：
     * 1. 实现okhttp3.Interceptor接口
     * 2. 包含intercept方法
     * 3. 内部调用了NeteaseMusicUtils.deserialdata（EAPI解密特征）
     *
     * hook流程：
     * 1. beforeHookedMethod：仅记录请求URL路径，不做任何修改
     * 2. afterHookedMethod：读取解密后的响应内容，检测空音源并通过代理获取替换
     *
     * @param context 应用上下文
     * @return 是否hook成功
     */
    private boolean hookNewVersion(final Context context) {
        try {
            Class<?> eapiDecryptInterceptorClass = findEapiDecryptInterceptor(context);
            if (eapiDecryptInterceptorClass == null) {
                XposedBridge.log("EAPIHook: 未找到EAPI解密拦截器类，回退旧版方式");
                Log.d(TAG, "EAPIHook: 未找到EAPI解密拦截器类，回退旧版方式");
                return false;
            }

            Method interceptMethod = null;
            for (Method m : eapiDecryptInterceptorClass.getDeclaredMethods()) {
                if (m.getName().equals("intercept")) {
                    interceptMethod = m;
                    break;
                }
            }
            if (interceptMethod == null) {
                XposedBridge.log("EAPIHook: EAPI解密拦截器intercept方法未找到，回退旧版方式");
                return false;
            }

            XposedBridge.log("EAPIHook: 使用新版hook方式，拦截器类=" + eapiDecryptInterceptorClass.getName());
            Log.d(TAG, "EAPIHook: 使用新版hook方式，拦截器类=" + eapiDecryptInterceptorClass.getName());
            XposedBridge.hookMethod(interceptMethod, new XC_MethodHook() {
                private final ThreadLocal<String> requestUrlPath = new ThreadLocal<>();

                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        Object chain = param.args[0];
                        Object request = XposedHelpers.callMethod(chain, "request");
                        Object httpUrl = XposedHelpers.callMethod(request, "url");
                        String urlPath = (String) XposedHelpers.callMethod(httpUrl, "encodedPath");
                        requestUrlPath.set(urlPath);
                    } catch (Exception e) {
                        requestUrlPath.set("unknown");
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    String urlPath = requestUrlPath.get();
                    requestUrlPath.remove();

                    // 代理未开启则跳过
                    if (!SettingHelper.getInstance().isEnable(SettingHelper.proxy_master_key))
                        return;

                    // 检查原始方法是否抛出了异常
                    Throwable throwable = param.getThrowable();
                    if (throwable != null) {
                        Log.e(TAG, "EAPIHook: intercept异常 - " + throwable.getMessage());
                        try {
                            String errorContent;
                            if (urlPath != null && (urlPath.contains("song/enhance/player/url")
                                    || urlPath.contains("song/enhance/download/url"))) {
                                errorContent = "{\"code\":200,\"data\":[]}";
                            } else {
                                errorContent = "{\"code\":500,\"message\":\"network error\"}";
                            }
                            Object errorResponse = buildErrorResponse(param, errorContent);
                            if (errorResponse != null) {
                                param.setResult(errorResponse);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "EAPIHook: 构造错误响应失败 - " + e.getMessage());
                            param.setResult(null);
                        }
                        return;
                    }

                    Object response = param.getResult();
                    if (response == null) return;

                    Object responseBody = XposedHelpers.callMethod(response, "body");
                    if (responseBody == null) return;

                    // 只处理EAPI请求
                    if (urlPath == null || (!urlPath.contains("/eapi/") && !urlPath.contains("/xeapi/"))) return;

                    String original;
                    Object contentType;
                    try {
                        contentType = XposedHelpers.callMethod(responseBody, "contentType");
                        original = (String) XposedHelpers.callMethod(responseBody, "string");
                    } catch (Exception e) {
                        XposedBridge.log("EAPIHook: 读取responseBody失败 - " + e.getMessage());
                        return;
                    }
                    if (TextUtils.isEmpty(original)) return;

                    Object chain = param.args[0];
                    Object request = XposedHelpers.callMethod(chain, "request");
                    LinkedHashMap<String, String> paramsMap = EApiHookHelper.getRequestParams(request);

                    // 处理代理音源替换
                    String modified = processEapiResponse(context, urlPath, original, paramsMap);

                    // 无论是否修改内容都必须重建ResponseBody
                    String finalContent = (modified != null) ? modified : original;
                    rebuildResponseBody(param, response, contentType, finalContent);
                }
            });
            return true;
        } catch (Throwable t) {
            XposedBridge.log("EAPIHook: 新版hook方式失败: " + t.getMessage());
            Log.e(TAG, "EAPIHook: 新版hook方式失败: " + t.getMessage());
            return false;
        }
    }

    /**
     * 重建ResponseBody和Response
     */
    private void rebuildResponseBody(XC_MethodHook.MethodHookParam param, Object response,
                                      Object contentType, String content) throws Exception {
        Class<?> responseBodyClass = XposedHelpers.findClass("okhttp3.ResponseBody", appContext.getClassLoader());
        Object newBody;
        try {
            newBody = XposedHelpers.callStaticMethod(responseBodyClass, "create", contentType, content);
        } catch (Exception e) {
            try {
                newBody = XposedHelpers.callStaticMethod(responseBodyClass, "create", content, contentType);
            } catch (Exception e2) {
                newBody = XposedHelpers.callStaticMethod(responseBodyClass, "create",
                        contentType, content.length(), content);
            }
        }
        Object newResponse = XposedHelpers.callMethod(response, "newBuilder");
        newResponse = XposedHelpers.callMethod(newResponse, "body", newBody);
        newResponse = XposedHelpers.callMethod(newResponse, "header",
                "Content-Length", String.valueOf(content.length()));
        newResponse = XposedHelpers.callMethod(newResponse, "build");
        param.setResult(newResponse);
    }

    /**
     * 动态查找EAPI解密拦截器类
     *
     * 不同版本的混淆后类名不同（如9.5.25中是interceptor.r，9.5.30中是interceptor.s），
     * 因此通过类特征来识别，而非硬编码类名。
     *
     * 识别特征：
     * 1. 位于com.netease.cloudmusic.network.interceptor包下
     * 2. 实现okhttp3.Interceptor接口
     * 3. 声明了protected方法b(ResponseBody)（解密Retrofit响应）
     * 4. 声明了private方法a(f, ResponseBody)（解密EAPI响应）
     *
     * @param context 应用上下文
     * @return EAPI解密拦截器Class，未找到返回null
     */
    private Class<?> findEapiDecryptInterceptor(Context context) {
        ClassLoader cl = context.getClassLoader();
        Class<?> interceptorClass = XposedHelpers.findClassIfExists("okhttp3.Interceptor", cl);
        if (interceptorClass == null) {
            XposedBridge.log("EAPIHook: okhttp3.Interceptor接口未找到");
            return null;
        }

        // 尝试已知的类名列表（从新到旧），优先匹配
        String[] knownClassNames = {
                "com.netease.cloudmusic.network.interceptor.s",  // 9.5.30
                "com.netease.cloudmusic.network.interceptor.r",  // 9.5.25
        };
        for (String className : knownClassNames) {
            Class<?> clazz = XposedHelpers.findClassIfExists(className, cl);
            if (clazz != null && isEapiDecryptInterceptor(clazz, interceptorClass)) {
                XposedBridge.log("EAPIHook: 通过已知类名找到EAPI解密拦截器: " + className);
                return clazz;
            }
        }

        // 已知类名未命中，遍历interceptor包下所有类查找
        XposedBridge.log("EAPIHook: 已知类名未命中，尝试遍历查找...");
        try {
            // 通过dex扫描interceptor包下实现Interceptor接口的类
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                    "^com\\.netease\\.cloudmusic\\.network\\.interceptor\\.[a-z]{1,3}$");
            java.util.List<String> classList = ClassHelper.getFilteredClasses(pattern, null);
            for (String className : classList) {
                try {
                    Class<?> clazz = XposedHelpers.findClassIfExists(className, cl);
                    if (clazz != null && isEapiDecryptInterceptor(clazz, interceptorClass)) {
                        XposedBridge.log("EAPIHook: 通过遍历找到EAPI解密拦截器: " + className);
                        return clazz;
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            XposedBridge.log("EAPIHook: 遍历查找EAPI解密拦截器失败: " + e.getMessage());
        }

        return null;
    }

    /**
     * 判断一个类是否是EAPI解密拦截器
     *
     * 特征判断：
     * 1. 实现okhttp3.Interceptor接口
     * 2. 包含intercept方法
     * 3. 包含protected方法b(ResponseBody)（Retrofit解密）
     * 4. 包含private方法a(xxx, ResponseBody)（EAPI解密）
     *
     * @param clazz 待检查的类
     * @param interceptorClass okhttp3.Interceptor接口Class
     * @return 是否是EAPI解密拦截器
     */
    private boolean isEapiDecryptInterceptor(Class<?> clazz, Class<?> interceptorClass) {
        try {
            // 必须实现Interceptor接口
            if (!interceptorClass.isAssignableFrom(clazz)) return false;

            // 必须有intercept方法
            boolean hasIntercept = false;
            boolean hasDecryptMethod = false;

            for (Method m : clazz.getDeclaredMethods()) {
                if (m.getName().equals("intercept")) {
                    hasIntercept = true;
                }
                // EAPI解密拦截器有protected b(ResponseBody)方法用于Retrofit解密
                // 以及private a(xxx, ResponseBody)方法用于EAPI解密
                // 这两个方法的共同特征是参数包含ResponseBody
                if (m.getName().equals("b") || m.getName().equals("a")) {
                    Class<?>[] paramTypes = m.getParameterTypes();
                    for (Class<?> pt : paramTypes) {
                        if (pt.getName().contains("ResponseBody")) {
                            hasDecryptMethod = true;
                            break;
                        }
                    }
                }
            }

            return hasIntercept && hasDecryptMethod;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 旧版hook方式：通过ClassHelper.HttpResponse.getResultMethod() hook响应处理方法
     */
    private void hookOldVersion(final Context context) {
        Method resultMethod = ClassHelper.HttpResponse.getResultMethod(context);
        if (resultMethod == null) {
            XposedBridge.log("EAPIHook: getResultMethod返回null，跳过hook");
            Log.w(TAG, "EAPIHook: getResultMethod返回null，跳过hook");
            return;
        }
        XposedBridge.log("EAPIHook: 使用旧版hook方式（HttpResponse.getResultMethod）");
        XposedBridge.hookMethod(resultMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                // 代理未开启则跳过
                if (!SettingHelper.getInstance().isEnable(SettingHelper.proxy_master_key))
                    return;
                // 返回参数不对
                if ((!(param.getResult() instanceof String) && !(param.getResult() instanceof JSONObject)))
                    return;
                // 返回参数为空
                String original = param.getResult().toString();
                if (TextUtils.isEmpty(original)) {
                    return;
                }
                ClassHelper.HttpResponse httpResponse = new ClassHelper.HttpResponse(param.thisObject);
                Object eapi = httpResponse.getEapi(context);
                android.net.Uri uri = ClassHelper.HttpUrl.getUri(context, eapi);
                if (!uri.getPath().contains("/eapi/"))
                    return;
                String path = uri.getPath();

                LinkedHashMap<String, String> paramsMap = ClassHelper.HttpParams.getParams(context, eapi);
                String modified = processEapiResponse(context, path, original, paramsMap);

                if (modified != null) {
                    param.setResult(param.getResult() instanceof JSONObject ? new JSONObject(modified) : modified);
                }
            }
        });
    }

    /**
     * 检查音源响应中是否有空URL，如果有则通过代理服务器获取替换音源
     *
     * 设计思路：
     * 1. 先让请求正常走网易云服务器，获取原始响应
     * 2. 检查响应中是否有歌曲的URL为空（无法播放的付费/下架歌曲）
     * 3. 只有URL为空的歌曲才通过代理服务器获取替换音源
     * 4. 将替换音源合并到原始响应中
     *
     * @param context    应用上下文
     * @param modified   经过modifyPlayer处理后的响应JSON
     * @param paramsMap  原始请求参数
     * @param path       请求路径
     * @return 合并替换音源后的响应JSON，如果没有空URL则原样返回
     */
    private String replaceEmptyUrlWithProxy(Context context, String modified,
                                             LinkedHashMap<String, String> paramsMap,
                                             String path) {
        try {
            JSONObject responseJson = new JSONObject(modified);
            JSONArray dataArray = responseJson.optJSONArray("data");
            if (dataArray == null || dataArray.length() == 0) return modified;

            // 收集URL为空的歌曲ID
            StringBuilder emptyIds = new StringBuilder();
            int emptyCount = 0;
            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject songObj = dataArray.optJSONObject(i);
                if (songObj == null) continue;
                boolean urlEmpty = songObj.isNull("url") || songObj.optString("url", "").isEmpty();
                long songId = songObj.optLong("id", 0);
                int code = songObj.optInt("code", 0);
                if (songId != 0 && (urlEmpty || code != 200)) {
                    if (emptyIds.length() > 0) emptyIds.append(",");
                    emptyIds.append(songId).append("_0");
                    emptyCount++;
                }
            }

            if (emptyCount == 0) return modified;

            Log.d(TAG, "EAPIHook: 代理替换，发现" + emptyCount + "首空音源 IDs=" + emptyIds);

            // 从请求参数中提取音质等级
            String level = "exhigh";
            String encodeType = "aac";
            if (paramsMap != null) {
                try {
                    String paramsStr = paramsMap.get("params");
                    if (paramsStr != null) {
                        JSONObject paramsJson = EAPIHelper.decrypt(paramsStr);
                        if (paramsJson != null) {
                            level = paramsJson.optString("level", level);
                            encodeType = paramsJson.optString("encodeType", encodeType);
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "EAPIHook: 解析请求参数失败 - " + e.getMessage());
                }
            }

            String proxyResult = requestProxyForSongUrl(context, emptyIds.toString(), level, encodeType);
            if (proxyResult == null || proxyResult.isEmpty()) {
                Log.w(TAG, "EAPIHook: 代理未返回替换音源");
                return modified;
            }

            // 解析代理返回的替换音源，合并到原始响应中
            try {
                JSONObject proxyJson = new JSONObject(proxyResult);
                if (proxyJson.optInt("code") != 200) {
                    Log.w(TAG, "EAPIHook: 代理返回code=" + proxyJson.optInt("code") + "，替换失败");
                    return modified;
                }
                JSONArray proxyData = proxyJson.optJSONArray("data");
                if (proxyData == null) {
                    Log.w(TAG, "EAPIHook: 代理返回data为null");
                    return modified;
                }

                // 将代理返回的音源信息合并到原始响应
                for (int i = 0; i < proxyData.length(); i++) {
                    JSONObject proxySong = proxyData.optJSONObject(i);
                    if (proxySong == null) continue;
                    long proxySongId = proxySong.optLong("id", 0);
                    String proxyUrl = proxySong.optString("url", "");
                    if (proxySongId == 0 || proxyUrl == null || proxyUrl.isEmpty()) continue;

                    String actualUrl = decodePackageUrl(proxyUrl);
                    if (actualUrl != null) proxyUrl = actualUrl;

                    // 在原始响应中找到对应歌曲，替换URL
                    for (int j = 0; j < dataArray.length(); j++) {
                        JSONObject songObj = dataArray.optJSONObject(j);
                        if (songObj != null && songObj.optLong("id") == proxySongId) {
                            songObj.put("url", proxyUrl);
                            songObj.put("code", 200);
                            if (proxySong.has("br")) songObj.put("br", proxySong.optInt("br"));
                            if (proxySong.has("size")) songObj.put("size", proxySong.optInt("size"));
                            if (proxySong.has("md5")) songObj.put("md5", proxySong.optString("md5"));
                            if (proxySong.has("type")) songObj.put("type", proxySong.optString("type"));
                            if (proxySong.has("level")) songObj.put("level", proxySong.optString("level"));
                            if (proxySong.has("encodeType")) songObj.put("encodeType", proxySong.optString("encodeType"));
                            dataArray.put(j, songObj);
                            Log.d(TAG, "EAPIHook: 歌曲ID=" + proxySongId + " 代理替换成功");
                            break;
                        }
                    }
                }
                responseJson.put("data", dataArray);
                return responseJson.toString();
            } catch (Exception e) {
                Log.e(TAG, "EAPIHook: 合并代理音源失败 - " + e.getMessage());
                return modified;
            }
        } catch (Exception e) {
            Log.e(TAG, "EAPIHook: replaceEmptyUrlWithProxy异常 - " + e.getMessage());
            return modified;
        }
    }

    /**
     * 解码代理服务器返回的/package/格式URL
     */
    private String decodePackageUrl(String packageUrl) {
        try {
            if (packageUrl == null || !packageUrl.contains("/package/")) return null;

            int packageStart = packageUrl.indexOf("/package/");
            String afterPackage = packageUrl.substring(packageStart + "/package/".length());

            int slashIndex = afterPackage.indexOf('/');
            if (slashIndex <= 0) return null;

            String base64Part = afterPackage.substring(0, slashIndex);

            byte[] decoded = android.util.Base64.decode(base64Part, android.util.Base64.DEFAULT);
            String actualUrl = new String(decoded, "UTF-8");

            if (actualUrl.startsWith("http://") || actualUrl.startsWith("https://")) {
                return actualUrl;
            }
            Log.w(TAG, "EAPIHook: /package/ Base64解码结果不是有效URL: " + actualUrl);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "EAPIHook: /package/ URL解码失败 - " + e.getMessage());
            return null;
        }
    }

    /**
     * 通过代理服务器请求替换音源URL
     */
    private String requestProxyForSongUrl(Context context, String ids, String level, String encodeType) {
        try {
            String proxyHost = SettingHelper.getInstance().getSetting(SettingHelper.proxy_server_key) ?
                    SettingHelper.getInstance().getHttpProxy() : "127.0.0.1";
            int proxyPort = SettingHelper.getInstance().getProxyPort();

            Log.d(TAG, "EAPIHook: 代理请求 ids=" + ids + " level=" + level);

            String[] idArray = ids.split(",");
            StringBuilder idsJson = new StringBuilder("[");
            for (int i = 0; i < idArray.length; i++) {
                if (i > 0) idsJson.append(",");
                idsJson.append("\"").append(idArray[i]).append("\"");
            }
            idsJson.append("]");

            String urlStr = "https://interface3.music.163.com/api/song/enhance/player/url/v1?"
                    + "ids=" + java.net.URLEncoder.encode(idsJson.toString(), "UTF-8")
                    + "&level=" + level
                    + "&encodeType=" + encodeType;

            java.net.Proxy proxy = new java.net.Proxy(java.net.Proxy.Type.HTTP,
                    new java.net.InetSocketAddress(proxyHost, proxyPort));

            javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
            sslContext.init(null, new javax.net.ssl.TrustManager[]{new HTTPSTrustManager()}, new java.security.SecureRandom());

            java.net.URL url = new java.net.URL(urlStr);
            javax.net.ssl.HttpsURLConnection conn = (javax.net.ssl.HttpsURLConnection) url.openConnection(proxy);
            conn.setSSLSocketFactory(sslContext.getSocketFactory());
            conn.setHostnameVerifier((hostname, session) -> true);
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Cookie", "os=android");
            conn.setRequestProperty("User-Agent", "NeteaseMusic/8.10.05");
            conn.setRequestProperty("Accept", "*/*");
            conn.setRequestProperty("Accept-Encoding", "identity");

            int responseCode = conn.getResponseCode();

            if (responseCode == 200) {
                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                return response.toString();
            } else {
                java.io.InputStream errorStream = conn.getErrorStream();
                if (errorStream != null) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(errorStream, "UTF-8"));
                    StringBuilder errorResponse = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorResponse.append(line);
                    }
                    reader.close();
                    Log.e(TAG, "EAPIHook: 代理请求失败 code=" + responseCode + " " + errorResponse.toString());
                }
                return null;
            }
        } catch (java.net.ConnectException e) {
            Log.e(TAG, "EAPIHook: 代理连接失败 - " + e.getMessage());
            return null;
        } catch (java.net.SocketTimeoutException e) {
            Log.e(TAG, "EAPIHook: 代理请求超时");
            return null;
        } catch (Exception e) {
            Log.e(TAG, "EAPIHook: 代理请求异常 - " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * 处理EAPI响应内容，根据请求路径进行不同的修改
     * 仅保留音源代理相关逻辑
     *
     * @param context    应用上下文
     * @param path       请求路径
     * @param original   原始响应内容
     * @param paramsMap  请求参数Map
     * @return 修改后的响应内容，如果不需要修改返回null
     */
    private String processEapiResponse(Context context, String path, String original,
                                        LinkedHashMap<String, String> paramsMap) throws Throwable {
        // 检查代理模式是否已启动
        boolean proxyActive = SettingHelper.getInstance().isEnable(SettingHelper.proxy_master_key)
                && "1".equals(ExtraHelper.getExtraDate(ExtraHelper.SCRIPT_STATUS));

        if (path.contains("song/enhance/player/url")) {
            // 先执行本地修改（设置fee=0, flag=0等），使歌曲可播放
            String modified = EAPIHelper.modifyPlayer(original);

            // 代理模式下，检查音源是否为空，为空时通过代理获取替换音源
            if (proxyActive) {
                modified = replaceEmptyUrlWithProxy(context, modified, paramsMap, path);
            }
            return modified;
        } else if (path.contains("song/enhance/download/url")) {
            JSONObject jsonObject = new JSONObject(original);
            JSONObject object = jsonObject.getJSONObject("data");
            JSONArray array = new JSONArray();
            array.put(object);
            jsonObject.put("data", array);
            String modified = EAPIHelper.modifyPlayer(jsonObject.toString())
                    .replace("[", "").replace("]", "");

            // 代理模式下，检查下载音源是否为空，为空时通过代理获取替换
            if (proxyActive) {
                modified = replaceEmptyUrlWithProxy(context, modified, paramsMap, path);
            }
            return modified;
        } else if (path.contains("batch")) {
            // batch请求中包含歌曲详情和版权信息，需要修改privilege使无版权歌曲可播放
            return processBatchPrivilege(original);
        } else if (path.contains("song/detail") || path.contains("song/privilege")) {
            // 歌曲详情/版权信息请求，修改privilege使无版权歌曲可播放
            return processSongPrivilege(original);
        }
        return null;
    }

    /**
     * 处理batch请求中的版权信息
     *
     * batch请求会将多个API请求合并到一个响应中，格式如：
     * {"/api/v1/song/detail": {...}, "/api/v1/playlist/manipulate/tracks": {...}}
     *
     * 需要遍历所有key，找到包含songs/privilege的响应并修改版权字段
     *
     * @param original 原始响应
     * @return 修改后的响应，无需修改返回null
     */
    private String processBatchPrivilege(String original) throws Throwable {
        JSONObject jsonObject = new JSONObject(original);
        boolean modified = false;

        // 遍历batch响应中的所有key
        java.util.Iterator<String> keys = jsonObject.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!jsonObject.isNull(key)) {
                Object value = jsonObject.get(key);
                if (value instanceof JSONObject) {
                    JSONObject subObj = (JSONObject) value;
                    // 处理包含songs数组的响应（如song/detail）
                    if (subObj.has("songs") || subObj.has("privileges")) {
                        modifyPrivilegeInResponse(subObj);
                        modified = true;
                    }
                }
            }
        }

        return modified ? jsonObject.toString() : null;
    }

    /**
     * 处理歌曲详情/版权信息请求
     *
     * 响应格式：
     * {"songs": [...], "privileges": [...]}
     * 或
     * {"code": 200, "data": [{"privilege": {...}, ...}]}
     *
     * @param original 原始响应
     * @return 修改后的响应，无需修改返回null
     */
    private String processSongPrivilege(String original) throws Throwable {
        JSONObject jsonObject = new JSONObject(original);
        boolean modified = modifyPrivilegeInResponse(jsonObject);
        return modified ? jsonObject.toString() : null;
    }

    /**
     * 修改响应中的版权信息，使无版权歌曲显示为可播放
     *
     * 核心逻辑：
     * - offlinestatus < 0 表示无版权，客户端会弹出"无版权"弹窗
     * - playMaxLevel > 0 表示可在线播放
     * - downMaxLevel > 0 表示可下载
     * - flag & 128 (NO_COPRYRIGHT) 表示无版权标记
     *
     * 修改策略：
     * 1. offlinestatus: 设为0（有版权）
     * 2. playMaxLevel: 设为320000（可播放最高品质）
     * 3. downMaxLevel: 设为320000（可下载最高品质）
     * 4. fee: 设为0（免费）
     * 5. flag: 清除NO_COPRYRIGHT标记
     * 6. payed: 设为0
     *
     * @param jsonObject 响应JSON对象
     * @return 是否进行了修改
     */
    private boolean modifyPrivilegeInResponse(JSONObject jsonObject) throws Throwable {
        boolean modified = false;

        // 处理privileges数组
        if (jsonObject.has("privileges")) {
            JSONArray privileges = jsonObject.optJSONArray("privileges");
            if (privileges != null) {
                for (int i = 0; i < privileges.length(); i++) {
                    JSONObject priv = privileges.optJSONObject(i);
                    if (priv != null && modifySinglePrivilege(priv)) {
                        modified = true;
                    }
                }
            }
        }

        // 处理songs数组中的privilege字段
        if (jsonObject.has("songs")) {
            JSONArray songs = jsonObject.optJSONArray("songs");
            if (songs != null) {
                for (int i = 0; i < songs.length(); i++) {
                    JSONObject song = songs.optJSONObject(i);
                    if (song != null && song.has("privilege")) {
                        JSONObject priv = song.optJSONObject("privilege");
                        if (priv != null && modifySinglePrivilege(priv)) {
                            modified = true;
                        }
                    }
                }
            }
        }

        // 处理data数组中的privilege字段
        if (jsonObject.has("data")) {
            Object dataObj = jsonObject.get("data");
            if (dataObj instanceof JSONArray) {
                JSONArray dataArray = (JSONArray) dataObj;
                for (int i = 0; i < dataArray.length(); i++) {
                    JSONObject item = dataArray.optJSONObject(i);
                    if (item != null && item.has("privilege")) {
                        JSONObject priv = item.optJSONObject("privilege");
                        if (priv != null && modifySinglePrivilege(priv)) {
                            modified = true;
                        }
                    }
                }
            } else if (dataObj instanceof JSONObject) {
                JSONObject data = (JSONObject) dataObj;
                if (data.has("privilege")) {
                    JSONObject priv = data.optJSONObject("privilege");
                    if (priv != null && modifySinglePrivilege(priv)) {
                        modified = true;
                    }
                }
            }
        }

        return modified;
    }

    /**
     * 修改单个privilege对象，使歌曲显示为可播放
     *
     * @param priv privilege JSON对象
     * @return 是否进行了修改
     */
    private boolean modifySinglePrivilege(JSONObject priv) throws Throwable {
        // 只修改无版权的歌曲（offlinestatus < 0 或 fee > 0 或有NO_COPRYRIGHT标记）
        int offlinestatus = priv.optInt("offlinestatus", 0);
        int fee = priv.optInt("fee", 0);
        int flag = priv.optInt("flag", 0);

        // 如果已经是免费且有版权，不需要修改
        if (offlinestatus >= 0 && fee == 0 && (flag & 128) == 0) {
            return false;
        }

        // 修改版权状态
        priv.put("offlinestatus", 0);           // 有版权
        priv.put("playMaxLevel", 320000);        // 可播放（最高品质）
        priv.put("downMaxLevel", 320000);        // 可下载（最高品质）
        priv.put("fee", 0);                      // 免费
        priv.put("flag", flag & ~128);           // 清除NO_COPRYRIGHT标记
        priv.put("payed", 0);                    // 未付费（免费不需要付费）
        priv.put("maxbr", 999000);               // 最高音质

        // 清除试听信息
        priv.remove("freeTrialInfo");
        priv.remove("freeTrialPrivilege");

        return true;
    }

    /**
     * 构造一个HTTP错误响应对象
     */
    private Object buildErrorResponse(XC_MethodHook.MethodHookParam param, String content) {
        try {
            Object chain = param.args[0];
            Object request = XposedHelpers.callMethod(chain, "request");

            Class<?> responseBodyClass = XposedHelpers.findClass("okhttp3.ResponseBody", appContext.getClassLoader());
            Object mediaType = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("okhttp3.MediaType", appContext.getClassLoader()),
                    "parse", "application/json; charset=utf-8");
            Object errorBody = XposedHelpers.callStaticMethod(responseBodyClass, "create", mediaType, content);

            Class<?> responseBuilderClass = XposedHelpers.findClass(
                    "okhttp3.Response$Builder", appContext.getClassLoader());
            Object responseBuilder = responseBuilderClass.newInstance();
            XposedHelpers.callMethod(responseBuilder, "request", request);
            XposedHelpers.callMethod(responseBuilder, "protocol",
                    XposedHelpers.getStaticObjectField(
                            XposedHelpers.findClass("okhttp3.Protocol", appContext.getClassLoader()),
                            "HTTP_1_1"));
            XposedHelpers.callMethod(responseBuilder, "code", 500);
            XposedHelpers.callMethod(responseBuilder, "message", "Network Error");
            XposedHelpers.callMethod(responseBuilder, "body", errorBody);
            return XposedHelpers.callMethod(responseBuilder, "build");
        } catch (Exception e) {
            Log.e(TAG, "EAPIHook: buildErrorResponse失败 - " + e.getMessage());
            XposedBridge.log("EAPIHook: buildErrorResponse失败 - " + e.getMessage());
            return null;
        }
    }

    /**
     * 不变灰功能：Hook MusicInfo.hasCopyRight() 返回true
     *
     * 原理：
     * MusicInfo.hasCopyRight() 内部调用 SongPrivilege.hasCopyRight()，
     * 而 SongPrivilege.hasCopyRight() = offlinestatus >= 0。
     * 当歌曲无版权时 offlinestatus < 0，hasCopyRight() 返回false，
     * 客户端会弹出"因合作方要求，该资源暂时无法收听"弹窗。
     *
     * 直接Hook hasCopyRight() 返回true，让客户端认为所有歌曲都有版权，
     * 这样就不会弹无版权弹窗，而是进入播放页面请求player/url，
     * 然后由代理服务器替换空音源。
     */
    public static void hookGrayFunction(Context context) {
        if (!SettingHelper.getInstance().isEnable(SettingHelper.proxy_gray_key))
            return;

        try {
            Class<?> musicInfoClass = XposedHelpers.findClassIfExists(
                    "com.netease.cloudmusic.meta.MusicInfo", context.getClassLoader());
            if (musicInfoClass != null) {
                XposedHelpers.findAndHookMethod(musicInfoClass, "hasCopyRight",
                        XC_MethodReplacement.returnConstant(true));
                XposedBridge.log("EAPIHook: 成功hook MusicInfo.hasCopyRight()");
            }
        } catch (Throwable e) {
            XposedBridge.log("EAPIHook: hook hasCopyRight失败 - " + e.getMessage());
        }
    }

    /**
     * 音源代理功能：Hook SongPrivilege的设置方法，使歌曲可播放
     *
     * 原理：
     * 当代理总开关开启时，Hook SongPrivilege.setDownloadMaxbr()或setFreeLevel()方法，
     * 在设置下载码率时同时设置playMaxLevel、downMaxLevel等字段，
     * 使歌曲在UI上显示为可播放状态。
     *
     * 这解决了仅靠网络响应修改不够的问题：
     * - 网络响应修改只能修改从服务器获取的数据
     * - 但客户端本地缓存的privilege数据仍可能标记歌曲为不可播放
     * - Hook Java方法可以在任何时机（包括从缓存读取时）修改privilege
     */
    public static void hookSongPrivilege(Context context) {
        if (!SettingHelper.getInstance().isEnable(SettingHelper.proxy_master_key))
            return;

        try {
            Class<?> songPrivilegeClass = XposedHelpers.findClassIfExists(
                    "com.netease.cloudmusic.meta.virtual.SongPrivilege", context.getClassLoader());
            if (songPrivilegeClass == null) {
                XposedBridge.log("EAPIHook: SongPrivilege类未找到，跳过hook");
                return;
            }

            // 查找设置方法，不同版本方法名可能不同
            Method method = null;
            try {
                method = songPrivilegeClass.getMethod("setDownloadMaxbr", int.class);
            } catch (NoSuchMethodException e) {
                try {
                    method = songPrivilegeClass.getMethod("setFreeLevel", int.class);
                } catch (NoSuchMethodException ex) {
                    XposedBridge.log("EAPIHook: 未找到setDownloadMaxbr或setFreeLevel方法");
                    return;
                }
            }

            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Object object = param.thisObject;
                    long id = (long) XposedHelpers.callMethod(object, "getId");
                    // id为0表示无效数据，跳过
                    if (id == 0) return;

                    // 读取maxbr字段
                    int maxbr = 0;
                    Field[] fields = object.getClass().getDeclaredFields();
                    for (Field field : fields) {
                        if (field.getType() == int.class && field.getName().equals("maxbr")) {
                            field.setAccessible(true);
                            maxbr = (int) field.get(object);
                            break;
                        }
                    }
                    if (maxbr == 0) maxbr = 999000;

                    try {
                        param.args[0] = maxbr;
                        XposedHelpers.callMethod(object, "setSubPriv", 1);
                        XposedHelpers.callMethod(object, "setSharePriv", 1);
                        XposedHelpers.callMethod(object, "setCommentPriv", 1);
                        XposedHelpers.callMethod(object, "setDownMaxLevel", maxbr);
                        XposedHelpers.callMethod(object, "setPlayMaxLevel", maxbr);
                        try {
                            if (object.getClass().getDeclaredMethod("setPlayMaxbr", int.class) != null)
                                XposedHelpers.callMethod(object, "setPlayMaxbr", maxbr);
                        } catch (NoSuchMethodException ignored) {
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "EAPIHook: hookSongPrivilege设置字段失败 - " + e.getMessage());
                    }
                }
            });
            XposedBridge.log("EAPIHook: 成功hook SongPrivilege设置方法");
        } catch (Throwable e) {
            XposedBridge.log("EAPIHook: hook SongPrivilege失败 - " + e.getMessage());
        }
    }
}

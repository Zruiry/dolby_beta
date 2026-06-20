package com.raincat.dolby_beta.hook;

import android.content.Context;
import android.util.Log;

import com.raincat.dolby_beta.helper.ExtraHelper;
import com.raincat.dolby_beta.helper.ScriptHelper;
import com.raincat.dolby_beta.helper.SettingHelper;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import static de.robv.android.xposed.XposedBridge.hookAllConstructors;
import static de.robv.android.xposed.XposedHelpers.findClassIfExists;

/**
 * <pre>
 *     author : RainCat
 *     e-mail : nining377@gmail.com
 *     time   : 2021/09/08
 *     desc   : 代理
 *     version: 1.0
 * </pre>
 */

public class ProxyHook {
    private static final String TAG = "dolby_beta";
    private static SSLSocketFactory socketFactory;
    // 自定义的信任所有证书的X509TrustManager实例
    private static X509TrustManager customTrustManager;
    // 缓存的代理OkHttpClient实例，通过newBuilder()从原始客户端创建
    // 避免直接修改共享的OkHttpClient导致并发竞态条件
    private static Object proxyClient;

    private String fieldSSLSocketFactory;
    private String fieldHttpUrl = "url";
    private String fieldProxy = "proxy";
    // 代理URL白名单
    // 注意：song/enhance/player/url 和 song/enhance/download/url 不再走代理路由，
    // 改为在EAPIHook中检测音源为空时再通过代理获取替换音源。
    // 原因：代理服务器返回的EAPI加密响应与新版网易云解密不兼容，
    // 强制路由所有音源请求会导致 deserialdata fail，所有音乐都无法播放。
    private final List<String> whiteUrlList = Arrays.asList("/package");

    public ProxyHook(Context context, boolean isPlayProcess) {
        Class<?> realCallClass = findClassIfExists("okhttp3.internal.connection.RealCall", context.getClassLoader());
        if (realCallClass != null) {
            fieldSSLSocketFactory = "sslSocketFactoryOrNull";
            XposedBridge.log("ProxyHook: 找到okhttp3.internal.connection.RealCall");
            Log.d(TAG, "ProxyHook: 找到okhttp3.internal.connection.RealCall");
        } else {
            realCallClass = findClassIfExists("okhttp3.RealCall", context.getClassLoader());
            if (realCallClass != null) {
                fieldSSLSocketFactory = "sslSocketFactory";
                XposedBridge.log("ProxyHook: 找到okhttp3.RealCall");
                Log.d(TAG, "ProxyHook: 找到okhttp3.RealCall");
            } else {
                realCallClass = findClassIfExists("okhttp3.z", context.getClassLoader());
                fieldSSLSocketFactory = "o";
                fieldHttpUrl = "a";
                fieldProxy = "d";
                XposedBridge.log("ProxyHook: 找到okhttp3.z");
                Log.d(TAG, "ProxyHook: 找到okhttp3.z");
            }
        }

        if (realCallClass == null) {
            XposedBridge.log("ProxyHook: 未找到RealCall类，代理hook失败");
            Log.e(TAG, "ProxyHook: 未找到RealCall类，代理hook失败");
            return;
        }

        hookAllConstructors(realCallClass, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args.length == 3) {
                    Object client = param.args[0];
                    Object request = param.args[1];

                    // 检查代理是否激活：代理主开关开启 且 脚本状态为1
                    boolean proxyActive = SettingHelper.getInstance().isEnable(SettingHelper.proxy_master_key)
                            && "1".equals(ExtraHelper.getExtraDate(ExtraHelper.SCRIPT_STATUS));
                    if (!proxyActive) return;

                    try {
                        Field urlField = request.getClass().getDeclaredField(fieldHttpUrl);
                        urlField.setAccessible(true);
                        Object urlObj = urlField.get(request);
                        for (String url : whiteUrlList) {
                           if (urlObj.toString().contains(url)) {
                                Object pClient = getOrCreateProxyClient(context, client);
                                if (pClient != null) {
                                    param.args[0] = pClient;
                                } else {
                                    Log.e(TAG, "ProxyHook: 创建代理客户端失败");
                                    setProxyFallback(context, client);
                                }
                                break;
                            }
                        }
                    } catch (NoSuchFieldException e) {
                        for (Field f : request.getClass().getDeclaredFields()) {
                            f.setAccessible(true);
                            try {
                                Object val = f.get(request);
                                if (val != null && val.toString().contains("song/enhance/player/url")) {
                                    for (String url : whiteUrlList) {
                                        if (val.toString().contains(url)) {
                                            Object pClient = getOrCreateProxyClient(context, client);
                                            if (pClient != null) {
                                                param.args[0] = pClient;
                                            } else {
                                                Log.e(TAG, "ProxyHook: 创建代理客户端失败");
                                                setProxyFallback(context, client);
                                            }
                                            break;
                                        }
                                    }
                                    break;
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                } else {
                    Log.w(TAG, "ProxyHook: RealCall参数数量不匹配 args.length=" + param.args.length);
                }
            }
        });

        Class<?> okHttpClientBuilderClass = XposedHelpers.findClassIfExists("okhttp3.OkHttpClient$Builder", context.getClassLoader());
        if (okHttpClientBuilderClass != null) {
            XposedBridge.hookAllMethods(okHttpClientBuilderClass, "addInterceptor", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);
                    if (param.args[0].getClass().getName().contains("com.netease.cloudmusic.network.cronet"))
                        param.setResult(param.thisObject);
//                        XposedBridge.hookAllMethods(param.args[0].getClass(), "intercept", new XC_MethodHook() {
//                            @Override
//                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
//                                super.beforeHookedMethod(param);
//                                Object object = param.args[0];
//                                if (object != null && object.getClass().getName().contains("Chain")) {
//                                    Object request = XposedHelpers.callMethod(object, "request");
//                                    if (request.toString().contains("song/enhance/player/url") || request.toString().contains("song/enhance/download/url")) {
//                                        Object response = XposedHelpers.callMethod(object, "proceed", request);
//                                        param.setResult(response);
//                                    }
//                                }
//                            }
//                        });
                }
            });
        }

        if (!isPlayProcess) {
            ExtraHelper.setExtraDate(ExtraHelper.SCRIPT_STATUS, "0");
            if (SettingHelper.getInstance().getSetting(SettingHelper.proxy_master_key)) {
                ScriptHelper.initScript(context, false);
                if (SettingHelper.getInstance().getSetting(SettingHelper.proxy_server_key)) {
                    ScriptHelper.startHttpProxyMode(context);
                } else {
                    ScriptHelper.startScript();
                }
            }
        }
    }

    /**
     * 创建独立的代理OkHttpClient实例
     *
     * 核心设计：通过原始OkHttpClient的newBuilder()创建新的客户端，
     * 在Builder上设置代理和SSL相关配置，然后build()出独立的客户端实例。
     * 这样每个需要代理的请求使用独立的客户端，不会修改共享的OkHttpClient，
     * 彻底消除并发请求间的SSL设置竞态条件。
     *
     * 修复的异常：
     * - CertPathValidatorException: Trust anchor for certification path not found
     *   原因：旧方案直接修改共享OkHttpClient的SSL字段，并发请求间restoreProxy()
     *   会恢复原始SSL设置，导致正在使用代理的请求SSL握手失败
     *
     * @param context       应用上下文
     * @param originalClient 原始OkHttpClient实例（不会被修改）
     * @return 配置好代理的OkHttpClient实例，失败返回null
     */
    private Object getOrCreateProxyClient(Context context, Object originalClient) {
        if (proxyClient != null) return proxyClient;

        try {
            String httpUrlHost = SettingHelper.getInstance().getSetting(SettingHelper.proxy_server_key) ?
                    SettingHelper.getInstance().getHttpProxy() : "127.0.0.1";
            int proxyPort = SettingHelper.getInstance().getProxyPort();

            // 优先使用ScriptHelper的SSL工厂（基于CA证书验证）
            if (socketFactory == null) {
                SSLSocketFactory scriptFactory = ScriptHelper.getSSLSocketFactory(context);
                if (scriptFactory != null) socketFactory = scriptFactory;
            }

            if (customTrustManager == null) {
                customTrustManager = createTrustAllManager();
            }
            if (customTrustManager == null || socketFactory == null) {
                Log.e(TAG, "ProxyHook: SSL初始化失败");
                return null;
            }

            Object builder = XposedHelpers.callMethod(originalClient, "newBuilder");

            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(httpUrlHost, proxyPort));
            XposedHelpers.callMethod(builder, "proxy", proxy);

            try {
                XposedHelpers.callMethod(builder, "sslSocketFactory", socketFactory, customTrustManager);
            } catch (Exception e) {
                try {
                    XposedHelpers.callMethod(builder, "sslSocketFactory", socketFactory);
                } catch (Exception e2) {
                    Log.e(TAG, "ProxyHook: sslSocketFactory设置失败 - " + e2.getMessage());
                }
            }

            HostnameVerifier trustAllVerifier = (hostname, session) -> true;
            XposedHelpers.callMethod(builder, "hostnameVerifier", trustAllVerifier);

            try {
                Class<?> certificatePinnerClass = XposedHelpers.findClass(
                        "okhttp3.CertificatePinner", context.getClassLoader());
                Object defaultPinner = XposedHelpers.getStaticObjectField(certificatePinnerClass, "DEFAULT");
                if (defaultPinner != null) {
                    XposedHelpers.callMethod(builder, "certificatePinner", defaultPinner);
                }
            } catch (Exception ignored) {}

            proxyClient = XposedHelpers.callMethod(builder, "build");
            return proxyClient;
        } catch (Exception e) {
            Log.e(TAG, "ProxyHook: 创建代理客户端失败 - " + e.getMessage());
            return null;
        }
    }

    /**
     * 回退方案：直接修改共享OkHttpClient的SSL字段
     *
     * 仅在newBuilder()方式失败时使用。此方式存在并发竞态风险，
     * 但不调用restoreProxy，避免SSL设置被并发恢复导致CertPathValidatorException。
     *
     * @param context 应用上下文
     * @param client  OkHttpClient实例（会被直接修改）
     */
    private void setProxyFallback(Context context, Object client) throws Exception {
        Field sslSocketFactoryField;
        try {
            sslSocketFactoryField = client.getClass().getDeclaredField(fieldSSLSocketFactory);
        } catch (NoSuchFieldException e) {
            sslSocketFactoryField = null;
            for (Field f : client.getClass().getDeclaredFields()) {
                if (SSLSocketFactory.class.isAssignableFrom(f.getType())) {
                    sslSocketFactoryField = f;
                    break;
                }
            }
            if (sslSocketFactoryField == null) {
                Log.e(TAG, "ProxyHook: 未找到SSL字段");
                return;
            }
        }
        sslSocketFactoryField.setAccessible(true);

        Field proxyField;
        try {
            proxyField = client.getClass().getDeclaredField(fieldProxy);
        } catch (NoSuchFieldException e) {
            proxyField = null;
            for (Field f : client.getClass().getDeclaredFields()) {
                if (java.net.Proxy.class.isAssignableFrom(f.getType())) {
                    proxyField = f;
                    break;
                }
            }
            if (proxyField == null) {
                Log.e(TAG, "ProxyHook: 未找到proxy字段");
                return;
            }
        }
        proxyField.setAccessible(true);

        if (customTrustManager == null) {
            customTrustManager = createTrustAllManager();
        }

        String httpUrlHost = SettingHelper.getInstance().getSetting(SettingHelper.proxy_server_key) ?
                SettingHelper.getInstance().getHttpProxy() : "127.0.0.1";
        int proxyPort = SettingHelper.getInstance().getProxyPort();
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(httpUrlHost, proxyPort));
        proxyField.set(client, proxy);

        if (socketFactory != null) {
            sslSocketFactoryField.set(client, socketFactory);
        }

        Field trustManagerField = findFieldByType(client, X509TrustManager.class, "x509TrustManager");
        if (trustManagerField != null && customTrustManager != null) {
            trustManagerField.setAccessible(true);
            trustManagerField.set(client, customTrustManager);
        }

        Field hostnameVerifierField = null;
        try {
            hostnameVerifierField = client.getClass().getDeclaredField("hostnameVerifier");
        } catch (NoSuchFieldException e) {
            for (Field f : client.getClass().getDeclaredFields()) {
                if (HostnameVerifier.class.isAssignableFrom(f.getType())) {
                    hostnameVerifierField = f;
                    break;
                }
            }
        }
        if (hostnameVerifierField != null) {
            hostnameVerifierField.setAccessible(true);
            hostnameVerifierField.set(client, (HostnameVerifier) (hostname, session) -> true);
        }

        Field certificatePinnerField = null;
        try {
            certificatePinnerField = client.getClass().getDeclaredField("certificatePinner");
        } catch (NoSuchFieldException e) {
            for (Field f : client.getClass().getDeclaredFields()) {
                if (f.getType().getName().contains("CertificatePinner")
                        || f.getType().getName().contains("certificatePinner")) {
                    certificatePinnerField = f;
                    break;
                }
            }
        }
        if (certificatePinnerField != null) {
            certificatePinnerField.setAccessible(true);
            try {
                Object defaultPinner = XposedHelpers.getStaticObjectField(certificatePinnerField.getType(), "DEFAULT");
                if (defaultPinner != null) {
                    certificatePinnerField.set(client, defaultPinner);
                }
            } catch (Exception ignored) {}
        }

        setCertificateChainCleaner(client);
    }

    /**
     * 替换OkHttpClient中的certificateChainCleaner字段（回退方案专用）
     *
     * certificateChainCleaner是OkHttp内部用于清理和验证证书链的组件，
     * 它内部持有X509TrustManager引用。当x509TrustManager被替换后，
     * 需要同步创建新的certificateChainCleaner，否则旧的仍会使用原始TrustManager验证。
     *
     * @param client OkHttpClient实例
     */
    private void setCertificateChainCleaner(Object client) {
        try {
            // 查找certificateChainCleaner字段
            // 该字段的类型是okhttp3.internal.tls.CertificateChainCleaner（可能被混淆）
            Field chainCleanerField = null;
            // 优先通过已知字段名查找
            for (String fieldName : new String[]{"certificateChainCleaner", "chainCleaner"}) {
                try {
                    chainCleanerField = client.getClass().getDeclaredField(fieldName);
                    break;
                } catch (NoSuchFieldException ignored) {
                }
            }
            // 如果通过字段名没找到，尝试通过类型特征查找
            // CertificateChainCleaner的特征：类名包含"CertificateChainCleaner"或"ChainCleaner"
            if (chainCleanerField == null) {
                for (Field f : client.getClass().getDeclaredFields()) {
                    String typeName = f.getType().getName();
                    // 匹配类名特征
                    if (typeName.contains("CertificateChainCleaner")
                            || typeName.contains("ChainCleaner")) {
                        chainCleanerField = f;
                        break;
                    }
                }
            }
            // 如果还是没找到，尝试通过okhttp3内部类特征查找
            // CertificateChainCleaner通常在okhttp3.internal.tls包下
            if (chainCleanerField == null) {
                for (Field f : client.getClass().getDeclaredFields()) {
                    // 跳过已识别的字段类型
                    if (SSLSocketFactory.class.isAssignableFrom(f.getType())
                            || HostnameVerifier.class.isAssignableFrom(f.getType())
                            || java.net.Proxy.class.isAssignableFrom(f.getType())
                            || X509TrustManager.class.isAssignableFrom(f.getType())) {
                        continue;
                    }
                    String typeName = f.getType().getName();
                    if (typeName.contains("okhttp3") && typeName.contains("tls")) {
                        chainCleanerField = f;
                        break;
                    }
                }
            }

            if (chainCleanerField == null) return;

            chainCleanerField.setAccessible(true);

            if (customTrustManager != null) {
                try {
                    Class<?> cleanerClass = chainCleanerField.getType();
                    Object newCleaner = XposedHelpers.callStaticMethod(cleanerClass, "get", customTrustManager);
                    if (newCleaner != null) {
                        chainCleanerField.set(client, newCleaner);
                    }
                } catch (Exception e) {
                    try {
                        chainCleanerField.set(client, null);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "ProxyHook: certificateChainCleaner替换失败 - " + e.getMessage());
        }
    }

    /**
     * 创建信任所有证书的X509TrustManager实例
     *
     * 实现方式：先获取系统默认的TrustManagerFactory，然后用自定义的TrustManager覆盖。
     * 同时使用SSLContext初始化，确保与OkHttp的sslSocketFactory配合使用。
     *
     * @return 信任所有证书的X509TrustManager实例
     */
    private X509TrustManager createTrustAllManager() {
        try {
            // 创建信任所有证书的X509TrustManager
            X509TrustManager trustAllManager = new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    // 信任所有客户端证书
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    // 信任所有服务器证书（包括代理服务器的自签名证书）
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            };

            // 使用自定义TrustManager初始化SSLContext，同步更新socketFactory
            // 这样sslSocketFactory和x509TrustManager使用同一套信任策略
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{trustAllManager}, new java.security.SecureRandom());
            socketFactory = sslContext.getSocketFactory();

            return trustAllManager;
        } catch (Exception e) {
            Log.e(TAG, "ProxyHook: 创建TrustAllManager失败 - " + e.getMessage());
            XposedBridge.log("ProxyHook: 创建TrustAllManager失败 - " + e.getMessage());
            return null;
        }
    }

    /**
     * 通过字段类型在对象中查找字段
     *
     * 优先通过已知字段名查找，找不到时遍历所有字段按类型匹配。
     * 适用于OkHttp字段名可能被混淆的场景。
     *
     * @param obj       要查找字段的对象
     * @param fieldType 字段类型
     * @param fieldName 优先尝试的字段名
     * @return 找到的Field对象，未找到返回null
     */
    private Field findFieldByType(Object obj, Class<?> fieldType, String fieldName) {
        // 优先通过字段名查找
        if (fieldName != null) {
            try {
                return obj.getClass().getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
            }
        }
        // 遍历所有字段按类型匹配
        for (Field f : obj.getClass().getDeclaredFields()) {
            if (fieldType.isAssignableFrom(f.getType())) {
                return f;
            }
        }
        return null;
    }
}

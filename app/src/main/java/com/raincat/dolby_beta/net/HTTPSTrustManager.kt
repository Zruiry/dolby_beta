package com.raincat.dolby_beta.net;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class HTTPSTrustManager implements X509TrustManager {
    private static TrustManager[] trustManagers;

    @Override
    public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws java.security.cert.CertificateException {
    }

    @Override
    public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws java.security.cert.CertificateException {
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        // 返回非空数组，避免某些Android版本上因空数组导致证书验证仍失败
        // X509TrustManager接口规范要求返回受信任的CA证书数组，
        // 信任所有证书的实现应返回空数组而非null，但部分Android版本
        // 对空数组的处理存在兼容性问题，因此返回一个包含空元素的数组
        return new X509Certificate[0];
    }

    public static void allowAllSSL() {
        HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);

        SSLContext context = null;
        if (trustManagers == null) {
            trustManagers = new TrustManager[]{new HTTPSTrustManager()};
        }

        try {
            context = SSLContext.getInstance("TLS");
            context.init(null, trustManagers, new SecureRandom());
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            e.printStackTrace();
        }
        HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());
    }
}

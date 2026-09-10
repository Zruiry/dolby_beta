/**
 * 类加载帮助 - 包含音源代理和美化设置所需的内部类
 * 保留：OKHttp3Response, OKHttp3Header, HttpResponse, HttpUrl, HttpParams, HttpInterceptor, BottomTabManager, TabIndexManager, FragmentPagerAdapter
 *
 * 从Legacy API迁移到Modern libxposed API
 * 旧版：XposedHelpers.findClassIfExists/callMethod/callStaticMethod
 * 新版：Java反射 ClassLoader.loadClass/Method.invoke/Field.get
 *
 */
package com.raincat.dolby_beta.helper

import android.content.Context
import android.net.Uri
import com.annimon.stream.Stream
import com.raincat.dolby_beta.utils.LogUtils
import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.dexbacked.DexBackedDexFile
import org.jf.dexlib2.iface.MultiDexContainer
import java.io.Closeable
import java.io.File
import java.io.Serializable
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

object ClassHelper {

    /** 混淆包名匹配正则：根包名为1-3个小写字母+数字（如 dl0、ek1、ab 等），不同版本会变化 */
    private val obfuscatePackagePattern = Pattern.compile("^[a-z][a-z0-9]{0,2}$")
    /** 主Tab模型所在的非混淆包名（BottomTabInfoVO 等），用于过滤特征匹配候选 */
    private const val MAIN_TAB_MODEL_PACKAGE = "com.netease.cloudmusic.module.main"
    /** DEX 缓存 schema 版本号：扫描逻辑变更时递增，使旧缓存自动失效 */
    private const val CACHE_SCHEMA_VERSION = 2

    /** 类加载器 */
    private var classLoader: ClassLoader? = null
    /** dex缓存 */
    private var classCacheList: MutableList<String>? = null
    /** dex缓存路径 */
    private var classCachePath: String? = null
    /** 网易云版本 */
    private var versionCode = 0

    @JvmStatic
    @Synchronized
    fun getCacheClassList(context: Context, version: Int, listener: OnCacheClassListener) {
        if (classLoader == null) {
            classLoader = context.classLoader
            versionCode = version
            val cacheFile = context.getExternalFilesDir(null)!!
            if (cacheFile.exists() || cacheFile.mkdirs())
                classCachePath = cacheFile.path
        }
        if (classCacheList == null) {
            classCacheList = if (SettingHelper.getInstance().isEnable(SettingHelper.dex_key))
                FileHelper.readFileFromSD(classCachePath + File.separator + "class-$version-v$CACHE_SCHEMA_VERSION").toMutableList()
            else
                mutableListOf()
            if (classCacheList!!.isEmpty()) {
                Thread(Runnable { getCacheClassByZip(context, version, listener) }, "ClassHelper-DexScan").start()
            } else {
                listener.onGet()
            }
        } else {
            listener.onGet()
        }
    }

    private fun getCacheClassByZip(context: Context, version: Int, listener: OnCacheClassListener) {
        try {
            // 优先使用 applicationInfo.sourceDir（原始 APK 路径）
            // 避免 Tinker 热修复场景下 packageResourcePath 返回补丁包路径（不含完整 DEX）
            val appInstallFile = File(context.applicationInfo.sourceDir)
            LogUtils.i("ClassHelper: 开始DEX扫描 - APK路径=${appInstallFile.path}")
            val zip = ZipFile(appInstallFile).entries()
            var dexCount = 0
            var classCount = 0
            while (zip.hasMoreElements()) {
                val dexInZip: ZipEntry = zip.nextElement()
                if (dexInZip.name.startsWith("classes") && dexInZip.name.endsWith(".dex")) {
                    dexCount++
                    @Suppress("UNCHECKED_CAST")
                    val dexEntry = DexFileFactory.loadDexEntry(appInstallFile, dexInZip.name, true, null)
                            as MultiDexContainer.DexEntry<DexBackedDexFile>
                    val dexFile = dexEntry.dexFile
                    for (classDef in dexFile.classes) {
                        var classType = classDef.type
                        // 扫描 com/netease/cloudmusic、okhttp3 开头的类
                        if (classType.contains("com/netease/cloudmusic") || classType.contains("okhttp3")) {
                            classType = classType.substring(1, classType.length - 1).replace("/", ".")
                            classCacheList!!.add(classType)
                            classCount++
                        } else {
                            // 扫描混淆包名下的类（根包名为1-3个小写字母+数字，如 dl0、ek1、ab 等）
                            // 混淆包名在不同版本会变化，通过正则匹配避免硬编码
                            val path = classType.substring(1, classType.length - 1)
                            val rootPackage = path.substringBefore("/", "")
                            if (obfuscatePackagePattern.matcher(rootPackage).matches()) {
                                classCacheList!!.add(path.replace("/", "."))
                                classCount++
                            }
                        }
                    }
                }
            }
            LogUtils.i("ClassHelper: DEX扫描完成 - dex文件数=$dexCount, 扫描类数=$classCount, 总缓存数=${classCacheList!!.size}")
        } catch (e: Exception) {
            LogUtils.e("ClassHelper: DEX扫描异常 - ${e.message}")
        } finally {
            FileHelper.writeFileFromSD(classCachePath + File.separator + "class-$version-v$CACHE_SCHEMA_VERSION", classCacheList!!)
            listener.onGet()
        }
    }

    interface OnCacheClassListener {
        fun onGet()
    }

    @JvmStatic
    fun getFilteredClasses(pattern: Pattern, comparator: Comparator<String>?): List<String> {
        val cache = classCacheList ?: return emptyList()
        val list = Stream.of(*cache.toTypedArray())
            .filter { s -> pattern.matcher(s).find() }
            .toList()
        if (comparator != null) {
            Collections.sort(list, comparator)
        }
        return list
    }

    /**
     * 通过类名加载类（替代XposedHelpers.findClassIfExists的包装方法）
     * 加载失败时回退到加载NeteaseMusicApplication类，确保Stream.map不返回null
     */
    private fun getClassByXposed(className: String): Class<*>? {
        var clazz = findClassIfExists(className, classLoader!!)
        if (clazz == null)
            clazz = findClassIfExists("com.netease.cloudmusic.NeteaseMusicApplication", classLoader!!)
        return clazz
    }

    /**
     * 安全加载类（替代XposedHelpers.findClassIfExists）
     */
    @JvmStatic
    fun findClassIfExists(className: String, classLoader: ClassLoader): Class<*>? {
        return try {
            classLoader.loadClass(className)
        } catch (e: ClassNotFoundException) {
            null
        }
    }

    /**
     * OkHttp3 Response封装 - EAPIHook旧版方式需要
     */
    class OKHttp3Response(private val okHttp3Response: Any) {

        companion object {
            private var clazz: Class<*>? = null

            fun getClazz(): Class<*>? {
                if (clazz == null) {
                    val pattern = Pattern.compile("^okhttp3\\.[a-zA-Z]{1,8}$")
                    val list = getFilteredClasses(pattern, Collections.reverseOrder())

                    try {
                        clazz = Stream.of(list)
                            .map { getClassByXposed(it) }
                            .filter { it != null }
                            .map { it!! }
                            .filter { c -> !Modifier.isAbstract(c.modifiers) }
                            .filter { c -> Modifier.isPublic(c.modifiers) }
                            .filter { c -> c.interfaces.size == 1 }
                            .filter { c -> c.interfaces[0] == Closeable::class.java }
                            .filter { c -> Stream.of(*c.declaredFields).anyMatch { m -> m.type == Int::class.javaPrimitiveType } }
                            .filter { c -> Stream.of(*c.declaredFields).anyMatch { m -> m.type == String::class.java } }
                            .filter { c -> Stream.of(*c.declaredFields).anyMatch { m -> m.type == Long::class.javaPrimitiveType } }
                            .findFirst()
                            .orElse(null)
                    } catch (e: Exception) {
                        LogUtils.e("ClassHelper: 找不到OKHttp3Response核心类，音源代理功能可能失效")
                    }
                }
                return clazz
            }
        }

        @Throws(IllegalAccessException::class, NullPointerException::class)
        fun getHeadersObject(): Any {
            val fields = getClazz()!!.declaredFields
            val dataField = Stream.of(*fields)
                .filter { f -> f.type == OKHttp3Header.getClazz() }
                .filter { f -> Stream.of(*f.type.declaredFields).anyMatch { pf -> pf.type == Array<String>::class.java } }
                .findFirst().get()

            dataField.isAccessible = true
            return dataField.get(okHttp3Response)!!
        }
    }

    /**
     * OkHttp3 Header封装 - EAPIHook旧版方式需要
     */
    class OKHttp3Header(private val okHttp3Header: Any) {

        companion object {
            private var clazz: Class<*>? = null

            fun getClazz(): Class<*>? {
                if (clazz == null) {
                    val pattern = Pattern.compile("^okhttp3\\.[a-zA-Z]{1,7}$")
                    val list = getFilteredClasses(pattern, Collections.reverseOrder())

                    try {
                        clazz = Stream.of(list)
                            .map { getClassByXposed(it) }
                            .filter { it != null }
                            .map { it!! }
                            .filter { c -> !Modifier.isAbstract(c.modifiers) }
                            .filter { c -> Modifier.isPublic(c.modifiers) }
                            .filter { c -> Stream.of(*c.declaredFields).anyMatch { m -> m.type == Array<String>::class.java } }
                            .findFirst()
                            .orElse(null)
                    } catch (e: Exception) {
                        LogUtils.e("ClassHelper: 找不到OKHttp3Header核心类，音源代理功能可能失效")
                    }
                }
                return clazz
            }
        }

        @Suppress("UNCHECKED_CAST")
        @Throws(IllegalAccessException::class, NullPointerException::class)
        fun getHeaders(): Array<String> {
            val fields = getClazz()!!.declaredFields
            val dataField = Stream.of(*fields)
                .filter { f -> f.type == Array<String>::class.java }
                .findFirst().get()

            dataField.isAccessible = true
            return dataField.get(okHttp3Header) as Array<String>
        }
    }

    /**
     * 获取请求返回 - EAPIHook旧版方式需要
     */
    class HttpResponse(private val httpResponse: Any) {

        companion object {
            private var clazz: Class<*>? = null
            private var getResultMethod: Method? = null

            fun getClazz(): Class<*>? {
                if (clazz == null) {
                    val pattern: Pattern = if (versionCode < 154)
                        Pattern.compile("^com\\.netease\\.cloudmusic\\.[a-z]\\.[a-z]\\.[a-z]\\.[a-z]$")
                    else
                        Pattern.compile("^com\\.netease\\.cloudmusic\\.network\\.[a-z]+\\.[a-z]+\\.[a-z]+$")
                    val list = getFilteredClasses(pattern, Collections.reverseOrder())

                    try {
                        clazz = Stream.of(list)
                            .map { getClassByXposed(it) }
                            .filter { it != null }
                            .map { it!! }
                            .filter { c -> !Modifier.isAbstract(c.modifiers) }
                            .filter { c -> Modifier.isPublic(c.modifiers) }
                            .filter { c -> c.superclass == Any::class.java }
                            .filter { c -> Stream.of(*c.declaredFields).anyMatch { m -> m.type == OKHttp3Response.getClazz() } }
                            .findFirst()
                            .orElse(null)
                    } catch (e: Exception) {
                        LogUtils.e("ClassHelper: 找不到HttpResponse核心类，音源代理功能可能失效")
                    }
                }
                return clazz
            }

            fun getResultMethod(): Method? {
                if (getResultMethod == null) {
                    try {
                        val methodList = getClazz()?.declaredMethods?.toList() ?: return null
                        getResultMethod = Stream.of(methodList)
                            .filter { m -> m.exceptionTypes.size == 2 }
                            .findFirst()
                            .orElse(null)
                    } catch (e: Exception) {
                        LogUtils.e("ClassHelper: 找不到getResultMethod，音源代理功能可能失效")
                    }
                }
                return getResultMethod
            }
        }

        @Throws(IllegalAccessException::class, NullPointerException::class)
        fun getResponseObject(): Any {
            val fields = getClazz()!!.declaredFields
            val dataField = Stream.of(*fields)
                .filter { f -> Stream.of(*f.type.interfaces).anyMatch { i -> i == Closeable::class.java } }
                .filter { f -> Stream.of(*f.type.declaredFields).anyMatch { pf -> pf.type.name.startsWith("okhttp3") } }
                .findFirst().get()

            dataField.isAccessible = true
            return dataField.get(httpResponse)!!
        }

        @Throws(IllegalAccessException::class, NullPointerException::class)
        fun getEapi(): Any {
            val fields = getClazz()!!.declaredFields
            val dataField = Stream.of(*fields)
                .filter { c -> Modifier.isAbstract(c.type.modifiers) }
                .filter { c -> c.type.superclass == Any::class.java }
                .filter { c -> Stream.of(*c.type.declaredFields).anyMatch { m -> m.type.name.startsWith("okhttp3") } }
                .findFirst().get()

            dataField.isAccessible = true
            return dataField.get(httpResponse)!!
        }
    }

    /**
     * 获取请求URL - EAPIHook旧版方式需要
     */
    object HttpUrl {
        private var clazz: Class<*>? = null

        fun getClazz(): Class<*>? {
            if (clazz == null) {
                val pattern: Pattern = if (versionCode < 154)
                    Pattern.compile("^com\\.netease\\.cloudmusic\\.[a-z]\\.[a-z]\\.[a-z]\\.[a-z]$")
                else
                    Pattern.compile("^com\\.netease\\.cloudmusic\\.network\\.[a-z]+\\.[a-z]+\\.[a-z]+$")
                val list = getFilteredClasses(pattern, Collections.reverseOrder())

                try {
                    clazz = Stream.of(list)
                        .map { getClassByXposed(it) }
                        .filter { it != null }
                        .map { it!! }
                        .filter { c -> !Modifier.isAbstract(c.modifiers) }
                        .filter { c -> Modifier.isPublic(c.modifiers) }
                        .filter { c -> c.superclass == Any::class.java }
                        .filter { c -> Stream.of(*c.declaredFields).anyMatch { m -> m.type.name.startsWith("okhttp3") } }
                        .filter { c -> Stream.of(*c.declaredFields).anyMatch { m -> m.type == Int::class.javaPrimitiveType } }
                        .findFirst()
                        .orElse(null)
                } catch (e: Exception) {
                    LogUtils.e("ClassHelper: 找不到HttpUrl核心类，音源代理功能可能失效")
                }
            }
            return clazz
        }

        fun getUri(eapi: Any): Uri {
            try {
                val fields = eapi.javaClass.declaredFields
                val dataField = Stream.of(*fields)
                    .filter { f -> Stream.of(*f.type.declaredFields).anyMatch { pf -> pf.type.name.startsWith("okhttp3") } }
                    .filter { f -> Stream.of(*f.type.declaredFields).anyMatch { pf -> pf.type == Int::class.javaPrimitiveType } }
                    .findFirst().orElse(null) ?: return Uri.EMPTY

                dataField.isAccessible = true
                val urlObj = dataField.get(eapi) ?: return Uri.EMPTY

                val urlFields = urlObj.javaClass.declaredFields
                val urlField = Stream.of(*urlFields)
                    .filter { f -> f.type == String::class.java }
                    .findFirst().orElse(null) ?: return Uri.EMPTY

                urlField.isAccessible = true
                val url = urlField.get(urlObj) as? String ?: return Uri.EMPTY
                return Uri.parse(url)
            } catch (e: Exception) {
                return Uri.EMPTY
            }
        }
    }

    /**
     * 获取请求参数 - EAPIHook旧版方式需要
     */
    object HttpParams {
        fun getParams(eapi: Any): LinkedHashMap<String, String> {
            val paramsMap = LinkedHashMap<String, String>()
            try {
                val fields = eapi.javaClass.declaredFields
                val dataField = Stream.of(*fields)
                    .filter { f -> f.type == LinkedHashMap::class.java }
                    .findFirst().orElse(null) ?: return paramsMap

                dataField.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val rawMap = dataField.get(eapi) as? LinkedHashMap<String, Any> ?: return paramsMap
                for (key in rawMap.keys) {
                    val value = rawMap[key]
                    paramsMap[key] = value?.toString() ?: ""
                }
            } catch (e: Exception) {
                // 提取参数失败
            }
            return paramsMap
        }
    }

    /**
     * CDN拦截器方法获取
     */
    object HttpInterceptor {
        fun getMethodList(): List<Method>? {
            try {
                val pattern = Pattern.compile("^com\\.netease\\.cloudmusic\\.network\\.[a-z]+\\.[a-z]+\$")
                val list = getFilteredClasses(pattern, null)

                val interceptorClass = Stream.of(list)
                    .map { getClassByXposed(it) }
                    .filter { it != null }
                    .map { it!! }
                    .filter { c -> !Modifier.isAbstract(c.modifiers) }
                    .filter { c -> Stream.of(*c.declaredMethods).anyMatch { m -> m.name == "intercept" } }
                    .filter { c -> Stream.of(*c.declaredMethods).anyMatch { m -> m.name == "a" || m.name == "b" } }
                    .findFirst()
                    .orElse(null) ?: return null

                val methods = mutableListOf<Method>()
                for (m in interceptorClass.declaredMethods) {
                    if (m.name == "a" || m.name == "b") {
                        val paramTypes = m.parameterTypes
                        if (paramTypes.size == 3) {
                            methods.add(m)
                        }
                    }
                }
                return methods
            } catch (e: Exception) {
                LogUtils.e("ClassHelper: HttpInterceptor方法查找失败 - ${e.message}")
                return null
            }
        }
    }

    /**
     * 判断类的方法签名是否引用主Tab模型包（非混淆包）
     *
     * 用于在特征匹配结果中优先挑出真正的底部Tab管理类：网易云的主Tab相关模型
     * （BottomTabInfoVO / MainPageTabApiResult 等）固定在 com.netease.cloudmusic.module.main 下
     */
    private fun referencesMainTabModelPackage(clazz: Class<*>): Boolean =
        clazz.declaredMethods.any { m ->
            m.returnType.name.startsWith(MAIN_TAB_MODEL_PACKAGE)
                    || m.parameterTypes.any { p -> p.name.startsWith(MAIN_TAB_MODEL_PACKAGE) }
        }

    /**
     * 底部Tab管理类查找（完全通过特征匹配，不依赖混淆类名和方法名）
     *
     * 通用特征：
     * 1. 实现 java.io.Serializable 接口
     * 2. 包含返回 List 的无参方法（获取Tab列表）
     * 3. 包含 (List): void 方法（保存Tab列表）
     *
     * 经反编译源码验证，9.5.25/9.5.30/9.5.35 中 BottomTabManager 方法名一致：
     * - 9.5.25~9.5.30: dl0.g，h() 返回 List，u(List) 返回 void
     * - 9.5.35: dl0.h，h() 返回 List，u(List) 返回 void
     * 但为兼容未来版本，特征匹配不依赖方法名
     *
     * 使用正则匹配混淆包名下的类（根包名1-3个小写字母+数字），通过 DEX 缓存进行特征筛选
     */
    object BottomTabManager {
        private var clazz: Class<*>? = null

        @JvmStatic
        fun getClazz(): Class<*>? {
            if (clazz != null) return clazz
            try {
                // 正则匹配混淆包名下的类（不硬编码包名，兼容不同版本）
                val pattern = Pattern.compile("^[a-z][a-z0-9]{0,2}\\.[a-z]{1,3}$")
                val list = getFilteredClasses(pattern, null)
                LogUtils.i("ClassHelper: BottomTabManager 特征匹配扫描混淆包类，共 ${list.size} 个")
                // 调试：打印前20个类名，确认 dl0.g/dl0.h 是否在缓存中
                list.take(20).forEach { LogUtils.i("ClassHelper: BottomTabManager 候选类 - $it") }
                val candidates = Stream.of(list)
                    .map { getClassByXposed(it) }
                    .filter { it != null }
                    .map { it!! }
                    // 特征1：直接实现 java.io.Serializable 接口（非继承）
                    // BottomTabManager 直接 implements Serializable，而误匹配类如 xk.g 通过继承 MemberPushOption 间接实现
                    .filter { c -> c.interfaces.any { it == Serializable::class.java } }
                    // 特征2：包含返回 List 的无参方法（获取Tab列表）
                    .filter { c -> Stream.of(*c.declaredMethods).anyMatch { m ->
                        m.parameterTypes.isEmpty() &&
                        List::class.java.isAssignableFrom(m.returnType)
                    } }
                    // 特征3：包含 (List): void 方法（保存Tab列表）
                    .filter { c -> Stream.of(*c.declaredMethods).anyMatch { m ->
                        m.returnType == Void::class.javaPrimitiveType &&
                        m.parameterTypes.size == 1 && List::class.java.isAssignableFrom(m.parameterTypes[0])
                    } }
                    .toList()
                // 特征4：方法签名引用主Tab模型所在的非混淆包，用于排除同样具备 List 读写特征的其它数据类
                // （如 9.5.90 的歌单/歌曲聚合类）。命中即用；历史版本若无此类方法则回退到前3个特征的结果，
                // 保证特征4 不成立时不影响旧版本匹配
                clazz = candidates.firstOrNull { c -> referencesMainTabModelPackage(c) }
                    ?: candidates.firstOrNull()
                if (clazz != null) {
                    LogUtils.i("ClassHelper: 特征匹配找到BottomTabManager: ${clazz!!.name}")
                } else {
                    LogUtils.e("ClassHelper: 特征匹配未找到BottomTabManager")
                }
            } catch (e: Exception) {
                LogUtils.e("ClassHelper: BottomTabManager查找失败 - ${e.message}")
            }
            return clazz
        }
    }

    /**
     * Tab索引管理类查找（完全通过特征匹配，不依赖混淆类名和方法名）
     *
     * 通用特征：
     * 1. 继承 androidx.lifecycle.ViewModel
     * 2. 包含 (String): int 方法（tabCode转position）
     * 3. 包含 (int): String 方法（position转tabCode）
     *
     * 经反编译源码验证，9.5.25/9.5.30/9.5.35 中 TabIndexManager 仅有1对互逆方法：
     * - 9.5.25~9.5.30: s4(String):int 和 p4(int):String（dl0.m）
     * - 9.5.35: t4(String):int 和 q4(int):String（dl0.n）
     * 该类中 (String):int 和 (int):String 方法各只有1个，特征唯一
     *
     * 使用正则匹配混淆包名下的类（根包名1-3个小写字母+数字），通过 DEX 缓存进行特征筛选
     */
    object TabIndexManager {
        private var clazz: Class<*>? = null

        @JvmStatic
        fun getClazz(context: Context): Class<*>? {
            if (clazz != null) return clazz
            try {
                // 正则匹配混淆包名下的类（不硬编码包名，兼容不同版本）
                val pattern = Pattern.compile("^[a-z][a-z0-9]{0,2}\\.[a-z]{1,3}$")
                val list = getFilteredClasses(pattern, null)
                val viewModelClass = findClassIfExists("androidx.lifecycle.ViewModel", context.classLoader)
                    ?: return null
                clazz = Stream.of(list)
                    .map { getClassByXposed(it) }
                    .filter { it != null }
                    .map { it!! }
                    .filter { c -> c.superclass == viewModelClass }
                    // 特征2：包含 (String): int 方法（tabCode转position）
                    .filter { c -> Stream.of(*c.declaredMethods).anyMatch { m ->
                        m.returnType == Int::class.javaPrimitiveType &&
                        m.parameterTypes.size == 1 && m.parameterTypes[0] == String::class.java
                    } }
                    // 特征3：包含 (int): String 方法（position转tabCode）
                    .filter { c -> Stream.of(*c.declaredMethods).anyMatch { m ->
                        m.returnType == String::class.java &&
                        m.parameterTypes.size == 1 && m.parameterTypes[0] == Int::class.javaPrimitiveType
                    } }
                    .findFirst()
                    .orElse(null)
                if (clazz != null) {
                    LogUtils.i("ClassHelper: 特征匹配找到TabIndexManager: ${clazz!!.name}")
                } else {
                    LogUtils.e("ClassHelper: 特征匹配未找到TabIndexManager")
                }
            } catch (e: Exception) {
                LogUtils.e("ClassHelper: TabIndexManager查找失败 - ${e.message}")
            }
            return clazz
        }
    }

    /**
     * Fragment适配器类查找（完全通过特征匹配，不依赖混淆类名）
     *
     * 特征：
     * 1. 继承 androidx.viewpager2.adapter.FragmentStateAdapter
     * 2. 包含 createFragment(int): Fragment 方法
     */
    object FragmentPagerAdapter {
        private var clazz: Class<*>? = null

        @JvmStatic
        fun getClazz(context: Context): Class<*>? {
            if (clazz != null) return clazz
            try {
                // 特征匹配：在 com.netease.cloudmusic.adapter 包下查找
                val pattern = Pattern.compile("^com\\.netease\\.cloudmusic\\.adapter\\.[a-z]{1,3}$")
                val list = getFilteredClasses(pattern, null)
                val fragmentStateAdapterClass = findClassIfExists(
                    "androidx.viewpager2.adapter.FragmentStateAdapter", context.classLoader
                ) ?: return null
                val fragmentClass = findClassIfExists("androidx.fragment.app.Fragment", context.classLoader)
                    ?: return null
                clazz = Stream.of(list)
                    .map { getClassByXposed(it) }
                    .filter { it != null }
                    .map { it!! }
                    .filter { c -> fragmentStateAdapterClass.isAssignableFrom(c) }
                    .filter { c -> Stream.of(*c.declaredMethods).anyMatch { m ->
                        m.name == "createFragment" && m.returnType == fragmentClass &&
                        m.parameterTypes.size == 1 && m.parameterTypes[0] == Int::class.javaPrimitiveType
                    } }
                    .findFirst()
                    .orElse(null)
                if (clazz != null) {
                    LogUtils.i("ClassHelper: 特征匹配找到FragmentPagerAdapter: ${clazz!!.name}")
                } else {
                    LogUtils.e("ClassHelper: 特征匹配未找到FragmentPagerAdapter")
                }
            } catch (e: Exception) {
                LogUtils.e("ClassHelper: FragmentPagerAdapter查找失败 - ${e.message}")
            }
            return clazz
        }
    }
}

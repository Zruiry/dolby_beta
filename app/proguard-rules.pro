# ============================================================
# Modern libxposed API 102 保留规则
# ============================================================

# 保留模块入口类MainHook
# LSPosed通过META-INF/xposed/java_init.list找到入口类，
# 通过反射实例化（继承XposedModule，LSPosed自动处理构造函数和attachFramework）
-keep class com.raincat.dolby_beta.MainHook {
    public <init>(...);
}

# 保留XposedModule基类的所有方法（LSPosed通过反射调用生命周期回调）
-keep class io.github.libxposed.api.XposedModule {
    public *;
}

# 保留XposedInterfaceWrapper（attachFramework方法必须保留）
-keep class io.github.libxposed.api.XposedInterfaceWrapper {
    public *;
}

# 保留XposedInterface相关接口（Hooker、Chain、HookHandle等用于运行时hook注册）
-keep interface io.github.libxposed.api.XposedInterface$Hooker {
    *;
}
-keep interface io.github.libxposed.api.XposedInterface$Chain {
    *;
}
-keep interface io.github.libxposed.api.XposedInterface$HookHandle {
    *;
}

# 保留XposedModuleInterface（生命周期回调接口）
-keep interface io.github.libxposed.api.XposedModuleInterface {
    *;
}
-keep interface io.github.libxposed.api.XposedModuleInterface$ModuleLoadedParam {
    *;
}
-keep interface io.github.libxposed.api.XposedModuleInterface$PackageReadyParam {
    *;
}
-keep interface io.github.libxposed.api.XposedModuleInterface$PackageLoadedParam {
    *;
}

# 保留ScriptHelper（包含静态字段modulePath被MainHook引用）
-keep class com.raincat.dolby_beta.helper.ScriptHelper

# 跳过所有Json实体类
-keep public class **.*model*.** {*;}

-keep public class android.app.**
-keep class com.gyf.barlibrary.* {*;}
-dontwarn com.gyf.barlibrary.**

-dontwarn org.jetbrains.annotations.**
-dontwarn sun.misc.Unsafe
-dontwarn com.google.common.collect.MinMaxPriorityQueue
-dontwarn com.google.common.util.concurrent.FuturesGetChecked**
-dontwarn javax.lang.model.element.Modifier
-dontwarn afu.org.checkerframework.**
-dontwarn org.checkerframework.**
-dontwarn android.app.**
-dontwarn org.jf.dexlib2.dexbacked.**

#混淆变量和函数
-obfuscationdictionary proguard-class.txt
#混淆类名
-classobfuscationdictionary proguard-class.txt
# 指定class
-packageobfuscationdictionary proguard-class.txt
# 禁用repackageclasses：Xposed模块大量使用反射加载目标应用的类，
# repackageclasses会把模块类移到统一包下，可能导致类加载问题
# -repackageclasses com.raincat.dolby_beta
# 保留所有日志输出（debug/verbose/info/warn/error），便于问题排查
# 如需发布正式版可恢复以下配置以移除debug和verbose日志：
# -assumenosideeffects class android.util.Log {
#     public static boolean isLoggable(java.lang.String, int);
#     public static int d(...);
#     public static int v(...);
# }

# ============================================================
# Jetpack Compose / Material 3 保留规则（设置界面）
# ============================================================

# 保留 SettingsScreenKt（SettingHook 通过 SettingsScreenKt.showSettingsDialog 调用）
-keep class com.raincat.dolby_beta.ui.SettingsScreenKt {
    public *;
}

# 保留 ComposeView（运行时反射实例化，被 ComponentDialog.setContentView 使用）
-keep class androidx.compose.ui.platform.ComposeView { *; }

# 保留 ComponentDialog（运行时反射实例化）
-keep class androidx.activity.ComponentDialog { *; }

# 保留 Compose runtime 核心（避免 R8 移除 Composable 函数）
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.material3.** { *; }
-keep class androidx.compose.material.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.foundation.** { *; }

# 保留 Kotlin metadata（Compose 编译器依赖 Kotlin metadata 反射）
-keep class kotlin.Metadata { *; }
-keepattributes *Annotation*,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,Signature,InnerClasses,EnclosingMethod

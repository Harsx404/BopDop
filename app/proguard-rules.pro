# NewPipeExtractor (YouTube fallback) uses reflection + Rhino for signature deobfuscation.
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn org.mozilla.classfile.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn java.beans.**
-dontwarn org.jsoup.**
-dontwarn com.google.re2j.**
-dontwarn javax.annotation.**
-dontwarn org.slf4j.**

# BotGuard WebView bridge (PoTokens) is called from JavaScript.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Kuromoji loads its dictionary as resources next to its own classes; keep the names and packages.
-keep class com.atilika.kuromoji.** { *; }

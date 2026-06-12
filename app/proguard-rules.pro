# commons-compress 在运行期通过服务加载各归档实现，保留其类避免被裁剪。
-keep class org.apache.commons.compress.** { *; }
-dontwarn org.apache.commons.compress.**

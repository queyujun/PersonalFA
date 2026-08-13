# SQLCipher / Room
-keep class net.zetetic.database.** { *; }
-keep class net.sqlcipher.** { *; }

# 保留数据模型（后续接入序列化时用到）
-keepclassmembers class com.yingjing.pfa.**.model.** { *; }

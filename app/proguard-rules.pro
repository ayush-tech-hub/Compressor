# Add project specific ProGuard rules here.
-keep class com.compressx.app.** { *; }
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context,androidx.work.WorkerParameters);
}

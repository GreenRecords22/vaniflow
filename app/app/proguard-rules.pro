# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.vaniflow.app.**$$serializer { *; }
-keepclassmembers class com.vaniflow.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.vaniflow.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Room entities and DAOs
-keep class com.vaniflow.app.data.local.db.entity.** { *; }
-keep class com.vaniflow.app.data.local.db.dao.** { *; }
-keep class * extends androidx.room.RoomDatabase

# Keep Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends androidx.lifecycle.ViewModel

# Keep Native JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}

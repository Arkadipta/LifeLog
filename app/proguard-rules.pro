-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep class com.lifelog.app.domain.model.** { *; }
-keep class com.lifelog.app.data.db.entity.** { *; }
-dontwarn kotlinx.serialization.**
-keep class kotlinx.serialization.** { *; }

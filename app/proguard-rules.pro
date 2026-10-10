# ── Room ────────────────────────────────────────────────────────────────────────
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# ── On-disk data shapes ─────────────────────────────────────────────────────────
# Both packages are stored, not just held in memory: entities are SQLite columns and
# the domain models are the @Serializable classes whose JSON lands in fieldValuesJson /
# optionsJson / recurrenceRuleJson / chart config columns. They stay kept wholesale so
# nothing about a released build's shrinking can change what an old database decodes to.
-keep class com.lifelog.app.domain.model.** { *; }
-keep class com.lifelog.app.data.db.entity.** { *; }

# ── kotlinx.serialization ───────────────────────────────────────────────────────
# Deliberately no rules here. kotlinx-serialization-core ships its own consumer rules
# (META-INF/com.android.tools/proguard + .../r8) which AGP merges automatically: they
# keep @Serializable classes' Companion fields, their serializer() lookups, the
# $$serializer.descriptor field, and silence the ClassValueReferences warning. R8 also
# models generated serializers directly. A blanket `-keep class kotlinx.serialization.**`
# on top of that pinned 370 library classes (358 of them unobfuscated) that shrinking
# should have been free to remove or rename.

# ── Logging ─────────────────────────────────────────────────────────────────────
# Backstop for the BuildConfig.DEBUG gate in util/AppLog.kt: strips any debug/verbose
# logging that does not go through logD() — a direct call of ours, or a library's — out
# of the release DEX. Warnings and errors are kept on purpose; they report real failures
# and are worth having in a release logcat when a user reports a bug.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

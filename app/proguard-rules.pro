# Keep Room / Compose internals intact.
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# Entities are read by generated code only, but keep them for safety against reflection.
-keepclassmembers class com.timetrack.app.data.** { *; }

# Kotlin metadata
-keep class kotlin.Metadata { *; }

# Coroutines
-dontwarn kotlinx.coroutines.**

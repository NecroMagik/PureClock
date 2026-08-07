# ============================================================================
# ROOM DATABASE KEEP RULES
# ============================================================================
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep class * extends androidx.room.EntityDeletionOrUpdateAdapter {
    <init>(...);
}
-keep class * extends androidx.room.SharedSQLiteStatement {
    <init>(...);
}
-keep class *_Impl {
    <init>();
}
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}

# Сохраняем все DAO интерфейсы и их методы
-keep @androidx.room.Dao interface * {
    *;
}

# Сохраняем сгенерированные реализации DAO
-keep class * implements androidx.room.RoomDatabase {
    *;
}

-dontwarn androidx.room.paging.**

# ============================================================================
# DATA MODELS & SERIALIZATION KEEP RULES
# ============================================================================
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    !private <methods>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Сохраняем все пакеты дата-классов от переименования R8
-keep class com.necromagik.pureclock.data.** { *; }
-keepclassmembers class com.necromagik.pureclock.data.** { *; }

# Правильное сохранение Enum для Gson и Compose
-keepclassmembers enum com.necromagik.pureclock.data.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
package io.github.nottarun7.fitt.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.nottarun7.fitt.progress.PhotoEntity
import io.github.nottarun7.fitt.progress.PhotoDao

enum class TrackingMetric(val label: String) { REPS("Reps"), SECONDS("Seconds") }

@Entity(tableName = "routines")
data class RoutineEntity(@PrimaryKey val id: String, val name: String, val updatedAt: Long)

@Entity(
    tableName = "routine_exercises",
    foreignKeys = [ForeignKey(entity = RoutineEntity::class, parentColumns = ["id"], childColumns = ["routineId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("routineId")],
)
data class RoutineExerciseEntity(
    @PrimaryKey val id: String,
    val routineId: String,
    val catalogueId: String?,
    val name: String,
    val metric: String,
    val position: Int,
    val targetSets: Int,
    val targetValue: Int,
    val perSide: Boolean = false,
    val assistance: Boolean = false,
)

@Entity(tableName = "sessions", indices = [Index(value = ["activeSlot"], unique = true)])
data class SessionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val routineId: String?,
    val startedAt: Long,
    val completedAt: Long? = null,
    // SQLite permits many nulls in a unique index, but only one active slot = 1.
    val activeSlot: Int? = 1,
)

@Entity(
    tableName = "session_exercises",
    foreignKeys = [ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")],
)
data class SessionExerciseEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val catalogueId: String?,
    val name: String,
    val metric: String,
    val position: Int,
    val targetSets: Int,
    val targetValue: Int,
    val perSide: Boolean = false,
    val assistance: Boolean = false,
)

@Entity(
    tableName = "logged_sets",
    foreignKeys = [ForeignKey(entity = SessionExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("exerciseId")],
)
data class SetEntity(
    @PrimaryKey val id: String,
    val exerciseId: String,
    val position: Int,
    val value: Int,
    val weight: Double?,
    val unit: String,
    val assistance: Boolean,
)

@Entity(
    tableName = "set_drafts",
    foreignKeys = [ForeignKey(entity = SessionExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE)],
)
data class SetDraftEntity(
    @PrimaryKey val exerciseId: String,
    val value: String = "",
    val weight: String = "",
    val unit: String = "KG",
    val assistance: Boolean = false,
)

data class RoutineWithExercises(
    @Embedded val routine: RoutineEntity,
    @Relation(parentColumn = "id", entityColumn = "routineId") val exercises: List<RoutineExerciseEntity>,
) {
    val orderedExercises get() = exercises.sortedBy { it.position }
}

data class SessionExerciseWithSets(
    @Embedded val exercise: SessionExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "exerciseId") val sets: List<SetEntity>,
    @Relation(parentColumn = "id", entityColumn = "exerciseId") val drafts: List<SetDraftEntity>,
) {
    val orderedSets get() = sets.sortedBy { it.position }
    val draft get() = drafts.singleOrNull() ?: SetDraftEntity(exercise.id, assistance = exercise.assistance)
}

data class SessionWithExercises(
    @Embedded val session: SessionEntity,
    @Relation(entity = SessionExerciseEntity::class, parentColumn = "id", entityColumn = "sessionId")
    val exercises: List<SessionExerciseWithSets>,
) {
    val orderedExercises get() = exercises.sortedBy { it.exercise.position }
    val setCount get() = exercises.sumOf { it.sets.size }
}

data class HistorySummary(@Embedded val session: SessionEntity, val setCount: Int, val exerciseCount: Int)

@Dao
interface WorkoutDao {
    @Transaction @Query("SELECT * FROM sessions WHERE completedAt IS NOT NULL ORDER BY completedAt, id")
    fun observeCompletedSessions(): Flow<List<SessionWithExercises>>
    @Transaction @Query("SELECT * FROM routines ORDER BY id")
    suspend fun allRoutines(): List<RoutineWithExercises>
    @Transaction @Query("SELECT * FROM sessions ORDER BY id")
    suspend fun allSessions(): List<SessionWithExercises>
    @Query("DELETE FROM routines WHERE id IN (:ids)") suspend fun deleteRoutines(ids: List<String>)
    @Query("DELETE FROM sessions WHERE id IN (:ids)") suspend fun deleteSessions(ids: List<String>)
    @Insert suspend fun insertRoutines(routines: List<RoutineEntity>)
    @Insert suspend fun insertSets(sets: List<SetEntity>)
    @Insert suspend fun insertDrafts(drafts: List<SetDraftEntity>)
    @Transaction @Query("SELECT * FROM routines ORDER BY updatedAt DESC, id")
    fun observeRoutines(): Flow<List<RoutineWithExercises>>

    @Transaction @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun routine(id: String): RoutineWithExercises?

    @Upsert suspend fun saveRoutine(routine: RoutineEntity)
    @Insert suspend fun insertRoutineExercises(exercises: List<RoutineExerciseEntity>)
    @Query("DELETE FROM routine_exercises WHERE routineId = :id") suspend fun clearRoutineExercises(id: String)
    @Query("DELETE FROM routines WHERE id = :id") suspend fun deleteRoutine(id: String)

    @Transaction @Query("SELECT * FROM sessions WHERE activeSlot = 1 LIMIT 1")
    fun observeActive(): Flow<SessionWithExercises?>
    @Transaction @Query("SELECT * FROM sessions WHERE activeSlot = 1 LIMIT 1")
    suspend fun active(): SessionWithExercises?
    @Transaction @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun session(id: String): SessionWithExercises?
    @Transaction @Query("SELECT * FROM sessions WHERE id = :id")
    fun observeSession(id: String): Flow<SessionWithExercises?>

    @Query("""
        SELECT sessions.*, COUNT(DISTINCT logged_sets.id) AS setCount,
            COUNT(DISTINCT CASE WHEN logged_sets.id IS NOT NULL THEN session_exercises.id END) AS exerciseCount
        FROM sessions LEFT JOIN session_exercises ON sessions.id = session_exercises.sessionId
        LEFT JOIN logged_sets ON session_exercises.id = logged_sets.exerciseId
        WHERE completedAt IS NOT NULL GROUP BY sessions.id ORDER BY completedAt DESC, sessions.id
    """)
    fun observeHistory(): Flow<List<HistorySummary>>

    @Insert suspend fun insertSession(session: SessionEntity)
    @Insert suspend fun insertSessionExercises(exercises: List<SessionExerciseEntity>)
    @Query("UPDATE sessions SET completedAt = :completedAt, activeSlot = NULL WHERE id = :id AND activeSlot = 1")
    suspend fun finishSession(id: String, completedAt: Long)
    @Query("DELETE FROM sessions WHERE id = :id AND activeSlot = 1") suspend fun discardSession(id: String)
    @Query("SELECT * FROM session_exercises WHERE id = :id") suspend fun exercise(id: String): SessionExerciseEntity?
    @Query("SELECT * FROM logged_sets WHERE id = :id") suspend fun set(id: String): SetEntity?
    @Insert suspend fun insertSet(set: SetEntity)
    @Update suspend fun updateSet(set: SetEntity)
    @Delete suspend fun deleteSet(set: SetEntity)
    @Upsert suspend fun saveDraft(draft: SetDraftEntity)
    @Query("DELETE FROM set_drafts WHERE exerciseId IN (SELECT id FROM session_exercises WHERE sessionId = :id)")
    suspend fun clearDrafts(id: String)
    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM logged_sets WHERE exerciseId = :id")
    suspend fun nextSetPosition(id: String): Int
}

@Database(
    entities = [RoutineEntity::class, RoutineExerciseEntity::class, SessionEntity::class,
        SessionExerciseEntity::class, SetEntity::class, SetDraftEntity::class, FoodEntity::class,
        MealEntity::class, MealItemEntity::class, DietScheduleEntity::class, ScheduleItemEntity::class, NutritionSettingsEntity::class, PhotoEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class WorkoutDatabase : RoomDatabase() {
    abstract fun workouts(): WorkoutDao
    abstract fun nutrition(): NutritionDao
    abstract fun photos(): PhotoDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS progress_photos (id TEXT NOT NULL, date TEXT NOT NULL, caption TEXT NOT NULL, addedAt INTEGER NOT NULL, sha256 TEXT NOT NULL, byteCount INTEGER NOT NULL, width INTEGER NOT NULL, height INTEGER NOT NULL, galleryUri TEXT, PRIMARY KEY(id))")
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS foods (id TEXT NOT NULL, name TEXT NOT NULL, servingLabel TEXT NOT NULL, calories REAL NOT NULL, protein REAL NOT NULL, carbs REAL NOT NULL, fat REAL NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE TABLE IF NOT EXISTS meals (id TEXT NOT NULL, date TEXT NOT NULL, name TEXT NOT NULL, notes TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE TABLE IF NOT EXISTS diet_schedules (id TEXT NOT NULL, name TEXT NOT NULL, daysOfWeek INTEGER NOT NULL, timeMinute INTEGER NOT NULL, enabled INTEGER NOT NULL, notes TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))")
                for ((table, parent) in listOf("meal_items" to "meals", "schedule_items" to "diet_schedules")) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS $table (parentId TEXT NOT NULL, id TEXT NOT NULL, position INTEGER NOT NULL, foodId TEXT, name TEXT NOT NULL, servingLabel TEXT NOT NULL, quantity REAL NOT NULL, calories REAL NOT NULL, protein REAL NOT NULL, carbs REAL NOT NULL, fat REAL NOT NULL, PRIMARY KEY(id), FOREIGN KEY(parentId) REFERENCES $parent(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_${table}_parentId ON $table(parentId)")
                }
                db.execSQL("CREATE TABLE IF NOT EXISTS nutrition_settings (id INTEGER NOT NULL, reminderMinute INTEGER NOT NULL, PRIMARY KEY(id))")
            }
        }
        fun open(context: Context, name: String = "fitt.db"): WorkoutDatabase =
            Room.databaseBuilder(context.applicationContext, WorkoutDatabase::class.java, name).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }
}

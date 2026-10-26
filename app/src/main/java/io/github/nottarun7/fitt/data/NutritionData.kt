package io.github.nottarun7.fitt.data

import androidx.room.*
import io.github.nottarun7.fitt.interchange.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.util.UUID

@Entity(tableName = "foods")
data class FoodEntity(@PrimaryKey val id: String, val name: String, val servingLabel: String,
    val calories: Double, val protein: Double, val carbs: Double, val fat: Double, val updatedAt: Long)

data class PortionColumns(val id: String, val position: Int, val foodId: String?, val name: String,
    val servingLabel: String, val quantity: Double, val calories: Double, val protein: Double, val carbs: Double, val fat: Double)

fun FoodEntity.portable() = BackupFood(id, name, servingLabel, calories, protein, carbs, fat, updatedAt)
fun BackupFood.entity() = FoodEntity(id, name, servingLabel, calories, protein, carbs, fat, updatedAt)
fun PortionColumns.portable() = BackupPortion(id, position, foodId, name, servingLabel, quantity, calories, protein, carbs, fat)
fun BackupPortion.columns() = PortionColumns(id, position, foodId, name, servingLabel, quantity, calories, protein, carbs, fat)

@Entity(tableName = "meals")
data class MealEntity(@PrimaryKey val id: String, val date: String, val name: String, val notes: String, val updatedAt: Long)

@Entity(tableName = "meal_items", primaryKeys = ["id"],
    foreignKeys = [ForeignKey(entity = MealEntity::class, parentColumns = ["id"], childColumns = ["parentId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("parentId")])
data class MealItemEntity(val parentId: String, @Embedded val item: PortionColumns)

@Entity(tableName = "diet_schedules")
data class DietScheduleEntity(@PrimaryKey val id: String, val name: String, val daysOfWeek: Int,
    val timeMinute: Int, val enabled: Boolean, val notes: String, val updatedAt: Long)

@Entity(tableName = "schedule_items", primaryKeys = ["id"],
    foreignKeys = [ForeignKey(entity = DietScheduleEntity::class, parentColumns = ["id"], childColumns = ["parentId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("parentId")])
data class ScheduleItemEntity(val parentId: String, @Embedded val item: PortionColumns)

@Entity(tableName = "nutrition_settings")
data class NutritionSettingsEntity(@PrimaryKey val id: Int = 1, val reminderMinute: Int = 1200)

data class MealWithItems(@Embedded val meal: MealEntity,
    @Relation(parentColumn = "id", entityColumn = "parentId") val items: List<MealItemEntity>)
data class ScheduleWithItems(@Embedded val schedule: DietScheduleEntity,
    @Relation(parentColumn = "id", entityColumn = "parentId") val items: List<ScheduleItemEntity>)

fun MealWithItems.portable() = BackupMeal(meal.id, meal.date, meal.name, meal.notes, meal.updatedAt, items.map { it.item.portable() }.sortedBy { it.position })
fun ScheduleWithItems.portable() = BackupDietSchedule(schedule.id, schedule.name, schedule.daysOfWeek, schedule.timeMinute,
    schedule.enabled, schedule.notes, schedule.updatedAt, items.map { it.item.portable() }.sortedBy { it.position })

@Dao
interface NutritionDao {
    @Query("SELECT * FROM foods ORDER BY name COLLATE NOCASE, id") fun observeFoods(): Flow<List<FoodEntity>>
    @Transaction @Query("SELECT * FROM meals ORDER BY date DESC, updatedAt DESC, id") fun observeMeals(): Flow<List<MealWithItems>>
    @Transaction @Query("SELECT * FROM diet_schedules ORDER BY timeMinute, id") fun observeSchedules(): Flow<List<ScheduleWithItems>>
    @Query("SELECT * FROM nutrition_settings WHERE id = 1") fun observeSettings(): Flow<NutritionSettingsEntity?>
    @Query("SELECT * FROM foods ORDER BY id") suspend fun foods(): List<FoodEntity>
    @Transaction @Query("SELECT * FROM meals ORDER BY id") suspend fun meals(): List<MealWithItems>
    @Transaction @Query("SELECT * FROM diet_schedules ORDER BY id") suspend fun schedules(): List<ScheduleWithItems>
    @Query("SELECT * FROM nutrition_settings WHERE id = 1") suspend fun settings(): NutritionSettingsEntity?
    @Query("SELECT * FROM foods WHERE id = :id") suspend fun food(id: String): FoodEntity?
    @Transaction @Query("SELECT * FROM diet_schedules WHERE id = :id") suspend fun schedule(id: String): ScheduleWithItems?
    @Upsert suspend fun saveFood(food: FoodEntity)
    @Upsert suspend fun saveMeal(meal: MealEntity)
    @Upsert suspend fun saveSchedule(schedule: DietScheduleEntity)
    @Upsert suspend fun saveSettings(settings: NutritionSettingsEntity)
    @Insert suspend fun insertMealItems(items: List<MealItemEntity>)
    @Insert suspend fun insertScheduleItems(items: List<ScheduleItemEntity>)
    @Query("DELETE FROM meal_items WHERE parentId = :id") suspend fun clearMealItems(id: String)
    @Query("DELETE FROM schedule_items WHERE parentId = :id") suspend fun clearScheduleItems(id: String)
    @Query("DELETE FROM foods WHERE id IN (:ids)") suspend fun deleteFoods(ids: List<String>)
    @Query("DELETE FROM meals WHERE id IN (:ids)") suspend fun deleteMeals(ids: List<String>)
    @Query("DELETE FROM diet_schedules WHERE id IN (:ids)") suspend fun deleteSchedules(ids: List<String>)
}

data class NutritionOverview(val foods: List<BackupFood> = emptyList(), val meals: List<BackupMeal> = emptyList(),
    val schedules: List<BackupDietSchedule> = emptyList(), val reminderMinute: Int = 1200,
    val loading: Boolean = true, val error: String? = null)

class NutritionRepository(private val database: WorkoutDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao = database.nutrition()
    val overview = combine(dao.observeFoods(), dao.observeMeals(), dao.observeSchedules(), dao.observeSettings()) { foods, meals, schedules, settings ->
        NutritionOverview(foods.map { it.portable() }, meals.map { it.portable() }, schedules.map { it.portable() }, settings?.reminderMinute ?: 1200, loading = false)
    }
    suspend fun saveFood(food: BackupFood) {
        val saved = food.copy(name = food.name.trim(), servingLabel = food.servingLabel.trim(), updatedAt = clock())
        valid(BackupNutrition(foods = listOf(saved)))
        dao.saveFood(saved.entity())
    }
    suspend fun saveMeal(meal: BackupMeal) = database.withTransaction {
        val saved = meal.copy(name = meal.name.trim(), updatedAt = clock())
        valid(BackupNutrition(meals = listOf(saved)))
        writeMeal(saved)
    }
    suspend fun saveSchedule(schedule: BackupDietSchedule) = database.withTransaction {
        val saved = schedule.copy(name = schedule.name.trim(), updatedAt = clock())
        valid(BackupNutrition(schedules = listOf(saved)))
        writeSchedule(saved)
    }
    suspend fun logSchedule(id: String, date: LocalDate): String = database.withTransaction {
        val plan = dao.schedule(id)?.portable() ?: throw IllegalArgumentException("This schedule was removed. Reopen your schedules.")
        require(plan.enabled) { "Enable this schedule before logging it." }
        require(plan.items.isNotEmpty()) { "Add foods with nutrition values before logging this text plan as a meal." }
        val meal = BackupMeal(newNutritionId(), date.toString(), plan.name, plan.notes, clock(),
            plan.items.map { it.copy(id = newNutritionId()) })
        valid(BackupNutrition(meals = listOf(meal))); writeMeal(meal); meal.id
    }
    suspend fun deleteFood(id: String) = dao.deleteFoods(listOf(id))
    suspend fun deleteMeal(id: String) = dao.deleteMeals(listOf(id))
    suspend fun deleteSchedule(id: String) = dao.deleteSchedules(listOf(id))
    suspend fun reminderTime(minute: Int) {
        require(minute in 0..1439) { "Choose a valid reminder time." }
        dao.saveSettings(NutritionSettingsEntity(reminderMinute = minute))
    }
    private fun valid(data: BackupNutrition) {
        val problems = data.validateNutrition()
        require(problems.isEmpty()) { problems.take(3).joinToString("\n") { "${it.path}: ${it.message}" } }
    }
    private suspend fun writeMeal(meal: BackupMeal) {
        dao.saveMeal(MealEntity(meal.id, meal.date, meal.name, meal.notes, meal.updatedAt)); dao.clearMealItems(meal.id)
        dao.insertMealItems(meal.items.map { MealItemEntity(meal.id, it.columns()) })
    }
    private suspend fun writeSchedule(schedule: BackupDietSchedule) {
        dao.saveSchedule(DietScheduleEntity(schedule.id, schedule.name, schedule.daysOfWeek, schedule.timeMinute, schedule.enabled, schedule.notes, schedule.updatedAt))
        dao.clearScheduleItems(schedule.id); dao.insertScheduleItems(schedule.items.map { ScheduleItemEntity(schedule.id, it.columns()) })
    }
}

fun newNutritionId(): String = UUID.randomUUID().toString()
fun BackupFood.portion(position: Int = 0) = BackupPortion(newNutritionId(), position, id, name, servingLabel, 1.0, calories, protein, carbs, fat)

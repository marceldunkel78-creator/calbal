package de.fatburner

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class Food(
    val name: String,
    val kcal: Double,
    val carbs: Double,
    val fat: Double,
    val protein: Double,
)

data class MealEntry(
    val id: Long,
    val date: String,
    val meal: String,
    val food: String,
    val servings: Double,
    val kcal: Double,
    val carbs: Double,
    val fat: Double,
    val protein: Double,
)

data class BodyMeasurement(
    val id: Long,
    val date: String,
    val weight: Double?,
    val waist: Double?,
    val hip: Double?,
)

data class DailyBurn(val date: String, val kcal: Double)

class AppRepository(context: android.content.Context) {
    private val preferences = context.getSharedPreferences("fatburner", android.content.Context.MODE_PRIVATE)

    fun foods(): List<Food> = readArray("foods") { item ->
        Food(item.getString("name"), item.getDouble("kcal"), item.getDouble("carbs"), item.getDouble("fat"), item.getDouble("protein"))
    }

    fun meals(): List<MealEntry> = readArray("meals") { item ->
        MealEntry(
            item.getLong("id"), item.getString("date"), item.getString("meal"), item.getString("food"),
            item.getDouble("servings"), item.getDouble("kcal"), item.getDouble("carbs"),
            item.getDouble("fat"), item.getDouble("protein"),
        )
    }

    fun measurements(): List<BodyMeasurement> = readArray("measurements") { item ->
        BodyMeasurement(
            item.getLong("id"), item.getString("date"), item.optionalDouble("weight"),
            item.optionalDouble("waist"), item.optionalDouble("hip"),
        )
    }

    fun burns(): List<DailyBurn> = readArray("burns") { item ->
        DailyBurn(item.getString("date"), item.getDouble("kcal"))
    }

    fun reminderDays(): Int = preferences.getInt("reminderDays", 0)

    fun saveFoods(values: List<Food>) = writeArray("foods", values.map { value ->
        JSONObject().put("name", value.name).put("kcal", value.kcal).put("carbs", value.carbs)
            .put("fat", value.fat).put("protein", value.protein)
    })

    fun saveMeals(values: List<MealEntry>) = writeArray("meals", values.map { value ->
        JSONObject().put("id", value.id).put("date", value.date).put("meal", value.meal)
            .put("food", value.food).put("servings", value.servings).put("kcal", value.kcal)
            .put("carbs", value.carbs).put("fat", value.fat).put("protein", value.protein)
    })

    fun saveMeasurements(values: List<BodyMeasurement>) = writeArray("measurements", values.map { value ->
        JSONObject().put("id", value.id).put("date", value.date).putNullable("weight", value.weight)
            .putNullable("waist", value.waist).putNullable("hip", value.hip)
    })

    fun saveBurns(values: List<DailyBurn>) = writeArray("burns", values.map { value ->
        JSONObject().put("date", value.date).put("kcal", value.kcal)
    })

    fun saveReminderDays(days: Int) {
        preferences.edit().putInt("reminderDays", days).apply()
    }

    private fun <T> readArray(key: String, decode: (JSONObject) -> T): List<T> =
        JSONArray(preferences.getString(key, "[]")).let { array ->
            (0 until array.length()).map { decode(array.getJSONObject(it)) }
        }

    private fun writeArray(key: String, values: List<JSONObject>) {
        preferences.edit().putString(key, JSONArray(values).toString()).apply()
    }
}

private fun JSONObject.optionalDouble(key: String): Double? =
    if (isNull(key)) null else optDouble(key).takeIf { it.isFinite() }

private fun JSONObject.putNullable(key: String, value: Double?): JSONObject =
    put(key, value ?: JSONObject.NULL)

fun today(): String = LocalDate.now().toString()

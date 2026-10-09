package de.fatburner

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

private val Ink = Color(0xFF1C2933)
private val Muted = Color(0xFF71808B)
private val Green = Color(0xFF13865B)
private val Red = Color(0xFFD94C45)
private val CanvasBg = Color(0xFFF4F6F4)
private val Amber = Color(0xFFE99C42)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = AppRepository(this)
        setContent {
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    primary = Green,
                    secondary = Amber,
                    background = CanvasBg,
                    surface = Color.White,
                    onSurface = Ink,
                ),
            ) {
                FatburnerApp(repository, this)
            }
        }
    }
}

private enum class Screen(val title: String, val marker: String) {
    TODAY("Heute", "◷"), MEALS("Ernährung", "＋"), BODY("Maße", "↗"), STATS("Statistik", "▥"), SETTINGS("Mehr", "⚙"),
}

@Composable
private fun FatburnerApp(repository: AppRepository, activity: ComponentActivity) {
    var screen by remember { mutableStateOf(Screen.TODAY) }
    var foods by remember { mutableStateOf(repository.foods()) }
    var meals by remember { mutableStateOf(repository.meals()) }
    var measurements by remember { mutableStateOf(repository.measurements()) }
    var burns by remember { mutableStateOf(repository.burns()) }
    var reminderDays by remember { mutableStateOf(repository.reminderDays()) }
    var showMealDialog by remember { mutableStateOf(false) }
    var showBurnDialog by remember { mutableStateOf(false) }
    var showMeasurementDialog by remember { mutableStateOf(false) }
    var showFoodPicker by remember { mutableStateOf(false) }
    val todayMeals = meals.filter { it.date == today() }
    val consumed = todayMeals.sumOf { it.kcal }
    val burned = burns.firstOrNull { it.date == today() }?.kcal
    val balance = burned?.let { consumed - it }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Scaffold(
        containerColor = CanvasBg,
        bottomBar = {
            NavigationBar(containerColor = Color.White, modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                Screen.entries.forEach { item ->
                    NavigationBarItem(
                        selected = screen == item,
                        onClick = { screen = item },
                        icon = { Text(item.marker, fontSize = 19.sp) },
                        label = { Text(item.title, fontSize = 10.sp) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TopHeading(
                screen.title,
                if (screen == Screen.TODAY) "Deine Bilanz auf einen Blick" else null,
                if (screen == Screen.TODAY || screen == Screen.MEALS) ({ showMealDialog = true }) else null,
            )
            when (screen) {
                Screen.TODAY -> TodayScreen(
                    burned = burned,
                    consumed = consumed,
                    balance = balance,
                    meals = todayMeals,
                    allBalance = burns.takeIf { it.isNotEmpty() }?.sumOf { burn ->
                        meals.filter { it.date == burn.date }.sumOf { it.kcal } - burn.kcal
                    },
                    onEditBurn = { showBurnDialog = true },
                    onAddMeal = { showMealDialog = true },
                    onMeals = { screen = Screen.MEALS },
                )
                Screen.MEALS -> MealsScreen(todayMeals, foods, onAdd = { showMealDialog = true })
                Screen.BODY -> BodyScreen(measurements, onAdd = { showMeasurementDialog = true })
                Screen.STATS -> StatsScreen(meals, burns, measurements)
                Screen.SETTINGS -> SettingsScreen(
                    reminderDays = reminderDays,
                    onReminderChange = { days ->
                        reminderDays = days
                        repository.saveReminderDays(days)
                        val workManager = WorkManager.getInstance(activity)
                        if (days == 0) {
                            workManager.cancelUniqueWork("body_measurement_reminder")
                        } else {
                            if (Build.VERSION.SDK_INT >= 33) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            val request = PeriodicWorkRequestBuilder<ReminderWorker>(days.toLong(), TimeUnit.DAYS).build()
                            workManager.enqueueUniquePeriodicWork(
                                "body_measurement_reminder",
                                ExistingPeriodicWorkPolicy.UPDATE,
                                request,
                            )
                        }
                    },
                )
            }
        }
    }

    if (showMealDialog) {
        MealDialog(
            foods = foods,
            onDismiss = { showMealDialog = false },
            onSave = { food, meal, servings ->
                val existing = foods.firstOrNull { it.name.equals(food.name, ignoreCase = true) }
                val savedFood = existing ?: food
                if (existing == null) {
                    foods = foods + food
                    repository.saveFoods(foods)
                }
                val entry = MealEntry(
                    System.currentTimeMillis(), today(), meal, savedFood.name, servings,
                    savedFood.kcal * servings, savedFood.carbs * servings,
                    savedFood.fat * servings, savedFood.protein * servings,
                )
                meals = meals + entry
                repository.saveMeals(meals)
                showMealDialog = false
            },
            onSelectFood = { showFoodPicker = true },
        )
    }
    if (showFoodPicker) {
        FoodPickerDialog(foods, onDismiss = { showFoodPicker = false }) { food ->
            showFoodPicker = false
            showMealDialog = false
            meals = meals + MealEntry(
                System.currentTimeMillis(), today(), "Snack", food.name, 1.0,
                food.kcal, food.carbs, food.fat, food.protein,
            )
            repository.saveMeals(meals)
        }
    }
    if (showBurnDialog) {
        NumberDialog(
            title = "Tagesverbrauch erfassen",
            label = "Verbrauch in kcal",
            initial = burned.takeIf { it > 0 }?.toString().orEmpty(),
            onDismiss = { showBurnDialog = false },
        ) { value ->
            burns = burns.filterNot { it.date == today() } + DailyBurn(today(), value)
            repository.saveBurns(burns)
            showBurnDialog = false
        }
    }
    if (showMeasurementDialog) {
        MeasurementDialog(
            current = measurements.lastOrNull { it.date == today() },
            onDismiss = { showMeasurementDialog = false },
        ) { weight, waist, hip ->
            measurements = measurements.filterNot { it.date == today() } +
                BodyMeasurement(System.currentTimeMillis(), today(), weight, waist, hip)
            repository.saveMeasurements(measurements)
            showMeasurementDialog = false
        }
    }
}

@Composable
private fun TopHeading(title: String, subtitle: String?, onAdd: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
            if (subtitle != null) {
                Text(subtitle, fontSize = 13.sp, color = Muted)
                Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)), fontSize = 12.sp, color = Muted)
            }
        }
        if (onAdd != null) {
            Surface(
                onClick = onAdd,
                shape = CircleShape,
                color = Green,
                contentColor = Color.White,
                modifier = Modifier.size(58.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("+", fontSize = 36.sp, fontWeight = FontWeight.Light)
                }
            }
        }
    }
}

@Composable
private fun TodayScreen(
    burned: Double?,
    consumed: Double,
    balance: Double?,
    meals: List<MealEntry>,
    allBalance: Double?,
    onEditBurn: () -> Unit,
    onAddMeal: () -> Unit,
    onMeals: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("VERBRAUCH", burned, "kcal", Muted, Modifier.weight(1f))
                MetricCard(
                    "BILANZ",
                    balance,
                    "kcal",
                    when { balance == null -> Muted; balance <= 0 -> Green; else -> Red },
                    Modifier.weight(1f),
                )
                MetricCard("AUFNAHME", consumed, "kcal", Amber, Modifier.weight(1f))
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text("Dein Kalorienkonto", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Spacer(Modifier.height(8.dp))
                    if (allBalance == null) {
                        Text("—", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Muted)
                        Text("Erfasse den Tagesverbrauch, um das Fett-Äquivalent abzuschätzen.", fontSize = 12.sp, color = Muted)
                    } else {
                        val fatKg = abs(allBalance) / 7000.0
                        val direction = when {
                            allBalance < 0 -> "geschätzter Fettverlust"
                            allBalance > 0 -> "geschätzte Fettzunahme"
                            else -> "rechnerisch ausgeglichen"
                        }
                        val balanceColor = when {
                            allBalance < 0 -> Green
                            allBalance > 0 -> Red
                            else -> Muted
                        }
                        Text("${fatKg.format(2)} kg", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = balanceColor)
                        Text("$direction · grobe Schätzung auf Basis von 7.000 kcal/kg", fontSize = 12.sp, color = Muted)
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onEditBurn, modifier = Modifier.fillMaxWidth()) {
                        Text(if (burned != null) "Tagesverbrauch ändern" else "Tagesverbrauch eintragen")
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Mahlzeiten heute", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = onMeals) { Text("Alle anzeigen") }
            }
        }
        if (meals.isEmpty()) {
            item { EmptyCard("Noch keine Mahlzeiten erfasst", "Tippe auf +, um deine erste Mahlzeit einzutragen.") }
        } else {
            items(meals.takeLast(4).reversed(), key = { it.id }) { meal -> MealRow(meal) }
        }
        item {
            Button(onClick = onAddMeal, modifier = Modifier.fillMaxWidth()) { Text("Mahlzeit hinzufügen") }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: Double?, unit: String, color: Color, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Muted, maxLines = 1)
            Spacer(Modifier.height(8.dp))
            Text(value?.roundToInt()?.toString() ?: "—", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = color)
            Text(unit, fontSize = 10.sp, color = Muted)
        }
    }
}

@Composable
private fun MealsScreen(meals: List<MealEntry>, foods: List<Food>, onAdd: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 16.dp, 16.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Heute · ${meals.sumOf { it.kcal }.roundToInt()} kcal", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        if (meals.isEmpty()) item { EmptyCard("Dein Ernährungstagebuch ist leer", "Erfasse eine Mahlzeit über den Plus-Button.") }
        items(meals.filter { it.date == today() }.reversed(), key = { it.id }) { MealRow(it, showMacros = true) }
        item {
            Spacer(Modifier.height(4.dp))
            Text("Meine Lebensmittel", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Nährwerte pro Portion · lokal auf deinem Gerät gespeichert", fontSize = 12.sp, color = Muted)
        }
        if (foods.isEmpty()) item { EmptyCard("Noch keine Lebensmittel", "Beim Erfassen einer Mahlzeit wird das Lebensmittel gespeichert.") }
        items(foods, key = { it.name }) { food ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(food.name, fontWeight = FontWeight.SemiBold)
                    Text("${food.kcal.roundToInt()} kcal · KH ${food.carbs.format(1)} g · Fett ${food.fat.format(1)} g · Eiweiß ${food.protein.format(1)} g", fontSize = 12.sp, color = Muted)
                }
            }
        }
        item { Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("Mahlzeit hinzufügen") } }
    }
}

@Composable
private fun MealRow(entry: MealEntry, showMacros: Boolean = false) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entry.food, fontWeight = FontWeight.SemiBold)
                Text("${entry.meal} · ${entry.servings.format(1)} Portion", fontSize = 12.sp, color = Muted)
                if (showMacros) Text("KH ${entry.carbs.format(1)} g · Fett ${entry.fat.format(1)} g · Eiweiß ${entry.protein.format(1)} g", fontSize = 11.sp, color = Muted)
            }
            Text("${entry.kcal.roundToInt()} kcal", fontWeight = FontWeight.Bold, color = Ink)
        }
    }
}

@Composable
private fun BodyScreen(measurements: List<BodyMeasurement>, onAdd: () -> Unit) {
    val latest = measurements.maxByOrNull { it.date }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 16.dp, 16.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text("Letzte Messung", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(latest?.date?.formatDate() ?: "Noch keine Messung", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MeasurementValue("Gewicht", latest?.weight, "kg")
                        MeasurementValue("Taille", latest?.waist, "cm")
                        MeasurementValue("Hüfte", latest?.hip, "cm")
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("Messung eintragen") }
                }
            }
        }
        item { Text("Messverlauf", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        if (measurements.isEmpty()) item { EmptyCard("Dein Verlauf startet hier", "Regelmäßige Messungen machen Veränderungen sichtbar.") }
        items(measurements.sortedByDescending { it.date }, key = { it.id }) { measurement ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(measurement.date.formatDate(), fontWeight = FontWeight.Medium)
                    Text(
                        listOfNotNull(
                            measurement.weight?.let { "${it.format(1)} kg" },
                            measurement.waist?.let { "T ${it.format(1)} cm" },
                            measurement.hip?.let { "H ${it.format(1)} cm" },
                        ).joinToString(" · ").ifBlank { "—" },
                        color = Muted,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun MeasurementValue(label: String, value: Double?, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value?.format(1) ?: "—", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Ink)
        Text(unit, fontSize = 11.sp, color = Muted)
        Text(label, fontSize = 11.sp, color = Muted)
    }
}

private enum class Period(val label: String, val days: Long) {
    DAY("Tag", 1), WEEK("Woche", 7), MONTH("Monat", 30), YEAR("Jahr", 365),
}

private enum class ChartKind(val label: String) {
    INTAKE("Aufnahme"), EXPENDITURE("Verbrauch"), BALANCE("Bilanz"),
    WEIGHT("Gewicht"), WAIST("Taille"), HIP("Hüfte"),
}

@Composable
private fun StatsScreen(meals: List<MealEntry>, burns: List<DailyBurn>, measurements: List<BodyMeasurement>) {
    var period by remember { mutableStateOf(Period.WEEK) }
    var kind by remember { mutableStateOf(ChartKind.BALANCE) }
    val start = LocalDate.now().minusDays(period.days - 1)
    val series = when (kind) {
        ChartKind.INTAKE -> meals.filter { it.date >= start.toString() }
            .groupBy { it.date }.toSortedMap().values.map { entries -> entries.sumOf { it.kcal } }
        ChartKind.EXPENDITURE -> burns.filter { it.date >= start.toString() }.sortedBy { it.date }.map { it.kcal }
        ChartKind.BALANCE -> burns.filter { it.date >= start.toString() }.sortedBy { it.date }.map { burn ->
            burn.kcal - meals.filter { it.date == burn.date }.sumOf { it.kcal }
        }
        ChartKind.WEIGHT -> measurements.filter { it.weight != null && it.date >= start.toString() }.sortedBy { it.date }.mapNotNull { it.weight }
        ChartKind.WAIST -> measurements.filter { it.waist != null && it.date >= start.toString() }.sortedBy { it.date }.mapNotNull { it.waist }
        ChartKind.HIP -> measurements.filter { it.hip != null && it.date >= start.toString() }.sortedBy { it.date }.mapNotNull { it.hip }
    }
    val isCalories = kind in listOf(ChartKind.INTAKE, ChartKind.EXPENDITURE, ChartKind.BALANCE)
    val unit = if (isCalories) "kcal" else if (kind == ChartKind.WEIGHT) "kg" else "cm"
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 16.dp, 16.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Period.entries.forEach { item ->
                    FilterChip(item.label, period == item) { period = item }
                }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(ChartKind.entries) { item ->
                    FilterChip(item.label, kind == item) { kind = item }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(if (kind == ChartKind.BALANCE) "Tagesbilanz · Verbrauch minus Aufnahme" else "${kind.label} im Verlauf", fontWeight = FontWeight.Bold)
                    Text("${period.label} · $unit", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                    if (series.isEmpty()) {
                        Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
                            Text("Noch keine Daten für diesen Zeitraum", color = Muted)
                        }
                    } else {
                        LineChart(series, kind == ChartKind.BALANCE, Modifier.fillMaxWidth().height(190.dp))
                    }
                    if (isCalories && series.isNotEmpty()) {
                        val total = series.sum()
                        val summary = when (kind) {
                            ChartKind.INTAKE -> "Aufnahme gesamt: ${total.roundToInt()} kcal"
                            ChartKind.EXPENDITURE -> "Verbrauch gesamt: ${total.roundToInt()} kcal"
                            ChartKind.BALANCE -> "${if (total >= 0) "Defizit" else "Überschuss"}: ${abs(total).roundToInt()} kcal · ca. ${(abs(total) / 7000).format(2)} kg Fett-Äquivalent"
                            else -> ""
                        }
                        Text(summary, color = if (kind != ChartKind.BALANCE || total >= 0) Green else Red, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) Green else Color.White,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(label, color = if (selected) Color.White else Ink, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp))
    }
}

@Composable
private fun LineChart(values: List<Double>, calories: Boolean, modifier: Modifier = Modifier) {
    val lineColor = if (calories) Green else Amber
    Canvas(modifier) {
        val left = 12.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 12.dp.toPx()
        val bottom = size.height - 20.dp.toPx()
        val min = minOf(0.0, values.minOrNull() ?: 0.0)
        val max = maxOf(1.0, values.maxOrNull() ?: 1.0)
        val range = (max - min).coerceAtLeast(1.0)
        for (index in 0..3) {
            val y = top + (bottom - top) * index / 3
            drawLine(Color(0xFFE9EEEA), Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
        }
        if (calories && min < 0 && max > 0) {
            val zeroY = bottom - ((0.0 - min) / range).toFloat() * (bottom - top)
            drawLine(Amber, Offset(left, zeroY), Offset(right, zeroY), strokeWidth = 1.dp.toPx())
        }
        if (values.size == 1) {
            val y = bottom - ((values.first() - min) / range).toFloat() * (bottom - top)
            drawCircle(lineColor, radius = 5.dp.toPx(), center = Offset((left + right) / 2, y))
        } else {
            values.zipWithNext().forEachIndexed { index, pair ->
                val x1 = left + (right - left) * index / (values.size - 1)
                val x2 = left + (right - left) * (index + 1) / (values.size - 1)
                val y1 = bottom - ((pair.first - min) / range).toFloat() * (bottom - top)
                val y2 = bottom - ((pair.second - min) / range).toFloat() * (bottom - top)
                drawLine(lineColor, Offset(x1, y1), Offset(x2, y2), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(lineColor, radius = 3.dp.toPx(), center = Offset(x1, y1))
                if (index == values.lastIndex - 1) drawCircle(lineColor, radius = 3.dp.toPx(), center = Offset(x2, y2))
            }
        }
    }
}

@Composable
private fun SettingsScreen(reminderDays: Int, onReminderChange: (Int) -> Unit) {
    val intervals = listOf(0, 7, 14, 30)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 16.dp, 16.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text("Mess-Erinnerung", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Lass dich regelmäßig ans Messen erinnern.", fontSize = 12.sp, color = Muted)
                    Spacer(Modifier.height(14.dp))
                    intervals.forEach { days ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onReminderChange(days) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(if (days == 0) "Aus" else "Alle $days Tage", Modifier.weight(1f))
                            if (reminderDays == days) Text("✓", color = Green, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Daten & Integrationen", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Deine Einträge bleiben lokal auf diesem Gerät.", fontSize = 13.sp, color = Muted)
                    Divider()
                    Text("Garmin Connect", fontWeight = FontWeight.SemiBold)
                    Text("Der Verbrauch wird vorerst manuell erfasst. Für eine offizielle Garmin-Connect-Anbindung ist eine Freigabe über das Garmin-Entwicklerprogramm erforderlich.", fontSize = 12.sp, color = Muted)
                    Divider()
                    Text("Nährwerte", fontWeight = FontWeight.SemiBold)
                    Text("Beim ersten Eintrag trägst du die Werte vom Etikett selbst ein. Das Lebensmittel wird für spätere Mahlzeiten gespeichert.", fontSize = 12.sp, color = Muted)
                }
            }
        }
    }
}

@Composable
private fun MealDialog(
    foods: List<Food>,
    onDismiss: () -> Unit,
    onSave: (Food, String, Double) -> Unit,
    onSelectFood: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var meal by remember { mutableStateOf("Frühstück") }
    var servings by remember { mutableStateOf("1") }
    var kcal by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mahlzeit hinzufügen") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(430.dp)) {
                item { Text("Nährwerte pro Portion aus der Verpackung", fontSize = 12.sp, color = Muted) }
                if (foods.isNotEmpty()) item {
                    OutlinedButton(onClick = onSelectFood, modifier = Modifier.fillMaxWidth()) { Text("Gespeichertes Lebensmittel auswählen") }
                }
                item { InputField("Lebensmittel", name, { name = it }, KeyboardType.Text) }
                item { InputField("Mahlzeit (z. B. Frühstück)", meal, { meal = it }, KeyboardType.Text) }
                item { InputField("Anzahl Portionen", servings, { servings = it }, KeyboardType.Decimal) }
                item { InputField("Kalorien (kcal)", kcal, { kcal = it }, KeyboardType.Decimal) }
                item { InputField("Kohlenhydrate (g)", carbs, { carbs = it }, KeyboardType.Decimal) }
                item { InputField("Fett (g)", fat, { fat = it }, KeyboardType.Decimal) }
                item { InputField("Eiweiß (g)", protein, { protein = it }, KeyboardType.Decimal) }
                if (error != null) item { Text(error.orEmpty(), color = Red, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val portionCount = servings.decimal()
                val food = Food(name.trim(), kcal.decimal() ?: -1.0, carbs.decimal() ?: -1.0, fat.decimal() ?: -1.0, protein.decimal() ?: -1.0)
                if (food.name.isBlank() || portionCount == null || portionCount <= 0 ||
                    food.kcal < 0 || food.carbs < 0 || food.fat < 0 || food.protein < 0
                ) {
                    error = "Bitte Lebensmittel, Portionen und gültige Nährwerte eintragen."
                } else onSave(food, meal.ifBlank { "Mahlzeit" }, portionCount)
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun FoodPickerDialog(foods: List<Food>, onDismiss: () -> Unit, onSelect: (Food) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lebensmittel auswählen") },
        text = {
            LazyColumn(modifier = Modifier.height(320.dp)) {
                items(foods, key = { it.name }) { food ->
                    Column(Modifier.fillMaxWidth().clickable { onSelect(food) }.padding(vertical = 12.dp)) {
                        Text(food.name, fontWeight = FontWeight.SemiBold)
                        Text("${food.kcal.roundToInt()} kcal pro Portion", color = Muted, fontSize = 12.sp)
                    }
                    Divider()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
}

@Composable
private fun NumberDialog(title: String, label: String, initial: String, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InputField(label, value, { value = it }, KeyboardType.Decimal)
                if (error != null) Text(error.orEmpty(), color = Red, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = value.decimal()
                if (parsed == null || parsed < 0) error = "Bitte einen gültigen Wert ab 0 eingeben."
                else onSave(parsed)
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun MeasurementDialog(current: BodyMeasurement?, onDismiss: () -> Unit, onSave: (Double?, Double?, Double?) -> Unit) {
    var weight by remember { mutableStateOf(current?.weight?.toString().orEmpty()) }
    var waist by remember { mutableStateOf(current?.waist?.toString().orEmpty()) }
    var hip by remember { mutableStateOf(current?.hip?.toString().orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Körpermaße messen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Lass Felder leer, die du heute nicht messen möchtest.", fontSize = 12.sp, color = Muted)
                InputField("Gewicht (kg)", weight, { weight = it }, KeyboardType.Decimal)
                InputField("Taille (cm)", waist, { waist = it }, KeyboardType.Decimal)
                InputField("Hüfte (cm)", hip, { hip = it }, KeyboardType.Decimal)
                if (error != null) Text(error.orEmpty(), color = Red, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val rawValues = listOf(weight, waist, hip)
                val values = rawValues.map { raw -> raw.takeIf { it.isNotBlank() }?.decimal() }
                if (values.all { it == null } || values.indices.any { index ->
                        val parsed = values[index]
                        rawValues[index].isNotBlank() && (parsed == null || parsed <= 0.0)
                    }
                ) {
                    error = "Bitte mindestens ein gültiges Maß größer als 0 eintragen; ungültige Felder korrigieren oder leeren."
                } else onSave(values[0], values[1], values[2])
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun InputField(label: String, value: String, onChange: (String) -> Unit, type: KeyboardType) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = type),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun EmptyCard(title: String, subtitle: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 12.sp, color = Muted)
        }
    }
}

private fun String.decimal(): Double? = replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
private fun Double.format(digits: Int): String = String.format(Locale.GERMANY, "%.${digits}f", this)
private fun String.formatDate(): String = runCatching {
    LocalDate.parse(this).format(DateTimeFormatter.ofPattern("d. MMM yyyy", Locale.GERMAN))
}.getOrDefault(this)

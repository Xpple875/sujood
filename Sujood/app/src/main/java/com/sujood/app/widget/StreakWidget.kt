package com.sujood.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentHeight
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.sujood.app.MainActivity
import com.sujood.app.data.local.datastore.UserPreferences
import kotlinx.coroutines.flow.first

class StreakWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs     = UserPreferences(context)
        val settings  = prefs.userSettings.first()
        val streak    = settings.streakDays
        val completed = settings.completedPrayersToday.size

        provideContent { StreakWidgetContent(streak, completed) }
    }
}

@Composable
private fun StreakWidgetContent(streak: Int, completed: Int) {
    val total   = 5
    val bg      = ColorProvider(Color(0xFF0D1020))
    val blue    = ColorProvider(Color(0xFF3B82F6))
    val blueDim = ColorProvider(Color(0xFF1D4ED8))
    val white   = ColorProvider(Color.White)
    val muted   = ColorProvider(Color(0xFF475569))
    val empty   = ColorProvider(Color(0xFF1E293B))
    val gold    = ColorProvider(Color(0xFFFBBF24))
    val allDone = completed == total

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bg)
            .cornerRadius(20.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(18.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            // Label
            Text(
                "PRAYER STREAK",
                style = TextStyle(color = blue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(GlanceModifier.height(8.dp))
            // Big number
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$streak",
                    style = TextStyle(
                        color = if (allDone) gold else white,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(GlanceModifier.width(5.dp))
                Text(
                    "day streak",
                    style = TextStyle(color = muted, fontSize = 12.sp)
                )
            }
            Spacer(GlanceModifier.height(12.dp))
            // Prayer dots row
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                for (i in 0 until total) {
                    val done = i < completed
                    Box(
                        modifier = GlanceModifier
                            .size(width = 0.dp, height = 6.dp)
                            .defaultWeight()
                            .background(if (done) blueDim else empty)
                            .cornerRadius(3.dp)
                    ) {}
                    if (i < total - 1) Spacer(GlanceModifier.width(4.dp))
                }
            }
            Spacer(GlanceModifier.height(7.dp))
            // Status text
            Text(
                if (allDone) "All 5 prayers complete today ✓"
                else "$completed / $total prayers today",
                style = TextStyle(
                    color = if (allDone) gold else muted,
                    fontSize = 11.sp,
                    fontWeight = if (allDone) FontWeight.Bold else FontWeight.Normal
                )
            )
        }
    }
}

class StreakWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StreakWidget()
}

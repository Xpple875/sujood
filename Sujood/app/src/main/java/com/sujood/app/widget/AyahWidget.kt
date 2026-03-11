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
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.sujood.app.MainActivity
import java.util.Calendar

private val AYAHS = listOf(
    Pair("Verily, with hardship comes ease.", "Ash-Sharh 94:6"),
    Pair("Indeed, prayer prohibits immorality and wrongdoing.", "Al-Ankabut 29:45"),
    Pair("And seek help through patience and prayer.", "Al-Baqarah 2:45"),
    Pair("Allah does not burden a soul beyond that it can bear.", "Al-Baqarah 2:286"),
    Pair("Establish prayer for My remembrance.", "Ta-Ha 20:14"),
    Pair("Remember Me and I will remember you.", "Al-Baqarah 2:152"),
    Pair("Verily, in the remembrance of Allah do hearts find rest.", "Ar-Ra'd 13:28"),
    Pair("He is with you wherever you are.", "Al-Hadid 57:4"),
    Pair("So be patient. Indeed, the promise of Allah is truth.", "Ar-Rum 30:60"),
    Pair("Call upon Me; I will respond to you.", "Ghafir 40:60"),
    Pair("My mercy encompasses all things.", "Al-A'raf 7:156"),
    Pair("Allah is sufficient for us, and He is the best disposer of affairs.", "Al-Imran 3:173"),
    Pair("Do not despair of the mercy of Allah.", "Az-Zumar 39:53"),
    Pair("Whoever fears Allah, He will make a way out for him.", "At-Talaq 65:2")
)

class AyahWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val ayah = AYAHS[dayOfYear % AYAHS.size]
        provideContent { AyahWidgetContent(verse = ayah.first, reference = ayah.second) }
    }
}

@Composable
private fun AyahWidgetContent(verse: String, reference: String) {
    val bg      = ColorProvider(Color(0xFF0A0C1A))
    val purple  = ColorProvider(Color(0xFF8B5CF6))
    val purDim  = ColorProvider(Color(0xFF4C1D95).copy(alpha = 0.6f))
    val textCol = ColorProvider(Color(0xFFE2E8F0))
    val muted   = ColorProvider(Color(0xFF64748B))
    val white   = ColorProvider(Color.White)

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
            // Header row
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Quote badge
                Box(
                    modifier = GlanceModifier
                        .size(26.dp)
                        .background(purDim)
                        .cornerRadius(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "❝",
                        style = TextStyle(color = purple, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    )
                }
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    "AYAH OF THE DAY",
                    style = TextStyle(color = purple, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                )
            }
            Spacer(GlanceModifier.height(12.dp))
            // Verse text
            Text(
                verse,
                style = TextStyle(
                    color = textCol,
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic
                )
            )
            Spacer(GlanceModifier.defaultWeight())
            // Reference
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = GlanceModifier
                        .size(width = 3.dp, height = 14.dp)
                        .background(purple)
                        .cornerRadius(2.dp)
                ) {}
                Spacer(GlanceModifier.width(7.dp))
                Text(
                    reference,
                    style = TextStyle(color = muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

class AyahWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AyahWidget()
}

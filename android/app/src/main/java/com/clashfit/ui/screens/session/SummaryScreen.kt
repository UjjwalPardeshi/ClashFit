package com.clashfit.ui.screens.session

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clashfit.AppGraph
import com.clashfit.core.model.FatigueBand
import com.clashfit.data.RepEntity
import com.clashfit.data.SessionEntity
import com.clashfit.data.SetEntity
import com.clashfit.engine.core.AsymmetrySummary
import com.clashfit.engine.core.describeAsymmetry
import com.clashfit.ui.components.EmberButton
import com.clashfit.ui.components.EmptyState
import com.clashfit.ui.components.Kicker
import com.clashfit.ui.components.OutlineButton
import com.clashfit.ui.components.PanelBox
import com.clashfit.ui.components.RuleRow
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.components.StatTile
import com.clashfit.ui.components.color
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Fresh
import com.clashfit.ui.theme.Gassed
import com.clashfit.ui.theme.Heavy
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.Rule
import com.clashfit.ui.theme.Working
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** The session as evidence: the fatigue curve, the form trend, the best and worst rep. */
data class SummaryData(
    val session: SessionEntity,
    val sets: List<SetEntity>,
    val reps: List<RepEntity>,
) {
    val formMeanPct: Int get() = if (reps.isEmpty()) 0 else (reps.map { it.formScore }.average() * 100).toInt()
    val best: RepEntity? get() = reps.maxByOrNull { it.formScore }
    val worst: RepEntity? get() = reps.minByOrNull { it.formScore }
    val cleanCount: Int get() = reps.count { it.verdict == "CLEAN" }
    val okCount: Int get() = reps.count { it.verdict == "OK" }
    val shallowCount: Int get() = reps.count { it.verdict == "SHALLOW" }
    val peakBand: FatigueBand get() = runCatching { FatigueBand.valueOf(session.peakBand) }.getOrDefault(FatigueBand.FRESH)
}

class SummaryViewModel(private val graph: AppGraph, private val sessionId: Long) : ViewModel() {
    private val _data = MutableStateFlow<SummaryData?>(null)
    val data: StateFlow<SummaryData?> = _data.asStateFlow()
    private val _exported = MutableStateFlow<String?>(null)
    val exported: StateFlow<String?> = _exported.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val dao = graph.db.sessions()
            val s = dao.session(sessionId) ?: return@launch
            _data.value = SummaryData(s, dao.sets(sessionId), dao.reps(sessionId))
        }
    }

    /** CSV of every rep's raw telemetry, written to files/export with RFC 4180 escaping. */
    fun exportCsv(context: Context) {
        val d = _data.value ?: return
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                val dir = File(context.filesDir, "export").apply { mkdirs() }
                val f = File(dir, "clashfit-${d.session.exerciseId}-${d.session.id}.csv")
                f.bufferedWriter().use { w ->
                    w.appendLine("repIndex,tStartMs,tEndMs,formScore,depth,rom,tempo,alignment,reason,verdict,concentricVelocity,damage,combo,fatigue,band,validFrameRatio,depthCm,heightCm,holdSec")
                    for (r in d.reps) {
                        val fields = listOf(
                            r.repIndex, r.tStartMs, r.tEndMs, r.formScore, r.depth, r.rom, r.tempo, r.alignment,
                            escapeCsv(r.reason), escapeCsv(r.verdict),
                            r.concentricVelocity, r.damage, r.comboAtRep, r.fatigueValue, escapeCsv(r.fatigueBand), r.validFrameRatio,
                            r.depthCm ?: "", r.heightCm ?: "", r.holdSec ?: ""
                        )
                        w.appendLine(fields.joinToString(","))
                    }
                }
                f
            }
            _exported.value = file.absolutePath
            // Share the file via intent, allowing user to email or message it
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.csv_provider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "ClashFit Session · ${d.session.exerciseId}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share CSV export"))
        }
    }

    companion object {
        /**
         * RFC 4180 CSV escaping: wrap fields containing comma, newline, or quote in double quotes,
         * and escape internal quotes as double-double.
         */
        fun escapeCsv(value: String): String {
            return if (value.contains(",") || value.contains("\n") || value.contains("\"")) {
                "\"" + value.replace("\"", "\"\"") + "\""
            } else {
                value
            }
        }

        fun factory(graph: AppGraph, sessionId: Long) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SummaryViewModel(graph, sessionId) as T
        }
    }
}

@Composable
fun SummaryScreen(graph: AppGraph, sessionId: Long, onHome: () -> Unit, onAgain: (SessionEntity) -> Unit) {
    val vm: SummaryViewModel = viewModel(key = "summary-$sessionId", factory = SummaryViewModel.factory(graph, sessionId))
    val data by vm.data.collectAsStateWithLifecycle()
    val exported by vm.exported.collectAsStateWithLifecycle()
    val d = data
    if (d == null) { EmptyState("Summary", "Loading the set…"); return }

    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Kicker("Session · ${d.session.exerciseId.replace('_', ' ')} · ${d.session.mode.replace('_', ' ')}")
        Text(if (d.session.outcome == "BOSS_DOWN" || d.session.outcome == "GAME_WON") "BOSS DOWN" else "SET SAVED", style = MaterialTheme.typography.displaySmall, color = Ink)
        SectionGap(16)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("${d.session.totalReps}", "reps", Modifier.weight(1f))
            StatTile("${d.session.totalDamage}", "damage", Modifier.weight(1f))
            StatTile("${d.formMeanPct}%", "form", Modifier.weight(1f))
            StatTile(d.peakBand.label, "peak", Modifier.weight(1f), color = d.peakBand.color())
        }
        SectionGap()
        Kicker("Fatigue curve", color = InkFaint)
        Spacer(Modifier.height(10.dp))
        PanelBox(Modifier.fillMaxWidth(), padding = 12) { FatigueCurve(d.reps, Modifier.fillMaxWidth().height(200.dp)) }
        SectionGap()
        Kicker("Form per rep", color = InkFaint)
        Spacer(Modifier.height(10.dp))
        PanelBox(Modifier.fillMaxWidth(), padding = 12) { FormSparkline(d.reps, Modifier.fillMaxWidth().height(90.dp)) }
        Spacer(Modifier.height(8.dp))
        Text("CLEAN ${d.cleanCount} · OK ${d.okCount} · SHALLOW ${d.shallowCount}", style = MaterialTheme.typography.labelMedium, color = InkMuted)
        SectionGap()
        d.best?.let { RuleRow("Best rep · #${it.repIndex}", "${(it.formScore * 100).toInt()}%${it.depthCm?.let { c -> " · %.0f cm".format(c) } ?: ""}") }
        d.worst?.let { RuleRow("Worst rep · #${it.repIndex}", "${(it.formScore * 100).toInt()}% · ${it.reason}") }
        for (set in d.sets) {
            RuleRow("Set ${set.setIndex} · ${set.reps} reps", "${(set.formMean * 100).toInt()}% · ${set.fatigueBandEnd}")
            set.coachLine?.let { Text("\"$it\"", style = MaterialTheme.typography.bodyMedium, color = InkMuted, modifier = Modifier.padding(vertical = 8.dp)) }
            if (set.asymmetryPct != null) {
                val summary = AsymmetrySummary(
                    usable = 0,
                    enough = true,
                    meanLsi = 100f - set.asymmetryPct,
                    deficitPct = set.asymmetryPct.toFloat(),
                    weakerSide = set.weakerSide,
                    consistency = 1f,
                    consistent = true,
                )
                Text(describeAsymmetry(summary), style = MaterialTheme.typography.bodyMedium, color = InkMuted, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
        SectionGap()
        val ctx = androidx.compose.ui.platform.LocalContext.current
        EmberButton("Fight again", Modifier.fillMaxWidth()) { onAgain(d.session) }
        Spacer(Modifier.height(10.dp))
        OutlineButton(if (exported == null) "Export CSV" else "Exported", Modifier.fillMaxWidth()) { vm.exportCsv(ctx) }
        exported?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = InkFaint, modifier = Modifier.padding(top = 6.dp)) }
        Spacer(Modifier.height(10.dp))
        OutlineButton("Home", Modifier.fillMaxWidth(), onClick = onHome)
        SectionGap()
    }
}

/** Fatigue rising across the set, over the four band regions. The one image no other app has. */
@Composable
fun FatigueCurve(reps: List<RepEntity>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val bands = listOf(0f to Fresh, 0.15f to Working, 0.30f to Heavy, 0.50f to Gassed, 0.65f to Gassed)
        for (i in 0 until bands.size - 1) {
            val y0 = h * (bands[i].first / 0.65f); val y1 = h * (bands[i + 1].first / 0.65f)
            drawRect(bands[i].second.copy(alpha = 0.14f), topLeft = Offset(0f, y0), size = Size(w, y1 - y0))
        }
        for (t in listOf(0.15f, 0.30f, 0.50f)) {
            val y = h * (t / 0.65f)
            drawLine(Rule, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }
        if (reps.size < 2) return@Canvas
        val path = Path()
        reps.forEachIndexed { i, r ->
            val x = w * i / (reps.size - 1).toFloat()
            val y = h * (r.fatigueValue.coerceIn(0f, 0.65f) / 0.65f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Ember, style = Stroke(width = 5f))
        val last = reps.last()
        drawCircle(Ember, radius = 7f, center = Offset(w, h * (last.fatigueValue.coerceIn(0f, 0.65f) / 0.65f)))
    }
}

@Composable
fun FormSparkline(reps: List<RepEntity>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        drawLine(Rule, Offset(0f, h * 0.2f), Offset(w, h * 0.2f), strokeWidth = 1f)
        drawLine(Rule, Offset(0f, h * 0.45f), Offset(w, h * 0.45f), strokeWidth = 1f)
        if (reps.isEmpty()) return@Canvas
        val bw = w / reps.size
        reps.forEachIndexed { i, r ->
            val colour = when (r.verdict) { "CLEAN" -> Fresh; "OK" -> Working; else -> Heavy }
            val bh = h * r.formScore.coerceIn(0.04f, 1f)
            drawRect(colour, topLeft = Offset(i * bw + 1f, h - bh), size = Size((bw - 2f).coerceAtLeast(1f), bh))
        }
    }
}

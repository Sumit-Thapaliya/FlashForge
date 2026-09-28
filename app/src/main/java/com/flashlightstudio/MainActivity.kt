package com.flashlightstudio

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import com.flashlightstudio.data.PatternSegment
import com.flashlightstudio.data.SavedPattern
import com.flashlightstudio.viewmodel.StudioViewModel

private val Lime = Color(0xFFB9F36B)
private val Teal = Color(0xFF62D7C6)
private data class StudioPalette(val muted: Color, val panel: Color, val panel2: Color)
private val LocalStudioPalette = androidx.compose.runtime.staticCompositionLocalOf { StudioPalette(Color(0xFF91A0AB), Color(0xFF131B23), Color(0xFF1A252E)) }
private val Muted: Color
    @Composable get() = LocalStudioPalette.current.muted
private val Panel: Color
    @Composable get() = LocalStudioPalette.current.panel
private val Panel2: Color
    @Composable get() = LocalStudioPalette.current.panel2

class MainActivity : ComponentActivity() {
    private lateinit var studioViewModel: StudioViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        studioViewModel = androidx.lifecycle.ViewModelProvider(this).get(StudioViewModel::class.java)
        setContent { FlashlightStudioApp(studioViewModel) }
    }
    override fun onStop() { if (::studioViewModel.isInitialized) studioViewModel.stopEffects(); super.onStop() }
}

private enum class Page(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Rounded.Bolt), Studio("Studio", Icons.Rounded.Tune), Patterns("Patterns", Icons.Rounded.AutoAwesome), Tools("Tools", Icons.Rounded.GridView), Settings("Settings", Icons.Rounded.Settings)
}

@Composable
private fun FlashlightStudioApp(vm: StudioViewModel) {
    val context = LocalContext.current
    val dark by vm.darkTheme.collectAsStateWithLifecycle()
    val flashAvailable by vm.torch.available.collectAsStateWithLifecycle()
    val on by vm.torch.isOn.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val message by vm.status.collectAsStateWithLifecycle()
    val patterns by vm.savedPatterns.collectAsStateWithLifecycle()
    var page by remember { mutableStateOf(Page.Home) }
    var flashWarningAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showScreenLight by remember { mutableStateOf(false) }
    var strobeOnMs by remember { mutableFloatStateOf(200f) }
    var strobeOffMs by remember { mutableFloatStateOf(200f) }
    var beatSensitivity by remember { mutableFloatStateOf(.55f) }
    var morseText by remember { mutableStateOf("HELP") }
    var morseSpeed by remember { mutableFloatStateOf(180f) }
    var editorName by remember { mutableStateOf("My pattern") }
    var segments by remember { mutableStateOf(listOf(PatternSegment(true, 100), PatternSegment(false, 300), PatternSegment(true, 500), PatternSegment(false, 100), PatternSegment(true, 100))) }
    var showSaveDialog by remember { mutableStateOf(false) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) pendingAction?.invoke() else { pendingAction = null } }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) vm.startBeat(beatSensitivity) }
    fun runWithCamera(action: () -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) action()
        else { pendingAction = action; cameraPermission.launch(Manifest.permission.CAMERA) }
    }
    fun requestFlashWarning(action: () -> Unit) { flashWarningAction = action }
    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Lime, secondary = Teal, background = Color(0xFF090D12), surface = Color(0xFF131B23)) else lightColorScheme(primary = Color(0xFF446800), secondary = Color(0xFF00796B), background = Color(0xFFF2F5F0), surface = Color.White)) {
      androidx.compose.runtime.CompositionLocalProvider(LocalStudioPalette provides if (dark) StudioPalette(Color(0xFF91A0AB), Color(0xFF131B23), Color(0xFF1A252E)) else StudioPalette(Color(0xFF5E6B61), Color.White, Color(0xFFE8EEE5))) {
        val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 700
        Scaffold(containerColor = MaterialTheme.colorScheme.background,
            bottomBar = { if (!wide) StudioBottomBar(page) { page = it } }) { insets ->
            Row(Modifier.fillMaxSize().padding(insets)) {
                if (wide) NavigationRail(containerColor = Panel) { Page.entries.forEach { item -> NavigationRailItem(selected = page == item, onClick = { page = item }, icon = { Icon(item.icon, null) }, label = { Text(item.label) }) } }
                Box(Modifier.fillMaxSize().weight(1f)) {
                    when (page) {
                        Page.Home -> HomePage(on = on, available = flashAvailable, running = running, onToggle = { runWithCamera { vm.torch.toggle() } }, onNavigate = { page = it }, onStop = vm::stopEffects, onWarning = { mode -> requestFlashWarning { runWithCamera { if (mode == "Strobe") vm.startStrobe(strobeOnMs.toLong(), strobeOffMs.toLong()) else vm.sos() } } })
                        Page.Studio -> StudioPage(on = on, running = running, onWarning = { requestFlashWarning { runWithCamera { vm.startStrobe(strobeOnMs.toLong(), strobeOffMs.toLong()) } } }, startStrobe = { runWithCamera { vm.startStrobe(strobeOnMs.toLong(), strobeOffMs.toLong()) } }, stop = vm::stopEffects, onMs = strobeOnMs, onMsChange = { strobeOnMs = it }, offMs = strobeOffMs, offMsChange = { strobeOffMs = it }, startTimer = { seconds -> runWithCamera { vm.startTimer(seconds) } }, chooseMode = { mode ->
                            when (mode) { "SOS" -> requestFlashWarning { runWithCamera { vm.sos() } }; "Beacon" -> requestFlashWarning { runWithCamera { vm.beacon() } }; "Rapid" -> requestFlashWarning { runWithCamera { vm.rapid() } }; "Slow beacon" -> requestFlashWarning { runWithCamera { vm.slowBeacon() } } }
                        })
                        Page.Patterns -> PatternPage(vm, segments, { segments = it }, editorName, { editorName = it }, { vibration -> requestFlashWarning { runWithCamera { vm.startPattern(segments, vibration = vibration) } } }, { showSaveDialog = true }, onSelect = { saved -> segments = saved.segments; editorName = saved.name }, onPlaySaved = { saved -> requestFlashWarning { runWithCamera { vm.startPattern(saved.segments, repeat = true) } } }, onDelete = vm::deletePattern, onRandom = { segments = vm.randomPattern() }, running = running, stop = vm::stopEffects)
                        Page.Tools -> ToolsPage(onOpenScreenLight = { showScreenLight = true }, beatRunning = running, sensitivity = beatSensitivity, onSensitivity = { beatSensitivity = it }, onBeatStart = { runWithCamera { if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.startBeat(beatSensitivity) else micPermission.launch(Manifest.permission.RECORD_AUDIO) } }, stop = vm::stopEffects, status = message, playMorse = { text -> requestFlashWarning { runWithCamera { val code = vm.morse(text, morseSpeed.toLong()); vm.startPattern(code) } } }, morseText = morseText, onMorseText = { morseText = it }, morseCode = vm.morseString(morseText), morseSpeed = morseSpeed, onMorseSpeed = { morseSpeed = it })
                        Page.Settings -> SettingsPage(dark, vm::setTheme, vm.haptics.collectAsStateWithLifecycle().value, vm::setHaptics, flashAvailable)
                    }
                    if (message != null) Surface(Modifier.align(Alignment.BottomCenter).padding(16.dp), color = Color(0xFF30404A), shape = RoundedCornerShape(14.dp)) { Text(message!!, Modifier.padding(horizontal = 16.dp, vertical = 12.dp), color = Color.White, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        flashWarningAction?.let { action -> AlertDialog(onDismissRequest = { flashWarningAction = null }, title = { Text("Flashing-light safety") }, text = { Text("Flashing lights can trigger seizures or discomfort in people with photosensitivity. Stop immediately if you feel unwell. Never point a bright light at someone's eyes.") }, confirmButton = { TextButton(onClick = { flashWarningAction = null; action() }) { Text("Continue") } }, dismissButton = { TextButton(onClick = { flashWarningAction = null }) { Text("Cancel") } }) }
        if (showSaveDialog) AlertDialog(onDismissRequest = { showSaveDialog = false }, title = { Text("Save pattern") }, text = { OutlinedTextField(editorName, { editorName = it }, label = { Text("Pattern name") }) }, confirmButton = { TextButton(onClick = { vm.savePattern(editorName, segments); showSaveDialog = false }) { Text("Save") } }, dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") } })
        if (showScreenLight) ScreenLightDialog(onDismiss = { showScreenLight = false })
      }
    }
}

@Composable private fun StudioBottomBar(selected: Page, onClick: (Page) -> Unit) { NavigationBar(containerColor = Panel) { Page.entries.forEach { item -> NavigationBarItem(selected == item, { onClick(item) }, icon = { Icon(item.icon, null) }, label = { Text(item.label, fontSize = 10.sp) }) } } }

@Composable
private fun HomePage(on: Boolean, available: Boolean, running: Boolean, onToggle: () -> Unit, onNavigate: (Page) -> Unit, onStop: () -> Unit, onWarning: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 22.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column { Text("FLASHFORGE", fontWeight = FontWeight.Bold, letterSpacing = 2.sp); Text("Your light, precisely controlled", color = Muted, style = MaterialTheme.typography.bodySmall) }
            StatusPill(if (!available) "NO FLASH" else if (on) "TORCH ON" else if (running) "ACTIVE" else "READY", on || running)
        }
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            val scale by animateFloatAsState(if (on) 1.03f else 1f, label = "torchPulse")
            Box(Modifier.sizeIn(maxWidth = 330.dp).fillMaxWidth().aspectRatio(1f).scale(scale).clip(CircleShape).background(Brush.radialGradient(listOf(if (on) Color(0x5555DCC6) else Color(0x332E4850), Color(0x10151F27), Color.Transparent))), contentAlignment = Alignment.Center) {
                Surface(onClick = onToggle, modifier = Modifier.fillMaxSize(.70f), shape = CircleShape, color = if (on) Lime else Panel2, border = BorderStroke(1.dp, if (on) Color(0xFFD9FFA7) else Color(0xFF35434D)), shadowElevation = if (on) 22.dp else 5.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(if (on) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff, null, Modifier.size(55.dp), tint = if (on) Color(0xFF19240F) else Lime); Spacer(Modifier.height(10.dp)); Text(if (on) "TAP TO TURN OFF" else "TAP TO TURN ON", fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold, color = if (on) Color(0xFF19240F) else Color.White) }
                }
            }
            Text(if (!available) "No camera flash detected" else if (on) "Illuminating" else "Standing by", color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
        if (running) OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Stop, null); Text("STOP EFFECT") }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(Panel).padding(5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Normal", "Strobe", "SOS", "Morse").forEach { mode -> Surface(onClick = { when (mode) { "Strobe", "SOS" -> onWarning(mode); "Morse" -> onNavigate(Page.Tools); else -> onNavigate(Page.Home) } }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp), color = if (mode == "Normal") Panel2 else Color.Transparent) { Text(mode, Modifier.padding(vertical = 12.dp), textAlign = TextAlign.Center, fontSize = 12.sp, color = if (mode == "Normal") Lime else Muted) } }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("QUICK ACCESS", color = Muted, fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold); Text("OPEN STUDIO", color = Lime, fontSize = 10.sp, modifier = Modifier.clickable { onNavigate(Page.Studio) }) }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) { QuickCard("Screen light", "Color + brightness", Icons.Rounded.LightMode, Modifier.weight(1f)) { onNavigate(Page.Tools) }; QuickCard("Beat mode", "Audio reactive", Icons.Rounded.GraphicEq, Modifier.weight(1f)) { onNavigate(Page.Tools) }; QuickCard("Timer", "Auto-off", Icons.Rounded.Timer, Modifier.weight(1f)) { onNavigate(Page.Studio) } }
        Surface(onClick = { onWarning("SOS") }, modifier = Modifier.fillMaxWidth(), color = Color(0xFF381B21), shape = RoundedCornerShape(16.dp)) { Row(Modifier.padding(horizontal = 15.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Warning, null, tint = Color(0xFFFF7777)); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("Quick emergency · SOS", fontWeight = FontWeight.SemiBold); Text("Starts a repeating light signal after safety confirmation", color = Color(0xFFE6A8A8), fontSize = 10.sp) }; Icon(Icons.Rounded.ChevronRight, null, tint = Color(0xFFFF9999)) } }
        Surface(onClick = { onNavigate(Page.Patterns) }, modifier = Modifier.fillMaxWidth(), color = Panel, shape = RoundedCornerShape(18.dp)) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.AutoAwesome, null, tint = Lime); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Custom Patterns", fontWeight = FontWeight.SemiBold); Text("Build, save and play a light sequence", color = Muted, fontSize = 10.sp) }; Icon(Icons.Rounded.ChevronRight, null, tint = Muted) } }
        CapabilityRow(available)
    }
}

@Composable private fun QuickCard(title: String, subtitle: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) { Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(17.dp), color = Panel) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { Icon(icon, null, tint = Lime, modifier = Modifier.size(20.dp)); Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1); Text(subtitle, fontSize = 9.sp, color = Muted, maxLines = 1) } } }
@Composable private fun StatusPill(text: String, lit: Boolean) { Surface(shape = CircleShape, color = Panel2) { Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(7.dp).background(if (lit) Lime else Color(0xFF64717C), CircleShape)); Spacer(Modifier.width(7.dp)); Text(text, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .6.sp) } } }
@Composable private fun CapabilityRow(flash: Boolean) { val context = LocalContext.current; val battery = remember { (context.getSystemService(android.content.Context.BATTERY_SERVICE) as android.os.BatteryManager).getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) }; Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) { SmallStat("FLASH", if (flash) "Available" else "Not found"); SmallStat("BATTERY", if (battery >= 0) "$battery%" else "—"); SmallStat("ANDROID", android.os.Build.VERSION.RELEASE ?: "—") } }
@Composable private fun SmallStat(label: String, value: String) { Column { Text(label, color = Muted, fontSize = 8.sp, letterSpacing = 1.sp); Spacer(Modifier.height(3.dp)); Text(value, fontSize = 10.sp, fontWeight = FontWeight.Medium) } }

@Composable
private fun StudioPage(on: Boolean, running: Boolean, onWarning: () -> Unit, startStrobe: () -> Unit, stop: () -> Unit, onMs: Float, onMsChange: (Float) -> Unit, offMs: Float, offMsChange: (Float) -> Unit, startTimer: (Int) -> Unit, chooseMode: (String) -> Unit) {
    var timerChoice by remember { mutableIntStateOf(30) }
    var customSeconds by remember { mutableStateOf("120") }
    ScreenColumn { ScreenHeader("Studio", "Patterns and controls for real-world use")
        PanelCard("Strobe controls", "Photosensitivity warning: rapid flashes may trigger seizures or discomfort.") {
            LabelValue("Flash ON", "${onMs.toInt()} ms"); Slider(onMs, onMsChange, valueRange = 50f..900f)
            LabelValue("Flash OFF", "${offMs.toInt()} ms"); Slider(offMs, offMsChange, valueRange = 50f..900f)
            PrimaryButton(if (running) "Stop effect" else "Start strobe", if (running) stop else onWarning, modifier = Modifier.fillMaxWidth())
        }
        PanelCard("Emergency patterns", "Tap to start. Press Stop effect at any time.") {
            listOf("SOS", "Emergency beacon", "Slow beacon", "Rapid flash").forEach { title ->
                Row(Modifier.fillMaxWidth().clickable { chooseMode(when (title) { "SOS" -> "SOS"; "Emergency beacon" -> "Beacon"; "Slow beacon" -> "Slow beacon"; else -> "Rapid" }) }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Sensors, null, tint = Lime); Spacer(Modifier.width(12.dp)); Text(title, Modifier.weight(1f)); Icon(Icons.Rounded.ChevronRight, null, tint = Muted) }
            }
            if (running) OutlinedButton(onClick = stop, modifier = Modifier.fillMaxWidth()) { Text("Stop now") }
        }
        PanelCard("Flashlight timer", "Turns the torch off automatically after the selected duration.") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(10, 30, 60, 300).forEach { seconds -> FilterChip(timerChoice == seconds, { timerChoice = seconds }, label = { Text(if (seconds < 60) "${seconds}s" else "${seconds / 60}m") }) } }
            OutlinedTextField(customSeconds, { customSeconds = it.filter(Char::isDigit).take(5) }, label = { Text("Custom seconds") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            PrimaryButton("Start ${timerChoice}s timer", { startTimer(timerChoice) }, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { startTimer(customSeconds.toIntOrNull()?.coerceIn(1, 86400) ?: 120) }) { Text("Start custom timer") }
        }
    }
}

@Composable
private fun PatternPage(vm: StudioViewModel, segments: List<PatternSegment>, update: (List<PatternSegment>) -> Unit, name: String, setName: (String) -> Unit, preview: (Boolean) -> Unit, save: () -> Unit, onSelect: (SavedPattern) -> Unit, onPlaySaved: (SavedPattern) -> Unit, onDelete: (String) -> Unit, onRandom: () -> Unit, running: Boolean, stop: () -> Unit) {
    var syncVibration by remember { mutableStateOf(false) }
    ScreenColumn { ScreenHeader("Pattern Studio", "Make a custom flash sequence")
        PanelCard("Timeline editor", "Tap each block to switch its state. Drag to set duration.") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) { segments.forEachIndexed { i, segment -> Surface(onClick = { update(segments.toMutableList().also { it[i] = it[i].copy(on = !it[i].on) }) }, modifier = Modifier.weight((segment.durationMs.coerceAtLeast(100) / 100f).coerceAtMost(15f)), shape = RoundedCornerShape(9.dp), color = if (segment.on) Lime else Panel2) { Text(if (segment.on) "ON" else "OFF", Modifier.padding(vertical = 11.dp), textAlign = TextAlign.Center, color = if (segment.on) Color(0xFF1B260F) else Color.White, fontSize = 9.sp) } } }
            segments.forEachIndexed { i, segment ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1} · ${if (segment.on) "ON" else "OFF"}", Modifier.width(68.dp), fontSize = 10.sp)
                    Slider(segment.durationMs.toFloat(), { value -> update(segments.toMutableList().also { list -> list[i] = list[i].copy(durationMs = value.toLong()) }) }, valueRange = 50f..1500f, modifier = Modifier.weight(1f))
                    Text("${segment.durationMs}ms", Modifier.width(48.dp), fontSize = 9.sp)
                    IconButton(onClick = { if (segments.size > 1) update(segments.filterIndexed { index, _ -> index != i }) }) { Icon(Icons.Rounded.DeleteOutline, null, tint = Muted) }
                    IconButton(onClick = { update(segments.toMutableList().also { it.add(i + 1, segment) }) }) { Icon(Icons.Rounded.ContentCopy, null, tint = Muted) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { update(segments + PatternSegment(true, 250)) }) { Icon(Icons.Rounded.Add, null); Text("Add segment") }; OutlinedButton(onClick = onRandom) { Icon(Icons.Rounded.Shuffle, null); Text("Random") } }
            OutlinedTextField(name, setName, label = { Text("Pattern name") }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Vibrate on ON segments", Modifier.weight(1f), fontSize = 12.sp); Switch(syncVibration, { syncVibration = it }) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { PrimaryButton(if (running) "Stop preview" else "Preview", { if (running) stop() else preview(syncVibration) }, Modifier.weight(1f)); OutlinedButton(onClick = save, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.Save, null); Text("Save") } }
        }
        PanelCard("Saved patterns", "Stored privately on this device with DataStore.") {
            val saved by vm.savedPatterns.collectAsStateWithLifecycle()
            if (saved.isEmpty()) Text("No saved patterns yet", color = Muted, style = MaterialTheme.typography.bodySmall)
            saved.forEach { item -> Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f).clickable { onSelect(item) }) { Text(item.name, fontWeight = FontWeight.Medium); Text("${item.segments.size} segments", fontSize = 10.sp, color = Muted) }; IconButton(onClick = { onPlaySaved(item) }) { Icon(Icons.Rounded.PlayArrow, null, tint = Lime) }; IconButton(onClick = { onDelete(item.name) }) { Icon(Icons.Rounded.DeleteOutline, null, tint = Muted) } } }
        }
        PanelCard("Built-in presets", "Choose a preset to load its timeline") {
            val presets = listOf("🔦 Normal" to listOf(PatternSegment(true, 1000)), "🚨 Beacon" to listOf(PatternSegment(true, 700), PatternSegment(false, 1500)), "🎵 Party" to listOf(PatternSegment(true, 130), PatternSegment(false, 160), PatternSegment(true, 300), PatternSegment(false, 200)), "🌙 Reading" to listOf(PatternSegment(true, 2000)))
            presets.forEach { (title, data) -> Text(title, Modifier.fillMaxWidth().clickable { update(data); setName(title.substringAfter(' ')) }.padding(vertical = 8.dp)) }
        }
    }
}

@Composable
private fun ToolsPage(onOpenScreenLight: () -> Unit, beatRunning: Boolean, sensitivity: Float, onSensitivity: (Float) -> Unit, onBeatStart: () -> Unit, stop: () -> Unit, status: String?, playMorse: (String) -> Unit, morseText: String, onMorseText: (String) -> Unit, morseCode: String, morseSpeed: Float, onMorseSpeed: (Float) -> Unit) {
    ScreenColumn { ScreenHeader("Tools", "Purpose-built modes and device utilities")
        PanelCard("Morse code", "Enter letters or numbers; spaces separate words.") {
            OutlinedTextField(morseText, onMorseText, label = { Text("Message") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(morseCode.ifBlank { "Morse preview appears here" }, color = Teal, style = MaterialTheme.typography.bodyMedium)
            LabelValue("Speed", "${morseSpeed.toInt()} ms / dot"); Slider(morseSpeed, onMorseSpeed, valueRange = 80f..500f)
            PrimaryButton("Flash Morse message", { playMorse(morseText) }, Modifier.fillMaxWidth())
        }
        PanelCard("Screen light", "Full-screen color lamp with brightness presets.") { Text("White · warm · cool · emergency red · night light", color = Muted, style = MaterialTheme.typography.bodySmall); PrimaryButton("Open screen light", onOpenScreenLight, Modifier.fillMaxWidth()) }
        PanelCard("Beat mode", "Microphone is processed locally only while active; no audio is recorded or saved.") {
            Text("Start only when you want audio-reactive flashing. Keep the app in the foreground.", color = Muted, style = MaterialTheme.typography.bodySmall)
            LabelValue("Sensitivity", "${(sensitivity * 100).toInt()}%"); Slider(sensitivity, onSensitivity)
            PrimaryButton(if (beatRunning) "Stop beat mode" else "Enable microphone & start", if (beatRunning) stop else onBeatStart, Modifier.fillMaxWidth())
        }
        PanelCard("Vibration sync", "Pair tactile feedback with your pattern.") { Text("In Pattern Studio, enable ‘Vibrate on ON segments’ before previewing. Devices without a vibrator simply skip vibration.", color = Muted, style = MaterialTheme.typography.bodySmall) }
        PanelCard("Android limits", "This app cannot listen for every system notification. Quick Settings tiles and widgets require separate Android components and launcher/device support; they are not included in this build.") { Text("No background torch service is used. Effects stop when the app leaves the foreground.", color = Muted, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable private fun SettingsPage(dark: Boolean, setDark: (Boolean) -> Unit, haptics: Boolean, setHaptics: (Boolean) -> Unit, flashAvailable: Boolean) {
    ScreenColumn { ScreenHeader("Settings", "Preferences are saved on this device")
        PanelCard("Theme", "Choose app appearance") { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Dark" to true, "Light" to false).forEach { (label, value) -> FilterChip(selected = dark == value, onClick = { setDark(value) }, label = { Text(label) }) } } }
        PanelCard("Behavior", "") { SettingSwitch("Haptic feedback", haptics, setHaptics); SettingSwitch("Strobe warning", true) {}; Text("Flashlight effects stop when the app is backgrounded.", color = Muted, style = MaterialTheme.typography.bodySmall) }
        PanelCard("Device dashboard", "Live capability information") { SmallStat("FLASHLIGHT", if (flashAvailable) "Available" else "Not available"); SmallStat("CAMERA", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"); SmallStat("ANDROID", "${android.os.Build.VERSION.RELEASE} · API ${android.os.Build.VERSION.SDK_INT}"); val context = LocalContext.current; val manager = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator; SmallStat("VIBRATION", if (manager.hasVibrator()) "Available" else "Unavailable") }
        PanelCard("Privacy and permissions", "Camera permission is used for the hardware torch. Microphone permission is requested only when Beat Mode starts. Audio stays in memory for local beat detection and is not saved. System notification interception is not supported.") { Text("FlashForge · 1.1", color = Muted) }
    }
}
@Composable private fun SettingSwitch(title: String, checked: Boolean, onChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f)); Switch(checked, onChange) } }

@Composable private fun ScreenLightDialog(onDismiss: () -> Unit) {
    var chosen by remember { mutableIntStateOf(0) }; var brightness by remember { mutableFloatStateOf(.8f) }; var pulse by remember { mutableStateOf(false) }
    val colors = listOf(Color.White, Color(0xFFFFD9A3), Color(0xFFBDE9FF), Color(0xFFFF3030), Color(0xFFFFC98B))
    val color = colors[chosen]
    val alpha by animateFloatAsState(if (pulse) if (brightness < .12f) .06f else brightness * .72f + .22f else brightness, label = "screenPulse")
    LaunchedEffect(pulse) { if (pulse) { while (true) { delay(650); brightness = if (brightness < .5f) .9f else .25f } } }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(color.copy(alpha = alpha)).clickable { }.padding(22.dp)) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xE60D1218)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Screen light", Modifier.weight(1f), fontWeight = FontWeight.Bold); IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, null, tint = Color.White) } }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) { colors.forEachIndexed { i, c -> Box(Modifier.size(38.dp).clip(CircleShape).background(c).border(if (i == chosen) 3.dp else 1.dp, if (i == chosen) Lime else Muted, CircleShape).clickable { chosen = i }) } }
                Text("Brightness · ${(brightness * 100).toInt()}%", color = Color.White); Slider(brightness, { brightness = it }, valueRange = .03f..1f)
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Gentle pulse", Modifier.weight(1f), color = Color.White); Switch(pulse, { pulse = it }) }
                Text("Tap X to close", color = Color.LightGray, fontSize = 11.sp)
            }
        }
    }
}

@Composable private fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) { Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(top = 22.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(15.dp), content = content) }
@Composable private fun ScreenHeader(title: String, subtitle: String) { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall) } }
@Composable private fun PanelCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) { Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(21.dp)).background(Panel).padding(17.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) { Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium); if (subtitle.isNotBlank()) Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall); content() } }
@Composable private fun LabelValue(label: String, value: String) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(value, color = Lime, fontSize = 12.sp) } }
@Composable private fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) { Button(onClick, modifier = modifier, shape = RoundedCornerShape(14.dp)) { Text(text) } }

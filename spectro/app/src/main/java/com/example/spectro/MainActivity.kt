package com.example.spectro

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { SpectrogramScreen(this) }
            }
        }
    }
}

@Composable
fun SpectrogramScreen(activity: ComponentActivity) {
    val context = LocalContext.current
    val library = remember { SoundLibrary(context) }
    val model = remember { SpectrogramModel() }
    val sounds = remember { mutableStateListOf<LabeledSound>() }

    fun refresh() {
        sounds.clear(); sounds.addAll(library.loadAll())
        model.library = sounds.toList()
    }
    LaunchedEffect(Unit) { refresh() }

    // --- microphone permission ---
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.RECORD_AUDIO) }

    // --- start/stop audio with the activity lifecycle ---
    DisposableEffect(granted) {
        if (!granted) return@DisposableEffect onDispose {}
        val engine = AudioEngine(model::onFrame)
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> engine.start()
                Lifecycle.Event.ON_PAUSE -> engine.stop()
                else -> {}
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer); engine.stop() }
    }

    var userPaused by remember { mutableStateOf(false) }
    var floorDb by remember { mutableFloatStateOf(model.floorDb) }
    var pending by remember { mutableStateOf<FloatArray?>(null) }  // fingerprint awaiting a name
    var nameInput by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Live Spectrogram", style = MaterialTheme.typography.titleLarge)

        if (!granted) {
            Text("Microphone permission is required.")
            Button(onClick = { launcher.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Grant permission") }
        }

        // --- spectrogram ---
        Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black)) {
            Canvas(Modifier.fillMaxSize()) {
                @Suppress("UNUSED_VARIABLE") val t = model.tick   // subscribe to updates
                val img = model.bitmap.asImageBitmap()
                val wx = model.writeX
                val cols = SpectrogramModel.COLS
                val h = SpectrogramModel.BINS
                val scale = size.width / cols
                // oldest part: [wx, cols) on the left, newest part: [0, wx) on the right
                val firstW = cols - wx
                if (firstW > 0) drawImage(
                    img, IntOffset(wx, 0), IntSize(firstW, h),
                    IntOffset(0, 0), IntSize((firstW * scale).toInt() + 1, size.height.toInt())
                )
                if (wx > 0) drawImage(
                    img, IntOffset(0, 0), IntSize(wx, h),
                    IntOffset((firstW * scale).toInt(), 0), IntSize((wx * scale).toInt() + 1, size.height.toInt())
                )
            }
            Column(
                Modifier.fillMaxHeight().padding(4.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text("11 kHz", fontSize = 10.sp, color = Color.White)
                Text("5.5 kHz", fontSize = 10.sp, color = Color.White)
                Text("0", fontSize = 10.sp, color = Color.White)
            }
            model.detected?.let { d ->
                Surface(
                    Modifier.align(Alignment.TopEnd).padding(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        "${d.name}  ${(d.score * 100).toInt()}%",
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        // --- controls ---
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { userPaused = !userPaused; model.paused = userPaused }) {
                Text(if (userPaused) "Resume" else "Pause")
            }
            Button(onClick = {
                val sig = SoundLibrary.signature(model.grabRecent())
                if (sig == null) {
                    Toast.makeText(context, "No sound detected. Make the sound, then tap Label.", Toast.LENGTH_SHORT).show()
                } else {
                    model.paused = true      // freeze the display so you can see what you're labeling
                    nameInput = ""
                    pending = sig
                }
            }) { Text("Label sound") }
        }

        Text("Sensitivity (noise floor ${floorDb.toInt()} dB)", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = floorDb,
            onValueChange = { floorDb = it; model.floorDb = it },
            valueRange = -110f..-50f
        )

        if (sounds.isNotEmpty()) {
            Text("Saved sounds", style = MaterialTheme.typography.labelLarge)
            sounds.forEach { s ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.name, Modifier.weight(1f))
                    TextButton(onClick = { library.delete(s.name); refresh() }) { Text("Delete") }
                }
            }
        }
    }

    // --- naming dialog ---
    pending?.let { sig ->
        AlertDialog(
            onDismissRequest = { pending = null; model.paused = userPaused },
            title = { Text("Name this sound") },
            text = {
                OutlinedTextField(
                    value = nameInput, onValueChange = { nameInput = it },
                    singleLine = true, label = { Text("e.g. Doorbell, Clap, Whistle") }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = nameInput.isNotBlank(),
                    onClick = {
                        library.save(LabeledSound(nameInput.trim(), sig))
                        refresh()
                        pending = null; model.paused = userPaused
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { pending = null; model.paused = userPaused }) { Text("Cancel") }
            }
        )
    }
}

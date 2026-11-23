package com.example.spectro

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*

class MainActivity : Activity() {

    private lateinit var model: SpectrogramModel
    private lateinit var library: SoundLibrary
    private lateinit var engine: AudioEngine
    private lateinit var spectrogramView: SpectrogramView
    private lateinit var detectedText: TextView
    private lateinit var soundsList: LinearLayout
    private var userPaused = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun hasMic() =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        library = SoundLibrary(this)
        model = SpectrogramModel()
        engine = AudioEngine(model::onFrame)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        root.addView(TextView(this).apply { text = "Live Spectrogram"; textSize = 20f })

        spectrogramView = SpectrogramView(this, model)
        root.addView(spectrogramView, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply { topMargin = dp(8) })

        detectedText = TextView(this).apply {
            text = "Listening…"
            textSize = 18f
            setPadding(0, dp(8), 0, dp(4))
        }
        root.addView(detectedText)

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val pauseBtn = Button(this).apply { text = "Pause" }
        pauseBtn.setOnClickListener {
            userPaused = !userPaused
            model.paused = userPaused
            pauseBtn.text = if (userPaused) "Resume" else "Pause"
        }
        val labelBtn = Button(this).apply { text = "Label sound" }
        labelBtn.setOnClickListener { onLabelClicked() }
        buttons.addView(pauseBtn)
        buttons.addView(labelBtn)
        root.addView(buttons)

        val sensLabel = TextView(this).apply { text = "Sensitivity (noise floor ${model.floorDb.toInt()} dB)" }
        root.addView(sensLabel)
        root.addView(SeekBar(this).apply {
            max = 60
            progress = (model.floorDb + 110f).toInt()   // range -110 .. -50 dB
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    model.floorDb = -110f + p
                    sensLabel.text = "Sensitivity (noise floor ${model.floorDb.toInt()} dB)"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        root.addView(TextView(this).apply { text = "Saved sounds"; setPadding(0, dp(8), 0, 0) })
        soundsList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(soundsList) }, LinearLayout.LayoutParams(MATCH_PARENT, dp(130)))

        setContentView(root)

        model.onUpdate = { spectrogramView.postInvalidateOnAnimation() }
        model.onDetection = { d ->
            runOnUiThread {
                detectedText.text = if (d == null) "Listening…" else "Detected: ${d.name}  ${(d.score * 100).toInt()}%"
            }
        }

        refreshSounds()
        if (!hasMic()) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
    }

    private fun refreshSounds() {
        val sounds = library.loadAll()
        model.library = sounds
        soundsList.removeAllViews()
        for (s in sounds) {
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(this).apply { text = s.name }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            row.addView(Button(this).apply {
                text = "Delete"
                setOnClickListener { library.delete(s.name); refreshSounds() }
            })
            soundsList.addView(row)
        }
    }

    private fun onLabelClicked() {
        val sig = SoundLibrary.signature(model.grabRecent())
        if (sig == null) {
            Toast.makeText(this, "No sound detected. Make the sound, then tap Label.", Toast.LENGTH_SHORT).show()
            return
        }
        model.paused = true   // freeze the display so you can see what you're labeling
        val input = EditText(this).apply { hint = "e.g. Doorbell, Clap, Whistle"; setSingleLine() }
        val box = FrameLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle("Name this sound")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    library.save(LabeledSound(name, sig))
                    refreshSounds()
                }
            }
            .setNegativeButton("Cancel", null)
            .setOnDismissListener { model.paused = userPaused }
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (hasMic()) engine.start()
    }

    override fun onResume() {
        super.onResume()
        if (hasMic()) engine.start()
    }

    override fun onPause() {
        engine.stop()
        super.onPause()
    }
}

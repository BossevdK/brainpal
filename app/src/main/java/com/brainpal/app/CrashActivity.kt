package com.brainpal.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Eenvoudig scherm zonder Compose dat de foutmelding toont als de app crasht. Draait in een eigen proces,
 * dus het werkt ook als de crash al bij het opstarten gebeurt.
 */
class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getStringExtra("t") ?: "Geen details beschikbaar."
        val pad = (16 * resources.displayMetrics.density).toInt()

        val title = TextView(this).apply {
            this.text = "BrainPal is gecrasht"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            setPadding(pad, pad * 2, pad, pad / 2)
        }
        val hint = TextView(this).apply {
            this.text = "Maak een foto van dit scherm of kopieer de tekst en stuur die door."
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(pad, 0, pad, pad / 2)
        }
        val copy = Button(this).apply {
            this.text = "Kopieer foutmelding"
            setOnClickListener {
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("crash", text))
            }
        }
        val status = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(pad, pad / 2, pad, 0)
            visibility = android.view.View.GONE
        }
        val upd = Button(this).apply { this.text = "Controleer op updates" }
        var info: UpdateInfo? = null
        var apk: java.io.File? = null
        fun say(t: String) = runOnUiThread { status.text = t; status.visibility = android.view.View.VISIBLE }
        upd.setOnClickListener {
            val i = info
            upd.isEnabled = false
            Thread {
                try {
                    if (i == null) {
                        say("Controleren…")
                        val found = checkForUpdate(currentVersion(this))
                        info = found
                        if (found == null) say("Je hebt de nieuwste versie. Stuur de foutmelding hierboven door.")
                        else { say("Versie ${found.version} is beschikbaar."); runOnUiThread { upd.text = "Download en installeer ${found.version}" } }
                    } else {
                        val file = apk ?: downloadUpdate(this, i) { p -> say("Downloaden… ${(p * 100).toInt()}%") }.also { apk = it }
                        if (installApk(this, file)) say("Het installatiescherm van Android opent.")
                        else say("Zet bij de instelling die nu opent 'Toestaan' aan voor BrainPal, ga terug en tik nog een keer op de knop.")
                    }
                } catch (e: Exception) {
                    say("Mislukt: ${e.message ?: "geen verbinding"}")
                }
                runOnUiThread { upd.isEnabled = true }
            }.start()
        }
        val body = TextView(this).apply {
            this.text = text
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.BLACK)
            setTextIsSelectable(true)
            setPadding(pad, pad, pad, pad)
        }
        val scroll = ScrollView(this).apply { addView(body) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            addView(title)
            addView(hint)
            addView(copy)
            addView(upd)
            addView(status)
            addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)
    }
}

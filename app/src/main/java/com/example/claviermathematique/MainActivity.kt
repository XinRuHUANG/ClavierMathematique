package com.example.claviermathematique

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
        }

        val title = TextView(this).apply {
            text = "Clavier Mathématique"
            textSize = 26f
        }

        val description = TextView(this).apply {
            text = """
                Ton clavier de symboles mathématiques.

                8 catégories :
                • Calcul
                • Comparaison
                • Grec
                • Ensembles
                • Analyse
                • Logique
                • Algèbre linéaire
                • Autres

                Active le clavier ci-dessous pour commencer.
            """.trimIndent()
            textSize = 17f
            setPadding(0, 24, 0, 24)
        }

        val settingsButton = Button(this).apply {
            text = "1. Activer le clavier"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
        }

        val pickerButton = Button(this).apply {
            text = "2. Choisir le clavier"
            setOnClickListener {
                val manager =
                    getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                manager.showInputMethodPicker()
            }
        }

        layout.addView(title)
        layout.addView(description)
        layout.addView(settingsButton)
        layout.addView(pickerButton)

        setContentView(layout)
    }
}

package com.example.claviermathematique

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(40), dp(24), dp(24))
        }

        val title = TextView(this).apply {
            text = "Panneau Mathématique"
            textSize = 26f
        }

        val description = TextView(this).apply {
            text = """
                Utilise ton clavier Android habituel.

                Le panneau flottant permet d'accéder rapidement aux symboles :

                • Calcul
                • Comparaison
                • Grec
                • Ensembles
                • Analyse
                • Logique
                • Algèbre linéaire
                • Autres

                Tu peux déplacer et redimensionner le panneau.

                Appuie sur un symbole pour le copier.
            """.trimIndent()

            textSize = 16f
            setPadding(0, dp(24), 0, dp(24))
        }

        val permissionButton = Button(this).apply {
            text = "1. Autoriser le panneau flottant"

            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
            }
        }

        val startButton = Button(this).apply {
            text = "2. Ouvrir le panneau mathématique"

            setOnClickListener {
                if (Settings.canDrawOverlays(this@MainActivity)) {
                    startService(
                        Intent(
                            this@MainActivity,
                            FloatingMathService::class.java
                        )
                    )
                } else {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
            }
        }

        layout.addView(title)
        layout.addView(description)
        layout.addView(permissionButton)
        layout.addView(startButton)

        setContentView(layout)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
package com.example.claviermathematique

import android.inputmethodservice.InputMethodService
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

class MathKeyboardService : InputMethodService() {

    private lateinit var root: LinearLayout
    private lateinit var symbolArea: LinearLayout
    private var currentCategory = "Calcul"

    private val categories = linkedMapOf(
        "Calcul" to listOf(
            "+", "−", "×", "÷", "±", "∓", "=", "≠",
            "√", "∛", "^", "%", "!", "(", ")", "/"
        ),
        "Comparaison" to listOf(
            "<", ">", "≤", "≥", "≈", "≃", "≡", "∼",
            "∝", "≪", "≫", "≅", "≇", "≮", "≯", "≊"
        ),
        "Grec" to listOf(
            "α", "β", "γ", "δ", "ε", "ζ", "η", "θ",
            "ι", "κ", "λ", "μ", "ν", "ξ", "ο", "π",
            "ρ", "σ", "τ", "υ", "φ", "χ", "ψ", "ω",
            "Γ", "Δ", "Θ", "Λ", "Ξ", "Π", "Σ", "Ω",
            "e", "i", "ℯ", "ϕ", "ϵ", "ς"
        ),
        "Ensembles" to listOf(
            "∈", "∉", "∋", "⊂", "⊆", "⊃", "⊇", "∪",
            "∩", "∅", "∖", "⊄", "⊈", "ℕ", "ℤ", "ℚ",
            "ℝ", "ℂ", "𝔽", "⊕", "⊗", "×", "∁", "∝"
        ),
        "Analyse" to listOf(
            "lim", "→", "∞", "∑", "∏", "∫", "∬", "∮",
            "∂", "∇", "′", "″", "Δ", "d/dx", "dx", "dy",
            "∯", "∰", "∴", "∵", "↦", "⟶", "∄", "∃"
        ),
        "Logique" to listOf(
            "∀", "∃", "∄", "¬", "∧", "∨", "⇒", "⇔",
            "⟹", "⟺", "⊤", "⊥", "∴", "∵", "⊢", "⊨",
            "⊻", "⊼", "⊽", "∎"
        ),
        "Algèbre linéaire" to listOf(
            "→", "·", "×", "⊗", "⊕", "⟨", "⟩", "‖",
            "det", "tr", "ker", "Im", "dim", "span",
            "Aᵀ", "A⁻¹", "Iₙ", "λ", "v⃗", "u⃗", "0⃗", "ℝⁿ"
        ),
        "Autres" to listOf(
            "P(A)", "P(A|B)", "E(X)", "Var", "Cov", "σ", "σ²",
            "sin", "cos", "tan", "arcsin", "ln", "log",
            "∠", "°", "⊥", "∥", "△", "²", "³", "⁴", "ⁿ",
            "₀", "₁", "₂", "₃", "ₙ", "½", "⅓", "¼", "¾",
            "[", "]", "{", "}", "|", "‖", "⌊", "⌋", "⌈", "⌉",
            ":=", "↦", "≈", "∼", "≅", "∅"
        )
    )

    override fun onCreateInputView(): View {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setBackgroundColor(Color.rgb(235, 237, 242))
        }

        val categoryScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }

        val categoryRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        categories.keys.forEach { category ->
            val button = Button(this).apply {
                text = category
                isAllCaps = false
                textSize = 12f
                setPadding(dp(5), 0, dp(5), 0)
                setOnClickListener {
                    currentCategory = category
                    renderSymbols()
                }
            }
            categoryRow.addView(
                button,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(46)
                )
            )
        }

        categoryScroll.addView(categoryRow)
        root.addView(
            categoryScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
            )
        )

        symbolArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        root.addView(
            symbolArea,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        addControl(controls, "⌫") {
            currentInputConnection?.deleteSurroundingText(1, 0)
        }

        addControl(controls, "Espace") {
            currentInputConnection?.commitText(" ", 1)
        }

        addControl(controls, "↵") {
            currentInputConnection?.commitText("\n", 1)
        }

        addControl(controls, "🌐") {
            val manager =
                getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            manager.showInputMethodPicker()
        }

        root.addView(
            controls,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
            )
        )

        renderSymbols()
        return root
    }

    private fun renderSymbols() {
        symbolArea.removeAllViews()

        val symbols = categories[currentCategory] ?: emptyList()

        symbols.chunked(4).forEach { rowSymbols ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            rowSymbols.forEach { symbol ->
                val button = Button(this).apply {
                    text = symbol
                    isAllCaps = false
                    textSize = if (symbol.length > 4) 15f else 21f
                    setPadding(dp(1), 0, dp(1), 0)

                    setOnClickListener {
                        currentInputConnection?.commitText(symbol, 1)
                    }
                }

                val params = LinearLayout.LayoutParams(
                    0,
                    dp(49),
                    1f
                ).apply {
                    setMargins(dp(2), dp(1), dp(2), dp(1))
                }

                row.addView(button, params)
            }

            repeat(4 - rowSymbols.size) {
                row.addView(
                    View(this),
                    LinearLayout.LayoutParams(0, dp(49), 1f)
                )
            }

            symbolArea.addView(row)
        }
    }

    private fun addControl(
        parent: LinearLayout,
        label: String,
        action: () -> Unit
    ) {
        val button = Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { action() }
        }

        parent.addView(
            button,
            LinearLayout.LayoutParams(
                0,
                dp(46),
                1f
            )
        )
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}

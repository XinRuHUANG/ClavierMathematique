
package com.example.claviermathematique

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
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
    private lateinit var categoryRow: LinearLayout
    private lateinit var pageLabel: TextView

    private var currentCategory = "Calcul"
    private var currentPage = 0
    private var uppercase = false
    private var symbolsVisible = true

    private val darkTheme: Boolean
        get() = (resources.configuration.uiMode
            and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private val backgroundColor: Int
        get() = if (darkTheme) Color.rgb(29, 30, 39)
        else Color.rgb(235, 237, 242)

    private val keyColor: Int
        get() = if (darkTheme) Color.rgb(53, 55, 67)
        else Color.WHITE

    private val specialColor: Int
        get() = if (darkTheme) Color.rgb(76, 78, 119)
        else Color.rgb(207, 211, 235)

    private val foregroundColor: Int
        get() = if (darkTheme) Color.WHITE
        else Color.rgb(35, 39, 50)

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

    private val recentSymbols = mutableListOf<String>()

    override fun onCreate() {
        super.onCreate()

        val saved = getSharedPreferences(
            "math_keyboard", Context.MODE_PRIVATE
        ).getString("recent", "") ?: ""

        if (saved.isNotEmpty()) {
            recentSymbols.addAll(saved.split("\u001F"))
        }
    }

    override fun onCreateInputView(): View {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(3), dp(3), dp(3), dp(3))
            setBackgroundColor(backgroundColor)
        }

        // Onglets des catégories mathématiques
        val categoryScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        categoryRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        categoryScroll.addView(categoryRow)

        root.addView(
            categoryScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(42)
            )
        )

        // Zone de symboles : 7 colonnes x 3 lignes
        symbolArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        root.addView(
            symbolArea,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(124)
            )
        )

        // Indicateur de pagination
        pageLabel = TextView(this).apply {
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(foregroundColor)
        }

        root.addView(
            pageLabel,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(18)
            )
        )

        renderCategories()
        renderSymbols()

        // Rangée dédiée aux chiffres
        val numberRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        "1234567890".forEach { digit ->
            addKey(
                numberRow, digit.toString(),
                weight = 1f, height = 37
            ) {
                commit(digit.toString())
            }
        }

        root.addView(
            numberRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(39)
            )
        )

        // Première rangée AZERTY
        addLetterRow("azertyuiop")

        // Deuxième rangée AZERTY
        addLetterRow("qsdfghjklm")

        // Troisième rangée : majuscule, lettres, effacer
        val thirdRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        addKey(thirdRow, "⇧", 1.5f, 43, special = true) {
            uppercase = !uppercase
            refreshLetterRows()
        }

        "wxcvbn".forEach { letter ->
            addKey(thirdRow, letter.toString(), 1f, 43) {
                typeLetter(letter)
            }
        }

        addKey(thirdRow, "'", 0.8f, 43) {
            commit("'")
        }

        addKey(thirdRow, "⌫", 1.5f, 43, special = true) {
            currentInputConnection?.deleteSurroundingText(1, 0)
        }

        root.addView(
            thirdRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(45)
            )
        )

        // Rangée inférieure : modes, ponctuation, espace et commandes
        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        addKey(bottomRow, "?123", 1.25f, 44, special = true) {
            symbolsVisible = !symbolsVisible
            symbolArea.visibility =
                if (symbolsVisible) View.VISIBLE else View.GONE
            pageLabel.visibility =
                if (symbolsVisible) View.VISIBLE else View.GONE
        }

        addKey(bottomRow, ",", 0.7f, 44) {
            commit(",")
        }

        addKey(bottomRow, "Espace", 3f, 44) {
            commit(" ")
        }

        addKey(bottomRow, ".", 0.7f, 44) {
            commit(".")
        }

        addKey(bottomRow, "↵", 1.1f, 44, special = true) {
            currentInputConnection?.commitText("\n", 1)
        }

        addKey(bottomRow, "🌐", 1.1f, 44, special = true) {
            val manager = getSystemService(
                Context.INPUT_METHOD_SERVICE
            ) as InputMethodManager
            manager.showInputMethodPicker()
        }

        root.addView(
            bottomRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46)
            )
        )

        return root
    }

    private fun renderCategories() {
        categoryRow.removeAllViews()

        (listOf("Récents") + categories.keys).forEach { category ->
            addKey(
                categoryRow,
                category,
                weight = 0f,
                height = 38,
                special = category == currentCategory ||
                    (category == "Récents" && currentCategory == "Récents"),
                horizontalPadding = 7
            ) {
                currentCategory = category
                currentPage = 0
                symbolsVisible = true
                symbolArea.visibility = View.VISIBLE
                pageLabel.visibility = View.VISIBLE
                renderCategories()
                renderSymbols()
            }
        }
    }

    private fun renderSymbols() {
        symbolArea.removeAllViews()

        val symbols = if (currentCategory == "Récents") {
            recentSymbols.toList()
        } else {
            categories[currentCategory] ?: emptyList()
        }

        val pages = symbols.chunked(21)
        val pageCount = pages.size
        if (currentPage >= pageCount) currentPage = 0

        val pageSymbols = pages.getOrNull(currentPage) ?: emptyList()

        pageSymbols.chunked(7).forEach { rowSymbols ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            rowSymbols.forEach { symbol ->
                addKey(row, symbol, 1f, 35) {
                    commit(symbol)
                    rememberSymbol(symbol)
                }
            }

            repeat(7 - rowSymbols.size) {
                row.addView(
                    View(this),
                    LinearLayout.LayoutParams(0, dp(35), 1f)
                )
            }

            symbolArea.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(38)
                )
            )
        }

        // Navigation entre les pages de symboles
        val navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        addKey(navigation, "‹", 1f, 24, special = true) {
            if (pageCount > 0) {
                currentPage = (currentPage - 1 + pageCount) % pageCount
                renderSymbols()
            }
        }

        pageLabel.text = if (pageCount == 0) {
            "Aucun symbole récent"
        } else {
            "Page ${currentPage + 1} / $pageCount"
        }

        addKey(navigation, "›", 1f, 24, special = true) {
            if (pageCount > 0) {
                currentPage = (currentPage + 1) % pageCount
                renderSymbols()
            }
        }

        // Les boutons de navigation restent dans la zone des symboles.
        symbolArea.addView(navigation)
    }

    private fun addLetterRow(letters: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            tag = "letters:$letters"
        }

        letters.forEach { letter ->
            addKey(row, letter.toString(), 1f, 43) {
                typeLetter(letter)
            }
        }

        root.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(45)
            )
        )
    }

    private fun refreshLetterRows() {
        // Met à jour les libellés des deux premières rangées AZERTY.
        for (index in 0 until root.childCount) {
            val row = root.getChildAt(index) as? LinearLayout ?: continue
            val letters = row.tag as? String ?: continue
            val text = letters.removePrefix("letters:")

            for (i in 0 until minOf(row.childCount, text.length)) {
                val button = row.getChildAt(i) as? Button ?: continue
                button.text = if (uppercase) {
                    text[i].uppercaseChar().toString()
                } else {
                    text[i].toString()
                }
            }
        }
    }

    private fun typeLetter(letter: Char) {
        val value = if (uppercase) {
            letter.uppercaseChar().toString()
        } else {
            letter.toString()
        }

        commit(value)
        uppercase = false
        refreshLetterRows()
    }

    private fun commit(value: String) {
        currentInputConnection?.commitText(value, 1)
    }

    private fun rememberSymbol(symbol: String) {
        recentSymbols.remove(symbol)
        recentSymbols.add(0, symbol)

        while (recentSymbols.size > 21) {
            recentSymbols.removeAt(recentSymbols.lastIndex)
        }

        getSharedPreferences(
            "math_keyboard", Context.MODE_PRIVATE
        ).edit().putString(
            "recent", recentSymbols.joinToString("\u001F")
        ).apply()
    }

    private fun addKey(
        parent: LinearLayout,
        label: String,
        weight: Float,
        height: Int,
        special: Boolean = false,
        horizontalPadding: Int = 1,
        action: () -> Unit
    ) {
        val button = Button(this).apply {
            text = label
            isAllCaps = false
            textSize = when {
                label.length > 8 -> 10f
                label.length > 4 -> 12f
                label.length > 2 -> 14f
                else -> 19f
            }
            typeface = Typeface.DEFAULT
            setTextColor(foregroundColor)
            minHeight = 0
            minWidth = 0
            stateListAnimator = null
            setPadding(dp(horizontalPadding), 0, dp(horizontalPadding), 0)

            background = GradientDrawable().apply {
                setColor(if (special) specialColor else keyColor)
                cornerRadius = dp(5).toFloat()
            }

            setOnClickListener { action() }
        }

        val params = LinearLayout.LayoutParams(
            if (weight == 0f) {
                LinearLayout.LayoutParams.WRAP_CONTENT
            } else {
                0
            },
            dp(height),
            weight
        ).apply {
            setMargins(dp(1), dp(1), dp(1), dp(1))
        }

        parent.addView(button, params)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}

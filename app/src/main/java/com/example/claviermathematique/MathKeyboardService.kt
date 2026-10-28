
package com.example.claviermathematique

import android.content.Context
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

class MathKeyboardService : InputMethodService() {

    private lateinit var root: LinearLayout
    private lateinit var symbolArea: SwipeLayout
    private lateinit var symbolPageIndicator: TextView
    private lateinit var keyboardArea: LinearLayout
    private lateinit var letterKeyboard: LinearLayout
    private lateinit var permanentSpecialRow: HorizontalScrollView
    private lateinit var specialKeyboard: SwipeLayout
    private lateinit var specialPageIndicator: TextView
    private lateinit var shiftButton: Button
    private lateinit var modeButton: Button

    private var currentCategory = "Calcul"
    private var specialMode = false
    private var capsLock = false
    private var shiftPending = false
    private var lastShiftTap = 0L
    private var symbolPage = 0
    private var specialPage = 0

    private val darkMode: Boolean
        get() = (resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private val keyboardBackground: Int
        get() = if (darkMode) Color.rgb(30, 30, 30) else Color.rgb(235, 237, 242)

    private val keyBackground: Int
        get() = if (darkMode) Color.rgb(65, 65, 65) else Color.rgb(250, 250, 252)

    private val keyTextColor: Int
        get() = if (darkMode) Color.WHITE else Color.BLACK

    private val recentSymbols = mutableListOf<String>()

    private val categories = linkedMapOf(
        "Calcul" to listOf(
            "+", "−", "×", "÷", "±", "∓", "=", "≠", "√", "∛",
            "^", "%", "!", "(", ")", "/", "·", "⋅", "∞", "∣",
            "∤", "∝", "∼", "≈", "≃", "≅", "≡", "≢", "∴", "∵",
            "∎", "∶", "∷", "∜", "‰"
        ),
        "Comparaison" to listOf(
            "<", ">", "≤", "≥", "≈", "≃", "≡", "∼", "∝", "≪",
            "≫", "≅", "≇", "≮", "≯", "≊", "≲", "≳", "≶", "≷",
            "≺", "≻", "≼", "≽", "≉", "≋", "≌", "≍", "≦", "≧"
        ),
        "Grec" to listOf(
            "α", "β", "γ", "δ", "ε", "ζ", "η", "θ", "ι", "κ",
            "λ", "μ", "ν", "ξ", "ο", "π", "ρ", "σ", "τ", "υ",
            "φ", "χ", "ψ", "ω", "Γ", "Δ", "Θ", "Λ", "Ξ", "Π",
            "Σ", "Ω", "ℯ", "ϕ", "ϵ", "ς"
        ),
        "Ensembles" to listOf(
            "∈", "∉", "∋", "⊂", "⊆", "⊃", "⊇", "∪", "∩", "∅",
            "∖", "⊄", "⊈", "ℕ", "ℤ", "ℚ", "ℝ", "ℂ", "𝔽", "⊕",
            "⊗", "∁", "⊊", "⊋", "⊍", "⊎", "∃", "∀", "∉", "∋"
        ),
        "Analyse" to listOf(
            "lim", "→", "∞", "∑", "∏", "∫", "∬", "∮", "∂", "∇",
            "′", "″", "Δ", "d/dx", "dx", "dy", "∯", "∰", "∴", "∵",
            "↦", "⟶", "∄", "∃", "∂x", "∂y", "∂²", "∮", "∫", "∬"
        ),
        "Logique" to listOf(
            "∀", "∃", "∄", "¬", "∧", "∨", "⇒", "⇔", "⟹", "⟺",
            "⊤", "⊥", "∴", "∵", "⊢", "⊨", "⊻", "⊼", "⊽", "∎",
            "⊣", "⊩", "⊬", "⊭", "⊮", "⊯", "∦", "∥", "≃", "≡"
        ),
        "Algèbre linéaire" to listOf(
            "→", "·", "×", "⊗", "⊕", "⟨", "⟩", "‖", "det", "tr",
            "ker", "Im", "dim", "span", "Aᵀ", "A⁻¹", "Iₙ", "λ", "v⃗", "u⃗",
            "0⃗", "ℝⁿ", "Id", "rank", "diag", "A²", "Aᵀ", "A⁻¹", "⟶", "↦"
        ),
        "Autres" to listOf(
            "P(A)", "P(A|B)", "E(X)", "Var", "Cov", "σ", "σ²", "sin", "cos", "tan",
            "arcsin", "ln", "log", "∠", "°", "⊥", "∥", "△", "²", "³",
            "⁴", "ⁿ", "₀", "₁", "₂", "₃", "ₙ", "½", "⅓", "¼",
            "¾", "[", "]", "{", "}", "|", "‖", "⌊", "⌋", "⌈",
            "⌉", ":=", "↦", "≈", "∼", "≅", "∅", "∝", "∴", "∵"
        )
    )

    // Caractères spéciaux toujours visibles en mode AZERTY.
    private val permanentSpecials =
        listOf("@", "#", "€", "$", "%", "&", "*", "-", "+", "=")

    // Deux pages de caractères spéciaux, avec 30 touches par page.
    private val specialPages = listOf(
        listOf(
            "@", "#", "€", "$", "%", "&", "*", "-", "+", "=",
            "(", ")", "[", "]", "{", "}", "/", "\\", "|", "_",
            "!", "?", ":", ";", "'", "\"", ",", ".", "~", "`"
        ),
        listOf(
            "£", "¥", "¢", "§", "©", "®", "™", "°", "…", "—",
            "–", "·", "•", "¿", "¡", "«", "»", "‹", "›", "‰",
            "^", "=", "<", ">", "+", "-", "*", "/", ":", ";"
        )
    )

    override fun onCreateInputView(): View {
        loadRecentSymbols()

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(3), dp(4), dp(14))
            setBackgroundColor(keyboardBackground)
        }

        buildCategoryRow()

        symbolArea = SwipeLayout(this) { direction ->
            val count = symbolPageCount()
            if (count > 1) {
                symbolPage = (symbolPage + direction + count) % count
                renderSymbols()
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
        }

        root.addView(
            symbolArea,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(102)
            )
        )

        symbolPageIndicator = makeIndicator()

        root.addView(
            symbolPageIndicator,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(15)
            )
        )

        keyboardArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        root.addView(
            keyboardArea,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        buildNumberRow()
        buildLetterKeyboard()
        buildPermanentSpecialRow()
        buildSpecialKeyboard()
        buildBottomControls()

        renderSymbols()
        updateShiftButton()
        updateKeyboardVisibility()

        return root
    }

    private fun buildCategoryRow() {
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        (listOf("Récents") + categories.keys.toList()).forEach { category ->
            val button = makeKey(category, 11f) {
                currentCategory = category
                symbolPage = 0
                renderSymbols()
            }

            row.addView(
                button,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(38)
                ).apply {
                    setMargins(dp(1), dp(1), dp(1), dp(1))
                }
            )
        }

        scroll.addView(row)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(41)
            )
        )
    }

    private fun symbolsForCurrentCategory(): List<String> =
        if (currentCategory == "Récents") {
            recentSymbols.toList()
        } else {
            categories[currentCategory].orEmpty()
        }

    private fun symbolPageCount(): Int =
        maxOf(1, (symbolsForCurrentCategory().size + 29) / 30)

    private fun renderSymbols() {
        if (!::symbolArea.isInitialized) return

        symbolArea.removeAllViews()

        val symbols = symbolsForCurrentCategory()
        val pageCount = symbolPageCount()

        symbolPage = symbolPage.coerceIn(0, pageCount - 1)

        val visibleSymbols = symbols.drop(symbolPage * 30).take(30)

        visibleSymbols.chunked(10).forEach { chunk ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            chunk.forEach { symbol ->
                val button = makeKey(
                    symbol,
                    if (symbol.length > 4) 11f else 16f
                ) {
                    insertSymbol(symbol)
                }

                row.addView(
                    button,
                    LinearLayout.LayoutParams(0, dp(31), 1f).apply {
                        setMargins(dp(1), dp(1), dp(1), dp(1))
                    }
                )
            }

            repeat(10 - chunk.size) {
                row.addView(
                    View(this),
                    LinearLayout.LayoutParams(0, dp(31), 1f)
                )
            }

            symbolArea.addView(row)
        }

        symbolPageIndicator.text = if (pageCount > 1) {
            "Glisser ← / →  •  ${symbolPage + 1}/$pageCount"
        } else {
            if (currentCategory == "Récents") {
                "Symboles récents"
            } else {
                "Symboles mathématiques"
            }
        }
    }

    private fun buildNumberRow() {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        "1234567890".forEach { digit ->
            val button = makeKey(digit.toString(), 16f) {
                commit(digit.toString())
            }

            row.addView(
                button,
                LinearLayout.LayoutParams(0, dp(34), 1f).apply {
                    setMargins(dp(1), dp(1), dp(1), dp(1))
                }
            )
        }

        keyboardArea.addView(row)
    }

    private fun buildLetterKeyboard() {
        letterKeyboard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        addLetterRow(letterKeyboard, "azertyuiop")
        addLetterRow(letterKeyboard, "qsdfghjklm")

        val thirdRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        shiftButton = makeKey("⇧", 20f) {
            handleShiftTap()
        }

        thirdRow.addView(
            shiftButton,
            LinearLayout.LayoutParams(0, dp(39), 1.15f).apply {
                setMargins(dp(1), dp(1), dp(1), dp(1))
            }
        )

        // Maj fonctionne aussi pour w, x, c, v, b et n.
        "wxcvbn,;:'".forEach { character ->
            val button = makeKey(character.toString(), 16f) {
                if (character.isLetter()) {
                    val output = if (capsLock || shiftPending) {
                        character.uppercaseChar().toString()
                    } else {
                        character.toString()
                    }

                    commit(output)

                    if (!capsLock) {
                        shiftPending = false
                    }

                    updateShiftButton()
                    refreshLetterLabels()
                } else {
                    commit(character.toString())
                }
            }

            thirdRow.addView(
                button,
                LinearLayout.LayoutParams(0, dp(39), 0.78f).apply {
                    setMargins(dp(1), dp(1), dp(1), dp(1))
                }
            )
        }

        val deleteButton = makeKey("⌫", 19f) {
            currentInputConnection?.deleteSurroundingText(1, 0)
        }

        thirdRow.addView(
            deleteButton,
            LinearLayout.LayoutParams(0, dp(39), 1.15f).apply {
                setMargins(dp(1), dp(1), dp(1), dp(1))
            }
        )

        letterKeyboard.addView(thirdRow)
        keyboardArea.addView(letterKeyboard)
    }

    private fun addLetterRow(parent: LinearLayout, characters: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        characters.forEach { character ->
            val label = if (capsLock || shiftPending) {
                character.uppercaseChar().toString()
            } else {
                character.toString()
            }

            val button = makeKey(label, 16f) {
                val output = if (capsLock || shiftPending) {
                    character.uppercaseChar().toString()
                } else {
                    character.toString()
                }

                commit(output)

                if (!capsLock) {
                    shiftPending = false
                }

                updateShiftButton()
                refreshLetterLabels()
            }

            row.addView(
                button,
                LinearLayout.LayoutParams(0, dp(39), 1f).apply {
                    setMargins(dp(1), dp(1), dp(1), dp(1))
                }
            )
        }

        parent.addView(row)
    }

    private fun buildPermanentSpecialRow() {
        permanentSpecialRow = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        permanentSpecials.forEach { symbol ->
            val button = makeKey(symbol, 16f) {
                commit(symbol)
            }

            row.addView(
                button,
                LinearLayout.LayoutParams(dp(42), dp(33)).apply {
                    setMargins(dp(1), dp(1), dp(1), dp(1))
                }
            )
        }

        permanentSpecialRow.addView(row)

        keyboardArea.addView(
            permanentSpecialRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(35)
            )
        )
    }

    private fun buildSpecialKeyboard() {
        specialKeyboard = SwipeLayout(this) { direction ->
            specialPage =
                (specialPage + direction + specialPages.size) % specialPages.size

            renderSpecialPage()
        }.apply {
            orientation = LinearLayout.VERTICAL
        }

        specialPageIndicator = makeIndicator()

        keyboardArea.addView(specialKeyboard)

        keyboardArea.addView(
            specialPageIndicator,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(15)
            )
        )

        renderSpecialPage()
    }

    private fun renderSpecialPage() {
        if (!::specialKeyboard.isInitialized) return

        specialKeyboard.removeAllViews()

        specialPages[specialPage].chunked(10).forEach { chunk ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            chunk.forEach { symbol ->
                val button = makeKey(symbol, 16f) {
                    commit(symbol)
                }

                row.addView(
                    button,
                    LinearLayout.LayoutParams(0, dp(39), 1f).apply {
                        setMargins(dp(1), dp(1), dp(1), dp(1))
                    }
                )
            }

            repeat(10 - chunk.size) {
                row.addView(
                    View(this),
                    LinearLayout.LayoutParams(0, dp(39), 1f)
                )
            }

            specialKeyboard.addView(row)
        }

        specialPageIndicator.text =
            "Glisser ← / →  •  ${specialPage + 1}/${specialPages.size}"
    }

    private fun buildBottomControls() {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        modeButton = addBottomButton(row, "?123", 1.1f) {
            specialMode = !specialMode
            updateKeyboardVisibility()
        }

        addBottomButton(row, ",", 0.65f) {
            commit(",")
        }

        addBottomButton(row, "Espace", 2.2f) {
            commit(" ")
        }

        addBottomButton(row, ".", 0.65f) {
            commit(".")
        }

        addBottomButton(row, "↵", 0.9f) {
            commit("\n")
        }

        addBottomButton(row, "🌐", 0.9f) {
            val manager =
                getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

            manager.showInputMethodPicker()
        }

        keyboardArea.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(42)
            )
        )
    }

    private fun addBottomButton(
        parent: LinearLayout,
        label: String,
        weight: Float,
        action: () -> Unit
    ): Button {
        val button = makeKey(label, 13f, action)

        parent.addView(
            button,
            LinearLayout.LayoutParams(0, dp(39), weight).apply {
                setMargins(dp(1), dp(1), dp(1), dp(1))
            }
        )

        return button
    }

    private fun makeIndicator(): TextView =
        TextView(this).apply {
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(keyTextColor)
        }

    private fun makeKey(
        label: String,
        size: Float,
        action: () -> Unit
    ): Button =
        Button(this).apply {
            text = label
            textSize = size
            isAllCaps = false
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(1), 0, dp(1), 0)
            setTextColor(keyTextColor)
            backgroundTintList = ColorStateList.valueOf(keyBackground)
            setOnClickListener { action() }
        }

    private fun handleShiftTap() {
        val now = SystemClock.uptimeMillis()

        if (now - lastShiftTap in 1..350) {
            capsLock = !capsLock
            shiftPending = false
        } else {
            shiftPending = true
        }

        lastShiftTap = now
        updateShiftButton()
        refreshLetterLabels()
    }

    private fun updateShiftButton() {
        if (!::shiftButton.isInitialized) return

        val active = capsLock || shiftPending

        shiftButton.text = if (capsLock) "⇧▴" else "⇧"

        shiftButton.backgroundTintList = ColorStateList.valueOf(
            if (active) Color.WHITE else keyBackground
        )

        shiftButton.setTextColor(
            if (active) Color.BLACK else keyTextColor
        )
    }

    private fun refreshLetterLabels() {
        if (!::letterKeyboard.isInitialized) return

        val rows = listOf("azertyuiop", "qsdfghjklm")

        rows.forEachIndexed { rowIndex, characters ->
            val row = letterKeyboard.getChildAt(rowIndex) as? LinearLayout
                ?: return@forEachIndexed

            characters.forEachIndexed innerLoop@{ index, character ->
                val button = row.getChildAt(index) as? Button
                    ?: return@innerLoop

                button.text = if (capsLock || shiftPending) {
                    character.uppercaseChar().toString()
                } else {
                    character.toString()
                }
            }
        }

        val thirdRow = letterKeyboard.getChildAt(2) as? LinearLayout
            ?: return

        "wxcvbn".forEachIndexed { index, character ->
            val button = thirdRow.getChildAt(index + 1) as? Button
                ?: return@forEachIndexed

            button.text = if (capsLock || shiftPending) {
                character.uppercaseChar().toString()
            } else {
                character.toString()
            }
        }
    }

    private fun updateKeyboardVisibility() {
        if (!::letterKeyboard.isInitialized ||
            !::specialKeyboard.isInitialized
        ) {
            return
        }

        letterKeyboard.visibility =
            if (specialMode) View.GONE else View.VISIBLE

        specialKeyboard.visibility =
            if (specialMode) View.VISIBLE else View.GONE

        specialPageIndicator.visibility =
            if (specialMode) View.VISIBLE else View.GONE

        modeButton.text = if (specialMode) "ABC" else "?123"

        // La rangée de caractères courants reste visible dans les deux modes.
        permanentSpecialRow.visibility = View.VISIBLE
    }

    private fun insertSymbol(symbol: String) {
        commit(symbol)

        recentSymbols.remove(symbol)
        recentSymbols.add(0, symbol)

        while (recentSymbols.size > 30) {
            recentSymbols.removeAt(recentSymbols.lastIndex)
        }

        getSharedPreferences("math_keyboard", Context.MODE_PRIVATE)
            .edit()
            .putString("recent_symbols", recentSymbols.joinToString("\u001F"))
            .apply()

        if (currentCategory == "Récents") {
            symbolPage = 0
            renderSymbols()
        }
    }

    private fun loadRecentSymbols() {
        recentSymbols.clear()

        val stored = getSharedPreferences(
            "math_keyboard",
            Context.MODE_PRIVATE
        ).getString("recent_symbols", "").orEmpty()

        if (stored.isNotBlank()) {
            recentSymbols.addAll(
                stored.split("\u001F").filter { it.isNotBlank() }
            )
        }
    }

    private fun commit(value: String) {
        currentInputConnection?.commitText(value, 1)
    }

    /**
     * Gère les glissements horizontaux.
     * direction = -1 pour la page précédente, +1 pour la suivante.
     */
    private inner class SwipeLayout(
        context: Context,
        private val onSwipe: (Int) -> Unit
    ) : LinearLayout(context) {

        private var startX = 0f
        private var startY = 0f
        private var swipeHandled = false

        override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    swipeHandled = false
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - startX
                    val dy = event.y - startY

                    if (abs(dx) > dp(35) && abs(dx) > abs(dy)) {
                        swipeHandled = true
                        return true
                    }
                }
            }

            return super.onInterceptTouchEvent(event)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_UP -> {
                    val dx = event.x - startX
                    val dy = event.y - startY

                    if (swipeHandled &&
                        abs(dx) > dp(35) &&
                        abs(dx) > abs(dy)
                    ) {
                        onSwipe(if (dx < 0) 1 else -1)
                        performClick()
                    }

                    swipeHandled = false
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    swipeHandled = false
                    return true
                }
            }

            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}

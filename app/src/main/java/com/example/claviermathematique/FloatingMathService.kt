package com.example.claviermathematique

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// ======================================================
// CONFIGURATION : modifier les paramètres numériques ici
// Les dimensions sont en dp, les tailles de texte en sp.
// ======================================================

private object MathPanelConfig {

    // Dimensions et position initiales
    const val PANEL_WIDTH = 700
    const val PANEL_HEIGHT = 420
    const val PANEL_X = 50
    const val PANEL_Y = 200

    // Limites de redimensionnement
    const val MIN_WIDTH = 300
    const val MAX_WIDTH = 1000
    const val MIN_HEIGHT = 180
    const val MAX_HEIGHT = 800

    // Barre de titre
    const val TITLE_BAR_HEIGHT = 36
    const val TITLE_TEXT_SIZE = 16f
    const val TITLE_BUTTON_WIDTH = 44

    // Colonne des catégories
    const val CATEGORY_WIDTH = 94
    const val CATEGORY_BUTTON_HEIGHT = 32
    const val CATEGORY_TEXT_SIZE = 10f

    // Grille des symboles
    const val SYMBOLS_PER_ROW = 7
    const val ROWS_PER_PAGE = 4
    const val SYMBOLS_PER_PAGE = SYMBOLS_PER_ROW * ROWS_PER_PAGE
    const val SYMBOL_BUTTON_HEIGHT = 38
    const val SYMBOL_TEXT_SIZE = 18f
    const val LONG_SYMBOL_TEXT_SIZE = 13f
    const val LONG_SYMBOL_LENGTH = 4

    // Espacements
    const val PANEL_PADDING = 1
    const val CONTENT_PADDING = 0
    const val SYMBOL_MARGIN = 1
    const val CATEGORY_MARGIN = 1
    const val PAGE_INDICATOR_HEIGHT = 12
    const val RESIZE_HANDLE_HEIGHT = 10

    // Historique
    const val MAX_RECENT_SYMBOLS = 30

    // Détection du balayage horizontal
    const val SWIPE_DISTANCE = 60
    const val SWIPE_VELOCITY = 40
}

// ======================================================
// Service du panneau flottant
// ======================================================

class FloatingMathService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: LinearLayout
    private lateinit var mainArea: LinearLayout
    private lateinit var contentArea: SwipeableLinearLayout
    private lateinit var categoryArea: LinearLayout
    private lateinit var pageIndicator: TextView
    private lateinit var titleText: TextView

    private lateinit var params: WindowManager.LayoutParams
    private lateinit var preferences: SharedPreferences

    private var currentCategory = "Calcul"
    private var currentPage = 0
    private var panelWidth = 0
    private var panelHeight = 0
    private var isMinimized = false

    private val recentSymbols = mutableListOf<String>()

    private val categoryNames: List<String>
        get() = listOf("Récents") + categories.keys.toList()

    private val categories = linkedMapOf(
        "Calcul" to listOf(
            "+", "−", "×", "÷", "±",
            "=", "≠", "√", "∛", "^",
            "%", "!", "(", ")", "/",
            "·", "∞", "∝", "≈", "≡",
            "∼", "∴", "∵", "∜"
        ),

        "Comparaison" to listOf(
            "<", ">", "≤", "≥", "≈",
            "≃", "≡", "∼", "≪", "≫",
            "≅", "≇", "≮", "≯", "≲",
            "≳", "≺", "≻", "≼", "≽"
        ),

        "Grec" to listOf(
            "α", "β", "γ", "δ", "ε",
            "ζ", "η", "θ", "ι", "κ",
            "λ", "μ", "ν", "ξ", "ο",
            "π", "ρ", "σ", "τ", "υ",
            "φ", "χ", "ψ", "ω",
            "Γ", "Δ", "Θ", "Λ", "Ξ",
            "Π", "Σ", "Ω"
        ),

        "Ensembles" to listOf(
            "∈", "∉", "∋", "⊂", "⊆",
            "⊃", "⊇", "∪", "∩", "∅",
            "∖", "ℕ", "ℤ", "ℚ", "ℝ",
            "ℂ", "𝔽", "⊕", "⊗", "∁"
        ),

        "Analyse" to listOf(
            "lim", "→", "∞", "∑", "∏",
            "∫", "∬", "∮", "∂", "∇",
            "′", "″", "Δ", "dx", "dy",
            "↦", "⟶", "∃", "∄"
        ),

        "Logique" to listOf(
            "∀", "∃", "∄", "¬", "∧",
            "∨", "⇒", "⇔", "⟹", "⟺",
            "⊤", "⊥", "⊢", "⊨", "∎"
        ),

        "Algèbre linéaire" to listOf(
            "→", "·", "×", "⊗", "⊕",
            "⟨", "⟩", "‖", "det", "tr",
            "ker", "Im", "dim", "span",
            "Aᵀ", "A⁻¹", "Iₙ", "λ",
            "v⃗", "u⃗", "0⃗", "ℝⁿ"
        ),

        "Autres" to listOf(
            "P(A)", "P(A|B)", "E(X)", "Var",
            "Cov", "σ", "σ²", "sin",
            "cos", "tan", "ln", "log",
            "∠", "°", "⊥", "∥",
            "²", "³", "⁴", "ⁿ",
            "₀", "₁", "₂", "₃",
            "[", "]", "{", "}",
            "⌊", "⌋", "⌈", "⌉"
        )
    )

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        preferences = getSharedPreferences("math_panel", Context.MODE_PRIVATE)

        loadState()
        createFloatingPanel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // --------------------------------------------------
    // Chargement et sauvegarde
    // --------------------------------------------------

    private fun loadState() {
        panelWidth = preferences.getInt(
            "width",
            dp(MathPanelConfig.PANEL_WIDTH)
        ).coerceIn(
            dp(MathPanelConfig.MIN_WIDTH),
            dp(MathPanelConfig.MAX_WIDTH)
        )

        panelHeight = preferences.getInt(
            "height",
            dp(MathPanelConfig.PANEL_HEIGHT)
        ).coerceIn(
            dp(MathPanelConfig.MIN_HEIGHT),
            dp(MathPanelConfig.MAX_HEIGHT)
        )

        val savedRecents = preferences.getString("recent_symbols", null)
        if (!savedRecents.isNullOrEmpty()) {
            recentSymbols.addAll(
                savedRecents.split("\u001F")
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(MathPanelConfig.MAX_RECENT_SYMBOLS)
            )
        }

        val savedCategory = preferences.getString("category", "Calcul") ?: "Calcul"
        currentCategory = if (
            savedCategory == "Récents" || categories.containsKey(savedCategory)
        ) savedCategory else "Calcul"

        currentPage = preferences.getInt("page", 0).coerceAtLeast(0)
    }

    private fun saveState() {
        if (!::params.isInitialized || !::preferences.isInitialized) return

        preferences.edit()
            .putInt("x", params.x)
            .putInt("y", params.y)
            .putInt("width", params.width)
            .putInt("height", if (isMinimized) panelHeight else params.height)
            .putString("category", currentCategory)
            .putInt("page", currentPage)
            .putString("recent_symbols", recentSymbols.joinToString("\u001F"))
            .apply()
    }

    // --------------------------------------------------
    // Construction de la fenêtre
    // --------------------------------------------------

    private fun createFloatingPanel() {
        floatingView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(MathPanelConfig.PANEL_PADDING),
                dp(MathPanelConfig.PANEL_PADDING),
                dp(MathPanelConfig.PANEL_PADDING),
                0
            )
            setBackgroundColor(Color.rgb(245, 245, 245))
        }

        params = WindowManager.LayoutParams(
            panelWidth,
            panelHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = preferences.getInt("x", dp(MathPanelConfig.PANEL_X))
            y = preferences.getInt("y", dp(MathPanelConfig.PANEL_Y))
        }

        buildTitleBar()

        mainArea = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        contentArea = SwipeableLinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(MathPanelConfig.CONTENT_PADDING),
                dp(MathPanelConfig.CONTENT_PADDING),
                dp(MathPanelConfig.CONTENT_PADDING),
                dp(MathPanelConfig.CONTENT_PADDING)
            )
            gravity = Gravity.TOP
            onSwipe = { direction -> navigateHorizontally(direction) }
        }

        // Défilement vertical des symboles lorsque le panneau est réduit.
        val symbolsScroll = ScrollView(this).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            clipToPadding = false
            addView(
                contentArea,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        mainArea.addView(
            symbolsScroll,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            )
        )

        categoryArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
        }
        buildCategoryBar()

        // La liste des catégories possède son propre défilement vertical.
        val categoriesScroll = ScrollView(this).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            clipToPadding = false
            addView(
                categoryArea,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        mainArea.addView(
            categoriesScroll,
            LinearLayout.LayoutParams(
                dp(MathPanelConfig.CATEGORY_WIDTH),
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        )

        floatingView.addView(
            mainArea,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        pageIndicator = TextView(this).apply {
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.DKGRAY)
            setPadding(0, 0, 0, 0)
            setOnClickListener { goToNextPage() }
            contentDescription = "Appuyer pour passer à la page suivante"
        }

        floatingView.addView(
            pageIndicator,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(MathPanelConfig.PAGE_INDICATOR_HEIGHT)
            )
        )

        buildResizeHandle()

        windowManager.addView(floatingView, params)
        renderSymbols()
    }

    private fun buildTitleBar() {
        val titleBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(4), 0)
            setBackgroundColor(Color.rgb(45, 45, 55))
        }

        titleText = TextView(this).apply {
            text = "⠿  Mathématiques"
            textSize = MathPanelConfig.TITLE_TEXT_SIZE
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
        }

        // Le déplacement est attaché au titre uniquement afin de ne pas
        // bloquer les clics sur les boutons réduire et fermer.
        setupDrag(titleText)

        titleBar.addView(
            titleText,
            LinearLayout.LayoutParams(
                0,
                dp(MathPanelConfig.TITLE_BAR_HEIGHT),
                1f
            )
        )

        val minimizeButton = Button(this).apply {
            text = "−"
            textSize = 18f
            isAllCaps = false
            setPadding(0, 0, 0, 0)
            setOnClickListener { toggleMinimize() }
        }

        val closeButton = Button(this).apply {
            text = "✕"
            textSize = 16f
            isAllCaps = false
            setPadding(0, 0, 0, 0)
            setOnClickListener { stopSelf() }
        }

        titleBar.addView(
            minimizeButton,
            LinearLayout.LayoutParams(
                dp(MathPanelConfig.TITLE_BUTTON_WIDTH),
                dp(MathPanelConfig.TITLE_BAR_HEIGHT)
            )
        )

        titleBar.addView(
            closeButton,
            LinearLayout.LayoutParams(
                dp(MathPanelConfig.TITLE_BUTTON_WIDTH),
                dp(MathPanelConfig.TITLE_BAR_HEIGHT)
            )
        )

        floatingView.addView(
            titleBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(MathPanelConfig.TITLE_BAR_HEIGHT)
            )
        )
    }

    // --------------------------------------------------
    // Catégories
    // --------------------------------------------------

    private fun buildCategoryBar() {
        categoryArea.removeAllViews()

        categoryNames.forEach { category ->
            val button = Button(this).apply {
                text = when (category) {
                    "Comparaison" -> "Compar."
                    "Ensembles" -> "Ensembles"
                    "Algèbre linéaire" -> "Algèbre"
                    else -> category
                }

                textSize = MathPanelConfig.CATEGORY_TEXT_SIZE
                isAllCaps = false
                setPadding(0, 0, 0, 0)

                setOnClickListener {
                    currentCategory = category
                    currentPage = 0
                    renderSymbols()
                    updateCategoryAppearance()
                    saveState()
                }
            }

            categoryArea.addView(
                button,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(MathPanelConfig.CATEGORY_BUTTON_HEIGHT)
                ).apply {
                    bottomMargin = dp(MathPanelConfig.CATEGORY_MARGIN)
                }
            )
        }

        updateCategoryAppearance()
    }

    private fun updateCategoryAppearance() {
        if (!::categoryArea.isInitialized) return

        for (index in 0 until categoryArea.childCount) {
            val button = categoryArea.getChildAt(index) as Button
            val category = categoryNames[index]

            if (category == currentCategory) {
                button.setBackgroundColor(Color.rgb(55, 105, 165))
                button.setTextColor(Color.WHITE)
            } else {
                button.setBackgroundColor(Color.rgb(225, 225, 225))
                button.setTextColor(Color.DKGRAY)
            }
        }
    }

    private fun symbolsForCurrentCategory(): List<String> {
        return if (currentCategory == "Récents") {
            recentSymbols
        } else {
            categories[currentCategory] ?: emptyList()
        }
    }

    // --------------------------------------------------
    // Affichage des symboles et pagination
    // --------------------------------------------------

    private fun renderSymbols() {
        if (!::contentArea.isInitialized) return

        contentArea.removeAllViews()

        val symbols = symbolsForCurrentCategory()
        val pageCount = max(
            1,
            (symbols.size + MathPanelConfig.SYMBOLS_PER_PAGE - 1) /
                MathPanelConfig.SYMBOLS_PER_PAGE
        )

        currentPage = currentPage.coerceIn(0, pageCount - 1)

        val visibleSymbols = symbols
            .drop(currentPage * MathPanelConfig.SYMBOLS_PER_PAGE)
            .take(MathPanelConfig.SYMBOLS_PER_PAGE)

        visibleSymbols
            .chunked(MathPanelConfig.SYMBOLS_PER_ROW)
            .forEach { rowSymbols ->

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                rowSymbols.forEach { symbol ->
                    val button = Button(this).apply {
                        text = symbol
                        textSize = if (
                            symbol.length > MathPanelConfig.LONG_SYMBOL_LENGTH
                        ) {
                            MathPanelConfig.LONG_SYMBOL_TEXT_SIZE
                        } else {
                            MathPanelConfig.SYMBOL_TEXT_SIZE
                        }
                        isAllCaps = false
                        setPadding(dp(1), 0, dp(1), 0)
                        minHeight = 0
                        minimumHeight = 0
                        setOnClickListener { insertOrCopySymbol(symbol) }
                    }

                    row.addView(
                        button,
                        LinearLayout.LayoutParams(
                            0,
                            dp(MathPanelConfig.SYMBOL_BUTTON_HEIGHT),
                            1f
                        ).apply {
                            setMargins(
                                dp(MathPanelConfig.SYMBOL_MARGIN),
                                dp(MathPanelConfig.SYMBOL_MARGIN),
                                dp(MathPanelConfig.SYMBOL_MARGIN),
                                dp(MathPanelConfig.SYMBOL_MARGIN)
                            )
                        }
                    )
                }

                repeat(
                    MathPanelConfig.SYMBOLS_PER_ROW - rowSymbols.size
                ) {
                    row.addView(
                        View(this),
                        LinearLayout.LayoutParams(0, 1, 1f)
                    )
                }

                contentArea.addView(
                    row,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(MathPanelConfig.SYMBOL_BUTTON_HEIGHT + 2 * MathPanelConfig.SYMBOL_MARGIN)
                    )
                )
            }

        updatePageIndicator(pageCount)
        updateCategoryAppearance()
    }

    private fun updatePageIndicator(pageCount: Int? = null) {
        if (!::pageIndicator.isInitialized) return

        val symbols = symbolsForCurrentCategory()
        val actualPageCount = pageCount ?: max(
            1,
            (symbols.size + MathPanelConfig.SYMBOLS_PER_PAGE - 1) /
                MathPanelConfig.SYMBOLS_PER_PAGE
        )

        pageIndicator.text = when {
            currentCategory == "Récents" && recentSymbols.isEmpty() ->
                "Aucun symbole récent"

            actualPageCount <= 1 ->
                "1 / 1"

            else ->
                (1..actualPageCount).joinToString("  ") { page ->
                    if (page - 1 == currentPage) "●" else "○"
                }
        }
    }

    private fun goToNextPage() {
        val pageCount = max(
            1,
            (symbolsForCurrentCategory().size +
                MathPanelConfig.SYMBOLS_PER_PAGE - 1) /
                MathPanelConfig.SYMBOLS_PER_PAGE
        )

        if (pageCount > 1) {
            currentPage = (currentPage + 1) % pageCount
            renderSymbols()
            saveState()
        }
    }

    /**
     * Balayage horizontal :
     * - change de page dans la catégorie courante ;
     * - si l'on est déjà sur la première/dernière page, passe à la catégorie
     *   précédente/suivante.
     */
    private fun navigateHorizontally(direction: Int) {
        val names = categoryNames
        val pageCount = max(
            1,
            (symbolsForCurrentCategory().size +
                MathPanelConfig.SYMBOLS_PER_PAGE - 1) /
                MathPanelConfig.SYMBOLS_PER_PAGE
        )

        if (direction > 0) {
            if (currentPage < pageCount - 1) {
                currentPage++
            } else {
                val categoryIndex = names.indexOf(currentCategory)
                currentCategory = names[
                    (categoryIndex + 1).coerceAtMost(names.lastIndex)
                ]
                currentPage = 0
            }
        } else {
            if (currentPage > 0) {
                currentPage--
            } else {
                val categoryIndex = names.indexOf(currentCategory)
                currentCategory = names[
                    (categoryIndex - 1).coerceAtLeast(0)
                ]
                val newPageCount = max(
                    1,
                    (symbolsForCurrentCategory().size +
                        MathPanelConfig.SYMBOLS_PER_PAGE - 1) /
                        MathPanelConfig.SYMBOLS_PER_PAGE
                )
                currentPage = newPageCount - 1
            }
        }

        renderSymbols()
        saveState()
    }

    // --------------------------------------------------
    // Insertion et historique
    // --------------------------------------------------

    private fun insertOrCopySymbol(symbol: String) {
        recentSymbols.remove(symbol)
        recentSymbols.add(0, symbol)

        while (recentSymbols.size > MathPanelConfig.MAX_RECENT_SYMBOLS) {
            recentSymbols.removeAt(recentSymbols.lastIndex)
        }

        val inserted = MathAccessibilityService.instance
            ?.insertSymbol(symbol) ?: false

        if (!inserted) {
            Toast.makeText(
                this,
                "Insertion impossible : active le service d'accessibilité et sélectionne un champ de texte.",
                Toast.LENGTH_LONG
            ).show()
        }

        updatePageIndicator()
        saveState()
    }

    // --------------------------------------------------
    // Déplacement du panneau
    // --------------------------------------------------

    private fun setupDrag(dragView: View) {
        dragView.setOnTouchListener(
            object : View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var initialTouchX = 0f
                private var initialTouchY = 0f

                override fun onTouch(view: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = params.x
                            initialY = params.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            return true
                        }

                        MotionEvent.ACTION_MOVE -> {
                            params.x = initialX +
                                (event.rawX - initialTouchX).toInt()
                            params.y = initialY +
                                (event.rawY - initialTouchY).toInt()

                            windowManager.updateViewLayout(floatingView, params)
                            return true
                        }

                        MotionEvent.ACTION_UP,
                        MotionEvent.ACTION_CANCEL -> {
                            saveState()
                            return true
                        }
                    }
                    return false
                }
            }
        )
    }

    // --------------------------------------------------
    // Redimensionnement
    // --------------------------------------------------

    private fun buildResizeHandle() {
        val handle = TextView(this).apply {
            text = "◢"
            textSize = 11f
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            setPadding(dp(2), 0, dp(2), 0)
            setTextColor(Color.DKGRAY)
        }

        setupResize(handle)

        floatingView.addView(
            handle,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(MathPanelConfig.RESIZE_HANDLE_HEIGHT)
            )
        )
    }

    private fun setupResize(resizeView: View) {
        resizeView.setOnTouchListener(
            object : View.OnTouchListener {
                private var startWidth = 0
                private var startHeight = 0
                private var startX = 0f
                private var startY = 0f

                override fun onTouch(view: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            startWidth = params.width
                            startHeight = params.height
                            startX = event.rawX
                            startY = event.rawY
                            return true
                        }

                        MotionEvent.ACTION_MOVE -> {
                            val newWidth = startWidth +
                                (event.rawX - startX).toInt()
                            val newHeight = startHeight +
                                (event.rawY - startY).toInt()

                            params.width = min(
                                max(newWidth, dp(MathPanelConfig.MIN_WIDTH)),
                                dp(MathPanelConfig.MAX_WIDTH)
                            )
                            params.height = min(
                                max(newHeight, dp(MathPanelConfig.MIN_HEIGHT)),
                                dp(MathPanelConfig.MAX_HEIGHT)
                            )
                            panelWidth = params.width
                            panelHeight = params.height

                            windowManager.updateViewLayout(floatingView, params)
                            return true
                        }

                        MotionEvent.ACTION_UP,
                        MotionEvent.ACTION_CANCEL -> {
                            saveState()
                            return true
                        }
                    }
                    return false
                }
            }
        )
    }

    // --------------------------------------------------
    // Réduction / restauration
    // --------------------------------------------------

    private fun toggleMinimize() {
        isMinimized = !isMinimized

        mainArea.visibility = if (isMinimized) View.GONE else View.VISIBLE
        pageIndicator.visibility = if (isMinimized) View.GONE else View.VISIBLE

        // Le bouton de réduction reste accessible dans la barre de titre.
        params.height = if (isMinimized) {
            dp(MathPanelConfig.TITLE_BAR_HEIGHT +
                MathPanelConfig.PANEL_PADDING * 2)
        } else {
            panelHeight
        }

        windowManager.updateViewLayout(floatingView, params)
        saveState()
    }

    // --------------------------------------------------
    // Nettoyage
    // --------------------------------------------------

    override fun onDestroy() {
        if (::params.isInitialized && ::preferences.isInitialized) {
            saveState()
        }

        if (::floatingView.isInitialized && floatingView.isAttachedToWindow) {
            windowManager.removeView(floatingView)
        }

        super.onDestroy()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    /**
     * Layout qui intercepte uniquement les gestes horizontaux suffisamment
     * longs. Les clics normaux sur les boutons restent fonctionnels.
     */
    private inner class SwipeableLinearLayout(context: Context) :
        LinearLayout(context) {

        var onSwipe: ((Int) -> Unit)? = null

        private var downX = 0f
        private var downY = 0f
        private var swipeHandled = false

        override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    swipeHandled = false
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.x - downX
                    val deltaY = event.y - downY

                    if (
                        abs(deltaX) > dp(MathPanelConfig.SWIPE_DISTANCE) &&
                        abs(deltaX) > abs(deltaY) * 1.2f
                    ) {
                        swipeHandled = true
                        return true
                    }
                }
            }
            return super.onInterceptTouchEvent(event)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_UP -> {
                    if (swipeHandled) {
                        val deltaX = event.x - downX
                        if (abs(deltaX) > dp(MathPanelConfig.SWIPE_VELOCITY)) {
                            onSwipe?.invoke(if (deltaX < 0) 1 else -1)
                        }
                        performClick()
                        swipeHandled = false
                        return true
                    }
                }

                MotionEvent.ACTION_CANCEL -> {
                    swipeHandled = false
                }
            }
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }
}

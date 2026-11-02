package com.example.claviermathematique

import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min

class FloatingMathService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: LinearLayout
    private lateinit var contentArea: LinearLayout
    private lateinit var pageIndicator: TextView

    private lateinit var params: WindowManager.LayoutParams
    private lateinit var preferences: SharedPreferences

    private var currentCategory = "Calcul"
    private var currentPage = 0

    private var panelWidth = 700
    private var panelHeight = 420

    private val recentSymbols = mutableListOf<String>()

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

        "Algèbre" to listOf(
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

        windowManager =
            getSystemService(WINDOW_SERVICE) as WindowManager

        preferences =
            getSharedPreferences("math_panel", Context.MODE_PRIVATE)

        loadState()
        createFloatingPanel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun loadState() {
        panelWidth = preferences.getInt("width", dp(700))
        panelHeight = preferences.getInt("height", dp(420))
    }

    private fun createFloatingPanel() {

        floatingView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
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
            x = preferences.getInt("x", 50)
            y = preferences.getInt("y", 200)
        }

        buildTitleBar()
        buildCategoryBar()

        contentArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        floatingView.addView(
            contentArea,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        pageIndicator = TextView(this).apply {
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(4), 0, dp(4))
        }

        floatingView.addView(
            pageIndicator,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(28)
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

        val title = TextView(this).apply {
            text = "⠿  Mathématiques"
            textSize = 17f
            setTextColor(Color.WHITE)
        }

        titleBar.addView(
            title,
            LinearLayout.LayoutParams(
                0,
                dp(44),
                1f
            )
        )

        val minimize = Button(this).apply {
            text = "−"
            textSize = 18f
            setOnClickListener {
                toggleMinimize()
            }
        }

        val close = Button(this).apply {
            text = "✕"
            textSize = 16f
            setOnClickListener {
                stopSelf()
            }
        }

        titleBar.addView(
            minimize,
            LinearLayout.LayoutParams(dp(48), dp(44))
        )

        titleBar.addView(
            close,
            LinearLayout.LayoutParams(dp(48), dp(44))
        )

        setupDrag(titleBar)

        floatingView.addView(
            titleBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44)
            )
        )
    }

    private fun buildCategoryBar() {

        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val allCategories =
            listOf("Récents") + categories.keys.toList()

        allCategories.forEach { category ->

            val button = Button(this).apply {
                text = category
                textSize = 12f
                isAllCaps = false

                setOnClickListener {
                    currentCategory = category
                    currentPage = 0
                    renderSymbols()
                }
            }

            row.addView(
                button,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(40)
                )
            )
        }

        scroll.addView(row)

        floatingView.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(42)
            )
        )
    }

    private fun symbolsForCurrentCategory(): List<String> {

        return if (currentCategory == "Récents") {
            recentSymbols
        } else {
            categories[currentCategory] ?: emptyList()
        }
    }

    private fun renderSymbols() {

        if (!::contentArea.isInitialized) return

        contentArea.removeAllViews()

        val symbols = symbolsForCurrentCategory()

        val symbolsPerPage = 30
        val pageCount =
            max(1, (symbols.size + symbolsPerPage - 1) / symbolsPerPage)

        currentPage =
            currentPage.coerceIn(0, pageCount - 1)

        val visibleSymbols =
            symbols.drop(currentPage * symbolsPerPage)
                .take(symbolsPerPage)

        visibleSymbols.chunked(10).forEach { rowSymbols ->

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            rowSymbols.forEach { symbol ->

                val button = Button(this).apply {
                    text = symbol
                    textSize =
                        if (symbol.length > 4) 13f else 18f

                    isAllCaps = false

                    setOnClickListener {
                        copySymbol(symbol)
                    }
                }

                row.addView(
                    button,
                    LinearLayout.LayoutParams(
                        0,
                        dp(50),
                        1f
                    ).apply {
                        setMargins(dp(2), dp(2), dp(2), dp(2))
                    }
                )
            }

            repeat(10 - rowSymbols.size) {

                row.addView(
                    View(this),
                    LinearLayout.LayoutParams(
                        0,
                        dp(50),
                        1f
                    )
                )
            }

            contentArea.addView(row)
        }

        updatePageIndicator(pageCount)
    }

    private fun updatePageIndicator(pageCount: Int) {

        val circles = buildString {

            for (i in 0 until pageCount) {

                append(
                    if (i == currentPage) "● "
                    else "○ "
                )
            }
        }

        pageIndicator.text =
            if (currentCategory == "Récents" &&
                recentSymbols.isEmpty()
            ) {
                "Aucun symbole récent"
            } else {
                circles.trim()
            }
    }

    private fun copySymbol(symbol: String) {

        val clipboard =
            getSystemService(Context.CLIPBOARD_SERVICE)
                    as ClipboardManager

        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                "Symbole mathématique",
                symbol
            )
        )

        recentSymbols.remove(symbol)
        recentSymbols.add(0, symbol)

        if (recentSymbols.size > 30) {
            recentSymbols.removeLast()
        }

        android.widget.Toast.makeText(
            this,
            "$symbol copié",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    private fun setupDrag(dragView: View) {

        dragView.setOnTouchListener(
            object : View.OnTouchListener {

                private var initialX = 0
                private var initialY = 0

                private var initialTouchX = 0f
                private var initialTouchY = 0f

                override fun onTouch(
                    view: View,
                    event: MotionEvent
                ): Boolean {

                    when (event.action) {

                        MotionEvent.ACTION_DOWN -> {

                            initialX = params.x
                            initialY = params.y

                            initialTouchX = event.rawX
                            initialTouchY = event.rawY

                            return true
                        }

                        MotionEvent.ACTION_MOVE -> {

                            params.x =
                                initialX +
                                (event.rawX - initialTouchX).toInt()

                            params.y =
                                initialY +
                                (event.rawY - initialTouchY).toInt()

                            windowManager.updateViewLayout(
                                floatingView,
                                params
                            )

                            return true
                        }

                        MotionEvent.ACTION_UP -> {

                            saveState()

                            return true
                        }
                    }

                    return false
                }
            }
        )
    }

    private fun buildResizeHandle() {

        val handle = TextView(this).apply {
            text = "◢"
            textSize = 22f
            gravity = Gravity.BOTTOM or Gravity.END
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }

        setupResize(handle)

        floatingView.addView(
            handle,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(30)
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

                override fun onTouch(
                    view: View,
                    event: MotionEvent
                ): Boolean {

                    when (event.action) {

                        MotionEvent.ACTION_DOWN -> {

                            startWidth = params.width
                            startHeight = params.height

                            startX = event.rawX
                            startY = event.rawY

                            return true
                        }

                        MotionEvent.ACTION_MOVE -> {

                            val newWidth =
                                startWidth +
                                (event.rawX - startX).toInt()

                            val newHeight =
                                startHeight +
                                (event.rawY - startY).toInt()

                            params.width =
                                min(max(newWidth, dp(300)), dp(1000))

                            params.height =
                                min(max(newHeight, dp(180)), dp(800))

                            windowManager.updateViewLayout(
                                floatingView,
                                params
                            )

                            return true
                        }

                        MotionEvent.ACTION_UP -> {

                            saveState()

                            return true
                        }
                    }

                    return false
                }
            }
        )
    }

    private fun toggleMinimize() {

        if (contentArea.visibility == View.VISIBLE) {

            contentArea.visibility = View.GONE
            pageIndicator.visibility = View.GONE

        } else {

            contentArea.visibility = View.VISIBLE
            pageIndicator.visibility = View.VISIBLE
        }
    }

    private fun saveState() {

        preferences.edit()
            .putInt("x", params.x)
            .putInt("y", params.y)
            .putInt("width", params.width)
            .putInt("height", params.height)
            .apply()
    }

    override fun onDestroy() {

        saveState()

        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }

        super.onDestroy()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}

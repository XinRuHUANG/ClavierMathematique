package com.example.claviermathematique

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class MathAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: MathAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    /**
     * Insère le symbole au curseur du champ actuellement focalisé.
     * ACTION_SET_TEXT insère d'abord le symbole sans presse-papiers.
     * Si le champ ne prend pas en charge cette action, ACTION_PASTE
     * est déclenchée automatiquement : aucun collage manuel n'est requis.
     */
    fun insertSymbol(symbol: String): Boolean {
        val initialNode = rootInActiveWindow?.findFocus(
            AccessibilityNodeInfo.FOCUS_INPUT
        ) ?: return false

        if (!initialNode.isEditable || initialNode.isPassword) return false

        val oldText = initialNode.text?.toString() ?: return false

        val rawStart = initialNode.textSelectionStart
        val rawEnd = initialNode.textSelectionEnd

        val start = if (rawStart >= 0) {
            rawStart.coerceIn(0, oldText.length)
        } else {
            oldText.length
        }

        val end = if (rawEnd >= 0) {
            rawEnd.coerceIn(start, oldText.length)
        } else {
            start
        }

        val updatedText =
            oldText.substring(0, start) +
            symbol +
            oldText.substring(end)

        val cursorPosition = start + symbol.length

        val setTextArgs = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                updatedText
            )
        }

        if (!initialNode.performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                setTextArgs
            )
        ) {
            // Repli automatique par collage pour les champs incompatibles.
            val clipboard =
                getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

            clipboard.setPrimaryClip(
                ClipData.newPlainText("Symbole mathématique", symbol)
            )

            return initialNode.performAction(
                AccessibilityNodeInfo.ACTION_PASTE
            )
        }

        // Récupérer un nouveau nœud après la modification du texte.
        val updatedNode = rootInActiveWindow?.findFocus(
            AccessibilityNodeInfo.FOCUS_INPUT
        ) ?: return true

        val selectionArgs = Bundle().apply {
            putInt(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT,
                cursorPosition
            )
            putInt(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT,
                cursorPosition
            )
        }

        updatedNode.performAction(
            AccessibilityNodeInfo.ACTION_SET_SELECTION,
            selectionArgs
        )

        return true
    }

}

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
        val node = rootInActiveWindow?.findFocus(
            AccessibilityNodeInfo.FOCUS_INPUT
        ) ?: return false

        if (!node.isEditable || node.isPassword) return false

        // Vérifier que le champ possède bien une sélection valide.
        val text = node.text?.toString() ?: return false
        val selectionStart = node.textSelectionStart
        val selectionEnd = node.textSelectionEnd

        if (selectionStart < 0 || selectionEnd < 0) {
            return false
        }

        // Préparer le symbole dans le presse-papiers.
        val clipboard =
            getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        clipboard.setPrimaryClip(
            ClipData.newPlainText("Symbole mathématique", symbol)
        )

        // Android insère le symbole à la position du curseur.
        // Si un texte est sélectionné, il est remplacé.
        val pasted = node.performAction(
            AccessibilityNodeInfo.ACTION_PASTE
        )

        if (!pasted) {
            return false
        }

        return true
    }


}

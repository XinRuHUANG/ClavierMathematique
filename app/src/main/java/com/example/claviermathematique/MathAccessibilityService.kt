package com.example.claviermathematique

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo

class MathAccessibilityService : AccessibilityService() {

    companion object {
        var instance: MathAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun insertSymbol(symbol: String): Boolean {
        val node = rootInActiveWindow?.findFocus(
            AccessibilityNodeInfo.FOCUS_INPUT
        ) ?: return false

        if (!node.isEditable) return false

        val oldText = node.text?.toString() ?: ""
        val start = node.textSelectionStart
            .coerceIn(0, oldText.length)
        val end = node.textSelectionEnd
            .coerceIn(start, oldText.length)

        val newText = oldText.substring(0, start) +
                symbol +
                oldText.substring(end)

        val setText = android.os.Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                newText
            )
        }

        if (!node.performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                setText
            )
        ) return false

        val newCursor = start + symbol.length

        val selection = android.os.Bundle().apply {
            putInt(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT,
                newCursor
            )
            putInt(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT,
                newCursor
            )
        }

        node.performAction(
            AccessibilityNodeInfo.ACTION_SET_SELECTION,
            selection
        )

        return true
    }
}
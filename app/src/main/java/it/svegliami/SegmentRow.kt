package it.svegliami

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Riga di pulsanti affiancati della stessa larghezza, tutti su una riga sola.
 * Con [multi] falso se ne seleziona uno (come i radio button), altrimenti più d'uno.
 */
class SegmentRow @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : LinearLayout(ctx, attrs) {
    var multi = false
    var onChange: (() -> Unit)? = null
    private val values = mutableListOf<Int>()
    private val buttons = mutableListOf<TextView>()

    init {
        orientation = HORIZONTAL
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    fun setOptions(options: List<Pair<String, Int>>, selected: Set<Int>) {
        removeAllViews(); values.clear(); buttons.clear()
        options.forEachIndexed { i, (label, value) ->
            val b = TextView(context).apply {
                text = label
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(context.getColorStateList(R.color.segment_text))
                setBackgroundResource(R.drawable.bg_segment)
                isSelected = value in selected
                minHeight = dp(44)
                maxLines = 1
                setOnClickListener { toggle(this) }
            }
            addView(b, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { if (i > 0) marginStart = dp(5) })
            values += value; buttons += b
        }
    }

    private fun toggle(b: TextView) {
        if (multi) b.isSelected = !b.isSelected
        else {
            if (b.isSelected) return
            buttons.forEach { it.isSelected = it === b }
        }
        onChange?.invoke()
    }

    fun selected(): Set<Int> = values.filterIndexed { i, _ -> buttons[i].isSelected }.toSet()
    fun selectedOne(default: Int): Int = selected().firstOrNull() ?: default
}

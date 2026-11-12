package it.svegliami

import android.graphics.Color
import android.view.ViewGroup
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Layout a tutto schermo (edge-to-edge), obbligatorio con targetSdk 36 su Android 16.
 * Lo sfondo della schermata passa sotto barra di stato e barra di navigazione, mentre il
 * contenuto riceve un margine pari alle barre (e alla tastiera quando è aperta).
 * Va chiamata subito dopo setContentView.
 */
object SystemBars {
    fun apply(activity: AppCompatActivity) {
        // Icone chiare in ogni caso: il tema dell'app è sempre scuro, anche col telefono in modalità chiara.
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val left = root.paddingLeft
        val top = root.paddingTop
        val right = root.paddingRight
        val bottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + maxOf(bars.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }
    }
}

package it.svegliami

import android.app.Activity
import android.content.Intent
import com.google.android.material.bottomnavigation.BottomNavigationView

object NavigationUi {
    fun bind(activity: Activity, navigation: BottomNavigationView, selectedId: Int) {
        markSelected(navigation, selectedId)
        navigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navHome -> if (activity is MainActivity) activity.scrollToTop() else launchMain(activity)
                R.id.navSleep -> launch(activity, SleepActivity::class.java)
                R.id.navInsights -> launch(activity, InsightsActivity::class.java)
                R.id.navJournal -> launch(activity, JournalActivity::class.java)
                R.id.navSettings -> launch(activity, SettingsActivity::class.java)
                else -> return@setOnItemSelectedListener false
            }
            true
        }
        navigation.setOnItemReselectedListener { item ->
            if (activity is MainActivity) when (item.itemId) {
                R.id.navHome -> activity.scrollToTop()
            }
        }
    }

    /**
     * Evidenzia una voce senza eseguirne l'azione: assegnare selectedItemId farebbe scattare
     * i listener (che aprono un'altra schermata o riportano in cima).
     */
    fun markSelected(navigation: BottomNavigationView, id: Int) {
        navigation.menu.findItem(id)?.isChecked = true
    }

    private fun launch(activity: Activity, destination: Class<*>) {
        if (activity.javaClass == destination) return
        activity.startActivity(Intent(activity, destination))
        if (activity !is MainActivity) activity.finish()
    }

    /** Torna alla Home chiudendo le schermate aperte sopra. */
    private fun launchMain(activity: Activity) {
        val intent = Intent(activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        activity.startActivity(intent)
        if (activity !is MainActivity) activity.finish()
    }
}

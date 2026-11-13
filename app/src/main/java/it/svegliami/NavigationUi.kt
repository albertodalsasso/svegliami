package it.svegliami

import android.app.Activity
import android.content.Intent
import com.google.android.material.bottomnavigation.BottomNavigationView

object NavigationUi {
    const val EXTRA_OPEN_SETTINGS = "it.svegliami.OPEN_SETTINGS"

    fun bind(activity: Activity, navigation: BottomNavigationView, selectedId: Int) {
        navigation.selectedItemId = selectedId
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

    private fun launch(activity: Activity, destination: Class<*>) {
        if (activity.javaClass == destination) return
        activity.startActivity(Intent(activity, destination))
        if (activity !is MainActivity) activity.finish()
    }

    private fun launchMain(activity: Activity, openSettings: Boolean = false) {
        val intent = Intent(activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (openSettings) intent.putExtra(EXTRA_OPEN_SETTINGS, true)
        activity.startActivity(intent)
        if (activity !is MainActivity) activity.finish()
    }
}

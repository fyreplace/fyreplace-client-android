package app.fyreplace.client.data.repositories

import android.content.SharedPreferences
import androidx.core.content.edit
import app.fyreplace.client.data.services.WildFyreService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AreaRepository(
    private val wildFyre: WildFyreService,
    private val preferences: SharedPreferences
) {
    var preferredAreaName: String
        get() = preferences.getString(PREFS_KEY_AREA_PREFERRED, "").orEmpty()
        set(value) = preferences.edit { putString(PREFS_KEY_AREA_PREFERRED, value) }

    suspend fun getAreas() = withContext(Dispatchers.IO) { wildFyre.getAreas() }

    suspend fun getAreaReputation() =
        withContext(Dispatchers.IO) { wildFyre.getAreaRep(preferredAreaName) }

    private companion object {
        const val PREFS_KEY_AREA_PREFERRED = "area.preferred"
    }
}

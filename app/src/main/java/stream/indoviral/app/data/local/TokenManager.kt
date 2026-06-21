package stream.indoviral.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import stream.indoviral.app.domain.model.User
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "indoviral_prefs")

@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_TOKEN = stringPreferencesKey("token")
        private val KEY_USER_ID = intPreferencesKey("user_id")
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_AVATAR = stringPreferencesKey("avatar")
    }

    val token: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_TOKEN]
    }

    val user: Flow<User?> = context.dataStore.data.map { prefs ->
        val id = prefs[KEY_USER_ID] ?: return@map null
        val username = prefs[KEY_USERNAME] ?: return@map null
        User(id, username, prefs[KEY_AVATAR])
    }

    suspend fun saveAuth(token: String, user: User) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TOKEN] = token
            prefs[KEY_USER_ID] = user.id
            prefs[KEY_USERNAME] = user.username
            if (user.avatar != null) prefs[KEY_AVATAR] = user.avatar
            else prefs.remove(KEY_AVATAR)
        }
    }

    suspend fun updateAvatar(avatarPath: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AVATAR] = avatarPath
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}

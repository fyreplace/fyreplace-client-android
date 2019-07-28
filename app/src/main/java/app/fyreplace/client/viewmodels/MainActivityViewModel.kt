package app.fyreplace.client.viewmodels

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.viewModelScope
import app.fyreplace.client.Constants
import app.fyreplace.client.R
import app.fyreplace.client.data.models.Author
import app.fyreplace.client.data.models.Post
import app.fyreplace.client.data.repositories.AuthRepository
import app.fyreplace.client.data.repositories.AuthorRepository
import app.fyreplace.client.data.repositories.NotificationRepository
import app.fyreplace.client.data.repositories.SettingsRepository
import kotlinx.coroutines.*
import java.text.SimpleDateFormat

class MainActivityViewModel(application: Application) : FailureHandlingViewModel(application) {
    private var uiRefreshTickerJob: Job? = null
    private val mUiRefreshTick = MutableLiveData<Unit>()
    private val mIsLogged = MutableLiveData<Boolean>()
    private val mSelf = MutableLiveData<Author?>()
    private val mUserAvatarNewData = MutableLiveData<ByteArray>()
    private val mNotificationCount = MutableLiveData<Int>()
    private val mNotificationBadgeVisible = MutableLiveData<Boolean>()
    private val mPostInfo = MutableLiveData<PostInfo?>()
    private var userAvatarFileName: String? = null
    private var userAvatarMimeType: String? = null

    val uiRefreshTick: LiveData<Unit> = mUiRefreshTick
    var startupLogin = true
        private set
    val isLogged: LiveData<Boolean> = mIsLogged
    val userId: LiveData<Long> = Transformations.map(mSelf) { it?.user ?: -1 }
    val userName: LiveData<String> = Transformations.map(mSelf) { it?.name.orEmpty() }
    val userBio: LiveData<String> = Transformations.map(mSelf) { it?.bio.orEmpty() }
    val userAvatar: LiveData<String> = Transformations.map(mSelf) { it?.avatar.orEmpty() }
    val userAvatarNewData: LiveData<ByteArray> = mUserAvatarNewData
    val notificationCount: LiveData<Int> = mNotificationCount
    val notificationCountText: LiveData<String> =
        Transformations.map(notificationCount) { if (it < 100) it.toString() else "99" }
    val notificationBadgeVisible: LiveData<Boolean> = mNotificationBadgeVisible
    val postInfo: LiveData<PostInfo?> = mPostInfo
    val selectedThemeIndex = MutableLiveData<Int>()
    val shouldShowNotificationBadge = MutableLiveData<Boolean>()

    init {
        if (AuthRepository.authToken.isNotEmpty()) {
            login()
        } else {
            mIsLogged.value = false
        }

        selectedThemeIndex.value = THEMES.indexOfFirst { it == SettingsRepository.theme }
        selectedThemeIndex.observeForever { SettingsRepository.theme = THEMES[it] }
        shouldShowNotificationBadge.value = SettingsRepository.showBadge
        shouldShowNotificationBadge.observeForever { SettingsRepository.showBadge = it }
    }

    fun login() {
        uiRefreshTickerJob = viewModelScope.launch {
            while (true) {
                delay(UI_UPDATE_MILLIS)
                mUiRefreshTick.postValue(Unit)
            }
        }
        mIsLogged.value = true
        updateProfileInfoAsync()
    }

    fun logout() {
        startupLogin = false
        mIsLogged.value = false
        mSelf.value = null
        uiRefreshTickerJob?.cancel()
        AuthRepository.clearAuthToken()
    }

    fun updateNotificationCountAsync() = launchCatching(Dispatchers.IO) {
        mNotificationCount.postValue(NotificationRepository.getNotificationCount())
    }

    fun forceNotificationCount(count: Int) = mNotificationCount.postValue(count)

    fun setProfileAsync(bio: String) = launchCatching {
        if (bio != userBio.value) {
            mSelf.postValue(withContext(Dispatchers.IO) { AuthorRepository.updateSelfBio(bio) })
        }

        if (userAvatarNewData.value != null && userAvatarFileName != null && userAvatarMimeType != null) {
            withContext(Dispatchers.IO) {
                mSelf.postValue(
                    AuthorRepository.updateSelfAvatar(
                        userAvatarFileName!!,
                        userAvatarMimeType!!,
                        userAvatarNewData.value!!
                    )
                )
            }

            resetPendingProfileAvatar()
        }
    }

    fun setPendingProfileAvatar(fileName: String, mimeType: String, avatar: ByteArray) {
        userAvatarFileName = fileName
        userAvatarMimeType = mimeType
        mUserAvatarNewData.postValue(avatar)
    }

    fun resetPendingProfileAvatar() {
        userAvatarFileName = null
        userAvatarMimeType = null
        mUserAvatarNewData.postValue(null)
    }

    fun setNotificationBadgeVisible(visible: Boolean) = mNotificationBadgeVisible.postValue(visible)

    fun setPost(post: Post?) = mPostInfo.postValue(post?.let { PostInfo(it.author, DATE_FORMAT.format(it.created)) })

    private fun updateProfileInfoAsync() = viewModelScope.launch {
        updateNotificationCountAsync()
        mSelf.postValue(AuthorRepository.getSelf())
    }

    companion object {
        private const val UI_UPDATE_MILLIS = 10_000L
        private val DATE_FORMAT = SimpleDateFormat.getDateInstance()

        val THEMES = arrayOf(
            Constants.Themes.AUTOMATIC,
            Constants.Themes.LIGHT,
            Constants.Themes.DARK
        )

        val NAVIGATION_LINKS = mapOf(
            R.id.fyreplace_website to Constants.Links.Fyreplace.WEBSITE,
            R.id.fyreplace_open_source to Constants.Links.Fyreplace.OPEN_SOURCE,
            R.id.wildfyre_website to Constants.Links.WildFyre.WEBSITE,
            R.id.wildfyre_open_source to Constants.Links.WildFyre.OPEN_SOURCE,
            R.id.wildfyre_faq to Constants.Links.WildFyre.FAQ,
            R.id.wildfyre_terms_and_conditions to Constants.Links.WildFyre.TERMS_AND_CONDITIONS,
            R.id.wildfyre_privacy_policy to Constants.Links.WildFyre.PRIVACY_POLICY
        )
    }

    data class PostInfo(
        val author: Author?,
        val date: String
    )
}

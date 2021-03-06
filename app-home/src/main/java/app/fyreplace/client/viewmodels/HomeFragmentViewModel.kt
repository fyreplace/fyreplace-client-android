package app.fyreplace.client.viewmodels

import androidx.lifecycle.viewModelScope
import app.fyreplace.client.data.models.Post
import app.fyreplace.client.data.repositories.AreaRepository
import app.fyreplace.client.data.repositories.CommentRepository
import app.fyreplace.client.data.repositories.PostRepository
import kotlinx.coroutines.*
import retrofit2.HttpException
import kotlin.coroutines.coroutineContext

class HomeFragmentViewModel(
    areaRepository: AreaRepository,
    commentRepository: CommentRepository,
    private val postRepository: PostRepository
) : PostFragmentViewModel(areaRepository, commentRepository, postRepository) {
    private val postReserve: MutableList<Post> = mutableListOf()
    private var fetchJob: Job? = null
    private var endOfPosts = false
    private var lastAreaName: String? = null

    suspend fun nextPost(areaName: String? = null, force: Boolean = false) {
        if (areaName == lastAreaName) {
            return
        }

        val forced = areaName != null || force

        if (!forced && lastAreaName == null) {
            return
        }

        if (forced) {
            if (areaName != null) {
                lastAreaName = areaName
            }

            setPost(null)
            mHasContent.postValue(true)
            fetchJob?.cancel()
            postReserve.clear()
        }

        if (postReserve.isEmpty()) {
            setPost(null)
            fillReserve()
        }

        if (endOfPosts) {
            setPost(null)
            return
        }

        setPost(postReserve.removeAt(0))

        if (postReserve.isEmpty()) {
            fetchJob = viewModelScope.launch(Dispatchers.IO) { fetchPosts() }
        }

        viewModelScope.launch(Dispatchers.IO) {
            delay(SPREAD_DELAY)
            mAllowSpread.postValue(contentLoaded.value)
        }
    }

    suspend fun spread(spread: Boolean) {
        mAllowSpread.postValue(false)
        var forceReload = false

        try {
            postRepository.spread(postAreaName, postId, spread)
        } catch (e: Exception) {
            mAllowSpread.postValue(true)

            if (e is HttpException && e.code() == 403) {
                forceReload = true
            } else {
                throw e
            }
        } finally {
            nextPost(force = forceReload)
        }
    }

    private suspend fun fillReserve() {
        endOfPosts = false

        try {
            fetchJob?.join()
        } catch (e: Exception) {
            // Don't cancel everything just because the background fetch failed
        }

        while (!endOfPosts && postReserve.isEmpty()) {
            fetchPosts()
        }
    }

    private suspend fun fetchPosts() {
        val superPost = postRepository.getNextPosts(RESERVE_SIZE)

        if (superPost.count == 0) {
            mHasContent.postValue(false)
            endOfPosts = true
        } else if (coroutineContext.isActive) {
            mHasContent.postValue(true)
            postReserve.addAll(superPost.results.filter { p -> p.id != postId })
        }
    }

    private companion object {
        const val RESERVE_SIZE = 10
        const val SPREAD_DELAY = 500L
    }
}

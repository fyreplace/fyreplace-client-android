package net.wildfyre.client.data

import net.wildfyre.client.Constants
import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*


/**
 * Object containing services used by the different repositories.
 */
object Services {
    /**
     * Service connecting to the WildFyre API.
     */
    val webService: WebService = Retrofit.Builder()
        .baseUrl(Constants.Api.BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(WebService::class.java)
}

/**
 * Helper function lifting some boilerplate code out of the repositories and hooking errors to a [FailureHandler].
 *
 * @param T The type of the object that is going to be received by the callback if the operation succeeds
 * @param failureHandler An object capable of propagating any error
 * @param callback The callback to run when the operation succeeds
 */
fun <T> Call<T>.then(failureHandler: FailureHandler, callback: (result: T) -> Unit) =
    enqueue(DefaultCallback<T>(failureHandler) { callback(it) })

/**
 * @see then
 */
fun Call<Unit>.then(failureHandler: FailureHandler, callback: () -> Unit) =
    enqueue(NoResultCallback(failureHandler) { callback() })

fun <T> Response<T>.toResult(): T? = if (isSuccessful) body() else null

open class DefaultCallback<T>(
    private val failureHandler: FailureHandler,
    private val action: (result: T) -> Unit
) : Callback<T> {
    override fun onResponse(call: Call<T>, response: Response<T>) = if (response.isSuccessful) {
        getResult(response)?.let(action) ?: onFailure(call, ApiNoResultException())
    } else {
        val body = response.errorBody()?.use { it.charStream().readText() } ?: "<no body>"
        onFailure(call, ApiCallException(response.code(), response.message(), body))
    }

    override fun onFailure(call: Call<T>, t: Throwable) = failureHandler.onFailure(t)

    protected open fun getResult(response: Response<T>): T? = response.body()
}

class NoResultCallback(
    failureHandler: FailureHandler,
    action: (result: Unit) -> Unit
) :
    DefaultCallback<Unit>(failureHandler, action) {
    override fun getResult(response: Response<Unit>): Unit? = Unit
}

class ApiCallException(code: Int, message: String, body: String) : Exception("$code: $message\n\t$body")

class ApiNoResultException : Exception("No body result received")

/**
 * Retrofit implementation of the WildFyre API.
 */
interface WebService {
    // Authentication

    @POST("/account/auth/")
    @Headers("Content-Type: application/json")
    fun postAuth(
        @Body auth: Auth
    ): Call<AuthToken>


    // Account

    @GET("/account/")
    fun getAccount(
        @Header("Authorization") authorization: String
    ): Call<Account>

    @GET("/bans/")
    fun getBans(
        @Header("Authorization/") authorization: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int
    ): Call<SuperBan>

    @GET("/users/")
    fun getSelf(
        @Header("Authorization") authorization: String
    ): Call<Author>

    @GET("/users/{userId}/")
    fun getUser(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: Long
    ): Call<Author>

    @PUT("/users/")
    @Multipart
    fun putAvatar(
        @Header("Authorization") authorization: String,
        @Part avatar: MultipartBody.Part // name = "avatar"
    ): Call<Author>

    @PATCH("/users/")
    @Headers("Content-Type: application/json")
    fun patchBio(
        @Header("Authorization") authorization: String,
        @Body bio: AuthorPatch
    ): Call<Author>

    @PATCH("/account/")
    @Headers("Content-Type: application/json")
    fun patchAccount(
        @Header("Authorization") authorization: String,
        @Body accountPatch: AccountPatch
    ): Call<Unit>


    // Registration

    @POST("/account/register/")
    @Headers("Content-Type: application/json")
    fun postRegistration(
        @Body recover: Registration
    ): Call<RegistrationResult>

    @POST("/account/recover/")
    @Headers("Content-Type: application/json")
    fun postRecovery(
        @Body recovery: PasswordRecoveryStep1
    ): Call<RecoverTransaction>

    @POST("/account/recover/reset/")
    @Headers("Content-Type: application/json")
    fun postRecovery(
        @Body recovery: PasswordRecoveryStep2
    ): Call<Reset>

    @POST("/account/recover/reset/")
    @Headers("Content-Type: application/json")
    fun postRecovery(
        @Body recovery: UsernameRecovery
    ): Call<RecoverTransaction>


    // Flags

    @GET("/choices/flag/reasons/")
    fun getFlagReasons(
        @Header("Authorization") authorization: String
    ): Call<List<Choice>>

    @POST("/areas/{areaName}/{postId}/flag/")
    @Headers("Content-Type: application/json")
    fun postFlag(
        @Header("Authorization") authorization: String,
        @Path("areaName") areaName: String,
        @Path("postId") postId: Long,
        @Body flag: Flag
    ): Call<Unit>

    @POST("/areas/{areaName}/{postId}/{commentId}/flag/")
    @Headers("Content-Type: application/json")
    fun postFlag(
        @Header("Authorization") authorization: String,
        @Path("areaName") areaName: String,
        @Path("postId") postId: Long,
        @Path("commentId") commentId: Long,
        @Body flag: Flag
    ): Call<Unit>


    // Notifications

    @GET("/areas/{areaName}/subscribed/")
    fun getPosts(
        @Header("Authorization") authorization: String,
        @Path("areaName") areaName: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int
    ): Call<SuperPost>

    @GET("/areas/notification/")
    fun getNotifications(
        @Header("Authorization") authorization: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int
    ): Call<SuperNotification>

    @DELETE("/areas/notification/")
    fun deleteNotifications(
        @Header("Authorization") authorization: String
    ): Call<Unit>


    // Areas

    @GET("/areas/{areaName}/rep/")
    fun getAreaRep(
        @Header("Authorization") authorization: String,
        @Path("areaName") areaName: String
    ): Call<Reputation>

    @GET("/areas/")
    fun getAreas(
        @Header("Authorization") authorization: String
    ): Call<List<Area>>


    // Posts

    @GET("/areas/{areaName}/")
    fun getPosts(
        @Header("Authorization") authorization: String,
        @Path("areaName") areaName: String,
        @Query("limit") limit: Int
    ): Call<List<Post>>

    @GET("/areas/{areaName}/own/")
    fun getOwnPosts(
        @Header("Authorization") authorization: String,
        @Path("areaName") areaName: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int
    ): Call<SuperPost>

    @GET("/areas/{areaName}/{postId}/")
    fun getPost(
        @Header("Authorization") authorization: String,
        @Path("areaName") areaName: String,
        @Path("postId") postId: Long
    ): Call<Post>

    @POST("/areas/{areaName}/")
    @Headers("Content-Type: application/json")
    fun postPost(
        @Header("Authorization") authorization: String,
        @Path("areaName") areaName: String,
        @Body post: Post
    ): Call<Unit>

    @POST("/areas/{areaName}/{postId}/")
    @Headers("Content-Type: application/json")
    @Multipart
    fun postPicture(
        @Header("Authorization") authorization: String,
        @Path("areaName") areaName: String,
        @Path("postId") postId: Long,
        @Part image: MultipartBody.Part, // name = "image"
        @Part commentText: MultipartBody.Part // name = "text"
    ): Call<Comment>

    @POST("/areas/{areaName}/{postId}/spread/")
    @Headers("Content-Type: application/json")
    fun postSpread(
        @Header("Authorization") authorization: String,
        @Path("areaName") areaName: String,
        @Path("postId") postId: Long,
        @Body spread: Spread
    ): Call<Unit>

    @PUT("/areas/{areaName}/{postId}/subscribe/")
    @Headers("Content-Type: application/json")
    fun putSubscription(
        @Header("Authorization") authorization: String,
        @Path("areaName") areaName: String,
        @Path("postId") postId: Long,
        @Body subscription: Subscription
    ): Call<Unit>

    @DELETE("/areas/{areaName}/{postId}/")
    fun deletePost(
        @Header("Authorization") authorization: String,
        @Path("areaName") areaName: String,
        @Path("postId") postId: Long
    ): Call<Unit>


    // Drafts

    @GET("/areas/{areaName}/drafts/")
    fun getDrafts(
        @Header("Authorization") authorization: String,
        @Path("areaName") areaName: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int
    ): Call<SuperPost>

    @GET("/areas/{areaName}/drafts/{postId}/")
    fun getDraft(
        @Header("Authorization") authorization: String,
        @Path("areaName") areaName: String,
        @Query("postId") postId: Long
    ): Call<Post>

    @POST("/areas/{areaName}/drafts/")
    @Headers("Content-Type: application/json")
    fun postDraft(
        @Header("Authorization") authorization: String,
        @Path("areaName") areaName: String,
        @Body post: Post
    ): Call<Unit>

    @POST("/areas/{areaName}/drafts/{postId}/publish/")
    @Headers("Content-Type: application/json")
    fun postDraftPublication(
        @Header("Authorization") authorization: String,
        @Path("areaName") areaName: String,
        @Query("postId") postId: Long
    ): Call<Unit>

    @PUT("/areas/{areaName}/drafts/{postId}/")
    @Headers("Content-Type: application/json")
    @Multipart
    fun putPicture(
        @Header("Authorization") authorization: String,
        @Path("areaName") areaName: String,
        @Path("postId") postId: Long,
        @Part image: MultipartBody.Part, // name = "image"
        @Part postText: MultipartBody.Part // name = "text"
    ): Call<Post>

    @PUT("/areas/{areaName}/drafts/{postId}/")
    fun putPicture(
        @Header("Authorization") authorization: String,
        @Path("areaName") areaName: String,
        @Path("postId") postId: Long,
        @Body post: Post
    ): Call<Unit>

    @PUT("/areas/{areaName}/drafts/{postId}/img/{slot}/")
    @Multipart
    fun putImage(
        @Header("Authorization") authorization: String,
        @Path("areaName") areaName: String,
        @Path("postId") postId: Long,
        @Path("postId") slot: Int,
        @Part image: MultipartBody.Part, // name = "image"
        @Part comment: MultipartBody.Part // name = "comment"
    ): Call<Image>

    @PATCH("/areas/{areaName}/drafts/{postId}/")
    @Headers("Content-Type: application/json")
    fun patchDraft(
        @Header("Authorization") authorization: String,
        @Path("areaName") areaName: String,
        @Path("postId") postId: Long,
        @Body post: Post
    ): Call<Unit>

    @DELETE("/areas/{areaName}/drafts/{postId}/")
    fun deleteDraft(
        @Header("Authorization") authorization: String,
        @Path("areaName") areaName: String,
        @Path("postId") postId: Long
    ): Call<Unit>

    @DELETE("/areas/{areaName}/drafts/{postId}/img/{slot}/")
    fun deleteImage(
        @Header("Authorization") authorization: String,
        @Path("areaName") areaName: String,
        @Path("postId") postId: Long,
        @Path("postId") slot: Int
    ): Call<Unit>


    // Comments

    @POST("/areas/{areaName}/{postId}/")
    @Headers("Content-Type: application/json")
    fun postComment(
        @Header("Authorization") authorization: String,
        @Path("areaName") areaName: String,
        @Path("postId") postId: Long,
        @Body comment: CommentText
    ): Call<Comment>

    @POST("/areas/{areaName}/{postId}/")
    @Headers("Content-Type: application/json")
    fun postImage(
        @Header("Authorization") authorization: String,
        @Path("areaName") areaName: String,
        @Path("postId") postId: Long,
        @Part image: MultipartBody.Part, // name = "image"
        @Part commentText: MultipartBody.Part // name = "text"
    ): Call<Comment>

    @DELETE("/areas/{areaName}/{postId}/{commentId}/")
    fun deleteComment(
        @Header("Authorization") authorization: String,
        @Path("areaName") areaName: String,
        @Path("postId") postId: Long,
        @Path("commentId") commentId: Long
    ): Call<Unit>
}

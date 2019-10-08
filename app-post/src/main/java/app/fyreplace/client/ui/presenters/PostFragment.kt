package app.fyreplace.client.ui.presenters

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.*
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.isVisible
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.observe
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.fyreplace.client.app.post.R
import app.fyreplace.client.app.post.databinding.FragmentPostBinding
import app.fyreplace.client.data.models.Author
import app.fyreplace.client.data.models.Comment
import app.fyreplace.client.data.models.ImageData
import app.fyreplace.client.data.models.Post
import app.fyreplace.client.ui.*
import app.fyreplace.client.ui.adapters.CommentsAdapter
import app.fyreplace.client.ui.drawables.BottomSheetArrowDrawableWrapper
import app.fyreplace.client.ui.widgets.CommentSheetBehavior
import app.fyreplace.client.viewmodels.CentralViewModel
import app.fyreplace.client.viewmodels.PostFragmentViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import ru.noties.markwon.recycler.MarkwonAdapter
import kotlin.math.max

open class PostFragment : FailureHandlingFragment(R.layout.fragment_post), BackHandlingFragment,
    ToolbarUsingFragment, ImageSelector {
    override val viewModel by viewModel<PostFragmentViewModel>()
    override lateinit var bd: FragmentPostBinding
    override val contextWrapper by lazy { requireActivity() }
    protected val cbd by lazy {
        bd.collapsibleComments ?: bd.staticComments ?: throw IllegalStateException()
    }
    private val centralViewModel by sharedViewModel<CentralViewModel>()
    private val fragmentArgs by inject<Args> { parametersOf(this) }
    private val navigator by inject<Navigator> { parametersOf(this) }
    private val markdown by lazyMarkdown()
    private val highlightedCommentIds by lazy { if (canUseFragmentArgs()) fragmentArgs.newCommentsIds else null }
    private var commentsSheetCallback: CommentsSheetCallback<View>? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = FragmentPostBinding.inflate(inflater, container, false).run {
        lifecycleOwner = viewLifecycleOwner
        model = viewModel
        bd = this
        return@run root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val markdownAdapter = MarkwonAdapter.createTextViewIsRoot(R.layout.post_entry)
        val commentsAdapter = CommentsAdapter(this, navigator, markdown)

        bd.content.adapter = markdownAdapter
        cbd.commentsList.setHasFixedSize(true)
        cbd.commentsList.adapter = commentsAdapter
        cbd.commentsList.addItemDecoration(
            DividerItemDecoration(
                view.context,
                (cbd.commentsList.layoutManager as LinearLayoutManager).orientation
            )
        )

        if (canUseFragmentArgs()) launch {
            fragmentArgs.post?.let { viewModel.setPost(it) }
                ?: viewModel.setPostData(fragmentArgs.areaName, fragmentArgs.postId)
            viewModel.setIsOwnPost(fragmentArgs.ownPost)
        }

        centralViewModel.userId.observe(viewLifecycleOwner) { commentsAdapter.selfId = it }
        viewModel.post.observe(viewLifecycleOwner) {
            centralViewModel.setPost(it)
        }
        viewModel.contentLoaded.observe(viewLifecycleOwner) { cbd.commentNew.isEnabled = it }
        viewModel.authorId.observe(viewLifecycleOwner) { commentsAdapter.authorId = it }
        viewModel.markdownContent.observe(viewLifecycleOwner) {
            lifecycleScope.launch(Dispatchers.Default) {
                markdownAdapter.setMarkdown(markdown, it)
                withContext(Dispatchers.Main) { markdownAdapter.notifyDataSetChanged() }
            }
        }
        viewModel.comments.observe(viewLifecycleOwner) {
            lifecycleScope.launch(Dispatchers.Default) {
                commentsAdapter.setComments(it, highlightedCommentIds)
                withContext(Dispatchers.Main) { commentsAdapter.notifyDataSetChanged() }
            }
        }
        viewModel.newCommentImage.observe(viewLifecycleOwner) { image ->
            if (image != null) {
                cbd.commentNew.startIconDrawable?.let {
                    DrawableCompat.setTint(
                        it,
                        ContextCompat.getColor(view.context, R.color.colorPrimary)
                    )
                }
            } else {
                cbd.commentNew.setStartIconDrawable(R.drawable.ic_attach_file_black)
            }
        }
        viewModel.canSendNewComment.observe(viewLifecycleOwner) {
            cbd.commentNew.isEndIconVisible = it
        }

        cbd.commentsList.addOnScrollListener(CommentsScrollListener())
        cbd.goUp.setOnClickListener { cbd.commentsList.smoothScrollToPosition(0) }
        cbd.goDown.setOnClickListener {
            cbd.commentsList.smoothScrollToPosition(max(commentsAdapter.itemCount - 1, 0))
        }

        for (button in listOf(cbd.goUp, cbd.goDown)) {
            button.setTag(
                R.id.anim_scale_x,
                SpringAnimation(button, SpringAnimation.SCALE_X).setSpring(BUTTON_ANIM_SPRING)
            )
            button.setTag(
                R.id.anim_scale_y,
                SpringAnimation(button, SpringAnimation.SCALE_Y)
                    .setSpring(BUTTON_ANIM_SPRING).apply {
                        addEndListener { _, _, value, _ ->
                            if (button.isVisible && value == 0f) {
                                button.isVisible = false
                            }
                        }
                    }
            )
        }

        cbd.commentNew.setEndIconOnClickListener {
            clearCommentInput()
            launch {
                viewModel.sendNewComment()
                commentsAdapter.doOnCommentsChanged { cbd.goDown.callOnClick() }
            }
        }
        cbd.commentNew.setStartIconOnClickListener { requestImage() }

        bd.collapsibleComments?.let {
            cbd.commentCount.setOnClickListener { toggleComments() }

            val commentsExpanded = savedInstanceState?.getBoolean(SAVE_COMMENTS_EXPANDED)
                ?: (highlightedCommentIds != null)
            val behavior = CommentSheetBehavior.from(it.root)

            commentsSheetCallback = CommentsSheetCallback(behavior, cbd.arrow, commentsExpanded)
                .apply { behavior.addBottomSheetCallback(this) }

            if (commentsExpanded) {
                toggleComments()
            }
        }
    }

    override fun onDestroyView() {
        cbd.commentsList.clearOnScrollListeners()

        commentsSheetCallback?.let {
            CommentSheetBehavior.from(cbd.root).removeBottomSheetCallback(it)
        }

        clearCommentInput()
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (view != null) {
            bd.collapsibleComments?.let {
                outState.putBoolean(
                    SAVE_COMMENTS_EXPANDED,
                    BottomSheetBehavior.from(it.root).state == BottomSheetBehavior.STATE_EXPANDED
                )
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.actions_fragment_post, menu)
        inflater.inflate(R.menu.actions_fragment_sharing, menu)
        inflater.inflate(R.menu.actions_fragment_deletion, menu)

        viewModel.subscribed.observe(viewLifecycleOwner) {
            menu.findItem(R.id.action_subscribe).run {
                setTitle(
                    if (it)
                        R.string.post_action_unsubscribe
                    else
                        R.string.post_action_subscribe
                )
                setIcon(
                    if (it)
                        R.drawable.ic_notifications_white
                    else
                        R.drawable.ic_notifications_none_white
                )
            }
        }

        viewModel.post.observe(viewLifecycleOwner) {
            menu.findItem(R.id.action_share).intent = it?.let {
                getShareIntent(
                    postShareUrl(viewModel.postAreaName, viewModel.postId),
                    getString(R.string.post_action_share_title)
                )
            }
        }

        val deleteItem = menu.findItem(R.id.action_delete)
        viewModel.isOwnPost.observe(viewLifecycleOwner) { deleteItem.isVisible = it }
        viewModel.authorId.observe(viewLifecycleOwner) {
            deleteItem.isVisible = it == centralViewModel.userId.value
        }

        val postMenuItems = listOf(R.id.action_subscribe, R.id.action_share, R.id.action_delete)
            .map { menu.findItem(it) }
        viewModel.contentLoaded.observe(viewLifecycleOwner) {
            postMenuItems.forEach { action -> action.isEnabled = it }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_subscribe -> launch { viewModel.changeSubscription() }
            R.id.action_delete -> AlertDialog.Builder(contextWrapper)
                .setTitle(R.string.post_action_delete_dialog_title)
                .setNegativeButton(R.string.no, null)
                .setPositiveButton(R.string.yes) { _, _ ->
                    launch {
                        viewModel.deletePost()
                        findNavController().navigateUp()
                    }
                }
                .show()
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onCreateContextMenu(
        menu: ContextMenu,
        v: View,
        menuInfo: ContextMenu.ContextMenuInfo?
    ) {
        super.onCreateContextMenu(menu, v, menuInfo)
        requireActivity().menuInflater.inflate(R.menu.context_fragment_post_comment, menu)

        val position = v.tag as Int
        val comment = (cbd.commentsList.adapter as CommentsAdapter).getComment(position)

        menu.findItem(R.id.action_copy).setOnMenuItemClickListener { copyComment(comment); true }
        menu.findItem(R.id.action_share).setOnMenuItemClickListener { shareComment(comment); true }
        menu.findItem(R.id.action_delete).run {
            isVisible = comment.author?.user == centralViewModel.userId.value
            setOnMenuItemClickListener { deleteComment(position, comment); true }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super<FailureHandlingFragment>.onActivityResult(requestCode, resultCode, data)
        super<ImageSelector>.onActivityResult(requestCode, resultCode, data)
    }

    override fun onGoBack(method: BackHandlingFragment.Method) =
        method == BackHandlingFragment.Method.UP_BUTTON || bd.collapsibleComments?.let { binding ->
            (BottomSheetBehavior.from(binding.root).state != BottomSheetBehavior.STATE_EXPANDED)
                .apply { takeUnless { it }?.run { toggleComments() } }
        } ?: true

    override fun onImage(image: ImageData) = viewModel.setCommentImage(image)

    open fun canUseFragmentArgs() = arguments != null

    private fun toggleComments() {
        bd.collapsibleComments?.let {
            val commentsBehavior = BottomSheetBehavior.from(it.root)

            when {
                commentsBehavior.state in setOf(
                    BottomSheetBehavior.STATE_HIDDEN,
                    BottomSheetBehavior.STATE_COLLAPSED
                ) ->
                    commentsBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                commentsBehavior.state == BottomSheetBehavior.STATE_EXPANDED ->
                    commentsBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }
    }

    private fun clearCommentInput() {
        hideSoftKeyboard(cbd.commentNew)
        cbd.commentNew.clearFocus()
    }

    private fun requestImage() {
        val dialog = AlertDialog.Builder(contextWrapper)
            .setView(R.layout.post_dialog_comment_image)
            .setTitle(R.string.post_comment_attach_file_dialog_title)
            .setNegativeButton(R.string.post_comment_attach_file_dialog_negative) { _, _ ->
                selectImage(requestImagePhoto)
            }
            .setPositiveButton(R.string.post_comment_attach_file_dialog_positive) { _, _ ->
                selectImage(requestImageFile)
            }
            .setNeutralButton(R.string.post_comment_attach_file_dialog_neutral) { _, _ -> viewModel.resetCommentImage() }
            .show()

        val image = dialog.findViewById<ImageView>(R.id.image)
        image?.isVisible = viewModel.newCommentImage.value != null
        viewModel.newCommentImage.value?.let {
            image?.setImageDrawable(
                BitmapDrawable(
                    resources,
                    BitmapFactory.decodeByteArray(it.bytes, 0, it.bytes.size)
                )
            )
        }
    }

    private fun copyComment(comment: Comment) {
        getSystemService(contextWrapper, ClipboardManager::class.java)?.setPrimaryClip(
            ClipData.newPlainText(
                getString(R.string.post_comment_copy_label),
                comment.text
            )
        )

        Toast.makeText(context, getString(R.string.post_comment_copy_toast), Toast.LENGTH_SHORT)
            .show()
    }

    private fun shareComment(comment: Comment) = startActivity(
        getShareIntent(
            postShareUrl(viewModel.postAreaName, viewModel.postId, comment.id),
            getString(R.string.post_comment_share_title)
        )
    )

    private fun deleteComment(position: Int, comment: Comment) {
        AlertDialog.Builder(contextWrapper)
            .setTitle(R.string.post_comment_delete_dialog_title)
            .setNegativeButton(R.string.no, null)
            .setPositiveButton(R.string.yes) { _, _ ->
                launch { viewModel.deleteComment(position, comment) }
            }
            .show()
    }

    private companion object {
        const val SAVE_COMMENTS_EXPANDED = "save.comments.expanded"
        val BUTTON_ANIM_SPRING: SpringForce = SpringForce()
            .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)
    }

    interface Args {
        val post: Post?
        val areaName: String?
        val postId: Long
        val ownPost: Boolean
        val newCommentsIds: List<Long>?
    }

    interface Navigator {
        fun navigateToUser(author: Author)
    }

    private inner class CommentsScrollListener : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                clearCommentInput()
            }
        }

        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            for (pair in mapOf(
                cbd.goUp to (recyclerView.canScrollVertically(-1) && dy < 0),
                cbd.goDown to (recyclerView.canScrollVertically(1) && dy > 0)
            )) {
                val button = pair.key
                val visible = pair.value

                if (button.isVisible != visible) {
                    if (visible) {
                        button.isVisible = true
                    }

                    for (key in listOf(R.id.anim_scale_x, R.id.anim_scale_y)) {
                        (button.getTag(key) as? SpringAnimation)?.animateToFinalPosition(if (visible) 1f else 0f)
                    }
                }
            }
        }
    }

    private inner class CommentsSheetCallback<V : View>(
        private val behavior: CommentSheetBehavior<V>,
        arrow: ImageView,
        commentsExpanded: Boolean
    ) : BottomSheetBehavior.BottomSheetCallback() {
        private val arrowWrapper = BottomSheetArrowDrawableWrapper(arrow, !commentsExpanded)

        @SuppressLint("SwitchIntDef")
        override fun onStateChanged(bottomSheet: View, newState: Int) {
            bd.content.isVisible = newState != BottomSheetBehavior.STATE_EXPANDED
            behavior.canDrag = newState != BottomSheetBehavior.STATE_EXPANDED

            when (newState) {
                BottomSheetBehavior.STATE_COLLAPSED -> {
                    clearCommentInput()
                    arrowWrapper.setPointingUp(true)
                }
                BottomSheetBehavior.STATE_EXPANDED -> arrowWrapper.setPointingUp(false)
            }
        }

        override fun onSlide(bottomSheet: View, slideOffset: Float) = Unit
    }
}
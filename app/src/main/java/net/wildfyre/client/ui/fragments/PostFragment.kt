package net.wildfyre.client.ui.fragments

import android.animation.LayoutTransition
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.*
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.android.synthetic.main.fragment_post.*
import kotlinx.android.synthetic.main.fragment_post_comments.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.wildfyre.client.R
import net.wildfyre.client.WildFyreApplication
import net.wildfyre.client.data.Comment
import net.wildfyre.client.databinding.FragmentPostBinding
import net.wildfyre.client.ui.PostPlugin
import net.wildfyre.client.ui.adapters.CommentsAdapter
import net.wildfyre.client.ui.drawables.BottomSheetArrowDrawableWrapper
import net.wildfyre.client.ui.hideSoftKeyboard
import net.wildfyre.client.viewmodels.*
import ru.noties.markwon.Markwon
import ru.noties.markwon.core.CorePlugin
import ru.noties.markwon.ext.strikethrough.StrikethroughPlugin
import ru.noties.markwon.image.ImagesPlugin
import ru.noties.markwon.image.okhttp.OkHttpImagesPlugin
import ru.noties.markwon.recycler.MarkwonAdapter
import ru.noties.markwon.recycler.table.TableEntryPlugin

open class PostFragment : FailureHandlingFragment(R.layout.fragment_post), CommentsAdapter.OnCommentDeleted {
    override val viewModels: List<FailureHandlingViewModel> by lazy { listOf(viewModel) }
    private val viewModel by lazyViewModel<PostFragmentViewModel>()
    private val mainViewModel by lazyActivityViewModel<MainActivityViewModel>()
    private val fragmentArgs by navArgs<PostFragmentArgs>()
    private val onBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = toggleComments()
    }
    private lateinit var markdown: Markwon
    private val highlightedCommentIds by lazy {
        if (arguments != null)
            fragmentArgs.newCommentsIds?.asList()
                ?: (if (fragmentArgs.selectedCommentId >= 0) listOf(fragmentArgs.selectedCommentId) else null)
        else
            null
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        setHasOptionsMenu(true)
        markdown = Markwon.builder(context)
            .usePlugin(CorePlugin.create())
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(PostPlugin.create(context))
            .usePlugin(ImagesPlugin.create(context))
            .usePlugin(OkHttpImagesPlugin.create())
            .usePlugin(TableEntryPlugin.create(context))
            .build()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        FragmentPostBinding.inflate(inflater, container, false).run {
            lifecycleOwner = viewLifecycleOwner
            model = viewModel
            return@run root
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val markdownAdapter = MarkwonAdapter.createTextViewIsRoot(R.layout.post_entry)
        content.adapter = markdownAdapter
        val commentsAdapter = CommentsAdapter(markdown, this)
        comments_list.setHasFixedSize(true)
        comments_list.adapter = commentsAdapter
        comments_list.addItemDecoration(
            DividerItemDecoration(
                view.context,
                (comments_list.layoutManager as LinearLayoutManager).orientation
            )
        )

        if (arguments != null) {
            viewModel.setPostDataAsync(fragmentArgs.areaName, fragmentArgs.postId)
        }

        viewModel.post.observe(viewLifecycleOwner, Observer { mainViewModel.setPost(it) })
        viewModel.selfId.observe(viewLifecycleOwner, Observer { commentsAdapter.selfId = it })
        viewModel.authorId.observe(viewLifecycleOwner, Observer { commentsAdapter.authorId = it })
        viewModel.markdownContent.observe(viewLifecycleOwner, Observer {
            launch {
                withContext(Dispatchers.Default) { markdownAdapter.setMarkdown(markdown, it) }
                markdownAdapter.notifyDataSetChanged()
            }
        })
        viewModel.comments.observe(viewLifecycleOwner, Observer { commentList ->
            launch {
                withContext(Dispatchers.Default) { commentsAdapter.setComments(commentList, highlightedCommentIds) }
                commentsAdapter.notifyDataSetChanged()
            }
        })
        viewModel.commentAddedEvent.observe(viewLifecycleOwner, Observer {
            commentsAdapter.addComment(it)
            commentsAdapter.notifyItemInserted(commentsAdapter.itemCount - 1)
        })
        viewModel.commentRemovedEvent.observe(viewLifecycleOwner, Observer {
            commentsAdapter.removeComment(it)
            commentsAdapter.notifyItemRemoved(it)
        })
        viewModel.newCommentData.observe(
            viewLifecycleOwner,
            Observer { comment_new.isEndIconVisible = it.isNotBlank() }
        )

        comments_list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING && comment_new.hasFocus()) {
                    clearCommentInput()
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy != 0) {
                    go_up.isVisible = recyclerView.canScrollVertically(-1) && dy < 0
                    go_down.isVisible = recyclerView.canScrollVertically(1) && dy > 0
                }
            }
        })

        listOf(go_up.parent as ViewGroup, go_down.parent as ViewGroup).forEach {
            it.layoutTransition.setAnimator(
                LayoutTransition.APPEARING,
                ANIMATOR_SCALE_UP
            )
            it.layoutTransition.setAnimator(
                LayoutTransition.DISAPPEARING,
                ANIMATOR_SCALE_DOWN
            )
        }

        go_up.setOnClickListener { comments_list.smoothScrollToPosition(0) }
        go_down.setOnClickListener { comments_list.smoothScrollToPosition(Math.max(commentsAdapter.itemCount - 1, 0)) }
        comment_new.setEndIconOnClickListener { clearCommentInput(); viewModel.sendNewCommentAsync() }

        collapsible_comments?.let {
            comment_count.setOnClickListener { toggleComments() }

            val commentsExpanded = savedInstanceState?.getBoolean(SAVE_COMMENTS_EXPANDED)
                ?: (highlightedCommentIds != null) || onBackPressedCallback.isEnabled
            val arrowWrapper = BottomSheetArrowDrawableWrapper(arrow, !commentsExpanded)

            BottomSheetBehavior.from(it).setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                @SuppressLint("SwitchIntDef")
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    onBackPressedCallback.isEnabled = newState == BottomSheetBehavior.STATE_EXPANDED
                    content?.isVisible = newState != BottomSheetBehavior.STATE_EXPANDED

                    when (newState) {
                        BottomSheetBehavior.STATE_COLLAPSED -> {
                            clearCommentInput()
                            arrowWrapper.setPointingUp(true)
                        }
                        BottomSheetBehavior.STATE_EXPANDED -> arrowWrapper.setPointingUp(false)
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) = Unit
            })

            if (commentsExpanded) {
                onBackPressedCallback.isEnabled = true
                toggleComments()
            }
        }
    }

    override fun onDestroyView() {
        clearCommentInput()
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        collapsible_comments?.let {
            outState.putBoolean(
                SAVE_COMMENTS_EXPANDED,
                BottomSheetBehavior.from(it).state == BottomSheetBehavior.STATE_EXPANDED
            )
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) =
        inflater.inflate(R.menu.fragment_post_actions, menu)

    override fun onCommentDeleted(position: Int, comment: Comment) {
        viewModel.deleteCommentAsync(position, comment)
    }

    private fun toggleComments() {
        if (collapsible_comments == null) {
            return
        }

        val commentsBehavior = BottomSheetBehavior.from(collapsible_comments)

        when {
            commentsBehavior.state in setOf(BottomSheetBehavior.STATE_HIDDEN, BottomSheetBehavior.STATE_COLLAPSED) ->
                commentsBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            commentsBehavior.state == BottomSheetBehavior.STATE_EXPANDED ->
                commentsBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
    }

    private fun clearCommentInput() {
        comment_new?.let {
            hideSoftKeyboard(it)
            it.clearFocus()
        }
    }

    private companion object {
        const val SAVE_COMMENTS_EXPANDED = "save.comments.expanded"
        val TRANSLATION_BUTTON = WildFyreApplication.context.resources.getDimension(R.dimen.comments_button_translation)
        val ANIMATOR_SCALE_DOWN: ObjectAnimator = ObjectAnimator.ofPropertyValuesHolder(
            Unit,
            PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 0f, TRANSLATION_BUTTON),
            PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0f)
        )
        val ANIMATOR_SCALE_UP: ObjectAnimator = ObjectAnimator.ofPropertyValuesHolder(
            Unit,
            PropertyValuesHolder.ofFloat(View.TRANSLATION_X, TRANSLATION_BUTTON, 0f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f)
        )
    }
}

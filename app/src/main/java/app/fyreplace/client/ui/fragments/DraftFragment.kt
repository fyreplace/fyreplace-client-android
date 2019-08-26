package app.fyreplace.client.ui.fragments

import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModel
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.RecyclerView
import app.fyreplace.client.R
import app.fyreplace.client.ui.hideSoftKeyboard
import app.fyreplace.client.ui.lazyMarkdown
import app.fyreplace.client.viewmodels.DraftFragmentViewModel
import app.fyreplace.client.viewmodels.lazyViewModel
import kotlinx.android.synthetic.main.draft_editor.*
import kotlinx.android.synthetic.main.fragment_draft.*
import ru.noties.markwon.recycler.MarkwonAdapter

class DraftFragment : FailureHandlingFragment(R.layout.fragment_draft), BackHandlingFragment,
    TextWatcher {
    override val viewModels: List<ViewModel> by lazy { listOf(viewModel) }
    override val viewModel by lazyViewModel<DraftFragmentViewModel>()
    private val fragmentArgs by navArgs<DraftFragmentArgs>()
    private val markdown by lazyMarkdown()
    private val markdownAdapter = MarkwonAdapter.createTextViewIsRoot(R.layout.post_entry)
    private var allowDirtyingDraft = false
    private var countDownTimer: CountDownTimer? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.setDraft(fragmentArgs.draft)

        preview?.adapter = markdownAdapter
        editor.addTextChangedListener(this)
        editor.setText(fragmentArgs.draft.text)

        if (fragmentArgs.showHint) launch {
            Toast.makeText(
                requireContext(),
                getString(
                    R.string.draft_hint_toast,
                    viewModel.getPreferredArea()?.displayName
                ),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroyView() {
        hideSoftKeyboard(editor)
        super.onDestroyView()
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        allowDirtyingDraft = true
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.actions_fragment_draft, menu)
        inflater.inflate(R.menu.actions_fragment_deletion, menu)
        menu.findItem(R.id.action_preview).isVisible = preview == null
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val context = requireContext()

        when (item.itemId) {
            R.id.action_preview -> {
                hideSoftKeyboard(editor)
                AlertDialog.Builder(context)
                    .setView(R.layout.draft_preview_dialog)
                    .show()
                    .findViewById<RecyclerView>(R.id.preview)?.adapter = markdownAdapter
                updatePreview()
            }
            R.id.action_publish -> launch {
                saveDraft(false)
                viewModel.publishDraft()
                Toast.makeText(
                    context,
                    R.string.draft_action_publish_toast,
                    Toast.LENGTH_SHORT
                ).show()
                findNavController().navigateUp()
            }
            R.id.action_save -> launch { saveDraft(true) }
            R.id.action_delete -> AlertDialog.Builder(context)
                .setTitle(R.string.draft_action_delete_dialog_title)
                .setNegativeButton(R.string.no, null)
                .setPositiveButton(R.string.yes) { _, _ ->
                    launch {
                        viewModel.deleteDraft()
                        findNavController().navigateUp()
                    }
                }
                .show()
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onGoBack(): Boolean {
        if (viewModel.saved) {
            return true
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.draft_back_dialog_title)
            .setNegativeButton(R.string.no) { _, _ -> findNavController().navigateUp() }
            .setPositiveButton(R.string.yes) { _, _ ->
                launch {
                    saveDraft(true)
                    findNavController().navigateUp()
                }
            }
            .show()

        return false
    }

    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) = Unit

    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit

    override fun afterTextChanged(s: Editable) {
        if (allowDirtyingDraft) {
            viewModel.dirtyDraft()
        }

        if (preview != null) {
            countDownTimer?.cancel()
            countDownTimer = Timer().apply { start() }
        }
    }

    private fun updatePreview() {
        markdownAdapter.setMarkdown(markdown, editor.text.toString())
        markdownAdapter.notifyDataSetChanged()
    }

    private suspend fun saveDraft(showConfirmation: Boolean) {
        viewModel.saveDraft(editor.text.toString())

        if (showConfirmation) {
            Toast.makeText(context, R.string.draft_action_save_toast, Toast.LENGTH_SHORT).show()
        }
    }

    private companion object {
        const val PREVIEW_DELAY = 1500L
    }

    private inner class Timer : CountDownTimer(PREVIEW_DELAY, Long.MAX_VALUE) {
        override fun onFinish() = updatePreview()

        override fun onTick(millisUntilFinished: Long) = Unit
    }
}

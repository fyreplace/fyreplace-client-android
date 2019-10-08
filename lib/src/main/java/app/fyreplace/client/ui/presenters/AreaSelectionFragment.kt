package app.fyreplace.client.ui.presenters

import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.widget.AdapterView
import android.widget.Spinner
import androidx.fragment.app.Fragment
import androidx.lifecycle.observe
import app.fyreplace.client.lib.R
import app.fyreplace.client.lib.databinding.ActionAreaSelectingAreaSpinnerBinding
import app.fyreplace.client.ui.FailureHandler
import app.fyreplace.client.viewmodels.AreaSelectingFragmentViewModel
import org.koin.androidx.viewmodel.ext.android.getSharedViewModel

/**
 * Interface for fragments displaying an area selector in their menu.
 */
interface AreaSelectionFragment : FailureHandler {
    fun onCreateOptionsMenu(fragment: Fragment, menu: Menu, inflater: MenuInflater) {
        val areaSelectingViewModel = fragment.getSharedViewModel<AreaSelectingFragmentViewModel>()
        val areaSelectorMenuItem = menu.findItem(R.id.action_area_selector)
        val areaSpinner = areaSelectorMenuItem?.actionView as Spinner

        ActionAreaSelectingAreaSpinnerBinding.bind(areaSpinner).apply {
            lifecycleOwner = fragment.viewLifecycleOwner
            model = areaSelectingViewModel
        }

        areaSelectingViewModel.preferredArea.observe(fragment.viewLifecycleOwner) {
            areaSelectorMenuItem.title = fragment.getString(
                R.string.area_selecting_action_area_selector,
                it.displayName
            )
        }

        areaSelectingViewModel.areas.observe(fragment.viewLifecycleOwner) { areas ->
            areaSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    launch { areaSelectingViewModel.setPreferredAreaName(areas[position].name) }
                }
            }
        }

        launch { areaSelectingViewModel.updateAreas() }
    }
}
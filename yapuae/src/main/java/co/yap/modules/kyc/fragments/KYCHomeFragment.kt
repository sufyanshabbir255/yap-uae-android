package co.yap.modules.kyc.fragments

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.databinding.Observable
import androidx.databinding.library.baseAdapters.BR
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.findNavController
import co.yap.yapuae.R
import co.yap.modules.kyc.activities.DocumentsResponse
import co.yap.modules.kyc.enums.DocScanStatus
import co.yap.modules.kyc.interfaces.IKYCHome
import co.yap.modules.kyc.viewmodels.KYCHomeViewModel
import co.yap.translation.Strings
import co.yap.yapcore.firebase.FirebaseEvent
import co.yap.yapcore.firebase.trackEventWithScreenName
import com.digitify.identityscanner.docscanner.activities.IdentityScannerActivity
import com.digitify.identityscanner.docscanner.enums.DocumentType
import java.io.File

class KYCHomeFragment : KYCChildFragment<IKYCHome.ViewModel>(), IKYCHome.View {

    override fun getBindingVariable(): Int = BR.viewModel

    override fun getLayoutId(): Int = R.layout.fragment_kyc_home

    override val viewModel: KYCHomeViewModel
        get() = ViewModelProviders.of(this).get(KYCHomeViewModel::class.java)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        trackEventWithScreenName(FirebaseEvent.SCAN_ID)
//        requireActivity().firebaseTagManagerEvent(FirebaseTagManagerModel(action = FirebaseEvents.SCAN_ID.event))
        shouldSkipScreen()
        addObservers()
    }


    private fun addObservers() {
        viewModel.state.addOnPropertyChangedCallback(stateObserver)
        viewModel.clickEvent.observe(this, Observer {
            when (it) {
                R.id.cvCard -> openCardScanner()
                R.id.btnNext -> {
                    viewModel.parentViewModel?.finishKyc?.value = DocumentsResponse(true)
                }
                R.id.tvSkip -> {
                    trackEventWithScreenName(FirebaseEvent.CLICK_SKIP_EID)
                    viewModel.parentViewModel?.finishKyc?.value = DocumentsResponse(false)
                }
            }
        })
    }

    private fun shouldSkipScreen() {
        viewModel.parentViewModel?.skipFirstScreen?.value?.let {
            if (it) {
                findNavController().navigate(R.id.action_KYCHomeFragment_to_eidInfoReviewFragment)
            } else if (viewModel.parentViewModel?.gotoInformationErrorFragment?.value == true) {
                navigateToInformationErrorFragment()
            } else {
                viewModel.state.eidScanStatus = DocScanStatus.SCAN_PENDING
            }
        } ?: if (viewModel.parentViewModel?.gotoInformationErrorFragment?.value == true) {
            navigateToInformationErrorFragment()
        }
    }

    private fun navigateToInformationErrorFragment() {
        val action =
            KYCHomeFragmentDirections.actionKYCHomeFragmentToInformationErrorFragment(
                getString(Strings.screen_kyc_information_error_display_text_title_from_us),
                getString(Strings.screen_kyc_information_error_text_description_from_us)
            )
        navigate(action)
    }

    private val stateObserver = object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
            when (propertyId) {
                BR.eidScanStatus -> {
                    if (viewModel.state.eidScanStatus === DocScanStatus.SCAN_COMPLETED) {
                        findNavController().navigate(R.id.action_KYCHomeFragment_to_eidInfoReviewFragment)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        viewModel.clickEvent.removeObservers(this)
        viewModel.state.removeOnPropertyChangedCallback(stateObserver)
        super.onDestroyView()
    }

    private fun openCardScanner() {
        startActivityForResult(
            IdentityScannerActivity.getLaunchIntent(
                requireContext(),
                DocumentType.EID,
                IdentityScannerActivity.SCAN_FROM_CAMERA
            ),
            IdentityScannerActivity.SCAN_EID_CAM
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IdentityScannerActivity.SCAN_EID_CAM && resultCode == Activity.RESULT_OK) {
            data?.let {
                viewModel.onEIDScanningComplete(it.getParcelableExtra(IdentityScannerActivity.SCAN_RESULT))
            }
        }
    }

    override fun onDestroy() {
        viewModel.parentViewModel?.paths?.forEach { filePath ->
            File(filePath).deleteRecursively()
        }
        super.onDestroy()
    }

    override fun onBackPressed(): Boolean {
        return true
    }

}
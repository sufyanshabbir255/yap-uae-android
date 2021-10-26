package co.yap.modules.dashboard.more.changepasscode.fragments

import android.os.Bundle
import android.view.View
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import co.yap.yapuae.BR
import co.yap.yapuae.R
import co.yap.modules.dashboard.more.changepasscode.interfaces.IChangePassCodeSuccess
import co.yap.modules.dashboard.more.changepasscode.viewmodels.ChangePasscodeSuccessViewModel
import co.yap.translation.Strings

class ChangePasscodeSuccessFragment :
    ChangePasscodeBaseFragment<IChangePassCodeSuccess.ViewModel>(),
    IChangePassCodeSuccess.View {

    override fun getBindingVariable(): Int = BR.viewModel
    override fun getLayoutId(): Int = R.layout.fragment_change_passcodee_success
    override val viewModel: ChangePasscodeSuccessViewModel
        get() = ViewModelProviders.of(this).get(ChangePasscodeSuccessViewModel::class.java)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.buttonClickEvent.observe(this, Observer {
            activity?.finish()
        })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.state.topSubHeading =
            getString(Strings.screen_change_passcode_success_display_text_sub_heading)
        viewModel.state.title =
            getString(Strings.screen_change_passcode_success_display_text_heading)
    }

    override fun onDestroy() {
        viewModel.buttonClickEvent.removeObservers(this)
        super.onDestroy()
    }

    override fun onBackPressed(): Boolean {
        return true
    }
}
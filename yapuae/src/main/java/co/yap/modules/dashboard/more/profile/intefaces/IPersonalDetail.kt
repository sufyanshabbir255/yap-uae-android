package co.yap.modules.dashboard.more.profile.intefaces

import android.graphics.drawable.Drawable
import co.yap.networking.cards.responsedtos.Address
import co.yap.yapcore.IBase
import co.yap.yapcore.SingleClickEvent

interface IPersonalDetail {

    interface State : IBase.State {
        var fullName: String
        var phoneNumber: String
        var email: String?
        var address: String
        var drawbleRight: Drawable?
        var verificationText: String
    }

    interface ViewModel : IBase.ViewModel<State> {

        val clickEvent: SingleClickEvent

        var UPDATE_ADDRESS_UI: Int

        fun handlePressOnBackButton()

        fun handlePressOnEditPhone(id: Int)

        fun handlePressOnEditEmail(id: Int)

        fun handlePressOnEditAddress(id: Int)

        fun handlePressOnDocumentCard(id: Int)

        fun handlePressOnScanCard(id: Int)

        fun toggleToolBar(hide: Boolean)

        fun updateToolBarText(heading: String)

        fun requestUpdateAddress(
            updateAddressRequest: Address,
            success: (updateAddressSuccess: Boolean) -> Unit
        )

        fun setUpVerificationLayout()

    }

    interface View : IBase.View<ViewModel>
}
package co.yap.modules.dashboard.cards.addpaymentcard.main.states

import androidx.databinding.Bindable
import co.yap.yapuae.BR
import co.yap.modules.dashboard.cards.addpaymentcard.main.interfaces.IAddPaymentCard
import co.yap.yapcore.BaseState

class AddPaymentCardsState : BaseState(), IAddPaymentCard.State {

    @get:Bindable
    override var tootlBarTitle: String = ""
        set(value) {
            field = value
            notifyPropertyChanged(BR.tootlBarTitle)

        }

    @get:Bindable
    override var tootlBarVisibility: Int = 0x00000000
        set(value) {
            field = value
            notifyPropertyChanged(BR.tootlBarVisibility)

        }
}
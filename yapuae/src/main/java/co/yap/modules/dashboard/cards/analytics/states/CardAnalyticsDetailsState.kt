package co.yap.modules.dashboard.cards.analytics.states

import androidx.databinding.Bindable
import androidx.databinding.ObservableField
import co.yap.yapuae.BR
import co.yap.modules.dashboard.cards.analytics.interfaces.ICardAnalyticsDetails
import co.yap.yapcore.BaseState

class CardAnalyticsDetailsState : BaseState(), ICardAnalyticsDetails.State {
    override var title: ObservableField<String> = ObservableField("Title")
    override var totalSpendings: ObservableField<String> = ObservableField("Spendings")
    override var monthlyTotalPercentage: ObservableField<String> = ObservableField("")
    override var countWithDate: ObservableField<String> = ObservableField()
    override var avgSpending: ObservableField<String> = ObservableField()
    override var currToLast: ObservableField<String> = ObservableField("0.0")
    override var ImageUrl: ObservableField<String> = ObservableField("Url")

    @get:Bindable
    override var position: Int = 0
        set(value) {
            field = value
            notifyPropertyChanged(BR.position)
        }

    @get:Bindable
    override var percentCardVisibility: Boolean = true
        set(value) {
            field = value
            notifyPropertyChanged(BR.percentCardVisibility)
        }
    override var categories: ObservableField<ArrayList<Any>> = ObservableField()
}
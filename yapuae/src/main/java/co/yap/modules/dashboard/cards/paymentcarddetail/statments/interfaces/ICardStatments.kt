package co.yap.modules.dashboard.cards.paymentcarddetail.statments.interfaces

import androidx.databinding.ObservableField
import co.yap.modules.dashboard.cards.paymentcarddetail.statments.adaptor.CardStatementsAdaptor
import co.yap.networking.cards.responsedtos.Card
import co.yap.networking.transactions.responsedtos.CardStatement
import co.yap.yapcore.IBase


interface ICardStatments {

    interface View : IBase.View<ViewModel>

    interface ViewModel : IBase.ViewModel<State> {
        var card: Card
        fun loadStatements(serialNumber: String)
        fun loadStatementsFromDashBoard()
        val adapter: ObservableField<CardStatementsAdaptor>
    }

    interface State : IBase.State {
        var year: ObservableField<String>
        var hasRecords: ObservableField<Boolean>
        var statementList: List<CardStatement>?
        var nextMonth: Boolean?
        var previousMonth: Boolean?
        var statementType:ObservableField<String>
    }
}
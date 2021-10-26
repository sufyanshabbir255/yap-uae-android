package co.yap.modules.dashboard.cards.analytics.fragments

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.RecyclerView
import co.yap.yapuae.BR
import co.yap.yapuae.R
import co.yap.yapuae.databinding.FragmentMerchantAnalyticsBinding
import co.yap.modules.dashboard.cards.analytics.adaptors.MerchantAnalyticsAdaptor
import co.yap.modules.dashboard.cards.analytics.interfaces.IMerchantAnalytics
import co.yap.modules.dashboard.cards.analytics.main.fragments.CardAnalyticsBaseFragment
import co.yap.modules.dashboard.cards.analytics.viewmodels.MerchantAnalyticsViewModel
import co.yap.networking.transactions.responsedtos.TxnAnalytic
import co.yap.yapcore.constants.Constants
import co.yap.yapcore.interfaces.OnItemClickListener

class MerchantAnalyticsFragment : CardAnalyticsBaseFragment<IMerchantAnalytics.ViewModel>(),
    IMerchantAnalytics.View {
    override fun getBindingVariable(): Int = BR.viewModel

    override fun getLayoutId(): Int = R.layout.fragment_merchant_analytics

    override val viewModel: MerchantAnalyticsViewModel
        get() = ViewModelProviders.of(this).get(MerchantAnalyticsViewModel::class.java)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initAdaptor()
        setObservers()
    }

    override fun setObservers() {
        viewModel.parentViewModel?.merchantAnalyticsItemLiveData?.observe(
            this,

            Observer { txnAnalytics ->
                if (txnAnalytics == null) {
                    return@Observer
                }
                getAdaptor().setList(txnAnalytics)
            })

        /*viewModel.parentViewModel?.selectedItemPositionParent?.observe(
            this,
            Observer { selectedPosition ->
                val view = getBinding().recycler.layoutManager?.findViewByPosition(selectedPosition)
                getBinding().recycler.removeOnScrollListener(onScrollListener)
                getBinding().recycler.addOnScrollListener(onScrollListener)
                getBinding().recycler.smoothScrollToPosition(selectedPosition)
            })*/
    }

    private fun initAdaptor() {
        getBinding().recycler.adapter = MerchantAnalyticsAdaptor(mutableListOf())
        getAdaptor().setItemListener(listener)
    }

    val listener = object : OnItemClickListener {
        override fun onItemClick(view: View, data: Any, pos: Int) {
            viewModel.parentViewModel?.selectedItemPosition?.value = pos
            navigateDetails(pos)
        }
    }

    private fun navigateDetails(pos: Int) {
        Constants.MERCHANT_TYPE = "merchant-name"
        val selectedItem = getAdaptor().getDataForPosition(pos)
        var category: ArrayList<String> = arrayListOf()
        category.clear()
        if (selectedItem.title?.contains("Other") == true) {
            category = selectedItem.categories ?: arrayListOf()
        } else {
            category.add(selectedItem.title ?: "")
        }
        navigate(
            R.id.cardAnalyticsDetailsFragment,
            bundleOf(
                Constants.TRANSACTION_DETAIL to TxnAnalytic(
                    title = selectedItem.title,
                    txnCount = selectedItem.txnCount,
                    totalSpending = selectedItem.totalSpending,
                    logoUrl = selectedItem.logoUrl,
                    totalSpendingInPercentage = selectedItem.totalSpendingInPercentage,
                    categories = category
                ),
                Constants.TRANSACTION_POSITION to pos
            )
        )
    }

    private val onScrollListener: RecyclerView.OnScrollListener =
        object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(
                recyclerView: RecyclerView,
                newState: Int
            ) {
                when (newState) {
                    RecyclerView.SCROLL_STATE_IDLE -> {
                        val pos = viewModel.parentViewModel?.selectedItemPositionParent?.value
                        pos?.let { position ->
                            val view =
                                getBinding().recycler.layoutManager?.findViewByPosition(position)
                        }
                    }
                }
            }
        }

    private fun getAdaptor(): MerchantAnalyticsAdaptor {
        return getBinding().recycler.adapter as MerchantAnalyticsAdaptor
    }

    private fun getBinding(): FragmentMerchantAnalyticsBinding {
        return (viewDataBinding as FragmentMerchantAnalyticsBinding)
    }

}
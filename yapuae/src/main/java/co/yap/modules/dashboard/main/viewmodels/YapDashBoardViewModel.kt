package co.yap.modules.dashboard.main.viewmodels

import android.app.Application
import androidx.lifecycle.MutableLiveData
import co.yap.app.YAPApplication
import co.yap.modules.dashboard.main.interfaces.IYapDashboard
import co.yap.modules.dashboard.main.states.YapDashBoardState
import co.yap.networking.authentication.AuthRepository
import co.yap.networking.customers.CustomersRepository
import co.yap.networking.interfaces.IRepositoryHolder
import co.yap.networking.messages.MessagesRepository
import co.yap.networking.models.RetroApiResponse
import co.yap.yapcore.SingleClickEvent
import co.yap.yapcore.constants.Constants
import co.yap.yapcore.helpers.SharedPreferenceManager
import co.yap.yapcore.helpers.Utils
import co.yap.yapcore.helpers.extentions.maskAccountNumber
import co.yap.yapcore.helpers.extentions.maskIbanNumber
import co.yap.yapcore.managers.SessionManager
import kotlinx.coroutines.delay

class YapDashBoardViewModel(application: Application) :
    YapDashboardChildViewModel<IYapDashboard.State>(application), IYapDashboard.ViewModel,
    IRepositoryHolder<MessagesRepository> {

    override val clickEvent: SingleClickEvent = SingleClickEvent()
    override val state: YapDashBoardState = YapDashBoardState()
    override val showUnverifedscreen: MutableLiveData<Boolean> = MutableLiveData()
    override val repository: MessagesRepository = MessagesRepository
    val customerRepository: CustomersRepository = CustomersRepository
    override val authRepository: AuthRepository = AuthRepository
    override var EVENT_LOGOUT_SUCCESS: Int = 101
    override var isYapHomeFragmentVisible: MutableLiveData<Boolean> = MutableLiveData(false)
    override var isYapStoreFragmentVisible: MutableLiveData<Boolean> = MutableLiveData(false)
    override var isYapCardsFragmentVisible: MutableLiveData<Boolean> = MutableLiveData(false)
    override var isYapMoreFragmentVisible: MutableLiveData<Boolean> = MutableLiveData(false)
    override var isUnverifiedScreenNotVisible: MutableLiveData<Boolean> = MutableLiveData(false)
    override var isShowHomeTour: MutableLiveData<Boolean> = MutableLiveData(false)
    override var isKycCompelted: MutableLiveData<Boolean> = MutableLiveData(false)


    override fun handlePressOnNavigationItem(id: Int) {
        clickEvent.setValue(id)
    }

    override fun copyAccountInfoToClipboard() {
        val info = "Account: ${state.accountNo}\nIBAN: ${state.ibanNo}"
        Utils.copyToClipboard(context, info)
        state.toast = "Copied to clipboard"
    }


    override fun getAccountInfo(): String {
        return "Name: ${SessionManager.user?.currentCustomer?.getFullName()}\n" +
                "IBAN: ${SessionManager.user?.iban}\n" +
                "Swift/BIC: ${SessionManager.user?.bank?.swiftCode}\n" +
                "Account: ${SessionManager.user?.accountNo}\n" +
                "Bank: ${SessionManager.user?.bank?.name}\n" +
                "Address: ${SessionManager.user?.bank?.address}\n"
    }

    override fun onCreate() {
        super.onCreate()
        updateVersion()
        getHelpPhoneNo()
        if (SessionManager.deepLinkFlowId.value == null) {
            launch {
                delay(1500)
                showUnverifedscreen.value =
                    SessionManager.user?.currentCustomer?.isEmailVerified.equals("N", true)
            }
        }
        state.isFounder.set(SessionManager.user?.currentCustomer?.founder)
    }

    override fun resendVerificationEmail(callBack: () -> Unit) {
        launch {
            state.loading = true
            when (val response =
                customerRepository.resendVerificationEmail()) {
                is RetroApiResponse.Success -> {
                    state.loading = false
                }
                is RetroApiResponse.Error -> {
                    state.loading = false
                    state.error = response.error.message
                }
            }
        }
    }

    private fun updateVersion() {
        state.appVersion.set(
            String.format(
                "Version %s (%s)",
                YAPApplication.configManager?.versionName ?: "",
                YAPApplication.configManager?.versionCode ?: ""
            )
        )
    }

    override fun onResume() {
        super.onResume()
        SessionManager.getCurrenciesFromServer { _, _ -> }
        populateState()
    }

    override fun populateState() {
        SessionManager.user?.let { it ->
            it.accountNo?.let { state.accountNo = it.maskAccountNumber() }
            it.iban?.let { state.ibanNo = it.maskIbanNumber() }
            state.fullName = it.currentCustomer.getFullName()
            state.firstName = it.currentCustomer.firstName
            state.userNameImage.set(it.currentCustomer.getPicture() ?: "")
        }
    }

    private fun getHelpPhoneNo() {
        launch {
            when (val response =
                repository.getHelpDeskContact()) {
                is RetroApiResponse.Success -> {
                    response.data.data?.let {
                        SessionManager.helpPhoneNumber = it
                    }
                }
                is RetroApiResponse.Error -> {
                }
            }
        }
    }

    override fun logout() {
        val deviceId: String? =
            SharedPreferenceManager.getInstance(context).getValueString(Constants.KEY_APP_UUID)
        launch {
            state.loading = true
            when (val response = authRepository.logout(deviceId.toString())) {
                is RetroApiResponse.Success -> {
                    clickEvent.setValue(EVENT_LOGOUT_SUCCESS)
                    state.loading = true
                }
                is RetroApiResponse.Error -> {
                    state.toast = response.error.message
                    state.loading = false
                }
            }
        }
    }

}
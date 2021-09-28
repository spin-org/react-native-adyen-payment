package com.rnlib.adyen.ui

import android.content.*
import android.content.res.Configuration
import android.os.Bundle
import android.os.IBinder
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer
import com.adyen.checkout.components.ActionComponentData
import com.adyen.checkout.components.ComponentError
import com.adyen.checkout.components.PaymentComponentState
import com.adyen.checkout.components.analytics.AnalyticEvent
import com.adyen.checkout.components.analytics.AnalyticsDispatcher
import com.adyen.checkout.components.model.PaymentMethodsApiResponse
import com.adyen.checkout.components.model.paymentmethods.PaymentMethod
import com.adyen.checkout.components.model.paymentmethods.StoredPaymentMethod
import com.adyen.checkout.components.model.payments.response.Action
import com.adyen.checkout.components.util.PaymentMethodTypes
import com.adyen.checkout.core.log.LogUtil
import com.adyen.checkout.core.log.Logger
import com.adyen.checkout.core.util.LocaleUtil
import com.adyen.checkout.dropin.R
import com.adyen.checkout.dropin.ui.LoadingDialogFragment
import com.adyen.checkout.dropin.ui.action.ActionComponentDialogFragment
import com.adyen.checkout.dropin.ui.stored.PreselectedStoredPaymentMethodFragment
import com.adyen.checkout.googlepay.GooglePayComponent
import com.adyen.checkout.googlepay.GooglePayComponentState
import com.adyen.checkout.googlepay.GooglePayConfiguration
import com.adyen.checkout.redirect.RedirectUtil
import com.adyen.checkout.wechatpay.WeChatPayUtils
import com.rnlib.adyen.ActionHandler
import com.rnlib.adyen.AdyenComponent
import com.rnlib.adyen.AdyenComponentConfiguration
import com.rnlib.adyen.service.AdyenDropInService
import com.rnlib.adyen.service.AdyenDropInServiceInterface
import com.rnlib.adyen.service.AdyenDropInServiceResult
import com.rnlib.adyen.ui.base.DropInBottomSheetDialogFragment
import com.rnlib.adyen.ui.component.CardComponentDialogFragment
import com.rnlib.adyen.ui.component.GenericComponentDialogFragment
import com.rnlib.adyen.ui.paymentmethods.PaymentMethodListDialogFragment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.json.JSONObject
import java.util.Locale

private val TAG = LogUtil.getTag()

private const val PRESELECTED_PAYMENT_METHOD_FRAGMENT_TAG = "PRESELECTED_PAYMENT_METHOD_FRAGMENT"
private const val PAYMENT_METHODS_LIST_FRAGMENT_TAG = "PAYMENT_METHODS_LIST_FRAGMENT"
private const val COMPONENT_FRAGMENT_TAG = "COMPONENT_DIALOG_FRAGMENT"
private const val ACTION_FRAGMENT_TAG = "ACTION_DIALOG_FRAGMENT"
private const val LOADING_FRAGMENT_TAG = "LOADING_DIALOG_FRAGMENT"

private const val PAYMENT_METHODS_RESPONSE_KEY = "PAYMENT_METHODS_RESPONSE_KEY"
private const val DROP_IN_CONFIGURATION_KEY = "DROP_IN_CONFIGURATION_KEY"
private const val DROP_IN_RESULT_INTENT = "DROP_IN_RESULT_INTENT"
private const val IS_WAITING_FOR_RESULT = "IS_WAITING_FOR_RESULT"

private const val ADYEN_COMPONENT_INTENT = "ADYEN_COMPONENT_INTENT"

private const val GOOGLE_PAY_REQUEST_CODE = 1

/**
 * Activity that presents the available PaymentMethods to the Shopper.
 */
@Suppress("TooManyFunctions")
class AdyenComponentActivity : AppCompatActivity(), DropInBottomSheetDialogFragment.Protocol, ActionHandler.ActionHandlingInterface {

    private lateinit var adyenComponentViewModel: AdyenComponentViewModel

    private lateinit var googlePayComponent: GooglePayComponent

    private lateinit var actionHandler: ActionHandler

    private var isWaitingResult = false

    private val loadingDialog = LoadingDialogFragment.newInstance()

    private val googlePayObserver: Observer<GooglePayComponentState> = Observer {
        if (it?.isValid == true) {
            requestPaymentsCall(it)
        }
    }

    private val googlePayErrorObserver: Observer<ComponentError> = Observer {
        Logger.d(TAG, "GooglePay error - ${it?.errorMessage}")
        onBackPressed()
    }

    private var adyenDropInService: AdyenDropInServiceInterface? = null
    private var serviceBound: Boolean = false

    // these queues exist for when a call is requested before the service is bound
    private var paymentDataQueue: PaymentComponentState<*>? = null
    private var actionDataQueue: ActionComponentData? = null

    private val serviceConnection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, binder: IBinder) {
            Logger.d(TAG, "onServiceConnected")
            val dropInBinder = binder as? AdyenDropInService.DropInBinder ?: return
            adyenDropInService = dropInBinder.getService()
            adyenDropInService?.observeResult(this@AdyenComponentActivity, Observer {
                handleDropInServiceResult(it)
            })

            paymentDataQueue?.let {
                Logger.d(TAG, "Sending queued payment request")
                requestPaymentsCall(it)
                paymentDataQueue = null
            }

            actionDataQueue?.let {
                Logger.d(TAG, "Sending queued action request")
                requestDetailsCall(it)
                actionDataQueue = null
            }
        }

        override fun onServiceDisconnected(className: ComponentName) {
            Logger.d(TAG, "onServiceDisconnected")
            adyenDropInService = null
        }
    }

    override fun attachBaseContext(newBase: Context?) {
        Logger.d(TAG, "attachBaseContext")
        super.attachBaseContext(createLocalizedContext(newBase))
    }

    @ExperimentalCoroutinesApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.d(TAG, "onCreate - $savedInstanceState")
        setContentView(R.layout.activity_drop_in)
        overridePendingTransition(0, 0)

        val bundle = savedInstanceState ?: intent.extras

        val initializationSuccessful = initializeBundleVariables(bundle)
        if (!initializationSuccessful) {
            terminateWithError("Initialization failed")
            return
        }

        val paymentType = adyenComponentViewModel.paymentMethodsApiResponse.paymentMethods!![0].type!!
        if (noDialogPresent()) {
            when {
                adyenComponentViewModel.showPreselectedStored || adyenComponentViewModel.showPreselectedStored -> {
                    showPreselectedDialog()
                }
                adyenComponentViewModel.adyenComponentConfiguration.dropIn -> {
                    showPaymentMethodsDialog()
                }
                paymentType == PaymentMethodTypes.GOOGLE_PAY || paymentType == PaymentMethodTypes.GOOGLE_PAY_LEGACY -> {
                    startGooglePay(
                        adyenComponentViewModel.getPaymentMethod(paymentType),
                        adyenComponentViewModel.adyenComponentConfiguration.getConfigurationForPaymentMethod(
                            PaymentMethodTypes.GOOGLE_PAY,
                            this
                        )
                    )
                }
                else -> {
                    showComponentDialog(adyenComponentViewModel.getPaymentMethod(paymentType))
                }
            }
        }

        actionHandler = ActionHandler(this, adyenComponentViewModel.adyenComponentConfiguration)
        actionHandler.restoreState(this, savedInstanceState)
        handleIntent(intent)

        sendAnalyticsEvent()
    }

    private fun noDialogPresent(): Boolean {
        return getFragmentByTag(PRESELECTED_PAYMENT_METHOD_FRAGMENT_TAG) == null &&
                getFragmentByTag(PAYMENT_METHODS_LIST_FRAGMENT_TAG) == null &&
                getFragmentByTag(COMPONENT_FRAGMENT_TAG) == null &&
                getFragmentByTag(ACTION_FRAGMENT_TAG) == null
    }

    // False positive from countryStartPosition
    @Suppress("MagicNumber")
    private fun createLocalizedContext(baseContext: Context?): Context? {
        if (baseContext == null) {
            return baseContext
        }

        val config = Configuration(baseContext.resources.configuration)
        val locale = Locale.getDefault()

        return try {
            config.setLocale(locale)
            baseContext.createConfigurationContext(config)
        } catch (e: IllegalArgumentException) {
            Logger.e(TAG, "Failed to parse locale $locale")
            baseContext
        }
    }

    private fun initializeBundleVariables(bundle: Bundle?): Boolean {
        if (bundle == null) {
            Logger.e(TAG, "Failed to initialize - bundle is null")
            return false
        }
        isWaitingResult = bundle.getBoolean(IS_WAITING_FOR_RESULT, false)
        val adyenComponentConfiguration: AdyenComponentConfiguration? = bundle.getParcelable(DROP_IN_CONFIGURATION_KEY)
        val paymentMethodsApiResponse: PaymentMethodsApiResponse? = bundle.getParcelable(PAYMENT_METHODS_RESPONSE_KEY)
        val resultHandlerIntent: Intent? = bundle.getParcelable(DROP_IN_RESULT_INTENT)
        return if (adyenComponentConfiguration != null && paymentMethodsApiResponse != null) {
            adyenComponentViewModel = getViewModel {
                AdyenComponentViewModel(
                    paymentMethodsApiResponse,
                    adyenComponentConfiguration,
                    resultHandlerIntent
                )
            }
            true
        } else {
            Logger.e(
                TAG,
                "Failed to initialize bundle variables " +
                        "- dropInConfiguration: ${if (adyenComponentConfiguration == null) "null" else "exists"} " +
                        "- paymentMethodsApiResponse: ${if (paymentMethodsApiResponse == null) "null" else "exists"}"
            )
            false
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            GOOGLE_PAY_REQUEST_CODE -> googlePayComponent.handleActivityResult(resultCode, data)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Logger.d(TAG, "onNewIntent")
        if (intent != null) {
            handleIntent(intent)
        } else {
            Logger.e(TAG, "Null intent")
        }
    }

    override fun onStart() {
        super.onStart()
        bindService()
    }

    private fun bindService() {
        val bound = AdyenDropInService.bindService(this, serviceConnection, adyenComponentViewModel.adyenComponentConfiguration.serviceComponentName)
        if (bound) {
            serviceBound = true
        } else {
            Logger.e(
                TAG,
                "Error binding to ${adyenComponentViewModel.adyenComponentConfiguration.serviceComponentName.className}. " +
                        "The system couldn't find the service or your client doesn't have permission to bind to it"
            )
        }
    }

    override fun onStop() {
        super.onStop()
        unbindService()
    }

    private fun unbindService() {
        if (serviceBound) {
            AdyenDropInService.unbindService(this, serviceConnection)
            serviceBound = false
        }
    }

    override fun requestPaymentsCall(paymentComponentState: PaymentComponentState<*>) {
        Logger.d(TAG, "requestPaymentsCall")
        if (adyenDropInService == null) {
            Logger.e(TAG, "service is disconnected, adding to queue")
            paymentDataQueue = paymentComponentState
            return
        }
        isWaitingResult = true
        setLoading(true)
        // include amount value if merchant passed it to the DropIn
        if (!adyenComponentViewModel.adyenComponentConfiguration.amount.isEmpty) {
            paymentComponentState.data.amount = adyenComponentViewModel.adyenComponentConfiguration.amount
        }
        adyenDropInService?.requestPaymentsCall(paymentComponentState)
    }

    override fun requestDetailsCall(actionComponentData: ActionComponentData) {
        Logger.d(TAG, "requestDetailsCall")
        if (adyenDropInService == null) {
            Logger.e(TAG, "service is disconnected, adding to queue")
            actionDataQueue = actionComponentData
            return
        }
        isWaitingResult = true
        setLoading(true)
        adyenDropInService?.requestDetailsCall(actionComponentData)
    }

    override fun showError(errorMessage: String, reason: String, terminate: Boolean) {
        Logger.d(TAG, "showError - message: $errorMessage")
        AlertDialog.Builder(this)
            .setTitle(R.string.error_dialog_title)
            .setMessage(errorMessage)
            .setOnDismissListener { this@AdyenComponentActivity.errorDialogDismissed(reason, terminate) }
            .setPositiveButton(R.string.error_dialog_button) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun errorDialogDismissed(reason: String, terminateDropIn: Boolean) {
        if (terminateDropIn) {
            terminateWithError(reason)
        } else {
            setLoading(false)
        }
    }

    override fun displayAction(action: Action) {
        Logger.d(TAG, "showActionDialog")
        setLoading(false)
        hideAllScreens()
        val actionFragment = ActionComponentDialogFragment.newInstance(action)
        actionFragment.show(supportFragmentManager, ACTION_FRAGMENT_TAG)
        actionFragment.setToHandleWhenStarting()
    }

    override fun onActionError(errorMessage: String) {
        showError(getString(R.string.action_failed), errorMessage, true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Logger.d(TAG, "onSaveInstanceState")

        outState.run {
            putParcelable(PAYMENT_METHODS_RESPONSE_KEY, adyenComponentViewModel.paymentMethodsApiResponse)
            putParcelable(DROP_IN_CONFIGURATION_KEY, adyenComponentViewModel.adyenComponentConfiguration)
            putBoolean(IS_WAITING_FOR_RESULT, isWaitingResult)

            actionHandler.saveState(this)
        }
    }

    override fun onResume() {
        super.onResume()
        setLoading(isWaitingResult)
    }

    override fun showPreselectedDialog() {
        Logger.d(TAG, "showPreselectedDialog")
        hideAllScreens()
        PreselectedStoredPaymentMethodFragment.newInstance(adyenComponentViewModel.preselectedStoredPayment)
            .show(supportFragmentManager, PRESELECTED_PAYMENT_METHOD_FRAGMENT_TAG)
    }

    override fun showPaymentMethodsDialog() {
        Logger.d(TAG, "showPaymentMethodsDialog")
        hideAllScreens()
        PaymentMethodListDialogFragment().show(supportFragmentManager, PAYMENT_METHODS_LIST_FRAGMENT_TAG)
    }

    override fun showStoredComponentDialog(storedPaymentMethod: StoredPaymentMethod, fromPreselected: Boolean) {
        Logger.d(TAG, "showStoredComponentDialog")
        hideAllScreens()
        val dialogFragment = when (storedPaymentMethod.type) {
            PaymentMethodTypes.SCHEME -> CardComponentDialogFragment
            else -> GenericComponentDialogFragment
        }.newInstance(storedPaymentMethod, adyenComponentViewModel.adyenComponentConfiguration, fromPreselected)

        dialogFragment.show(supportFragmentManager, COMPONENT_FRAGMENT_TAG)
    }

    override fun showComponentDialog(paymentMethod: PaymentMethod) {
        Logger.d(TAG, "showComponentDialog")
        hideAllScreens()
        val dialogFragment = when (paymentMethod.type) {
            PaymentMethodTypes.SCHEME -> CardComponentDialogFragment
            else -> GenericComponentDialogFragment
        }.newInstance(paymentMethod, adyenComponentViewModel.adyenComponentConfiguration)

        dialogFragment.show(supportFragmentManager, COMPONENT_FRAGMENT_TAG)
    }

    private fun hideAllScreens() {
        hideFragmentDialog(PRESELECTED_PAYMENT_METHOD_FRAGMENT_TAG)
        hideFragmentDialog(PAYMENT_METHODS_LIST_FRAGMENT_TAG)
        hideFragmentDialog(COMPONENT_FRAGMENT_TAG)
        hideFragmentDialog(ACTION_FRAGMENT_TAG)
    }

    override fun terminateDropIn() {
        Logger.d(TAG, "terminateDropIn")
        adyenComponentViewModel.adyenComponentConfiguration.resultHandlerIntent
            .putExtra(AdyenComponent.ERROR_REASON_USER_CANCELED, "Cancelled").let { intent ->
            startActivity(intent)
        }
        overridePendingTransition(0, R.anim.fade_out)
    }

    override fun startGooglePay(paymentMethod: PaymentMethod, googlePayConfiguration: GooglePayConfiguration) {
        Logger.d(TAG, "startGooglePay")
        googlePayComponent = GooglePayComponent.PROVIDER.get(this, paymentMethod, googlePayConfiguration)
        googlePayComponent.observe(this@AdyenComponentActivity, googlePayObserver)
        googlePayComponent.observeErrors(this@AdyenComponentActivity, googlePayErrorObserver)

        hideFragmentDialog(PAYMENT_METHODS_LIST_FRAGMENT_TAG)
        googlePayComponent.startGooglePayScreen(this, GOOGLE_PAY_REQUEST_CODE)
    }

    private fun handleDropInServiceResult(adyenDropInServiceResult: AdyenDropInServiceResult) {
        Logger.d(TAG, "handleDropInServiceResult - ${adyenDropInServiceResult::class.simpleName}")
        isWaitingResult = false
        when (adyenDropInServiceResult) {
            is AdyenDropInServiceResult.Finished -> {
                sendResult(adyenDropInServiceResult.result)
            }
            is AdyenDropInServiceResult.Action -> {
                val action = Action.SERIALIZER.deserialize(JSONObject(adyenDropInServiceResult.actionJSON))
                actionHandler.handleAction(this, action, ::sendResult)
            }
            is AdyenDropInServiceResult.Error -> {
                Logger.d(TAG, "handleDropInServiceResult ERROR - reason: ${adyenDropInServiceResult.reason}")
                val reason = adyenDropInServiceResult.reason ?: "Unspecified reason"
                if (adyenDropInServiceResult.errorMessage == null) {
                    showError(getString(R.string.payment_failed), reason, adyenDropInServiceResult.dismissDropIn)
                } else {
                    showError(adyenDropInServiceResult.errorMessage, reason, adyenDropInServiceResult.dismissDropIn)
                }
            }
        }
    }

    private fun sendResult(content: String) {
        adyenComponentViewModel.adyenComponentConfiguration.resultHandlerIntent
            .putExtra(AdyenComponent.RESULT_KEY, content).let { intent ->
                startActivity(intent)
            }
        terminateSuccessfully()
    }

    private fun terminateSuccessfully() {
        Logger.d(TAG, "terminateSuccessfully")
        terminate()
    }

    private fun terminateWithError(reason: String) {
        Logger.d(TAG, "terminateWithError")
        adyenComponentViewModel.adyenComponentConfiguration.resultHandlerIntent
            .putExtra(AdyenComponent.ERROR_REASON_KEY, reason).let { intent ->
                startActivity(intent)
            }
        terminate()
    }

    private fun terminate() {
        Logger.d(TAG, "terminate")
        finish()
        overridePendingTransition(0, R.anim.fade_out)
    }

    private fun handleIntent(intent: Intent) {
        Logger.d(TAG, "handleIntent: action - ${intent.action}")
        isWaitingResult = false

        if (WeChatPayUtils.isResultIntent(intent)) {
            Logger.d(TAG, "isResultIntent")
            actionHandler.handleWeChatPayResponse(intent)
        }

        when (intent.action) {
            // Redirect response
            Intent.ACTION_VIEW -> {
                val data = intent.data
                if (data != null && data.toString().startsWith(RedirectUtil.REDIRECT_RESULT_SCHEME)) {
                    actionHandler.handleRedirectResponse(intent)
                } else {
                    Logger.e(TAG, "Unexpected response from ACTION_VIEW - ${intent.data}")
                }
            }
            else -> {
                Logger.e(TAG, "Unable to find action")
            }
        }
    }

    private fun sendAnalyticsEvent() {
        Logger.d(TAG, "sendAnalyticsEvent")
        val analyticEvent = AnalyticEvent.create(
            this,
            AnalyticEvent.Flavor.DROPIN,
            "dropin",
            adyenComponentViewModel.adyenComponentConfiguration.shopperLocale
        )
        AnalyticsDispatcher.dispatchEvent(this, adyenComponentViewModel.adyenComponentConfiguration.environment, analyticEvent)
    }

    private fun hideFragmentDialog(tag: String) {
        getFragmentByTag(tag)?.dismiss()
    }

    private fun getFragmentByTag(tag: String): DialogFragment? {
        val fragment = supportFragmentManager.findFragmentByTag(tag)
        return fragment as DialogFragment?
    }

    private fun setLoading(showLoading: Boolean) {
        if (showLoading) {
            if (!loadingDialog.isAdded) {
                loadingDialog.show(supportFragmentManager, LOADING_FRAGMENT_TAG)
            }
        } else {
            getFragmentByTag(LOADING_FRAGMENT_TAG)?.dismiss()
        }
    }

    companion object {
        fun createIntent(
            context: Context,
            adyenComponentConfiguration: AdyenComponentConfiguration,
            paymentMethodsApiResponse: PaymentMethodsApiResponse,
            resultHandlerIntent: Intent?
        ): Intent {
            val intent = Intent(context, AdyenComponentActivity::class.java)
            intent.putExtra(PAYMENT_METHODS_RESPONSE_KEY, paymentMethodsApiResponse)
            intent.putExtra(DROP_IN_CONFIGURATION_KEY, adyenComponentConfiguration)
            intent.putExtra(DROP_IN_RESULT_INTENT, resultHandlerIntent)
            intent.putExtra(ADYEN_COMPONENT_INTENT, adyenComponentConfiguration.resultHandlerIntent)
            return intent
        }
    }
}


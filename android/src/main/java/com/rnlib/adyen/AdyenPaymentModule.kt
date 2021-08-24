package com.rnlib.adyen

import android.app.Activity
import android.content.Intent
import android.content.Context

import com.adyen.checkout.components.model.PaymentMethodsApiResponse
import com.adyen.checkout.bcmc.BcmcConfiguration
import com.adyen.checkout.card.CardConfiguration
import com.adyen.checkout.googlepay.GooglePayConfiguration
import com.adyen.checkout.core.exception.CheckoutException
import com.adyen.checkout.core.log.Logger
import com.adyen.checkout.dropin.DropIn
import com.adyen.checkout.core.api.Environment
import com.adyen.checkout.components.model.payments.Amount
import android.util.Log
import android.widget.Toast

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ActivityEventListener
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.Promise

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Call
import org.json.JSONObject
import java.util.Locale

import com.adyen.checkout.components.model.paymentmethods.PaymentMethod
import com.adyen.checkout.components.util.PaymentMethodTypes
import com.adyen.checkout.entercash.EntercashConfiguration
import com.adyen.checkout.eps.EPSConfiguration
import com.adyen.checkout.ideal.IdealConfiguration
import com.adyen.checkout.molpay.MolpayConfiguration
import com.adyen.checkout.dotpay.DotpayConfiguration
import com.adyen.checkout.openbanking.OpenBankingConfiguration
import com.adyen.checkout.sepa.SepaConfiguration

import com.google.android.gms.wallet.WalletConstants

import com.rnlib.adyen.ui.LoadingDialogFragment
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.adyen.checkout.components.base.AddressVisibility
import com.rnlib.adyen.service.AdyenDropInService
import org.json.JSONArray

class AdyenPaymentModule(private var reactContext : ReactApplicationContext) : ReactContextBaseJavaModule(reactContext),ActivityEventListener {

    
    private val loadingDialog  = LoadingDialogFragment.newInstance()
    private var promise: Promise? = null
    private var emitEvent : Boolean = false

    companion object {
        private const val REACT_CLASS = "AdyenPayment"
        const val MERCHANT_ACCOUNT_KEY = "merchantAccount"
        const val CLIENT_KEY = "client_key"
        const val BASE_URL_KEY = "base_url"
        const val ENVIRONMENT_KEY = "environment"
        const val ADDITIONAL_HTTP_HEADERS_KEY = "additional_http_headers"

        private val TAG: String = AdyenPaymentModule::class.java.simpleName
        private const val LOADING_FRAGMENT_TAG = "LOADING_DIALOG_FRAGMENT"
        private var paymentData : JSONObject = JSONObject()
        private val configData : AppServiceConfigData = AppServiceConfigData()
        private var paymentMethodsApiResponse : PaymentMethodsApiResponse = PaymentMethodsApiResponse()

        fun  getPaymentData(): JSONObject{
           return paymentData
        }
        fun  getAppServiceConfigData(): AppServiceConfigData{
           return configData
        }
    }
    

    init {
        Logger.setLogcatLevel(Log.DEBUG)
        reactApplicationContext.addActivityEventListener(this)

    }

    fun emitDeviceEvent(eventName: String, eventData: WritableMap?) {
        reactApplicationContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java).emit(eventName, eventData)
    }

    override fun getName() = REACT_CLASS

    fun getAmt(amtJson : JSONObject) : Amount {
        val amount = Amount()
        amount.currency = amtJson.getString("currency") as String
        amount.value = amtJson.getInt("value")
        return amount;
    }

    fun sendSuccess(message : JSONObject){
        if(promise != null){
            promise!!.resolve(ReactNativeUtils.convertJsonToMap(message))
        }
        if(emitEvent){
            emitEvent = false
            val evtObj : JSONObject = JSONObject()
            evtObj.put("message",message)
            emitDeviceEvent("onSuccess",ReactNativeUtils.convertJsonToMap(evtObj))
        }
    }
    
    fun sendFailure(code : String,message : String){
        if(promise != null){
            promise!!.reject(code, message)
            //this.promise = null
        }
        if(emitEvent){
            emitEvent = false
            val evtObj : JSONObject = JSONObject()
            evtObj.put("code",code)
            evtObj.put("message",message)
            emitDeviceEvent("onError",ReactNativeUtils.convertJsonToMap(evtObj))
        }
    }

    @ReactMethod
    fun initialize(appServiceConfigData : ReadableMap){
        val appServiceConfigJSON : JSONObject = ReactNativeUtils.convertMapToJson(appServiceConfigData)
        val headersMap: MutableMap<String, String> = linkedMapOf()
        headersMap["Accept"] = "application/json"
        headersMap["Accept-Charset"] = "utf-8"
        headersMap["Content-Type"] = "application/json"
        val additionalHttpHeaders : JSONObject =  appServiceConfigJSON.getJSONObject(ADDITIONAL_HTTP_HEADERS_KEY)
        for(key in additionalHttpHeaders.keys()){
            headersMap[key as String] = additionalHttpHeaders.getString(key)
        }
        configData.environment = appServiceConfigJSON.getString(ENVIRONMENT_KEY)
        configData.baseUrl = appServiceConfigJSON.getString(BASE_URL_KEY)
        configData.appUrlHeaders = headersMap
        configData.clientKey = appServiceConfigJSON.getString(CLIENT_KEY)
    }
    
    @ReactMethod
    fun startPaymentPromise(component : String,componentData : ReadableMap,paymentDetails : ReadableMap,promise : Promise){
        this.promise = promise
        this.showPayment(component,componentData,paymentDetails)
    }

    @ReactMethod
    fun startPayment(component : String,componentData : ReadableMap,paymentDetails : ReadableMap) {
        this.emitEvent = true
        this.showPayment(component,componentData,paymentDetails)
    }

    fun showPayment(component : String,componentData : ReadableMap,paymentDetails : ReadableMap) {
        paymentData = ReactNativeUtils.convertMapToJson(paymentDetails)
        val compData = ReactNativeUtils.convertMapToJson(componentData)
        val additionalData: MutableMap<String, String> = linkedMapOf()
        val amount = getAmt(paymentData.getJSONObject("amount"))
        val paymentMethodReq : PaymentMethodsRequest = PaymentMethodsRequest(amount.value)

        val paymentMethods : Call<ResponseBody> = ApiService.checkoutApi(configData.baseUrl).paymentMethods(configData.appUrlHeaders,paymentMethodReq)
        setLoading(true)
        paymentMethods.enqueue(object : retrofit2.Callback<ResponseBody> {
            override fun onResponse(call : Call<ResponseBody>,response : Response<ResponseBody>) {
                setLoading(false)
                if (response.isSuccessful) {
                    // tasks available
                    val pmApiResponse : PaymentMethodsApiResponse = PaymentMethodsApiResponse.SERIALIZER.deserialize(JSONObject(response.body()?.string()))
                    val paymentMethodsList : MutableList<PaymentMethod> = mutableListOf<PaymentMethod>()
                    if(component != "dropin"){
                        for (each in pmApiResponse.paymentMethods!!) {
                            Log.i(TAG,each.toString())
                            if (each.type == component) {
                                paymentMethodsList.add(each)
                                break
                            }
                        }
                        pmApiResponse.paymentMethods = paymentMethodsList
                        paymentMethodsApiResponse = pmApiResponse
                        when(component){
                            PaymentMethodTypes.GOOGLE_PAY -> showGooglePayComponent(compData)
                            PaymentMethodTypes.GOOGLE_PAY_LEGACY -> showGooglePayComponent(compData)
                            PaymentMethodTypes.SCHEME -> showCardComponent(compData)
                            PaymentMethodTypes.IDEAL -> showIdealComponent(compData)
                            PaymentMethodTypes.MOLPAY_MALAYSIA -> showMOLPayComponent(component,compData)
                            PaymentMethodTypes.MOLPAY_THAILAND -> showMOLPayComponent(component,compData)
                            PaymentMethodTypes.MOLPAY_VIETNAM -> showMOLPayComponent(component,compData)
                            PaymentMethodTypes.DOTPAY -> showDotPayComponent(compData)
                            PaymentMethodTypes.EPS -> showEPSComponent(compData)
                            PaymentMethodTypes.ENTERCASH -> showEnterCashComponent(compData)
                            PaymentMethodTypes.OPEN_BANKING -> showOpenBankingComponent(compData)
                            PaymentMethodTypes.SEPA -> showSEPAComponent(compData)
                            PaymentMethodTypes.BCMC -> showBCMCComponent(compData)
                            else -> {
                                val evtObj : JSONObject = JSONObject()
                                evtObj.put("code","ERROR_UNKNOWN_PAYMENT_METHOD")
                                evtObj.put("message","Unknown Payment Method")
                                emitDeviceEvent("onError",ReactNativeUtils.convertJsonToMap(evtObj))
                            }
                        }
                    }else{
                        paymentMethodsApiResponse = pmApiResponse
                        showDropInComponent(compData)
                    }
                } else {
                   val byteArray = response.errorBody()?.bytes()
                    if (byteArray != null) {
                        Log.e(TAG, "errorBody - ${String(byteArray)}")
                    }
                }
            }
            override fun onFailure(call: Call<ResponseBody> ?, t: Throwable ?) {
                // something went completely south (like no internet connection)
                setLoading(false)
                sendFailure("ERROR_GENERAL",t!!.message.toString())

                // TODO: Define what to do if [t.message] is null
                Log.d("Error", t.message!!)
            }
        })
    }

    private fun createConfigurationBuilder(context : Context) : AdyenComponentConfiguration.Builder {
        val resultIntent : Intent = (context.packageManager.getLaunchIntentForPackage(context.applicationContext.packageName)) as Intent
        resultIntent.flags = (Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val adyenConfigurationBuilder = AdyenComponentConfiguration.Builder(
            context,
            AdyenComponentService::class.java,
            configData.clientKey
        )
        when (configData.environment) {
            "test" -> {adyenConfigurationBuilder.setEnvironment(Environment.TEST)}
            "eu" -> {adyenConfigurationBuilder.setEnvironment(Environment.EUROPE)}
            "us" -> {adyenConfigurationBuilder.setEnvironment(Environment.UNITED_STATES)}
            "au" -> {adyenConfigurationBuilder.setEnvironment(Environment.AUSTRALIA)}
            else -> {
                adyenConfigurationBuilder.setEnvironment(Environment.TEST)
            }
        }
        //adyenConfigurationBuilder.setEnvironment(Environment.TEST)
        val countryCode = paymentData.getString("countryCode")
        val language = Locale.getDefault().getDisplayLanguage()
        val shopperLocale = Locale(language,countryCode)
        //val shoppersLocale = Locale(paymentData.getString("shopperLocale").toLowerCase().split("_")[0])
        adyenConfigurationBuilder.setShopperLocale(shopperLocale)
        try {
            adyenConfigurationBuilder.setAmount(getAmt(paymentData.getJSONObject("amount")))
        } catch (e: CheckoutException) {
            Log.e(TAG, "Amount not valid", e)
        }
        return adyenConfigurationBuilder
    }

    @Suppress("UNUSED_PARAMETER")
    private fun showIdealComponent(componentData : JSONObject){
        val context = reactApplicationContext
        val idealConfiguration = IdealConfiguration.Builder(context, configData.clientKey).build()
        val configBuilder : AdyenComponentConfiguration.Builder = createConfigurationBuilder(context)
        configBuilder.addIdealConfiguration(idealConfiguration)
        AdyenComponent.startPayment(currentActivity!!, paymentMethodsApiResponse, configBuilder.build())
    }

    @Suppress("UNUSED_PARAMETER")
    private fun showMOLPayComponent(component : String,componentData : JSONObject){
        val context = reactApplicationContext
        val molPayConfiguration = MolpayConfiguration.Builder(context, configData.clientKey).build()
        val configBuilder : AdyenComponentConfiguration.Builder = createConfigurationBuilder(context)
        when (component){
            PaymentMethodTypes.MOLPAY_MALAYSIA -> configBuilder.addMolpayMalasyaConfiguration(molPayConfiguration)
            PaymentMethodTypes.MOLPAY_THAILAND -> configBuilder.addMolpayThailandConfiguration(molPayConfiguration)
            PaymentMethodTypes.MOLPAY_VIETNAM -> configBuilder.addMolpayVietnamConfiguration(molPayConfiguration)
        }
        AdyenComponent.startPayment(currentActivity!!, paymentMethodsApiResponse, configBuilder.build())
    }

    @Suppress("UNUSED_PARAMETER")
    private fun showDotPayComponent(componentData : JSONObject){
        val context = reactApplicationContext
        val dotPayConfiguration = DotpayConfiguration.Builder(context, configData.clientKey).build()
        val configBuilder : AdyenComponentConfiguration.Builder = createConfigurationBuilder(context)
        configBuilder.addDotpayConfiguration(dotPayConfiguration)
        AdyenComponent.startPayment(currentActivity!!, paymentMethodsApiResponse, configBuilder.build())
    }

    @Suppress("UNUSED_PARAMETER")
    private fun showEPSComponent(componentData : JSONObject){
        val context = reactApplicationContext
        val epsConfiguration = EPSConfiguration.Builder(context, configData.clientKey).build()
        val configBuilder : AdyenComponentConfiguration.Builder = createConfigurationBuilder(context)
        configBuilder.addEpsConfiguration(epsConfiguration)
        AdyenComponent.startPayment(currentActivity!!, paymentMethodsApiResponse, configBuilder.build())
    }

    @Suppress("UNUSED_PARAMETER")
    private fun showEnterCashComponent(componentData : JSONObject){
        val context = reactApplicationContext
        val enterCashConfiguration = EntercashConfiguration.Builder(context, configData.clientKey).build()
        val configBuilder : AdyenComponentConfiguration.Builder = createConfigurationBuilder(context)
        configBuilder.addEntercashConfiguration(enterCashConfiguration)
        AdyenComponent.startPayment(currentActivity!!, paymentMethodsApiResponse, configBuilder.build())
    }

    @Suppress("UNUSED_PARAMETER")
    private fun showOpenBankingComponent(componentData : JSONObject){
        val context = reactApplicationContext
        val openBankingConfiguration = OpenBankingConfiguration.Builder(context, configData.clientKey).build()
        val configBuilder : AdyenComponentConfiguration.Builder = createConfigurationBuilder(context)
        configBuilder.addOpenBankingConfiguration(openBankingConfiguration)
        AdyenComponent.startPayment(currentActivity!!, paymentMethodsApiResponse, configBuilder.build())
    }

    @Suppress("UNUSED_PARAMETER")
    private fun showSEPAComponent(componentData : JSONObject){
        val context = reactApplicationContext
        val sepaConfiguration = SepaConfiguration.Builder(context, configData.clientKey).build()
        val configBuilder : AdyenComponentConfiguration.Builder = createConfigurationBuilder(context)
        configBuilder.addSepaConfiguration(sepaConfiguration)
        AdyenComponent.startPayment(currentActivity!!, paymentMethodsApiResponse, configBuilder.build())
    }

    @Suppress("UNUSED_PARAMETER")
    private fun showBCMCComponent(componentData : JSONObject){
        val context = reactApplicationContext
        val bcmcComponent : JSONObject = componentData.getJSONObject(PaymentMethodTypes.BCMC)
        val bcmcConfiguration = BcmcConfiguration.Builder(context, configData.clientKey).build()
        val configBuilder : AdyenComponentConfiguration.Builder = createConfigurationBuilder(context)
        configBuilder.addBcmcConfiguration(bcmcConfiguration)
        AdyenComponent.startPayment(currentActivity!!, paymentMethodsApiResponse, configBuilder.build())
    }

    @Suppress("UNUSED_PARAMETER")
    private fun showCardComponent(componentData : JSONObject){
        val context = reactApplicationContext
        val cardComponent : JSONObject = componentData.getJSONObject(PaymentMethodTypes.SCHEME)
        val shouldShowPostalCode = cardComponent.getBoolean("shouldShowPostalCode")
        val cardConfiguration = CardConfiguration.Builder(context, configData.clientKey)
                            .setShowStorePaymentField(cardComponent.getBoolean("shouldShowSCAToggle"))
                            .setAddressVisibility(if (shouldShowPostalCode) AddressVisibility.POSTAL_CODE else AddressVisibility.NONE)
                            .build()
        val configBuilder : AdyenComponentConfiguration.Builder = createConfigurationBuilder(context)
        configBuilder.addCardConfiguration(cardConfiguration)
        AdyenComponent.startPayment(currentActivity!!, paymentMethodsApiResponse, configBuilder.build())
    }

    @Suppress("UNUSED_PARAMETER")
    private fun showGooglePayComponent(componentData: JSONObject) {
        val context = reactApplicationContext
        val gpayComponent : JSONObject = componentData.getJSONObject(PaymentMethodTypes.GOOGLE_PAY_LEGACY)
        val merchantAccount =  gpayComponent.getString(MERCHANT_ACCOUNT_KEY)
        val googlePayConfigBuilder = GooglePayConfiguration.Builder(context, configData.clientKey)
        googlePayConfigBuilder.setMerchantAccount(merchantAccount)
        when (configData.environment) {
            "test" -> {googlePayConfigBuilder.setGooglePayEnvironment(WalletConstants.ENVIRONMENT_TEST)}
            "live" -> {googlePayConfigBuilder.setGooglePayEnvironment(WalletConstants.ENVIRONMENT_PRODUCTION)}
            "eu" -> {googlePayConfigBuilder.setGooglePayEnvironment(WalletConstants.ENVIRONMENT_PRODUCTION)}
            "us" -> {googlePayConfigBuilder.setGooglePayEnvironment(WalletConstants.ENVIRONMENT_PRODUCTION)}
            "au" -> {googlePayConfigBuilder.setGooglePayEnvironment(WalletConstants.ENVIRONMENT_PRODUCTION)}
            else -> {
                googlePayConfigBuilder.setGooglePayEnvironment(WalletConstants.ENVIRONMENT_PRODUCTION)
            }
        }
        val amount = getAmt(paymentData.getJSONObject("amount"))
        googlePayConfigBuilder.setAmount(amount)
        googlePayConfigBuilder.setCountryCode(paymentData.getString("countryCode"))
        val googlePayConfig = googlePayConfigBuilder.build()
        val configBuilder : AdyenComponentConfiguration.Builder = createConfigurationBuilder(context)
        configBuilder.addGooglePayConfiguration(googlePayConfig)
        AdyenComponent.startPayment(currentActivity!!, paymentMethodsApiResponse, configBuilder.build())
    }
 
    private fun showDropInComponent(componentData : JSONObject) {

        Log.d(TAG, "startDropIn")
        val context = reactApplicationContext
        val localeArr = paymentData.getString("shopperLocale").split("_")
        val shopperLocale = Locale(localeArr[0],localeArr[1])

        val googlePayConfigBuilder = GooglePayConfiguration.Builder(context, configData.clientKey)
        when (configData.environment) {
            "test" -> {googlePayConfigBuilder.setGooglePayEnvironment(WalletConstants.ENVIRONMENT_TEST)}
            "live" -> {googlePayConfigBuilder.setGooglePayEnvironment(WalletConstants.ENVIRONMENT_PRODUCTION)}
            "eu" -> {googlePayConfigBuilder.setGooglePayEnvironment(WalletConstants.ENVIRONMENT_PRODUCTION)}
            "us" -> {googlePayConfigBuilder.setGooglePayEnvironment(WalletConstants.ENVIRONMENT_PRODUCTION)}
            "au" -> {googlePayConfigBuilder.setGooglePayEnvironment(WalletConstants.ENVIRONMENT_PRODUCTION)}
            else -> {
                googlePayConfigBuilder.setGooglePayEnvironment(WalletConstants.ENVIRONMENT_PRODUCTION)
            }
        }
        googlePayConfigBuilder.setCountryCode(paymentData.getString("countryCode"))
        val googlePayConfig = googlePayConfigBuilder.build()

        val cardComponent : JSONObject = componentData.getJSONObject(PaymentMethodTypes.SCHEME)
        val cardConfiguration = CardConfiguration.Builder(context, configData.clientKey)
                            .setShopperReference(paymentData.getString("shopperReference"))
                            .setShopperLocale(shopperLocale)
                            .setShowStorePaymentField(cardComponent.optBoolean("showStorePaymentField"))
                            .build()

        val bcmcComponent : JSONObject = if(componentData.has(PaymentMethodTypes.BCMC))  componentData.getJSONObject(PaymentMethodTypes.BCMC) else JSONObject()
        var bcmcConfiguration : BcmcConfiguration? = null
        if(bcmcComponent.length() != 0){
          bcmcConfiguration = BcmcConfiguration.Builder(context, configData.clientKey)
                                .setShopperLocale(shopperLocale)
                                .build()
        }

        val resultIntent = Intent(reactContext as Context, super.getCurrentActivity()!!::class.java)
        resultIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        
        val adyenComponentConfigurationBuilder = AdyenComponentConfiguration.Builder(
            super.getCurrentActivity() as Context,
            AdyenDropInService::class.java,
            configData.clientKey
        ).addCardConfiguration(cardConfiguration)
            .addGooglePayConfiguration(googlePayConfig)

        if((bcmcComponent.length() != 0) && bcmcConfiguration != null){
          adyenComponentConfigurationBuilder.addBcmcConfiguration(bcmcConfiguration)
        }

        when (configData.environment) {
            "test" -> {adyenComponentConfigurationBuilder.setEnvironment(Environment.TEST)}
            "live" -> {adyenComponentConfigurationBuilder.setEnvironment(Environment.EUROPE)}
            "eu" -> {adyenComponentConfigurationBuilder.setEnvironment(Environment.EUROPE)}
            "us" -> {adyenComponentConfigurationBuilder.setEnvironment(Environment.UNITED_STATES)}
            "au" -> {adyenComponentConfigurationBuilder.setEnvironment(Environment.AUSTRALIA)}
            else -> {
                adyenComponentConfigurationBuilder.setEnvironment(Environment.TEST)
            }
        }
        
        adyenComponentConfigurationBuilder.setShopperLocale(shopperLocale)

        val amount = Amount()
        val amtJson : JSONObject  = paymentData.getJSONObject("amount")
        amount.currency = amtJson.getString("currency")
        amount.value = amtJson.getInt("value")

        try {
            adyenComponentConfigurationBuilder.setAmount(amount)
        } catch (e: CheckoutException) {
            Log.e(TAG, "Amount $amount not valid", e)
        }

        adyenComponentConfigurationBuilder.setDropIn(true)
        
        AdyenComponent.startPayment(currentActivity!!, paymentMethodsApiResponse, adyenComponentConfigurationBuilder.build())
        
    }

    override fun onNewIntent(intent: Intent?) {
        Log.d(TAG, "onNewIntent")
        if (intent?.hasExtra(AdyenComponent.RESULT_KEY) == true) {
            // TODO: Define what to do if [intent.getStringExtra(AdyenComponent.RESULT_KEY)] is null
            Log.d(TAG,intent.getStringExtra(AdyenComponent.RESULT_KEY)!!)
            //Toast.makeText(getReactApplicationContext(), intent.getStringExtra(DropIn.RESULT_KEY), Toast.LENGTH_SHORT).show()
            val response : JSONObject = JSONObject(intent.getStringExtra(AdyenComponent.RESULT_KEY))
            sendResponse(response)
        }else if(intent?.hasExtra(AdyenComponent.ERROR_REASON_USER_CANCELED) == true){
//            sendFailure("ERROR_CANCELLED","Transaction Cancelled")
        }
    }

    override fun onActivityResult(activity:Activity, requestCode:Int, resultCode:Int, data: Intent?){
       Log.d(TAG, "Calling activity result")
        parseActivityResult(requestCode, resultCode, data)
    }

    private fun sendResponse(response : JSONObject){
        val resultType : String = response.get("resultType").toString()
        if(resultType == "SUCCESS"){
            val detailsResponse : JSONObject = response.getJSONObject("message")
            val rsCode : String = detailsResponse.getString("resultCode")
            if(rsCode == "Authorised" || rsCode == "Received" || rsCode == "Pending"){
                val message : JSONObject = JSONObject()
                val addt_data_obj : JSONObject = if(detailsResponse.has("additionalData"))  detailsResponse.getJSONObject("additionalData") else JSONObject()
                val creditCards : JSONArray? = if(detailsResponse.has("payments"))  detailsResponse.getJSONArray("payments") else null
                message.put("resultCode", detailsResponse.getString("resultCode"))
                message.put("merchantReference", detailsResponse.getString("merchantReference"))
                message.put("pspReference", detailsResponse.getString("pspReference"))
                message.put("additionalData", addt_data_obj)
                if(creditCards != null)  message.put("creditCards", creditCards)

                sendSuccess(message)
            }else if(rsCode == "Refused" || rsCode == "Error"){
                val err_refusal_code = detailsResponse.getString("refusalReasonCode")
                val err_code = when(err_refusal_code) {
                    "0" -> "ERROR_GENERAL"
                    "2" -> "ERROR_TRANSACTION_REFUSED"
                    "3" -> "ERROR_REFERRAL"
                    "4" -> "ERROR_ACQUIRER"
                    "5" -> "ERROR_BLOCKED_CARD"
                    "6" -> "ERROR_EXPIRED_CARD"
                    "7" -> "ERROR_INVALID_AMOUNT"
                    "8" -> "ERROR_INVALID_CARDNUMBER"
                    "9" -> "ERROR_ISSUER_UNAVAILABLE"
                    "10" -> "ERROR_BANK_NOT_SUPPORTED"
                    "11" -> "ERROR_3DSECURE_AUTH_FAILED"
                    "12" -> "ERROR_NO_ENOUGH_BALANCE"
                    "14" -> "ERROR_FRAUD_DETECTED"
                    "15" -> "ERROR_CANCELLED"
                    "16" -> "ERROR_CANCELLED"
                    "17" -> "ERROR_INVALID_PIN"
                    "18" -> "ERROR_PIN_RETRY_EXCEEDED"
                    "19" -> "ERROR_UNABLE_VALIDATE_PIN"
                    "20" -> "ERROR_FRAUD_DETECTED"
                    "21" -> "ERROR_SUBMMISSION_ADYEN"
                    "23" -> "ERROR_TRANSACTION_REFUSED"
                    "24" -> "ERROR_CVC_DECLINED"
                    "25" -> "ERROR_RESTRICTED_CARD"
                    "27" -> "ERROR_DO_NOT_HONOR"
                    "28" -> "ERROR_WDRW_AMOUNT_EXCEEDED"
                    "29" -> "ERROR_WDRW_COUNT_EXCEEDED"
                    "31" -> "ERROR_FRAUD_DETECTED"
                    "32" -> "ERROR_AVS_DECLINED"
                    "33" -> "ERROR_CARD_ONLINE_PIN"
                    "34" -> "ERROR_NO_ACCT_ATCHD_CARD"
                    "35" -> "ERROR_NO_ACCT_ATCHD_CARD"
                    "36" -> "ERROR_MOBILE_PIN"
                    "37" -> "ERROR_CONTACTLESS_FALLBACK"
                    "38" -> "ERROR_AUTH_REQUIRED"
                    else -> "ERROR_UNKNOWN"
                }
                sendFailure(err_code,detailsResponse.getString("refusalReason"))
            }else if(rsCode == "Cancelled"){
//                sendFailure("ERROR_CANCELLED","Transaction Cancelled")
            }else{
                sendFailure("ERROR_UNKNOWN","Unknown Error")
            }
        }else if (resultType=="ERROR"){
            sendFailure(response.get("code").toString(),response.get("message").toString())
        }else if(resultType=="ERROR_VALIDATION"){
            Toast.makeText(reactApplicationContext, response.get("message").toString(), Toast.LENGTH_SHORT).show()
        }else{
            sendFailure("ERROR_UNKNOWN","Unknown Error")
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun parseActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(TAG, "parseActivityResult")
        if (requestCode == DropIn.DROP_IN_REQUEST_CODE && resultCode == Activity.RESULT_CANCELED && data != null) {
//            sendFailure("ERROR_CANCELLED","Transaction Cancelled")
            Log.d(TAG, "DropIn CANCELED")
        }
    }

    private fun setLoading(showLoading: Boolean) {
        val curr_activity : FragmentActivity = currentActivity as FragmentActivity
        val curr_fragment_mgr : FragmentManager = curr_activity.supportFragmentManager as FragmentManager
        if (showLoading) {
            if (!loadingDialog.isAdded) {
                loadingDialog.show(curr_fragment_mgr, LOADING_FRAGMENT_TAG)
            }
        } else {
            val df : DialogFragment = curr_fragment_mgr.findFragmentByTag(LOADING_FRAGMENT_TAG) as DialogFragment
            df.dismiss()
        }
    }

}

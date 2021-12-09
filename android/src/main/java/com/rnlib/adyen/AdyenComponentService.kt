package com.rnlib.adyen

import com.adyen.checkout.core.log.LogUtil
import com.adyen.checkout.core.log.Logger
import com.adyen.checkout.redirect.RedirectComponent
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.*
import retrofit2.Call
import java.io.IOException
import android.util.Log

import com.rnlib.adyen.service.AdyenDropInService
import com.rnlib.adyen.service.AdyenDropInServiceResult

/**
 * This is just an example on how to make networkModule calls on the [DropInService].
 * You should make the calls to your own servers and have additional data or processing if necessary.
 */
class AdyenComponentService : AdyenDropInService() {

    companion object {
        private val TAG = LogUtil.getTag()
        private val CONTENT_TYPE: MediaType = "application/json".toMediaType()
    }

    /*
    fun getCheckoutApi(baseURL: String): CheckoutApiService {
        return Retrofit.Builder()
            .baseUrl(baseURL)
            .client(ApiWorker.client)
            .addConverterFactory(ApiWorker.gsonConverter)
            .addCallAdapterFactory(CoroutineCallAdapterFactory())
            .build()
            .create(CheckoutApiService::class.java)
    }*/

    override fun makePaymentsCall(paymentComponentData: JSONObject): AdyenDropInServiceResult {
        Log.i(TAG, "makePaymentsCall")
        // Check out the documentation of this method on the parent DropInService class
        /*
                amount: {
                    value: Amt,
                    currency: 'EUR'
                },
                reference: "",
                shopperReference : shopper_internal_reference_id,
                shopperEmail : user.email,
                countryCode: countryCode.toUpperCase(),
                shopperLocale: shopperLocale,
                returnUrl: "",
                merchantAccount: MERCHANT_ACCOUNT
                additionalData : {
                        allow3DS2 : true,
                        executeThreeD : false
                }
        */

        val configData : AppServiceConfigData = AdyenPaymentModule.getAppServiceConfigData();
        val paymentRequest : JSONObject = AdyenPaymentModule.getPaymentData();
        val amount = paymentRequest.getJSONObject("amount")
        paymentRequest.putOpt("payment_method", paymentComponentData.getJSONObject("paymentMethod"))
        paymentRequest.put("return_url", RedirectComponent.getReturnUrl(applicationContext))
        paymentRequest.put("amount", amount.getInt("value"))

        val requestBody = paymentRequest.toString().toRequestBody(CONTENT_TYPE)
        var call = ApiService.checkoutApi(configData.baseUrl).addCards(configData.appUrlHeaders,requestBody)
        when (paymentRequest.getString("reference")) {
            "api/v1/adyen/trip_payments" -> call = ApiService.checkoutApi(configData.baseUrl).tripPayments(configData.appUrlHeaders,requestBody)
            "api/v1/payments/top_up" -> call = ApiService.checkoutApi(configData.baseUrl).userCredit(configData.appUrlHeaders,requestBody)
            "api/v1/spin_passes" -> call = ApiService.checkoutApi(configData.baseUrl).spinPasses(configData.appUrlHeaders,requestBody)
        }
        return handleResponse(call)
    }

    override fun makeDetailsCall(actionComponentData: JSONObject): AdyenDropInServiceResult {
        Log.d(TAG, "makeDetailsCall")

        val configData : AppServiceConfigData = AdyenPaymentModule.getAppServiceConfigData();
        val requestBody = actionComponentData.toString().toRequestBody(CONTENT_TYPE)
        val call = ApiService.checkoutApi(configData.baseUrl).details(configData.appUrlHeaders,requestBody)

        return handleResponse(call)
    }

    private fun isJSONValid(test:String) : Boolean {
        try {
             JSONObject(test)
        } catch (ex:JSONException) {
            // edited, to include @Arthur's comment
            // e.g. in case JSONArray is valid as well...
            try {
                JSONArray(test)
            } catch (ex1:JSONException) {
                return false
            }
        }
        return true;
    }

    @Suppress("NestedBlockDepth")
    private fun handleResponse(call: Call<ResponseBody>): AdyenDropInServiceResult {
        return try {
            val response = call.execute()

            val byteArray = response.errorBody()?.bytes()
            if (byteArray != null) {
                Logger.e(TAG, "errorBody - ${String(byteArray)}")
                if(isJSONValid(String(byteArray))){
                    // Ex : {"type":"configuration","errorCode":"905","errorMessage":"Payment details are not supported"}
                    val detailsErrResponse = JSONObject(String(byteArray))
                    if(detailsErrResponse.has("errorCode") && detailsErrResponse.has("errorMessage")){
                        val errType = detailsErrResponse.getString("type")
                        val errCode = detailsErrResponse.getString("errorCode")
                        val errMessage = detailsErrResponse.getString("errorMessage")
                        val appendedErrMsg = if(errType=="validation") errMessage else (errCode + " : " + errMessage)
                        val resultType = if(errType=="validation") "ERROR_VALIDATION" else "ERROR"
                        val errObj : JSONObject = JSONObject()
                        errObj.put("resultType",resultType)
                        errObj.put("code","ERROR_PAYMENT_DETAILS")
                        errObj.put("message", appendedErrMsg)
                        AdyenDropInServiceResult.Finished(errObj.toString())
                    } else {
                        val errObj: JSONObject = JSONObject()
                        errObj.put("resultType", "ERROR")
                        errObj.put("code", "ERROR_GENERAL_1")
                        errObj.put("message", String(byteArray))
                        AdyenDropInServiceResult.Finished(errObj.toString())
                    }
                } else {
                    val errObj: JSONObject = JSONObject()
                    errObj.put("resultType", "ERROR")
                    errObj.put("code", "ERROR_GENERAL_2")
                    errObj.put("message", String(byteArray))
                    AdyenDropInServiceResult.Finished(errObj.toString())
                }

            }else{

                val detailsResponse = JSONObject(response.body()?.string())

                if (response.isSuccessful) {
                    if (detailsResponse.has("action")) {
                        AdyenDropInServiceResult.Action(detailsResponse.get("action").toString())
                    } else {

                        val successObj : JSONObject = JSONObject()
                        successObj.put("resultType","SUCCESS")
                        successObj.put("message",detailsResponse)
                        AdyenDropInServiceResult.Finished(successObj.toString())
                    }
                } else {
                    Logger.e(TAG, "FAILED - ${response.message()}")
                    //CallResult(CallResult.ResultType.ERROR, response.message().toString())
                    val errObj : JSONObject = JSONObject()
                    errObj.put("resultType","ERROR")
                    errObj.put("code","ERROR_GENERAL_3")
                    errObj.put("message",response.message().toString())
                    AdyenDropInServiceResult.Finished(errObj.toString())
                }
            }
        } catch (e: IOException) {
            Logger.e(TAG, "IOException", e)
            //CallResult(CallResult.ResultType.ERROR, "IOException")
            val errObj : JSONObject = JSONObject()
            errObj.put("resultType","ERROR")
            errObj.put("code","ERROR_IOEXCEPTION")
            errObj.put("message","Unable to Connect to the Server")
            AdyenDropInServiceResult.Finished(errObj.toString())
        }
    }
}

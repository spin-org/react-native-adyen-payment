package com.rnlib.adyen
import com.adyen.checkout.components.model.payments.Amount

@Suppress("MagicNumber")
data class PaymentMethodsRequest(
    val amount: Int
)

data class AdditionalData(val allow3DS2 : String = "true",val executeThreeD: String="false")
data class PaymentData(val countryCode : String="FR", val shopperLocale: String = "en_US", val amount : Amount, val reference : String="", val shopperReference: String="", val shopperEmail : String="", val merchantAccount: String ="", val returnUrl: String="", val additionalData : AdditionalData)
data class AppServiceConfigData(
    var environment: String = "",
    var baseUrl: String = "",
    var appUrlHeaders: Map<String, String> = HashMap<String, String>(),
    var clientKey: String = ""
)
package com.rnlib.adyen.ui

import android.content.Intent
import androidx.lifecycle.ViewModel
import com.adyen.checkout.components.model.PaymentMethodsApiResponse
import com.adyen.checkout.components.model.paymentmethods.PaymentMethod
import com.adyen.checkout.components.model.paymentmethods.StoredPaymentMethod
import com.adyen.checkout.components.util.PaymentMethodTypes
import com.rnlib.adyen.AdyenComponentConfiguration

class AdyenComponentViewModel(
    val paymentMethodsApiResponse: PaymentMethodsApiResponse,
    val adyenComponentConfiguration: AdyenComponentConfiguration,
    val resultHandlerIntent: Intent?
) : ViewModel() {

    val showPreselectedStored = (paymentMethodsApiResponse.storedPaymentMethods?.isNotEmpty() ?: false) &&
            adyenComponentConfiguration.showPreselectedStoredPaymentMethod
    val preselectedStoredPayment = paymentMethodsApiResponse.storedPaymentMethods?.firstOrNull {
        it.isEcommerce && PaymentMethodTypes.SUPPORTED_PAYMENT_METHODS.contains(it.type)
    } ?: StoredPaymentMethod()

    fun getStoredPaymentMethod(id: String): StoredPaymentMethod {
        return paymentMethodsApiResponse.storedPaymentMethods?.firstOrNull { it.id == id } ?: StoredPaymentMethod()
    }

    fun getPaymentMethod(type: String): PaymentMethod {
        return paymentMethodsApiResponse.paymentMethods?.firstOrNull { it.type == type } ?: PaymentMethod()
    }
}

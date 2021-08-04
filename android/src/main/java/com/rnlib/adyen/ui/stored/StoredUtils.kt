package com.rnlib.adyen.ui.stored

import com.adyen.checkout.components.model.paymentmethods.StoredPaymentMethod
import com.adyen.checkout.components.util.PaymentMethodTypes
import com.adyen.checkout.dropin.ui.paymentmethods.GenericStoredModel
import com.adyen.checkout.dropin.ui.paymentmethods.StoredCardModel
import com.adyen.checkout.dropin.ui.paymentmethods.StoredPaymentMethodModel

internal fun makeStoredModel(storedPaymentMethod: StoredPaymentMethod): StoredPaymentMethodModel {
    return when (storedPaymentMethod.type) {
        PaymentMethodTypes.SCHEME -> {
            StoredCardModel(
                storedPaymentMethod.id.orEmpty(),
                storedPaymentMethod.brand.orEmpty(),
                storedPaymentMethod.lastFour.orEmpty(),
                storedPaymentMethod.expiryMonth.orEmpty(),
                storedPaymentMethod.expiryYear.orEmpty()
            )
        }
        else -> GenericStoredModel(
            storedPaymentMethod.id.orEmpty(),
            storedPaymentMethod.type.orEmpty(),
            storedPaymentMethod.name.orEmpty()
        )
    }
}
package com.rnlib.adyen.ui.paymentmethods

import com.adyen.checkout.components.model.paymentmethods.PaymentMethod

// TODO: 24/11/2020 delete this class
class PaymentMethodsModel {
    var storedPaymentMethods: MutableList<PaymentMethod> = mutableListOf()
    var paymentMethods: MutableList<PaymentMethod> = mutableListOf()
}

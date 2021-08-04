package com.rnlib.adyen.service

/**
 * The result from a server call request on the [AdyenDropInService]
 */
sealed class AdyenDropInServiceResult {

    /**
     * Call was successful and payment is finished. This does not necessarily mean that the
     * payment was authorized, it can simply indicate that all the necessary network calls were
     * made without any exceptions or unexpected errors.
     */
    class Finished(val result: String) : AdyenDropInServiceResult()

    /**
     * Call was successful and returned with an
     * [com.adyen.checkout.components.model.payments.response.Action] that needs to be handled.
     */
    class Action(val actionJSON: String) : AdyenDropInServiceResult()

    /**
     * Call failed with an error. Can have the localized error message which will be shown
     * in an Alert Dialog, otherwise a generic error message will be shown.
     */
    class Error(val errorMessage: String? = null, val reason: String? = null, val dismissDropIn: Boolean = false) : AdyenDropInServiceResult()
}

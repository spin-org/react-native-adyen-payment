package com.rnlib.adyen

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Parcel
import android.os.Parcelable
import com.adyen.checkout.adyen3ds2.Adyen3DS2Configuration
import com.adyen.checkout.await.AwaitConfiguration
import com.adyen.checkout.bcmc.BcmcConfiguration
import com.adyen.checkout.blik.BlikConfiguration
import com.adyen.checkout.card.CardConfiguration
import com.adyen.checkout.components.base.Configuration
import com.adyen.checkout.components.model.payments.Amount
import com.adyen.checkout.components.util.CheckoutCurrency
import com.adyen.checkout.components.util.PaymentMethodTypes
import com.adyen.checkout.components.util.ValidationUtils
import com.adyen.checkout.core.api.Environment
import com.adyen.checkout.core.exception.CheckoutException
import com.adyen.checkout.core.log.LogUtil
import com.adyen.checkout.core.model.JsonUtils
import com.adyen.checkout.core.util.LocaleUtil
import com.adyen.checkout.core.util.ParcelUtils
import com.adyen.checkout.dotpay.DotpayConfiguration
import com.adyen.checkout.entercash.EntercashConfiguration
import com.adyen.checkout.eps.EPSConfiguration
import com.adyen.checkout.googlepay.GooglePayConfiguration
import com.adyen.checkout.ideal.IdealConfiguration
import com.adyen.checkout.mbway.MBWayConfiguration
import com.adyen.checkout.molpay.MolpayConfiguration
import com.adyen.checkout.openbanking.OpenBankingConfiguration
import com.adyen.checkout.qrcode.QRCodeConfiguration
import com.adyen.checkout.redirect.RedirectConfiguration
import com.adyen.checkout.sepa.SepaConfiguration
import com.adyen.checkout.wechatpay.WeChatPayActionConfiguration
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.set

/**
 * This is the base configuration for the Drop-In solution. You need to use the [Builder] to instantiate this class.
 * There you will find specific methods to add configurations for each specific PaymentComponent, to be able to customize their behavior.
 * If you don't specify anything, a default configuration will be used.
 */
@SuppressWarnings("TooManyFunctions")
class AdyenComponentConfiguration : Configuration, Parcelable {

    private val availablePaymentConfigs: Map<String, Configuration>
    private val availableActionConfigs: Map<Class<*>, Configuration>
    val resultHandlerIntent: Intent
    val serviceComponentName: ComponentName
    val amount: Amount
    var dropIn: Boolean = false
    val showPreselectedStoredPaymentMethod: Boolean

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<AdyenComponentConfiguration> {
            override fun createFromParcel(parcel: Parcel) = AdyenComponentConfiguration(parcel)
            override fun newArray(size: Int) = arrayOfNulls<AdyenComponentConfiguration>(size)
        }
    }

    @Suppress("LongParameterList")
    constructor(
        builder: Builder
    ) : super(builder.shopperLocale, builder.environment, builder.clientKey) {
        this.availablePaymentConfigs = builder.availablePaymentConfigs
        this.availableActionConfigs = builder.availableActionConfigs
        this.resultHandlerIntent = builder.resultHandlerIntent
        this.serviceComponentName = builder.serviceComponentName
        this.amount = builder.amount
        this.dropIn = builder.dropIn
        this.showPreselectedStoredPaymentMethod = builder.showPreselectedStoredPaymentMethod
    }

    constructor(parcel: Parcel) : super(parcel) {
        @Suppress("UNCHECKED_CAST")
        availablePaymentConfigs =
            parcel.readHashMap(Configuration::class.java.classLoader) as HashMap<String, Configuration>
        @Suppress("UNCHECKED_CAST")
        availableActionConfigs =
            parcel.readHashMap(Configuration::class.java.classLoader) as HashMap<Class<*>, Configuration>
        resultHandlerIntent = parcel.readParcelable(Intent::class.java.classLoader)!!
        serviceComponentName = parcel.readParcelable(ComponentName::class.java.classLoader)!!
        amount = Amount.CREATOR.createFromParcel(parcel)
        dropIn = ParcelUtils.readBoolean(parcel)
        showPreselectedStoredPaymentMethod = ParcelUtils.readBoolean(parcel)
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, flags)
        dest.writeMap(availablePaymentConfigs)
        dest.writeMap(availableActionConfigs)
        dest.writeParcelable(resultHandlerIntent, flags)
        dest.writeParcelable(serviceComponentName, flags)
        JsonUtils.writeToParcel(dest, Amount.SERIALIZER.serialize(amount))
        ParcelUtils.writeBoolean(dest, dropIn)
        ParcelUtils.writeBoolean(dest, showPreselectedStoredPaymentMethod)
    }

    override fun describeContents(): Int {
        return ParcelUtils.NO_FILE_DESCRIPTOR
    }

    internal fun <T : Configuration> getConfigurationForPaymentMethodOrNull(
        paymentMethod: String,
        context: Context
    ): T? {
        return try {
            getConfigurationForPaymentMethod(paymentMethod, context)
        } catch (e: CheckoutException) {
            null
        }
    }

    internal fun <T : Configuration> getConfigurationForPaymentMethod(
        paymentMethod: String,
        context: Context
    ): T {
        return if (availablePaymentConfigs.containsKey(paymentMethod)) {
            @Suppress("UNCHECKED_CAST")
            availablePaymentConfigs[paymentMethod] as T
        } else {
            getDefaultConfigForPaymentMethod(paymentMethod, this)
        }
    }

    internal inline fun <reified T : Configuration> getConfigurationForAction(): T {
        val actionClass = T::class.java
        return if (availableActionConfigs.containsKey(actionClass)) {
            @Suppress("UNCHECKED_CAST")
            availableActionConfigs[actionClass] as T
        } else {
            getDefaultConfigForAction(this)
        }
    }

    /**
     * Builder for creating a [AdyenComponentConfiguration] where you can set specific Configurations for a Payment Method
     */
    class Builder {

        companion object {
            val TAG = LogUtil.getTag()
        }

        var availablePaymentConfigs = HashMap<String, Configuration>()
        var availableActionConfigs = HashMap<Class<*>, Configuration>()
        var resultHandlerIntent: Intent
            private set
        var shopperLocale: Locale
            private set
        var environment: Environment = Environment.EUROPE
            private set
        var clientKey: String
            private set
        var serviceComponentName: ComponentName
            private set
        var amount: Amount = Amount.EMPTY
            private set
        var dropIn: Boolean = false
            private set
        var showPreselectedStoredPaymentMethod: Boolean = true
            private set

        private val packageName: String
        private val serviceClassName: String

        /**
         *
         * Create a [AdyenComponentConfiguration]
         *
         * @param context
         * @param resultHandlerIntent The Intent used with [Activity.startActivity] that will contain the payment result extra with key [RESULT_KEY].
         * @param serviceClass Service that extended from [DropInService] that would handle network requests.
         * @param clientKey Your Client Key used for network calls from the SDK to Adyen.
         */
        constructor(context: Context, resultHandlerIntent: Intent, serviceClass: Class<out Any?>, clientKey: String) {
            this.packageName = context.packageName
            this.serviceClassName = serviceClass.name

            this.resultHandlerIntent = resultHandlerIntent
            this.serviceComponentName = ComponentName(packageName, serviceClassName)
            this.shopperLocale = LocaleUtil.getLocale(context)

            if (!ValidationUtils.isClientKeyValid(clientKey)) {
                throw CheckoutException("Client key is not valid.")
            }
            this.clientKey = clientKey
        }

        /**
         * Create a Builder with the same values of an existing Configuration object.
         */
        constructor(adyenComponentConfiguration: AdyenComponentConfiguration) {
            this.packageName = adyenComponentConfiguration.serviceComponentName.packageName
            this.serviceClassName = adyenComponentConfiguration.serviceComponentName.className
            this.resultHandlerIntent = adyenComponentConfiguration.resultHandlerIntent
            this.serviceComponentName = adyenComponentConfiguration.serviceComponentName
            this.shopperLocale = adyenComponentConfiguration.shopperLocale
            this.environment = adyenComponentConfiguration.environment
            this.amount = adyenComponentConfiguration.amount
            this.clientKey = adyenComponentConfiguration.clientKey
            this.dropIn = adyenComponentConfiguration.dropIn
            this.showPreselectedStoredPaymentMethod = adyenComponentConfiguration.showPreselectedStoredPaymentMethod
        }

        fun setServiceComponentName(serviceComponentName: ComponentName): Builder {
            this.serviceComponentName = serviceComponentName
            return this
        }

        fun setResultHandlerIntent(resultHandlerIntent: Intent): Builder {
            this.resultHandlerIntent = resultHandlerIntent
            return this
        }

        /**
         * Sets the [Locale] to be used for localization on the Drop-in flow.<br>
         * Note that the [Locale] on the specific component configuration will still take priority and can cause inconsistency in the UI.<br>
         * Also, due to technical limitations, this Locale will be converted to String and lose additional variants other than language and country.
         */
        fun setShopperLocale(shopperLocale: Locale): Builder {
            this.shopperLocale = shopperLocale
            return this
        }

        fun setDropIn(dropIn: Boolean): Builder {
            this.dropIn = dropIn
            return this
        }

        fun setShowPreselectedStoredPaymentMethod(showStoredPaymentMethod: Boolean): Builder {
            this.showPreselectedStoredPaymentMethod = showStoredPaymentMethod
            return this
        }

        fun setEnvironment(environment: Environment): Builder {
            this.environment = environment
            return this
        }

        fun setAmount(amount: Amount): Builder {
            if (!CheckoutCurrency.isSupported(amount.currency) || amount.value < 0) {
                throw CheckoutException("Currency is not valid.")
            }
            this.amount = amount
            return this
        }

        /**
         * Add configuration for Credit Card payment method.
         */
        fun addCardConfiguration(cardConfiguration: CardConfiguration): Builder {
            availablePaymentConfigs[PaymentMethodTypes.SCHEME] = cardConfiguration
            return this
        }

        /**
         * Add configuration for iDeal payment method.
         */
        fun addIdealConfiguration(idealConfiguration: IdealConfiguration): Builder {
            availablePaymentConfigs[PaymentMethodTypes.IDEAL] = idealConfiguration
            return this
        }

        /**
         * Add configuration for MolPay Thailand payment method.
         */
        fun addMolpayThailandConfiguration(molpayConfiguration: MolpayConfiguration): Builder {
            availablePaymentConfigs[PaymentMethodTypes.MOLPAY_THAILAND] = molpayConfiguration
            return this
        }

        /**
         * Add configuration for MolPay Malasya payment method.
         */
        fun addMolpayMalasyaConfiguration(molpayConfiguration: MolpayConfiguration): Builder {
            availablePaymentConfigs[PaymentMethodTypes.MOLPAY_MALAYSIA] = molpayConfiguration
            return this
        }

        /**
         * Add configuration for MolPay Vietnam payment method.
         */
        fun addMolpayVietnamConfiguration(molpayConfiguration: MolpayConfiguration): Builder {
            availablePaymentConfigs[PaymentMethodTypes.MOLPAY_VIETNAM] = molpayConfiguration
            return this
        }

        /**
         * Add configuration for DotPay payment method.
         */
        fun addDotpayConfiguration(dotpayConfiguration: DotpayConfiguration): Builder {
            availablePaymentConfigs[PaymentMethodTypes.DOTPAY] = dotpayConfiguration
            return this
        }

        /**
         * Add configuration for EPS payment method.
         */
        fun addEpsConfiguration(epsConfiguration: EPSConfiguration): Builder {
            availablePaymentConfigs[PaymentMethodTypes.EPS] = epsConfiguration
            return this
        }

        /**
         * Add configuration for EnterCash payment method.
         */
        fun addEntercashConfiguration(entercashConfiguration: EntercashConfiguration): Builder {
            availablePaymentConfigs[PaymentMethodTypes.ENTERCASH] = entercashConfiguration
            return this
        }

        /**
         * Add configuration for Open Banking payment method.
         */
        fun addOpenBankingConfiguration(openBankingConfiguration: OpenBankingConfiguration): Builder {
            availablePaymentConfigs[PaymentMethodTypes.OPEN_BANKING] = openBankingConfiguration
            return this
        }

        /**
         * Add configuration for Google Pay payment method.
         */
        fun addGooglePayConfiguration(googlePayConfiguration: GooglePayConfiguration): Builder {
            availablePaymentConfigs[PaymentMethodTypes.GOOGLE_PAY] = googlePayConfiguration
            availablePaymentConfigs[PaymentMethodTypes.GOOGLE_PAY_LEGACY] = googlePayConfiguration
            return this
        }

        /**
         * Add configuration for Sepa payment method.
         */
        fun addSepaConfiguration(sepaConfiguration: SepaConfiguration): Builder {
            availablePaymentConfigs[PaymentMethodTypes.SEPA] = sepaConfiguration
            return this
        }

        /**
         * Add configuration for BCMC payment method.
         */
        fun addBcmcConfiguration(bcmcConfiguration: BcmcConfiguration): Builder {
            availablePaymentConfigs[PaymentMethodTypes.BCMC] = bcmcConfiguration
            return this
        }

        /**
         * Add configuration for MB WAY payment method.
         */
        fun addMBWayConfiguration(mbwayConfiguration: MBWayConfiguration): Builder {
            availablePaymentConfigs[PaymentMethodTypes.MB_WAY] = mbwayConfiguration
            return this
        }

        /**
         * Add configuration for Blik payment method.
         */
        fun addBlikConfiguration(blikConfiguration: BlikConfiguration): Builder {
            availablePaymentConfigs[PaymentMethodTypes.BLIK] = blikConfiguration
            return this
        }

        /**
         * Add configuration for 3DS2 action.
         */
        fun add3ds2ActionConfiguration(configuration: Adyen3DS2Configuration): Builder {
            availableActionConfigs[configuration::class.java] = configuration
            return this
        }

        /**
         * Add configuration for Await action.
         */
        fun addAwaitActionConfiguration(configuration: AwaitConfiguration): Builder {
            availableActionConfigs[configuration::class.java] = configuration
            return this
        }

        /**
         * Add configuration for QR code action.
         */
        fun addQRCodeActionConfiguration(configuration: QRCodeConfiguration): Builder {
            availableActionConfigs[configuration::class.java] = configuration
            return this
        }

        /**
         * Add configuration for Redirect action.
         */
        fun addRedirectActionConfiguration(configuration: RedirectConfiguration): Builder {
            availableActionConfigs[configuration::class.java] = configuration
            return this
        }

        /**
         * Add configuration for WeChat Pay action.
         */
        fun addWeChatPayActionConfiguration(configuration: WeChatPayActionConfiguration): Builder {
            availableActionConfigs[configuration::class.java] = configuration
            return this
        }

        /**
         * Create the [AdyenComponentConfiguration] instance.
         */
        fun build(): AdyenComponentConfiguration {
            if (!ValidationUtils.doesClientKeyMatchEnvironment(clientKey, environment)) {
                throw CheckoutException("Client key does not match the environment.")
            }

            return AdyenComponentConfiguration(this)
        }
    }
}



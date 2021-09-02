import {NativeEventEmitter, NativeModules,Platform} from 'react-native';

const { AdyenPayment } = NativeModules;
const events = new NativeEventEmitter(AdyenPayment);
let onAdyenPaymentSuccessListener;
let onAdyenPaymentErrorListener;
const DROPIN = "dropin";
const IDEAL = "ideal";
const MOLPAY_MALAYSIA = "molpay_ebanking_fpx_MY";
const MOLPAY_THAILAND = "molpay_ebanking_TH";
const MOLPAY_VIETNAM = "molpay_ebanking_VN";
const DOTPAY = "dotpay";
const EPS = "eps";
const ENTERCASH = "entercash";
const OPEN_BANKING = "openbanking_UK";
const SCHEME = "scheme";
const GOOGLE_PAY = "googlepay";
const GOOGLE_PAY_LEGACY = "paywithgoogle";
const SEPA = "sepadirectdebit";
const BCMC = "bcmc";
const WECHAT_PAY_SDK = "wechatpaySDK";
const APPLE_PAY = "applepay";
const AFTERPAY="afterpay_default";

export default {
    DROPIN,
    IDEAL,
    MOLPAY_MALAYSIA,MOLPAY_THAILAND,MOLPAY_VIETNAM,DOTPAY,EPS,ENTERCASH,OPEN_BANKING,
    SCHEME,
    GOOGLE_PAY,
    GOOGLE_PAY_LEGACY,
    SEPA,
    BCMC,
    WECHAT_PAY_SDK,
    APPLE_PAY,
    AFTERPAY,
    initialize(appServiceConfigData){
        return AdyenPayment.initialize(appServiceConfigData);
    },
    startPaymentPromise(component,componentData,paymentDetails){
        const default_components = [DROPIN,IDEAL,MOLPAY_MALAYSIA,MOLPAY_THAILAND,MOLPAY_VIETNAM,DOTPAY,
            EPS,ENTERCASH,OPEN_BANKING,SCHEME,SEPA,BCMC];
        var supported_components;
        if(Platform.OS === 'ios'){
            supported_components = [APPLE_PAY];
        }
        else if (Platform.OS  === 'android') {
            supported_components = [WECHAT_PAY_SDK,GOOGLE_PAY,GOOGLE_PAY_LEGACY,AFTERPAY];
        }
        if(default_components.indexOf(component) !== -1 || supported_components.indexOf(component) !== -1){
            return AdyenPayment.startPaymentPromise(component,componentData,paymentDetails)
        }else{
            throw new Error(`${component} is not supported for ${Platform.OS} Platform`);
        }
    },
    startPayment(component,componentData,paymentDetails){
        const default_components = [DROPIN,IDEAL,MOLPAY_MALAYSIA,MOLPAY_THAILAND,MOLPAY_VIETNAM,DOTPAY,
            EPS,ENTERCASH,OPEN_BANKING,SCHEME,SEPA,BCMC];
        var supported_components;
        if(Platform.OS === 'ios'){
            supported_components = [APPLE_PAY];
        }
        else if (Platform.OS  === 'android') {
            supported_components = [WECHAT_PAY_SDK,GOOGLE_PAY,GOOGLE_PAY_LEGACY,AFTERPAY];
        }
        if(default_components.indexOf(component) !== -1 || supported_components.indexOf(component) !== -1){
            return AdyenPayment.startPayment(component,componentData,paymentDetails)
        }else{
            throw new Error(`${component} is not supported for ${Platform.OS} Platform`);
        }
    },
    canMakeNativePayPayments() {
        if (Platform.OS === 'ios') {
            return AdyenPayment.canMakeApplePayPayments()
        } else if (Platform.OS === 'android') {
            return AdyenPayment.canMakeGooglePayments()
        } else {
            throw new Error(`${component} is not supported for ${Platform.OS} Platform`); 
        }
    },
    /**
     * @callback mOnSuccess
     * @param {Object} message
     */
    /**
     * After successfully payment, added payload data for confirmation payments
     * @param {mOnSuccess} mOnSuccess
     */
    onSuccess(mOnSuccess) {
        this._validateParam(mOnSuccess, 'onSuccess', 'function');
        if (onAdyenPaymentSuccessListener) {
          onAdyenPaymentSuccessListener.remove();
        }
        onAdyenPaymentSuccessListener = events.addListener('onSuccess', (response) => {
            mOnSuccess(response['message']);
        });
    },
    /**
     * @callback mOnError
     * @param {String} error_code
     * @param {String} message
     */
    /**
     * If payment was cancelled or something else. Calling instead of onPaymentResult event.
     * @param {mOnError} mOnError
     */
    onError(mOnError) {
        this._validateParam(mOnError, 'onError', 'function');
        if (onAdyenPaymentErrorListener) {
          onAdyenPaymentErrorListener.remove();
        }
        onAdyenPaymentErrorListener = events.addListener('onError', (response) => {
            mOnError(response['code'], response['message']);
        });
    },
    /**
     * @param {*} param
     * @param {String} methodName
     * @param {String} requiredType
     * @private
     */
    _validateParam(param, methodName, requiredType) {
        if (typeof param !== requiredType) {
            throw new Error(`Error: Adyen.${methodName}() requires a ${requiredType === 'function' ? 'callback function' : requiredType} but got a ${typeof param}`);
        }
    },
    events,
    removeListeners(){
        if(null != onAdyenPaymentSuccessListener)
            onAdyenPaymentSuccessListener.remove();
        if(null != onAdyenPaymentErrorListener)
            onAdyenPaymentErrorListener.remove();
    }
};
#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>
#import <React/RCTLog.h>
@import PassKit;

@interface RCT_EXTERN_MODULE(AdyenPayment, NSObject)
RCT_EXTERN_METHOD(startPayment:(NSString *)component componentData:(NSDictionary *)componentData paymentDetails:(NSDictionary *)paymentDetails)
RCT_EXTERN_METHOD(initialize:(NSDictionary *)appServiceConfigData)
RCT_EXTERN_METHOD(startPaymentPromise:(NSString *)component componentData:(NSDictionary *)componentData paymentDetails:(NSDictionary *)paymentDetails resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
RCT_EXPORT_METHOD(canMakeApplePayPayments:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)
{
    resolve(@([PKPaymentAuthorizationViewController canMakePaymentsUsingNetworks:@[PKPaymentNetworkVisa, PKPaymentNetworkAmex, PKPaymentNetworkMasterCard, PKPaymentNetworkDiscover]]));
}
@end

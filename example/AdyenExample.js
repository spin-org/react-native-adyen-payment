import React, {useState, useEffect} from 'react';
import {Platform, View, Button, Text} from 'react-native';
import AdyenPayment from 'react-native-adyen-payment';
import {
  ADYEN_MERCHANT_ACCOUNT,
  SPIN_BEARER_TOKEN,
  ADYEN_CLIENT_KEY,
  ADYEN_BASE_URL,
  ADYEN_ENVIRONMENT,
} from '@env';

const MOCK_PAYMENT_DETAILS = {
  amount: {
    value: 100,
    currency: 'EUR',
  },
  reference: 'api/v1/adyen/add_cards',
  shopperReference: '123456',
  shopperEmail: 'test@test.test',
  shopperLocale: 'en_US',
  channel: Platform.OS === 'ios' ? 'iOS' : 'Android',
  countryCode: 'ES',
  // Remember to replace returnUrl with your app scheme
  returnUrl:
    Platform.OS === 'ios' ? 'your-ios-scheme://' : 'your-android-scheme://',
  merchantAccount: ADYEN_MERCHANT_ACCOUNT,
  regionId: 241,
  additionalData: {
    allow3DS2: true,
    executeThreeD: true,
  },
  baseRate: 500,
  effectiveRate: 100,
};

const MOCK_COMPONENT_DATA = {
  scheme: {
    shouldShowSCAToggle: true,
    shouldShowPostalCode: false,
  },
  // Uncomment to add Apple Pay (replace apple_pay_merchant_id):
  applepay: {
    apple_pay_merchant_id: 'merchant.com.spin',
    supportedNetworks: ['visa', 'masterCard', 'amex', 'discover'],
    merchantCapabilities: ['supports3DS'],
  },

  paywithgoogle: {
    merchantAccount: 'SpinUS',
  },
};

const APP_SERVICE_CONFIG_DATA = {
  environment: ADYEN_ENVIRONMENT,
  base_url: ADYEN_BASE_URL,
  client_key: ADYEN_CLIENT_KEY,
  // Add any additional headers to pass to your backend
  additional_http_headers: {
    'Device-Platform': Platform.OS, // Example
    Authorization: `Bearer ${SPIN_BEARER_TOKEN}`,
  },
};

const STATUS = {
  none: 'none',
  initiated: 'initiated',
  success: 'success',
  failure: 'failure',
};

function AdyenExample() {
  const [status, setStatus] = useState(STATUS.none);
  const [canMakeNativePayPayments, setCanMakeNativePayPayments] = useState(false);

  useEffect(async () => {
    AdyenPayment.initialize(APP_SERVICE_CONFIG_DATA);

    AdyenPayment.onSuccess((payload) => {
      console.log('success', payload);
      setStatus(STATUS.success);
    });
    AdyenPayment.onError((payload) => {
      console.log('failure', payload);
      setStatus(STATUS.failure);
    });
    await AdyenPayment.canMakeNativePayPayments().then(data => {
      setCanMakeNativePayPayments(data)
    })
  }, [canMakeNativePayPayments, setCanMakeNativePayPayments]);

  function handleButtonPress(type) {
    setStatus(STATUS.initiated);

    try {
      AdyenPayment.startPaymentPromise(
        type,
        MOCK_COMPONENT_DATA,
        MOCK_PAYMENT_DETAILS,
      );
    } catch (err) {
      console.error(err);
    }
  }

  function handleCardButtonPress() {
    handleButtonPress(AdyenPayment.SCHEME);
  }

  function handleDropinButtonPress() {
    handleButtonPress(AdyenPayment.DROPIN);
  }

  function handleApplePayButtonPress() {
    handleButtonPress(AdyenPayment.APPLE_PAY);
  }

  function handleGooglePayPress() {
    handleButtonPress(AdyenPayment.GOOGLE_PAY_LEGACY);
  }



  return (
    <View>
      <Text>Status: {status}</Text>
      <Text>canMakeNativePayPayments: {canMakeNativePayPayments ? "true" : "false"}</Text>
      <Button title="Drop-in" onPress={handleDropinButtonPress} />
      <Button title="Card Component" onPress={handleCardButtonPress} />
      {Platform.OS === 'ios' && (
        <Button title="Apple Pay" onPress={handleApplePayButtonPress} />
      )}
      {Platform.OS === 'android' && (
        <Button title="Google Pay" onPress={handleGooglePayPress} />
      )}
    </View>
  );
}

export default React.memo(AdyenExample);
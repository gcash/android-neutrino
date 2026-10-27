package cash.bchd.android_neutrino;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PaymentRequestsTest {

    @Test
    public void paysTheUsersFeeRateWhenTheMerchantRequiresLess() {
        assertEquals(1000, PaymentRequests.satPerKbFee(1, 0));
        assertEquals(5000, PaymentRequests.satPerKbFee(5, 1000));
    }

    @Test
    public void paysTheMerchantsRequiredFeeRateWhenItIsHigher() {
        assertEquals(1001, PaymentRequests.satPerKbFee(1, 1001));
        assertEquals(2000, PaymentRequests.satPerKbFee(1, 2000));
    }

    @Test
    public void keepsARequiredFeeRateAboveIntegerMaxValue() {
        int required = (int) 3000000000L;
        assertEquals(3000000000L, Integer.toUnsignedLong(PaymentRequests.satPerKbFee(1, required)));
    }
}

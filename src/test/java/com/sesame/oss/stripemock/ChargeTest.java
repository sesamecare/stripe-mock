package com.sesame.oss.stripemock;

import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.param.ChargeCreateParams;
import com.stripe.param.ChargeUpdateParams;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ChargeTest extends AbstractStripeMockTest {


    @Test
    void testCharge() throws StripeException {
        Charge charge = Charge.create(ChargeCreateParams.builder()
                                                        .setAmount(10_00L)
                                                        .setCurrency("usd")
                                                        .build());
        assertEquals(charge, Charge.retrieve(charge.getId()));

        Charge updatedCharge = charge.update(ChargeUpdateParams.builder()
                                                               .setTransferGroup("my-transfer-group")
                                                               .build());

        assertEquals(updatedCharge, Charge.retrieve(charge.getId()));
    }

    // todo: test confirming
    // todo: test customer
    // todo: test failure scenarios
    // todo: test payment methods, including sources
}

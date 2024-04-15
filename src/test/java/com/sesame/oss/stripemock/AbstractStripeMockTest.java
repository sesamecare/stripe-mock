package com.sesame.oss.stripemock;

import com.stripe.model.Invoice;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AbstractStripeMockTest {
    @BeforeAll
    static void setUp() {
        StripeMock.start();
    }

    @AfterAll
    static void afterAll() {
        StripeMock.stop();
    }

    @BeforeEach
    void reset() {
        StripeMock.reset();
    }

    /**
     * For unknown reasons, the invoice urls for an invoice keep changing. As a result, we'll need to compare the two WITHOUT looking at the invoice urls.
     */
    protected void assertInvoiceEquals(Invoice i1, Invoice i2) {
        i1.setFromInvoice(null);
        i2.setFromInvoice(null);

        i1.setHostedInvoiceUrl(null);
        i2.setHostedInvoiceUrl(null);

        i1.setInvoicePdf(null);
        i2.setInvoicePdf(null);

        assertEquals(i1, i2);
    }
}

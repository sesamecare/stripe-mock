package com.sesame.oss.stripemock.entities;

import com.sesame.oss.stripemock.http.ResponseCodeException;
import com.sesame.oss.stripemock.util.Utilities;
import com.stripe.model.SetupIntent;

import java.time.Clock;
import java.util.Map;

class SetupIntentManager extends AbstractEntityManager<SetupIntent> {
    protected SetupIntentManager(Clock clock) {
        super(clock, SetupIntent.class, "seti");
    }

    @Override
    protected SetupIntent initialize(SetupIntent setupIntent, Map<String, Object> formData) throws ResponseCodeException {
        setupIntent.setClientSecret(setupIntent.getId() + "_secret_" + Utilities.randomStringOfLength(25));
        setupIntent.setStatus("requires_payment_method");
        return setupIntent;
    }
}

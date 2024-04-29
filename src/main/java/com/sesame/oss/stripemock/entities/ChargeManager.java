package com.sesame.oss.stripemock.entities;

import com.sesame.oss.stripemock.http.ResponseCodeException;
import com.stripe.model.BalanceTransaction;
import com.stripe.model.Charge;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

class ChargeManager extends AbstractEntityManager<Charge> {
    private final StripeEntities stripeEntities;

    ChargeManager(Clock clock, StripeEntities stripeEntities) {
        super(clock, Charge.class, "ch", 24);
        this.stripeEntities = stripeEntities;
    }

    @Override
    public Charge add(Map<String, Object> formData, String stripeAccount) throws ResponseCodeException {
        if (formData.get("source") instanceof String source) {
            Map<String, Object> sourceMap = new HashMap<>();
            sourceMap.put("id", source);
            sourceMap.put("object", "source");
            formData.put("source", sourceMap);
        }

        return super.add(formData, stripeAccount);
    }

    @Override
    protected Charge initialize(Charge charge, Map<String, Object> formData, String stripeAccount) throws ResponseCodeException {
        charge.setRefunded(Boolean.FALSE);
        // todo: this can be pending and failed, too, and we should probably not let it succeed until we know that it will. But this will do for now.
        charge.setStatus("succeeded");
        // By registering this, it can be converted on the fly when expanded or fetched.
        BalanceTransactionManager balanceTransactionEntityManager = (BalanceTransactionManager) stripeEntities.getEntityManager(BalanceTransaction.class);
        balanceTransactionEntityManager.register(charge.getBalanceTransaction(), charge);
        return super.initialize(charge, formData, stripeAccount);
    }

    // todo: there must be a corresponding charge for each payment intent.
    //  Are we going to store these or just fake them by looking up the payment intent?
    //  If the former, we should consider doing the same for balance transactions
    //  Whatever we choose, we should probably be consistent

    @Override
    public boolean canPerformOperation(String operation) {
        return operation.equals("search");
    }
}

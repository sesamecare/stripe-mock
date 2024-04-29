package com.sesame.oss.stripemock.entities;

import com.sesame.oss.stripemock.util.Utilities;
import com.stripe.model.*;

import java.util.Collections;
import java.util.HashMap;

public final class BalanceTransactionMapper {

    static <T extends BalanceTransactionSource> BalanceTransaction toBalanceTransaction(T entity, String stripeAccount) {
        return switch (entity) {
            case Refund refund -> toBalanceTransaction(refund, stripeAccount);
            case Payout payout -> toBalanceTransaction(payout, stripeAccount);
            case Charge charge -> toBalanceTransaction(charge, stripeAccount);
            case TransferReversal transferReversal -> toBalanceTransaction(transferReversal, stripeAccount);
            case Transfer transfer -> toBalanceTransaction(transfer, stripeAccount);
            default -> throw new IllegalArgumentException("Can't turn %s into a balance transaction");
        };
    }

    // todo: which of these amount should be negative? Does it depend on the stripe account context in which the call takes place?

    private static BalanceTransaction toBalanceTransaction(Refund refund, String stripeAccount) {
        if (stripeAccount != null) {
            throw new IllegalStateException("Should not request refund balance transactions when specifying a stripe connect account id");
        }
        BalanceTransaction balanceTransaction = new BalanceTransaction();
        balanceTransaction.setCreated(refund.getCreated());
        balanceTransaction.setAvailableOn(refund.getCreated());
        balanceTransaction.setAmount(-refund.getAmount());
        balanceTransaction.setNet(-refund.getAmount());
        balanceTransaction.setFee(0L);
        balanceTransaction.setFeeDetails(Collections.emptyList());
        balanceTransaction.setCurrency(refund.getCurrency());
        balanceTransaction.setDescription(refund.getDescription());
        balanceTransaction.setId(refund.getBalanceTransaction());
        balanceTransaction.setObject("balance_transaction");
        balanceTransaction.setStatus(refund.getStatus()
                                           .equals("succeeded") ? "available" : "pending");
        balanceTransaction.setType("refund");
        balanceTransaction.setSourceObject(refund);
        balanceTransaction.setReportingCategory("refund");
        return balanceTransaction;
    }

    private static BalanceTransaction toBalanceTransaction(Charge charge, String stripeAccount) {
        if (stripeAccount != null) {
            throw new IllegalStateException("Should not request refund balance transactions when specifying a stripe connect account id");
        }

        BalanceTransaction balanceTransaction = new BalanceTransaction();
        balanceTransaction.setCreated(charge.getCreated());
        balanceTransaction.setAvailableOn(charge.getCreated());
        balanceTransaction.setAmount(charge.getAmount());
        balanceTransaction.setNet(charge.getAmount());
        balanceTransaction.setFee(0L);
        balanceTransaction.setFeeDetails(Collections.emptyList());
        balanceTransaction.setCurrency(charge.getCurrency());
        balanceTransaction.setDescription(charge.getDescription());
        balanceTransaction.setId(charge.getBalanceTransaction());
        balanceTransaction.setObject("balance_transaction");
        balanceTransaction.setStatus(charge.getStatus()
                                           .equals("succeeded") ? "available" : "pending");
        balanceTransaction.setType("charge");
        balanceTransaction.setSourceObject(charge);
        balanceTransaction.setReportingCategory("charge");
        return balanceTransaction;
    }

    private static BalanceTransaction toBalanceTransaction(Payout payout, String stripeAccount) {
        long amount = payout.getAmount();
        if (stripeAccount != null) {
            // The default direction of a payout is OUT from the connect account
            // Thus, if there is an account specified, the amount must be negative
            // todo: what happens if there isn't a stripe account specified? Does this EVER show up? If so, what's the direction?
            amount = -amount;
        }
        BalanceTransaction balanceTransaction = new BalanceTransaction();
        balanceTransaction.setCreated(payout.getCreated());
        balanceTransaction.setAvailableOn(payout.getCreated());
        balanceTransaction.setAmount(amount);
        balanceTransaction.setNet(amount);
        balanceTransaction.setFee(0L);
        balanceTransaction.setFeeDetails(Collections.emptyList());
        balanceTransaction.setCurrency(payout.getCurrency());
        balanceTransaction.setDescription(payout.getDescription());
        balanceTransaction.setId(payout.getBalanceTransaction());
        balanceTransaction.setObject("balance_transaction");
        balanceTransaction.setStatus(payout.getStatus()
                                           .equals("paid") ? "available" : "pending");
        balanceTransaction.setType("payout");
        balanceTransaction.setSourceObject(payout);
        balanceTransaction.setReportingCategory("payout");
        return balanceTransaction;
    }

    private static BalanceTransaction toBalanceTransaction(Transfer transfer, String stripeAccount) {
        long amount = transfer.getAmount();
        if (stripeAccount == null) {
            // The default direction of a transfer is OUT from the main account, which means IN to the connect account.
            // Thus, if there is no account specified, the amount must be negative
            amount = -amount;
        }
        BalanceTransaction balanceTransaction = new BalanceTransaction();
        balanceTransaction.setCreated(transfer.getCreated());
        balanceTransaction.setAvailableOn(transfer.getCreated());
        // todo: if this transfer was reversed, we need to take that into account here as well
        balanceTransaction.setAmount(amount);
        balanceTransaction.setNet(amount);
        balanceTransaction.setFee(0L);
        balanceTransaction.setFeeDetails(Collections.emptyList());
        balanceTransaction.setCurrency(transfer.getCurrency());
        balanceTransaction.setDescription(transfer.getDescription());
        balanceTransaction.setId(transfer.getBalanceTransaction());
        balanceTransaction.setObject("balance_transaction");
        balanceTransaction.setStatus("available");
        // todo: this should apparently be 'payment', and the source object should be a charge? wtf?
        //  How could the source be the charge? Why wouldn't that be the transfer?
        //  Maybe it just looks like a charge. It's got an id like this: py_1P6VdeFVKnpiM7amROiLJQ2S
        //  Those can't even be found in stripe. So maybe it's just the *format* of the data that's the charge
        balanceTransaction.setType("payment");
        Charge source = new Charge();
        source.setId(Utilities.randomIdWithPrefix("py", 24));
        source.setCurrency(transfer.getCurrency());
        source.setDescription(transfer.getDescription());
        source.setAmount(amount);
        source.setAmountRefunded(0L);
        Charge.BillingDetails billingDetails = new Charge.BillingDetails();
        billingDetails.setAddress(new Address());
        source.setBillingDetails(billingDetails);
        source.setCaptured(true);
        source.setCreated(transfer.getCreated());
        source.setDisputed(false);
        source.setFraudDetails(new Charge.FraudDetails());
        source.setLivemode(false);
        source.setMetadata(transfer.getMetadata());
        source.setObject("charge");
        source.setPaid(true);
        Charge.PaymentMethodDetails paymentMethodDetails = new Charge.PaymentMethodDetails();
        paymentMethodDetails.setStripeAccount(new Charge.PaymentMethodDetails.StripeAccount());
        paymentMethodDetails.setType("stripe_account");
        source.setPaymentMethodDetails(paymentMethodDetails);
        source.setRefunded(false);
        Account sourceAccount = new Account();
        sourceAccount.setObject("account");
        sourceAccount.setId(transfer.getDestination());
        source.setSource(sourceAccount);
        source.setStatus("succeeded");
        source.setSourceTransferObject(transfer);

        balanceTransaction.setSourceObject(source);
        balanceTransaction.setReportingCategory("charge");
        return balanceTransaction;
    }

    private static BalanceTransaction toBalanceTransaction(TransferReversal transferReversal, String stripeAccount) {
        long amount = transferReversal.getAmount();
        if (stripeAccount != null) {
            // The default direction of a transfer reversal is IN to the main account, which means OUT from the connect account.
            // Thus, if there is an account specified, the amount must be negative
            amount = -amount;
        }
        BalanceTransaction balanceTransaction = new BalanceTransaction();
        balanceTransaction.setCreated(transferReversal.getCreated());
        balanceTransaction.setAvailableOn(transferReversal.getCreated());
        balanceTransaction.setAmount(amount);
        balanceTransaction.setNet(amount);
        balanceTransaction.setFee(0L);
        balanceTransaction.setFeeDetails(Collections.emptyList());
        balanceTransaction.setCurrency(transferReversal.getCurrency());
        balanceTransaction.setDescription("REFUND FOR PAYMENT");
        balanceTransaction.setId(transferReversal.getBalanceTransaction());
        Refund source = new Refund();
        source.setAmount(amount);
        source.setBalanceTransaction(transferReversal.getBalanceTransaction());
        source.setCreated(transferReversal.getCreated());
        source.setCurrency(transferReversal.getCurrency());
        source.setMetadata(new HashMap<>());
        source.setObject("refund");
        source.setSourceTransferReversalObject(transferReversal);
        source.setStatus("succeeded");
        source.setTransferReversal(transferReversal.getId());
        // todo: these source ids should probably point to something real. Same for the transfer
        source.setCharge(Utilities.randomIdWithPrefix("py", 24));
        source.setId(Utilities.randomIdWithPrefix("pyr", 24));
        balanceTransaction.setObject("balance_transaction");
        balanceTransaction.setStatus("available");
        balanceTransaction.setType("payment_refund");
        balanceTransaction.setSourceObject(source);
        balanceTransaction.setReportingCategory("refund");
        return balanceTransaction;
    }
}

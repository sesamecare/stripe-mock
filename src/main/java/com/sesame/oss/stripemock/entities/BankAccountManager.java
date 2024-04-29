package com.sesame.oss.stripemock.entities;

import com.sesame.oss.stripemock.http.QueryParameters;
import com.sesame.oss.stripemock.http.ResponseCodeException;
import com.stripe.model.Account;
import com.stripe.model.BankAccount;

import java.time.Clock;
import java.util.*;

class BankAccountManager extends AbstractEntityManager<BankAccount> {
    private final Map<String, String> providedBankAccountNumbers = new HashMap<>();
    private final StripeEntities stripeEntities;

    BankAccountManager(Clock clock, StripeEntities stripeEntities) {
        super(clock, BankAccount.class, "ba", 24);
        this.stripeEntities = stripeEntities;
    }

    @Override
    public BankAccount add(Map<String, Object> formData, String stripeAccount, String parentEntityType, String parentEntityId) throws ResponseCodeException {
        if (!parentEntityType.equals("accounts")) {
            throw new UnsupportedOperationException("External accounts can't be attached to things that are not accounts");
        }
        Account parentAccount = stripeEntities.getEntityManager(Account.class)
                                              .get(parentEntityId, stripeAccount)
                                              .orElseThrow(() -> ResponseCodeException.noSuchEntity(400, "accounts", parentEntityId));

        BankAccount bankAccount = add(formData, stripeAccount);
        bankAccount.setAccount(parentAccount.getId());

        parentAccount.getExternalAccounts()
                     .getData()
                     .add(bankAccount);
        return bankAccount;
    }

    @Override
    protected BankAccount initialize(BankAccount bankAccount, Map<String, Object> formData, String stripeAccount) throws ResponseCodeException {
        String accountNumber = (String) formData.get("account_number");
        if (accountNumber == null) {
            Map<String, Object> externalAccount = (Map<String, Object>) formData.get("external_account");
            accountNumber = (String) externalAccount.get("account_number");
        }
        providedBankAccountNumbers.put(bankAccount.getId(), Objects.requireNonNull(accountNumber));
        return super.initialize(bankAccount, formData, stripeAccount);
    }

    @Override
    public List<BankAccount> list(QueryParameters query, String stripeAccount, String parentEntityType, String parentEntityId) throws ResponseCodeException {
        if (!parentEntityType.equals("accounts")) {
            throw new UnsupportedOperationException("External accounts can't be attached to things that are not accounts");
        }
        stripeEntities.getEntityManager(Account.class)
                      .get(parentEntityId, stripeAccount)
                      .orElseThrow(() -> ResponseCodeException.noSuchEntity(400, "accounts", parentEntityId));

        return entities.values()
                       .stream()
                       .filter(bankAccount -> bankAccount.getAccount()
                                                         .equals(parentEntityId))
                       .toList();
    }

    @Override
    public Optional<BankAccount> delete(String id, String stripeAccount, String parentEntityType, String parentEntityId) throws ResponseCodeException {
        if (!parentEntityType.equals("accounts")) {
            throw new UnsupportedOperationException("External accounts can't be attached to things that are not accounts");
        }
        EntityManager<Account> accountsEntityManager = stripeEntities.getEntityManager(Account.class);
        Account parentAccount = accountsEntityManager.get(parentEntityId, stripeAccount)
                                                     .orElseThrow(() -> ResponseCodeException.noSuchEntity(400, "accounts", parentEntityId));

        BankAccount bankAccount = entities.get(id);
        if (bankAccount == null) {
            return Optional.empty();
        }

        parentAccount.getExternalAccounts()
                     .getData()
                     .remove(bankAccount);

        bankAccount.setDeleted(true);
        return Optional.of(bankAccount);
    }

    @Override
    public Optional<BankAccount> get(String id, String stripeAccount, String parentEntityType, String parentEntityId) throws ResponseCodeException {
        if (!parentEntityType.equals("accounts")) {
            throw new UnsupportedOperationException("External accounts can't be attached to things that are not accounts");
        }
        EntityManager<Account> accountsEntityManager = stripeEntities.getEntityManager(Account.class);
        accountsEntityManager.get(parentEntityId, stripeAccount)
                             .orElseThrow(() -> ResponseCodeException.noSuchEntity(400, "accounts", parentEntityId));

        return Optional.ofNullable(entities.get(id));
    }

    @Override
    public String getNormalizedEntityName() {
        // Technically speaking there are more external accounts than this, but for now let's stick to just bank accounts.
        return "external_accounts";
    }

    public Optional<String> getAccountNumber(String bankAccountId) {
        return Optional.ofNullable(providedBankAccountNumbers.get(bankAccountId));
    }
}

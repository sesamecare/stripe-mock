package com.sesame.oss.stripemock;

import com.stripe.exception.IdempotencyException;
import com.stripe.exception.PermissionException;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.BankAccount;
import com.stripe.model.ExternalAccount;
import com.stripe.net.RequestOptions;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountCreateParams.*;
import com.stripe.param.AccountCreateParams.Capabilities.Transfers;
import com.stripe.param.AccountCreateParams.Company.Address;
import com.stripe.param.AccountCreateParams.Settings.Payouts;
import com.stripe.param.AccountCreateParams.Settings.Payouts.Schedule;
import com.stripe.param.AccountCreateParams.Settings.Payouts.Schedule.Interval;
import com.stripe.param.AccountUpdateParams;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class AccountTest extends AbstractStripeMockTest {
    @Test
    void shouldGetTheSameResponseForIdempotentRequests() throws StripeException {
        AccountCreateParams input = defaultCreationParameters("Stripe-mock test company name");
        RequestOptions options = RequestOptions.builder()
                                               .setIdempotencyKey(String.valueOf(Math.random()))
                                               .build();
        Account a1 = Account.create(input, options);
        Account a2 = Account.create(input, options);
        assertEquals(a1, a2);
        a1.delete();
    }


    // todo: tests for missing type: com.stripe.exception.InvalidRequestException: Missing required param: type.; code: parameter_missing
    // todo: tests: com.stripe.exception.InvalidRequestException: The `business_type` must be provided when sending either of `individual` or `company` parameters.

    @Test
    void shouldNotBeAbleToCreateDifferentEntitiesUsingTheSameIdempotencyKey() throws StripeException {
        String idempotencyKey = String.valueOf(Math.random());
        Account account = Account.create(defaultCreationParameters("Stripe-mock test company name"),
                                         RequestOptions.builder()
                                                       .setIdempotencyKey(idempotencyKey)
                                                       .build());
        IdempotencyException idempotencyException = assertThrows(IdempotencyException.class,
                                                                 () -> Account.create(defaultCreationParameters("Other company name"),
                                                                                      RequestOptions.builder()
                                                                                                    .setIdempotencyKey(idempotencyKey)
                                                                                                    .build()));
        assertEquals(String.format(
                             "Keys for idempotent requests can only be used with the same parameters they were first used with. Try using a key other than '%s' if you meant to execute a different request.",
                             idempotencyKey),
                     idempotencyException.getStripeError()
                                         .getMessage());
        account.delete();
    }

    @Test
    void testAccount() throws Exception {
        Account createdAccount = //
                Account.create(defaultCreationParameters("Stripe-mock test company name"));

        Account retrievedAccount = Account.retrieve(createdAccount.getId());
        assertEquals(createdAccount, retrievedAccount);

        List<ExternalAccount> externalAccounts = retrievedAccount.getExternalAccounts()
                                                                 .getData();
        assertEquals(1, externalAccounts.size());
        BankAccount bankAccount = (BankAccount) externalAccounts.getFirst();
        assertEquals(createdAccount.getId(), bankAccount.getAccount());
        assertEquals("bank_account", bankAccount.getObject());

        Account updatedAccount = //
                retrievedAccount.update(AccountUpdateParams.builder()
                                                           .setBusinessType(AccountUpdateParams.BusinessType.INDIVIDUAL)
                                                           .build(),
                                        RequestOptions.builder()
                                                      .build());

        Account retrievedUpdatedAccount = Account.retrieve(createdAccount.getId());
        assertEquals(updatedAccount, retrievedUpdatedAccount);

        Account deletedAccount = createdAccount.delete();
        assertTrue(deletedAccount.getDeleted());
        // Unlike many other things that have been deleted, this actually throws an exception.
        PermissionException permissionException = assertThrows(PermissionException.class, () -> Account.retrieve(createdAccount.getId()));
        assertTrue(permissionException.getStripeError()
                                      .getMessage()
                                      .endsWith(String.format(
                                              " does not have access to account '%s' (or that account does not exist). Application access may have been revoked.",
                                              createdAccount.getId())));
        assertEquals("account_invalid", permissionException.getCode());
        assertEquals("invalid_request_error",
                     permissionException.getStripeError()
                                        .getType());
        // todo: also test things like Account.capabilities(), .persons(), and .reject()
    }

    @Test
    void shouldNotBeAbleToFetchUnknownAccount() {
        PermissionException permissionException = assertThrows(PermissionException.class, () -> Account.retrieve("acct_nope"));
        assertTrue(permissionException.getStripeError()
                                      .getMessage()
                                      .endsWith(
                                              " does not have access to account 'acct_nope' (or that account does not exist). Application access may have been revoked."));
        assertEquals("account_invalid", permissionException.getCode());
        assertEquals("invalid_request_error",
                     permissionException.getStripeError()
                                        .getType());
    }

    public static AccountCreateParams defaultCreationParameters(String companyName) {
        return defaultCreationParametersBuilder(companyName).build();
    }

    public static Builder defaultCreationParametersBuilder(String companyName) {
        Map<String, String> externalAccount = new HashMap<>();
        externalAccount.put("object", "bank_account");
        externalAccount.put("country", "US");
        externalAccount.put("currency", "USD");
        externalAccount.put("routing_number", "110000000");
        externalAccount.put("account_number", "000123456789");
        externalAccount.put("default_for_currency", "true");

        return AccountCreateParams.builder()
                                  .setCountry("US")
                                  .setType(Type.CUSTOM)
                                  .setBusinessType(BusinessType.COMPANY)
                                  .setTosAcceptance(TosAcceptance.builder()
                                                                 .setUserAgent("Mozilla")
                                                                 .setIp("127.0.0.1")
                                                                 .setDate(Instant.now(StripeMock.getClock())
                                                                                 .getEpochSecond())
                                                                 .build())
                                  .setCompany(Company.builder()
                                                     .setTaxId("88-8888888")
                                                     .setName(companyName)
                                                     .setAddress(Address.builder()
                                                                        .setLine1("1 main street")
                                                                        .setCity("New York")
                                                                        .setPostalCode("12345")
                                                                        .setState("NY")
                                                                        .build())
                                                     .build())
                                  .setSettings(Settings.builder()
                                                       .setPayouts(Payouts.builder()
                                                                          .setSchedule(Schedule.builder()
                                                                                               .setInterval(Interval.MANUAL)
                                                                                               .build())
                                                                          .build())
                                                       .build())
                                  .setBusinessProfile(BusinessProfile.builder()
                                                                     .setName(companyName)
                                                                     .setProductDescription("Test")
                                                                     .build())
                                  .setCapabilities(Capabilities.builder()
                                                               .setTransfers(Transfers.builder()
                                                                                      .setRequested(true)
                                                                                      .build())
                                                               .build())
                                  .putExtraParam("external_account", externalAccount)
                                  .putMetadata("integration_test", "true");
    }

}

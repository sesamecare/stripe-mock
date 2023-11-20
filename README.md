# Stripe mock

This is meant to be a dynamic mock that mimics the internals of Stripe in terms of how entities relate to each other, what transitions they can go through,
etc. There are other static mocks out there that will mimic just the API, but this is meant to be able to be a full replacement for running unit tests.

## Design philosophy
The intent is that this should be a drop-in replacement for actually talking to Stripe. As such, there are only two ways to interact with the Stripe mock:
1. The `StripeMock` class
2. The Stripe classes, such as `PaymentIntent` and `Customer` etc.

This means that there is **no** access to the entities themselves, or any of the internals inside the Stripe mock. While it might be tempting to just set up 
your tests "directly" inside the mock's memory, this would be a bad idea, because it might let you validate invariants, and it might let you do things you 
could not possibly do if you were testing using a real Stripe connection. 

There are some exceptions, however. These exceptions are mostly to smooth the transition from using real Stripe testing to using a mock, and they are related
to the boostrapping done in `StripeMock.reset()`. 
1. You can change the time using `StripeMock.adjustTime()` to let you set up things like subscriptions in the past. This is useful when migrating from using 
   Stripe testing, where you might rely on things you created in the past in Stripe.
2. You can set the id for entities on creation by passing `StripeMock.OVERRIDE_ID_FOR_TESTING` in via the metadata.

If you are starting from scratch, you should avoid using these features, as well as the bootstrapping, if possible. It would be better to create (and 
potentially destroy) the Stripe resources you need with each test. However, there are things that are hard to test, such as the passage of time, that are
tricky to test without these features. Long-term this might be replaced by implementing Stripe's test clocks.

# How do I use it?
This example uses jUnit, but you can use any testing framework you would like. The idea is that you start and stop the mock rarely, and you reset the state
as often as you would like. As you can see in this code base, it's easy to do this in an abstract base class for your tests, such as in 
`com.sesame.oss.stripemock.AbstractStripeMockTest`. This is what it looks like
```java
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
```

## Bootstrapping / Migrating
If you are migrating from tests that use real Stripe, you might be relying on data that exists in Stripe already. In this case you can bootstrap these entities.
This bootstrap only runs if the Stripe mock is enabled, so you don't have to modify any code when switching as per the "Disabling the mock" section below.
You pass a bootstrap instance to the `StripeMock.reset()` method. This causes it to be run when the mock is reset, assuming the mock is enabled.
This is what it looks like:
```java

StripeMock.reset(() -> {
    createTheExpectedCustomerOnStripe();
});
```

In these cases you might also have expected ids of things. When using the Stripe mock, you can pass in metadata to any `.create()` to force the id of that
entity. You should only do this when bootstrapping, as this isn't a feature that's supported by Stripe.
This is what it looks like:
```java
Customer createdCustomer = Customer.create(CustomerCreateParams.builder()
                                                               .putMetadata(StripeMock.OVERRIDE_ID_FOR_TESTING, "cus_abc123")
                                                               .setName("Mike Smith")
                                                               .build());
assertEquals("cus_abc123", createdCustomer.getId());

```

# Justification

Running unit tests against Stripe's test environment works really well, but it's incredibly slow. This aims to significantly reduce Stripe-heavy unit tests.
In one repo, IntelliJ IDEA reported going from just over 8 minutes for the complete set of unit tests to just over 50 seconds with the mock enabled. These
tests make heavy use of Stripe, so your results might differ, but the Stripe mock can significantly improve your test times.

# Alternatives

We could have made something like a simple WireMock-based thing that we just hook into the Stripe client. While that would work for smaller things, it would
mean a lot of mocking, as the API responses from Stripe can be very large, and we would have to express any logic we might need as things like scenario
transitions. It feels like this would require more work, and be less flexible, so we chose to go with our current approach instead.

# Progress

It's unlikely this mock will ever support ALL Stripe's entities. It will support the ones we need for our purposes, and as we need more and more, so will
this mock expand to support more and more.

# TODO list
There are still *a lot* of TODOs in the code. That's fine. That's just notes of all the things that we'd like to support, and places where we need to align
the responses with the real Stripe api responses.

* More entities
* More actions on entities. Simple actions like PaymentIntent.confirm() come first, but there are more complex and less used actions that will have to wait.
* Pagination
* Search
* More listings

## Future features

Right now we only need to be able to assert via the API. In the future, we might want to do stuff like artificially move time forward, and to trigger webhooks.
Depending on what needs arise, things like this might be added. Even possibly webhooks via an interface, rather than an HTTP endpoint, to support various
kinds of unit tests.

# Dependencies

We want as few dependencies as possible. Ideally zero. That's why we use java.util.logging and the build-in simple HTTP server. This should make the library
usable in as many situations as possible. The using application will provide the Stripe and GSON dependencies. We need tests, obviously, so we depend on
junit, but that's only for tests.

# Disabling the mock

It's possible that you might want to run using the Stripe mock under certain circumstances, and then using real Stripe under others. Perhaps you run the mock
for normal unit tests, and real Stripe for nightly builds. This can easily be accomplished without any code changes. Set either the `stripe.mock.disabled`
system property or the `STRIPE_MOCK_DISABLED` environment variable to `true`, and the mocks will be disabled. If you want, you can also inject an actual test
Stripe key into the system by specifying it in either the `stripe.api.key` system property or the `STRIPE_API_KEY` environment variable.

# How do I know it behaves exactly like the Stripe API?
You don't, and it doesn't. Not 100%. However, our goal is to behave the same way for the majority of use cases we actually have. We're obviously not 
re-implementing Stripe here. But we aim to be as correct as possible for the most common use-cases. We validate this by running our same tests against
Stripe itself, and ensuring that the results we get out are the same. Once the tests are tested on Stripe, we adjust the Stripe mock to produce the same
output. That's how we can get close enough for it to be usable.

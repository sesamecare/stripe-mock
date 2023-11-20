package com.sesame.oss.stripemock;

import com.sesame.oss.stripemock.entities.StripeEntities;
import com.sesame.oss.stripemock.http.StripeApiHttpHandler;
import com.sesame.oss.stripemock.util.MutableClock;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Instant;

public class StripeMock {
    /**
     * When switching from using a normal stripe integration during testing, there might be "known" values, for example customers with a known
     * set of payment methods, that are returned in other mocks. As those mocks might be static, we're going to make it possible to mimic this
     * behavior. This is done by allowing an ID to be overridden when passed in as metadata. Ideally this should be rare, but it will smooth
     * transition from real stripe tests to mocked stripe tests.
     */
    public static final String OVERRIDE_ID_FOR_TESTING = "__override_id_for_testing";
    private static final MutableClock CLOCK = new MutableClock(Clock.systemDefaultZone()
                                                                    .getZone(),
                                                               Clock.systemDefaultZone()
                                                                    .instant());
    private final StripeEntities stripeEntities = new StripeEntities(CLOCK);
    private final HttpServer httpServer;

    /**
     * This check exists for easy switching between the real and mocked mode. This is useful if you want to run the same set of tests in two modes,
     * for example with the mock enabled during normal unit tests, and then disabled during nightly tests. Setting this flag allows you to do so
     * without changing the code. However, if you make use of {@link #OVERRIDE_ID_FOR_TESTING}, that's not supported by stripe itself, so this might
     * have limited usefulness.
     *
     * <p>To make sure that this can be controlled exclusively from outside the process, you can use the STRIPE_API_KEY environment variable or the
     * stripe.api.key system property to set the key.
     */
    private static final boolean DISABLED = Boolean.parseBoolean(System.getProperty("stripe.mock.disabled", System.getenv("STRIPE_MOCK_DISABLED")));
    private static final boolean LOG_REQUESTS = Boolean.parseBoolean(System.getProperty("stripe.mock.log.requests", System.getenv("STRIPE_MOCK_LOG_REQUESTS")));
    private static final String STRIPE_API_KEY = System.getProperty("stripe.api.key", System.getenv("STRIPE_API_KEY"));

    private static volatile StripeMock stripeMock;
    private static volatile boolean logRequests = LOG_REQUESTS;

    private StripeMock(int port) throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress("localhost", port), 50);
        httpServer.createContext("/v1/", new StripeApiHttpHandler(stripeEntities));
        httpServer.start();
    }

    /**
     * This will adjust time to the instant specified. This is handy if you need to create things in the past.
     * By default the time is set at the creation of the mock, and does not advance unless this method is called.
     *
     * @param instant the instant you want to adjust the mock's clock to.
     */
    public static void adjustTimeTo(Instant instant) {
        CLOCK.setInstant(instant);
    }

    static Clock getClock() {
        return CLOCK;
    }

    /**
     * Setting this will override whatever was specified via system properties or environment variables. This can be set at any time, even after the mock is started.
     */
    public static void setLogRequests(boolean logRequests) {
        StripeMock.logRequests = logRequests;
    }

    public static boolean isDisabled() {
        return DISABLED;
    }

    public static boolean isLogRequests() {
        return logRequests;
    }

    public static synchronized int start() {
        return start(0);
    }

    public static synchronized int start(int port) {
        if (DISABLED) {
            if (Stripe.apiKey == null) {
                Stripe.apiKey = STRIPE_API_KEY;
            }
            return -1;
        }
        if (stripeMock == null) {
            try {
                stripeMock = new StripeMock(port);
                int boundPort = stripeMock.httpServer.getAddress()
                                                     .getPort();
                if (Stripe.apiKey == null) {
                    // If it's already set, we don't touch it.
                    // That way people can easily switch between the mock and normal tests.
                    // However if it is not set, we have to set it, as otherwise the REST client won't work.
                    Stripe.apiKey = "sk_test_clearly_fake";
                }
                Stripe.overrideApiBase("http://localhost:" + boundPort);
                Stripe.overrideConnectBase("http://localhost:" + boundPort);
                Stripe.overrideUploadBase("http://localhost:" + boundPort);
            } catch (IOException e) {
                throw new IllegalStateException(String.format("Could not start stripe mock on port %d", port), e);
            }
        }
        return stripeMock.httpServer.getAddress()
                                    .getPort();
    }

    /**
     * Resets the internal state, but does not stop the service.
     */
    public static synchronized void reset() {
        try {
            reset(null);
        } catch (StripeException e) {
            // This should never happen as we don't have a bootstrap.
            throw new AssertionError(e);
        }
    }

    /**
     * Resets the internal state, but does not stop the service.
     *
     * @param bootstrapIfEnabled as it is {@link #DISABLED possible} to disable the stripe mock with a flag, any mock-only bootstrapping should be
     *                           applied conditionally as well. To do that, pass a {@link StripeBootstrap} in here with the bootstrap code. This ensures
     *                           that it is only run if the mock is enabled.
     */
    public static synchronized void reset(StripeBootstrap bootstrapIfEnabled) throws StripeException {
        if (DISABLED) {
            return;
        }
        if (stripeMock != null) {
            stripeMock.stripeEntities.clear();
        }
        if (bootstrapIfEnabled != null) {
            bootstrapIfEnabled.bootstrap();
        }
    }

    public static synchronized void stop() {
        if (DISABLED) {
            return;
        }
        if (stripeMock != null) {
            reset();
            stripeMock.httpServer.stop(0);
            stripeMock = null;

            Stripe.overrideApiBase(Stripe.LIVE_API_BASE);
            Stripe.overrideConnectBase(Stripe.CONNECT_API_BASE);
            Stripe.overrideUploadBase(Stripe.UPLOAD_API_BASE);
        }
    }

    public interface StripeBootstrap {
        public void bootstrap() throws StripeException;
    }
}

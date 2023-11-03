package com.sesame.oss.stripemock;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

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

}

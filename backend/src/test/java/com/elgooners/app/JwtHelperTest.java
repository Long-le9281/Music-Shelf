package com.elgooners.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtHelperTest {

    @Test
    void tokenContainsOriginalUsername() {
        JwtHelper helper = new JwtHelper();

        String token = helper.createToken("demo-user");
        String decodedUsername = helper.getUsernameFromToken(token);

        assertEquals("demo-user", decodedUsername);
    }

    @Test
    void invalidTokenReturnsNull() {
        JwtHelper helper = new JwtHelper();

        assertNull(helper.getUsernameFromToken("not-a-real-jwt"));
    }
}


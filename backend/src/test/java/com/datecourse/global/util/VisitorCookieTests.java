package com.datecourse.global.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.Cookie;

class VisitorCookieTests {

    @Test
    void 쿠키가_없으면_새_id_를_만들어_HttpOnly_로_1년_내려보낸다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-Proto", "https");
        MockHttpServletResponse response = new MockHttpServletResponse();

        UUID id = VisitorCookie.ensure(request, response);

        assertThat(response.getHeader("Set-Cookie"))
                .contains("dc_vid=" + id).contains("HttpOnly").contains("Secure").contains("SameSite=Lax").contains("Max-Age=31536000");
    }

    @Test
    void 있으면_그_id_를_그대로_쓰고_이상한_값은_없는_것으로() {
        UUID id = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("dc_vid", id.toString()));
        assertThat(VisitorCookie.ensure(request, new MockHttpServletResponse())).isEqualTo(id);

        MockHttpServletRequest bad = new MockHttpServletRequest();
        bad.setCookies(new Cookie("dc_vid", "not-a-uuid"));
        assertThat(VisitorCookie.read(bad)).isEmpty();
    }
}

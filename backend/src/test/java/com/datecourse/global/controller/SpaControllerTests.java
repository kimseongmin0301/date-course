package com.datecourse.global.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SpaController.class)
class SpaControllerTests {

    @Autowired
    MockMvc mvc;

    @Test
    void 화면_주소는_index_html() throws Exception {
        mvc.perform(get("/search")).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        mvc.perform(get("/courses/12")).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        mvc.perform(get("/saved")).andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void 코스_번호가_아니면_404() throws Exception {
        mvc.perform(get("/courses/abc")).andExpect(status().isNotFound());
    }
}

package com.datecourse.domain.keyword.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.datecourse.domain.keyword.dto.KeywordResponse;
import com.datecourse.domain.keyword.service.KeywordService;

@WebMvcTest(KeywordController.class)
class KeywordControllerTests {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    KeywordService keywordService;

    @Test
    void 키워드_칩_목록() throws Exception {
        given(keywordService.all()).willReturn(List.of(
                new KeywordResponse("quiet", "조용", "mood"),
                new KeywordResponse("indoor", "실내", "setting")));

        mvc.perform(get("/api/keywords"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("quiet"))
                .andExpect(jsonPath("$[0].name").value("조용"))
                .andExpect(jsonPath("$[1].kind").value("setting"));
    }
}

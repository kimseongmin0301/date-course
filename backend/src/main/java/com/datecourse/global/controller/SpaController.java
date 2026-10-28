package com.datecourse.global.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 화면(React) 주소로 바로 들어오거나 새로고침하면 index.html 을 준다 (화면이 주소를 보고 그린다).
 * "/" 는 스프링이 static/index.html 을 알아서 보여준다. 그 밖의 없는 주소는 404.
 */
@Controller
public class SpaController {

    @GetMapping({"/search", "/saved", "/courses/{id:[0-9]+}"})
    public String index() {
        return "forward:/index.html";
    }
}

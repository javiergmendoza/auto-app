package com.javi.autoapp.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

@Slf4j
@Controller
public class LoginController {

    @RequestMapping("/login")
    public ModelAndView login() {
        return new ModelAndView("login");
    }
}

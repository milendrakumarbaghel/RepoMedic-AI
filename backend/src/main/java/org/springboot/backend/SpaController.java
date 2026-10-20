package org.springboot.backend;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class SpaController {
    @GetMapping({"/", "/dashboard", "/history", "/analyses", "/analyses/{id}", "/analysis/{id}"})
    String index() {
        if (!new ClassPathResource("static/index.html").exists())
            throw new ApiException(404, "ui_not_built", "Frontend assets are not present; the backend API is available under /api");
        return "forward:/index.html";
    }
}

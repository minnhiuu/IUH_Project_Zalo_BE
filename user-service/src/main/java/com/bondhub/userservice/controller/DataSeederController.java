package com.bondhub.userservice.controller;

import com.bondhub.userservice.service.user.DataSeederService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/internal/seed")
@RequiredArgsConstructor
public class DataSeederController {

    private final DataSeederService dataSeederService;

    @PostMapping("/users")
    public String seedUsers(@RequestParam(defaultValue = "50") int count) {
        return dataSeederService.seedUsers(count);
    }
}

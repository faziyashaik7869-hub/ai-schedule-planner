package com.example.gemini_backend;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class GeminiController {

    @Autowired
    private GeminiService geminiService;

    @PostMapping("/chat")
    public String chat(@RequestBody Map<String, String> request) {
        String prompt = request.get("prompt");
        return geminiService.askGemini(prompt);
    }
}
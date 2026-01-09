package com.gabriel.qaphptranslationagent.controller;

import com.gabriel.qaphptranslationagent.model.PageElement;
import com.gabriel.qaphptranslationagent.model.TranslationSuggestion;
import com.gabriel.qaphptranslationagent.service.WebScannerService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;
import java.util.*;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/agent")
public class TranslationAgentController {

    private final WebScannerService scannerService;
    private final ChatClient chatClient;

    // No Spring AI moderno, injetamos o Builder para configurar o ChatClient
    public TranslationAgentController(WebScannerService scannerService, ChatClient.Builder chatClientBuilder) {
        this.scannerService = scannerService;
        // No construtor do seu Controller
        this.chatClient = chatClientBuilder
                .defaultSystem("""
                Você é um agente de QA especializado em tradução de software.
                CONTEXTO: Um sistema de troca de casas (Home4Her).
                OBJETIVO: Identificar textos em INGLÊS e sugerir chaves semânticas e traduções para PORTUGUÊS.
                
                EXEMPLO DE FORMATO:
                Original: "Create a home listing and secure 300 HerMiles!"
                Chave: "dashboard.home.promo_bonus"
                Tradução: "Crie um anúncio de casa e garanta 300 HerMiles!"
                
                REGRAS:
                - Use chaves que descrevam o local do texto (ex: header.logout, menu.settings).
                - Nunca use 'modulo.submodulo'.
                - Se o texto já estiver em Português, ignore-o.
                """)
                .build();
    }

    @GetMapping("/scan-full")
    public List<TranslationSuggestion> startFullScan(@RequestParam String baseUrl) {
        // 1. Crawler descobre todas as páginas e extrai os elementos
        Map<String, List<PageElement>> siteData = scannerService.discoverAndScan(baseUrl);

        // 2. Consolida todos os elementos de todas as páginas em uma lista única para a IA
        // Adicionamos a URL da página no elemento para a IA ter contexto
        return siteData.entrySet().stream()
                .flatMap(entry -> analyzeElements(entry.getValue(), entry.getKey()).stream())
                .collect(Collectors.toList());
    }

    @GetMapping("/analyze")
    public List<TranslationSuggestion> analyzeSinglePage(@RequestParam String baseUrl) {
        List<PageElement> elements = scannerService.scanLocalhost(baseUrl);
        return analyzeElements(elements, baseUrl);
    }

    /**
     * Método privado que faz a chamada real para a IA
     */
    private List<TranslationSuggestion> analyzeElements(List<PageElement> elements, String url) {
        return chatClient.prompt()
                .user(u -> u.text("Analise estes elementos da página {url}: {elements}")
                        .param("url", url)
                        .param("elements", elements))
                // O .entity() faz o parse automático do JSON para sua lista de objetos
                .call()
                .entity(new ParameterizedTypeReference<List<TranslationSuggestion>>() {});
    }
}

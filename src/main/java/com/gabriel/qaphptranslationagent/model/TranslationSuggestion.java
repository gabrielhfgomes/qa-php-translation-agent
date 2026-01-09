package com.gabriel.qaphptranslationagent.model;

public record TranslationSuggestion(
        String pageUrl,          // Em qual página o erro foi encontrado
        String originalText,     // O texto em inglês (ex: "Create a home listing")
        String suggestedKey,     // A chave que a IA inventou (ex: "home.create_listing")
        String translatedText,   // A tradução sugerida (ex: "Criar anúncio de casa")
        String cssSelector,      // O caminho para o Java saber onde injetar o data-translate
        boolean alreadyExists    // Se essa chave já existe nos seus arquivos JSON/PHP
) {}
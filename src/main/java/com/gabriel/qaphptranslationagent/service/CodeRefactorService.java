package com.gabriel.qaphptranslationagent.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class CodeRefactorService {

    @Value("${project.php.path}")
    private String phpProjectPath;

    public void injectDataTranslate(String originalText, String key) throws IOException {
        Path root = Paths.get(phpProjectPath);

        List<Path> files = Files.walk(root)
                .filter(p -> p.toString().endsWith(".php") || p.toString().endsWith(".blade.php"))
                .toList();

        // Escapa caracteres especiais do texto original para não quebrar a Regex
        String escapedText = Pattern.quote(originalText);

        // Regex explica:
        // (<[a-zA-Z0-String]+) -> Grupo 1: Pega o início da tag (ex: <span ou <p)
        // ([^>]*?)             -> Grupo 2: Pega qualquer atributo existente antes do fechamento
        // >                    -> O fechamento da tag de abertura
        // \\s* -> Qualquer espaço ou quebra de linha opcional
        // " + escapedText + "  -> O texto que queremos traduzir
        String regex = "(<[a-zA-Z0-9]+)([^>]*?)>(\\s*)" + escapedText;

        for (Path filePath : files) {
            String content = Files.readString(filePath, StandardCharsets.UTF_8);

            // Verifica se o texto existe e se JÁ NÃO tem o data-translate
            if (content.contains(originalText) && !content.contains("data-translate=\"" + key + "\"")) {

                Pattern pattern = Pattern.compile(regex);
                Matcher matcher = pattern.matcher(content);

                StringBuilder sb = new StringBuilder();
                boolean found = false;

                while (matcher.find()) {
                    // Monta a nova tag: <tag + atributos + data-translate + > + espaços + texto
                    String replacement = matcher.group(1) + matcher.group(2) + " data-translate=\"" + key + "\">" + matcher.group(3) + originalText;
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
                    found = true;
                }
                matcher.appendTail(sb);

                if (found) {
                    Files.writeString(filePath, sb.toString(), StandardCharsets.UTF_8);
                    System.out.println("✅ Refatorado com Regex: " + filePath.getFileName());
                }
            }
        }
    }
}

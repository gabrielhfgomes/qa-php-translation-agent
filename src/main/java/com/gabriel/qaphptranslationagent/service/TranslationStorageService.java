package com.gabriel.qaphptranslationagent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class TranslationStorageService {

    @Value("${project.php.lang-path}")
    private String langPath;

    public void updateTranslations(String key, String ptValue, String enValue) throws Exception {
        // No PHP, as chaves costumam ser o texto original em inglês
        // Mas como estamos automatizando, usaremos a sugestão da IA

        // Atualiza pt.php
        updatePhpFile("/pt/frontend.php", key, ptValue);
        // Atualiza en.php
        updatePhpFile("/en/frontend.php", key, enValue);
    }

    private void updatePhpFile(String fileName, String key, String value) throws IOException {
        Path path = Paths.get(langPath, fileName);

        if (!Files.exists(path)) {
            // Se o arquivo não existe, cria um esqueleto básico
            String skeleton = "<?php\n\nreturn array (\n);\n";
            Files.writeString(path, skeleton);
        }

        String content = Files.readString(path, StandardCharsets.UTF_8);

        // Verifica se a chave já existe para não duplicar
        if (content.contains("'" + key + "' =>")) {
            System.out.println("⚠️ Chave já existe em " + fileName + ". Pulando escrita.");
            return;
        }

        // Prepara a nova linha no formato PHP
        // Escapa aspas simples para não quebrar o código PHP
        String escapedKey = key.replace("'", "\\'");
        String escapedValue = value.replace("'", "\\'");
        String newLine = "  '" + escapedKey + "' => '" + escapedValue + "',\n";

        // Localiza a última ocorrência do fechamento do array
        int lastIndex = content.lastIndexOf(");");
        if (lastIndex != -1) {
            StringBuilder sb = new StringBuilder(content);
            sb.insert(lastIndex, newLine);
            Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
            System.out.println("✅ Adicionado ao PHP: " + fileName);
        }
    }
}
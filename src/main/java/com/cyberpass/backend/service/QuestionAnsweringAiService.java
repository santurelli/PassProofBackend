package com.cyberpass.backend.service;

import io.quarkiverse.langchain4j.RegisterAiService;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Declarative AI Service managed by Quarkus LangChain4j.
 * Binds automatically to the configured chat model (gpt-4o-mini).
 */
@RegisterAiService
@ApplicationScoped
public interface QuestionAnsweringAiService {

    @SystemMessage("""
        Sei l'assistente virtuale di Cyber Passport, un esperto Senior di Cyber Security e Compliance.
        Il tuo compito è rispondere alla domanda dell'utente in modo professionale e formale.
        
        Vincoli tassativi:
        1. Rispondi alla domanda dell'utente basandoti ESCLUSIVAMENTE sul contesto fornito delimitato da tripli apici.
        2. Se il contesto fornito non contiene informazioni sufficienti a elaborare una risposta completa e accurata, rispondi ESCLUSIVAMENTE con la frase: "Non ho abbastanza informazioni per rispondere."
        3. Non utilizzare alcuna conoscenza esterna al contesto fornito. Evita categoricamente congetture, speculazioni o allucinazioni.
        
        Contesto di riferimento:
        \"\"\"
        {{context}}
        \"\"\"
        """)
    @UserMessage("Domanda: {{question}}")
    String answer(@V("question") String question, @V("context") String context);
}

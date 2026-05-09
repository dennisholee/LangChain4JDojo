package io.forest.composableretrieval.core;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.router.DefaultQueryRouter;
import dev.langchain4j.service.AiServices;

import java.util.List;

/**
 * Application-layer RAG service.
 *
 * <p>Wires together:
 * <ul>
 *   <li>One or more {@link ContentRetriever}s (backed by LangChain4J adapters)</li>
 *   <li>{@link DefaultQueryRouter} — routes each query to all provided retrievers in parallel</li>
 *   <li>LangChain4J {@link DefaultRetrievalAugmentor} to inject retrieved context into the prompt</li>
 *   <li>A {@link ChatLanguageModel} (e.g. OpenAI GPT-4o-mini) to generate the final answer</li>
 * </ul>
 *
 * <p>The composable retriever's adapters are bridged to {@link ContentRetriever} via
 * {@link io.forest.composableretrieval.adapters.RetrieverPortContentRetrieverBridge} so that clean-architecture
 * port implementations can be used seamlessly inside the LangChain4J RAG pipeline.
 */
public class RagService {

    private final Assistant assistant;

    public RagService(ChatLanguageModel chatModel, List<ContentRetriever> contentRetrievers) {
        // DefaultQueryRouter broadcasts the query to ALL provided ContentRetrievers.
        RetrievalAugmentor augmentor = DefaultRetrievalAugmentor.builder()
            .queryRouter(new DefaultQueryRouter(contentRetrievers))
            .build();

        this.assistant = AiServices.builder(Assistant.class)
            .chatLanguageModel(chatModel)
            .retrievalAugmentor(augmentor)
            .build();
    }

    /** Ask a free-form question; the retrieved context is injected automatically. */
    public String answer(String question) {
        return assistant.answer(question);
    }

    /** LangChain4J AiServices proxy — implemented at runtime via dynamic proxy. */
    interface Assistant {
        String answer(String question);
    }
}

package io.quarkiverse.langchain4j.it.chat;

import static io.quarkiverse.langchain4j.it.MessageUtil.createChatCompletionRequest;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;

import org.jboss.resteasy.reactive.RestStreamElementType;

import dev.ai4j.openai4j.chat.ChatCompletionChoice;
import dev.ai4j.openai4j.chat.Delta;
import dev.ai4j.openai4j.chat.Message;
import io.quarkiverse.langchain4j.QuarkusOpenAiClient;
import io.quarkiverse.langchain4j.runtime.config.LangChain4jRuntimeConfig;
import io.quarkiverse.langchain4j.runtime.config.OpenAiServer;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

@Path("quarkusClient/chat")
public class QuarkusOpenAiClientChatResource {

    private final QuarkusOpenAiClient quarkusOpenAiClient;

    public QuarkusOpenAiClientChatResource(LangChain4jRuntimeConfig runtimeConfig) {
        OpenAiServer openAiServer = runtimeConfig.openAi();
        String token = openAiServer.apiKey().get();
        String baseUrl = openAiServer.baseUrl();
        quarkusOpenAiClient = QuarkusOpenAiClient.builder().openAiApiKey(token).baseUrl(baseUrl).build();
    }

    @GET
    @Path("sync")
    public String sync() {
        return quarkusOpenAiClient
                .chatCompletion(createChatCompletionRequest("Write a short 1 paragraph funny poem about dynamic typing"))
                .execute().content();
    }

    @GET
    @Path("async")
    public Uni<String> async() {
        var request = createChatCompletionRequest("Write a short 1 paragraph funny poem about Scrum");
        return Uni.createFrom().emitter(emitter -> {
            quarkusOpenAiClient.chatCompletion(request)
                    .onResponse(res -> emitter.complete(res.content()))
                    .onError(emitter::fail)
                    .execute();
        });
    }

    @GET
    @Path("streaming")
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> streaming() {
        var request = createChatCompletionRequest("Write a short 1 paragraph funny poem about javascript frameworks");
        return Multi.createFrom().emitter(emitter -> {
            quarkusOpenAiClient.chatCompletion(request)
                    .onPartialResponse(r -> {
                        if (r.choices() != null) {
                            if (r.choices().size() == 1) {
                                ChatCompletionChoice choice = r.choices().get(0);
                                Delta delta = choice.delta();
                                if (delta != null) {
                                    if (delta.content() != null) {
                                        emitter.emit(delta.content());
                                    }
                                } else { // normally this is not needed but mock APIs don't really work with the streaming response
                                    Message message = choice.message();
                                    if (message != null) {
                                        emitter.emit(message.content());
                                    }
                                }
                            }
                        }
                    })
                    .onComplete(() -> {
                        emitter.complete();
                    })
                    .onError((t) -> {
                        emitter.fail(t);
                    })
                    .execute();
        });
    }

}

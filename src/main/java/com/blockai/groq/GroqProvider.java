package com.blockai.groq;

import java.util.function.Consumer;

import com.blockai.ai.planner.Roadmap;

public class GroqProvider implements AIProvider {
    @Override
    public void sendMessageAsync(String contextMessage, Consumer<Roadmap> onSuccess, Consumer<String> onError) {
        GroqClient.sendMessageAsync(contextMessage, onSuccess, onError);
    }
}

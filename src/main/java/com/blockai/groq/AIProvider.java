package com.blockai.groq;

import com.blockai.ai.planner.Roadmap;

public interface AIProvider {
    void sendMessageAsync(String contextMessage, java.util.function.Consumer<Roadmap> onSuccess, java.util.function.Consumer<String> onError);
}

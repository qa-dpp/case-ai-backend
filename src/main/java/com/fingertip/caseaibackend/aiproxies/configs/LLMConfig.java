package com.fingertip.caseaibackend.aiproxies.configs;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LLMConfig {


    @Value("${case-model.analyze.api-key}")
    private String analyze_apiKey = "";
    @Value("${case-model.analyze.base-url}")
    private String analyze_baseUrl = "";
    @Value("${case-model.analyze.model}")
    private String analyze_Model = "";


    @Bean
    public ChatModel analyzeModel() {
        OpenAiChatOptions options = new OpenAiChatOptions();
        options.setModel(analyze_Model);   // 模型类型
        options.setTemperature(0.7);       // 随机性控制
        return OpenAiChatModel.builder().openAiApi(OpenAiApi.builder().apiKey(analyze_apiKey).baseUrl(analyze_baseUrl).build()).defaultOptions(options).build();
    }

    @Value("${case-model.generate.api-key}")
    private String generate_apiKey = "";
    @Value("${case-model.generate.base-url}")
    private String generate_baseUrl = "";
    @Value("${case-model.generate.model}")
    private String generate_Model = "";


    @Bean
    public ChatModel generateModel() {
        OpenAiChatOptions options = new OpenAiChatOptions();
        options.setModel(generate_Model);   // 模型类型
        options.setTemperature(0.7);       // 随机性控制
        return OpenAiChatModel.builder().openAiApi(OpenAiApi.builder().apiKey(generate_apiKey).baseUrl(generate_baseUrl).build()).defaultOptions(options).build();
    }

    @Value("${case-model.reviewer.api-key}")
    private String reviewer_apiKey = "";
    @Value("${case-model.reviewer.base-url}")
    private String reviewer_baseUrl = "";
    @Value("${case-model.reviewer.model}")
    private String reviewer_Model = "";

    @Bean
    public ChatModel reviewerModel() {
        OpenAiChatOptions options = new OpenAiChatOptions();
        options.setModel(reviewer_Model);   // 模型类型
        options.setTemperature(0.7);       // 随机性控制
        return OpenAiChatModel.builder().openAiApi(OpenAiApi.builder().apiKey(reviewer_apiKey).baseUrl(reviewer_baseUrl).build()).defaultOptions(options).build();
    }

    @Value("${case-model.format.api-key}")
    private  String format_apiKey = "";
    @Value("${case-model.format.base-url}")
    private  String format_baseUrl = "";
    @Value("${case-model.format.model}")
    private  String format_Model = "";


    @Bean
    public ChatModel formatModel() {
        OpenAiChatOptions options = new OpenAiChatOptions();
        options.setModel(format_Model);   // 模型类型
        options.setTemperature(0.7);       // 随机性控制
        return OpenAiChatModel.builder().openAiApi(OpenAiApi.builder().apiKey(format_apiKey).baseUrl(format_baseUrl).build()).defaultOptions(options).build();
    }

    @Value("${case-model.visual.api-key}")
    private String visual_apiKey = "";
    @Value("${case-model.visual.base-url}")
    private String visual_baseUrl = "";
    @Value("${case-model.visual.model}")
    private String visual_Model = "";

    @Bean
    public ChatModel visualModel() {
        OpenAiChatOptions options = new OpenAiChatOptions();
        options.setModel(visual_Model);   // 模型类型
        options.setTemperature(0.7);       // 随机性控制
        return OpenAiChatModel.builder().openAiApi(OpenAiApi.builder().apiKey(visual_apiKey).baseUrl(visual_baseUrl).build()).defaultOptions(options).build();
    }
}

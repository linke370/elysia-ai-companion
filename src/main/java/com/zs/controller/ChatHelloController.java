package com.zs.controller;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
public class ChatHelloController {
    @Resource(name = "qwen")
    private ChatModel qwenchatModel;
    @Resource(name = "deepSeek")
    private ChatModel deepSeekModel;
   @Resource(name = "deepSeekChatClient")
   private ChatClient deepSeekChatClient;


    @GetMapping("/doChat1")
    public Flux<String> daoChat1( String msg){
        SystemMessage systemMessage = new SystemMessage("你是一名小学老师，擅长讲故事");
        UserMessage userMessage = new UserMessage(msg);
        Prompt prompt = new Prompt(systemMessage,userMessage );
        Flux<String> map = deepSeekModel.stream(prompt).map(chatResponse -> chatResponse.getResults().get(0).getOutput().getText());
        return map;
    }
    @GetMapping("/doChat2")
    public Flux<String> daoChat2(@RequestParam(name = "msg",defaultValue = "葫芦娃") String msg){
            return deepSeekChatClient.prompt()
                    .system("你是一名刚刚入职的小学老师，不擅长讲故事")
                    .user(msg)
                    .stream()
                    .chatResponse()
                    .map(chatResponse -> chatResponse.getResults().get(0).getOutput().getText());
    }
    @GetMapping("/doChat3")
    public Flux<String> daoChat3(@RequestParam(name = "msg",defaultValue = "你是谁？") String msg){
        return deepSeekChatClient.prompt().system("你是一名刚刚入职的小学老师")
                .user(msg)
                .stream()
                .content();
    }
    @GetMapping("/doChat4")
    public String daoChat4(@RequestParam(name = "msg",defaultValue = "你是谁？") String msg){
        String result = deepSeekChatClient.prompt()
                .user(msg)
                .call()
                .chatResponse()
                .getResults()
                .get(0)
                .getOutput()
                .getText();
        return result;
    }
    @GetMapping("/doChat5")
    public Flux<String> daoChat5(@RequestParam(name = "msg",defaultValue = "你是谁？") String msg){
        String result = deepSeekChatClient.prompt()
                .user(msg)
                .call()
                .chatResponse()
                .getResults()
                .get(0)
                .getOutput()
                .getText();
        return Flux.just(result);
    }
}

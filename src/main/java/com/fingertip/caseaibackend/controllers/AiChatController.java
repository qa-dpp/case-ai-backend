package com.fingertip.caseaibackend.controllers;

import com.alibaba.cloud.ai.dashscope.chat.MessageFormat;
import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.alibaba.fastjson.JSON;
import com.fingertip.caseaibackend.aiproxies.nodes.CaseFormatNode;
import com.fingertip.caseaibackend.aiproxies.nodes.CaseGenerateNode;
import com.fingertip.caseaibackend.aiproxies.nodes.CaseReviewerNode;
import com.fingertip.caseaibackend.aiproxies.nodes.FeedbackDispatcher;
import com.fingertip.caseaibackend.commons.Consts;
import com.fingertip.caseaibackend.dtos.ApiResult;
import com.fingertip.caseaibackend.dtos.ChatDto;
import org.apache.pdfbox.Loader;
import org.bsc.async.AsyncGenerator;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import org.springframework.ai.content.Media;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import fr.opensagres.poi.xwpf.converter.pdf.PdfConverter;
import fr.opensagres.poi.xwpf.converter.pdf.PdfOptions;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

import static com.alibaba.cloud.ai.dashscope.common.DashScopeApiConstants.MESSAGE_FORMAT;
import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;
import static com.fingertip.caseaibackend.commons.Consts.ANALYZE_PROMPT;

@RestController
@RequestMapping("/ai-api")
public class AiChatController {

    private static final String DEFAULT_PROMPT = "你好，介绍下你自己！";

    private final ChatClient openAiAnalyzeChatClient;
    private final ChatClient openAiGenerateChatClient;
    private final ChatClient openAiReviewerChatClient;
    private final ChatClient openAiFormatChatClient;
    private final ChatClient openAiVisualChatClient;


    public AiChatController(@Qualifier("analyzeModel") ChatModel analyzeModel, @Qualifier("generateModel") ChatModel generateModel, @Qualifier("reviewerModel") ChatModel reviewerModel, @Qualifier("formatModel") ChatModel formatModel, @Qualifier("visualModel") ChatModel visualModel) {

        this.openAiAnalyzeChatClient = ChatClient.builder(analyzeModel)
                // 实现 Logger 的 Advisor
                .defaultAdvisors(new SimpleLoggerAdvisor())
                // 设置 ChatClient 中 ChatModel 的 Options 参数
                .defaultOptions(OpenAiChatOptions.builder().topP(0.7).build())
                .build();

        this.openAiGenerateChatClient = ChatClient.builder(generateModel)
                // 实现 Logger 的 Advisor
                .defaultAdvisors(new SimpleLoggerAdvisor())
                // 设置 ChatClient 中 ChatModel 的 Options 参数
                .defaultOptions(OpenAiChatOptions.builder().topP(0.7).build())
                .build();

        this.openAiReviewerChatClient = ChatClient.builder(reviewerModel)
                // 实现 Logger 的 Advisor
                .defaultAdvisors(new SimpleLoggerAdvisor())
                // 设置 ChatClient 中 ChatModel 的 Options 参数
                .defaultOptions(OpenAiChatOptions.builder().topP(0.7).build())
                .build();
        //暂时与用例生成大模型相同
        this.openAiFormatChatClient = ChatClient.builder(formatModel)
                // 实现 Logger 的 Advisor
               .defaultAdvisors(new SimpleLoggerAdvisor())
                // 设置 ChatClient 中 ChatModel 的 Options 参数
               .defaultOptions(OpenAiChatOptions.builder().topP(0.7).build())
               .build();
        this.openAiVisualChatClient =  ChatClient.builder(visualModel)
                // 实现 Logger 的 Advisor
                .defaultAdvisors(new SimpleLoggerAdvisor())
                // 设置 ChatClient 中 ChatModel 的 Options 参数
                .defaultOptions(OpenAiChatOptions.builder().topP(0.7).build())
                .build();
    }
    /**
     * 读取上传文件的内容并返回
     * @param files 上传的文件
     * @return ApiResult 包含文件内容的数据对象
     */
    @PostMapping("/file/upload")
    public ApiResult<String> uploadFile(@RequestParam("files") MultipartFile[] files) {
        ApiResult<String> result = new ApiResult<>();
        try {
            if (files == null || files.length == 0) {
                result.setMessage("上传文件为空");
                result.setCode(400);
                return result;
            }

            StringBuilder contentBuilder = new StringBuilder();
            for (MultipartFile file : files) {
                if (file.isEmpty()) {
                    continue;
                }
                String fileName = file.getOriginalFilename();
                if (fileName == null) {
                    continue;
                }

                if (fileName.endsWith(".docx")||fileName.endsWith(".pdf")) {
                    // 处理docx文件
                    List<Media> mediaList = null;
                    if (fileName.endsWith(".docx")) {
                        XWPFDocument docxDoc = new XWPFDocument(file.getInputStream());
                        ByteArrayOutputStream pdfOutputStream = new ByteArrayOutputStream();
                        // 使用POI将docx转换为PDF
                        PdfOptions options = PdfOptions.create();
                        PdfConverter.getInstance().convert(docxDoc, pdfOutputStream, options);

                        byte[] pdfBytes = pdfOutputStream.toByteArray();

                        mediaList = convertPdfToImages(pdfBytes);

                    }
                    // 处理pdf文件
                    else if (fileName.endsWith(".pdf")) {
                        mediaList = convertPdfToImages(file.getBytes());
                    }
                    if (mediaList != null && !mediaList.isEmpty()) {

                        UserMessage message =
                                UserMessage.builder().text(Consts.VISUAL_PROMPT).media(mediaList).metadata(new HashMap<>()).build();
                        message.getMetadata().put(MESSAGE_FORMAT, MessageFormat.IMAGE);
                        String content = openAiVisualChatClient.prompt(new Prompt(message)).call().content();
                        contentBuilder.append(content).append("\n");
                    }
                }
                else {
                    if (!file.isEmpty()) {
                        contentBuilder.append(file.getOriginalFilename()).append(":\n");
                        try {
                            contentBuilder.append(new String(file.getBytes(), StandardCharsets.UTF_8)).append("\n");
                        } catch (IOException e) {
                            result.setMessage("文件读取失败: " + e.getMessage());
                            result.setCode(500);
                            return result;
                        }
                    }

                }
            }
            String content = contentBuilder.toString();

            String resp = openAiAnalyzeChatClient
                    .prompt(ANALYZE_PROMPT)
                    .user(content)
                    .call()
                    .content();

            result.setData(resp);
//            result.setData(content);
//            Thread.sleep(1000);
            result.setMessage("解析完成");
            result.setCode(200);

        } catch (Exception e) {
            result.setMessage("文件处理异常: " + e.getMessage());
            result.setCode(500);
            return result;
        }
        return result;
    }

    private List<Media> convertPdfToImages(byte[] file) throws IOException {
        List<Media> mediaList = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(file)) {
            PDFRenderer renderer = new PDFRenderer(document);
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                BufferedImage image = renderer.renderImageWithDPI(page, 300);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "jpg", baos);
                byte[] imageBytes = baos.toByteArray();
                ByteArrayResource resource = new ByteArrayResource(imageBytes);
                mediaList.add(new Media( MimeTypeUtils.IMAGE_JPEG, resource));
//                mediaList.add(Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG).data(resource).name(UUID.randomUUID().toString()).build());

               /* // 创建保存目录（如果不存在）
                File saveDir = new File("D:\\temp_images");
                if (!saveDir.exists()) {
                    saveDir.mkdirs();
                }

                // 生成文件名：年月日时分秒+序号.png
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
                String timestamp = sdf.format(new Date());
                String fileName = timestamp + "_" + (page + 1) + ".jpg";
                File outputFile = new File(saveDir, fileName);

                // 保存图片到文件
                try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                    fos.write(imageBytes);
                }*/
               
            }
        }
        return mediaList;
    }

    @PostMapping(path = "/stream/file/upload", produces = "text/event-stream")
    public Flux<String> getDefaultPrompt(@RequestParam("files") MultipartFile[] files) {
        try {
            if (files == null || files.length == 0) {
                return Flux.error(new RuntimeException("上传文件为空"));
            }
            
            StringBuilder contentBuilder = new StringBuilder();
            for (MultipartFile file : files) {
                if (!file.isEmpty()) {
                    contentBuilder.append(file.getOriginalFilename()).append(":\n");
                    contentBuilder.append(new String(file.getBytes(), StandardCharsets.UTF_8)).append("\n");
                }
            }
            
            return openAiAnalyzeChatClient
                    .prompt(ANALYZE_PROMPT)
                    .user(contentBuilder.toString())
                    .stream()
                    .content();

        } catch (IOException e) {
            return Flux.error(e);
        }
    }

    @PostMapping(path = "/case/create", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> createCase(@RequestBody ChatDto chatDto) throws GraphStateException {
        OverAllStateFactory stateFactory = () -> {
            OverAllState state = new OverAllState();
            state.registerKeyAndStrategy(Consts.ORIGIN_MESSAGE, new ReplaceStrategy());
            state.registerKeyAndStrategy(Consts.CASE_INFO_MESSAGE, new ReplaceStrategy());
            state.registerKeyAndStrategy(Consts.CASE_REVIEW_MESSAGE, new ReplaceStrategy());
            state.registerKeyAndStrategy(Consts.CASE_FORMAT_MESSAGE, new ReplaceStrategy());
            return state;
        };
        StateGraph graph = new StateGraph(stateFactory)
                .addNode("generate",node_async(new CaseGenerateNode(openAiGenerateChatClient)))
                .addNode("review",node_async(new CaseReviewerNode(openAiReviewerChatClient)))
                .addNode("format",node_async(new CaseFormatNode(openAiFormatChatClient)))
                .addEdge(START, "generate")
                .addEdge("generate", "review")
                .addEdge("format", END)
                .addConditionalEdges("review",edge_async(new FeedbackDispatcher()), Map.of("positive", "format", "negative", "generate"));

        CompiledGraph compile = graph.compile();

        String caseInfo = chatDto.getContent();

        /*流返回参考官方Demo实现：
        * spring-ai-alibaba-graph-example/multiagent-openmanus/src/main/java/com/alibaba/cloud/ai/example/graph/stream/LLmSearchStreamController.java*/
        String threadId = UUID.randomUUID().toString();
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().unicast().onBackpressureBuffer();

        Map<String, Object> map = Map.of(Consts.ORIGIN_MESSAGE, caseInfo);
        AsyncGenerator<NodeOutput> generator = compile.stream(map, RunnableConfig.builder().threadId(threadId).build());

        CompletableFuture.runAsync(() -> generator.forEachAsync(output -> {
            try {
                System.out.println("output = " + output);
                sink.tryEmitNext(ServerSentEvent.builder(JSON.toJSONString(output.state().data())).build());
            }
            catch (Exception e) {
                throw new CompletionException(e);
            }
        }).thenRun(sink::tryEmitComplete).exceptionally(ex -> {
            sink.tryEmitError(ex);
            return null;
        }));
        return sink.asFlux()
                .doOnCancel(() -> System.out.println("Client disconnected from stream"))
                .doOnError(e -> System.err.println("Error occurred during streaming: " + e));


    }

    @GetMapping(path = "/case/test", produces = "text/event-stream")
    public Flux<Map<String, Object>> caseTest() throws GraphStateException {

        OverAllStateFactory stateFactory = () -> {
            OverAllState state = new OverAllState();
            state.registerKeyAndStrategy(Consts.ORIGIN_MESSAGE, new ReplaceStrategy());
            state.registerKeyAndStrategy(Consts.CASE_INFO_MESSAGE, new ReplaceStrategy());
            state.registerKeyAndStrategy(Consts.CASE_REVIEW_MESSAGE, new ReplaceStrategy());
            state.registerKeyAndStrategy(Consts.CASE_FORMAT_MESSAGE, new ReplaceStrategy());
            return state;
        };
        StateGraph graph = new StateGraph(stateFactory)
                .addNode("generate",node_async(new CaseGenerateNode(openAiGenerateChatClient)))
                .addNode("review",node_async(new CaseReviewerNode(openAiReviewerChatClient)))
                .addNode("format",node_async(new CaseFormatNode(openAiFormatChatClient)))
                .addEdge(START, "generate")
                .addEdge("generate", "review")
                .addEdge("format", END)
                .addConditionalEdges("review",edge_async(new FeedbackDispatcher()), Map.of("positive", "format", "negative", "generate"));

        CompiledGraph compile = graph.compile();

        String caseInfo = """
                登录功能验证：
                1、用户名长度为8到20个字符，不能包含特殊字符
                2、密码长度为6到8个字符，必须包含大小写字母和数字
                3、系统用户名和密码正确，登录成功，
                4、系统用户名和密码错误，提示账户或密码错误，
                5、超过5次错误，锁定10分钟""";

        Map<String, Object> map = Map.of(Consts.ORIGIN_MESSAGE, caseInfo);

        RunnableConfig cfg = RunnableConfig.builder().streamMode(CompiledGraph.StreamMode.SNAPSHOTS).build();
        return Flux.create(sink -> compile.stream(map, cfg)
                .forEachAsync(node -> sink.next(node.state().data()))
                .whenComplete((v, e) -> {
                    if (e != null) {
                        sink.error(e);
                    }
                    else {
                        sink.complete();
                    }
                }));
    }

}
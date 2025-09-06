package com.fingertip.caseaibackend.controllers;

import com.alibaba.cloud.ai.dashscope.chat.MessageFormat;
import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.fingertip.caseaibackend.aiproxies.nodes.CaseFormatNode;
import com.fingertip.caseaibackend.aiproxies.nodes.CaseGenerateNode;
import com.fingertip.caseaibackend.aiproxies.nodes.CaseReviewerNode;
import com.fingertip.caseaibackend.aiproxies.nodes.FeedbackDispatcher;
import com.fingertip.caseaibackend.commons.Consts;
import com.fingertip.caseaibackend.enums.StorageType;
import com.fingertip.caseaibackend.service.ThirdPartCaseFactory;
import com.fingertip.caseaibackend.service.ThirdPartCaseService;
import com.fingertip.caseaibackend.vo.ApiResult;
import com.fingertip.caseaibackend.entity.CaseInfo;
import com.fingertip.caseaibackend.dtos.CaseSaveReq;
import com.fingertip.caseaibackend.dtos.ChatDto;
import com.fingertip.caseaibackend.dtos.CaseQueryRequest;
import com.fingertip.caseaibackend.service.CaseInfoService;
import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.bsc.async.AsyncGenerator;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
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
import java.text.SimpleDateFormat;
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

    private final ChatClient openAiAnalyzeChatClient;
    private final ChatClient openAiGenerateChatClient;
    private final ChatClient openAiReviewerChatClient;
    private final ChatClient openAiFormatChatClient;
    private final ChatClient openAiVisualChatClient;


    @Autowired
    private CaseInfoService caseInfoService;

    @Autowired
    private ThirdPartCaseFactory thirdPartCaseFactory;


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
        this.openAiVisualChatClient = ChatClient.builder(visualModel)
                // 实现 Logger 的 Advisor
                .defaultAdvisors(new SimpleLoggerAdvisor())
                // 设置 ChatClient 中 ChatModel 的 Options 参数
                .defaultOptions(OpenAiChatOptions.builder().topP(0.7).build())
                .build();
    }

    /**
     * 读取上传文件的内容并返回
     *
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

                if (fileName.endsWith(".docx") || fileName.endsWith(".pdf")) {
                    // 处理docx文件
                    byte[] pdfBytes = null;
                    if (fileName.endsWith(".docx")) {
                        XWPFDocument docxDoc = new XWPFDocument(file.getInputStream());
                        ByteArrayOutputStream pdfOutputStream = new ByteArrayOutputStream();
                        // 使用POI将docx转换为PDF
                        PdfOptions options = PdfOptions.create();
                        PdfConverter.getInstance().convert(docxDoc, pdfOutputStream, options);
                        pdfBytes = pdfOutputStream.toByteArray();
                    }
                    // 处理pdf文件
                    else if (fileName.endsWith(".pdf")) {
                        pdfBytes = file.getBytes();
                    }
                    // 创建临时保存目录（若不存在则自动创建）
                    File saveDir = new File("/tmp/temp_images");
                    if (!saveDir.exists()) {
                        saveDir.mkdirs();
                        System.out.println("临时图片目录已创建: " + saveDir.getAbsolutePath());
                    }
                    try (PDDocument pdfDoc = Loader.loadPDF(pdfBytes)) {
                        // 存储图片信息和AI描述的列表
                        List<ImageInfo> imageInfoList = new ArrayList<>();

                        // 自定义PDF文本提取器，在图片位置插入占位符
                        PDFTextStripperWithImagePlaceholders stripper = new PDFTextStripperWithImagePlaceholders(imageInfoList);
                        stripper.setSortByPosition(true);
                        String contentWithPlaceholders = stripper.getText(pdfDoc);

                        // 遍历PDF每一页，提取内嵌图片并分析
                        for (int pageNum = 0; pageNum < pdfDoc.getNumberOfPages(); pageNum++) {
                            PDPage page = pdfDoc.getPage(pageNum);
                            int currentPageNum = pageNum;

                            // 创建PDF内容流处理器，用于识别图片对象
                            PDFStreamEngine imageExtractor = new PDFStreamEngine() {
                                @Override
                                protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
                                    String operation = operator.getName();
                                    // 处理图片绘制操作符（Do指令）
                                    if ("Do".equals(operation)) {
                                        COSName xObjectName = (COSName) operands.get(0);
                                        PDXObject xObject = getResources().getXObject(xObjectName);

                                        // 验证是否为图片对象
                                        if (xObject instanceof PDImageXObject imageXObject) {
                                            BufferedImage bufferedImage = imageXObject.getImage();
                                            String suffix = imageXObject.getSuffix();
                                            String mimeType = switch (suffix.toLowerCase()) {
                                                case "png" -> "image/png";
                                                case "jpg", "jpeg" -> "image/jpeg";
                                                case "gif" -> "image/gif";
                                                case "bmp" -> "image/bmp";
                                                default -> "image/jpeg";
                                            };
                                            String formatName = suffix;

                                            // 获取图片字节数组
                                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                            ImageIO.write(bufferedImage, formatName, baos);
                                            byte[] imageBytes = baos.toByteArray();

                                            // 生成保存文件名
                                            String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
                                            String fileName = String.format("extracted_image_%s_page_%d_img_%d.%s",
                                                    timestamp, currentPageNum + 1, imageInfoList.size() + 1, formatName);
                                            File outputFile = new File(saveDir, fileName);

                                            // 保存提取的图片到临时文件
                                            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                                                fos.write(imageBytes);
                                            }
                                            System.out.printf("提取图片: 页码=%d, 格式=%s, 大小=%.2fKB, 保存路径=%s%n",
                                                    currentPageNum + 1, mimeType, (double) imageBytes.length / 1024, outputFile.getAbsolutePath());

                                            // 创建Media对象并调用AI分析图片内容
                                            ByteArrayResource imageResource = new ByteArrayResource(imageBytes);
                                            Media media = Media.builder()
                                                    .name(UUID.randomUUID().toString())
                                                    .mimeType(MimeTypeUtils.parseMimeType(mimeType))
                                                    .data(imageResource)
                                                    .build();

                                            // 调用AI分析图片内容
                                            UserMessage message = UserMessage.builder()
                                                    .text(Consts.VISUAL_PROMPT)
                                                    .media(Collections.singletonList(media))
                                                    .metadata(new HashMap<>())
                                                    .build();
                                            String imageDescription = openAiVisualChatClient.prompt(new Prompt(message)).call().content();

                                            // 记录图片信息和AI描述
                                            imageInfoList.add(new ImageInfo(currentPageNum, imageDescription));
                                        }
                                    } else {
                                        super.processOperator(operator, operands);
                                    }
                                }
                            };
                            imageExtractor.processPage(page);
                        }

                        // 替换文本中的图片占位符为AI描述
                        String finalContent = contentWithPlaceholders;
                        for (int i = 0; i < imageInfoList.size(); i++) {
                            String placeholder = String.format("{media%d}", i);
                            finalContent = finalContent.replace(placeholder, imageInfoList.get(i).getDescription());
                        }
                        //清空临时目录
                        FileUtils.cleanDirectory(saveDir);
                        contentBuilder.append(finalContent).append("\n");
                    }catch (Exception e){
                        result.setMessage("文件处理异常-单文件解析: " + e.getMessage());
                        result.setCode(500);
                        return result;
                    }

                } else {
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
                    .prompt(buildStructuredReviewPrompt(content))
                    .call()
                    .content();

            result.setData(resp);
            result.setMessage("解析完成");
            result.setCode(200);

        } catch (Exception e) {
            result.setMessage("文件处理异常: " + e.getMessage());
            result.setCode(500);
            return result;
        }
        return result;
    }

    private String buildStructuredReviewPrompt(String originMessage) {
        return String.format(Consts.ANALYZE_PROMPT, originMessage);
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
            state.registerKeyAndStrategy(Consts.OLD_TESTCASE_MESSAGE, new ReplaceStrategy());
            state.registerKeyAndStrategy(Consts.ORIGIN_MESSAGE, new ReplaceStrategy());
            state.registerKeyAndStrategy(Consts.CASE_INFO_MESSAGE, new ReplaceStrategy());
            state.registerKeyAndStrategy(Consts.CASE_REVIEW_MESSAGE, new ReplaceStrategy());
            state.registerKeyAndStrategy(Consts.RETRY_COUNT, new ReplaceStrategy());
            state.registerKeyAndStrategy(Consts.REVIEW_SCORE, new ReplaceStrategy());
            //state.registerKeyAndStrategy(Consts.CASE_FORMAT_MESSAGE, new ReplaceStrategy());
            return state;
        };
        StateGraph graph = new StateGraph(stateFactory)

                .addNode("generate", node_async(new CaseGenerateNode(openAiGenerateChatClient)))
                .addNode("review", node_async(new CaseReviewerNode(openAiReviewerChatClient)))
                //.addNode("format", node_async(new CaseFormatNode(openAiFormatChatClient)))
                .addEdge(START, "generate")
                .addEdge("generate", "review")
                //.addEdge("format", END)
                //.addConditionalEdges("review", edge_async(new FeedbackDispatcher()), Map.of("positive", "format", "negative", "generate"));
                .addConditionalEdges("review", edge_async(new FeedbackDispatcher()), Map.of("pass", END, "fail", "generate"));


        CompiledGraph compile = graph.compile();
        String originMessage = chatDto.getContent();
        Map<String, Object> map = new HashMap<>();
        map.put(Consts.ORIGIN_MESSAGE, originMessage);

        //判断是否有历史用例，如果有则用续写的prompt,否则使用非续写的prompt
        String caseId = chatDto.getCaseId();
        if (!StringUtils.isBlank(caseId)) {
            CaseInfo caseInfo1 = caseInfoService.getOne(new LambdaQueryWrapper<CaseInfo>().eq(CaseInfo::getId, caseId));
            if (caseInfo1 != null) {
                map.put(Consts.OLD_TESTCASE_MESSAGE, caseInfo1.getCaseContent());
            }
        }

        /*流返回参考官方Demo实现：
         * spring-ai-alibaba-graph-example/multiagent-openmanus/src/main/java/com/alibaba/cloud/ai/example/graph/stream/LLmSearchStreamController.java*/
        String threadId = UUID.randomUUID().toString();
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().unicast().onBackpressureBuffer();
        AsyncGenerator<NodeOutput> generator = compile.stream(map, RunnableConfig.builder().threadId(threadId).build());

        CompletableFuture.runAsync(() -> generator.forEachAsync(output -> {
            try {
                System.out.println("output = " + output);
                sink.tryEmitNext(ServerSentEvent.builder(JSON.toJSONString(output.state().data())).build());
            } catch (Exception e) {
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

    /**
     * 保存用例
     *
     * @param caseSaveReq
     * @return
     */
    @PostMapping("/case/save")
    public ApiResult<Boolean> saveCaseInfo(@RequestBody CaseSaveReq caseSaveReq) {
        ApiResult<Boolean> result = new ApiResult<>();
        if (StringUtils.isBlank(caseSaveReq.getCaseName())) {
            result.setCode(400001);
            result.setMessage("用例名称不可为空");
            return result;
        }
        if (StringUtils.isBlank(caseSaveReq.getCaseContent())) {
            result.setCode(400002);
            result.setMessage("用例内容不可为空");
            return result;
        }
        CaseInfo caseInfo = new CaseInfo();
        caseInfo.setName(caseSaveReq.getCaseName());
        caseInfo.setCaseContent(caseSaveReq.getCaseContent());

        //根据用例名称查询是否已存在，存在则update，不存在则insert
        CaseInfo caseInfo1 = caseInfoService.getOne(new LambdaQueryWrapper<CaseInfo>().eq(CaseInfo::getName, caseSaveReq.getCaseName()));
        Boolean dbResult = false;
        if (caseInfo1 != null) {
            caseInfo.setId(caseInfo1.getId());
            dbResult = caseInfoService.updateById(caseInfo);
        } else {
            dbResult = caseInfoService.save(caseInfo);
        }

        if (dbResult) {
            result.setCode(200);
            result.setMessage("用例保存成功");
            result.setData(dbResult);

            //第三方平台存储
            //根据thirdPartType的配置，生成对象

            try {
                thirdPartCaseFactory.exec(caseSaveReq.getCaseName(),caseSaveReq.getCaseContent());
                result.setCode(200);
                result.setMessage("第三方用例保存成功");
                result.setData(dbResult);
                return result;
            } catch (Exception e) {
                result.setCode(400004);
                result.setMessage("第三方平台用例保存失败");
                result.setData(dbResult);
                return result;
            }


        }
        result.setCode(400003);
        result.setMessage("用例保存失败");
        result.setData(dbResult);
        return result;
    }


    /**
     * 根据用例名称查询测试用例列表
     *
     * @param
     * @return 分页查询结果
     */
    @PostMapping("/case/list")
    public ApiResult<List<CaseInfo>> getCaseList(@RequestBody CaseQueryRequest queryRequest) {
        ApiResult<List<CaseInfo>> result = new ApiResult<>();
        try {
            // 获取分页参数，设置默认值
            int page = queryRequest.getPage();
            int pageSize = queryRequest.getPageSize();
            String keyword = queryRequest.getKeyword();

            // 创建分页对象
            Page<CaseInfo> pageInfo = new Page<>(page, pageSize);

            // 构建查询条件
            LambdaQueryWrapper<CaseInfo> queryWrapper = new LambdaQueryWrapper<>();
            if (StringUtils.isNotBlank(keyword)) {
                queryWrapper.like(CaseInfo::getName, keyword);
            }
            // 按创建时间倒序查询
            queryWrapper.orderByDesc(CaseInfo::getId);

            // 执行分页查询
            Page<CaseInfo> casePage = caseInfoService.page(pageInfo, queryWrapper);

            // 设置返回结果
            result.setCode(200);
            result.setMessage("查询成功");
            result.setData(casePage.getRecords());
        } catch (Exception e) {
            result.setCode(500);
            result.setMessage("查询失败: " + e.getMessage());
            result.setData(null);
        }
        return result;
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
                .addNode("generate", node_async(new CaseGenerateNode(openAiGenerateChatClient)))
                .addNode("review", node_async(new CaseReviewerNode(openAiReviewerChatClient)))
                .addNode("format", node_async(new CaseFormatNode(openAiFormatChatClient)))
                .addEdge(START, "generate")
                .addEdge("generate", "review")
                .addEdge("format", END)
                .addConditionalEdges("review", edge_async(new FeedbackDispatcher()), Map.of("positive", "format", "negative", "generate"));

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
                    } else {
                        sink.complete();
                    }
                }));
    }

    @PostMapping("/test")
    public ApiResult<String> getCaseList() throws Exception {
        Resource resource = new DefaultResourceLoader().getResource("classpath:data/0818【私教预计到手价&&聚划算迁移】.pdf");

        // 创建临时保存目录（若不存在则自动创建）
        File saveDir = new File("/tmp/temp_images");
        if (!saveDir.exists()) {
            saveDir.mkdirs();
            System.out.println("临时图片目录已创建: " + saveDir.getAbsolutePath());
        }

        try (InputStream inputStream = resource.getInputStream()) {
            byte[] pdfBytes = inputStream.readAllBytes();
            try (PDDocument pdfDoc = Loader.loadPDF(pdfBytes)) {
                // 存储图片信息和AI描述的列表
                List<ImageInfo> imageInfoList = new ArrayList<>();

                // 自定义PDF文本提取器，在图片位置插入占位符
                PDFTextStripperWithImagePlaceholders stripper = new PDFTextStripperWithImagePlaceholders(imageInfoList);
                stripper.setSortByPosition(true);
                String contentWithPlaceholders = stripper.getText(pdfDoc);

                // 遍历PDF每一页，提取内嵌图片并分析
                for (int pageNum = 0; pageNum < pdfDoc.getNumberOfPages(); pageNum++) {
                    PDPage page = pdfDoc.getPage(pageNum);
                    int currentPageNum = pageNum;

                    // 创建PDF内容流处理器，用于识别图片对象
                    PDFStreamEngine imageExtractor = new PDFStreamEngine() {
                        @Override
                        protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
                            String operation = operator.getName();
                            // 处理图片绘制操作符（Do指令）
                            if ("Do".equals(operation)) {
                                COSName xObjectName = (COSName) operands.get(0);
                                PDXObject xObject = getResources().getXObject(xObjectName);

                                // 验证是否为图片对象
                                if (xObject instanceof PDImageXObject imageXObject) {
                                    BufferedImage bufferedImage = imageXObject.getImage();
                                    String suffix = imageXObject.getSuffix();
                                    String mimeType = switch (suffix.toLowerCase()) {
                                        case "png" -> "image/png";
                                        case "jpg", "jpeg" -> "image/jpeg";
                                        case "gif" -> "image/gif";
                                        case "bmp" -> "image/bmp";
                                        default -> "image/jpeg";
                                    };
                                    String formatName = suffix;

                                    // 获取图片字节数组
                                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                    ImageIO.write(bufferedImage, formatName, baos);
                                    byte[] imageBytes = baos.toByteArray();

                                    // 生成保存文件名
                                    String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
                                    String fileName = String.format("extracted_image_%s_page_%d_img_%d.%s",
                                            timestamp, currentPageNum + 1, imageInfoList.size() + 1, formatName);
                                    File outputFile = new File(saveDir, fileName);

                                    // 保存提取的图片到临时文件
                                    try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                                        fos.write(imageBytes);
                                    }
                                    System.out.printf("提取图片: 页码=%d, 格式=%s, 大小=%.2fKB, 保存路径=%s%n",
                                            currentPageNum + 1, mimeType, (double) imageBytes.length / 1024, outputFile.getAbsolutePath());

                                    // 创建Media对象并调用AI分析图片内容
                                    ByteArrayResource imageResource = new ByteArrayResource(imageBytes);
                                    Media media = Media.builder()
                                            .name(UUID.randomUUID().toString())
                                            .mimeType(MimeTypeUtils.parseMimeType(mimeType))
                                            .data(imageResource)
                                            .build();

                                    // 调用AI分析图片内容
                                    UserMessage message = UserMessage.builder()
                                            .text(Consts.VISUAL_PROMPT)
                                            .media(Collections.singletonList(media))
                                            .metadata(new HashMap<>())
                                            .build();
                                    String imageDescription = openAiVisualChatClient.prompt(new Prompt(message)).call().content();

                                    // 记录图片信息和AI描述
                                    imageInfoList.add(new ImageInfo(currentPageNum, imageDescription));
                                }
                            } else {
                                super.processOperator(operator, operands);
                            }
                        }
                    };
                    imageExtractor.processPage(page);
                }

                // 替换文本中的图片占位符为AI描述
                String finalContent = contentWithPlaceholders;
                for (int i = 0; i < imageInfoList.size(); i++) {
                    String placeholder = String.format("{media%d}", i);
                    finalContent = finalContent.replace(placeholder, imageInfoList.get(i).getDescription());
                }

                // 返回处理后的完整内容
                ApiResult<String> result = new ApiResult<>();
                result.setCode(200);
                result.setMessage("文档处理成功");
                result.setData(finalContent);
                return result;
            }
        }
    }


    // 内部辅助类：存储图片信息和AI描述
    private static class ImageInfo {
        private final int pageNum;
        private final String description;

        public ImageInfo(int pageNum, String description) {
            this.pageNum = pageNum;
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    // 自定义PDF文本提取器：在图片位置插入占位符
    private static class PDFTextStripperWithImagePlaceholders extends org.apache.pdfbox.text.PDFTextStripper {
        private final List<ImageInfo> imageInfoList;
        private int imageCounter = 0;

        public PDFTextStripperWithImagePlaceholders(List<ImageInfo> imageInfoList) throws IOException {
            this.imageInfoList = imageInfoList;
        }

        @Override
        protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
            String operation = operator.getName();
            if ("Do".equals(operation)) {
                COSName xObjectName = (COSName) operands.get(0);
                PDXObject xObject = getCurrentPage().getResources().getXObject(xObjectName);

                // 遇到图片时插入占位符
                if (xObject instanceof PDImageXObject) {
                    writeString(String.format("{media%d}", imageCounter++));
                }
            }
            super.processOperator(operator, operands);
        }
    }

}
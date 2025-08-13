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
import com.fingertip.caseaibackend.vo.ApiResult;
import com.fingertip.caseaibackend.entity.CaseInfo;
import com.fingertip.caseaibackend.dtos.CaseSaveReq;
import com.fingertip.caseaibackend.dtos.ChatDto;
import com.fingertip.caseaibackend.dtos.CaseQueryRequest;
import com.fingertip.caseaibackend.service.CaseInfoService;
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
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import org.springframework.ai.content.Media;

import java.io.*;
import java.net.URLEncoder;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.alibaba.cloud.ai.dashscope.common.DashScopeApiConstants.MESSAGE_FORMAT;
import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;
import static com.fingertip.caseaibackend.commons.Consts.ANALYZE_PROMPT;
import static com.fingertip.caseaibackend.entity.KityMinderNode.*;


@RestController
@RequestMapping("/ai-api")
public class AiChatController {

    private static final String DEFAULT_PROMPT = "你好，介绍下你自己！";

    private final ChatClient openAiAnalyzeChatClient;
    private final ChatClient openAiGenerateChatClient;
    private final ChatClient openAiReviewerChatClient;
    private final ChatClient openAiFormatChatClient;
    private final ChatClient openAiVisualChatClient;

    @Autowired
    private CaseInfoService caseInfoService;

    @Autowired
    private RestTemplate restTemplate;


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
                        //读取pdf文件内容
                        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
                            // 使用 PDFTextStripper 提取文本内容
                            org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
                            String pdfText = stripper.getText(document);
                            contentBuilder.append(pdfText).append("\n");
                        } catch (IOException e) {
                            result.setMessage("PDF 文件读取失败: " + e.getMessage());
                            result.setCode(500);
                            return result;
                        }
                        mediaList = convertPdfToImages(file.getBytes());
                    }
                    if (mediaList != null && !mediaList.isEmpty()) {

                        UserMessage message =
                                UserMessage.builder().text(Consts.VISUAL_PROMPT).media(mediaList).metadata(new HashMap<>()).build();
                        message.getMetadata().put(MESSAGE_FORMAT, MessageFormat.IMAGE);
                        String content = openAiVisualChatClient.prompt(new Prompt(message)).call().content();
                        contentBuilder.append(content).append("\n");
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
                mediaList.add(new Media(MimeTypeUtils.IMAGE_JPEG, resource));
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
            state.registerKeyAndStrategy(Consts.OLD_TESTCASE_MESSAGE, new ReplaceStrategy());
            state.registerKeyAndStrategy(Consts.ORIGIN_MESSAGE, new ReplaceStrategy());
            state.registerKeyAndStrategy(Consts.CASE_INFO_MESSAGE, new ReplaceStrategy());
            state.registerKeyAndStrategy(Consts.CASE_REVIEW_MESSAGE, new ReplaceStrategy());
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
                .addConditionalEdges("review", edge_async(new FeedbackDispatcher()), Map.of("positive", END, "negative", "generate"));


        CompiledGraph compile = graph.compile();
        String originMessage = chatDto.getContent();
        Map<String, Object> map = new HashMap<>();
        map.put(Consts.ORIGIN_MESSAGE, originMessage);

        //判断是否有历史用例，如果有则用续写的prompt,否则使用非续写的prompt
        String caseName = chatDto.getCaseId();
        if (!StringUtils.isBlank(caseName)) {
            CaseInfo caseInfo1 = caseInfoService.getOne(new LambdaQueryWrapper<CaseInfo>().eq(CaseInfo::getId, caseName));
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
            //转换为KityMinder格式的数据并保存到agileTC
            String kmData = convertMarkdownToKityMinder(caseSaveReq.getCaseContent());
            //String kmData = convertToKityMinderFormat(caseSaveReq.getCaseContent());
            //调用agileTC的用例创建接口
            ApiResult<Boolean> apiResult = saveToAgileTc(kmData, caseSaveReq.getCaseName(), result);
            if (apiResult.getCode() == 200) {
                result.setCode(apiResult.getCode());
                result.setMessage(apiResult.getMessage());
                return result;
            }
        }
        result.setCode(400003);
        result.setMessage("用例保存失败");
        result.setData(dbResult);
        return result;
    }

    private String convertMarkdownToKityMinder(String caseContent) {
        return null;
    }

    private ApiResult<Boolean> saveToAgileTc(String kmData, String caseName, ApiResult<Boolean> result) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            // 1. 调用GET接口检查用例是否存在
            String encodedCaseName = URLEncoder.encode(caseName, StandardCharsets.UTF_8.name());
            String getUrl = String.format(
                    "https://agile.leoao-inc.com/api/case/list?pageSize=10&pageNum=1&productLineId=1&caseType=0&title=%s&creator=&channel=1&requirementId=&bizId=root",
                    encodedCaseName  // 使用编码后的名称进行替换
            );
            ResponseEntity<Map> getResponse = restTemplate.getForEntity(getUrl, Map.class);

            if (getResponse.getStatusCode().is2xxSuccessful() && getResponse.getBody() != null) {
                Map<String, Object> responseBody = getResponse.getBody();
                List<Map<String, Object>> dataSources = (List<Map<String, Object>>) responseBody.get("dataSources");

                if (dataSources == null || dataSources.isEmpty()) {
                    // 2. 用例不存在，调用创建接口
                    Map<String, Object> createBody = new HashMap<>();
                    createBody.put("productLineId", 1);
                    createBody.put("creator", "admin");
                    createBody.put("caseType", 0);
                    // 正则表达式匹配 ```json 和 ``` 之间的内容（包含换行符）
//                    Pattern pattern = Pattern.compile(
//                            "([`']){3}\\s*json\\s*([\\s\\S]*?)\\s*\\1",  // 捕获组2：JSON内容
//                            Pattern.CASE_INSENSITIVE
//                    );
//                    Matcher matcher = pattern.matcher(kmData);
                    String cleanedJson = kmData;  // 初始化为原始数据，保留所有字符

//                    if (matcher.find()) {
//                        // 提取JSON内容并去除首尾空白（保留转义字符）
//                        cleanedJson = matcher.group(2).trim();
//                    } else {
//                        // 未找到JSON代码块，记录警告日志
//                        System.out.println("警告：未从kmData中提取到JSON代码块，使用原始内容");
//                    }
                    createBody.put("caseContent", cleanedJson);
                    createBody.put("title", caseName);  // 使用实际用例名称作为标题
                    createBody.put("channel", 1);
                    createBody.put("bizId", "-1");
                    createBody.put("id", "");
                    createBody.put("description", "");

                    HttpEntity<Map<String, Object>> createRequest = new HttpEntity<>(createBody, headers);
                    ResponseEntity<String> createResponse = restTemplate.postForEntity(
                            "https://agile.leoao-inc.com/api/case/create",
                            createRequest,
                            String.class
                    );
                    if (createResponse.getStatusCode().is2xxSuccessful()) {
                        result.setMessage("用例保存成功并同步创建到agileTC");
                    } else {
                        result.setMessage("用例保存成功，但agileTC创建失败: " + createResponse.getStatusCode());
                    }
                } else {
                    // 3. 用例已存在，调用更新接口
                    Map<String, Object> existingCase = dataSources.get(0);  // 获取第一个匹配用例
                    String caseId = (String) existingCase.get("id");  // 获取现有用例ID

                    Map<String, Object> updateBody = new HashMap<>();
                    updateBody.put("id", caseId);
                    updateBody.put("title", "更新内容，实际不会保存title");  // 按需求固定标题
                    updateBody.put("modifier", "admin");
                    updateBody.put("caseContent", kmData);  // 更新用例内容

                    HttpEntity<Map<String, Object>> updateRequest = new HttpEntity<>(updateBody, headers);
                    ResponseEntity<String> updateResponse = restTemplate.postForEntity(
                            "https://agile.leoao-inc.com/api/case/update",
                            updateRequest,
                            String.class
                    );
                    if (updateResponse.getStatusCode().is2xxSuccessful()) {
                        result.setMessage("用例保存成功并同步更新到agileTC");
                    } else {
                        result.setMessage("用例保存成功，但agileTC更新失败: " + updateResponse.getStatusCode());
                    }
                }
            } else {
                result.setMessage("用例保存成功，但agileTC查询接口调用失败");
            }
        } catch (Exception e) {
            result.setMessage("用例保存成功，但同步agileTC时发生错误: " + e.getMessage());
        }
        return result;

    }

    public static void main(String[] args) {
        String markdown = "# 管理后台API接口_后台下拉选查询聚划算活动测试用例\n" +
                "\n" +
                "## [管理后台API接口_后台下拉选查询聚划算活动_001] 模糊匹配查询存在活动名称的聚划算活动\n" +
                "- **测试目标**: 验证根据活动名称模糊匹配查询聚划算活动的正确性\n" +
                "- **前置条件**: 系统中存在多个聚划算活动数据，包含\"聚划算\"关键词的活动名称\n" +
                "- **优先级**: P0\n" +
                "- **预期结果**: 返回包含匹配活动名称的活动列表，且每个活动名称字段为\"活动名称+活动ID\"格式\n" +
                "\n" +
                "## [管理后台API接口_后台下拉选查询聚划算活动_002] 模糊匹配查询不存在活动名称的聚划算活动\n" +
                "- **测试目标**: 验证当活动名称不存在时的查询处理\n" +
                "- **前置条件**: 系统中不存在包含\"xyz123\"关键词的聚划算活动\n" +
                "- **优先级**: P1\n" +
                "- **预期结果**: 返回空列表或提示\"未找到匹配活动\"\n" +
                "\n" +
                "## [管理后台API接口_后台下拉选查询聚划算活动_003] 模糊匹配查询包含大小写混合的活动名称\n" +
                "- **测试目标**: 验证大小写不敏感的模糊匹配逻辑\n" +
                "- **前置条件**: 系统中存在活动名称为\"JUHUA_SUAN\"的聚划算活动\n" +
                "- **优先级**: P0\n" +
                "- **预期结果**: 输入\"juhua\"或\"JUHUA\"时返回该活动，名称字段为\"JUHUA_SUAN+活动ID\"\n" +
                "\n" +
                "## [管理后台API接口_后台下拉选查询聚划算活动_004] 模糊匹配查询包含特殊字符的活动名称\n" +
                "- **测试目标**: 验证特殊字符的模糊匹配处理\n" +
                "- **前置条件**: 系统中存在活动名称为\"聚划算-活动\"的聚划算活动\n" +
                "- **优先级**: P1\n" +
                "- **预期结果**: 输入\"聚划算\"或\"聚划算-\"时返回该活动，名称字段为\"聚划算-活动+活动ID\"\n" +
                "\n" +
                "## [管理后台API接口_后台下拉选查询聚划算活动_005] 空字符串查询所有聚划算活动\n" +
                "- **测试目标**: 验证空字符串参数的处理逻辑\n" +
                "- **前置条件**: 系统中存在至少3个聚划算活动\n" +
                "- **优先级**: P0\n" +
                "- **预期结果**: 返回所有聚划算活动列表，名称字段为\"活动名称+活动ID\"格式\n" +
                "\n" +
                "## [管理后台API接口_后台下拉选查询聚划算活动_006] 超长活动名称模糊匹配\n" +
                "- **测试目标**: 验证超长名称参数的边界处理\n" +
                "- **前置条件**: 系统中存在活动名称长度为255字符的聚划算活动\n" +
                "- **优先级**: P2\n" +
                "- **预期结果**: 输入完整名称时返回该活动，输入部分超长字符时返回匹配结果\n" +
                "\n" +
                "## [管理后台API接口_后台下拉选查询聚划算活动_007] 活动ID反查名称验证\n" +
                "- **测试目标**: 验证根据活动ID反查活动名称的正确性\n" +
                "- **前置条件**: 系统中存在活动ID为\"1001\"的聚划算活动\n" +
                "- **优先级**: P0\n" +
                "- **预期结果**: 当传入活动ID\"1001\"时，返回的名称字段为\"活动名称+1001\"格式\n" +
                "\n" +
                "## [管理后台API接口_后台下拉选查询聚划算活动_008] 无效活动ID反查处理\n" +
                "- **测试目标**: 验证无效活动ID的反查逻辑\n" +
                "- **前置条件**: 系统中不存在活动ID为\"9999\"的聚划算活动\n" +
                "- **优先级**: P1\n" +
                "- **预期结果**: 返回空列表或提示\"未找到对应活动\"\n" +
                "\n" +
                "## [管理后台API接口_后台下拉选查询聚划算活动_009] 参数缺失时的默认行为\n" +
                "- **测试目标**: 验证name参数缺失时的处理\n" +
                "- **前置条件**: 调用接口时未传name参数\n" +
                "- **优先级**: P2\n" +
                "- **预期结果**: 返回系统默认的活动列表（如全部活动或错误提示）\n" +
                "\n" +
                "## [管理后台API接口_后台下拉选查询聚划算活动_010] 参数类型错误处理\n" +
                "- **测试目标**: 验证name参数类型错误时的容错能力\n" +
                "- **前置条件**: 传入name参数为数字类型（如12345）\n" +
                "- **优先级**: P1\n" +
                "- **预期结果**: 返回错误提示\"参数类型错误\"或空列表\n" +
                "\n" +
                "## [管理后台API接口_后台下拉选查询聚划算活动_011] 活动名称完全匹配\n" +
                "- **测试目标**: 验证精确匹配场景\n" +
                "- **前置条件**: 系统中存在活动名称为\"聚划算秋季促销\"的活动\n" +
                "- **优先级**: P0\n" +
                "- **预期结果**: 输入\"聚划算秋季促销\"时返回该活动，名称字段为\"聚划算秋季促销+活动ID\"\n" +
                "\n" +
                "## [管理后台API接口_后台下拉选查询聚划算活动_012] 活动名称部分匹配\n" +
                "- **测试目标**: 验证部分匹配场景\n" +
                "- **前置条件**: 系统中存在活动名称为\"聚划算秋季大促\"的活动\n" +
                "- **优先级**: P0\n" +
                "- **预期结果**: 输入\"秋季\"时返回该活动，名称字段为\"聚划算秋季大促+活动ID\"\n" +
                "\n" +
                "## [管理后台API接口_后台下拉选查询聚划算活动_013] 多个匹配结果返回\n" +
                "- **测试目标**: 验证多结果返回的正确性\n" +
                "- **前置条件**: 系统中存在3个包含\"聚划算\"关键词的活动\n" +
                "- **优先级**: P0\n" +
                "- **预期结果**: 返回3个活动列表，每个名称字段为\"活动名称+活动ID\"格式\n" +
                "\n" +
                "## [管理后台API接口_后台下拉选查询聚划算活动_014] 活动名称中英文混合匹配\n" +
                "- **测试目标**: 验证中英文混合名称的模糊匹配\n" +
                "- **前置条件**: 系统中存在活动名称为\"JuHuaSuan 2023\"的聚划算活动\n" +
                "- **优先级**: P1\n" +
                "- **预期结果**: 输入\"JuHua\"或\"2023\"时返回该活动，名称字段为\"JuHuaSuan 2023+活动ID\"\n" +
                "\n" +
                "## [管理后台API接口_后台下拉选查询聚划算活动_015] 活动名称空格处理\n" +
                "- **测试目标**: 验证名称中空格的模糊匹配\n" +
                "- **前置条件**: 系统中存在活动名称为\"聚划算 活动\"的聚划算活动\n" +
                "- **优先级**: P1\n" +
                "- **预期结果**: 输入\"聚划算活动\"或\"聚划算 活动\"时返回该活动，名称字段为\"聚划算 活动+活动ID\"";
        //KityMinderData kityMinderData = convertMarkdownToKityMinder(markdown);
        //System.out.println(toJson(kityMinderData));
    }



    // 获取标题级别（#的数量）
    private static int getHeadingLevel(String line) {
        int level = 0;
        while (level < line.length() && line.charAt(level) == '#') {
            level++;
        }
        // 确保后面有空格
        if (level < line.length() && line.charAt(level) != ' ') {
            throw new IllegalArgumentException("Invalid heading format: " + line);
        }
        return level;
    }


    /**
     * 转换为KityMinder格式
     *
     * @param caseContent
     * @return
     */
    private String convertToKityMinderFormat(String caseContent) {
        String content = Consts.CASE_FORMAT_PROMPT + "\n\n" + caseContent;
        ChatResponse response = openAiFormatChatClient.prompt(content).call().chatResponse();
        String output = null;
        if (response != null) {
            output = response.getResult().getOutput().getText();
        }
        return output;
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
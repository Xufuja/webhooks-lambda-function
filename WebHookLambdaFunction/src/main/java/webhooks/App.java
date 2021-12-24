package webhooks;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.*;

/**
 * Handler for requests to Lambda function.
 */
public class App implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private LambdaLogger logger;
    private int returnCode;
    private String returnMessage;
    private final String path = "/tmp/messages.txt";
    private final int sizeLimit = 100000;
    private final Boolean sendHeaders = true;

    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
        this.logger = context.getLogger();
        logger.log("Webhook endpoint was hit");

        whMessageStoreVerifier(this.path);

        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();//.withHeaders(headers);

        try {
            logger.log("Creating response");
            createResponseValues(input);
            logger.log("Sending following response: " + this.returnCode + " " + this.returnMessage);
            return returnMessage.isEmpty() ? response.withStatusCode(returnCode) : response.withStatusCode(returnCode).withBody(returnMessage);

        } catch (Exception e) {
            logger.log(e.toString());
            return response.withStatusCode(500);
        }
    }
    public void createResponseValues(APIGatewayProxyRequestEvent input) throws IOException {
        String httpMethod = input.getHttpMethod();
        logger.log("Entering try catch block with method: " + httpMethod);
        switch (httpMethod) {
            case "GET": {
                if (input.getHeaders().containsKey("X-GCS-Webhooks-Endpoint-Verification")) {
                    logger.log("Received request with following header:\\r\\n X-GCS-Webhooks-Endpoint-Verification: " + input.getHeaders().get("X-GCS-Webhooks-Endpoint-Verification"));
                    setResponse(200, input.getHeaders().get("X-GCS-Webhooks-Endpoint-Verification"));
                } else {
                    logger.log(httpMethod + " received requesting logs");
                    setResponse(200, Files.readString(Paths.get(this.path), StandardCharsets.UTF_8));
                }
                break;
            }
            case "POST": {
                if (!input.getBody().isEmpty()) {
                    logger.log("Received message");
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();
                    JsonObject jsonObject = JsonParser.parseString(input.getBody()).getAsJsonObject();
                    String output = gson.toJson(jsonObject);
                    logger.log(output);

                    logger.log("Logging headers: " + this.sendHeaders);
                    String headerToPrint = "";
                    if (this.sendHeaders) {
                        for (var header : input.getHeaders().entrySet()) {
                            if (header.getKey().startsWith("X-GCS")) {
                                headerToPrint += header.getKey() + " " + header.getValue() + System.lineSeparator();
                            }
                        }
                        logger.log(headerToPrint);
                    }

                    PrintWriter printWriter = new PrintWriter(new FileOutputStream(new File(this.path), true));
                    logger.log("Writing to: " + this.path);
                    if (!headerToPrint.isEmpty()) {
                        printWriter.append(headerToPrint + System.lineSeparator());
                    }
                    printWriter.append(output + System.lineSeparator() + System.lineSeparator());
                    printWriter.close();
                    logger.log("Wrote messages to file");
                    setResponse(200, "");
                } else {
                    logger.log(httpMethod + " received without Webhooks message");
                    setResponse(400, "");
                }
                break;
            }
            default: {
                logger.log("Invalid method received");
                this.returnCode = 405;
                break;
            }
        }
    }
    public void setResponse(int returnCode, String returnMessage) {
        this.returnCode = returnCode;
        this.returnMessage = returnMessage;
    }
    public void whMessageStoreVerifier(String path) {
        File messages = new File(path);
        logger.log("Check if the file is bigger than limit in Bytes set to: " + this.sizeLimit);
        if (messages.exists()) {
            long size = messages.length();
            logger.log("Retrieved size in Bytes: " + size);
            if (size > this.sizeLimit) {
                messages.delete();
                logger.log("The messages.txt file has been deleted due to size constraints");
            }
        } else {
            logger.log("The messages.txt file does not exist");
        }

        try {
            logger.log("Create messages.txt if necessary");
            messages.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

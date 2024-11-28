package dev.otavio.UrlShorter;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Main implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final S3Client s3Client = S3Client.builder().build();

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        if (input.get("body") == null) {
            return buildErrorResponse(400, "Request body is missing");
        }

        String body = input.get("body").toString();

        Map<String, String> bodyMap;
        try {
            bodyMap = objectMapper.readValue(body, Map.class);
        } catch (JsonProcessingException e) {
                throw new RuntimeException("Unable to parse JSON body" , e);
        }

        String originalUrl = bodyMap.get("originalUrl");
        String expirationTime = bodyMap.get("expirationTime");

        if (originalUrl == null ||originalUrl.isBlank()) {
            return buildErrorResponse(400, "'Original URL' is required");
        }

        if (expirationTime == null ||expirationTime.isBlank()) {
            return buildErrorResponse(400, "'Expiration time' is required");
        }

        long expirationTimeInSeconds;
        try {
            expirationTimeInSeconds = Long.parseLong(expirationTime);
        } catch (NumberFormatException e) {
            return buildErrorResponse(400, "Field 'expirationTime' must be a valid number");
        }

        String shortUrlCode = UUID.randomUUID().toString().substring(0, 5);

        UrlData urlData = new UrlData(originalUrl, expirationTimeInSeconds);
        try {
            String urlJson = objectMapper.writeValueAsString(urlData);
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket("YOUR-S3-BUCKET-NAME")
                    .key(shortUrlCode + ".json")
                    .build();

            s3Client.putObject(request, RequestBody.fromString(urlJson));
        } catch (Exception e) {
            throw new RuntimeException("Error saving URL data to S3" + e.getMessage(), e);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("code", shortUrlCode);


        //CORS CONFIGURATION
        Map<String, String> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Headers", "Content-Type,X-Amz-Date,Authorization,X-Api-Key,X-Amz-Security-Token");
        headers.put("Access-Control-Allow-Methods", "OPTIONS,POST");
        response.put("headers", headers);

        return response;
    }

    //GENERATE MORE COMPREHENSIVE ERROR MESSAGES

    private Map<String, Object> buildErrorResponse(int statusCode, String message) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("statusCode", statusCode);
        errorResponse.put("body", message);

        Map<String, String> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Origin", "*");
        errorResponse.put("headers", headers);

        return errorResponse;

    }
}

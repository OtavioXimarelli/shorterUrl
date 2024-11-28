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

// Classe principal que implementa a interface RequestHandler para processar requisições na AWS Lambda
public class Main implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    // Objeto para manipulação de JSON (serialização e desserialização)
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Cliente S3 para interagir com o serviço AWS S3
    private final S3Client s3Client = S3Client.builder().build();

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        // Verifica se o corpo da requisição está presente
        if (input.get("body") == null) {
            return buildErrorResponse(400, "Request body is missing");
        }

        // Extrai o corpo da requisição como uma string
        String body = input.get("body").toString();

        Map<String, String> bodyMap;
        try {
            // Desserializa o JSON recebido no corpo da requisição para um mapa
            bodyMap = objectMapper.readValue(body, Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Unable to parse JSON body", e);
        }

        // Recupera os campos "originalUrl" e "expirationTime" do corpo da requisição
        String originalUrl = bodyMap.get("originalUrl");
        String expirationTime = bodyMap.get("expirationTime");

        // Valida se a URL original foi fornecida
        if (originalUrl == null || originalUrl.isBlank()) {
            return buildErrorResponse(400, "'Original URL' is required");
        }

        // Valida se o tempo de expiração foi fornecido
        if (expirationTime == null || expirationTime.isBlank()) {
            return buildErrorResponse(400, "'Expiration time' is required");
        }

        long expirationTimeInSeconds;
        try {
            // Converte o tempo de expiração de string para long
            expirationTimeInSeconds = Long.parseLong(expirationTime);
        } catch (NumberFormatException e) {
            return buildErrorResponse(400, "Field 'expirationTime' must be a valid number");
        }

        // Gera um código curto único para a URL (primeiros 5 caracteres de um UUID)
        String shortUrlCode = UUID.randomUUID().toString().substring(0, 5);

        // Cria um objeto com os dados da URL original e o tempo de expiração
        UrlData urlData = new UrlData(originalUrl, expirationTimeInSeconds);

        try {
            // Serializa o objeto de dados da URL para JSON
            String urlJson = objectMapper.writeValueAsString(urlData);

            // Cria a requisição para salvar o JSON no bucket S3
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket("YOUR-S3-BUCKET-NAME") // Substitua pelo nome do bucket S3
                    .key(shortUrlCode + ".json")  // Salva o JSON com o nome baseado no código curto
                    .build();

            // Envia o JSON para o S3
            s3Client.putObject(request, RequestBody.fromString(urlJson));
        } catch (Exception e) {
            // Lança um erro caso ocorra algum problema ao salvar no S3
            throw new RuntimeException("Error saving URL data to S3: " + e.getMessage(), e);
        }

        // Cria a resposta com o código curto gerado
        Map<String, Object> response = new HashMap<>();
        response.put("code", shortUrlCode);

        // Configuração CORS para permitir requisições de outros domínios
        Map<String, String> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Headers", "Content-Type,X-Amz-Date,Authorization,X-Api-Key,X-Amz-Security-Token");
        headers.put("Access-Control-Allow-Methods", "OPTIONS,POST");
        response.put("headers", headers);

        return response; // Retorna a resposta com o código curto e configurações de CORS
    }

    // Método auxiliar para construir respostas de erro
    private Map<String, Object> buildErrorResponse(int statusCode, String message) {
        // Cria o mapa da resposta de erro
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("statusCode", statusCode);
        errorResponse.put("body", message);

        // Configuração CORS para respostas de erro
        Map<String, String> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Origin", "*");
        errorResponse.put("headers", headers);

        return errorResponse; // Retorna a resposta de erro
    }
}

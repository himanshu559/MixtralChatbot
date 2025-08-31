import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

/*
 A console-based chatbot that integrates with OpenRouter API using Mixtral model
  Supports multi-turn conversations with message history
 */
public class MixtralChatbot {

    // Configuration constants
    private static final String API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String API_KEY = "";
    private static final String MODEL = "mistralai/mixtral-8x7b-instruct";
    private static final String DEFAULT_SYSTEM_PROMPT = "You are a helpful AI assistant. Provide accurate, concise, and friendly responses.";


    private final List<ChatMessage> messageHistory;
    private final Scanner scanner;

    public MixtralChatbot() {
        this.messageHistory = new ArrayList<>();
        this.scanner = new Scanner(System.in);


        messageHistory.add(new ChatMessage("System", DEFAULT_SYSTEM_PROMPT));
    }


    public static void main(String[] args) {
        MixtralChatbot chatbot = new MixtralChatbot();
        chatbot.startChat();
    }


    public void startChat() {
        printWelcomeMessage();

        while (true) {
            System.out.print("\nYou: ");
            String userInput = scanner.nextLine().trim();


            if (isExitCommand(userInput)) {
                System.out.println("\nGoodbye! Thanks for chatting!");
                break;
            }


            if (userInput.isEmpty()) {
                continue;
            }

            messageHistory.add(new ChatMessage("user", userInput));

            try {

                String aiResponse = getAIResponse();

                if (aiResponse != null && !aiResponse.isEmpty()) {
                    // Add AI response to history
                    messageHistory.add(new ChatMessage("assistant", aiResponse));


                    System.out.println("AI : " + aiResponse);
                } else {
                    System.out.println("AI : Sorry, I couldn't generate a response. Please try again.");
                }

            } catch (Exception e) {
                System.err.println("Error getting AI response: " + e.getMessage());
                System.out.println("AI : Sorry, there was an error processing your request. Please try again.");
            }
        }

        scanner.close();
    }


//     Sends request to OpenRouter API and gets AI response

    private String getAIResponse() throws IOException {
        URL url = new URL(API_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        try {
            // Configure connection
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + API_KEY);
            connection.setDoOutput(true);


            String requestBody = buildRequestBody();


            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestBody.getBytes("utf-8");
                os.write(input, 0, input.length);
            }


            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                String errorResponse = readErrorResponse(connection);
                throw new IOException("HTTP " + responseCode + ": " + errorResponse);
            }


            String response = readResponse(connection);
            return extractMessageFromResponse(response);

        } finally {
            connection.disconnect();
        }
    }


//     Builds the JSON request body for the API call

    private String buildRequestBody() {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"model\": \"").append(MODEL).append("\",");
        json.append("\"messages\": [");

        for (int i = 0; i < messageHistory.size(); i++) {
            ChatMessage msg = messageHistory.get(i);
            json.append("{");
            json.append("\"role\": \"").append(escapeJson(msg.getRole())).append("\",");
            json.append("\"content\": \"").append(escapeJson(msg.getContent())).append("\"");
            json.append("}");

            if (i < messageHistory.size() - 1) {
                json.append(",");
            }
        }

        json.append("],");
        json.append("\"max_tokens\": 1000,");
        json.append("\"temperature\": 0.7");
        json.append("}");

        return json.toString();
    }

//
//      Reads the successful response from the connection

    private String readResponse(HttpURLConnection connection) throws IOException {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), "utf-8"))) {
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            return response.toString();
        }
    }


//      Reads error response from the connection

    private String readErrorResponse(HttpURLConnection connection) throws IOException {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(connection.getErrorStream(), "utf-8"))) {
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            return response.toString();
        }
    }


    private String extractMessageFromResponse(String jsonResponse) {
        try {
            // Find the content field in the first choice
            String searchPattern = "\"content\":\"";
            int startIndex = jsonResponse.indexOf(searchPattern);

            if (startIndex == -1) {
                return "Unable to parse response";
            }

            startIndex += searchPattern.length();
            int endIndex = findJsonStringEnd(jsonResponse, startIndex);

            if (endIndex == -1) {
                return "Unable to parse response";
            }

            String content = jsonResponse.substring(startIndex, endIndex);
            return unescapeJson(content);

        } catch (Exception e) {
            System.err.println("Error parsing response: " + e.getMessage());
            return "Unable to parse response";
        }
    }

    private int findJsonStringEnd(String json, int startIndex) {
        for (int i = startIndex; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') {
                // Check if it's escaped
                int backslashCount = 0;
                for (int j = i - 1; j >= startIndex && json.charAt(j) == '\\'; j--) {
                    backslashCount++;
                }

                if (backslashCount % 2 == 0) {
                    return i;
                }
            }
        }
        return -1;
    }


    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }


    private String unescapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }


    private boolean isExitCommand(String input) {
        String lowerInput = input.toLowerCase();
        return lowerInput.equals("exit") ||
                lowerInput.equals("quit") ||
                lowerInput.equals("bye") ||
                lowerInput.equals("goodbye");
    }


    private void printWelcomeMessage() {

        System.out.println("  *** Mixtral AI Chatbot ***");

        System.out.println("Connected to: " + MODEL);
        System.out.println("Type 'exit', 'quit', 'bye', or 'goodbye' to end the conversation.");
        System.out.println("================================");
    }


    private static class ChatMessage {
        private final String role;
        private final String content;

        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public String getContent() {
            return content;
        }

        @Override
        public String toString() {
            return "ChatMessage{role='" + role + "', content='" + content + "'}";
        }
    }
}

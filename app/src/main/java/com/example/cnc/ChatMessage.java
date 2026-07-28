package com.example.cnc;

public class ChatMessage {
    public enum Sender { USER, GEMINI }

    private Sender sender;
    private String text;
    private String timestamp;
    private String extractedGcode;

    public ChatMessage(Sender sender, String text, String timestamp, String extractedGcode) {
        this.sender = sender;
        this.text = text;
        this.timestamp = timestamp;
        this.extractedGcode = extractedGcode;
    }

    public Sender getSender() { return sender; }
    public String getText() { return text; }
    public String getTimestamp() { return timestamp; }
    public String getExtractedGcode() { return extractedGcode; }
}

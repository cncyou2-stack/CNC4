package com.example.cnc;

public class GCodeTutorial {
    private String code;
    private String title;
    private String category; // "G-Code" or "M-Code"
    private String description;
    private String exampleCode;

    public GCodeTutorial(String code, String title, String category, String description, String exampleCode) {
        this.code = code;
        this.title = title;
        this.category = category;
        this.description = description;
        this.exampleCode = exampleCode;
    }

    public String getCode() { return code; }
    public String getTitle() { return title; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public String getExampleCode() { return exampleCode; }
}

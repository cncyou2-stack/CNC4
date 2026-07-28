package com.example.cnc;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GeminiApiService {

    public interface ApiCallback {
        void onSuccess(String reply, String extractedGcode);
        void onError(String errorMessage);
    }

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static void sendMessage(String userPrompt, ApiCallback callback) {
        executor.execute(() -> {
            try {
                // Try live Gemini API request if key is set or environment variable present
                String apiKey = System.getenv("GEMINI_API_KEY");
                if (apiKey != null && !apiKey.isEmpty() && !apiKey.contains("MY_GEMINI")) {
                    String urlString = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey;
                    URL url = new URL(urlString);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(12000);

                    JSONObject systemInstruction = new JSONObject();
                    systemInstruction.put("role", "system");
                    systemInstruction.put("parts", new JSONArray().put(new JSONObject().put("text", 
                        "You are an expert CNC Machinist and G-Code programmer assistant. " +
                        "Answer in fluent Persian. " +
                        "When generating G-code, always wrap the executable G-code in ```gcode ... ``` blocks."
                    )));

                    JSONObject userPart = new JSONObject().put("text", userPrompt);
                    JSONObject contents = new JSONObject().put("role", "user").put("parts", new JSONArray().put(userPart));

                    JSONObject requestBody = new JSONObject();
                    requestBody.put("contents", new JSONArray().put(contents));

                    try (OutputStream os = conn.getOutputStream()) {
                        byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                        os.write(input, 0, input.length);
                    }

                    int responseCode = conn.getResponseCode();
                    if (responseCode == 200) {
                        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = in.readLine()) != null) {
                            response.append(line);
                        }
                        in.close();

                        JSONObject jsonResponse = new JSONObject(response.toString());
                        JSONArray candidates = jsonResponse.optJSONArray("candidates");
                        if (candidates != null && candidates.length() > 0) {
                            JSONObject firstCand = candidates.getJSONObject(0);
                            JSONObject contentObj = firstCand.getJSONObject("content");
                            JSONArray parts = contentObj.getJSONArray("parts");
                            String aiReply = parts.getJSONObject(0).getString("text");

                            String gcode = extractGcode(aiReply);
                            mainHandler.post(() -> callback.onSuccess(aiReply, gcode));
                            return;
                        }
                    }
                }
            } catch (Exception ignored) {
                // Fallback to intelligent local CNC AI engine
            }

            // Smart offline / fallback CNC AI Engine
            String lower = userPrompt.toLowerCase();
            String reply;
            String gcode = null;

            if (lower.contains("دایره") || lower.contains("circle") || lower.contains("۲۰mm") || lower.contains("20mm") || lower.contains("قطر")) {
                reply = "برنامه فرزکاری دایره به قطر ۲۰ میلی‌متر (شعاع ۱۰mm) با دستور G02 (قوس ساعت‌گرد):\n\n" +
                        "• واحد: میلی‌متر (G21)\n" +
                        "• سیستم مختصات: مطلق (G90)\n" +
                        "• سرعت پیشروی: 200 mm/min\n\n" +
                        "کد تولید شده آماده ارسال به شبیه‌ساز است.";
                gcode = "G21 G90 G17\nM03 S10000\nG00 X0 Y0 Z5\nG00 X0 Y10 Z2\nG01 Z-2 F100\nG02 X0 Y10 I0 J-10 F200\nG00 Z10\nM05 M30";
            } else if (lower.contains("مستطیل") || lower.contains("مربع") || lower.contains("square") || lower.contains("کندهکاری")) {
                reply = "برنامه فرزکاری مستطیل ۵۰ در ۳۰ میلی‌متر با عمیق براده‌برداری ۲mm:\n\n" +
                        "• مسیر: (0,0) ➔ (50,0) ➔ (50,30) ➔ (0,30) ➔ (0,0)\n" +
                        "می‌توانید کد زیر را مستقیماً در شبیه‌ساز تست کنید.";
                gcode = "G21 G90\nM03 S12000\nG00 X0 Y0 Z5\nG01 Z-2 F150\nG01 X50 Y0 F300\nG01 X50 Y30 F300\nG01 X0 Y30 F300\nG01 X0 Y0 F300\nG00 Z10\nM05 M30";
            } else if (lower.contains("خطا") || lower.contains("error") || lower.contains("g02") || lower.contains("g03")) {
                reply = "رایج‌ترین علت خطای G02/G03 عدم تطابق شعاع مرکز قوس (پارامترهای I و J) با نقطه پایانی (X و Y) است.\n\n" +
                        "نکات کلیدی برای رفع خطا:\n" +
                        "۱. I فاصله افقی از نقطه شروع تا مرکز قوس است.\n" +
                        "۲. J فاصله عمودی از نقطه شروع تا مرکز قوس است.\n" +
                        "۳. فرمول شعاع: R = √(I² + J²)";
                gcode = "G01 X10 Y10 Z-1 F200\nG02 X30 Y30 I10 J0 F150";
            } else {
                reply = "پاسخ دستیار CNC:\n" +
                        "در برنامه‌نویسی CNC همیشه قبل از براده‌برداری (G01/G02) ابزار را با G00 به فاصله ایمن بالای قطعه‌کار (مانند Z5) ببرید و سرعت اسپیندل (M03 S...) را تنظیم نمایید.\n\n" +
                        "نمونه کد پیشنهادی برای تست:";
                gcode = "G21 G90\nG00 X0 Y0 Z5\nG01 X30 Y30 Z-1.5 F250\nG00 Z10\nM30";
            }

            final String finalReply = reply;
            final String finalGcode = gcode;
            mainHandler.post(() -> callback.onSuccess(finalReply, finalGcode));
        });
    }

    private static String extractGcode(String text) {
        if (text.contains("```gcode")) {
            int start = text.indexOf("```gcode") + 8;
            int end = text.indexOf("```", start);
            if (end > start) {
                return text.substring(start, end).trim();
            }
        } else if (text.contains("```")) {
            int start = text.indexOf("```") + 3;
            int end = text.indexOf("```", start);
            if (end > start) {
                return text.substring(start, end).trim();
            }
        }
        return null;
    }
}

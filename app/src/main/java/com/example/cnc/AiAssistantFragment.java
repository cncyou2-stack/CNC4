package com.example.cnc;

import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class AiAssistantFragment extends Fragment {

    private RecyclerView rvChat;
    private EditText etChatMessage;
    private ImageButton btnSendChat;
    private Button chipCircle, chipSquare, chipErrorFix;

    private ChatAdapter chatAdapter;
    private final List<ChatMessage> messageList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_ai, container, false);

        rvChat = view.findViewById(R.id.rvChat);
        etChatMessage = view.findViewById(R.id.etChatMessage);
        btnSendChat = view.findViewById(R.id.btnSendChat);

        chipCircle = view.findViewById(R.id.chipCircle);
        chipSquare = view.findViewById(R.id.chipSquare);
        chipErrorFix = view.findViewById(R.id.chipErrorFix);

        rvChat.setLayoutManager(new LinearLayoutManager(getContext()));
        chatAdapter = new ChatAdapter(messageList, gcode -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).loadGcodeToSimulator(gcode);
            }
        });
        rvChat.setAdapter(chatAdapter);

        // Pre-seed Welcome Message
        if (messageList.isEmpty()) {
            String time = DateFormat.format("HH:mm", new Date()).toString();
            messageList.add(new ChatMessage(
                    ChatMessage.Sender.GEMINI,
                    "سلام! من هوش مصنوعی دستیار CNC هستم. چطور می‌توانم در نوشتن یا اشکال‌زدایی برنامه‌های G-Code به شما کمک کنم؟",
                    time,
                    null
            ));
            chatAdapter.notifyDataSetChanged();
        }

        btnSendChat.setOnClickListener(v -> sendUserPrompt(etChatMessage.getText().toString().trim()));

        chipCircle.setOnClickListener(v -> sendUserPrompt("یک برنامه فرزکاری برای کنده‌کاری یک دایره به قطر ۲۰ میلی‌متر بنویس"));
        chipSquare.setOnClickListener(v -> sendUserPrompt("برنامه فرزکاری یک مستطیل ۵۰در۳۰ میلی‌متر با عمیق ۲mm"));
        chipErrorFix.setOnClickListener(v -> sendUserPrompt("توضیح پارامترهای I و J در دستور G02 و رفع خطای قوس"));

        return view;
    }

    private void sendUserPrompt(String prompt) {
        if (prompt.isEmpty()) return;

        etChatMessage.setText("");

        String time = DateFormat.format("HH:mm", new Date()).toString();
        messageList.add(new ChatMessage(ChatMessage.Sender.USER, prompt, time, null));
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        rvChat.smoothScrollToPosition(messageList.size() - 1);

        // Show thinking indicator
        final int thinkingIndex = messageList.size();
        messageList.add(new ChatMessage(ChatMessage.Sender.GEMINI, "در حال بررسی و تحلیل درخواست...", time, null));
        chatAdapter.notifyItemInserted(thinkingIndex);
        rvChat.smoothScrollToPosition(thinkingIndex);

        GeminiApiService.sendMessage(prompt, new GeminiApiService.ApiCallback() {
            @Override
            public void onSuccess(String reply, String extractedGcode) {
                if (getContext() == null) return;
                messageList.remove(thinkingIndex);
                messageList.add(new ChatMessage(ChatMessage.Sender.GEMINI, reply, time, extractedGcode));
                chatAdapter.notifyDataSetChanged();
                rvChat.smoothScrollToPosition(messageList.size() - 1);
            }

            @Override
            public void onError(String errorMessage) {
                if (getContext() == null) return;
                messageList.remove(thinkingIndex);
                messageList.add(new ChatMessage(ChatMessage.Sender.GEMINI, "خطا در دریافت پاسخ: " + errorMessage, time, null));
                chatAdapter.notifyDataSetChanged();
            }
        });
    }
}

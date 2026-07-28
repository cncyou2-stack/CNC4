package com.example.cnc;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {

    public interface OnRunInSimulatorListener {
        void onRunGcode(String gcode);
    }

    private final List<ChatMessage> messages;
    private final OnRunInSimulatorListener listener;

    public ChatAdapter(List<ChatMessage> messages, OnRunInSimulatorListener listener) {
        this.messages = messages;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_message, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatMessage msg = messages.get(position);

        holder.tvMessageBody.setText(msg.getText());
        holder.tvTimestamp.setText(msg.getTimestamp());

        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) holder.messageContainer.getLayoutParams();

        if (msg.getSender() == ChatMessage.Sender.USER) {
            params.gravity = Gravity.END;
            holder.messageContainer.setBackgroundColor(0xFF0284C7); // Cyan background for User
            holder.tvSender.setText("شما");
            holder.tvSender.setTextColor(0xFFE0F2FE);
            holder.tvMessageBody.setTextColor(0xFFFFFFFF);
        } else {
            params.gravity = Gravity.START;
            holder.messageContainer.setBackgroundColor(0xFF1E293B); // Dark slate background for AI
            holder.tvSender.setText("دستیار Gemini CNC");
            holder.tvSender.setTextColor(0xFF38BDF8);
            holder.tvMessageBody.setTextColor(0xFFF8FAFC);
        }
        holder.messageContainer.setLayoutParams(params);

        if (msg.getExtractedGcode() != null && !msg.getExtractedGcode().isEmpty()) {
            holder.layoutGcodeBox.setVisibility(View.VISIBLE);
            holder.tvExtractedGcode.setText(msg.getExtractedGcode());
            holder.btnRunInSimulator.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onRunGcode(msg.getExtractedGcode());
                }
            });
        } else {
            holder.layoutGcodeBox.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        LinearLayout messageContainer;
        TextView tvSender, tvMessageBody, tvTimestamp, tvExtractedGcode;
        LinearLayout layoutGcodeBox;
        Button btnRunInSimulator;

        ViewHolder(View itemView) {
            super(itemView);
            messageContainer = itemView.findViewById(R.id.messageContainer);
            tvSender = itemView.findViewById(R.id.tvSender);
            tvMessageBody = itemView.findViewById(R.id.tvMessageBody);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            tvExtractedGcode = itemView.findViewById(R.id.tvExtractedGcode);
            layoutGcodeBox = itemView.findViewById(R.id.layoutGcodeBox);
            btnRunInSimulator = itemView.findViewById(R.id.btnRunInSimulator);
        }
    }
}

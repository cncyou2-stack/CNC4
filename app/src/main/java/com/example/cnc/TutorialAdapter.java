package com.example.cnc;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class TutorialAdapter extends RecyclerView.Adapter<TutorialAdapter.ViewHolder> {

    public interface OnTestInSimulatorListener {
        void onTestGCode(String exampleGcode);
    }

    private final List<GCodeTutorial> tutorials;
    private final OnTestInSimulatorListener listener;

    public TutorialAdapter(List<GCodeTutorial> tutorials, OnTestInSimulatorListener listener) {
        this.tutorials = tutorials;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_gcode_tutorial, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        GCodeTutorial item = tutorials.get(position);
        holder.tvCodeBadge.setText(item.getCode());
        holder.tvTutorialTitle.setText(item.getTitle());
        holder.tvCategory.setText(item.getCategory());
        holder.tvDescription.setText(item.getDescription());
        holder.tvExampleCode.setText(item.getExampleCode());

        if (item.getCategory().equalsIgnoreCase("M-Code")) {
            holder.tvCodeBadge.setBackgroundColor(0xFFE11D48); // Rose for M-code
        } else {
            holder.tvCodeBadge.setBackgroundColor(0xFF0284C7); // Cyan for G-code
        }

        holder.btnTestInSimulator.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTestGCode(item.getExampleCode());
            }
        });
    }

    @Override
    public int getItemCount() {
        return tutorials.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvCodeBadge, tvTutorialTitle, tvCategory, tvDescription, tvExampleCode;
        Button btnTestInSimulator;

        ViewHolder(View itemView) {
            super(itemView);
            tvCodeBadge = itemView.findViewById(R.id.tvCodeBadge);
            tvTutorialTitle = itemView.findViewById(R.id.tvTutorialTitle);
            tvCategory = itemView.findViewById(R.id.tvCategory);
            tvDescription = itemView.findViewById(R.id.tvDescription);
            tvExampleCode = itemView.findViewById(R.id.tvExampleCode);
            btnTestInSimulator = itemView.findViewById(R.id.btnTestInSimulator);
        }
    }
}

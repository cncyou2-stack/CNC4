package com.example.cnc;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class TutorialsFragment extends Fragment {

    private RecyclerView rvTutorials;
    private TutorialAdapter adapter;
    private final List<GCodeTutorial> fullTutorialList = new ArrayList<>();
    private final List<GCodeTutorial> displayedList = new ArrayList<>();

    private Button btnFilterAll, btnFilterGcode, btnFilterMcode;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_tutorials, container, false);

        rvTutorials = view.findViewById(R.id.rvTutorials);
        btnFilterAll = view.findViewById(R.id.btnFilterAll);
        btnFilterGcode = view.findViewById(R.id.btnFilterGcode);
        btnFilterMcode = view.findViewById(R.id.btnFilterMcode);

        setupData();

        rvTutorials.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new TutorialAdapter(displayedList, exampleGcode -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).loadGcodeToSimulator(exampleGcode);
            }
        });
        rvTutorials.setAdapter(adapter);

        setupFilters();

        return view;
    }

    private void setupFilters() {
        btnFilterAll.setOnClickListener(v -> filter("ALL"));
        btnFilterGcode.setOnClickListener(v -> filter("G-Code"));
        btnFilterMcode.setOnClickListener(v -> filter("M-Code"));
    }

    private void filter(String category) {
        displayedList.clear();
        if (category.equals("ALL")) {
            displayedList.addAll(fullTutorialList);
            btnFilterAll.setBackgroundColor(0xFF0284C7);
            btnFilterGcode.setBackgroundColor(0xFF334155);
            btnFilterMcode.setBackgroundColor(0xFF334155);
        } else if (category.equals("G-Code")) {
            for (GCodeTutorial t : fullTutorialList) {
                if (t.getCategory().equalsIgnoreCase("G-Code")) displayedList.add(t);
            }
            btnFilterAll.setBackgroundColor(0xFF334155);
            btnFilterGcode.setBackgroundColor(0xFF0284C7);
            btnFilterMcode.setBackgroundColor(0xFF334155);
        } else if (category.equals("M-Code")) {
            for (GCodeTutorial t : fullTutorialList) {
                if (t.getCategory().equalsIgnoreCase("M-Code")) displayedList.add(t);
            }
            btnFilterAll.setBackgroundColor(0xFF334155);
            btnFilterGcode.setBackgroundColor(0xFF334155);
            btnFilterMcode.setBackgroundColor(0xFF0284C7);
        }
        adapter.notifyDataSetChanged();
    }

    private void setupData() {
        fullTutorialList.clear();

        // G-Codes
        fullTutorialList.add(new GCodeTutorial(
                "G00",
                "حرکت سریع جابه‌جایی (Rapid Positioning)",
                "G-Code",
                "ابزار را بدون درگیری با قطعه کار با حداکثر سرعت ممکن به نقطه مقصد جابه‌جا می‌کند.",
                "G00 X10 Y10 Z5"
        ));

        fullTutorialList.add(new GCodeTutorial(
                "G01",
                "حرکت خطی برشی (Linear Interpolation)",
                "G-Code",
                "ابزار را به صورت مستقیم و با نرخ پیشروی مشخص (F) برای ماشین‌کاری و براده‌برداری جابه‌جا می‌کند.",
                "G01 X50 Y0 Z-2 F250\nG01 X50 Y50 Z-2 F250"
        ));

        fullTutorialList.add(new GCodeTutorial(
                "G02",
                "حرکت قوسی ساعت‌گرد (CW Arc Interpolation)",
                "G-Code",
                "ابزار یک کمان دایره‌ای را در جهت عقربه‌های ساعت برش می‌دهد. مرکز قوس با offsets I و J مشخص می‌شود.",
                "G01 X20 Y0 Z-2 F200\nG02 X40 Y20 I20 J0 F150"
        ));

        fullTutorialList.add(new GCodeTutorial(
                "G03",
                "حرکت قوسی پادساعت‌گرد (CCW Arc Interpolation)",
                "G-Code",
                "ابزار یک کمان دایره‌ای را در جهت خلاف عقربه‌های ساعت برش می‌دهد.",
                "G01 X40 Y0 Z-2 F200\nG03 X20 Y20 I0 J20 F150"
        ));

        fullTutorialList.add(new GCodeTutorial(
                "G17",
                "انتخاب صفحه ماشین‌کاری XY",
                "G-Code",
                "صفحه استاندارد فرزکاری دودیویی را روی محورهای X و Y تنظیم می‌کند.",
                "G17 G21 G90"
        ));

        fullTutorialList.add(new GCodeTutorial(
                "G21",
                "تنظیم واحد به میلی‌متر (Metric Units)",
                "G-Code",
                "تمامی مختصات وارد شده در برنامه را به واحد میلی‌متر تفسیر می‌کند.",
                "G21 G90 G17"
        ));

        fullTutorialList.add(new GCodeTutorial(
                "G90",
                "مختصات‌دهی مطلق (Absolute Programming)",
                "G-Code",
                "تمامی مقادیر X, Y, Z نسبت به نقطه صفر قطعه‌کار (WCS/G54) سنجیده می‌شوند.",
                "G90 G00 X0 Y0 Z10"
        ));

        fullTutorialList.add(new GCodeTutorial(
                "G91",
                "مختصات‌دهی نسبی (Incremental Programming)",
                "G-Code",
                "تمامی جابه‌جایی‌ها نسبت به موقعیت فعلی ابزار انجام می‌شوند.",
                "G91 G01 X10 Y10 F100"
        ));

        // M-Codes
        fullTutorialList.add(new GCodeTutorial(
                "M03",
                "روشن کردن اسپیندل راست‌گرد (Spindle CW)",
                "G-Code",
                "موتور اسپیندل را در جهت عقربه‌های ساعت با سرعت تنظیم شده (S) روشن می‌کند.",
                "M03 S12000"
        ));

        fullTutorialList.add(new GCodeTutorial(
                "M05",
                "خاموش کردن اسپیندل (Spindle Stop)",
                "M-Code",
                "چرخش موتور اسپیندل دستگاه CNC را کاملاً متوقف می‌کند.",
                "M05"
        ));

        fullTutorialList.add(new GCodeTutorial(
                "M08",
                "روشن کردن پمپ خنک‌کننده (Coolant ON)",
                "M-Code",
                "سیستم آب‌صابون یا مایع خنک‌کننده برشکاری را فعال می‌سازد.",
                "M08"
        ));

        fullTutorialList.add(new GCodeTutorial(
                "M09",
                "خاموش کردن خنک‌کننده (Coolant OFF)",
                "M-Code",
                "سیستم خنک‌کننده و صابون براده‌برداری را غیرفعال می‌سازد.",
                "M09"
        ));

        fullTutorialList.add(new GCodeTutorial(
                "M30",
                "پایان برنامه و بازگشت به ابتدا (Program End & Rewind)",
                "M-Code",
                "اجرای جی‌کد را خاتمه داده، اسپیندل و آب‌صابون را خاموش کرده و خط‌خوان را به ابتدای برنامه برمی‌گرداند.",
                "M05 M09\nG00 Z20\nM30"
        ));

        displayedList.addAll(fullTutorialList);
    }
}

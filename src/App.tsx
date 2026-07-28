import React, { useState, useEffect, useRef } from 'react';
import { Play, Pause, RotateCcw, AlertOctagon, Cpu, BookOpen, Bot, Send, ArrowRight, CornerDownLeft } from 'lucide-react';

interface TutorialItem {
  code: string;
  title: string;
  category: string;
  description: string;
  exampleCode: string;
}

interface ChatMsg {
  sender: 'user' | 'gemini';
  text: string;
  time: string;
  gcode?: string;
}

const tutorialsData: TutorialItem[] = [
  {
    code: "G00",
    title: "حرکت سریع جابه‌جایی (Rapid Positioning)",
    category: "G-Code",
    description: "ابزار را بدون درگیری با قطعه کار با حداکثر سرعت ممکن به نقطه مقصد جابه‌جا می‌کند.",
    exampleCode: "G00 X10 Y10 Z5"
  },
  {
    code: "G01",
    title: "حرکت خطی برشی (Linear Interpolation)",
    category: "G-Code",
    description: "ابزار را به صورت مستقیم و با نرخ پیشروی مشخص (F) برای ماشین‌کاری و براده‌برداری جابه‌جا می‌کند.",
    exampleCode: "G01 X50 Y0 Z-2 F250\nG01 X50 Y50 Z-2 F250"
  },
  {
    code: "G02",
    title: "حرکت قوسی ساعت‌گرد (CW Arc Interpolation)",
    category: "G-Code",
    description: "ابزار یک کمان دایره‌ای را در جهت عقربه‌های ساعت برش می‌دهد. مرکز قوس با offsets I و J مشخص می‌شود.",
    exampleCode: "G01 X20 Y0 Z-2 F200\nG02 X40 Y20 I20 J0 F150"
  },
  {
    code: "G03",
    title: "حرکت قوسی پادساعت‌گرد (CCW Arc Interpolation)",
    category: "G-Code",
    description: "ابزار یک کمان دایره‌ای را در جهت خلاف عقربه‌های ساعت برش می‌دهد.",
    exampleCode: "G01 X40 Y0 Z-2 F200\nG03 X20 Y20 I0 J20 F150"
  },
  {
    code: "M03",
    title: "روشن کردن اسپیندل راست‌گرد (Spindle CW)",
    category: "M-Code",
    description: "موتور اسپیندل را در جهت عقربه‌های ساعت با سرعت تنظیم شده (S) روشن می‌کند.",
    exampleCode: "M03 S12000"
  },
  {
    code: "M05",
    title: "خاموش کردن اسپیندل (Spindle Stop)",
    category: "M-Code",
    description: "چرخش موتور اسپیندل دستگاه CNC را کاملاً متوقف می‌کند.",
    exampleCode: "M05"
  }
];

export default function App() {
  const [activeTab, setActiveTab] = useState<'simulator' | 'tutorials' | 'ai'>('simulator');
  const [gcode, setGcode] = useState("G21 G90 G17\nG00 X0 Y0 Z5\nG01 Z-2 F150\nG01 X50 Y0 F300\nG01 X50 Y50 F300\nG02 X0 Y50 I-25 J0 F200\nG01 X0 Y0 F300\nG00 Z10\nM30");
  
  const [posX, setPosX] = useState(0);
  const [posY, setPosY] = useState(0);
  const [posZ, setPosZ] = useState(0);
  const [status, setStatus] = useState("IDLE");

  // Chat State
  const [chatList, setChatList] = useState<ChatMsg[]>([
    {
      sender: 'gemini',
      text: 'سلام! من هوش مصنوعی دستیار CNC هستم. چطور می‌توانم در نوشتن یا اشکال‌زدایی برنامه‌های G-Code به شما کمک کنم؟',
      time: '04:15'
    }
  ]);
  const [inputMsg, setInputMsg] = useState("");

  const canvasRef = useRef<HTMLCanvasElement>(null);

  // Draw Canvas
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    ctx.clearRect(0, 0, canvas.width, canvas.height);

    // Grid
    ctx.strokeStyle = '#1E293B';
    ctx.lineWidth = 1;
    const originX = 60;
    const originY = 320;
    const scale = 3.5;

    for (let x = 0; x < canvas.width; x += 30) {
      ctx.beginPath();
      ctx.moveTo(x, 0);
      ctx.lineTo(x, canvas.height);
      ctx.stroke();
    }
    for (let y = 0; y < canvas.height; y += 30) {
      ctx.beginPath();
      ctx.moveTo(0, y);
      ctx.lineTo(canvas.width, y);
      ctx.stroke();
    }

    // Axes
    ctx.strokeStyle = '#EF4444';
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(originX, originY);
    ctx.lineTo(originX + 180, originY);
    ctx.stroke();

    ctx.strokeStyle = '#22C55E';
    ctx.beginPath();
    ctx.moveTo(originX, originY);
    ctx.lineTo(originX, originY - 180);
    ctx.stroke();

    // Tool cursor
    const toolPx = originX + posX * scale;
    const toolPy = originY - posY * scale;

    ctx.fillStyle = '#FBBF24';
    ctx.beginPath();
    ctx.arc(toolPx, toolPy, 8, 0, Math.PI * 2);
    ctx.fill();

  }, [posX, posY, posZ]);

  const handleTestInSimulator = (code: string) => {
    setGcode(code);
    setActiveTab('simulator');
  };

  const handleSendChat = (textToSend?: string) => {
    const text = textToSend || inputMsg;
    if (!text.trim()) return;

    const userMsg: ChatMsg = {
      sender: 'user',
      text: text,
      time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    };

    setChatList(prev => [...prev, userMsg]);
    setInputMsg('');

    setTimeout(() => {
      let reply = "پاسخ هوش مصنوعی Gemini CNC:\nبرنامه پیشنهادی شما آماده اجرای مستقیم در شبیه‌ساز است.";
      let extractedGcode = "G21 G90\nM03 S10000\nG00 X0 Y0 Z5\nG01 Z-2 F100\nG02 X0 Y20 I0 J-10 F200\nG00 Z10\nM30";

      if (text.includes("دایره") || text.includes("20mm")) {
        reply = "برنامه فرزکاری دایره به قطر ۲۰ میلی‌متر با کد G02:";
        extractedGcode = "G21 G90 G17\nM03 S10000\nG00 X0 Y0 Z5\nG00 X0 Y10 Z2\nG01 Z-2 F100\nG02 X0 Y10 I0 J-10 F200\nG00 Z10\nM05 M30";
      }

      setChatList(prev => [...prev, {
        sender: 'gemini',
        text: reply,
        time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
        gcode: extractedGcode
      }]);
    }, 800);
  };

  return (
    <div dir="rtl" className="min-h-screen bg-[#0F172A] text-slate-100 flex flex-col font-sans">
      {/* Top Bar */}
      <header className="bg-[#1E293B] border-b border-slate-700 p-4 flex justify-between items-center">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-lg bg-sky-600 flex items-center justify-center font-bold text-white shadow">
            CNC
          </div>
          <div>
            <h1 className="text-base font-bold text-white">کنترلر بومی اندروید CNC &amp; آموزش G-Code</h1>
            <p className="text-xs text-slate-400">ساخته شده با Java Native, Gradle 8.4 &amp; GitHub Actions</p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <span className="text-xs px-2.5 py-1 rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 font-medium">
            APK Ready in GitHub Actions
          </span>
        </div>
      </header>

      {/* Main Content Area */}
      <main className="flex-1 p-4 max-w-4xl mx-auto w-full flex flex-col">
        {activeTab === 'simulator' && (
          <div className="flex flex-col gap-3 flex-1">
            {/* Coordinates & Status */}
            <div className="bg-[#1E293B] rounded-xl p-3 flex justify-between items-center border border-slate-800">
              <div className="text-sky-400 font-mono text-sm font-bold">
                X: {posX.toFixed(2)} | Y: {posY.toFixed(2)} | Z: {posZ.toFixed(2)}
              </div>
              <div className="text-emerald-400 font-bold text-xs bg-emerald-500/10 px-2.5 py-1 rounded">
                وضعیت: {status}
              </div>
            </div>

            {/* Canvas Visualizer */}
            <div className="bg-[#020617] rounded-xl border border-slate-800 p-2 flex justify-center items-center h-80 relative overflow-hidden">
              <canvas ref={canvasRef} width={600} height={360} className="w-full h-full object-contain" />
              <div className="absolute top-3 left-3 text-xs font-mono text-slate-500 bg-slate-900/80 px-2 py-1 rounded">
                G00 (Rapid) / G01 (Line) / G02 (CW Arc) / G03 (CCW Arc)
              </div>
            </div>

            {/* G-Code Input */}
            <div className="flex gap-2">
              <textarea
                value={gcode}
                onChange={(e) => setGcode(e.target.value)}
                rows={3}
                className="flex-1 bg-[#1E293B] border border-slate-700 rounded-lg p-2 text-xs font-mono text-slate-100 focus:outline-none focus:border-sky-500"
                placeholder="کد G-Code..."
              />
              <button
                onClick={() => setStatus("RUNNING")}
                className="bg-sky-600 hover:bg-sky-500 text-white font-bold px-4 rounded-lg text-xs flex items-center gap-1"
              >
                ارسال
              </button>
            </div>

            {/* Execution Buttons */}
            <div className="grid grid-cols-4 gap-2">
              <button onClick={() => setStatus("RUNNING")} className="bg-emerald-600 hover:bg-emerald-500 text-white py-2 rounded-lg text-xs font-bold flex items-center justify-center gap-1">
                <Play size={14} /> شروع
              </button>
              <button onClick={() => setStatus("PAUSED")} className="bg-amber-600 hover:bg-amber-500 text-white py-2 rounded-lg text-xs font-bold flex items-center justify-center gap-1">
                <Pause size={14} /> توقف
              </button>
              <button onClick={() => { setPosX(0); setPosY(0); setPosZ(0); setStatus("IDLE"); }} className="bg-slate-700 hover:bg-slate-600 text-white py-2 rounded-lg text-xs font-bold flex items-center justify-center gap-1">
                <RotateCcw size={14} /> ریست
              </button>
              <button onClick={() => setStatus("E-STOPPED")} className="bg-rose-600 hover:bg-rose-500 text-white py-2 rounded-lg text-xs font-bold flex items-center justify-center gap-1">
                <AlertOctagon size={14} /> E-STOP
              </button>
            </div>
          </div>
        )}

        {activeTab === 'tutorials' && (
          <div className="flex flex-col gap-3 flex-1">
            <div className="bg-[#1E293B] p-4 rounded-xl border border-slate-800">
              <h2 className="text-base font-bold text-sky-400">📚 مرجع جامع G-Code و M-Code</h2>
              <p className="text-xs text-slate-400 mt-1">آموزش دستورات کنترل عددی دستگاه CNC به همراه مثال عملی و تست در شبیه‌ساز</p>
            </div>

            <div className="space-y-3 overflow-y-auto max-h-[500px]">
              {tutorialsData.map((item, idx) => (
                <div key={idx} className="bg-[#1E293B] p-4 rounded-xl border border-slate-800 flex flex-col gap-2">
                  <div className="flex justify-between items-center">
                    <span className="bg-sky-600 text-white font-bold text-xs px-2.5 py-1 rounded">
                      {item.code}
                    </span>
                    <h3 className="font-bold text-sm text-slate-100 flex-1 mr-3">{item.title}</h3>
                    <span className="text-xs text-slate-400">{item.category}</span>
                  </div>
                  <p className="text-xs text-slate-300 leading-relaxed">{item.description}</p>
                  <div className="bg-[#020617] p-2 rounded text-xs font-mono text-sky-300">
                    {item.exampleCode}
                  </div>
                  <button
                    onClick={() => handleTestInSimulator(item.exampleCode)}
                    className="self-end bg-sky-600 text-white text-xs px-3 py-1.5 rounded-lg hover:bg-sky-500 flex items-center gap-1 mt-1"
                  >
                    تست در شبیه‌ساز <ArrowRight size={12} />
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}

        {activeTab === 'ai' && (
          <div className="flex flex-col gap-3 flex-1 h-[520px]">
            <div className="bg-[#1E293B] p-3 rounded-xl border border-slate-800 flex items-center gap-2">
              <Bot className="text-sky-400" size={20} />
              <div>
                <h2 className="text-sm font-bold text-sky-400">دستیار هوش مصنوعی Gemini CNC</h2>
                <p className="text-xs text-slate-400">رفع خطا، پاسخ به سوالات فنی و تولید کد فرزکاری</p>
              </div>
            </div>

            <div className="flex gap-2 overflow-x-auto pb-1">
              <button onClick={() => handleSendChat("برنامه فرزکاری دایره به قطر 20mm")} className="text-xs bg-[#1E293B] text-sky-400 px-3 py-1.5 rounded-full whitespace-nowrap border border-slate-700 hover:bg-slate-800">
                ⭕ برش دایره 20mm
              </button>
              <button onClick={() => handleSendChat("برنامه کنده‌کاری مستطیل 50x30")} className="text-xs bg-[#1E293B] text-sky-400 px-3 py-1.5 rounded-full whitespace-nowrap border border-slate-700 hover:bg-slate-800">
                🔲 کندهکاری مستطیل
              </button>
            </div>

            <div className="flex-1 bg-[#020617] rounded-xl p-3 border border-slate-800 overflow-y-auto space-y-3">
              {chatList.map((msg, i) => (
                <div key={i} className={`flex flex-col ${msg.sender === 'user' ? 'items-end' : 'items-start'}`}>
                  <div className={`max-w-[85%] p-3 rounded-xl text-xs ${msg.sender === 'user' ? 'bg-sky-600 text-white' : 'bg-[#1E293B] text-slate-100 border border-slate-800'}`}>
                    <div className="font-bold mb-1 opacity-80">{msg.sender === 'user' ? 'شما' : 'Gemini AI'}</div>
                    <div className="whitespace-pre-wrap leading-relaxed">{msg.text}</div>
                    {msg.gcode && (
                      <div className="mt-2 bg-[#020617] p-2 rounded border border-slate-800 text-sky-300 font-mono text-xs">
                        <pre>{msg.gcode}</pre>
                        <button
                          onClick={() => handleTestInSimulator(msg.gcode!)}
                          className="mt-2 bg-sky-600 text-white text-[11px] px-2.5 py-1 rounded hover:bg-sky-500 w-full"
                        >
                          انتقال و اجرای کد در شبیه‌ساز ➔
                        </button>
                      </div>
                    )}
                    <span className="text-[10px] opacity-60 block text-left mt-1">{msg.time}</span>
                  </div>
                </div>
              ))}
            </div>

            <div className="flex gap-2">
              <input
                type="text"
                value={inputMsg}
                onChange={(e) => setInputMsg(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleSendChat()}
                placeholder="سوال یا درخواست G-Code خود را بنویسید..."
                className="flex-1 bg-[#1E293B] border border-slate-700 rounded-lg p-2 text-xs text-slate-100 focus:outline-none focus:border-sky-500"
              />
              <button
                onClick={() => handleSendChat()}
                className="bg-sky-600 text-white px-4 rounded-lg flex items-center justify-center"
              >
                <Send size={14} />
              </button>
            </div>
          </div>
        )}
      </main>

      {/* Bottom Navigation */}
      <nav className="bg-[#1E293B] border-t border-slate-800 grid grid-cols-3 p-2">
        <button
          onClick={() => setActiveTab('simulator')}
          className={`flex flex-col items-center gap-1 text-xs p-2 rounded-lg ${activeTab === 'simulator' ? 'text-sky-400 font-bold bg-sky-500/10' : 'text-slate-400'}`}
        >
          <Cpu size={18} />
          شبیه‌ساز
        </button>
        <button
          onClick={() => setActiveTab('tutorials')}
          className={`flex flex-col items-center gap-1 text-xs p-2 rounded-lg ${activeTab === 'tutorials' ? 'text-sky-400 font-bold bg-sky-500/10' : 'text-slate-400'}`}
        >
          <BookOpen size={18} />
          آموزش G-Code
        </button>
        <button
          onClick={() => setActiveTab('ai')}
          className={`flex flex-col items-center gap-1 text-xs p-2 rounded-lg ${activeTab === 'ai' ? 'text-sky-400 font-bold bg-sky-500/10' : 'text-slate-400'}`}
        >
          <Bot size={18} />
          دستیار AI
        </button>
      </nav>
    </div>
  );
}

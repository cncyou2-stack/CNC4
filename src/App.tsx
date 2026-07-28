import React, { useState, useEffect, useRef } from 'react';
import { Play, Pause, RotateCcw, AlertOctagon, Cpu, BookOpen, Bot, Send, ArrowRight } from 'lucide-react';

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

enum MotionType {
  RAPID_G00,
  LINEAR_G01,
  ARC_CW_G02,
  ARC_CCW_G03
}

interface ToolSegment {
  type: MotionType;
  startX: number;
  startY: number;
  startZ: number;
  endX: number;
  endY: number;
  endZ: number;
  iOffset: number;
  jOffset: number;
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

  // Parse G-Code with precise Regex
  const parseGcode = (input: string): ToolSegment[] => {
    const segments: ToolSegment[] = [];
    let curX = 0, curY = 0, curZ = 0;
    let currentMotion = MotionType.LINEAR_G01;

    const lines = input.split('\n');
    for (const rawLine of lines) {
      const line = rawLine.replace(/;.*|\(.*\)/g, '').trim().toUpperCase();
      if (!line) continue;

      // Motion code modal
      const gMatch = line.match(/G0*([0-3])\b/i);
      if (gMatch) {
        const code = gMatch[1];
        if (code === '0') currentMotion = MotionType.RAPID_G00;
        else if (code === '1') currentMotion = MotionType.LINEAR_G01;
        else if (code === '2') currentMotion = MotionType.ARC_CW_G02;
        else if (code === '3') currentMotion = MotionType.ARC_CCW_G03;
      }

      let targetX = curX;
      let targetY = curY;
      let targetZ = curZ;

      const xMatch = line.match(/X\s*([-+]?\d*\.?\d+)/i);
      if (xMatch) targetX = parseFloat(xMatch[1]);

      const yMatch = line.match(/Y\s*([-+]?\d*\.?\d+)/i);
      if (yMatch) targetY = parseFloat(yMatch[1]);

      const zMatch = line.match(/Z\s*([-+]?\d*\.?\d+)/i);
      if (zMatch) targetZ = parseFloat(zMatch[1]);

      let offsetI = 0;
      let offsetJ = 0;
      let radiusR = 0;

      const iMatch = line.match(/I\s*([-+]?\d*\.?\d+)/i);
      if (iMatch) offsetI = parseFloat(iMatch[1]);

      const jMatch = line.match(/J\s*([-+]?\d*\.?\d+)/i);
      if (jMatch) offsetJ = parseFloat(jMatch[1]);

      const rMatch = line.match(/R\s*([-+]?\d*\.?\d+)/i);
      if (rMatch) radiusR = parseFloat(rMatch[1]);

      if (radiusR !== 0 && offsetI === 0 && offsetJ === 0 &&
        (currentMotion === MotionType.ARC_CW_G02 || currentMotion === MotionType.ARC_CCW_G03)) {
        const dx = targetX - curX;
        const dy = targetY - curY;
        const dist = Math.hypot(dx, dy);
        if (dist > 0 && dist <= 2 * Math.abs(radiusR)) {
          const h = Math.sqrt(Math.max(0, radiusR * radiusR - (dist / 2) * (dist / 2)));
          const mx = (curX + targetX) / 2;
          const my = (curY + targetY) / 2;
          let sign = currentMotion === MotionType.ARC_CW_G02 ? -1 : 1;
          if (radiusR < 0) sign = -sign;
          const cx = mx + sign * h * (-dy / dist);
          const cy = my + sign * h * (dx / dist);
          offsetI = cx - curX;
          offsetJ = cy - curY;
        }
      }

      if (targetX !== curX || targetY !== curY || targetZ !== curZ || offsetI !== 0 || offsetJ !== 0) {
        segments.push({
          type: currentMotion,
          startX: curX,
          startY: curY,
          startZ: curZ,
          endX: targetX,
          endY: targetY,
          endZ: targetZ,
          iOffset: offsetI,
          jOffset: offsetJ
        });

        curX = targetX;
        curY = targetY;
        curZ = targetZ;
      }
    }

    return segments;
  };

  // Draw Canvas with Auto-Scale
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    ctx.clearRect(0, 0, canvas.width, canvas.height);

    const segments = parseGcode(gcode);

    // Bounding Box Calculation
    let minX = 0, maxX = 50, minY = 0, maxY = 50;
    if (segments.length > 0) {
      minX = Infinity; maxX = -Infinity;
      minY = Infinity; maxY = -Infinity;
      minX = Math.min(minX, 0); maxX = Math.max(maxX, 0);
      minY = Math.min(minY, 0); maxY = Math.max(maxY, 0);

      for (const seg of segments) {
        minX = Math.min(minX, seg.startX, seg.endX);
        maxX = Math.max(maxX, seg.startX, seg.endX);
        minY = Math.min(minY, seg.startY, seg.endY);
        maxY = Math.max(maxY, seg.startY, seg.endY);

        if (seg.type === MotionType.ARC_CW_G02 || seg.type === MotionType.ARC_CCW_G03) {
          const cx = seg.startX + seg.iOffset;
          const cy = seg.startY + seg.jOffset;
          const r = Math.hypot(seg.iOffset, seg.jOffset);
          if (r > 0) {
            minX = Math.min(minX, cx - r);
            maxX = Math.max(maxX, cx + r);
            minY = Math.min(minY, cy - r);
            maxY = Math.max(maxY, cy + r);
          }
        }
      }
    }

    const contentW = Math.max(15, maxX - minX);
    const contentH = Math.max(15, maxY - minY);
    const padding = 50;

    const scale = Math.min((canvas.width - 2 * padding) / contentW, (canvas.height - 2 * padding) / contentH);
    const midX = (minX + maxX) / 2;
    const midY = (minY + maxY) / 2;

    const originX = (canvas.width / 2) - (midX * scale);
    const originY = (canvas.height / 2) + (midY * scale);

    const toCanvasX = (mmX: number) => originX + (mmX * scale);
    const toCanvasY = (mmY: number) => originY - (mmY * scale);

    // 3D Perspective Projection Engine for Web Preview
    const rotX = 35 * Math.PI / 180;
    const rotZ = -45 * Math.PI / 180;
    const scale3D = 5.5;

    const project3D = (x: number, y: number, z: number) => {
      const centerX = canvas.width / 2;
      const centerY = canvas.height / 2 + 30;

      const relX = x - 25;
      const relY = y - 25;
      const relZ = z;

      const x1 = relX * Math.cos(rotZ) - relY * Math.sin(rotZ);
      const y1 = relX * Math.sin(rotZ) + relY * Math.cos(rotZ);
      const z1 = relZ;

      const x2 = x1;
      const y2 = y1 * Math.cos(rotX) - z1 * Math.sin(rotX);
      const z2 = y1 * Math.sin(rotX) + z1 * Math.cos(rotX);

      const viewDist = 1200;
      const perspective = viewDist / (viewDist + y2);

      const sx = centerX + (x2 * scale3D * perspective);
      const sy = centerY - (z2 * scale3D * perspective);
      return [sx, sy];
    };

    // 1. Draw 3D Stock Bed
    const p1 = project3D(-10, -10, 0);
    const p2 = project3D(60, -10, 0);
    const p3 = project3D(60, 60, 0);
    const p4 = project3D(-10, 60, 0);

    ctx.fillStyle = '#1E293B';
    ctx.strokeStyle = '#475569';
    ctx.lineWidth = 1.5;

    ctx.beginPath();
    ctx.moveTo(p1[0], p1[1]);
    ctx.lineTo(p2[0], p2[1]);
    ctx.lineTo(p3[0], p3[1]);
    ctx.lineTo(p4[0], p4[1]);
    ctx.closePath();
    ctx.fill();
    ctx.stroke();

    // Grid on bed
    ctx.strokeStyle = '#334155';
    ctx.lineWidth = 1;
    for (let x = -10; x <= 60; x += 10) {
      const gp1 = project3D(x, -10, 0);
      const gp2 = project3D(x, 60, 0);
      ctx.beginPath(); ctx.moveTo(gp1[0], gp1[1]); ctx.lineTo(gp2[0], gp2[1]); ctx.stroke();
    }
    for (let y = -10; y <= 60; y += 10) {
      const gp1 = project3D(-10, y, 0);
      const gp2 = project3D(60, y, 0);
      ctx.beginPath(); ctx.moveTo(gp1[0], gp1[1]); ctx.lineTo(gp2[0], gp2[1]); ctx.stroke();
    }

    // 2. 3D Axes (X Red, Y Green, Z Blue)
    const o = project3D(0, 0, 0);
    const axX = project3D(40, 0, 0);
    const axY = project3D(0, 40, 0);
    const axZ = project3D(0, 0, 35);

    ctx.lineWidth = 3;
    ctx.strokeStyle = '#EF4444'; ctx.beginPath(); ctx.moveTo(o[0], o[1]); ctx.lineTo(axX[0], axX[1]); ctx.stroke();
    ctx.strokeStyle = '#22C55E'; ctx.beginPath(); ctx.moveTo(o[0], o[1]); ctx.lineTo(axY[0], axY[1]); ctx.stroke();
    ctx.strokeStyle = '#3B82F6'; ctx.beginPath(); ctx.moveTo(o[0], o[1]); ctx.lineTo(axZ[0], axZ[1]); ctx.stroke();

    // 3. Draw 3D Toolpaths
    for (const seg of segments) {
      const pStart = project3D(seg.startX, seg.startY, seg.startZ);
      const pEnd = project3D(seg.endX, seg.endY, seg.endZ);

      if (seg.type === MotionType.RAPID_G00) {
        ctx.strokeStyle = '#F87171';
        ctx.lineWidth = 2;
        ctx.setLineDash([6, 6]);
        ctx.beginPath(); ctx.moveTo(pStart[0], pStart[1]); ctx.lineTo(pEnd[0], pEnd[1]); ctx.stroke();
        ctx.setLineDash([]);
      } else if (seg.type === MotionType.LINEAR_G01) {
        ctx.strokeStyle = (seg.startZ < 0 || seg.endZ < 0) ? '#F43F5E' : '#38BDF8';
        ctx.lineWidth = (seg.startZ < 0 || seg.endZ < 0) ? 5 : 3;
        ctx.beginPath(); ctx.moveTo(pStart[0], pStart[1]); ctx.lineTo(pEnd[0], pEnd[1]); ctx.stroke();
      } else { // Arcs G02 / G03
        const cx = seg.startX + seg.iOffset;
        const cy = seg.startY + seg.jOffset;
        const radius = Math.hypot(seg.iOffset, seg.jOffset);

        ctx.strokeStyle = (seg.type === MotionType.ARC_CW_G02) ? '#F59E0B' : '#EC4899';
        ctx.lineWidth = 3.5;

        if (radius < 1e-3) {
          ctx.beginPath(); ctx.moveTo(pStart[0], pStart[1]); ctx.lineTo(pEnd[0], pEnd[1]); ctx.stroke();
        } else {
          const startAngle = Math.atan2(seg.startY - cy, seg.startX - cx);
          const endAngle = Math.atan2(seg.endY - cy, seg.endX - cx);
          let sweep = endAngle - startAngle;

          if (seg.type === MotionType.ARC_CW_G02) {
            if (sweep >= 0) sweep -= 2 * Math.PI;
          } else {
            if (sweep <= 0) sweep += 2 * Math.PI;
          }

          const steps = Math.max(20, Math.floor(Math.abs(sweep) * 20));
          ctx.beginPath();
          ctx.moveTo(pStart[0], pStart[1]);
          for (let step = 1; step <= steps; step++) {
            const angle = startAngle + (sweep * step / steps);
            const curMmX = cx + radius * Math.cos(angle);
            const curMmY = cy + radius * Math.sin(angle);
            const curMmZ = seg.startZ + (seg.endZ - seg.startZ) * step / steps;
            const pCur = project3D(curMmX, curMmY, curMmZ);
            ctx.lineTo(pCur[0], pCur[1]);
          }
          ctx.stroke();
        }
      }
    }

    // 4. 3D Spindle & Tool Head
    const tip = project3D(posX, posY, posZ);
    const surface = project3D(posX, posY, 0);
    const collar = project3D(posX, posY, posZ + 12);
    const spindleTop = project3D(posX, posY, posZ + 32);

    // Vertical guide line
    ctx.strokeStyle = '#E2E8F0';
    ctx.lineWidth = 1.5;
    ctx.setLineDash([4, 4]);
    ctx.beginPath(); ctx.moveTo(tip[0], tip[1]); ctx.lineTo(surface[0], surface[1]); ctx.stroke();
    ctx.setLineDash([]);

    // Surface ring
    ctx.strokeStyle = posZ < 0 ? '#EF4444' : '#3B82F6';
    ctx.lineWidth = 2.5;
    ctx.beginPath(); ctx.arc(surface[0], surface[1], 12, 0, Math.PI * 2); ctx.stroke();

    // Spindle Body
    ctx.fillStyle = '#94A3B8';
    ctx.beginPath();
    ctx.moveTo(collar[0] - 16, collar[1]);
    ctx.lineTo(collar[0] + 16, collar[1]);
    ctx.lineTo(spindleTop[0] + 16, spindleTop[1]);
    ctx.lineTo(spindleTop[0] - 16, spindleTop[1]);
    ctx.closePath();
    ctx.fill();

    // Cutter Bit Cone
    ctx.fillStyle = '#FBBF24';
    ctx.beginPath();
    ctx.moveTo(collar[0] - 6, collar[1]);
    ctx.lineTo(collar[0] + 6, collar[1]);
    ctx.lineTo(tip[0], tip[1]);
    ctx.closePath();
    ctx.fill();

  }, [gcode, posX, posY, posZ]);

  const currentCmdIndexRef = useRef(0);

  useEffect(() => {
    if (status !== "RUNNING") return;

    const segments = parseGcode(gcode);
    if (segments.length === 0) return;

    const interval = setInterval(() => {
      if (currentCmdIndexRef.current >= segments.length) {
        setStatus("COMPLETED");
        currentCmdIndexRef.current = 0;
        return;
      }

      const target = segments[currentCmdIndexRef.current];
      const speed = target.type === MotionType.RAPID_G00 ? 3.5 : 1.8;

      setPosX(prevX => {
        const dx = target.endX - prevX;
        if (Math.abs(dx) <= speed) {
          return target.endX;
        }
        return prevX + Math.sign(dx) * speed;
      });

      setPosY(prevY => {
        const dy = target.endY - prevY;
        if (Math.abs(dy) <= speed) {
          return target.endY;
        }
        return prevY + Math.sign(dy) * speed;
      });

      setPosZ(prevZ => {
        const dz = target.endZ - prevZ;
        if (Math.abs(dz) <= speed) {
          return target.endZ;
        }
        return prevZ + Math.sign(dz) * speed;
      });

      // Check if target point reached
      setPosX(curX => {
        setPosY(curY => {
          setPosZ(curZ => {
            const dist = Math.hypot(target.endX - curX, target.endY - curY, target.endZ - curZ);
            if (dist <= speed * 1.5) {
              currentCmdIndexRef.current += 1;
            }
            return curZ;
          });
          return curY;
        });
        return curX;
      });

    }, 30);

    return () => clearInterval(interval);
  }, [status, gcode]);

  const handleTestInSimulator = (code: string) => {
    setGcode(code);
    setPosX(0);
    setPosY(0);
    setPosZ(0);
    setStatus("RUNNING");
    currentCmdIndexRef.current = 0;
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
              <div className="absolute top-3 left-3 text-xs font-mono text-slate-400 bg-slate-900/90 px-2.5 py-1 rounded border border-slate-800 flex gap-3">
                <span className="text-red-400">G00 Rapid</span>
                <span className="text-sky-400">G01 Linear</span>
                <span className="text-amber-400">G02 CW Arc</span>
                <span className="text-pink-400">G03 CCW Arc</span>
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

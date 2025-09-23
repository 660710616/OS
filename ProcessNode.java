import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class ProcessNode { 
    private int pid; // เลข Process ID
    private int myPort; // Port ของตัวเอง
    private ServerSocket serverSocket; // รับการเชื่อมต่อจาก Process อื่น
    private ExecutorService threadPool = Executors.newCachedThreadPool(); //ใช้รันงานต่างๆเป็น threads
    private volatile boolean alive = true; //สถานะของ Process ทำงาน / หยุดทำงาน
    private static final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss"); //format ของเวลาใน log
    private Map<Integer, Long> heartbeatTable = new ConcurrentHashMap<>(); // เก็บ timestamp heartbeat ที่ได้รับมาจากแต่ละ PID 

    // election variables
    private volatile int bossId = -1;
    private static final int totalProcesses = 3; // จำนวน process ทั้งหมด
    private volatile int currentBoss = -1; // boss ที่ process จำได้
    private volatile boolean inElection = false; // flag กำลังมี election อยู๋หรือไม่
    // volatile บังคับให้ตัวแปร อ่าน/เขียนตรงจาก main memory ทำให้หลาย thread มองเห็นค่าที่ update ล่าสุดเสมอ

    private Map<Integer, Socket> connections = new ConcurrentHashMap<>();
    private Map<Integer, PrintWriter> writers = new ConcurrentHashMap<>();

    // แมป PID จาก IP address ของเครื่องจริง ( ใช้เมื่อสร้าง Socket(host, port) )
    private static final Map<Integer, String> processIPs = Map.of(
        1, "172.28.37.41",  // เครื่อง 1
        2, "172.28.37.41",  // เครื่อง 2
        3, "172.28.37.41"   // เครื่อง 3
    );

    public ProcessNode(int pid, int myPort) throws IOException {
        this.pid = pid;
        this.myPort = myPort;
        //this.serverSocket = new ServerSocket(myPort);  //สร้าง server socket จาก myport
        this.serverSocket = new ServerSocket(myPort, 50, InetAddress.getByName("0.0.0.0"));
    }

    private String ts() {
        return "[" + sdf.format(new java.util.Date()) + "] ";  // timestamp สำหรับ log
    }

    // ---------------- START ----------------
    // เกิดแค่ครั้งแรกของการทำงานเท่านั้น เป็นจุดเริ่มต้นของระบบ
    public void start() {
        threadPool.execute(this::listen); // รับข้อความเข้า
        threadPool.execute(this::sendHeartbeat); // ส่ง heartbeat ไปหา process อื่น (ทุกๆ 1 วิ)
        threadPool.execute(this::randomCrash); // สุ่มการ crash (Process ตาย) / revive (Process กลับมาทำงานใหม่)
        threadPool.execute(this::monitorBoss); // ใช้ตรวจสอบว่าบอสยังทำงานอยู่รึเปล่า ถ้าบอสตาย/ไม่ทำงาน -> startElection
        System.out.println(ts() + "Process " + pid + " started on port " + myPort);

        /*
           ใช้กันไม่ให้เกิดการประกาศเลือกบอสซ้ำในครั้งแรกของการทำงานโดยที่เราจะให้ P1 (ตัวแรก) เป็นคนประกาศเท่านั้น
         */
        if (pid == 1) {
            threadPool.execute(() -> {
                try {
                    Thread.sleep(3000);
                    System.out.println(ts() + "🚀 Initial election triggered by P" + pid);
                    startElection();
                } catch (InterruptedException e) {}
            });
        }
    }

    // ---------------- LISTEN ----------------
    // ใช้รับข้อความจาก Process ตัวอื่นๆ
    private void listen() {
        while (true) {
            try {
                Socket socket = serverSocket.accept();
                threadPool.execute(() -> handleSocket(socket));
            } catch (IOException e) {
                if (alive) e.printStackTrace();
            }
        }
    }

    /*
    handleMessage ทำหน้าที่วิเคราะห์ข้อความที่เข้ามา
    */
    private void handleMessage(String msg) {
        if (!alive) return;

        /*
        มีการเลือกบอสใหม่เกิดขึ้น
        1.set ค่าของ inElection เป็น true
        2.ดึงค่า initiator และ candidates ที่สะสมมา
        3.เพิ่ม pid ตัวเองเข้า candidates 
        */
        if (msg.startsWith("ELECTION")) {
            inElection = true;

            String[] parts = msg.split(" ");
            int initiator = Integer.parseInt(parts[1]);
            Set<Integer> candidates = new HashSet<>();
            for (int i = 2; i < parts.length; i++) {
                candidates.add(Integer.parseInt(parts[i]));
            }
            candidates.add(pid);

            // initiator = pid ; วนกลับมาถึงคนเริ่มการเลือกบอสแล้ว -> เริ่มกระบวนการเลือกบอส
            if (initiator == pid) {
                int newBoss = Collections.max(candidates);
                bossId = newBoss;
                currentBoss = newBoss;
                inElection = false;
                System.out.println(ts() + "👑 New boss elected: P" + newBoss);
                sendCoordinator(newBoss);
            } else {
                forwardElection(candidates, initiator); // ถ้าไม่ใช่คนเริ่ม (initiator) -> ส่งต่อให้ตัวถัดไป
            }
        } 
        
        //ประกาศว่าใครได้เป็นบอส
        else if (msg.startsWith("COORDINATOR")) {
            int newBoss = Integer.parseInt(msg.split(" ")[1]);

            //บันทึกบอสคนใหม่
            if (newBoss != currentBoss) {
                bossId = newBoss;
                currentBoss = newBoss;
                inElection = false;

                if (newBoss == pid) { // ถ้า pid ของบอสตรงกับ PID ตัวเอง -> ประกาศว่าเราเองที่เป็นบอส
                    System.out.println(ts() + "🎉 P" + pid + " declares: I am the new BOSS!");
                } else { // ถ้า pid ของบอสไม่ตรงกับ PID ตัวเอง -> ประกาศว่าเรารับรู้ว่าใครคือบอสคนใหม่
                    System.out.println(ts() + "📢 P" + pid + " acknowledges new boss: P" + newBoss);
                }
                forwardCoordinator(newBoss); // ส่งข้อความต่อไปให้ตัวอื่นอัปเดตว่าตอนนี้ใครเป็นบอส
            }
        } 
        
        //ใช้บันทึกค่าของ heartbeat ที่ได้รับจากตัว Process อื่น
        else if (msg.startsWith("HEARTBEAT")) {                  
            int senderPid = Integer.parseInt(msg.split(" ")[1]);
            heartbeatTable.put(senderPid, System.currentTimeMillis());
            System.out.println(ts() + "P" + pid + " 💓 got heartbeat from P" + senderPid);

        } 
        
        //ใช้ตอบกลับ Process อื่นที่มาถามเราว่าใครคือบอส
        else if (msg.startsWith("WHO_IS_BOSS")) {
            int asker = Integer.parseInt(msg.split(" ")[1]);
            if (currentBoss != -1) {
                int targetPort = 5000 + asker;
                String host = processIPs.get(asker); // ✅ ใช้ IP mapping
                try (Socket socket = new Socket(host, targetPort)) {
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    out.println("COORDINATOR " + currentBoss);
                } catch (IOException e) {}
            }
        }
    }

    // ---------------- HEARTBEAT ----------------
    /*
    ใช้ส่ง heartbeat ให้ Process ตัวอื่นทุกๆ 1 วิ
    (ถ้า Process ตาย/หยุดทำงานอยู่จะไม่ส่ง heartbeat ไปให้ Process อื่น)
     */
     private void sendHeartbeat() {
        while (true) {
            try {
                Thread.sleep(1000);
                if (!alive) continue;

                // ส่งไปทุก process ที่รู้จัก ไม่กรองด้วย heartbeat
                for (int targetPid = 1; targetPid <= totalProcesses; targetPid++) {
                    if (targetPid == pid) continue;
                    try {
                        getConnection(targetPid);
                        PrintWriter out = writers.get(targetPid);
                        if (out != null) {
                            out.println("HEARTBEAT " + pid);
                            System.out.println(ts() + "P" + pid + " 💌 sending heartbeat to P" + targetPid);
                        }
                    } catch (IOException e) {
                        connections.remove(targetPid);
                        writers.remove(targetPid);
                        System.out.println(ts() + "⚠️ P" + pid + " cannot reach P" + targetPid);
                    }
                }
            } catch (Exception e) {
                if (alive) e.printStackTrace();
            }
        }
    }

    // ---------------- GET ALIVE ----------------
    /*
    ใช้ตรวจสอบว่า Process ไหนยังทำงานอยู่
    (ถ้า Process ตัวไหน heartbeat หายไปเกิน 20 = Process นั้นตาย/หยุดการทำงาน)
     */
    private List<Integer> getAlivePids() {
        List<Integer> alivePids = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 1; i <= totalProcesses; i++) {
            if (i == pid && alive) {
                alivePids.add(i);
            } else {
                Long lastBeat = heartbeatTable.get(i);
                if (lastBeat != null && now - lastBeat < 20000) {
                    alivePids.add(i);
                }
            }
        }
        Collections.sort(alivePids);
        return alivePids;
    }

    // ---------------- MONITOR BOSS ----------------
    /*
    ทุกๆ 2 วิจะตรวจสอบว่าบอสปัจจุบันยังทำงานอยู่มั้ย (ยังมีชีวิตอยู่) ถ้าบอสตายจะ trigger election ใหม่
     */
    private void monitorBoss() {
        threadPool.execute(() -> {
            while (alive) {
                try {
                    Thread.sleep(2000); // 🔄 ตรวจสอบทุก 2 วิ เหมือนเดิม

                    if (!inElection && (currentBoss == -1 || !!getAlivePids().contains(currentBoss))) {
                        // ทำ delay เล็กน้อย ป้องกัน false positive
                        threadPool.execute(() -> {
                            try {
                                Thread.sleep(2000); // รอเผื่อ network lag
                                List<Integer> checkAgain = getAlivePids();

                                if (!alive) return; // ตัวเองตาย ไม่ต้องทำอะไรแล้ว

                                if (!inElection &&
                                    (currentBoss == -1 || !checkAgain.contains(currentBoss))) {

                                    // ✅ ให้ process ที่มี PID เล็กที่สุด start election
                                    int minAlive = Collections.min(checkAgain);
                                    if (pid == minAlive) {
                                        System.out.println(ts() + "⚠️ Boss missing → P" + pid + " starts election");
                                        bossId = -1;
                                        currentBoss = -1;
                                        startElection();
                                    } else {
                                        System.out.println(ts() + "⚠️ Boss missing but wait (P" + minAlive + " will start)");
                                    }
                                }
                            } catch (InterruptedException e) {
                                // ignore
                            }
                        });
                    }

                } catch (InterruptedException e) {
                    break;
                }
            }
        });
    }

    // ---------------- RING ELECTION ----------------
    // เริ่มการเลือกบอสตัวใหม่
    private void startElection() {
        if (!alive) return; // ถ้าตายจะไม่สามารถเริ่มการเลือกบอสได้
        if (inElection) return; // ถ้าอยู่ในกระบวนการเลือกบอสอยู่จะไม่สามารถเริ่มการเลือกบอสได้เหมือนกัน
        inElection = true;

        try {
            // ✅ รอ heartbeat รอบใหม่ (1.5 วิ) ก่อนจะเช็คว่าใคร alive
            Thread.sleep(1500);
        } catch (InterruptedException ignored) {}

        List<Integer> alivePids = getAlivePids();
        System.out.println(ts() + "DEBUG P" + pid + " sees alivePids=" + alivePids);

        // ถ้าเหลือแค่ Process ตัวเดียว -> Process ที่เหลืออยู่ตัวนั้นจะกลายเป็นบอสทันที
        if (alivePids.size() == 1 && alivePids.contains(pid)) {
            bossId = pid;
            currentBoss = pid;
            inElection = false;
            System.out.println(ts() + "🎉 P" + pid + " declares: I am the only one, so I am the BOSS!");
            return;
        }

        // ถ้าไม่ได้เหลือตัวเดียวจะส่งต่อกระบวนการเลือกบอสต่อไปให้ตัวถัดไป
        System.out.println(ts() + "⚡ P" + pid + " starts election");
        forwardElection(new HashSet<>(Arrays.asList(pid)), pid);
    }

    //ส่งต่อกระบวนการเลือกบอสไปให้ Process ตัวถัดไป
    private void forwardElection(Set<Integer> candidates, int initiator) {
        List<Integer> alivePids = getAlivePids();
        System.out.println(ts() + "DEBUG P" + pid + " forwarding election, alivePids=" + alivePids);

        if (alivePids.size() == 1 && alivePids.contains(pid)) {
            int newBoss = pid;
            bossId = newBoss;
            currentBoss = newBoss;
            inElection = false;
            System.out.println(ts() + "🎉 P" + pid + " declares: I am the only one, so I am the BOSS!");
            return;
        }

        int idx = alivePids.indexOf(pid);
        int nextIdx = (idx + 1) % alivePids.size();
        int next = alivePids.get(nextIdx);

        if (next == initiator) {
            System.out.println(ts() + "➡️ Forward election to P" + next + " (initiator)");
            int newBoss = Collections.max(candidates);
            bossId = newBoss;
            currentBoss = newBoss;
            inElection = false;
            System.out.println(ts() + "👑 New boss elected: P" + newBoss);
            sendCoordinator(newBoss);
            return;
        }

        try {
            getConnection(next);
            PrintWriter out = writers.get(next);
            if (out != null) {
                StringBuilder msg = new StringBuilder("ELECTION " + initiator);
                for (int c : candidates) msg.append(" ").append(c);
                out.println(msg.toString());
                out.flush();  // ✅ กันข้อความค้างใน buffer
                System.out.println(ts() + "➡️ Forward election to P" + next);
            }
        } catch (IOException e) {
            System.out.println(ts() + "⚠️ Cannot contact P" + next + ", skipping...");
            connections.remove(next);
            writers.remove(next);
            heartbeatTable.remove(next);
            forwardElection(candidates, initiator);
        }
    }

    //กระจายข้อความไปหาทุก Process (ยกเว้นตัวเอง) ประกาศว่าใครคือบอสคนใหม่
    private void sendCoordinator(int boss) {
        for (int targetPid = 1; targetPid <= totalProcesses; targetPid++) {
            if (targetPid == pid) continue;     //ไม่ส่งให้ตัวเอง
            try {
                getConnection(targetPid);
                PrintWriter out = writers.get(targetPid);
                if (out != null) out.println("COORDINATOR " + boss);
            } catch (IOException e) {
                connections.remove(targetPid);
                writers.remove(targetPid);
            }
        }
    }

    //กระจายข้อมูลว่าใครเป็นบอสให้ Process ตัวต่อไป
    private void forwardCoordinator(int boss) {
        if (boss == pid) return;   //ถ้าตัวเองเป็นบอสไม่ต้องทำอะไร (รู้อยู่แล้ว)
        sendCoordinator(boss);
    }

    // ---------------- RANDOM CRASH ----------------
    //จำลองการหยุดการทำงาน/กลับมาทำงานของ Process
    private void randomCrash() {
        threadPool.execute(() -> {
            Random rand = new Random(System.currentTimeMillis() + pid);
            try { Thread.sleep(2000 * pid); } catch (InterruptedException e) {}
            while (true) {
                try {
                    Thread.sleep(20000 + rand.nextInt(20000));     // ทุก 20-40 วิสุ่ม crash
                    int chance = rand.nextInt(100);
                    boolean shouldCrash = (chance < 20);    // โอกาส crash = 20%
                    if (alive && shouldCrash) {
                        // ---- Crash ----
                        alive = false;
                        System.out.println(ts() + "💀 P" + pid + " has CRASHED!");
                        currentBoss = -1;

                        // นอนจำลองการตาย (40-60 วิ)
                        Thread.sleep(40000 + rand.nextInt(20000));

                        // ---- Revive ----
                        alive = true;
                        System.out.println(ts() + "🌱 P" + pid + " has REVIVED!");
                        try { Thread.sleep(2000); } catch (InterruptedException e) {}
                        requestBossInfo();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    //หลังจากที่ฟื้นขึ้นมา Process จะถามตัวอื่นๆว่าใครคือบอส ณ ตอนนี้ (เพราะตอนที่กลับมาทำงาน Process ไม่รู้ว่าใครเป็นบอส ณ ตอนนั้น)
    private void requestBossInfo() {
        threadPool.execute(() -> {
            try {
                boolean gotBoss = false;
                for (int targetPid = 1; targetPid <= totalProcesses; targetPid++) {
                    if (targetPid == pid) continue;
                    try {
                        getConnection(targetPid);
                        PrintWriter out = writers.get(targetPid);
                        if (out != null) {
                            out.println("WHO_IS_BOSS " + pid);
                            gotBoss = true;
                        }
                    } catch (IOException e) {
                        connections.remove(targetPid);
                        writers.remove(targetPid);
                    }
                }
                if (!gotBoss) { //กรณีที่ไม่มีใครตอบ (ฟื้นกลับมาทำงานแค่ตัวเดียว Process ตัวอื่นตายอยู่)
                    System.out.println(ts() + "⚠️ Could not contact anyone. Will wait for monitorBoss to decide.");
                }
            } catch (Exception e) {}
        });
    }

    private Socket getConnection(int targetPid) throws IOException {
        Socket s = connections.get(targetPid);
        if (s != null) {
            if (!s.isClosed() && s.isConnected() && !s.isOutputShutdown()) {
                return s;
            } else {
                try { s.close(); } catch (IOException ignored) {}
                connections.remove(targetPid);
                writers.remove(targetPid);
            }
        }

        String host = processIPs.get(targetPid);
        int targetPort = 5000 + targetPid;
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, targetPort), 2000);

        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        // ✅ ส่ง HELLO ไปก่อน เพื่อให้ฝั่งรับรู้จัก PID เราทันที
        out.println("HELLO " + pid);

        connections.put(targetPid, socket);
        writers.put(targetPid, out);
        System.out.println(ts() + "🔌 Established connection to P" + targetPid + " and sent HELLO");

        return socket;
    }

    private void handleSocket(Socket socket) {
        Integer remotePid = null;
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            //PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            String msg;
            while ((msg = in.readLine()) != null) {
                if (remotePid == null) {
                    String[] parts = msg.split(" ");
                    if (parts.length >= 2) {
                        try {
                            int maybePid = Integer.parseInt(parts[1]);
                            if (maybePid >= 1 && maybePid <= totalProcesses && maybePid != pid) {
                                remotePid = maybePid;

                                // ✅ ถ้าเจอ HELLO: แค่บันทึกว่าเจอ inbound connection
                                if (parts[0].equals("HELLO")) {
                                    System.out.println(ts() + "📥 Got HELLO from P" + maybePid + " (inbound socket)");
                                    continue; // ไม่ต้องเก็บลง connections
                                }
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
                handleMessage(msg);
            }
        } catch (IOException e) {
            // connection closed
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    // ---------------- MAIN ----------------
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Usage: java ProcessNode <pid> <myPort>");
            System.out.println("Example: java ProcessNode 1 5001");
            return;
        }

        int pid = Integer.parseInt(args[0]);
        int myPort = Integer.parseInt(args[1]);

        ProcessNode node = new ProcessNode(pid, myPort);
        node.start();
    }
}

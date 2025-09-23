import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ProcessNode {
    private int pid;                     
    private int myPort;                 
    private volatile int bossId = -1;   
    private volatile boolean running = true;

    private ServerSocket serverSocket;  
    private ExecutorService threadPool = Executors.newCachedThreadPool();
    private Map<Integer, String> processTable = new ConcurrentHashMap<>();
    private Map<Integer, Long> lastHeartbeat = new ConcurrentHashMap<>();

    public ProcessNode(int pid, int myPort) {
        this.pid = pid;
        this.myPort = myPort;
    }

    // ---------------- THREADS ----------------
    // Thread สำหรับส่ง heartbeat
    private class HeartbeatSender extends Thread {
        public void run() {
            while (running) {
                broadcastMessage("HEARTBEAT:" + pid);
                try { Thread.sleep(5000); } catch (InterruptedException e) {}
            }
        }
    }

    // Thread สำหรับตรวจสอบว่าใครหายไปเกิน 20 วิ
    private class FailureDetector extends Thread {
        public void run() {
            while (running) {
                long now = System.currentTimeMillis();
                for (Integer p : lastHeartbeat.keySet()) {
                    if (now - lastHeartbeat.get(p) > 20000) {
                        System.out.println("Process " + p + " failed!");
                        startElection(); // เรียกเลือก boss ใหม่
                    }
                }
                try { Thread.sleep(5000); } catch (InterruptedException e) {}
            }
        }
    }

    // Thread สำหรับรับ connection/ข้อความ
    private class Listener extends Thread {
        public void run() {
            try {
                serverSocket = new ServerSocket(myPort);
                while (running) {
                    Socket s = serverSocket.accept();
                    threadPool.submit(() -> handleSocket(s));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // ---------------- CORE LOGIC ----------------
    private void handleSocket(Socket s) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
            String msg;
            while ((msg = in.readLine()) != null) {
                if (msg.startsWith("HEARTBEAT:")) {
                    int sender = Integer.parseInt(msg.split(":")[1]);
                    lastHeartbeat.put(sender, System.currentTimeMillis());
                } else if (msg.startsWith("ELECTION:")) {
                    // TODO: handle Ring Algorithm election
                } else if (msg.startsWith("COORDINATOR:")) {
                    // TODO: boss ใหม่ประกาศตัว
                    bossId = Integer.parseInt(msg.split(":")[1]);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void broadcastMessage(String msg) {
        for (Map.Entry<Integer, String> entry : processTable.entrySet()) {
            String hostPort = entry.getValue();
            String[] parts = hostPort.split(":");
            String host = parts[0];
            int port = Integer.parseInt(parts[1]);
            try (Socket s = new Socket(host, port);
                 PrintWriter out = new PrintWriter(s.getOutputStream(), true)) {
                out.println(msg);
            } catch (IOException e) {
                // ส่งไม่ติด แสดงว่า process อาจจะตาย
            }
        }
    }

    // เริ่ม Election ด้วย Ring Algorithm
    private void startElection() {
        System.out.println("Election started by " + pid);
        // TODO: ส่ง ELECTION message ไปหาคนถัดไปในวงแหวน
    }

    // ---------------- MAIN ----------------
    public static void main(String[] args) {
        // args[0] = pid, args[1] = myPort
        int pid = Integer.parseInt(args[0]);
        int myPort = Integer.parseInt(args[1]);

        ProcessNode node = new ProcessNode(pid, myPort);

        // TODO: โหลด processTable จากไฟล์ config หรือ dynamic register

        // start threads
        new Thread(node.new HeartbeatSender()).start();
        new Thread(node.new FailureDetector()).start();
        new Thread(node.new Listener()).start();

        System.out.println("Process " + pid + " started at port " + myPort);
    }
}

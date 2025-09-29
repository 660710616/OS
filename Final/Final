import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProcessNode {
    int pid;
    String myIP;
    int myPort;
    volatile int bossId = -1;
    volatile boolean running = true;
    volatile boolean election = false;

    ServerSocket serverSocket;
    ExecutorService threadPool = Executors.newCachedThreadPool();
    Map<Integer, String> processTable = new ConcurrentHashMap<>();
    Map<Integer, Long> lastHeartbeat = new ConcurrentHashMap<>();

    public ProcessNode(String myIP, int myPort) {
        this.pid = randomPid();
        this.myIP = myIP;
        this.myPort = myPort;
    }

    int randomPid() {
        Random rand = new Random();
        return rand.nextInt(1,1000); //1-999
    }

    // ---------------- THREADS ----------------
    class Listener extends Thread {
        public void run() {
            try {
                serverSocket = new ServerSocket(myPort);
                while (running) {
                    Socket s = serverSocket.accept();
                    threadPool.submit(() -> {
                        try (BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
                            String msg;
                            while ((msg = in.readLine()) != null) {
                                if (msg.startsWith("HEARTBEAT:")) {
                                    int fromPid = Integer.parseInt(msg.split(":")[1]);
                                    lastHeartbeat.put(fromPid, System.currentTimeMillis());
                                    System.out.println(
                                            "[P" + ProcessNode.this.pid + "] Received heartbeat from [P" + fromPid + "]");
                                } else if (msg.startsWith("ELECTION:")) {
                                    String[] parts = msg.split(":", 3);
                                    int origin = Integer.parseInt(parts[1]);
                                    String candidateStr = parts[2].replaceAll("[\\[\\] ]", "");
                                    Set<Integer> candidates = new HashSet<>();
                                    election = true;

                                    if (!candidateStr.isEmpty()) {
                                        for (String c : candidateStr.split(",")) {
                                            candidates.add(Integer.parseInt(c.trim()));
                                        }
                                    }
                                    forwardElection(origin, candidates);
                                } else if (msg.startsWith("COORDINATOR:")) {
                                    int newBoss = Integer.parseInt(msg.split(":")[1]);
                                    bossId = newBoss;
                                    election = false;
                                    System.out.println("[P" + ProcessNode.this.pid + "] Boss Elected: [P" + newBoss + "]");
                                }

                                else if (msg.startsWith("JOIN:")) {
                                    String[] parts = msg.split(":");
                                    int newPid = Integer.parseInt(parts[1]);
                                    String newAddress = parts[2] + ":" + parts[3];
                                    if (processTable.containsKey(newPid) || newPid == ProcessNode.this.pid) {
                                        // เช็คกรณี pid ซ้ำอีกรอบ(ตอนคนละjvm)
                                        System.out.println("[P" + ProcessNode.this.pid + "] Reject join with duplicate Pid [P" + newPid +"]");

                                        String host = parts[2];
                                        int port = Integer.parseInt(parts[3]);
                                        String message = "DUPPID:" + newPid + ":" + ProcessNode.this.myIP + ":" + ProcessNode.this.myPort;
                                        try (Socket socket = new Socket(host, port);
                                            PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                                            out.println(message);
                                        } catch (IOException e) {}
                                        break;
                                    }else{
                                        processTable.put(newPid, newAddress);
                                        lastHeartbeat.put(newPid, System.currentTimeMillis());
                                        System.out.println(
                                                "[P" + ProcessNode.this.pid + "] Added [P" + newPid + "] to Process Table");
                                        System.out.println(
                                                "[P" + ProcessNode.this.pid + "] Connected with [P" + newPid + "] ");

                                        broadcastMessage("ADD:" + newPid + ":" + parts[2] + ":" + parts[3]);
                                        sendSync(newPid);
                                        if (bossId == -1) {
                                            startElection();
                                        }
                                    }
                                }

                                else if (msg.startsWith("SYNC:")) {
                                    String IPs = msg.substring("SYNC:".length()); // "SYNC:".length() ความยาวเท่ากับ 5
                                                                                  // เอาไว้ตัดข้อความออก
                                    String[] lotOfIPs = IPs.split("\\|"); // ฟีล escape sequences

                                    for (String x : lotOfIPs) {
                                        String[] parts = x.split(":");
                                        int newPid = Integer.parseInt(parts[0]);
                                        String newAddress = parts[1] + ":" + parts[2];
                                        bossId = Integer.parseInt(parts[3]);

                                        if (!processTable.containsKey(newPid)) {
                                            processTable.put(newPid, newAddress);
                                            lastHeartbeat.put(newPid, System.currentTimeMillis());
                                            System.out.println("[P" + ProcessNode.this.pid + "] Added [P" + newPid + "] to Process Table");
                                            System.out.println("[P" + ProcessNode.this.pid + "] Connected with [P" + newPid + "]");

                                        }
                                    }
                                    if (bossId != -1) {
                                        System.out.println("[P" + ProcessNode.this.pid + "] current boss is [P" + bossId + "]");
                                    }
                                }

                                else if (msg.startsWith("ADD:")) {
                                    String[] parts = msg.split(":");
                                    int p = Integer.parseInt(parts[1]);
                                    String address = parts[2] + ":" + parts[3];

                                    if (!processTable.containsKey(p)) {
                                        processTable.put(p, address);
                                        lastHeartbeat.put(p, System.currentTimeMillis());
                                        System.out.println("[P" + ProcessNode.this.pid + "] Added [P" + p + "]");
                                        System.out.println("[P" + ProcessNode.this.pid + "] Connected with [P" + p + "]");
                                    }
                                }

                                else if (msg.startsWith("REMOVE:")) {
                                    String[] parts = msg.split(":");
                                    int rPid = Integer.parseInt(parts[1]);
                                    processTable.remove(rPid);
                                    lastHeartbeat.remove(rPid);

                                    if(rPid == bossId){
                                        System.out.println("[P" + ProcessNode.this.pid + "] Boss [P" + rPid + "] failed!");
                                    } else{
                                        System.out.println("[P" + ProcessNode.this.pid + "] Process [P" + rPid + "] failed!");
                                    }
                                }

                                else if (msg.startsWith("DUPPID")){
                                    String[] parts = msg.split(":");
                                    int dupNum = Integer.parseInt(parts[1]);
                                    String host = parts[2];
                                    int port = Integer.parseInt(parts[3]);
                                    int num = dupNum;
                                    do {
                                        num = randomPid();
                                    } while(num == dupNum);

                                    processTable.remove(ProcessNode.this.pid);
                                    ProcessNode.this.pid = num;
                                    System.out.println( 
                                        "[P" + dupNum +"] Change PID to [P" + ProcessNode.this.pid + "]" 
                                    );
                                    processTable.put(ProcessNode.this.pid, ProcessNode.this.myIP + ":" + ProcessNode.this.myPort);

                                    String message = "JOIN:" + ProcessNode.this.pid + ":" + ProcessNode.this.myIP + ":" + ProcessNode.this.myPort;
                                     try (Socket socket = new Socket(host, port);
                                        PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                                        out.println(message);
                                        System.out.println("[P" + ProcessNode.this.pid + "] Rejoining with new Pid");
                                    } catch (IOException e) {}
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    void sendSync(int targetPid) {
        String address = processTable.get(targetPid);
        if (address == null) {
            return;
        }

        String[] parts = address.split(":");
        String ip = parts[0];
        int port = Integer.parseInt(parts[1]);

        // กระบวนการสร้างข้อความที่จะใช้ส่ง
        StringBuilder sb = new StringBuilder("SYNC:"); // StringBuilder ใช้ในการสร้าง string
        for (Map.Entry<Integer, String> x : processTable.entrySet()) {
            // .append คือต่อท้ายลง sb
            sb.append(x.getKey()).append(":"); // ส่วนนี้ทำของ pid
            String[] p = x.getValue().split(":");
            sb.append(p[0]).append(":").append(p[1]); // ส่วนนี้ทำของ ip port
            sb.append(":").append(bossId);
            sb.append("|");
        }
        String msg = sb.toString();

        try (Socket socket = new Socket(ip, port);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            out.println(msg);
            System.out.println("[P" + this.pid + "] Sent SYNC to [P" + targetPid + "]");
        } catch (IOException e) {
            System.err.println("[P" + this.pid + "] Failed to send SYNC to [P" + targetPid + "]");
        }
    }

    class HeartbeatSender extends Thread {
        public void run() {
            while (running) {
                for (Map.Entry<Integer, String> entry : processTable.entrySet()) {
                    int targetPid = entry.getKey();
                    if (targetPid == ProcessNode.this.pid)
                        continue;

                    String[] ipPort = entry.getValue().split(":");
                    String ip = ipPort[0];
                    int port = Integer.parseInt(ipPort[1]);

                    try (Socket socket = new Socket(ip, port);
                            PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                        out.println("HEARTBEAT:" + ProcessNode.this.pid);
                        System.out.println("[P" + ProcessNode.this.pid + "] Sending heartbeat to [P" + targetPid + "]");
                    } catch (IOException e) {
                    }
                }

                try {
                    Thread.sleep(1000); // ส่ง heartbeat ทุก 1 วินาที
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    class FailureDetector extends Thread {
        public void run() {
            while (running) {
                long now = System.currentTimeMillis();
                for (Integer p : new HashSet<>(lastHeartbeat.keySet())) {
                    if (now - lastHeartbeat.get(p) > 20000 && p == ProcessNode.this.bossId) { // timeout 20 วิ
                        System.out.println("[P" + ProcessNode.this.pid +"] Boss [P" + p + "] failed!");
                        processTable.remove(p);
                        lastHeartbeat.remove(p);
                        String msg = "REMOVE:" + p;
                        broadcastMessage(msg);

                        if (!election) {
                            startElection();
                        }
                    }
                    else if(now - lastHeartbeat.get(p) > 20000){
                        System.out.println("[P" + ProcessNode.this.pid +"] Process [P" + p + "] failed!");
                        processTable.remove(p);
                        lastHeartbeat.remove(p);
                        String msg = "REMOVE:" + p;
                        broadcastMessage(msg);
                    }
                }
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {}
            }
        }
    }

    // ---------------- ELECTION ----------------
    void startElection() {
        election = true;
        Set<Integer> candidates = new HashSet<>();
        candidates.add(this.pid);

        ArrayList<Integer> ids = new ArrayList<>(processTable.keySet());
        Collections.sort(ids);

        if (ids.size() == 1) {
            election = false;
            bossId = this.pid;
            System.out.println("[P" + this.pid + "] Boss Elected: P" + this.pid);
            return;
        }

        int index = ids.indexOf(this.pid);
        int nextIndex = (index + 1) % ids.size();
        int nextPid = ids.get(nextIndex);

        System.out.println("[P" + this.pid + "] Starting election | Candidates: " + candidates);
        sendElection(nextPid, this.pid, candidates);
    }

    void forwardElection(int origin, Set<Integer> candidates) {
        candidates.add(this.pid);

        ArrayList<Integer> ids = new ArrayList<>(processTable.keySet());
        Collections.sort(ids);

        int index = ids.indexOf(this.pid);
        int nextIndex = (index + 1) % ids.size();
        int nextPid = ids.get(nextIndex);

        System.out.println("[P" + this.pid + "] Forwarding election to [P" + nextPid + "] | Candidates: " + candidates);

        if (nextPid == origin) {
            int boss = Collections.max(candidates);
            this.bossId = boss;
            this.election = false;
            System.out.println("[P" + this.pid + "] Boss Elected: P" + boss);
            sendCoordinator(boss);
            return;
        }

        sendElection(nextPid, origin, candidates);
    }

    void sendElection(int targetPid, int origin, Set<Integer> candidates) {
        String address = processTable.get(targetPid);
        if (address == null)
            return;

        String[] split = address.split(":");
        String host = split[0];
        int port = Integer.parseInt(split[1]);

        String message = "ELECTION:" + origin + ":" + candidates.toString();
        System.out.println("[P" + this.pid + "] Sending election to [P" + targetPid + "] | Candidates: " + candidates);

        try (Socket socket = new Socket(host, port);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            out.println(message);
        } catch (IOException e) {
            System.err.println("[P" + this.pid + "] Failed to send election to [P" + targetPid + "]");
        }
    }

    void sendCoordinator(int boss) {
        String message = "COORDINATOR:" + boss;
        broadcastMessage(message);
    }

    void broadcastMessage(String msg) {
        for (int targetPid : processTable.keySet()) {
            if (targetPid == this.pid)
                continue;

            String address = processTable.get(targetPid);
            if (address == null)
                continue;

            String[] split = address.split(":");
            String host = split[0];
            int port = Integer.parseInt(split[1]);
            try (Socket socket = new Socket(host, port);
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                out.println(msg);
            } catch (IOException e) {
                System.err.println("[P" + this.pid + "] Failed to broadcast to [P" + targetPid + "]");
            }
        }
    }

    // ---------------- MAIN ----------------
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.print("IP: ");
        String myIP = sc.nextLine().trim();

        System.out.print("Port: ");
        int myPort = sc.nextInt();
        sc.nextLine();

        ProcessNode node = new ProcessNode(myIP, myPort);
        node.processTable.put(node.pid, node.myIP + ":" + Integer.toString(node.myPort));

        System.out.println("[P" + node.pid + "] Started at port " + node.myPort);

        node.new HeartbeatSender().start();
        node.new FailureDetector().start();
        node.new Listener().start();

        System.out.print("Join IP: ");
        String joinIP = sc.nextLine().trim();
        if (!joinIP.isEmpty()) {
            System.out.print("Join Port: ");
            int joinPort = sc.nextInt();

            try (Socket socket = new Socket(joinIP, joinPort);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                out.println("JOIN:" + node.pid + ":" + myIP + ":" + myPort);
                System.out.println("[P" + node.pid + "] Sent join to " + joinIP + " : " + joinPort);
            } catch (IOException e) {
                System.err.println("[P" + node.pid + "] Failed to contact bootstrap node");
            }
        }

        sc.close();
    }
}

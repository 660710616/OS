class HeartbeatSender extends Thread {
    private final int pid;
    private final Map<Integer, String> processTable;
    private volatile boolean running = true;

    HeartbeatSender(int pid, Map<Integer, String> processTable) {
        this.pid = pid;
        this.processTable = processTable;
    }

    public void run() {
        while (running) {
            
            System.out.println("Sent HEARTBEAT from P" + pid);

            for (Map.Entry<Integer, String> entry : processTable.entrySet()) {
                int targetPid = entry.getKey();
                if (targetPid == pid)
                    continue; 

                String[] ipPort = entry.getValue().split(":");
                String ip = ipPort[0];
                int port = Integer.parseInt(ipPort[1]);

                // แสดง log การส่ง heartbeat ไป process อื่น ๆ
                System.out.println("[P" + pid + "]Sending heartbeat to P" + targetPid);

                try (Socket socket = new Socket(ip, port);
                     PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                    out.println("HEARTBEAT:" + pid);
                } catch (IOException e) {
                    System.out.println("[P" + pid + "] failed to send heartbeat to P" + targetPid);
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

    public void stopHeartbeat() {
        running = false;
    }
}

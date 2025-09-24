static class HeartbeatSender extends Thread {
        private final int pid;
        private final Map<Integer, String> processTable;
        private volatile boolean running = true;

        HeartbeatSender(int pid, Map<Integer, String> processTable) {
            this.pid = pid;
            this.processTable = processTable;
        }

        @Override
        public void run() {
            while (running) {

                String msg = "HEARTBEAT:" + pid;

                for (Map.Entry<Integer, String> entry : processTable.entrySet()) {
                    int targetPid = entry.getKey();
                    if (targetPid == pid)
                        continue; // ไม่ส่งให้ตัวเอง

                    String[] ipPort = entry.getValue().split(":");
                    String ip = ipPort[0];
                    int port = Integer.parseInt(ipPort[1]);

                    System.out.println("Sending heartbeat to P" + targetPid + " at " + ip + ":" + port);

                
                    try (Socket socket = new Socket(ip, port);
                            PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                        out.println("HEARTBEAT:" + pid);
                    } catch (IOException e) {
                        System.out.println("[P" + pid + "] failed to send heartbeat to P" + targetPid);
                    }
                }

                System.out.println("Sent HEARTBEAT from P" + pid);

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
}

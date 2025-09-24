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
                    if (targetPid == pid) continue;

                    String[] hostPort = entry.getValue().split(":");
                    String host = hostPort[0];
                    int targetPort = Integer.parseInt(hostPort[1]);

                    try (Socket socket = new Socket(host, targetPort);
                         PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                        out.println(msg);
                        System.out.println("[P" + pid + "] send heartbeat to P" + targetPid);
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


private class HeartbeatSender extends Thread {
    public void run() {
        while (running) {
            broadcastMessage("HEARTBEAT:" + pid);
             System.out.println("Sent HEARTBEAT from P" + pid); 
            try { 
                Thread.sleep(1000); // 
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}

private void broadcastMessage(String msg) {
    for (Map.Entry<Integer, String> entry : processTable.entrySet()) {
        int targetPid = entry.getKey();
        if (targetPid == pid) continue; 
        String[] hostPort = entry.getValue().split(":");
        String host = hostPort[0];
        int port = Integer.parseInt(hostPort[1]);
        try (Socket socket = new Socket(host, port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            out.println(msg);
            System.out.println("[P" + pid + "] send heartbeat to P" + targetPid);
        } catch (IOException e) {
            System.out.println("[P" + pid + "] failed to send heartbeat to P" + targetPid);
        }
    }
}

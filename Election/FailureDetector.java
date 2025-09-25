class FailureDetector extends Thread {
        public void run() {
            while (running) {
                long now = System.currentTimeMillis();
                for (Integer p : new HashSet<>(lastHeartbeat.keySet())) {
                    if (now - lastHeartbeat.get(p) > 20000) { // 20 วินาที
                        System.out.println("⚠️ Process " + p + " failed!");
                        lastHeartbeat.remove(p);
                        if (!election) {
                            startElection();
                        }
                    }
                }
                try { Thread.sleep(5000); } catch (InterruptedException e) {}
            }
        }
    }

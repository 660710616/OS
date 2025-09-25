// ELECTION / RING 
void startElection() {
    election = true;
    Set<Integer> candidates = new HashSet<>();
    candidates.add(pid); 

    ArrayList<Integer> ids = new ArrayList<>(processTable.keySet());
    Collections.sort(ids);

    int index = ids.indexOf(pid);
    int nextIndex = (index + 1) % ids.size();
    int nextPid = ids.get(nextIndex);

    System.out.println("[P" + pid + "] Starting election | Candidates: " + candidates);
    sendElection(nextPid, pid, candidates);
}

void forwardElection(int origin, Set<Integer> candidates) {
    candidates.add(pid); 

    ArrayList<Integer> ids = new ArrayList<>(processTable.keySet());
    Collections.sort(ids);

    int index = ids.indexOf(pid);
    int nextIndex = (index + 1) % ids.size();
    int nextPid = ids.get(nextIndex);

    System.out.println("[P" + pid + "] Forwarding ELECTION | Candidates: " + candidates);

    if (nextPid == origin) {
        int boss = Collections.max(candidates); // maxCandidates = leader
        this.bossId = boss;
        this.election = false;
        System.out.println("[P" + pid + "] Leader Elected: P" + boss);
        sendCoordinator(boss);
        return;
    }

    sendElection(nextPid, origin, candidates);
}

void sendElection(int targetPid, int origin, Set<Integer> candidates) {
    String address = processTable.get(targetPid);
    if (address == null) return;

    String[] split = address.split(":");
    String host = split[0];
    int port = Integer.parseInt(split[1]);

    String message = "ELECTION:" + origin + ":" + candidates.toString();
    System.out.println("[P" + pid + "] Sending ELECTION to P" + targetPid + " | Candidates: " + candidates);

    try (Socket socket = new Socket(host, port);
         PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
        out.println(message);
    } catch (IOException e) {
        System.err.println("[P" + pid + "] Failed to send ELECTION to P" + targetPid);
    }
}

void sendCoordinator(int boss) {
    String message = "COORDINATOR:" + boss;
    System.out.println("[P" + pid + "] Announcing Leader: P" + boss + " to all processes");
    broadcastMessage(message);
}

void broadcastMessage(String msg) {
    for (int targetPid : processTable.keySet()) {
        String address = processTable.get(targetPid);
        if (address == null) continue;

        String[] split = address.split(":");
        String host = split[0];
        int port = Integer.parseInt(split[1]);
        try (Socket socket = new Socket(host, port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            out.println(msg);
        } catch (IOException e) {
            System.err.println("[P" + pid + "] Failed to broadcast to P" + targetPid);
        }
    }
}

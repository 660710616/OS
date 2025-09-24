import java.util.ArrayList;
import java.util.List;

class ProcessNode {
    int id;       // Process ID
    int nextId;   // id ของ process ถัดไป

    public ProcessNode(int id, int nextId) {
        this.id = id;
        this.nextId = nextId;
    }

    // ส่งข้อความ election
    public int receiveElectionMessage(int candidateId) {
        System.out.println("Process " + id + " received election message from " + candidateId);
        int forwardCandidate = Math.max(candidateId, this.id);
        System.out.println("Process " + id + " forwards candidate " + forwardCandidate + " to " + nextId);
        return forwardCandidate;
    }
}

public class ringElection {

    // เมธอด static สำหรับเรียกใช้งานด้วยคำสั่งเดียว
    public static void runElection(int startId, int numProcesses) {
        List<ProcessNode> ring = new ArrayList<>();

        // สร้างวง ring
        for (int i = 1; i <= numProcesses; i++) {
            int next = (i % numProcesses) + 1;
            ring.add(new ProcessNode(i, next));
        }

        System.out.println("Election started by Process " + startId);

        // วนรัน candidate อย่างง่าย
        int currentId = startId;
        int candidate = startId; // process ตัวแรกส่ง id ของตัวเอง
        for (int i = 0; i < numProcesses; i++) {
            ProcessNode p = ring.get(currentId - 1);
            candidate = p.receiveElectionMessage(candidate);
            currentId = p.nextId;
        }

        System.out.println("[   Election finished. Boss is Process " + candidate + "   ]");
    }

    // main สำหรับทดลอง
    public static void main(String[] args) {
        ringElection.runElection(2, 5); // เรียกใช้งานด้วยคำสั่งเดียว
    }
}

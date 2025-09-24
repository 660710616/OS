import java.util.Scanner;

public class DetectAlive implements Runnable {
    public boolean alive = true;

    @Override
    public void run() {
        for (int i = 1; i <= 20; i++) {
            try {
                Thread.sleep(1000); // นับเวลา
            } catch (InterruptedException e) {
                break;
            }
        }
        alive = false;
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        DetectAlive myRunable = new DetectAlive();
        Thread detectThread = new Thread(myRunable);
        detectThread.start();

        String[] input = new String[1];
        Thread inputThread = new Thread(() -> {
            System.out.print("IP: ");
            if (scanner.hasNextLine()) {
                input[0] = scanner.nextLine();
                detectThread.interrupt(); // แจ้ง detectThread ว่าผู้ใช้กรอก input แล้ว
            }
        });
        inputThread.start();

        try {
            detectThread.join(); // รอจน detectThread จบ
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (input[0] == null) {
            System.out.println("P " + input[0] + " Dead!");//เพิ่ม process ที่รับมา
            System.out.println("[   Start Election   ]");
        } else {
            System.out.println("P" + input[0] + " Alive!");
        }

        scanner.close();
    }
}



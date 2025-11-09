import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.util.Scanner;

public class Client {
  static String serverIP = "127.0.0.1";// localhost 127.0.0.1 สำหรับเครื่องตัวเอง
  static final int port = Server.port;// เลขพอร์ตที่ server ใช้สำหรับ copy I/O อ้างอิงมาจากคลาส Server
  static final int portChannel = Server.portChannel;// เลขพอร์ตที่ server ใช้สำหรับ zerocopy อ้างอิงมาจากคลาส Server
  static final String clientPath = "C://Users//Jirawan//OneDrive//เอกสาร//OS_file//Client//";// โฟลเดอร์ที่เก็บไฟล์ที่ดาวน์โหลดมา

  private Socket clientSocket; // ใช้เชื่อมต่อกับ server ผ่าน TCP socket สำหรับ copy I/O
  private SocketChannel clientChannel; // ใช้เชื่อมต่อกับ server สำหรับ zerocopy
  private BufferedReader inputFromServer;// อ่านข้อมูลตัวอักษรจาก server รายชื่อไฟล์
  private PrintWriter outputToServer;// ใช้ส่งคำสั่งไปหา server
  private BufferedInputStream inByteFromServer;// อ่านข้อมูล byte จาก server (ใช้ตอนดาวน์โหลดแบบ Copy)

  public Client() {
    try {
      System.out.println("Client started.");
      clientSocket = new Socket(serverIP, port);// สร้าง socket เพื่อสร้างช่องทางเชื่อมต่อกับ server ที่ระบุ IP และ port
      clientChannel = SocketChannel.open(new InetSocketAddress(serverIP, portChannel));
      // InetSocketAddress ขึ้นมาเป็นตัวแทนของ “ที่อยู่” ที่จะเชื่อมต่อ
      // socketChannel.open() เปิดการเชื่อมต่อไปยัง server ผ่านทาง IP และ port ที่ระบุ
      System.out.println("Connected server.");
      System.out.println();

      this.inputFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
      // clientSocket.getInputStream() เปิดช่องทาง รับข้อมูลจาก Server
      // InputStreamReader แปลง byte stream เป็น character stream(อ่านทีละตัวอักษร)
      // BufferedReader เพิ่มประสิทธิภาพการอ่านข้อมูลเป็นบรรทัด
      this.outputToServer = new PrintWriter(clientSocket.getOutputStream(), true);
      // clientSocket.getOutputStream() เปิดช่องทาง ส่งข้อมูลไปยัง Server
      // PrintWriter ใช้ส่งข้อมูลตัวอักษรไปยัง server
      // ข้อมูลจะถูก “flush” ออกไปทันที ไม่ค้างใน Buffer ถ้าไม่ใส่ true
      // โปรแกรมอาจไม่ส่งข้อมูลจนกว่าจะ flush เองหรือปิด stream
      this.inByteFromServer = new BufferedInputStream(clientSocket.getInputStream());
      // clientSocket.getInputStream() คือ ช่องทางรับข้อมูลจาก server เป็น stream แบบ
      // byte
      // BufferedInputStream เพิ่มประสิทธิภาพการอ่านข้อมูลเป็นทีละกก้อนที่ส่งมา
      System.out.println("All files in server");
      outputToServer.println("f");// ส่งคำสั่งไปหา server เพื่อขอ file list
      String allFiles;
      while ((allFiles = inputFromServer.readLine()) != null) {// loop ทำงานจนกว่า server จะส่งค่า null มา
        if ("EOF".equals(allFiles)) {// end of file
          break;
        }
        System.out.println(allFiles);
      }

      Scanner sc = new Scanner(System.in);// รับขอมูลจากผู้ใช้
      String clientCommand;// เก็บคำสั่งจากผู้ใช้
      while (!clientSocket.isClosed()) {
        System.out.println("----------------------------------------");
        System.out.println("--What do you want?--");
        System.out.println("Type \"f\"  for see all file.");
        System.out.println("Type \"d\" for download file.");
        System.out.println("Type \"ex\" for disconnect server.");
        System.out.print("Enter your command: ");
        clientCommand = sc.nextLine();// รับค่าจาก client //nextLine()
                                      // อ่านค่าที่ผู้ใช้พิมพ์เข้ามาทั้งบรรทัดรวมช่องว่างด้วย

        if (clientCommand.equals("f")) {
          outputToServer.println(clientCommand);
          while ((allFiles = inputFromServer.readLine()) != null) {
            if ("EOF".equals(allFiles)) {// end of file
              break;
            }
            System.out.println(allFiles);
          }
        } else if (clientCommand.equals("d")) {
          outputToServer.println(clientCommand);
          System.out.print("Enter file name to download: ");
          String fileName = sc.nextLine();
          outputToServer.println(fileName);

          System.out.println("Please select download type");
          System.out.println("1 For Copy\n2 For Zerocopy\n3 For Both type");
          System.out.print("Enter type number: ");
          String type = sc.nextLine();// nextLine() อ่านค่าที่ผู้ใช้พิมพ์เข้ามาทั้งบรรทัดรวมช่องว่างด้วย
          outputToServer.println(type);// ส่ง type ไป server หลังจากนั้น server จะส่งข้อมูลกลับมาเป็น tyepe ข้อมูลกับ
                                       // ข้อมูล

          String messageFromServer = inputFromServer.readLine(); // receive Long/String from server
                                                                 // อ่านข้อมูลบรรทัดแรกที่ server ส่งมา
          long fileSize = 0;// ขนาดไฟล์ที่ได้รับจาก server
          String errorFromServer = "";
          if (messageFromServer.equals("Long")) {// เช็คว่า server ส่งขนาดไฟล์มาให้ใช่ไหม
            fileSize = Long.parseLong(inputFromServer.readLine());
          } else if (messageFromServer.equals("String")) {
            errorFromServer = inputFromServer.readLine(); // ถ้าเป็น String ให้เก็บข้อความ error ที่ server ส่งมา
          }

          if (fileSize > 0) {// เช็คว่าได้ไฟล์มาจริงไหม
            if (type.equals("1")) {// เช็คว่า client เลือกแบบไหน
              long start = System.currentTimeMillis();
              copy(fileName, fileSize);// เรียกใช้เมธอด
              long end = System.currentTimeMillis();
              long time = end - start; // คิดเวลาที่ใช้ดาวน์โหลด
              System.out.println("> Time used " + time + " ms.");
            } else if (type.equals("2")) {
              long start = System.currentTimeMillis();
              zeroCopy(fileName, fileSize);
              long end = System.currentTimeMillis();
              long time = end - start;
              System.out.println("> Time used " + time + " ms.");
            } else if (type.equals("3")) {
              System.out.println("== Copy I/O ==");
              long startCopy = System.currentTimeMillis();
              copy(fileName, fileSize);
              long endCopy = System.currentTimeMillis();
              long timeCopy = endCopy - startCopy;
              System.out.println("> Copy I/O Time used: " + timeCopy + " ms.\n");

              System.out.println("== Zero Copy I/O ==");
              long startZero = System.currentTimeMillis();
              zeroCopy(fileName, fileSize);
              long endZero = System.currentTimeMillis();
              long timeZero = endZero - startZero;
              System.out.println("> Zero Copy Time used: " + timeZero + " ms.\n");
            } else {
              System.out.println("Wrong type, Please try again");
              continue;
            }
          } else {
            System.err.println("Server said: " + errorFromServer);
          }

        } else if (clientCommand.equals("ex")) {
          outputToServer.println(clientCommand);
          close();
        } else {
          System.out.println("Wrong command, Please try again");
        }
      }
    } catch (Exception e) {
      System.out.println("Connection error");
    }
  }

  public void copy(String fileName, long fileSize) {
    FileOutputStream fileToDisk = null;// FileOutputStream คือคลาสที่ใช้สำหรับเขียนข้อมูลแบบ byte ลงไฟล์ในเครื่อง
    try {
      fileToDisk = new FileOutputStream(clientPath + "Copy-" + fileName);// สร้างไฟล์ใหม่ในโฟลเดอร์ปลายทาง
      byte[] buffer = new byte[1024];// กำหนดขนาด buffer สำหรับที่พักการอ่านข้อมูลชั่วคราว(RAM)
                                     // โปรแกรมจะอ่านข้อมูลจาก server ทีละก้อน (chunk) ขนาด 1 KB ไม่กิน RAM มากเกินไป
      int bytesRead;
      long totalBytesRead = 0;// totalBytesRead เก็บจำนวน byte ที่อ่านได้จนถึงตอนนี้

      System.out.println("Start downloading file...");
      while (totalBytesRead < fileSize && (bytesRead = inByteFromServer.read(buffer)) != -1) {
        // เปรียบเทียบ totalBytesRead กับ fileSize เพื่อเช็คว่าอ่านข้อมูลครบหรือยัง และ
        // inByteFromServer.read(buffer) อ่านข้อมูลจาก server ใส่ลงใน buffer
        // ถ้าไม่มีแล้ว จะคืนค่า -1 แล้วหยุด loop

        fileToDisk.write(buffer, 0, bytesRead);// เขียนข้อมูลที่อ่านได้ลงไฟล์ในเครื่อง client
        totalBytesRead += bytesRead;// นับจำนวน byte ที่อ่านได้ในรอบนี้เพิ่มเข้าไปใน totalBytesRead

      }

      if (totalBytesRead != fileSize) {// เช็คว่าอ่านข้อมูลครบตามขนาดไฟล์ที่ server ส่งมาไหม
        System.err
            .println("Warning: File size mismatch. Expected: " + fileSize + ", but got: " + totalBytesRead);// err
                                                                                                            // ขึ้นเตือนเป็นสีแดง
      } else {
        System.out.println("File download completely!");
      }
    } catch (IOException e) {
      e.printStackTrace();// ถ้าทำสั่งข้างบนไม่สำเร็จ print ลำดับการทำงานที่เกิด error ออกมา
    }
  }

  public void zeroCopy(String fileName, long fileSize) {// ระบบปฏิบัติการ(OS) ใช้ buffer ภายในเคอร์เนล(kernel buffer)
                                                        // ให้โดยอัตโนมัติ ใช้ direct memory access (DMA)
                                                        // ช่วยในการโอนถ่ายข้อมูลระหว่างอุปกรณ์เครือข่ายกับหน่วยความจำหลักโดยไม่ต้องผ่าน
                                                        // CPU
    // ส่งข้อมูลจาก SocketChannel → FileChannel โดยตรง
    try (FileChannel destinationFile = new FileOutputStream(clientPath + "Zerocopy-" + fileName).getChannel()) {// สร้าง
                                                                                                                // FileChannel
                                                                                                                // สำหรับเขียนไฟล์ปลายทาง
      // FileOutputStream() เปิดไฟล์ในเครื่อง client เพื่อจะเขียนลง disk
      // .getChannel() เพื่อเข้าถึง os ของไฟล์โดยตรง
      // ใช้ FileChannel เข้าถ้ง์ OS โดยตรง เพื่อเรียกใช้ฟังก์ชัน transferFrom()
      long position = 0;// ตำแหน่งเริ่มต้นในการเขียนไฟล์
                        // ทำให้รู้ว่าตอนนี้เราอ่านหรือส่งข้อมูลไปถึงตรงไหนแล้ว
      long count; // จำนวน byte ที่ถูกโอนถ่ายในแต่ละครั้ง

      System.out.println("Start downloading file...");
      while (position < fileSize
          && (count = destinationFile.transferFrom(clientChannel, position, fileSize - position)) > 0) {
        // ส่งข้อมูลจากไฟล์ sourceFileเริ่มตั้งแต่ byte ที่ position ส่งไปยัง
        // clientChannel โดยส่งได้มากที่สุดเท่ากับ (ขนาดไฟล์ทั้งหมด - ตำแหน่งปัจจุบัน)
        position += count;
      }

      if (position == fileSize) {
        System.out.println("File download completely!");
      } else {// เช็คว่าอ่านข้อมูลครบตามขนาดไฟล์ที่ server ส่งมาไหม
        System.err.println("Warning: Expected file size was " + fileSize + " bytes, but only " + position
            + " bytes were transferred.");
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void close() {
    try {
      if (clientSocket != null && !clientSocket.isClosed()) {// เช็คว่า clientSocket ไม่ใช่ null และยังไม่ถูกปิด
        clientSocket.close();
        System.out.println("Disconnecting server.");
      }
      clientChannel.close();// ปิด SocketChannel สำหรับ zerocopy
      inByteFromServer.close();// ปิด input stream สำหรับรับข้อมูล byte จาก server (ใช้ตอนดาวน์โหลดแบบ Copy)
      outputToServer.close();// ปิด output stream สำหรับส่งข้อมูลไปยัง server
    } catch (IOException e) {
      System.err.println("Error closing client socket: " + e.getMessage());
    }
  }

  public static void main(String[] args) {
    new Client();// เรียกใช้ constructor ของคลาส Client
  }
}

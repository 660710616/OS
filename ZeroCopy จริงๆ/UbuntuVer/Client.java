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
    static String serverIP = "10.0.2.15";
    static final int port = 5001;
    static final int portChannel = 5002;
    static final String clientPath = "/home/tullathorn/Desktop/OS/Client";

    private Socket clientSocket;
    private SocketChannel clientChannel;
    private BufferedReader inputFromServer;
    private PrintWriter outputToServer;
    private BufferedInputStream inByteFromServer;

    public Client() {
        try {
            System.out.println("Client started.");
            clientSocket = new Socket(serverIP, port);
            clientChannel = SocketChannel.open(new InetSocketAddress(serverIP, portChannel));
            System.out.println("Connected server.");
            System.out.println();

            this.inputFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            this.outputToServer = new PrintWriter(clientSocket.getOutputStream(), true);
            this.inByteFromServer = new BufferedInputStream(clientSocket.getInputStream());
            System.out.println("All files in server");
            outputToServer.println("f");
            String allFiles;
            while ((allFiles = inputFromServer.readLine()) != null) {
                if ("EOF".equals(allFiles)) {
                    break;
                }
                System.out.println(allFiles);
            }

            Scanner sc = new Scanner(System.in);
            String clientCommand;
            while (!clientSocket.isClosed()) {
                System.out.println("----------------------------------------");
                System.out.println("--What do you want?--");
                System.out.println("Type \"f\"  for see all file.");
                System.out.println("Type \"d\" for download file.");
                System.out.println("Type \"ex\" for disconnect server.");
                System.out.print("Enter your command: ");
                clientCommand = sc.nextLine();
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
                    String type = sc.nextLine();
                    outputToServer.println(type);
                    String messageFromServer = inputFromServer.readLine();
                    long fileSize = 0;
                    String errorFromServer = "";
                    if (messageFromServer.equals("Long")) {
                        fileSize = Long.parseLong(inputFromServer.readLine());
                    } else if (messageFromServer.equals("String")) {
                        errorFromServer = inputFromServer.readLine();
                    }

                    if (fileSize > 0) {
                        if (type.equals("1")) {
                            long start = System.currentTimeMillis();
                            copy(fileName, fileSize);
                            long end = System.currentTimeMillis();
                            long time = end - start;
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
        FileOutputStream fileToDisk = null;
        try {
            fileToDisk = new FileOutputStream(clientPath + "/Copy-" + fileName);
            byte[] buffer = new byte[1024];
            int bytesRead;
            long totalBytesRead = 0;

            System.out.println("Start downloading file...");
            while (totalBytesRead < fileSize && (bytesRead = inByteFromServer.read(buffer)) != -1) {
                fileToDisk.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
            }

            if (totalBytesRead != fileSize) {
                System.err
                        .println("Warning: File size mismatch. Expected: " + fileSize + ", but got: " + totalBytesRead);
            } else {
                System.out.println("File download completely!");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void zeroCopy(String fileName, long fileSize) {
        try (FileChannel destinationFile = new FileOutputStream(clientPath + "/Zerocopy-" + fileName).getChannel()) {
            long position = 0;
            long count;
            System.out.println("Start downloading file...");
            while (position < fileSize
                    && (count = destinationFile.transferFrom(clientChannel, position, fileSize - position)) > 0) {
                position += count;
            }

            if (position == fileSize) {
                System.out.println("File download completely!");
            } else {
                System.err.println("Warning: Expected file size was " + fileSize + " bytes, but only " + position
                        + " bytes were transferred.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void close() {
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
                System.out.println("Disconnecting server.");
            }
            if (clientChannel != null && clientChannel.isOpen()) {
                clientChannel.close();
            }
            if (inByteFromServer != null) inByteFromServer.close();
            if (outputToServer != null) outputToServer.close();
        } catch (IOException e) {
            System.err.println("Error closing client socket: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        new Client();
    }
}


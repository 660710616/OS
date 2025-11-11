import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.net.InetSocketAddress;
import java.io.File;
import java.io.PrintWriter;

public class Server {
    String IP = "10.0.2.15";
    int myPort = 5001; // copyPort
    int zeroPort = 5002; // zeroport

    ServerSocketChannel serverChannel;
    ServerSocketChannel zeroCopyChannel;
    BufferedReader clientInput;
    PrintWriter toClient;
    SocketChannel clientChannel;
    SocketChannel zeroChannel;
    Socket clientSocket;

    class Handler extends Thread {
        public void run() {
            try {
                serverChannel = ServerSocketChannel.open();
                serverChannel.bind(new InetSocketAddress(IP, myPort));

                zeroCopyChannel = ServerSocketChannel.open();
                zeroCopyChannel.bind(new InetSocketAddress(IP, zeroPort));

                System.out.println("Server started:");
                System.out.println("Copy I/O on " + IP + ":" + myPort);
                System.out.println("Zero-Copy on " + IP + ":" + zeroPort);

                while (true) {
                    System.out.println("Waiting for client on port " + myPort + " (Copy I/O)");
                    System.out.println("Waiting for client on port " + zeroPort + " (Zero-Copy)");
                    clientChannel = serverChannel.accept();
                    System.out.println("Connect with Client (Port for copy I/O).");
                    clientSocket = clientChannel.socket();
                    zeroChannel = zeroCopyChannel.accept();
                    System.out.println("Connect with Client (Port for zero-copy).");

                    toClient = new PrintWriter(clientSocket.getOutputStream(), true);
                    BufferedReader clientInput = new BufferedReader(
                            new InputStreamReader(clientSocket.getInputStream()));

                    String command;
                    try{
                        while ((command = clientInput.readLine()) != null) {
                            if (command.equals("f")) {
                                allFiles();
                            } else if (command.equals("d")) {
                                String fileName = clientInput.readLine();
                                File file = new File("/home/tullathorn/Desktop/OS/Server/" + fileName);
                                String type = clientInput.readLine();

                                if (file.exists()) {
                                    toClient.println("Long");
                                    toClient.println(file.length());
                                    toClient.flush();

                                    if (type.equals("1")) {
                                        copyFile(file, clientSocket);
                                    } else if (type.equals("2")) {
                                        zeroCopyFile(file, zeroChannel);
                                    } else if (type.equals("3")){
                                        copyFile(file, clientSocket);
                                        zeroCopyFile(file, zeroChannel);
                                    }
                                } else {
                                    toClient.println("String");
                                    toClient.println("File does not exist.");
                                }
                            } else if (command.equals("ex")) {
                                System.out.println("Client Disconnected.");
                                break;
                            }
                        }
                    }catch (java.nio.channels.ClosedChannelException e) {
                    
                    }catch (IOException e){
                        e.printStackTrace();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void copyFile(File file, Socket clientSocket) {
        if (clientSocket == null || clientSocket.isClosed()) {
            System.err.println("Client socket is not open.");
            return;
        }

        BufferedInputStream fileInput = null;
        BufferedOutputStream outToClient = null;

        try {
            fileInput = new BufferedInputStream(new FileInputStream(file));
            outToClient = new BufferedOutputStream(clientSocket.getOutputStream());

            byte[] buffer = new byte[1024];
            int bytesRead;
            System.out.println("Start sending file (Copy I/O)..." + file.getName());

            while ((bytesRead = fileInput.read(buffer)) != -1) {
                outToClient.write(buffer, 0, bytesRead);
            }
            outToClient.flush();
            System.out.println("File sent successfully (Copy I/O).");
        } catch (IOException e) {
            e.printStackTrace();
        } 
    }

    private void zeroCopyFile(File file, SocketChannel clientChannel) {
        if (clientChannel == null || !clientChannel.isOpen()) {
            System.err.println("Client channel is not open.");
            return;
        }

        try (FileChannel sourceFile = new FileInputStream(file).getChannel()) {
            long position = 0;
            long fileSize = sourceFile.size();
            long transferred;
            System.out.println("Start sending file (Zero-Copy)");

            while (position < fileSize) {
                transferred = sourceFile.transferTo(position, fileSize - position, clientChannel);
                if (transferred <= 0)
                    break; 
                position += transferred;
            }

            if (position == fileSize) {
                System.out.println("File sent successfully (Zero-Copy).");
            } else {
                System.err.println("Warning: Not all bytes transferred. Expected: "
                        + fileSize + ", Transferred: " + position);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void allFiles() {
        File file = new File("/home/tullathorn/Desktop/OS/Server");
        File[] folder = file.listFiles();
        String files = "";
        double size = 0;
        String fs = "B";
        int count = 1;

        if (folder != null) {
            for (File x : folder) {
                size = x.length();
                if (size > 1000000) {
                    fs = "MB";
                    size = size / (1024 * 1024);
                } else if (size > 1000) {
                    fs = "KB";
                    size = size / 1024;
                }
                String sizeF = String.format("% .2f", size);
                files += count + ". File name: " + x.getName() + "| File size: " + sizeF + " " + fs + "\n";
                count++;
            }
            count = 1;
            toClient.println(files);
            toClient.println("EOF");
        } else {
            System.out.println("There are no file in the server.");
        }
    }

    public static void main(String[] args) {
        Server s = new Server();
        s.new Handler().start();
    }
}


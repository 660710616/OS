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
import java.net.ServerSocket;
import java.io.File;
import java.io.PrintWriter;

public class Server {
    String IP = "192.168.1.115";
    int myPort = 5001; //copyPort
    int zeroPort = 5002; //zeroport

    ServerSocket serverSocket;
    ServerSocketChannel serverChannel;
    ServerSocketChannel zeroCopyChannel;
    BufferedReader clientInput;
    PrintWriter toClient;
    SocketChannel clientChannel;
    SocketChannel zeroChannel;
    Socket clientSocket;

    class Handler extends Thread {
        public void run(){
            String command;
            try {
                serverChannel = ServerSocketChannel.open();
                serverChannel.bind(new InetSocketAddress(IP, myPort));

                zeroCopyChannel = ServerSocketChannel.open();
                zeroCopyChannel.bind(new InetSocketAddress(IP, zeroPort));

                System.out.println("Server started:");
                System.out.println("  Copy I/O on " + IP + ":" + myPort);
                System.out.println("  Zero-Copy on " + IP + ":" + zeroPort);

                while (true) {
                    clientChannel = serverChannel.accept();
                    clientSocket = clientChannel.socket();
                    
                    toClient = new PrintWriter(clientSocket.getOutputStream(), true);
                    BufferedReader clientInput = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    
                    command = clientInput.readLine();
                    if(command.equals("f")){
                        allFiles();
                    }
                    else if(command.equals("d")){
                        String fileName = clientInput.readLine();
                        File file = new File("C:\\Users\\CYBORG 15\\Desktop\\SU\\year3\\OS\\file\\" + fileName);  //เปลี่ยน path
                        String type = clientInput.readLine();

                        if(file.exists()){
                            toClient.println("Long");
                            toClient.println(file.length());

                            if(type.equals("1")){
                                copyFile(file, clientSocket);
                            }
                            else if(type.equals("2")){
                                System.out.println("Waiting for Zero-Copy client on port " + zeroPort);
                                zeroChannel = zeroCopyChannel.accept();
                                zeroCopyFile(file, zeroChannel);
                                zeroChannel.close();
                            }
                        }
                        else{
                            toClient.println("String");
                            toClient.println("File does not exist.");
                        }
                    }
                    else if(command.equals("ex")){
                        System.out.println("Client Disconnected.");
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

        try (
            BufferedInputStream fileInput = new BufferedInputStream(new FileInputStream(file));
            BufferedOutputStream outToClient = new BufferedOutputStream(clientSocket.getOutputStream());
        ) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            System.out.println("Start sending file (Copy I/O)..." + file.getName());

            while ((bytesRead = fileInput.read(buffer)) != -1) {//-1 means end of file
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
                if (transferred <= 0) break; // ป้องกัน loop ค้าง
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

    private void allFiles(){
        File file = new File("C:\\Users\\CYBORG 15\\Desktop\\SU\\year3\\OS\\file");
        File[] folder = file.listFiles();
        String files = "";
        double size = 0;
        String fs = "B";
        int count = 1;

        if(folder != null){
            for(File x : folder){
                size = x.length();
                if(size > 1000000){
                    fs = "MB";
                    size = size / (1024*1024);
                }
                else if(size > 1000){
                    fs = "KB";
                    size = size / 1024;
                }
                files += count + "File name: " + x.getName() + " File size: " + size + " " + fs + "\n";
                count++;
            }
            count = 1;
            toClient.println(files);
            toClient.println("EOF");
        }
        else{
            System.out.println("There are no in the server.");
        }
    }

    public static void main(String[] args) {
         new Server();  
    }
}

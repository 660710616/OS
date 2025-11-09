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

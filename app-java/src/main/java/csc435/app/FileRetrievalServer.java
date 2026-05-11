package csc435.app;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;
import java.util.Scanner;

public class FileRetrievalServer {
    private Server server;

    public void start(int port) throws IOException {
        server = ServerBuilder.forPort(port)
                .addService(new ServerProcessingEngine())
                .build()
                .start();
        System.out.print("> ");
    }

    public void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 1) {
            System.err.println("Usage: java -cp target/app-java-1.0-SNAPSHOT.jar csc435.app.FileRetrievalServer <port>");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);
        FileRetrievalServer serverApp = new FileRetrievalServer();
        serverApp.start(port);

        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNextLine()) {
            String command = scanner.nextLine().trim();
            if ("quit".equalsIgnoreCase(command)) {
                serverApp.stop();
                break;
            }
            System.out.print("> ");
        }
        scanner.close();
        serverApp.blockUntilShutdown();
    }
}
import java.net.*;
import java.io.*;
import java.util.*;

class Server{

    public static void main(String[] args){

        int port = 8080;
        String wwwhome = System.getProperty("user.dir");

        // open server socket
        ServerSocket socket = null;
        try {
            socket = new ServerSocket(port);
        } catch (IOException e) {
            System.err.println("Could not start server: " + e);
            System.exit(-1);
        }

		System.out.println("FileServer accepting connections on port " + port);
		int id=0;
        while (true) {
            Socket connection = null;
            // wait for request
			try{
				connection = socket.accept();
				ClientServiceThread cliThread = new ClientServiceThread(connection, id++, port, wwwhome);
	            cliThread.start();
			} catch (Exception e) {
            	System.out.println(e.getMessage());
        	}
        }
    }
}

class ClientServiceThread extends Thread {
	Socket connection;
	int clientID = -1;
	int port;
	String wwwhome;

	ClientServiceThread(Socket s, int i, int p, String w) {
		connection = s;
		clientID = i;
		port = p;
		wwwhome = w;
	}

	public void run() {
		try{
			System.out.println("Cliente en línea, ID: "+clientID);
			BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			OutputStream out = new BufferedOutputStream(connection.getOutputStream());
			PrintStream pout = new PrintStream(out);

			// read first line of request (ignore the rest)
			String request = in.readLine();

			// parse the line
			if (!request.startsWith("GET") || request.length()<14 ||
				!(request.endsWith("HTTP/1.0") || request.endsWith("HTTP/1.1"))) {
				// bad request
				errorReport(pout, connection, "400", "Bad Request",
							"Your browser sent a request that " +
							"this server could not understand.");
			} else {
				String req = request.substring(4, request.length()-9).trim();

				if (req.indexOf("secret")!=-1 ) {
					// evil hacker trying to read non-wwwhome or secret file
					errorReport(pout, connection, "403", "Forbidden",
								"You don't have permission to access the requested URL.");
				} else {
					String path = wwwhome + req;
					File f = new File(path);
					System.out.println("path "+path);
					System.out.println("f "+f);

					if (path.indexOf("home_old") != -1) {
						// redirect browser if referring to directory without final '/'
						pout.print("HTTP/1.0 302 Moved Permanently\r\n" +
								   "Location: http://" +
								   connection.getLocalAddress().getHostAddress() + ":" +
								   connection.getLocalPort() + "/\r\n\r\n");
						log(connection, "301 Moved Permanently");
					} else {
						if (f.isDirectory()) {
							// if directory, implicitly add 'home.html'
							path = path + "home.html";
							f = new File(path);
						}
						try {
							// send file
							InputStream file = new FileInputStream(f);
							pout.print("HTTP/1.0 200 OK\r\n" +
									   "Content-Type: " + guessContentType(path) + "\r\n" +
									   "Date: " + new Date() + "\r\n" +
									   "Server: FileServer 1.0\r\n\r\n");
							sendFile(file, out); // send raw file
							log(connection, "200 OK");
						} catch (FileNotFoundException e) {
							// file not found
							errorReport(pout, connection, "404", "Not Found",
										"The requested URL was not found on this server.");
						}
					}
				}
			}
			out.flush();
		} catch (IOException e) { System.err.println(e); }
	}

	private static void log(Socket connection, String msg){
        System.err.println(new Date() + " [" + connection.getInetAddress().getHostAddress() +
            ":" + connection.getPort() + "] " + msg);
    }

    private static void errorReport(PrintStream pout, Socket connection,
                                    String code, String title, String msg)
    {
        pout.print("HTTP/1.0 " + code + " " + title + "\r\n" +
                   "\r\n" +
                   "<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">\r\n" +
                   "<TITLE>" + code + " " + title + "</TITLE>\r\n" +
                   "</HEAD><BODY>\r\n" +
                   "<H1>" + title + "</H1>\r\n" + msg + "<P>\r\n" +
                   "<HR><ADDRESS>FileServer 1.0 at " +
                   connection.getLocalAddress().getHostName() +
                   " Port " + connection.getLocalPort() + "</ADDRESS>\r\n" +
                   "</BODY></HTML>\r\n");
        log(connection, code + " " + title);
    }

    private static String guessContentType(String path)
    {
        if (path.endsWith(".html") || path.endsWith(".htm"))
            return "text/html";
        else if (path.endsWith(".txt") || path.endsWith(".java"))
            return "text/plain";
        else if (path.endsWith(".gif"))
            return "image/gif";
        else if (path.endsWith(".class"))
            return "application/octet-stream";
        else if (path.endsWith(".jpg") || path.endsWith(".jpeg"))
            return "image/jpeg";
        else
            return "text/html";
    }

    private static void sendFile(InputStream file, OutputStream out)
    {
        try {
            byte[] buffer = new byte[1000];
            while (file.available()>0)
                out.write(buffer, 0, file.read(buffer));
        } catch (IOException e) { System.err.println(e); }
    }
}

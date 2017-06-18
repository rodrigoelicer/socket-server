import java.net.*;
import java.io.*;
import java.util.*;

class Server{

    public static void main(String[] args){

        int port = 8080;
        String wwwhome = System.getProperty("user.dir");

        ServerSocket socket = null;
        try {
			//Crea el socket en el puerto 8080
            socket = new ServerSocket(port);
        } catch (IOException e) {
            System.err.println("Could not start server: " + e);
            System.exit(-1);
        }
		System.out.println("FileServer accepting connections on port " + port);

		int id=0;
        while (true){
			//Por cada conexion, se crea un thread distinto
            Socket connection = null;
			try{
				connection = socket.accept();
				ClientServiceThread cliThread = new ClientServiceThread(connection, id++, port, wwwhome);
	            cliThread.start();
			}
			catch (Exception e) {
            	System.out.println(e.getMessage());
        	}
        }
    }
}

class ClientServiceThread extends Thread{
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

	public void run(){
		try{
			System.out.println("Conexion nueva, ID: "+clientID);
			BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			OutputStream out = new BufferedOutputStream(connection.getOutputStream());
			PrintStream pout = new PrintStream(out);

			//Formato -> 	GET / HTTP/1.1
			//				POST /form_submited.html HTTP/1.1
			String request = in.readLine();

			boolean isPost = request.startsWith("POST");
			int contentLength = 0;
			String line;
			while (!(line = in.readLine()).equals("")) {
		        if (isPost) {
		            final String contentHeader = "Content-Length: ";
		            if (line.startsWith(contentHeader)) {
		                contentLength = Integer.parseInt(line.substring(contentHeader.length()));
		            }
		        }
		    }

			StringBuilder body = new StringBuilder();
			if (isPost) {
				int c = 0;
				for (int i = 0; i < contentLength; i++) {
				   c = in.read();
				   body.append((char) c);
				}
			}
			//body -> user='user'&pass='pass'

			String req = request.substring(4, request.length()-9).trim();
			if (request.length()<14 || !(request.endsWith("HTTP/1.0") || request.endsWith("HTTP/1.1"))) {
				//Bad request
				errorReport(pout, connection, "400", "Bad Request",
							"Your browser sent a request that " +
							"this server could not understand.");
			}
			else if(request.startsWith("GET")) {
				if (req.indexOf("secret")!=-1 ) {
					//403
					errorReport(pout, connection, "403", "Forbidden",
								"You don't have permission to access the requested URL.");
				}
				else {
					String path = wwwhome + req;
					File f = new File(path);
					System.out.println("f: "+f);
					if (path.indexOf("home_old") != -1) {
						//Redirige a home.html
						pout.print("HTTP/1.0 302 Moved Permanently\r\n" +
								   "Location: http://" +
								   connection.getLocalAddress().getHostAddress() + ":" +
								   connection.getLocalPort() + "/\r\n\r\n");
						log(connection, "301 Moved Permanently");
					}
					else {
						if (f.isDirectory()) {
							//Pagina inicial al haber solamente "/"
							path = path + "home";
							System.out.println("path "+path);
							f = new File(path);
						}
						try {
							path = path + ".html";
							f = new File(path);
							//Envía archivo html
							InputStream file = new FileInputStream(f);
							pout.print("HTTP/1.0 200 OK\r\n" +
									   "Content-Type: text/html\r\n" +
									   "Date: " + new Date() + "\r\n" +
									   "Server: FileServer 1.0\r\n\r\n");
							sendFile(file, out); //Envía archivo
							log(connection, "200 OK");
						} catch (FileNotFoundException e) {
							//404 no se encuentra
							errorReport(pout, connection, "404", "Not Found",
										"The requested URL was not found on this server.");
						}
					}
				}
			}
			else if(request.startsWith("POST")){
				String path = wwwhome + req + ".html",
					user = "root",
					pass = "rdc2017";
				System.out.println("path "+path);

				File f = new File(path);
				File badPass = new File(wwwhome + "/fail.html");

				try {
					//Envía archivo html
					InputStream bad = new FileInputStream(badPass);
					InputStream file = new FileInputStream(f);
					pout.print("HTTP/1.0 200 OK\r\n" +
							   "Content-Type: text/html\r\n" +
							   "Date: " + new Date() + "\r\n" +
							   "Server: FileServer 1.0\r\n\r\n");
					if(body.toString().equals("user="+user+"&pass="+pass)){
						sendFile(file, out); //Envía archivo
					}
					else{
						sendFile(bad,out);
					}

					log(connection, "200 OK");
				} catch (FileNotFoundException e) {
					//404 no se encuentra
					errorReport(pout, connection, "404", "Not Found",
								"The requested URL was not found on this server.");
				}
			}

			//Se cierran debidamente el socket y PrintStream
			pout.close();
			connection.close();
		} catch (IOException e) { System.err.println(e); }
	}

	private static void log(Socket connection, String msg){
        System.err.println(new Date() + " [" + connection.getInetAddress().getHostAddress() +
            ":" + connection.getPort() + "] " + msg);
    }

    private static void errorReport(PrintStream pout, Socket connection,
	String code, String title, String msg){
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

    private static void sendFile(InputStream file, OutputStream out){
        try {
            byte[] buffer = new byte[1000];
            while (file.available()>0)
                out.write(buffer, 0, file.read(buffer));
        } catch (IOException e) { System.err.println(e); }
    }
}

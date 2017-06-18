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
		System.out.println("Server accepting connections on port " + port);

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

//--------------------------------
//Se crean los distintos thread por cada conexion nueva
//--------------------------------
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

			//Formato -> 	GET /home HTTP/1.1
			//				POST /login HTTP/1.1
			String request = in.readLine();
			//Formato ->	/login
			String req = request.substring(4, request.length()-9).trim();
			req = req.replace("?","");

			//--------------------------------
			//BONUS
			//--------------------------------
			//Se crea log.txt
			File log = new File(wwwhome + "/log.txt");
			log.createNewFile();

			BufferedWriter bw = null;
			FileWriter fw = null;

			try {
				InetAddress ip = connection.getInetAddress();
				String url = connection.getLocalAddress().getHostName(),
						port = Integer.toString(connection.getLocalPort()),
						url_final;
				url_final = url+":"+port+req;

				String content = "<"+ip+"> <"+url_final+"> <"+new Date()+">\n";

				fw = new FileWriter("log.txt",true);
				bw = new BufferedWriter(fw);
				bw.write(content);

				System.out.println("Done");
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				try {
					if (bw != null)
						bw.close();
					if (fw != null)
						fw.close();
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}

			//Se agrega /template a la direccion del proyecto para redirigir bien a los .html
			wwwhome = wwwhome + "/template";
			//--------------------------------
			//En "body" almacena la data pasada en POST
			//--------------------------------
			StringBuilder body = new StringBuilder();
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
			if (isPost) {
				int c = 0;
				for (int i = 0; i < contentLength; i++) {
				   c = in.read();
				   body.append((char) c);
				}
			}
			//body -> user='user'&pass='pass'

			//--------------------------------
			//Maneja las solicitudes tipo GET
			//Maneja 403,301, 200 y 404
			//--------------------------------
			if(request.startsWith("GET")) {
				String path1 = wwwhome + "/verified";
				boolean isCheck = new File(path1).exists();
				if (req.indexOf("secret")!=-1 && !isCheck) {
					//403
					errorReport(pout, connection, "403", "Forbidden",
								"You don't have permission to access the requested URL.");
				}
				else {
					String path = wwwhome + req;
					File f = new File(path);
					if (path.indexOf("home_old") != -1) {
						//Redirige a home.html
						pout.print("HTTP/1.0 301 Moved Permanently\r\n" +
								   "Location: http://" +
								   connection.getLocalAddress().getHostAddress() + ":" +
								   connection.getLocalPort() + "/\r\n\r\n");
						log("301 Moved Permanently");
					}
					else {
						if (f.isDirectory()) {
							//Pagina inicial al haber solamente "/"
							path = path + "home";
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
									   "Server: Server 1.0\r\n\r\n");
							sendFile(file, out); //Envía archivo
							log("200 OK");
						} catch (FileNotFoundException e) {
							//404 no se encuentra
							errorReport(pout, connection, "404", "Not Found",
										"The requested URL was not found on this server.");
						}
					}
				}
			}
			//--------------------------------
			//Maneja las solicitudes tipo POST
			//Maneja 200 y 404
			//--------------------------------
			else if(request.startsWith("POST")){
				String path = wwwhome + req + ".html",
					user = "root",
					pass = "rdc2017";

				File f = new File(path);
				File badPass = new File(wwwhome + "/fail.html");

				try {
					//Envía archivo html
					InputStream bad = new FileInputStream(badPass);
					InputStream file = new FileInputStream(f);
					pout.print("HTTP/1.0 200 OK\r\n" +
							   "Content-Type: text/html\r\n" +
							   "Date: " + new Date() + "\r\n" +
							   "Server: Server 1.0\r\n\r\n");
					if(body.toString().equals("user="+user+"&pass="+pass)){
						File verified = new File(wwwhome + "/verified");
						verified.createNewFile();
						sendFile(file, out); //Envía archivo
					}
					else{
						sendFile(bad,out);
					}

					log("200 OK");
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

	private static void log(String msg){
        System.err.println(msg);
    }

	//--------------------------------
	//genera html para errores 404 y 403
	//--------------------------------
    private static void errorReport(PrintStream pout, Socket connection,
	String code, String title, String msg){
        pout.print("HTTP/1.0 " + code + " " + title + "\r\n" +
                   "\r\n" +
                   "<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">\r\n" +
                   "<TITLE>" + code + " " + title + "</TITLE>\r\n" +
                   "</HEAD><BODY>\r\n" +
                   "<H1>" + title + "</H1>\r\n" + msg + "<P>\r\n" +
                   "<HR><ADDRESS>Server 1.0 at " +
                   connection.getLocalAddress().getHostName() +
                   " Port " + connection.getLocalPort() + "</ADDRESS>\r\n" +
                   "</BODY></HTML>\r\n");
        log(code + " " + title);
    }

	//--------------------------------
	//Funcion para enviar archivos
	//--------------------------------
    private static void sendFile(InputStream file, OutputStream out){
        try {
            byte[] buffer = new byte[1000];
            while (file.available()>0)
                out.write(buffer, 0, file.read(buffer));
        } catch (IOException e) { System.err.println(e); }
    }
}

package de.tlabs.thinkAir.dirService;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.UUID;

import org.jason.lxcoff.lib.ControlMessages;

public class PhoneHandler implements Runnable {

	//phone-client connect socket
	private Socket					clientSocket;
	private InputStream				is;
	private OutputStream			os;
	private ObjectOutputStream 		oos;
	private ObjectInputStream 		ois;
	
	//lxc connect socket
	private Socket 					conSocket;
	private InputStream				conis;
	private OutputStream			conos;
	private ObjectOutputStream		conoos;
	private ObjectInputStream		conois;
	
	private String					phoneID;
	static 	String 					appName;						// the app name sent by the phone	
	static	String 					apkFilePath;					// the path where the apk is installed
	static 	String 					objToExecute = null;	// the object to be executed sent by the phone
	static 	String 					methodName;						// the method to be executed
	static 	Class<?>[] 				pTypes;							// the types of the parameters passed to the method
	static 	Object[] 				pValues;						// the values of the parameteres to be passed to the method
	
	private Container				worker_container = null;
	
	private final int 				BUFFER = 8192;
	
	private DBHelper				dbh;
	private static String			logFileName = null;
	private static FileWriter 		logFileWriter = null;
	private String 					RequestLog = null;

	public PhoneHandler(Socket clientSocket, InputStream is, OutputStream os, DBHelper dbh) {
		this.clientSocket 	= clientSocket;
		this.is				= is;
		this.os				= os;
		this.dbh			= dbh;
		this.logFileName = "/root/dirservice/ExecRecord/execrecord.txt"; 
		File needlog = new File("/root/dirservice/ExecRecord/needlog");
		if(needlog.exists()){
			try {
				File logFile = new File(logFileName);
				logFile.createNewFile(); // Try creating new, if doesn't exist
				logFileWriter = new FileWriter(logFile, true);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	@Override
	public void run() {

		System.out.println("Waiting for commands from the phone...");

		int command = 0;

		try{
			ois = new ObjectInputStream(is);
			oos = new ObjectOutputStream(os);
			long startTime , dura;
			boolean connected;
			HashMap<String, String> result;

			while (command != -1) {

				command = is.read();
				System.out.println("Command: " + command);

				switch(command) {

				case ControlMessages.PHONE_AUTHENTICATION:

					// Read the ID of the requesting phone
					this.phoneID = (String)ois.readObject();
					System.out.println(this.phoneID);
					break;
					
				case ControlMessages.PING:
					System.out.println("Reply to PING");
					os.write(ControlMessages.PONG);
					break;
					
				case ControlMessages.APK_REGISTER:
					appName = (String) ois.readObject();
					apkFilePath = ControlMessages.DIRSERVICE_APK_DIR + appName + ".apk";
					if (apkPresent(apkFilePath)) {
						System.out.println("APK present " + appName);
						os.write(ControlMessages.APK_PRESENT);
					} else {
						startTime = System.nanoTime();
						System.out.println("request APK " + appName);
						os.write(ControlMessages.APK_REQUEST);
						// Receive the apk file from the client
						// 5 times just for testing
						for(int i=0; i<5; i++){
							System.out.println("Receving APK file for no. "+i+" time");
							receiveApk(ois, apkFilePath);
						}
						
						System.out.println("received APK");
						dura = System.nanoTime() - startTime;
						System.out.println("Transfering apk cost " + dura/1000000 + " ms.");
					}
					/*File dexFile = new File(apkFilePath);
					libraries = addLibraries(dexFile);
					objIn.addDex(dexFile);*/

					break;
					
				case ControlMessages.PHONE_COMPUTATION_REQUEST:					
					System.out.println("Execute request");
					
					connected = false;
					
					startTime = System.nanoTime();
					//when the old worker_container can't work now, we need find a new available container
					if(this.worker_container == null || this.worker_container.getStatus() != ContainerState.RESUMED){
						do{
							this.worker_container = findAvailableContainer();
							
							boolean preres = worker_container.prepareContainer();
							
							//while starting the container,we should wait for container to connect
							if(preres){
								connected = waitForContainerToAuthenticate(worker_container);
							}
							
						}while(!connected);
					}
					//otherwise we can still use the old container
					else{
						do{
							connected = waitForContainerToAuthenticate(worker_container);
						}while(!connected);
					}
					
					dura = System.nanoTime() - startTime;
					
					//资源准备
					this.RequestLog += dura/1000000 + " ";
					
					startTime = System.nanoTime();
					//receive the object from phone-client ois and repost the request to container
					result = (HashMap<String, String>) receiveAndRepost(ois, this.worker_container);
					
					releaseContainer(this.worker_container);
					try {
						// Send back over the socket connection
						System.out.println("Sending result back");
						System.out.println("Send retType is " + result.get("retType"));
						this.oos.writeObject(result.get("retType"));
						System.out.println("Send retVal is " + result.get("retVal"));
						this.oos.writeObject(result.get("retVal"));
						// Clear ObjectOutputCache - Java caching unsuitable
						// in this case
						this.oos.flush();
						//this.oos.reset();

						System.out.println("Result successfully sent");
					} catch (IOException e) {
						System.out.println("Connection failed when sending result back");
						e.printStackTrace();
						return;
					}
					dura = System.nanoTime()-startTime;
					//请求执行 + apk传输
					this.RequestLog += dura/1000000;
					this.traceLog(this.RequestLog);
					this.RequestLog = "";

					break;
					
				case ControlMessages.PHONE_COMPUTATION_REQUEST_WITH_FILE:					
					System.out.println("Execute request with file");
					
					connected = false;
					
					startTime = System.nanoTime();
					//when the old worker_container can't work now, we need find a new available container
					if(this.worker_container == null || this.worker_container.getStatus() != ContainerState.RESUMED){
						do{
							this.worker_container = findAvailableContainer();
							
							boolean preres = worker_container.prepareContainer();
							
							//while starting the container,we should wait for container to connect
							if(preres){
								connected = waitForContainerToAuthenticate(worker_container);
							}
							
						}while(!connected);
					}//otherwise we can still use the old container
					else{
						do{
							connected = waitForContainerToAuthenticate(worker_container);
						}while(!connected);
					}
					dura = System.nanoTime() - startTime;
					
					//资源准备
					this.RequestLog += dura/1000000 + " ";
					
					//开始文件传输
					startTime = System.nanoTime();
					System.out.println("The offloading need to send file first");
					String filePath = (String) ois.readObject();
					String fileName = filePath.substring(filePath.lastIndexOf("/")+1);
					filePath = "/root/system/off-app/off-file/" + fileName;
					//Actually we should always request the file.
					System.out.println("request File " + filePath);
					os.write(ControlMessages.SEND_FILE_REQUEST);
					// Receive the apk file from the client
					receiveApk(ois, filePath);
					
					dura = System.nanoTime() - startTime;
					
					//send file时间
					this.RequestLog += dura / 1000000 + " ";
					
					startTime = System.nanoTime();
					result = (HashMap<String, String>) receiveAndRepost(ois, this.worker_container);
					
					releaseContainer(this.worker_container);
					try {
						// Send back over the socket connection
						System.out.println("Send result back");
						this.oos.writeObject(result.get("retType"));
						this.oos.writeObject(result.get("retVal"));
						// Clear ObjectOutputCache - Java caching unsuitable
						// in this case
						this.oos.flush();
						//this.oos.reset();

						System.out.println("Result successfully sent");
					} catch (IOException e) {
						System.out.println("Connection failed when sending result back");
						e.printStackTrace();
						return;
					}
					
					dura = System.nanoTime()-startTime;
					//请求执行 + apk传输
					this.RequestLog += dura/1000000;
					this.traceLog(this.RequestLog);
					this.RequestLog = "";

					break;
				
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e){
			e.printStackTrace();
		} finally {
			try {
				oos.close();
				ois.close();
				clientSocket.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	private boolean apkPresent(String filename) {
		// return false;
		// TODO: more sophisticated checking for existence
		File apkFile = new File(filename);
		return apkFile.exists();
	}
	
	/**
	 * Method to retrieve an apk of an application that needs to be executed
	 * 
	 * @param objIn
	 *            Object input stream to simplify retrieval of data
	 * @return the file where the apk package is stored
	 * @throws IOException
	 *             throw up an exception thrown if socket fails
	 */
	private File receiveApk(ObjectInputStream objIn, String apkFilePath)
			throws IOException {
		// Receiving the apk file
		// Get the length of the file receiving
		int apkLen = objIn.readInt();
		System.out.println("Read apk len - " + apkLen);

		// Get the apk file
		byte[] tempArray = new byte[apkLen];
		System.out.println("Read apk");
		objIn.readFully(tempArray);

		// Write it to the filesystem
		File dexFile = new File(apkFilePath);
		FileOutputStream fout = new FileOutputStream(dexFile);

		BufferedOutputStream bout = new BufferedOutputStream(fout, BUFFER);
		bout.write(tempArray);
		bout.close();
		
		

		return dexFile;
	}
	
	/**
	 * Reads in the object to execute an operation on, name of the method to be
	 * executed and repost it
	 */
	private Object receiveAndRepost(ObjectInputStream objIn,
			Container container) {
		// Read the object in for execution
		System.out.println("Read Object");
		try {
			// Get the object
			String className = (String) objIn.readObject();
			
			objToExecute = (String) objIn.readObject();

			System.out.println("Read Method");
			// Read the name of the method to be executed
			methodName = (String) objIn.readObject();
			
			Object tempTypes = objIn.readObject();
			String[] pType = (String[]) tempTypes;
			
			String pValuestr = (String) objIn.readObject();
			
			System.out.println("Repost Method " + methodName);
			
			//long starttime = System.nanoTime();
			
			this.conos.write(ControlMessages.PHONE_COMPUTATION_REQUEST);
			
			this.conoos.writeObject(appName);
			
			//see if the runtime needs apk. In container case, this will always be no need.
//			int needApk = this.conis.read();
/*			if(needApk == ControlMessages.APK_REQUEST || true){
				sendApk("/root/cloneroot/off-app/org.witness.sscphase1.apk", this.conoos);
			}*/
//			this.conoos.reset();
			this.conoos.writeObject(className);
			
			this.conoos.writeObject(objToExecute);

			// Send the method to be executed
			// Log.d(TAG, "Write Method - " + m.getName());
			this.conoos.writeObject(methodName);

			// Log.d(TAG, "Write method parameter types");
			//this.conoos.writeObject(pTypes);
			this.conoos.writeObject(pType);

			// Log.d(TAG, "Write method parameter values");
			//this.conoos.writeObject(pValues);
			this.conoos.writeObject(pValuestr);
			this.conoos.flush();
			
			//waiting to retrieve result from container
			System.out.println("Reading result from container");
			
			HashMap<String ,String> result = new HashMap<String ,String>(); 
			String retType = (String) this.conois.readObject();
			result.put("retType", retType);
			String response = (String) this.conois.readObject();
			result.put("retVal", response);
			
			//long dura = System.nanoTime() - starttime;
			//Record repost and execution time, we need to minus the execution time(from the server) to get the request transfer time 
			//this.RequestLog += " " + dura /1000000; 
			return result;

		} catch (Exception e) {
			// catch and return any exception since we do not know how to handle
			// them on the server side
			e.printStackTrace();
			//return new ResultContainer(null, e, null);
			return null;
		}

	}
	
	private boolean waitForContainerToAuthenticate(Container container) {
		Long stime = System.nanoTime();
		while(true){
			try{
				this.conSocket  = new Socket();
				System.out.println("Connecting worker container ...");
				conSocket.connect(new InetSocketAddress(container.getIp(), container.getPortForDir()), 0);
				
				Long dura = (System.nanoTime()-stime);
				System.out.println("Connected worker container in " + dura/1000000 + "ms");
				
				this.worker_container.setStatus(ContainerState.ASSIGNED_TO_PHONE);
				this.conis = this.conSocket.getInputStream();
				this.conos = this.conSocket.getOutputStream();
				this.conois = new ObjectInputStream(this.conis);
				this.conoos = new ObjectOutputStream(this.conos);
				
				return true;
			}catch(ConnectException e){
				try {
					Thread.sleep(100);
				} catch (InterruptedException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}catch(IOException e){
				e.printStackTrace();
				try {
					this.conSocket.close();
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				return false;
			}
		}
		
	}
	
	private boolean waitForCloneToAuthenticate() {
		this.conSocket  = new Socket();
		try{
			System.out.println("Connecting worker container ...");
			conSocket.connect(new InetSocketAddress("192.168.155.3", 4322), 0);
			
			System.out.println("Connected to worker container");
			
			//this.worker_container.setStatus(ContainerState.ASSIGNED_TO_PHONE);
			this.conis = this.conSocket.getInputStream();
			this.conos = this.conSocket.getOutputStream();

			this.conoos = new ObjectOutputStream(this.conos);
			this.conoos.flush();
			this.conois = new ObjectInputStream(this.conis);
			
			return true;
			
		}catch(IOException e){
			e.printStackTrace();
			return false;
		}
	}

	private Container findAvailableContainer() {
		try{
			dbh.dbUpdate("lock tables lxc write"); 
			String sql = "select * from lxc where status in (2,1,0) order by status desc limit 1 ";
			ResultSet rs = dbh.dbSelect(sql);
			
			if(rs.next()){
				String name = rs.getString("name");
				String ip = rs.getString("ip");
				int status = rs.getInt("status");
				int mem = rs.getInt("mem");
				String cpuset = rs.getString("cpuset");
				int cpushare = rs.getInt("cpushare");
				//only update status in database so that nobody will choose this lxc anymore, 
				//while the container object has old status for prepareContainer deciding what operation to do
				dbh.dbUpdate("update lxc set status = " + ContainerState.ASSIGNING + " where name = '" + name + "'"); 
				dbh.dbUpdate("unlock tables");
				
				if(ip == null){
					ip = getAvailableIp();
					dbh.dbUpdate("update lxc set ip = '" + ip + "' where name = '" + name + "'");
				}
				
				Container con = new Container(name, ip, status, mem, cpuset, cpushare);
				return con;
			}else{
				dbh.dbUpdate("unlock tables");
				Container con = startNewContainer();
				return con;
				
			}
		} catch (SQLException e) {
            e.printStackTrace();
        } 
		return null;
		
	}
	
	/**
	 * Start the first available clone scanning the amazon clones first and virtualbox clones second.
	 * @return
	 */
	private Container startNewContainer() {
		try{
			//find available ip for new container
			String name = UUID.randomUUID().toString();
			String ip = getAvailableIp();
			if(ip == null)
				return null;
			
			Container con = new Container(name, ip, ContainerState.CREATING);
			
			String sql = "insert into lxc (name, ip, status) values ('" + name + "','" + ip + "', " + ContainerState.CREATING +")";

			boolean rs = dbh.dbUpdate(sql);
			
			if(rs){
				return con;
			}
			
		} catch (Exception e) {
            e.printStackTrace();
        } 
		
		return null;

	}
	
	private String getAvailableIp(){
		String ip = null;
		try{
			//find available ip for container
			dbh.dbUpdate("lock tables ip write"); 
			ResultSet rs = dbh.dbSelect("select ip from ip where inuse=0 limit 1");
			if(rs.next()){
				ip = rs.getString("ip");
			}
			
			if(ip != null){
				dbh.dbUpdate("update ip set inuse=1 where ip='" + ip + "'");
			}
			
			dbh.dbUpdate("unlock tables");
			return ip;
		} catch (SQLException e) {
            e.printStackTrace();
        } 
		
		return null;
		
	}
	
	private void releaseContainer(Container con){
		try{
			this.conoos.close();
			this.conois.close();
			this.conos.close();
			this.conis.close();
		}catch(IOException e){
			e.printStackTrace();
		}
		
		con.setStatus(ContainerState.RESUMED);
		
	}
	
	private void traceLog(String log){
		if (logFileWriter != null) {
			try {
				logFileWriter.append(log + "\n");
				logFileWriter.flush();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	private void sendApk(String apkName, ObjectOutputStream objOut)
			throws IOException {
		File apkFile = new File(apkName);
		FileInputStream fin = new FileInputStream(apkFile);
		BufferedInputStream bis = new BufferedInputStream(fin);
		byte[] tempArray = new byte[(int) apkFile.length()];
		bis.read(tempArray, 0, tempArray.length);
		// Send file length first
		System.out.println("Sending apk length - " + tempArray.length);
		objOut.writeInt(tempArray.length);
		// Send the file
		System.out.println("Sending apk");
		objOut.write(tempArray);
		objOut.flush();
		bis.close();
	}
}

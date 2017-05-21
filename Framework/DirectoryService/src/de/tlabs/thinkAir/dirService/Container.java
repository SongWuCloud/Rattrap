package de.tlabs.thinkAir.dirService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.security.PublicKey;
import java.sql.ResultSet;
import java.sql.SQLException;

public class Container implements Serializable {

	//private static final long serialVersionUID = -6097868122333778588L;
	private static final long serialVersionUID = 1L;

	private int 			id = -1;
	private String 			name; 
	private String 			ip = "10.0.3.2";
	private int				status;
	private int				portForDir = 4322;
	private ContainerType	type = ContainerType.LXC;
	private int				mem = 50;
	private String 			cpuset = null;
	private	int				cpushare = 1024;
	private DBHelper		dbh = null;

	public Container() {
		this.status = ContainerState.UNKNOWN;
	}

	public Container(String name) {
		this.name	= name;
		this.status = ContainerState.UNKNOWN;
		//this.type	= detectType(this.name);
	}

	public Container(String name, String ip, int status) {
		this.name	= name;
		this.ip		= ip;
		this.status = status;
	}
	
	public Container(String name, String ip, int status, int mem, String cpuset, int cpushare) {
		this.name	= name;
		this.ip		= ip;
		this.status = status;
		this.mem 	= mem;
		this.cpuset = cpuset;
		this.cpushare = cpushare;
	}
	
	protected void finalize() throws Throwable{
		super.finalize();  
		dbh.dbClose();
        System.out.println("The Container Object was destroyed!");
	}

	public enum ContainerType {
		UNKNOWN, VIRTUALBOX, AMAZON, LXC
	}
	
	/**
	 * Detect the type of the clone from the name
	 */
	public static ContainerType detectType(String name) {
		if (name.startsWith("vb-"))
			return ContainerType.VIRTUALBOX;
		else if (name.startsWith("amazon-"))
			return ContainerType.AMAZON;
		else {
			System.err.println("The type of this clone could not be determined");
			printInfoAboutContainerName();
			return ContainerType.UNKNOWN;
		}
	}

	/**
	 * @return the id
	 */
	public int getId() {
		return id;
	}

	/**
	 * @param id the id to set
	 */
	public void setId(int id) {
		this.id = id;
	}

	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name the name to set
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * @return the ip
	 */
	public String getIp() {
		return ip;
	}

	/**
	 * @param ip the ip to set
	 */
	public void setIp(String ip) {
		this.ip = ip;
	}

	/**
	 * @return the status
	 */
	public int getStatus() {
		try{
			if(this.dbh == null){
				dbh = new DBHelper();
			}
			ResultSet rs = dbh.dbSelect("select status from lxc where name = '" + this.name + "'"); 
			if(rs.next()){
				int status = rs.getInt("status");
				this.status = status;
			}
		} catch (SQLException e) {
            e.printStackTrace();
        } 
		return this.status;
	}

	/**
	 * @param status the status to set
	 */
	public void setStatus(int status) {
		try{
			if(this.dbh == null){
				dbh = new DBHelper();
			}
			dbh.dbUpdate("update lxc set status = " + status + " where name = '" + this.name + "'"); 
		}  catch (Exception e) {
            e.printStackTrace();
        } 
		this.status = status;
	}

	/**
	 * @return the portForPhone
	 */
	public int getPortForDir() {
		return portForDir;
	}

	/**
	 * @param portForPhone the portForPhone to set
	 */
	public void setPortForDir(int portForDir) {
		this.portForDir = portForDir;
	}

	/**
	 * @return the type
	 */
	public ContainerType getType() {
		return type;
	}

	/**
	 * @param type the type to set
	 */
	public void setType(ContainerType type) {
		this.type = type;
	}

	/**
	 * @return the mem
	 */
	public int getMem() {
		return mem;
	}

	/**
	 * @param mem the mem to set
	 */
	public void setMem(int mem) {
		this.mem = mem;
	}
	
	/**
	 * @return the cpuset
	 */
	public String getCpuset() {
		return cpuset;
	}

	/**
	 * @param Cpuset the Cpuset to set
	 */
	public void setCpuset(String cpuset) {
		this.cpuset = cpuset;
	}
	
	/**
	 * @return the cpushare
	 */
	public int getCpushare() {
		return mem;
	}

	/**
	 * @param cpushare the cpushare to set
	 */
	public void setCpushare(int cpushare) {
		this.cpushare = cpushare;
	}
	
	

	public void describeContainer() {
		System.out.println(name + " " + id + " " + ip);
	}

/*	public void initializeContainer(Configuration config) {
		this.portForContainer	= config.getContainerPortForContainers();
		this.portForPhone	= config.getContainerPortForPhones();
	}*/

	/**
	 * Find which is the status of this clone and make it available for the phone.
	 * @return True if it was possible to make the clone available<br>
	 * False otherwise
	 */
	public boolean prepareContainer() {

		switch(this.type) {

		case LXC:

			System.out.println("Preparing the LXC " + this.name);

			//switch (getTheStateOfLXC()) {
			switch (this.status) {
				case ContainerState.STOPPED:
					if (startContainer()) {
						System.out.println("Started the LXC " + this.name);
						this.status = ContainerState.ASSIGNING;
						return true;
					}
					else {
						System.err.println("Could not start the LXC" + this.name);
						this.status = ContainerState.STOPPED;
						return false;
					}
	
				case ContainerState.PAUSED:
					if (resumeContainer()) {
						System.out.println("Resumed the LXC " + this.name);
						this.status = ContainerState.ASSIGNING;
						return true;
					}
					else {
						System.err.println("Could not resume the LXC " + this.name);
						this.status = ContainerState.STOPPED;
						return false;
					}
	
				case ContainerState.RESUMED:
					System.out.println("The LXC " + this.name + " was already started");
					this.status = ContainerState.ASSIGNING;
					return true;
				
				case ContainerState.CREATING:
					if (createContainer() && startContainer()) {
						System.out.println("Created the LXC " + this.name);
						this.status = ContainerState.ASSIGNING;
						return true;
					}
					else {
						System.err.println("Could not create the LXC" + this.name);
						this.status = ContainerState.UNKNOWN;
						return false;
					}
			
			}

			return false;

		case AMAZON:
			System.out.println("Preparing the Amazon clone " + this.name);
			System.err.println("Not yet implemented for the amazon clones");
			break;

		case UNKNOWN:
			System.err.println("I don't know how to start the clone " + this.name);
			printInfoAboutContainerName();
			break;
		}

		return false;
	}

	// Methods to control the local LXC.

	private int getTheStateOfLXC() {
		switch (this.type) {

		case LXC:
			String out = executeCommand("lxc-info -n " + this.name);
			
			if (out.contains("STOPPED")) {
				return ContainerState.STOPPED;
			}
			else if (out.contains("RUNNING")) {
				return ContainerState.RESUMED;
			}
			else if (out.contains("FROZEN")) {
				return ContainerState.PAUSED;
			}

			break;

		case AMAZON:
			System.err.println("Not yet implemented for the amazon clones");
			break;

		case UNKNOWN:
			System.err.println("I cant't get the physical state of the clone " + this.name);
			printInfoAboutContainerName();
			break;
		}

		return ContainerState.UNKNOWN;
	}

	/**
	 * Start a container from stopped status
	 * @throws IllegalStateException
	 */
	public boolean startContainer() {

		//String out = executeCommand("VBoxManage startvm " + this.name + " --type headless");
		String out = executeCommand("lxc-start -n " + this.name + " -s lxc.network.ipv4=" + this.ip + "/24" );

		if (out.isEmpty()) 
			return true;

		return false;
	}
	
	/**
	 * Create a container from scratch
	 * @throws IllegalStateException
	 */
	public boolean createContainer() {

		//String out = executeCommand("VBoxManage startvm " + this.name + " --type headless");
		String out = executeCommand("/root/newlxc.py -n " + this.name + " --ip=" + this.ip);

		if (out.isEmpty()) 
			return true;

		return false;
	}

	public boolean resumeContainer() {
		executeCommand("lxc-unfreeze -n " + this.name);

		switch(getTheStateOfLXC()) {
		case ContainerState.STOPPED:
			return false;
		case ContainerState.PAUSED:
			return false;
		case ContainerState.RESUMED:
			return true;
		}

		return false;
	}

	public boolean pauseContainer() {
		
		executeCommand("lxc-freeze -n " + this.name);

		switch(getTheStateOfLXC()) {
		case ContainerState.STOPPED:
			return false;
		case ContainerState.PAUSED:
			return true;
		case ContainerState.RESUMED:
			return false;
		}

		return false;

	}

	public boolean stopContainer() {
		
		executeCommand("lxc-stop -n " + this.name + " -k");

		switch(getTheStateOfLXC()) {
		case ContainerState.STOPPED:
			return true;
		case ContainerState.PAUSED:
			return false;
		case ContainerState.RESUMED:
			return false;
		}

		return false;

	}
	
/*	public boolean releaseContainer() {
		
		this.setStatus(ContainerState.RESUMED);

		return true;

	}*/

	private String executeCommand(String command) {

		try {
			Process p = Runtime.getRuntime().exec(command);
			// you can pass the system command or a script to exec command.
			BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));

			BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));

			// read the output from the command
			StringBuilder sb = new StringBuilder();
			String s = "";
			while ((s = stdInput.readLine()) != null) {
//				System.out.println("Std OUT: "+s);
				sb.append(s);
			}

			while ((s = stdError.readLine()) != null) {
				System.out.println("Std ERROR : "+s);
			}

			return sb.toString();

		} catch (IOException e) {

			e.printStackTrace();
		}
		return null;
	}

	public static void printInfoAboutContainerName() {
		System.out.println("The name of the clone should start with vb- for VirtualBox clones, and with amazon- for Amazon clones.");
	}
}

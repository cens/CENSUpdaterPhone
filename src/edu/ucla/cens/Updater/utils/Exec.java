package edu.ucla.cens.Updater.utils;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import android.util.Log;

/**
 * Command line execution.
 */
public class Exec {
    public static final String TAG = Exec.class.getSimpleName();
	
    /**
     * Buffer length for reading command output 
     */
	//private static final int BUFF_LEN = 1024;
	
	/**
	 * Shell to execute. Bu default, it's su (superuser), but could
	 * be also sh in non-root execution is required.
	 */
	private String shell;
	
    public Exec(String shell) {
		this.shell = shell;
	}
    
    public Exec() {
		this.shell = "su";
	}

	/**
     * Executes command as super user in async mode.
     * @param command
     */
    public void execAsSuAsync(final String command) {
		new Thread(){
			public void run() {
				execAsSu(command);
			}
		}.start();
    	
    }

    /**
     * Executes command as super user.
     * @param command
     */
    public String execAsSu(String command) {
        String msg = null;
        String errorOut = "";
        boolean errorSeen = true;
        boolean successOrFailSeen = false;
        try {
            // Perform su to get root privilege
            //Process p = Runtime.getRuntime().exec(shell);
            // if using sh instead of su, we'll get this message:
            //   reboot operation not permitted.
            //Process p = Runtime.getRuntime().exec("sh");
            Process p = new ProcessBuilder(shell).redirectErrorStream(true).start();
            
            //InputStream stdin = p.getInputStream();
            //InputStream stderr = p.getErrorStream();
            BufferedReader stdin = new BufferedReader(new InputStreamReader(p.getInputStream()));

            // Write command
            Log.d(TAG, "Running: " + command);
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes(command + "\n");
            // Close the terminal

            //int read;
            StringBuffer sb = new StringBuffer();
            //read output
            //byte[] buffer = new byte[BUFF_LEN];
            String buffer;
            while((buffer = stdin.readLine()) != null) {
            	//String outputstr = new String(buffer, 0, read);
            	String outputstr = buffer;
            	//Log.d(TAG, "read " + Integer.toString(read) + " " + outputstr);
            	Log.d(TAG, "read " + Integer.toString(outputstr.length()) + " " + outputstr);
            	sb.append(outputstr);
            	if (outputstr.contains("Success") || outputstr.contains("Fail")) {
                    os.writeBytes("exit\n");	
            	    os.writeBytes("exit\n");	
                    os.flush();
                    os.close();
                    successOrFailSeen = true;
            		break;
            	}
            }            
            String stdOut = sb.toString();
            Log.d(TAG, "execAsSu stdout: " + stdOut);
            
            /*
            // read stderr
            sb = new StringBuffer();
            while(true){
                read = stderr.read(buffer);
                if(read<0){
                    //we have read everything
                    break;
                }
                sb.append(new String(buffer, 0, read));
            }            
            errorOut = sb.toString();
            Log.d(TAG, "execAsSu stderr: " + errorOut);
            */
            
            try {
            	//p.waitFor();
            	if (successOrFailSeen) {
            		p.destroy();
            		msg = stdOut;
            		if (msg.contains("Success")) {
            			errorSeen = false;
            		}
            	} else {
            		p.waitFor();
	                if (p.exitValue() == 0) {
	                	msg = "Executing '" + command + "' as root succeeded.\nexit value="+ p.exitValue();
	                	if (!errorOut.equals("")) {
	                    	msg += "\nError output: " + errorOut;
	                	}
	                	if (!stdOut.equals("")) {
	                    	msg += "\nOutput: " + stdOut;
	                	}
	                	errorSeen = false;
	                } else if (p.exitValue() == 1 && (command.startsWith("ls") || command.startsWith("/system/bin/ls"))) {
	                	msg = "Executing '" + command + "' as root succeeded, but the file was not found.\nexit value="+ p.exitValue();
	                	if (!errorOut.equals("")) {
	                    	msg += "\nError output: " + errorOut;
	                	}
	                	if (!stdOut.equals("")) {
	                    	msg += "\nOutput: " + stdOut;
	                	}
	                } else if (p.exitValue() == 127) {
	                	msg = "Executing '" + command + "' as root failed (command not found).\nexit value="+ p.exitValue();
	                	if (!errorOut.equals("")) {
	                    	msg += "\nError output: " + errorOut;
	                	}
	                	if (!stdOut.equals("")) {
	                    	msg += "\nOutput: " + stdOut;
	                	}
	                } else {
	                	msg = "Executing '" + command + "' as root failed.\nexit value=" + p.exitValue();
	                	if (!errorOut.equals("")) {
	                    	msg += "\nError output: " + errorOut;
	                	}
	                	if (!stdOut.equals("")) {
	                    	msg += "\nOutput: " + stdOut;
	                	}
	                }
            	}	
            } catch (InterruptedException e) {
            	msg = "Encountered InterruptedException while executing '" + command + "' as root.";
            }
        } catch (IOException e) {
        	msg = "Superuser is probably not configured, therefore reboot will not work.\n\nEncountered IOException while executing '" + command + "' as root: " + e;
            Log.e(TAG, msg);
            //AppManager.get().doToastMessage(msg);
            
        } finally {
        	if (errorSeen) {
        		throw new RuntimeException(msg);
        	}
        }
        return msg;
    }
    
	
}

package ext.jci;

import java.lang.reflect.InvocationTargetException;
import java.rmi.RemoteException;

import wt.epm.workspaces.EPMWorkspace;
import wt.fc.PersistenceHelper;
import wt.fc.QueryResult;
import wt.method.RemoteMethodServer;
import wt.pds.StatementSpec;
import wt.query.QuerySpec;
import wt.util.WTException;

public class TestTraining {
	
	public static final String REMOTE_CLASS = "ext.jci.Training";
	public static final String REMOTE_METHOD = "fileUpload";

	public static void main(String[] args) throws WTException, RemoteException, InvocationTargetException {

		RemoteMethodServer rms = RemoteMethodServer.getDefault();

		rms.setUserName("wcadmin");
		rms.setPassword("wcadmin");

		rms.invoke(REMOTE_METHOD, REMOTE_CLASS, null, null, null);

	}

}

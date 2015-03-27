package bluetooth;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;

import javax.bluetooth.*;
import javax.microedition.io.Connector;
import javax.obex.*;

/**
 * This program uses the BlueCove library to try to establish a bluetooth connection. 
 * Initially it searches for devices and registers the services that they provide. 
 * Afterwards, it asks the user which device he wishes to connect to.
 * Finally, it attempts to send a message with the newly established connection.
 * @author jpgrego
 *
 */
public class BluetoothConnector {
	
	private static Logger myLog = Logger.getLogger(BluetoothConnector.class.getName());
	private static final UUID[] searchUUIDSet = new UUID[]{new UUID(0x0003)};		// 0x0003 = RFCOMM UUID
	private static int[] attrIDSet = new int[]{0x0003};								// attribute ID to obtain serviceID

	private Map<RemoteDevice, List<String>> devicesAndServices;
	private Object inquiryCompleteLock, serviceSearchCompleteLock;
	
	public BluetoothConnector() {
		devicesAndServices = new HashMap<RemoteDevice, List<String>>();
		inquiryCompleteLock = new Object();
		serviceSearchCompleteLock = new Object();
	}
	
	public int searchDevices() throws BluetoothStateException, InterruptedException {
		
		int size;
		
		synchronized(inquiryCompleteLock) {
			boolean inquiryHasStarted = LocalDevice.getLocalDevice().getDiscoveryAgent().startInquiry(DiscoveryAgent.GIAC, listener);
			
			if(inquiryHasStarted) inquiryCompleteLock.wait();
		}
		
		size = devicesAndServices.size();
		if(size == 0) return 0;
		return size;
	}
	
	public void searchServices() throws BluetoothStateException,InterruptedException {
		for(RemoteDevice device : devicesAndServices.keySet()) searchServicesFromDevice(device);
	}
	
	public int searchServicesFromDevice(RemoteDevice device) throws BluetoothStateException,InterruptedException {
		synchronized(serviceSearchCompleteLock) {
			LocalDevice.getLocalDevice().getDiscoveryAgent().searchServices(attrIDSet, searchUUIDSet, device, listener);
			serviceSearchCompleteLock.wait();
		}
		
		return devicesAndServices.get(device).size();
	}
	
	public boolean sendTestMessage(String url) throws IOException {
		//serverURL = "btgoep://B8F9348D57DF:12";
		ClientSession clientSession = (ClientSession) Connector.open(url);
		HeaderSet hsConnectReply = clientSession.connect(null);
		
		if(hsConnectReply.getResponseCode() != ResponseCodes.OBEX_HTTP_OK) return false;
		
		HeaderSet hsOperation = clientSession.createHeaderSet();
		hsOperation.setHeader(HeaderSet.NAME, "Hello.txt");
		hsOperation.setHeader(HeaderSet.TYPE, "text");
		
		Operation putOperation = clientSession.put(hsOperation);
		byte[] msg = "Hello!".getBytes("iso-8859-1");
		OutputStream os = putOperation.openOutputStream();
		os.write(msg);
		os.close();
		putOperation.close();
		clientSession.disconnect(null);
		clientSession.close();
		return true;
	}
	
	public RemoteDevice[] getDevicesArray() {
		return devicesAndServices.keySet().toArray(new RemoteDevice[devicesAndServices.size()]);
	}
	
	public List<String> getServicesFromDevice(RemoteDevice device) {
		return devicesAndServices.get(device);
	}
	
	private DiscoveryListener listener = new DiscoveryListener() {
		public void deviceDiscovered(RemoteDevice btDevice, DeviceClass cod) {
			if(!devicesAndServices.containsKey(btDevice)) {
				List<String> services = new ArrayList<String>();
				devicesAndServices.put(btDevice, services);
			}
		} 
		
		public void inquiryCompleted(int respCode) {		
			switch(respCode) {
				case DiscoveryListener.INQUIRY_COMPLETED:
					myLog.fine("Device search is complete");
					break;
				case DiscoveryListener.INQUIRY_TERMINATED:
					myLog.fine("Device search was interrupted");
					break;
				case DiscoveryListener.INQUIRY_ERROR:
					myLog.warning("An error has occured during device search");
					break;
				default:
					myLog.warning("Unknown error code while searching for devices");
					break;
			}
			
			synchronized(inquiryCompleteLock) {
				inquiryCompleteLock.notifyAll();
			}
		}

		public void serviceSearchCompleted(int transID, int respCode) {		
			
			switch(respCode) {
				case DiscoveryListener.SERVICE_SEARCH_COMPLETED:
					myLog.fine("Service search is complete");
					break;
				case DiscoveryListener.SERVICE_SEARCH_TERMINATED:
					myLog.fine("Service search was interrupted");
					break;
				case DiscoveryListener.SERVICE_SEARCH_ERROR:
					myLog.warning("An error has occured while searching for services");
					break;
				case DiscoveryListener.SERVICE_SEARCH_DEVICE_NOT_REACHABLE:
					myLog.warning("The device is not reachable");
					break;
				case DiscoveryListener.SERVICE_SEARCH_NO_RECORDS:
					myLog.fine("No records were found during the service search");
					break;
					
				default:
					myLog.warning("Unknown error code during service search");
					break;
			}
			
			// service search is complete
			synchronized(serviceSearchCompleteLock) {
				serviceSearchCompleteLock.notifyAll();
			}
		}

		public void servicesDiscovered(int transID, ServiceRecord[] servRecord) {	
			String url;
			RemoteDevice device;
			for(int i = 0; i < servRecord.length; ++i) {
				url = servRecord[i].getConnectionURL(ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false); 
				if(url != null) {
					device = servRecord[i].getHostDevice();
					
					if(devicesAndServices.containsKey(device)) {
						List<String> serviceList = devicesAndServices.get(device);
						serviceList.add(url);
					}
					
					// this was supposed to obtain the service's name but I couldn't get it to work correctly
					//DataElement serviceName = servRecord[i].getAttributeValue(0x0100);
					//if(serviceName != null) System.out.printf("Service '%s' found\n", url);
					//else					System.out.printf("Service found\n", url);
				}
			}
		}
		
		
	};
	
}

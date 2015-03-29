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
 * This class was designed to provide the means to establish a bluetooth connection between two devices.
 * This code was not extensively tested and most probably won't work 100%
 * @author jpgrego
 *
 */
public class BluetoothConnector {
	
	private static Logger myLog = Logger.getLogger(BluetoothConnector.class.getName());
	
	/**
	 * These are the set of UUID's that will be searched for
	 */
	private static final UUID[] searchUUIDSet = new UUID[]{new UUID(0x0003)};		// 0x0003 = RFCOMM UUID
	
	/**
	 * These are the attributes of the services we wish to obtain
	 */
	private static int[] attrIDSet = new int[]{0x0003};								// 0x0003 = attribute ID to obtain serviceID

	/**
	 * Map containing the devices detected, and the respective services
	 */
	private Map<RemoteDevice, List<String>> devicesAndServices;
	
	/**
	 * Locks
	 */
	private Object inquiryCompleteLock, serviceSearchCompleteLock;
	
	/**
	 * Variable of the session established with a remote device
	 */
	private ClientSession clientSession;
	
	
	/**
	 * Constructor of the BluetoothConnector class
	 */
	public BluetoothConnector() {
		devicesAndServices = new HashMap<RemoteDevice, List<String>>();
		inquiryCompleteLock = new Object();
		serviceSearchCompleteLock = new Object();
	}
	
	/**
	 * This method initiates the scan for discoverable devices in the area and adds them to the devicesAndServices map
	 * @return the number of discoverable devices detected
	 * @throws BluetoothStateException thrown when a request is made to a Bluetooth device that cannot be supported in the current state
	 * @throws InterruptedException thrown when the thread was interrupted
	 */
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
	
	/**
	 * Initiates the scan of all the services available from all the devices that exist in the devicesAndServices map
	 * @throws BluetoothStateException
	 * @throws InterruptedException
	 */
	public void searchServices() throws BluetoothStateException,InterruptedException {
		for(RemoteDevice device : devicesAndServices.keySet()) searchServicesFromDevice(device);
	}
	
	/**
	 * Scans for all services that a specific device provides
	 * @param device the device to be scanned for services
	 * @return the number of services found
	 * @throws BluetoothStateException thrown when a request is made to a Bluetooth device that cannot be supported in the current state
	 * @throws InterruptedException thrown when the thread was interrupted
	 */
	public int searchServicesFromDevice(RemoteDevice device) throws BluetoothStateException,InterruptedException {
		synchronized(serviceSearchCompleteLock) {
			LocalDevice.getLocalDevice().getDiscoveryAgent().searchServices(attrIDSet, searchUUIDSet, device, listener);
			serviceSearchCompleteLock.wait();
		}
		
		return devicesAndServices.get(device).size();
	}
	
	/**
	 * Attempts to establish a connection to a provided URL
	 * @param url URL to the Bluetooth service
	 * @return boolean value, true if connection was successfully established, false otherwise
	 * @throws IOException thrown when the thread was interrupted
	 */
	public boolean connect(String url) throws IOException {
		clientSession = (ClientSession) Connector.open(url);
		HeaderSet hsConnectReply = clientSession.connect(null);
		if(hsConnectReply.getResponseCode() != ResponseCodes.OBEX_HTTP_OK) return false;
		return true;
	}
	
	/**
	 * Closes the established connection
	 * @throws IOException thrown when closing the connection failed for some reason
	 */
	public void disconnect() throws IOException {
		clientSession.disconnect(null);
		clientSession.close();
	}
	
	/**
	 * Test method that attempts to send a message through the established connection
	 * @throws IOException thrown when sending the message failed for some reason
	 */
	public void sendTestMessage() throws IOException {
		assert clientSession != null;
		
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
	}
	
	/**
	 * Obtain the devices that were detected
	 * @return array composed of RemoteDevice objects, the devices that were detected
	 */
	public RemoteDevice[] getDevicesArray() {
		return devicesAndServices.keySet().toArray(new RemoteDevice[devicesAndServices.size()]);
	}
	
	/**
	 * Obtain a list of the services provided by a device (it doesn't scan, just retrieves the services that were already scanned)
	 * @param device device whose services are to be retrieved
	 * @return List of strings representing the services that a Bluetooth device provides
	 */
	public List<String> getServicesFromDevice(RemoteDevice device) {
		return devicesAndServices.get(device);
	}
	
	/**
	 * Implementation of DiscoveryListener, providing device and service scanning
	 */
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

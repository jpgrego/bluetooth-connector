package bluetooth;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Scanner;
import java.util.Vector;
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
public class BluetoothTest {
	
	private static Logger myLog = Logger.getLogger(BluetoothTest.class.getName());
	
	static final UUID OBEX_FILE_TRANSFER = new UUID(0x0003);
	
	// must be Vector because of possible concurrent access
	public static final Vector<RemoteDevice> devicesDiscovered = new Vector<RemoteDevice>();
	public static final Vector<String> servicesFound = new Vector<String>();		// getDiscoveryAgent().searchServices(...) allows association of services to a device
	public static Object inquiryCompletedEvent = new Object(), serviceSearchCompletedEvent = new Object();
	
	public static void main(String[] args) throws IOException, InterruptedException {
		
		int count = 1, indexChoice = -1;
		int[] attrIDs = new int[] { 0x0100 };
		String serverURL;
		UUID[] searchUUIDSet = new UUID[] { OBEX_FILE_TRANSFER };
		Scanner scanner;
		
		devicesDiscovered.clear();		
		servicesFound.clear();
		
		synchronized(inquiryCompletedEvent) {
			boolean inquiryHasStarted = LocalDevice.getLocalDevice().getDiscoveryAgent().startInquiry(DiscoveryAgent.GIAC, listener);
			
			if(inquiryHasStarted) {
				System.out.printf("Searching for devices...\n");
				inquiryCompletedEvent.wait();
				System.out.printf("%d devices were found\n", devicesDiscovered.size());
			}
		}
		
		if(devicesDiscovered.size() == 0) {
			System.out.printf("No devices were found!\n");
			return;
		}
		
		System.out.printf("List of devices: \n");
		for(RemoteDevice dev : devicesDiscovered) System.out.printf("%d) %s (%s)\n", count++, dev.getBluetoothAddress(), dev.getFriendlyName(false));
		
		scanner = new Scanner(System.in);
		// crude input validation
		while(indexChoice <= -1 || indexChoice >= devicesDiscovered.size()) {
			System.out.printf("Choose device: ");		
			indexChoice = scanner.nextInt() - 1;
		}
		
		scanner.close();
		System.out.printf("Selected device : %s\n", devicesDiscovered.get(indexChoice).getFriendlyName(false));
		
		synchronized(serviceSearchCompletedEvent) {
			System.out.printf("Searching for services...\n");
			LocalDevice.getLocalDevice().getDiscoveryAgent().searchServices(attrIDs, searchUUIDSet, devicesDiscovered.get(indexChoice), listener);
			serviceSearchCompletedEvent.wait();
		}
		System.out.printf("List of services found on this device: \n");
		
		for(String s : servicesFound) System.out.printf("%s\n", s);
		
		// temporary
		serverURL = "btgoep://B8F9348D57DF:12";
		System.out.printf("Connecting to %s\n", serverURL);
		ClientSession clientSession = (ClientSession) Connector.open(serverURL);
		HeaderSet hsConnectReply = clientSession.connect(null);
		
		if(hsConnectReply.getResponseCode() != ResponseCodes.OBEX_HTTP_OK) {
			System.out.printf("Failed to connect\n");
		} 

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
	
	static DiscoveryListener listener = new DiscoveryListener() {
		public void deviceDiscovered(RemoteDevice btDevice, DeviceClass cod) {
			System.out.printf("%s found", btDevice.getBluetoothAddress());
			devicesDiscovered.add(btDevice);
			
			try {
				System.out.printf(" with name %s\n", btDevice.getFriendlyName(false));
			} catch(IOException failedToObtainName) {
				System.out.printf(" with no obtainable name\n");
			}
		} 
		
		public void inquiryCompleted(int respCode) {
			
			switch(respCode) {
				case DiscoveryListener.INQUIRY_COMPLETED:
					System.out.println("Device search is complete");
					break;
				case DiscoveryListener.INQUIRY_TERMINATED:
					System.out.println("Device search was interrupted");
					break;
				case DiscoveryListener.INQUIRY_ERROR:
					myLog.warning("An error has occured during device search");
					break;
				
				default:
					myLog.warning("Unknown error code while searching for devices");
					break;
			}
			
			synchronized(inquiryCompletedEvent) {
				inquiryCompletedEvent.notifyAll();
			}
		}

		public void serviceSearchCompleted(int transID, int respCode) {		
			
			switch(respCode) {
				case DiscoveryListener.SERVICE_SEARCH_COMPLETED:
					System.out.println("Service search is complete");
					break;
				case DiscoveryListener.SERVICE_SEARCH_TERMINATED:
					System.out.println("Service search was interrupted");
					break;
				case DiscoveryListener.SERVICE_SEARCH_ERROR:
					System.out.println("An error has occured while searching for services");
					break;
				case DiscoveryListener.SERVICE_SEARCH_DEVICE_NOT_REACHABLE:
					myLog.warning("The device is not reachable");
					break;
				case DiscoveryListener.SERVICE_SEARCH_NO_RECORDS:
					System.out.println("No records were found during the service search");
					break;
					
				default:
					myLog.warning("Unknown error code during service search");
					break;
			}
			
			// service search is complete
			synchronized(serviceSearchCompletedEvent) {
				serviceSearchCompletedEvent.notifyAll();
			}
		}

		public void servicesDiscovered(int transID, ServiceRecord[] servRecord) {	
			String url;
			for(int i = 0; i < servRecord.length; ++i) {
				url = servRecord[i].getConnectionURL(ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false); 
				if(url != null) {
					servicesFound.add(url);
					
					// this was supposed to obtain the service's name but I couldn't get it to work correctly
					//DataElement serviceName = servRecord[i].getAttributeValue(0x0100);
					//if(serviceName != null) System.out.printf("Service '%s' found\n", url);
					//else					System.out.printf("Service found\n", url);
				}
			}
		}
	};
	
}

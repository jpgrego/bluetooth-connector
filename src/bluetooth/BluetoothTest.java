package bluetooth;

import java.io.IOException;
import java.util.Scanner;
import java.util.Vector;
import javax.bluetooth.*;

/**
 * This program uses the BlueCove library to try to establish a bluetooth connection. 
 * Initially it searches for devices and registers the services that they provide. 
 * Afterwards, it asks the user which device he wishes to connect to.
 * Finally, it attempts to send a message with the newly established connection.
 * @author jpgrego
 *
 */
public class BluetoothTest {
	
	static final UUID OBEX_FILE_TRANSFER = new UUID(0x1106);
	
	// must be Vector because of possible concurrent access
	public static final Vector<RemoteDevice> devicesDiscovered = new Vector<RemoteDevice>();
	public static final Vector<String> servicesFound = new Vector<String>();		// getDiscoveryAgent().searchServices(...) allows association of services to a device
	public static Object inquiryCompletedEvent = new Object();
	
	public static void main(String[] args) throws IOException, InterruptedException {
		
		int count = 1, indexChoice = 9999;
		UUID serviceUUID = (args != null && args.length > 0) ? new UUID(args[0], false) : OBEX_FILE_TRANSFER;
		Scanner scanner = new Scanner(System.in);
		
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
		
		System.out.printf("List of devices: \n");
		for(RemoteDevice dev : devicesDiscovered) System.out.printf("%d) %s (%s)\n", count++, dev.getBluetoothAddress(), dev.getFriendlyName(false));
		
		// crude input validation
		while(indexChoice >= 9999) {
			System.out.printf("Choose device: ");		
			indexChoice = scanner.nextInt();
			System.out.println();
		}
		
		//devicesDiscovered.get(indexChoice).get;
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
		
		public void inquiryCompleted(int discType) {
			System.out.printf("Device search is complete\n");
			synchronized(inquiryCompletedEvent) {
				inquiryCompletedEvent.notifyAll();
			}
		}

		public void serviceSearchCompleted(int transID, int respCode) {			
			System.out.printf("Service search is complete\n");
		}

		public void servicesDiscovered(int transID, ServiceRecord[] servRecord) {	
			String url;
			for(int i = 0; i < servRecord.length; ++i) {
				url = servRecord[i].getConnectionURL(ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false); 
				if(url != null) {
					servicesFound.add(url);
					DataElement serviceName = servRecord[i].getAttributeValue(0x0100);
					
					if(serviceName != null) System.out.printf("Service '%s' found", url);
					else					System.out.printf("Service found", url);
				}
			}
		}
	};
	
}

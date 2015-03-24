package bluetooth;

import java.io.IOException;
import java.util.Vector;

import javax.bluetooth.DiscoveryListener;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.RemoteDevice;
import javax.bluetooth.DeviceClass;
import javax.bluetooth.ServiceRecord;
import javax.bluetooth.DiscoveryAgent;

// based on bluecove.org example
public class BluetoothTest {
	
	public static final Vector<RemoteDevice> devicesDiscovered = new Vector<RemoteDevice>();
	public static Object inquiryCompletedEvent = new Object();
	
	public static void main(String[] args) throws IOException, InterruptedException {
		
		devicesDiscovered.clear();		
		
		synchronized(inquiryCompletedEvent) {
			boolean inquiryHasStarted = LocalDevice.getLocalDevice().getDiscoveryAgent().startInquiry(DiscoveryAgent.GIAC, listener);
			
			if(inquiryHasStarted) {
				System.out.printf("Searching for devices...\n");
				inquiryCompletedEvent.wait();
				System.out.printf("%d devices were found\n", devicesDiscovered.size());
			}
		}
	}
	
	static DiscoveryListener listener = new DiscoveryListener() {
		public void deviceDiscovered(RemoteDevice btDevice, DeviceClass cod) {
			System.out.printf("%s found", btDevice.getBluetoothAddress());
			devicesDiscovered.addElement(btDevice);
			
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

		public void serviceSearchCompleted(int arg0, int arg1) {			
		}

		public void servicesDiscovered(int arg0, ServiceRecord[] arg1) {				
		}
	};
	
}

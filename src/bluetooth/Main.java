package bluetooth;

import java.io.IOException;
import java.util.Scanner;
import java.util.List;
import javax.bluetooth.RemoteDevice;

public class Main {
	public static void main(String[] args) throws IOException,InterruptedException {
		
		BluetoothConnector btConnector = new BluetoothConnector();
		int nDevices = 0, nServices = 0, count = 1, indexChoice = -1;
		Scanner scanner;
		RemoteDevice[] btDevices;
		List<String> deviceServices;
		String chosenService;
		
		System.out.print("Searching for devices...\n");
		
		while(nDevices <= 0) {
			nDevices = btConnector.searchDevices();
			if(nDevices == 0) System.out.printf("No devices were found! Retrying...\n");
		}
		
		System.out.printf("%d devices were found\n", nDevices);
		btDevices = btConnector.getDevicesArray();
		for(RemoteDevice dev : btDevices) {
			try {
				System.out.printf("%d) %s (%s)\n", count++, dev.getBluetoothAddress(), dev.getFriendlyName(false));
			} catch (IOException e) {
				// unable to get friendly name, just print without it
				System.out.printf("%d) %s\n", count++, dev.getBluetoothAddress());
			}
		}
		
		scanner = new Scanner(System.in);
		// crude input validation
		while(indexChoice <= -1 || indexChoice >= nDevices) {
			System.out.print("Choose device: ");		
			indexChoice = scanner.nextInt() - 1;
		}
		
		try {
			System.out.printf("Searching what services are available for device %s...\n", btDevices[indexChoice].getFriendlyName(false));
		} catch (IOException e) {
			// unable to get friendly name, just print without it
			System.out.printf("Searching what services are available for device %s...\n", btDevices[indexChoice].getBluetoothAddress());
		}
	
		count = 1;
		nServices = btConnector.searchServicesFromDevice(btDevices[indexChoice]);
		System.out.printf("%d services found\n", nServices);
		deviceServices = btConnector.getServicesFromDevice(btDevices[indexChoice]);
		for(String service : deviceServices) System.out.printf("%d) %s\n", count++, service);
		
		indexChoice = -1;
		while(indexChoice <= -1 || indexChoice >= deviceServices.size()) {
			System.out.print("Choose service: ");
			indexChoice = scanner.nextInt() - 1;
		}
		scanner.close();
		
		chosenService = deviceServices.get(indexChoice);
		System.out.printf("Connecting to service %s\n", chosenService);
		if(btConnector.connect(chosenService)) {
			System.out.print("Connected!\n");
		} else {
			System.out.print("Failed connecting to the chosen service\n");
			System.exit(1);
		}
		
		System.out.print("Sending test message through OBEX...\n");
		try {
			btConnector.sendTestMessage();
			System.out.print("Test message was sent successfully\n");
		} catch(IOException failedSendingMsg) {
			System.out.print("An error has occured sending the test message\n");
		}
		
		System.out.print("Exiting...\n");
	}
}

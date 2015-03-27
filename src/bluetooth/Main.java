package bluetooth;

import java.io.IOException;
import java.util.Scanner;
import javax.bluetooth.BluetoothStateException;
import javax.bluetooth.RemoteDevice;

public class Main {
	public static void main(String[] args) throws BluetoothStateException,InterruptedException {
		
		BluetoothConnector btConnector = new BluetoothConnector();
		int nDevices = 0, nServices = 0, count = 1, indexChoice = -1;
		Scanner scanner;
		RemoteDevice[] btDevices;
		
		System.out.printf("Searching for devices...\n");
		
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
			System.out.printf("Choose device: ");		
			indexChoice = scanner.nextInt() - 1;
		}
		scanner.close();
		
		try {
			System.out.printf("Searching what services are available for device %s...\n", btDevices[indexChoice].getFriendlyName(false));
		} catch (IOException e) {
			// unable to get friendly name, just print without it
			System.out.printf("Searching what services are available for device %s...\n", btDevices[indexChoice].getBluetoothAddress());
		}
	
		nServices = btConnector.searchServicesFromDevice(btDevices[indexChoice]);
		System.out.printf("%d services found\n", nServices);
	}
}
